package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSyncPeriod;
import com.clinic.clinicmanager.model.KsefSyncStatus;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.KsefSyncStatusType;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.KsefInvoiceItemRepo;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefIssuedInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.repo.KsefSyncPeriodRepo;
import com.clinic.clinicmanager.repo.KsefSyncStatusRepo;
import com.clinic.clinicmanager.service.impl.KsefAuthService;
import com.clinic.clinicmanager.service.impl.KsefInvoiceSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceMetadata;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceQueryFilters;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceQuerySubjectType;
import pl.akmf.ksef.sdk.client.model.invoice.QueryInvoiceMetadataResponse;

import com.clinic.clinicmanager.DTO.KsefInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefSyncStatusDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KsefInvoiceSyncServiceImplTest {

    @Mock private KsefAuthService ksefAuthService;
    @Mock private DefaultKsefClient ksefClient;
    @Mock private KsefInvoiceRepo invoiceRepo;
    @Mock private KsefInvoiceItemRepo invoiceItemRepo;
    @Mock private KsefIssuedInvoiceRepo issuedInvoiceRepo;
    @Mock private KsefSellerRepo sellerRepo;
    @Mock private KsefSyncStatusRepo syncStatusRepo;
    @Mock private KsefSyncPeriodRepo syncPeriodRepo;

    @InjectMocks
    private KsefInvoiceSyncServiceImpl syncService;

    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String KSEF_REF_1 = "20240115-FA-001";
    private static final String KSEF_REF_2 = "20240115-FA-002";

    private final Set<String> savedKsefRefs = new HashSet<>();
    private final Set<String> savedIssuedRefs = new HashSet<>();

    private static InvoiceQueryFilters subject2() {
        return argThat(f -> f != null && f.getSubjectType() == InvoiceQuerySubjectType.SUBJECT2);
    }

    private static InvoiceQueryFilters subject1() {
        return argThat(f -> f != null && f.getSubjectType() == InvoiceQuerySubjectType.SUBJECT1);
    }

    private QueryInvoiceMetadataResponse emptyMetadataResponse() {
        return mock(QueryInvoiceMetadataResponse.class);
    }

    private KsefSyncStatus freshStatus() {
        return KsefSyncStatus.builder()
                .id(1L)
                .status(KsefSyncStatusType.NEVER_SYNCED)
                .build();
    }

    private void stubAuth() throws Exception {
        savedKsefRefs.clear();
        savedIssuedRefs.clear();
        when(ksefAuthService.getAccessToken()).thenReturn(ACCESS_TOKEN);
        QueryInvoiceMetadataResponse issuedEmpty = emptyMetadataResponse();
        lenient().when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject1(), any()))
                .thenReturn(issuedEmpty);
        lenient().when(issuedInvoiceRepo.existsByKsefReferenceNumber(anyString())).thenAnswer(inv ->
                savedIssuedRefs.contains(inv.<String>getArgument(0)));
        lenient().when(issuedInvoiceRepo.save(any(KsefIssuedInvoice.class))).thenAnswer(inv -> {
            KsefIssuedInvoice invoice = inv.getArgument(0);
            savedIssuedRefs.add(invoice.getKsefReferenceNumber());
            return invoice;
        });
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.of(freshStatus()));
        when(syncStatusRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invoiceRepo.existsByKsefReferenceNumber(anyString())).thenAnswer(inv ->
                savedKsefRefs.contains(inv.<String>getArgument(0)));
        lenient().when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> {
            KsefInvoice invoice = inv.getArgument(0);
            savedKsefRefs.add(invoice.getKsefReferenceNumber());
            return invoice;
        });
        lenient().when(syncPeriodRepo.findByYearAndMonth(anyInt(), anyInt())).thenReturn(Optional.empty());
        lenient().when(syncPeriodRepo.save(any(KsefSyncPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(0L);
        lenient().when(sellerRepo.findByNip(anyString())).thenReturn(Optional.empty());
        lenient().when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private byte[] minimalXml(String invoiceNumber, String zaplacono) {
        return ("""
                <Faktura>
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>1234567890</NIP>
                            <Nazwa>Seller Sp. z o.o.</Nazwa>
                        </DaneIdentyfikacyjne>
                        <Adres>
                            <AdresL1>ul. Testowa 1</AdresL1>
                        </Adres>
                    </Podmiot1>
                    <Podmiot2>
                        <DaneIdentyfikacyjne>
                            <NIP>0987654321</NIP>
                            <Nazwa>Buyer S.A.</Nazwa>
                        </DaneIdentyfikacyjne>
                        <Adres>
                            <AdresL1>ul. Nabywcza 5</AdresL1>
                        </Adres>
                    </Podmiot2>
                    <Fa>
                        <P_1>2024-01-15</P_1>
                        <P_2>%s</P_2>
                        <RodzajFaktury>VAT</RodzajFaktury>
                        <KodWaluty>PLN</KodWaluty>
                        <FaWiersz>
                            <P_7>Item</P_7>
                            <P_8A>szt.</P_8A>
                            <P_8B>1</P_8B>
                            <P_9A>100.00</P_9A>
                            <P_11>100.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <P_15>123.00</P_15>
                        <Platnosc>
                            <Zaplacono>%s</Zaplacono>
                            <FormaPlatnosci>6</FormaPlatnosci>
                            <TerminPlatnosci>
                                <Termin>2024-01-29</Termin>
                            </TerminPlatnosci>
                        </Platnosc>
                    </Fa>
                </Faktura>
                """).formatted(invoiceNumber, zaplacono).getBytes(StandardCharsets.UTF_8);
    }

    private InvoiceMetadata stubbedMetadata(String ksefRef) {
        return stubbedMetadata(ksefRef, LocalDate.of(2024, 1, 15));
    }

    private InvoiceMetadata stubbedMetadata(String ksefRef, LocalDate issueDate) {
        InvoiceMetadata metadata = mock(InvoiceMetadata.class);
        when(metadata.getKsefNumber()).thenReturn(ksefRef);
        lenient().when(metadata.getIssueDate()).thenReturn(issueDate);
        return metadata;
    }

    private QueryInvoiceMetadataResponse stubbedResponse(boolean hasMore, InvoiceMetadata... items) {
        QueryInvoiceMetadataResponse response = mock(QueryInvoiceMetadataResponse.class);
        when(response.getInvoices()).thenReturn(List.of(items));
        lenient().when(response.getHasMore()).thenReturn(hasMore);
        return response;
    }

    private InvoiceMetadata stubbedMetadata(String ksefRef, LocalDate issueDate, OffsetDateTime storedAt) {
        InvoiceMetadata metadata = stubbedMetadata(ksefRef, issueDate);
        lenient().when(metadata.getPermanentStorageDate()).thenReturn(storedAt);
        return metadata;
    }

    private QueryInvoiceMetadataResponse stubbedEmptyResponse() {
        QueryInvoiceMetadataResponse response = mock(QueryInvoiceMetadataResponse.class);
        when(response.getInvoices()).thenReturn(List.of());
        return response;
    }

    @Test
    void syncInvoices_savesNewInvoice() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), eq(ACCESS_TOKEN)))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        verify(invoiceRepo).save(any(KsefInvoice.class));
    }

    @Test
    void syncInvoices_setsStatusSuccess_whenNoFailures() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(any(), any())).thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        KsefSyncStatus saved = captor.getValue();
        assertEquals(KsefSyncStatusType.SUCCESS, saved.getStatus());
        assertNotNull(saved.getLastSyncedAt());
        assertEquals(0, saved.getLastFailedCount());
    }

    @Test
    void syncInvoices_setsStatusPartialFailure_andDoesNotUpdateLastSyncedAt_whenAnyFails() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        InvoiceMetadata meta2 = stubbedMetadata(KSEF_REF_2);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1, meta2);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1)
                .thenReturn(stubbedEmptyResponse());
        when(ksefClient.getInvoice(eq(KSEF_REF_1), any()))
                .thenThrow(new RuntimeException("network error"));
        when(ksefClient.getInvoice(eq(KSEF_REF_2), any()))
                .thenReturn(minimalXml("FV/2024/01/002", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        KsefSyncStatus saved = captor.getValue();
        assertEquals(KsefSyncStatusType.PARTIAL_FAILURE, saved.getStatus());
        assertNull(saved.getLastSyncedAt());
        assertEquals(1, saved.getLastFailedCount());
        assertEquals(1, saved.getLastNewCount());
    }

    @Test
    void syncInvoices_rejectsXmlWithExternalEntity_andContinuesWithNextInvoice() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        InvoiceMetadata meta2 = stubbedMetadata(KSEF_REF_2);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1, meta2);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1)
                .thenReturn(stubbedEmptyResponse());
        String maliciousXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Faktura [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Faktura>
                    <Fa>
                        <P_2>&xxe;</P_2>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(eq(KSEF_REF_1), any()))
                .thenReturn(maliciousXml.getBytes(StandardCharsets.UTF_8));
        when(ksefClient.getInvoice(eq(KSEF_REF_2), any()))
                .thenReturn(minimalXml("FV/2024/01/002", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> invoiceCaptor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(invoiceCaptor.capture());
        assertEquals("FV/2024/01/002", invoiceCaptor.getValue().getInvoiceNumber());
        ArgumentCaptor<KsefSyncStatus> statusCaptor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(statusCaptor.capture());
        assertEquals(1, statusCaptor.getValue().getLastFailedCount());
        assertEquals(1, statusCaptor.getValue().getLastNewCount());
    }

    @Test
    void syncInvoices_parsesXmlFields_correctly() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();

        assertEquals(KSEF_REF_1, saved.getKsefReferenceNumber());
        assertEquals("FV/2024/01/001", saved.getInvoiceNumber());
        assertEquals("VAT", saved.getInvoiceType());
        assertEquals(LocalDate.of(2024, 1, 15), saved.getIssueDate());
        assertEquals(LocalDate.of(2024, 1, 29), saved.getPaymentDueDate());
        assertNotNull(saved.getSeller());
        assertEquals("1234567890", saved.getSeller().getNip());
        assertEquals("Seller Sp. z o.o.", saved.getSeller().getName());
        assertEquals("ul. Testowa 1", saved.getSeller().getAddress());
        assertFalse(saved.isAwaitingProductVerification());
        assertEquals("0987654321", saved.getBuyerNip());
        assertEquals("Buyer S.A.", saved.getBuyerName());
        assertEquals("ul. Nabywcza 5", saved.getBuyerAddress());
        assertEquals(new BigDecimal("100.00"), saved.getNetAmount());
        assertEquals(new BigDecimal("23.00"), saved.getVatAmount());
        assertEquals(new BigDecimal("123.00"), saved.getGrossAmount());
        assertEquals("PLN", saved.getCurrency());
        assertTrue(saved.getIsPaid());
        assertEquals("Przelew", saved.getPaymentMethod());
    }

    @Test
    void syncInvoices_reusesExistingSeller_andPropagatesAutoMarkProducts() throws Exception {
        stubAuth();
        KsefSeller existing = KsefSeller.builder()
                .id(99L)
                .nip("1234567890")
                .name("Seller Sp. z o.o.")
                .autoMarkProducts(true)
                .build();
        when(sellerRepo.findByNip("1234567890")).thenReturn(Optional.of(existing));
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();
        assertSame(existing, saved.getSeller());
        assertTrue(saved.isAwaitingProductVerification());
        verify(sellerRepo).save(existing);
    }

    @Test
    void syncInvoices_propagatesSellerDefaultExpenseCategory_whenAutoMarkProductsFalse() throws Exception {
        stubAuth();
        KsefSeller existing = KsefSeller.builder()
                .id(99L)
                .nip("1234567890")
                .name("Seller Sp. z o.o.")
                .autoMarkProducts(false)
                .defaultExpenseCategory(ExpenseCategoryCode.RENT)
                .build();
        when(sellerRepo.findByNip("1234567890")).thenReturn(Optional.of(existing));
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertEquals(ExpenseCategoryCode.RENT, captor.getValue().getCategory());
        assertFalse(captor.getValue().isAwaitingProductVerification());
    }

    @Test
    void syncInvoices_doesNotSetCategory_whenAutoMarkProductsTrue() throws Exception {
        stubAuth();
        KsefSeller existing = KsefSeller.builder()
                .id(99L)
                .nip("1234567890")
                .name("Seller Sp. z o.o.")
                .autoMarkProducts(true)
                .defaultExpenseCategory(ExpenseCategoryCode.RENT)
                .build();
        when(sellerRepo.findByNip("1234567890")).thenReturn(Optional.of(existing));
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertNull(captor.getValue().getCategory());
        assertTrue(captor.getValue().isAwaitingProductVerification());
    }

    @Test
    void syncInvoices_updatesExistingSellerContactDataFromXml() throws Exception {
        stubAuth();
        KsefSeller existing = KsefSeller.builder()
                .id(99L)
                .nip("1234567890")
                .name("Old Name")
                .address("Old Address")
                .email("old@example.com")
                .phone("000")
                .build();
        when(sellerRepo.findByNip("1234567890")).thenReturn(Optional.of(existing));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>1234567890</NIP>
                            <Nazwa>New Name</Nazwa>
                        </DaneIdentyfikacyjne>
                        <Adres>
                            <AdresL1>New Address</AdresL1>
                        </Adres>
                        <DaneKontaktowe>
                            <Email>new@example.com</Email>
                            <Telefon>123456789</Telefon>
                        </DaneKontaktowe>
                    </Podmiot1>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        assertEquals("New Name", existing.getName());
        assertEquals("New Address", existing.getAddress());
        assertEquals("new@example.com", existing.getEmail());
        assertEquals("123456789", existing.getPhone());
    }

    @Test
    void syncInvoices_setsIsPaidTrue_whenRozliczenieDoZaplatyIsZero() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                        <Rozliczenie><DoZaplaty>0</DoZaplaty></Rozliczenie>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertTrue(captor.getValue().getIsPaid());
    }

    @Test
    void syncInvoices_setsPaymentDate_andUsesOpisPlatnosci_whenPlatnoscInna() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                        <Platnosc>
                            <Zaplacono>1</Zaplacono>
                            <DataZaplaty>2026-05-31</DataZaplaty>
                            <PlatnoscInna>1</PlatnoscInna>
                            <OpisPlatnosci>Pobranie</OpisPlatnosci>
                        </Platnosc>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();
        assertTrue(saved.getIsPaid());
        assertEquals(LocalDate.of(2026, 5, 31), saved.getPaymentDate());
        assertEquals("Pobranie", saved.getPaymentMethod());
    }

    @Test
    void syncInvoices_computesNetFromGross_whenLineHasOnlyP11A_P11Vat() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                        <P_15>195.98</P_15>
                        <FaWiersz>
                            <P_7>Organizer</P_7>
                            <P_8A>szt.</P_8A>
                            <P_8B>2.000000</P_8B>
                            <P_9B>97.99</P_9B>
                            <P_11A>195.98</P_11A>
                            <P_11Vat>36.65</P_11Vat>
                            <P_12>23</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();
        assertEquals(new BigDecimal("159.33"), saved.getNetAmount());
        assertEquals(new BigDecimal("36.65"), saved.getVatAmount());
        assertEquals(new BigDecimal("195.98"), saved.getGrossAmount());
    }

    @Test
    void syncInvoices_computesNetFromGross_byDividingP11A_whenNoP11Vat() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                        <P_15>123.00</P_15>
                        <FaWiersz>
                            <P_11A>123.00</P_11A>
                            <P_12>23</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertEquals(new BigDecimal("100.00"), captor.getValue().getNetAmount());
    }

    @Test
    void syncInvoices_computesItemGross_whenInvoiceIssuedNetWise() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoiceItem item = captor.getValue().getItems().getFirst();
        assertEquals(new BigDecimal("100.00"), item.getUnitNetPrice());
        assertEquals(new BigDecimal("100.00"), item.getNetValue());
        assertEquals(new BigDecimal("123.0000"), item.getUnitGrossPrice());
        assertEquals(new BigDecimal("123.00"), item.getGrossValue());
    }

    @Test
    void syncInvoices_keepsItemGrossFromXml_whenInvoiceIssuedGrossWise() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                        <P_15>195.98</P_15>
                        <FaWiersz>
                            <P_7>Organizer</P_7>
                            <P_8A>szt.</P_8A>
                            <P_8B>2.000000</P_8B>
                            <P_9B>97.99</P_9B>
                            <P_11A>195.98</P_11A>
                            <P_11Vat>36.65</P_11Vat>
                            <P_12>23</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoiceItem item = captor.getValue().getItems().getFirst();
        assertEquals(new BigDecimal("97.99"), item.getUnitGrossPrice());
        assertEquals(new BigDecimal("195.98"), item.getGrossValue());
        assertEquals(new BigDecimal("159.33"), item.getNetValue());
    }

    @Test
    void syncInvoices_leavesItemGrossEqualToNet_whenVatRateIsNotNumeric() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        String xml = """
                <Faktura>
                    <Fa>
                        <P_2>FV/2024/01/001</P_2>
                        <FaWiersz>
                            <P_7>Usługa medyczna</P_7>
                            <P_8B>1</P_8B>
                            <P_9A>200.00</P_9A>
                            <P_11>200.00</P_11>
                            <P_12>zw</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """;
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoiceItem item = captor.getValue().getItems().getFirst();
        assertEquals(new BigDecimal("200.0000"), item.getUnitGrossPrice());
        assertEquals(new BigDecimal("200.00"), item.getGrossValue());
    }

    @Test
    void syncInvoices_setsIsPaidFalse_whenZaplaconoIsNotOne() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "0"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertFalse(captor.getValue().getIsPaid());
    }

    @Test
    void syncInvoices_skipsExistingInvoice() throws Exception {
        stubAuth();
        InvoiceMetadata meta1 = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page1 = stubbedResponse(false, meta1);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page1);
        when(invoiceRepo.existsByKsefReferenceNumber(KSEF_REF_1)).thenReturn(true);

        syncService.syncInvoices();

        verify(invoiceRepo, never()).save(any());
    }

    @Test
    void syncInvoices_stopsImmediately_whenEmptyListReturned() throws Exception {
        stubAuth();
        QueryInvoiceMetadataResponse emptyPage = stubbedEmptyResponse();
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(emptyPage);

        syncService.syncInvoices();

        verify(invoiceRepo, never()).save(any());
    }

    @Test
    void syncInvoices_advancesCursorByPermanentStorageDate_untilHasMoreFalse() throws Exception {
        stubAuth();
        InvoiceMetadata first = stubbedMetadata("REF-A", LocalDate.of(2026, 2, 10),
                OffsetDateTime.of(2026, 2, 10, 12, 0, 0, 0, ZoneOffset.UTC));
        InvoiceMetadata second = stubbedMetadata("REF-B", LocalDate.of(2026, 2, 20),
                OffsetDateTime.of(2026, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC));
        QueryInvoiceMetadataResponse pageMore = stubbedResponse(true, first);
        QueryInvoiceMetadataResponse pageLast = stubbedResponse(false, second);
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any()))
                .thenReturn(pageMore)
                .thenReturn(pageLast);
        when(ksefClient.getInvoice(any(), any())).thenReturn(minimalXml("FV", "1"));

        syncService.syncInvoices();

        verify(ksefClient, atLeast(2)).queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any());
        assertTrue(savedKsefRefs.contains("REF-A"));
        assertTrue(savedKsefRefs.contains("REF-B"));
    }

    @Test
    void syncInvoices_queriesWithOffsetZeroOnly_neverPagesByOffset() throws Exception {
        stubAuth();
        InvoiceMetadata first = stubbedMetadata("REF-A", LocalDate.of(2026, 2, 10),
                OffsetDateTime.of(2026, 2, 10, 12, 0, 0, 0, ZoneOffset.UTC));
        QueryInvoiceMetadataResponse pageMore = stubbedResponse(true, first);
        QueryInvoiceMetadataResponse pageLast = stubbedResponse(false,
                stubbedMetadata("REF-B", LocalDate.of(2026, 2, 20),
                        OffsetDateTime.of(2026, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC)));
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any()))
                .thenReturn(pageMore)
                .thenReturn(pageLast);
        when(ksefClient.getInvoice(any(), any())).thenReturn(minimalXml("FV", "1"));

        syncService.syncInvoices();

        verify(ksefClient, never()).queryInvoiceMetadata(intThat(o -> o != 0), anyInt(), any(), any(), any());
    }

    @Test
    void syncInvoices_stopsWindow_whenHasMoreButPermanentStorageDateMissing() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page = stubbedResponse(true, meta);
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any())).thenReturn(page);
        when(ksefClient.getInvoice(any(), any())).thenReturn(minimalXml("FV", "1"));

        syncService.syncInvoices();

        assertTrue(savedKsefRefs.contains(KSEF_REF_1));
    }

    @Test
    void syncInvoices_rejectsConcurrentSync_withConflictException() throws Exception {
        stubAuth();
        CountDownLatch insideSync = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        QueryInvoiceMetadataResponse emptyPage = emptyMetadataResponse();
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenAnswer(inv -> {
                    insideSync.countDown();
                    release.await();
                    return emptyPage;
                });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> first = executor.submit(() -> syncService.syncInvoices());
        try {
            assertTrue(insideSync.await(5, TimeUnit.SECONDS));

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> syncService.syncInvoices());
            assertTrue(ex.getMessage().contains("już trwa"));
        } finally {
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void syncInvoices_allowsSubsequentSync_afterPreviousCompletes() throws Exception {
        stubAuth();
        QueryInvoiceMetadataResponse empty = emptyMetadataResponse();
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(empty);

        syncService.syncInvoices();
        assertDoesNotThrow(() -> syncService.syncInvoices());
    }

    @Test
    void syncInvoices_throwsRuntimeException_andSetsPartialFailure_whenGetAccessTokenFails() {
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.of(freshStatus()));
        when(syncStatusRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ksefAuthService.getAccessToken())
                .thenThrow(new ResourceNotFoundException("no credentials"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> syncService.syncInvoices());
        assertTrue(ex.getMessage().contains("KSeF invoice sync failed"));

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        assertEquals(KsefSyncStatusType.PARTIAL_FAILURE, captor.getValue().getStatus());
    }

    @Test
    void syncInvoices_writesSyncPeriod_withKsefCountFromMetadata_perIssueMonth() throws Exception {
        stubAuth();
        InvoiceMetadata feb1 = stubbedMetadata("REF-FEB-1", LocalDate.of(2026, 2, 10));
        InvoiceMetadata feb2 = stubbedMetadata("REF-FEB-2", LocalDate.of(2026, 2, 20));
        InvoiceMetadata mar1 = stubbedMetadata("REF-MAR-1", LocalDate.of(2026, 3, 5));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, feb1, feb2, mar1);
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any()))
                .thenReturn(page)
                .thenReturn(stubbedEmptyResponse());
        when(ksefClient.getInvoice(any(), any())).thenReturn(minimalXml("FV", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefSyncPeriod> captor = ArgumentCaptor.forClass(KsefSyncPeriod.class);
        verify(syncPeriodRepo, atLeastOnce()).save(captor.capture());
        Map<String, Long> persisted = new HashMap<>();
        captor.getAllValues().forEach(p ->
                persisted.put(p.getYear() + "-" + p.getMonth(), p.getKsefCount()));
        assertEquals(2L, persisted.get("2026-2"));
        assertEquals(1L, persisted.get("2026-3"));
        assertEquals(0L, persisted.get("2026-4"));
    }

    @Test
    void syncInvoices_deduplicatesKsefCount_whenSameRefAppearsInOverlappingWindows() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata("REF-1", LocalDate.of(2026, 4, 1));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any()))
                .thenReturn(page);
        when(ksefClient.getInvoice(any(), any())).thenReturn(minimalXml("FV", "1"));

        syncService.syncInvoices();

        ArgumentCaptor<KsefSyncPeriod> captor = ArgumentCaptor.forClass(KsefSyncPeriod.class);
        verify(syncPeriodRepo, atLeastOnce()).save(captor.capture());
        KsefSyncPeriod april = captor.getAllValues().stream()
                .filter(p -> p.getYear() == 2026 && p.getMonth() == 4)
                .findFirst()
                .orElseThrow();
        assertEquals(1L, april.getKsefCount());
    }

    @Test
    void getSyncStatus_buildsSummary_withFullySyncedTrue_whenAllMonthsMatch() {
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.of(freshStatus()));
        when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(5L);
        when(syncPeriodRepo.findByYearAndMonth(anyInt(), anyInt())).thenAnswer(inv ->
                Optional.of(KsefSyncPeriod.builder()
                        .year(inv.getArgument(0))
                        .month(inv.getArgument(1))
                        .ksefCount(5L).build()));

        KsefSyncStatusDTO result = syncService.getSyncStatus();

        assertNotNull(result.getSummary());
        assertTrue(result.getSummary().isFullySynced());
        assertTrue(result.getSummary().getMonthsWithMismatch().isEmpty());
        assertNotNull(result.getSummary().getTotalKsef());
    }

    @Test
    void getSyncStatus_buildsSummary_withMismatchList_whenLocalCountBelowKsef() {
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.of(freshStatus()));
        when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(3L);
        when(syncPeriodRepo.findByYearAndMonth(anyInt(), anyInt())).thenAnswer(inv ->
                Optional.of(KsefSyncPeriod.builder()
                        .year(inv.getArgument(0))
                        .month(inv.getArgument(1))
                        .ksefCount(5L).build()));

        KsefSyncStatusDTO result = syncService.getSyncStatus();

        assertFalse(result.getSummary().isFullySynced());
        assertFalse(result.getSummary().getMonthsWithMismatch().isEmpty());
        result.getSummary().getMonthsWithMismatch().forEach(m -> assertEquals(2L, m.getMissing()));
    }

    @Test
    void getSyncStatus_buildsSummary_withFullySyncedFalse_whenNoSyncPeriodAtAll() {
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.of(freshStatus()));
        when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(0L);
        when(syncPeriodRepo.findByYearAndMonth(anyInt(), anyInt())).thenReturn(Optional.empty());

        KsefSyncStatusDTO result = syncService.getSyncStatus();

        assertFalse(result.getSummary().isFullySynced());
        assertNull(result.getSummary().getTotalKsef());
        assertEquals(0L, result.getSummary().getTotalLocal());
    }

    @Test
    void syncInvoices_skipsUpdateSyncPeriods_whenRateLimitHit() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_1, LocalDate.of(2026, 1, 10));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), any())).thenReturn(page);
        when(ksefClient.getInvoice(any(), any())).thenThrow(new RuntimeException("429 rate limit"));

        syncService.syncInvoices();

        verify(syncPeriodRepo, never()).save(any(KsefSyncPeriod.class));
    }

    @Test
    void getInvoiceCount_returnsLocalAndKsefCount_fromSyncPeriodSnapshot() {
        when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(1L);
        when(syncPeriodRepo.findByYearAndMonth(2024, 1)).thenReturn(Optional.of(
                KsefSyncPeriod.builder().year(2024).month(1).ksefCount(2L).build()));

        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setYear(2024);
        filter.setMonth(1);

        KsefInvoiceCountDTO result = syncService.getInvoiceCount(filter);

        assertEquals(1L, result.getLocalCount());
        assertEquals(2L, result.getKsefCount());
        verifyNoInteractions(ksefClient);
    }

    @Test
    void getInvoiceCount_returnsNullKsefCount_whenNoSyncPeriodForMonth() {
        when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(3L);
        when(syncPeriodRepo.findByYearAndMonth(2024, 1)).thenReturn(Optional.empty());

        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setYear(2024);
        filter.setMonth(1);

        KsefInvoiceCountDTO result = syncService.getInvoiceCount(filter);

        assertEquals(3L, result.getLocalCount());
        assertNull(result.getKsefCount());
    }

    @Test
    void getInvoiceCount_usesCurrentMonth_whenYearAndMonthNull() {
        when(invoiceRepo.countByIssueDateBetween(any(), any())).thenReturn(0L);
        when(syncPeriodRepo.findByYearAndMonth(anyInt(), anyInt())).thenReturn(Optional.empty());

        KsefInvoiceCountDTO result = syncService.getInvoiceCount(new KsefInvoiceFilterDTO());

        assertEquals(0L, result.getLocalCount());
        assertNull(result.getKsefCount());
        verify(invoiceRepo).countByIssueDateBetween(
                eq(LocalDate.now().withDayOfMonth(1)),
                eq(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())));
    }

    @Test
    void getSyncStatus_returnsCurrentStatus_whenExists() {
        LocalDateTime syncedAt = LocalDateTime.of(2024, 6, 1, 10, 0);
        KsefSyncStatus existing = KsefSyncStatus.builder()
                .id(1L)
                .status(KsefSyncStatusType.SUCCESS)
                .lastSyncedAt(syncedAt)
                .lastNewCount(3)
                .lastFailedCount(0)
                .build();
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.of(existing));

        KsefSyncStatusDTO result = syncService.getSyncStatus();

        assertEquals(KsefSyncStatusType.SUCCESS, result.getStatus());
        assertEquals(syncedAt, result.getLastSyncedAt());
        assertEquals(3, result.getLastNewCount());
        assertEquals(0, result.getLastFailedCount());
    }

    @Test
    void getSyncStatus_returnsNeverSynced_whenNoStatusInDb() {
        when(syncStatusRepo.findById(1L)).thenReturn(Optional.empty());
        when(syncStatusRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KsefSyncStatusDTO result = syncService.getSyncStatus();

        assertEquals(KsefSyncStatusType.NEVER_SYNCED, result.getStatus());
        assertNull(result.getLastSyncedAt());
    }

    private static final String KSEF_REF_KOR = "20260615-KOR-001";
    private static final String PARENT_REF = "20260610-FA-001";

    private byte[] correctionXml(String parentRef) {
        return ("""
                <Faktura>
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>1234567890</NIP>
                            <Nazwa>Seller Sp. z o.o.</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot1>
                    <Fa>
                        <P_1>2026-06-15</P_1>
                        <P_2>KOR/2026/06/001</P_2>
                        <RodzajFaktury>KOR</RodzajFaktury>
                        <KodWaluty>PLN</KodWaluty>
                        <DaneFaKorygowanej>
                            <NrKSeFFaKorygowanej>%s</NrKSeFFaKorygowanej>
                        </DaneFaKorygowanej>
                        <P_13_1>-100.00</P_13_1>
                        <P_14_1>-23.00</P_14_1>
                        <P_15>-123.00</P_15>
                        <FaWiersz>
                            <NrWierszaFa>1</NrWierszaFa>
                            <StanPrzed>1</StanPrzed>
                            <P_7>Krem</P_7>
                            <P_8A>szt.</P_8A>
                            <P_8B>1</P_8B>
                            <P_9A>100.00</P_9A>
                            <P_11>100.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <NrWierszaFa>1</NrWierszaFa>
                            <P_7>Krem</P_7>
                            <P_8A>szt.</P_8A>
                            <P_8B>0</P_8B>
                            <P_9A>100.00</P_9A>
                            <P_11>0.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """).formatted(parentRef).getBytes(StandardCharsets.UTF_8);
    }

    private KsefInvoice parentInvoice(String category) {
        return KsefInvoice.builder()
                .id(1L)
                .ksefReferenceNumber(PARENT_REF)
                .invoiceType("VAT")
                .category(category)
                .build();
    }

    @Test
    void syncInvoices_parsesCorrectionType_andSignedHeaderAmounts() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_KOR, LocalDate.of(2026, 6, 15));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_KOR, ACCESS_TOKEN)).thenReturn(correctionXml(PARENT_REF));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF))
                .thenReturn(Optional.of(parentInvoice("SERVICES")));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();
        assertEquals("KOR", saved.getInvoiceType());
        assertEquals(new BigDecimal("-100.00"), saved.getNetAmount());
        assertEquals(new BigDecimal("-23.00"), saved.getVatAmount());
        assertEquals(new BigDecimal("-123.00"), saved.getGrossAmount());
        assertEquals(PARENT_REF, saved.getCorrectedKsefNumber());
    }

    @Test
    void syncInvoices_collapsesCorrectionRowPair_intoSignedDelta() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_KOR, LocalDate.of(2026, 6, 15));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_KOR, ACCESS_TOKEN)).thenReturn(correctionXml(PARENT_REF));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF))
                .thenReturn(Optional.of(parentInvoice("SERVICES")));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();
        assertEquals(1, saved.getItems().size());
        assertEquals(new BigDecimal("-1"), saved.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("-100.00"), saved.getItems().getFirst().getNetValue());
    }

    @Test
    void syncInvoices_collapsesCorrectionRowPair_intoSignedGrossDelta() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_KOR, LocalDate.of(2026, 6, 15));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_KOR, ACCESS_TOKEN)).thenReturn(correctionXml(PARENT_REF));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF))
                .thenReturn(Optional.of(parentInvoice("SERVICES")));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoiceItem item = captor.getValue().getItems().getFirst();
        assertEquals(new BigDecimal("-123.00"), item.getGrossValue());
        assertEquals(new BigDecimal("123.0000"), item.getUnitGrossPrice());
    }

    @Test
    void syncInvoices_linksCorrectionToOriginal_andInheritsResolvedCategory() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_KOR, LocalDate.of(2026, 6, 15));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_KOR, ACCESS_TOKEN)).thenReturn(correctionXml(PARENT_REF));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF))
                .thenReturn(Optional.of(parentInvoice("SERVICES")));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        KsefInvoice saved = captor.getValue();
        assertEquals(1L, saved.getCorrectedInvoiceId());
        assertEquals("SERVICES", saved.getCategory());
        assertFalse(saved.isAwaitingProductVerification());
    }

    @Test
    void syncInvoices_leavesCorrectionDangling_whenOriginalNotFound() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_KOR, LocalDate.of(2026, 6, 15));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_KOR, ACCESS_TOKEN)).thenReturn(correctionXml(PARENT_REF));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF)).thenReturn(Optional.empty());

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        assertNull(captor.getValue().getCorrectedInvoiceId());
    }

    @Test
    void syncInvoices_backfillRelinksPendingCorrection_whenOriginalNowExists() throws Exception {
        stubAuth();
        QueryInvoiceMetadataResponse empty = stubbedEmptyResponse();
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(empty);

        KsefInvoice dangling = KsefInvoice.builder()
                .id(5L)
                .ksefReferenceNumber(KSEF_REF_KOR)
                .invoiceType("KOR")
                .correctedKsefNumber(PARENT_REF)
                .build();
        when(invoiceRepo.findPendingCorrectionsWithItems()).thenReturn(List.of(dangling));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF))
                .thenReturn(Optional.of(parentInvoice("EQUIPMENT")));

        syncService.syncInvoices();

        assertEquals(1L, dangling.getCorrectedInvoiceId());
        assertEquals("EQUIPMENT", dangling.getCategory());
        verify(invoiceRepo).save(dangling);
    }

    @Test
    void syncInvoices_backfillDoesNotSave_whenOriginalStillMissing() throws Exception {
        stubAuth();
        QueryInvoiceMetadataResponse empty = stubbedEmptyResponse();
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(empty);

        KsefInvoice dangling = KsefInvoice.builder()
                .id(5L)
                .ksefReferenceNumber(KSEF_REF_KOR)
                .invoiceType("KOR")
                .correctedKsefNumber(PARENT_REF)
                .build();
        when(invoiceRepo.findPendingCorrectionsWithItems()).thenReturn(List.of(dangling));
        when(invoiceRepo.findByKsefReferenceNumberWithItems(PARENT_REF)).thenReturn(Optional.empty());

        syncService.syncInvoices();

        assertNull(dangling.getCorrectedInvoiceId());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    private static final String ISSUED_REF_1 = "20260615-FS-001";
    private static final String ISSUED_REF_2 = "20260615-FS-002";
    private static final String ISSUED_KOR_REF = "20260701-KOR-FS-001";

    private void stubIssuedQuery(InvoiceMetadata... issuedMetadata) throws Exception {
        QueryInvoiceMetadataResponse receivedEmpty = emptyMetadataResponse();
        QueryInvoiceMetadataResponse issuedPage = stubbedResponse(false, issuedMetadata);
        QueryInvoiceMetadataResponse issuedEmpty = emptyMetadataResponse();
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any()))
                .thenReturn(receivedEmpty);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject1(), any()))
                .thenReturn(issuedPage)
                .thenReturn(issuedEmpty);
    }

    private KsefIssuedInvoice captureSavedIssuedInvoice() {
        ArgumentCaptor<KsefIssuedInvoice> captor = ArgumentCaptor.forClass(KsefIssuedInvoice.class);
        verify(issuedInvoiceRepo, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void syncIssuedInvoices_mapsBuyerFromPodmiot2_andKeepsAmountsAndItemsFromXml() throws Exception {
        stubAuth();
        stubIssuedQuery(stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)));
        when(ksefClient.getInvoice(ISSUED_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FS/2026/06/001", "1"));

        syncService.syncInvoices();

        KsefIssuedInvoice saved = captureSavedIssuedInvoice();
        assertEquals(ISSUED_REF_1, saved.getKsefReferenceNumber());
        assertEquals("FS/2026/06/001", saved.getInvoiceNumber());
        assertEquals("VAT", saved.getInvoiceType());
        assertEquals("Seller Sp. z o.o.", saved.getSellerName());
        assertEquals("1234567890", saved.getSellerNip());
        assertEquals("ul. Testowa 1", saved.getSellerAddress());
        assertEquals("0987654321", saved.getBuyerNip());
        assertEquals("Buyer S.A.", saved.getBuyerName());
        assertEquals("ul. Nabywcza 5", saved.getBuyerAddress());
        assertEquals(new BigDecimal("100.00"), saved.getNetAmount());
        assertEquals(new BigDecimal("23.00"), saved.getVatAmount());
        assertEquals(new BigDecimal("123.00"), saved.getGrossAmount());
        assertEquals("PLN", saved.getCurrency());
        assertEquals(1, saved.getItems().size());
        KsefIssuedInvoiceItem item = saved.getItems().getFirst();
        assertEquals("Item", item.getKsefItemName());
        assertEquals("szt.", item.getUnit());
        assertEquals(new BigDecimal("1"), item.getQuantity());
        assertEquals(new BigDecimal("100.00"), item.getNetValue());
        assertEquals(new BigDecimal("123.00"), item.getGrossValue());
        assertEquals(VatRate.VAT_23, item.getVatRate());
        assertSame(saved, item.getInvoice());
    }

    @Test
    void syncIssuedInvoices_mapsPodmiot1ToSeller_andPodmiot2ToBuyer_sidesNotSwappable() throws Exception {
        stubAuth();
        stubIssuedQuery(stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)));
        when(ksefClient.getInvoice(ISSUED_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FS/2026/06/001", "1"));

        syncService.syncInvoices();

        KsefIssuedInvoice saved = captureSavedIssuedInvoice();
        assertEquals("Seller Sp. z o.o.", saved.getSellerName());
        assertEquals("1234567890", saved.getSellerNip());
        assertEquals("ul. Testowa 1", saved.getSellerAddress());
        assertEquals("Buyer S.A.", saved.getBuyerName());
        assertEquals("0987654321", saved.getBuyerNip());
        assertEquals("ul. Nabywcza 5", saved.getBuyerAddress());
        assertNotEquals(saved.getSellerNip(), saved.getBuyerNip());
        assertNotEquals("Seller Sp. z o.o.", saved.getBuyerName());
        assertNotEquals("Buyer S.A.", saved.getSellerName());
    }

    @Test
    void syncIssuedInvoices_readsOwnCompanyDataFromXml_withoutCreatingKsefSellerRow() throws Exception {
        stubAuth();
        stubIssuedQuery(stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)));
        when(ksefClient.getInvoice(ISSUED_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FS/2026/06/001", "1"));

        syncService.syncInvoices();

        KsefIssuedInvoice saved = captureSavedIssuedInvoice();
        assertEquals("1234567890", saved.getSellerNip());
        assertTrue(savedKsefRefs.isEmpty());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
        verify(sellerRepo, never()).save(any(KsefSeller.class));
        verify(sellerRepo, never()).findByNip(anyString());
    }

    @Test
    void syncIssuedInvoices_parsesCorrectionAsSignedDifference_andStoresCorrectedKsefNumberAsText() throws Exception {
        stubAuth();
        stubIssuedQuery(stubbedMetadata(ISSUED_KOR_REF, LocalDate.of(2026, 7, 1)));
        when(ksefClient.getInvoice(ISSUED_KOR_REF, ACCESS_TOKEN))
                .thenReturn(correctionXml(PARENT_REF));

        syncService.syncInvoices();

        KsefIssuedInvoice saved = captureSavedIssuedInvoice();
        assertEquals("KOR", saved.getInvoiceType());
        assertEquals(new BigDecimal("-100.00"), saved.getNetAmount());
        assertEquals(new BigDecimal("-23.00"), saved.getVatAmount());
        assertEquals(new BigDecimal("-123.00"), saved.getGrossAmount());
        assertEquals(PARENT_REF, saved.getCorrectedKsefNumber());
        assertEquals(1, saved.getItems().size());
        assertEquals(new BigDecimal("-1"), saved.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("-100.00"), saved.getItems().getFirst().getNetValue());
        verify(invoiceRepo, never()).findByKsefReferenceNumberWithItems(anyString());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void syncIssuedInvoices_fetchesAndSavesNewInvoice_andCountsItAsNew() throws Exception {
        stubAuth();
        stubIssuedQuery(stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)));
        when(ksefClient.getInvoice(ISSUED_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FS/2026/06/001", "1"));

        syncService.syncInvoices();

        verify(ksefClient).getInvoice(ISSUED_REF_1, ACCESS_TOKEN);
        verify(issuedInvoiceRepo).save(any(KsefIssuedInvoice.class));
        verify(issuedInvoiceRepo, never()).delete(any(KsefIssuedInvoice.class));
        assertTrue(savedIssuedRefs.contains(ISSUED_REF_1));

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        assertEquals(1, captor.getValue().getLastIssuedNewCount());
    }

    @Test
    void syncIssuedInvoices_skipsAlreadyStoredInvoice_withoutFetchingXmlOrSaving_andDoesNotCountIt() throws Exception {
        stubAuth();
        savedIssuedRefs.add(ISSUED_REF_1);
        stubIssuedQuery(stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)));

        syncService.syncInvoices();

        verify(ksefClient, never()).getInvoice(eq(ISSUED_REF_1), any());
        verify(issuedInvoiceRepo, never()).save(any(KsefIssuedInvoice.class));
        verify(issuedInvoiceRepo, never()).delete(any(KsefIssuedInvoice.class));

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        assertEquals(0, captor.getValue().getLastIssuedNewCount());
        assertEquals(0, captor.getValue().getLastIssuedFailedCount());
    }

    @Test
    void syncIssuedInvoices_mixedBatch_fetchesOnlyNewInvoice_andCountsOnlyIt() throws Exception {
        stubAuth();
        savedIssuedRefs.add(ISSUED_REF_1);
        stubIssuedQuery(
                stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)),
                stubbedMetadata(ISSUED_REF_2, LocalDate.of(2026, 6, 15)));
        when(ksefClient.getInvoice(ISSUED_REF_2, ACCESS_TOKEN))
                .thenReturn(minimalXml("FS/2026/06/002", "1"));

        syncService.syncInvoices();

        verify(ksefClient, never()).getInvoice(eq(ISSUED_REF_1), any());
        KsefIssuedInvoice saved = captureSavedIssuedInvoice();
        assertEquals(ISSUED_REF_2, saved.getKsefReferenceNumber());
        verify(issuedInvoiceRepo, times(1)).save(any(KsefIssuedInvoice.class));

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        assertEquals(1, captor.getValue().getLastIssuedNewCount());
    }

    @Test
    void syncIssuedInvoicesForMonth_skipsAlreadyStoredInvoice_andSavesOnlyNewOne_withoutTouchingSyncStatus() throws Exception {
        savedIssuedRefs.clear();
        savedIssuedRefs.add(ISSUED_REF_1);
        when(ksefAuthService.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(issuedInvoiceRepo.existsByKsefReferenceNumber(anyString())).thenAnswer(inv ->
                savedIssuedRefs.contains(inv.<String>getArgument(0)));
        when(issuedInvoiceRepo.save(any(KsefIssuedInvoice.class))).thenAnswer(inv -> {
            KsefIssuedInvoice invoice = inv.getArgument(0);
            savedIssuedRefs.add(invoice.getKsefReferenceNumber());
            return invoice;
        });
        QueryInvoiceMetadataResponse issuedPage = stubbedResponse(false,
                stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)),
                stubbedMetadata(ISSUED_REF_2, LocalDate.of(2026, 6, 15)));
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject1(), any()))
                .thenReturn(issuedPage)
                .thenReturn(emptyMetadataResponse());
        when(ksefClient.getInvoice(ISSUED_REF_2, ACCESS_TOKEN))
                .thenReturn(minimalXml("FS/2026/06/002", "1"));

        syncService.syncIssuedInvoicesForMonth(YearMonth.of(2026, 6));

        verify(ksefClient, never()).getInvoice(eq(ISSUED_REF_1), any());
        verify(ksefClient).getInvoice(ISSUED_REF_2, ACCESS_TOKEN);
        verify(issuedInvoiceRepo, times(1)).save(any(KsefIssuedInvoice.class));
        assertTrue(savedIssuedRefs.contains(ISSUED_REF_2));
        verifyNoInteractions(syncStatusRepo);
    }

    @Test
    void syncReceivedInvoices_doNotTouchIssuedInvoiceRepo() throws Exception {
        stubAuth();
        QueryInvoiceMetadataResponse receivedPage = stubbedResponse(false, stubbedMetadata(KSEF_REF_1));
        when(ksefClient.queryInvoiceMetadata(eq(0), anyInt(), any(), subject2(), eq(ACCESS_TOKEN)))
                .thenReturn(receivedPage);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(minimalXml("FV/2024/01/001", "1"));

        syncService.syncInvoices();

        verify(invoiceRepo).save(any(KsefInvoice.class));
        assertTrue(savedIssuedRefs.isEmpty());
        verify(issuedInvoiceRepo, never()).save(any(KsefIssuedInvoice.class));
        verify(issuedInvoiceRepo, never()).delete(any(KsefIssuedInvoice.class));
        verify(issuedInvoiceRepo, never()).existsByKsefReferenceNumber(KSEF_REF_1);
    }

    @Test
    void syncIssuedInvoices_failureOnOneInvoiceDoesNotAbortSync_norAffectReceivedPipeline() throws Exception {
        stubAuth();
        stubIssuedQuery(
                stubbedMetadata(ISSUED_REF_1, LocalDate.of(2026, 6, 15)),
                stubbedMetadata(ISSUED_REF_2, LocalDate.of(2026, 6, 15)));
        when(ksefClient.getInvoice(eq(ISSUED_REF_1), any()))
                .thenThrow(new RuntimeException("issued fetch failed"));
        when(ksefClient.getInvoice(eq(ISSUED_REF_2), any()))
                .thenReturn(minimalXml("FS/2026/06/002", "1"));

        syncService.syncInvoices();

        assertTrue(savedIssuedRefs.contains(ISSUED_REF_2));
        assertFalse(savedIssuedRefs.contains(ISSUED_REF_1));
        assertTrue(savedKsefRefs.isEmpty());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));

        ArgumentCaptor<KsefSyncStatus> captor = ArgumentCaptor.forClass(KsefSyncStatus.class);
        verify(syncStatusRepo).save(captor.capture());
        KsefSyncStatus status = captor.getValue();
        assertEquals(KsefSyncStatusType.SUCCESS, status.getStatus());
        assertNotNull(status.getLastSyncedAt());
        assertEquals(0, status.getLastFailedCount());
        assertEquals(1, status.getLastIssuedNewCount());
        assertEquals(1, status.getLastIssuedFailedCount());
    }

    private String regularXmlWithLines() {
        return """
                <Faktura>
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>1234567890</NIP>
                            <Nazwa>Seller Sp. z o.o.</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot1>
                    <Fa>
                        <P_1>2026-06-10</P_1>
                        <P_2>FV/2026/06/001</P_2>
                        <RodzajFaktury>VAT</RodzajFaktury>
                        <FaWiersz>
                            <NrWierszaFa>1</NrWierszaFa>
                            <UU_ID>uuid-krem</UU_ID>
                            <P_7>Krem</P_7>
                            <P_8B>2</P_8B>
                            <P_11>200.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <NrWierszaFa>2</NrWierszaFa>
                            <P_7>Balsam</P_7>
                            <P_8B>1</P_8B>
                            <P_11>50.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <P_15>307.50</P_15>
                    </Fa>
                </Faktura>
                """;
    }

    private byte[] correctionXmlWithUuids(String parentRef) {
        return ("""
                <Faktura>
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>1234567890</NIP>
                            <Nazwa>Seller Sp. z o.o.</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot1>
                    <Fa>
                        <P_1>2026-06-15</P_1>
                        <P_2>KOR/2026/06/002</P_2>
                        <RodzajFaktury>KOR</RodzajFaktury>
                        <DaneFaKorygowanej>
                            <NrKSeFFaKorygowanej>%s</NrKSeFFaKorygowanej>
                        </DaneFaKorygowanej>
                        <P_15>-123.00</P_15>
                        <FaWiersz>
                            <NrWierszaFa>3</NrWierszaFa>
                            <UU_ID>uuid-before-3</UU_ID>
                            <StanPrzed>1</StanPrzed>
                            <P_7>Krem</P_7>
                            <P_8B>2</P_8B>
                            <P_11>200.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <NrWierszaFa>3</NrWierszaFa>
                            <UU_ID>uuid-after-3</UU_ID>
                            <P_7>Krem</P_7>
                            <P_8B>1</P_8B>
                            <P_11>100.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <NrWierszaFa>5</NrWierszaFa>
                            <UU_ID>uuid-before-5</UU_ID>
                            <StanPrzed>1</StanPrzed>
                            <P_7>Balsam</P_7>
                            <P_8B>1</P_8B>
                            <P_11>50.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <NrWierszaFa>5</NrWierszaFa>
                            <P_7>Balsam</P_7>
                            <P_8B>3</P_8B>
                            <P_11>150.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """).formatted(parentRef).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void syncInvoices_storesLineNumberAndUuId_forRegularInvoiceItems() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_1);
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any())).thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_1, ACCESS_TOKEN))
                .thenReturn(regularXmlWithLines().getBytes(StandardCharsets.UTF_8));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        List<KsefInvoiceItem> items = captor.getValue().getItems();
        assertEquals(1, items.get(0).getLineNumber());
        assertEquals("uuid-krem", items.get(0).getLineUuid());
        assertEquals(2, items.get(1).getLineNumber());
        assertNull(items.get(1).getLineUuid());
    }

    @Test
    void syncInvoices_storesSharedLineNumber_andUuIdFromAfterOrBeforeRow_forCorrectionItems() throws Exception {
        stubAuth();
        InvoiceMetadata meta = stubbedMetadata(KSEF_REF_KOR, LocalDate.of(2026, 6, 15));
        QueryInvoiceMetadataResponse page = stubbedResponse(false, meta);
        when(ksefClient.queryInvoiceMetadata(anyInt(), anyInt(), any(), subject2(), any())).thenReturn(page);
        when(ksefClient.getInvoice(KSEF_REF_KOR, ACCESS_TOKEN)).thenReturn(correctionXmlWithUuids(PARENT_REF));

        syncService.syncInvoices();

        ArgumentCaptor<KsefInvoice> captor = ArgumentCaptor.forClass(KsefInvoice.class);
        verify(invoiceRepo).save(captor.capture());
        List<KsefInvoiceItem> items = captor.getValue().getItems();
        assertEquals(2, items.size());
        assertEquals(3, items.get(0).getLineNumber());
        assertEquals("uuid-after-3", items.get(0).getLineUuid());
        assertEquals(new BigDecimal("-1"), items.get(0).getQuantity());
        assertEquals(5, items.get(1).getLineNumber());
        assertEquals("uuid-before-5", items.get(1).getLineUuid());
        assertEquals(new BigDecimal("2"), items.get(1).getQuantity());
    }

    private KsefInvoiceItem storedItem(Long id, String name, String quantity, Integer lineNumber) {
        return KsefInvoiceItem.builder()
                .id(id)
                .ksefItemName(name)
                .quantity(new BigDecimal(quantity))
                .lineNumber(lineNumber)
                .build();
    }

    private void stubStoredInvoice(Long id, String rawXml) {
        when(invoiceRepo.findById(id)).thenReturn(Optional.of(KsefInvoice.builder().id(id).rawXml(rawXml).build()));
    }

    @Test
    void backfillItemLines_fillsLineNumberAndUuId_byRowOrder_whenNameAndQuantityMatch() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of(1L));
        stubStoredInvoice(1L, regularXmlWithLines());
        when(invoiceItemRepo.findByInvoiceIdOrderByIdAsc(1L)).thenReturn(List.of(
                storedItem(10L, "Krem", "2.0000", null),
                storedItem(11L, "Balsam", "1", null)));
        when(invoiceItemRepo.updateLineIfMissing(anyLong(), any(), any())).thenReturn(1);

        syncService.backfillItemLines();

        verify(invoiceItemRepo).updateLineIfMissing(10L, 1, "uuid-krem");
        verify(invoiceItemRepo).updateLineIfMissing(11L, 2, null);
    }

    @Test
    void backfillItemLines_fillsCorrectionItems_fromCollapsedRowPairs() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of(2L));
        stubStoredInvoice(2L, new String(correctionXmlWithUuids(PARENT_REF), StandardCharsets.UTF_8));
        when(invoiceItemRepo.findByInvoiceIdOrderByIdAsc(2L)).thenReturn(List.of(
                storedItem(20L, "Krem", "-1", null),
                storedItem(21L, "Balsam", "2", null)));

        syncService.backfillItemLines();

        verify(invoiceItemRepo).updateLineIfMissing(20L, 3, "uuid-after-3");
        verify(invoiceItemRepo).updateLineIfMissing(21L, 5, "uuid-before-5");
    }

    @Test
    void backfillItemLines_isIdempotent_skipsItemsWithLineNumberAlreadySet() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of(1L));
        stubStoredInvoice(1L, regularXmlWithLines());
        when(invoiceItemRepo.findByInvoiceIdOrderByIdAsc(1L)).thenReturn(List.of(
                storedItem(10L, "Krem", "2", 1),
                storedItem(11L, "Balsam", "1", null)));

        syncService.backfillItemLines();

        verify(invoiceItemRepo, never()).updateLineIfMissing(eq(10L), any(), any());
        verify(invoiceItemRepo).updateLineIfMissing(11L, 2, null);
    }

    @Test
    void backfillItemLines_doesNothing_whenNoItemMissesLineNumber() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of());

        syncService.backfillItemLines();

        verify(invoiceRepo, never()).findById(any());
        verify(invoiceItemRepo, never()).updateLineIfMissing(any(), any(), any());
    }

    @Test
    void backfillItemLines_leavesItemNull_whenNameOrQuantityDoesNotMatchRow() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of(1L));
        stubStoredInvoice(1L, regularXmlWithLines());
        when(invoiceItemRepo.findByInvoiceIdOrderByIdAsc(1L)).thenReturn(List.of(
                storedItem(10L, "Krem", "3", null),
                storedItem(11L, "Balsam zmieniony", "1", null)));

        syncService.backfillItemLines();

        verify(invoiceItemRepo, never()).updateLineIfMissing(any(), any(), any());
    }

    @Test
    void backfillItemLines_skipsWholeInvoice_whenItemCountDiffersFromRows() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of(1L));
        stubStoredInvoice(1L, regularXmlWithLines());
        when(invoiceItemRepo.findByInvoiceIdOrderByIdAsc(1L)).thenReturn(List.of(
                storedItem(10L, "Krem", "2", null)));

        syncService.backfillItemLines();

        verify(invoiceItemRepo, never()).updateLineIfMissing(any(), any(), any());
    }

    @Test
    void backfillItemLines_skipsBrokenOrMissingXml_withoutThrowing_andContinuesWithNextInvoice() {
        when(invoiceItemRepo.findInvoiceIdsWithMissingLineNumber()).thenReturn(List.of(1L, 2L, 3L));
        stubStoredInvoice(1L, "<Faktura><Fa>");
        stubStoredInvoice(2L, null);
        stubStoredInvoice(3L, regularXmlWithLines());
        when(invoiceItemRepo.findByInvoiceIdOrderByIdAsc(3L)).thenReturn(List.of(
                storedItem(30L, "Krem", "2", null),
                storedItem(31L, "Balsam", "1", null)));

        assertDoesNotThrow(() -> syncService.backfillItemLines());

        verify(invoiceItemRepo, never()).findByInvoiceIdOrderByIdAsc(1L);
        verify(invoiceItemRepo, never()).findByInvoiceIdOrderByIdAsc(2L);
        verify(invoiceItemRepo).updateLineIfMissing(30L, 1, "uuid-krem");
        verify(invoiceItemRepo).updateLineIfMissing(31L, 2, null);
    }
}
