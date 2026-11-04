package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.NamedInvoiceDraftDTO;
import com.clinic.clinicmanager.DTO.NamedInvoiceItemDTO;
import com.clinic.clinicmanager.DTO.request.NamedInvoiceCreateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.Payment;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.Sale;
import com.clinic.clinicmanager.model.SaleItem;
import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.model.VisitItem;
import com.clinic.clinicmanager.model.Voucher;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.KsefIssuedInvoiceRepo;
import com.clinic.clinicmanager.repo.NamedInvoiceRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.service.impl.NamedInvoiceServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamedInvoiceServiceImplTest {

    @Mock NamedInvoiceRepo namedInvoiceRepo;
    @Mock KsefIssuedInvoiceRepo issuedInvoiceRepo;
    @Mock ClientRepo clientRepo;
    @Mock VisitRepo visitRepo;
    @Mock KsefInvoiceSyncService ksefInvoiceSyncService;

    @InjectMocks NamedInvoiceServiceImpl namedInvoiceService;

    private NamedInvoiceCreateDTO request(Long sourceVisitId) {
        NamedInvoiceCreateDTO request = new NamedInvoiceCreateDTO();
        request.setClientId(1L);
        request.setSourceVisitId(sourceVisitId);
        request.setInvoiceNumber("5/09/2026");
        request.setSequenceInMonth(5);
        request.setYear(2026);
        request.setMonth(9);
        request.setIssueDate(LocalDate.of(2026, 9, 9));
        request.setBuyerName("Anna Kowalska");
        request.setItems(new ArrayList<>());
        return request;
    }

    @Test
    void create_throwsConflict_whenVisitAlreadyHasInvoice() {
        NamedInvoice existing = NamedInvoice.builder()
                .id(42L).invoiceNumber("3/09/2026").sourceVisitId(7L).build();
        when(namedInvoiceRepo.findFirstBySourceVisitIdOrderByIdDesc(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> namedInvoiceService.create(request(7L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3/09/2026");

        verify(namedInvoiceRepo, never()).save(any());
    }

    @Test
    void create_succeeds_whenVisitHasNoInvoice() {
        Client client = Client.builder()
                .id(1L).firstName("Anna").lastName("Kowalska")
                .address("Polna 1").postalCode("30-100").city("Krakow").build();

        when(namedInvoiceRepo.findFirstBySourceVisitIdOrderByIdDesc(7L)).thenReturn(Optional.empty());
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(namedInvoiceRepo.save(any())).thenAnswer(invocation -> {
            NamedInvoice invoice = invocation.getArgument(0);
            invoice.setId(100L);
            invoice.setClient(client);
            return invoice;
        });

        NamedInvoiceDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            result = namedInvoiceService.create(request(7L));
        }

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getSourceVisitId()).isEqualTo(7L);
        verify(namedInvoiceRepo).save(any());
    }

    private Client client() {
        return Client.builder()
                .id(1L).firstName("Anna").lastName("Kowalska")
                .address("Polna 1").postalCode("30-100").city("Krakow").build();
    }

    private Payment payment(PaymentMethod method, String amount) {
        return Payment.builder().method(method).amount(new BigDecimal(amount)).build();
    }

    private Visit visitWithPayments(List<Payment> payments) {
        return Visit.builder()
                .id(7L)
                .client(client())
                .date(LocalDate.of(2026, 9, 8))
                .items(new ArrayList<>())
                .payments(new ArrayList<>(payments))
                .build();
    }

    @Test
    void buildDraft_fillsSaleDateWithVisitDate_andAllPaymentMethodsByAmountDesc() {
        Visit visit = visitWithPayments(List.of(
                payment(PaymentMethod.CASH, "20.00"),
                payment(PaymentMethod.CARD, "120.00"),
                payment(PaymentMethod.BLIK, "60.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getSaleDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(draft.getPaymentMethods())
                .containsExactly(PaymentMethod.CARD, PaymentMethod.BLIK, PaymentMethod.CASH);
        assertThat(draft.getNotes()).isNull();
    }

    @Test
    void buildDraft_ordersSplitPaymentByAmountDesc() {
        Visit visit = visitWithPayments(List.of(
                payment(PaymentMethod.CARD, "100.00"),
                payment(PaymentMethod.CASH, "40.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getPaymentMethods()).containsExactly(PaymentMethod.CARD, PaymentMethod.CASH);
    }

    @Test
    void buildDraft_sumsAmountsPerMethod_whenMethodRepeats() {
        Visit visit = visitWithPayments(List.of(
                payment(PaymentMethod.CASH, "30.00"),
                payment(PaymentMethod.CARD, "60.00"),
                payment(PaymentMethod.CASH, "50.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getPaymentMethods()).containsExactly(PaymentMethod.CASH, PaymentMethod.CARD);
    }

    @Test
    void buildDraft_skipsPaymentsWithNullMethod() {
        Visit visit = visitWithPayments(List.of(
                payment(null, "200.00"),
                payment(PaymentMethod.BLIK, "50.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getPaymentMethods()).containsExactly(PaymentMethod.BLIK);
    }

    @Test
    void buildDraft_leavesPaymentMethodsEmpty_whenVisitHasNoPayments() {
        Visit visit = visitWithPayments(List.of());

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getPaymentMethods()).isEmpty();
        assertThat(draft.getSaleDate()).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void create_persistsSaleDatePaymentMethodsAndNotes() {
        Client client = client();
        NamedInvoiceCreateDTO request = request(7L);
        request.setSaleDate(LocalDate.of(2026, 9, 8));
        request.setPaymentMethods(new LinkedHashSet<>(List.of(PaymentMethod.BLIK, PaymentMethod.CASH)));
        request.setNotes("  Płatność z góry  ");

        when(namedInvoiceRepo.findFirstBySourceVisitIdOrderByIdDesc(7L)).thenReturn(Optional.empty());
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(namedInvoiceRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            namedInvoiceService.create(request);
        }

        ArgumentCaptor<NamedInvoice> captor = ArgumentCaptor.forClass(NamedInvoice.class);
        verify(namedInvoiceRepo).save(captor.capture());

        NamedInvoice saved = captor.getValue();
        assertThat(saved.getSaleDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(saved.getPaymentMethods()).containsExactly(PaymentMethod.BLIK, PaymentMethod.CASH);
        assertThat(saved.getNotes()).isEqualTo("Płatność z góry");
    }

    @Test
    void update_persistsSaleDatePaymentMethodsAndNotes() {
        NamedInvoice existing = NamedInvoice.builder()
                .id(100L)
                .invoiceNumber("5/09/2026")
                .sequenceInMonth(5)
                .year(2026)
                .month(9)
                .issueDate(LocalDate.of(2026, 9, 9))
                .sourceVisitId(7L)
                .buyerName("Anna Kowalska")
                .client(client())
                .items(new ArrayList<>())
                .build();

        NamedInvoiceCreateDTO request = request(7L);
        request.setSaleDate(LocalDate.of(2026, 9, 7));
        request.setPaymentMethods(new LinkedHashSet<>(List.of(PaymentMethod.TRANSFER, PaymentMethod.CARD)));
        request.setNotes("Korekta opisu");

        when(namedInvoiceRepo.findByIdWithItems(100L)).thenReturn(Optional.of(existing));
        when(namedInvoiceRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NamedInvoiceDTO result = namedInvoiceService.update(100L, request);

        assertThat(existing.getSaleDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(existing.getPaymentMethods()).containsExactly(PaymentMethod.TRANSFER, PaymentMethod.CARD);
        assertThat(existing.getNotes()).isEqualTo("Korekta opisu");
        assertThat(result.getSaleDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(result.getPaymentMethods()).containsExactly(PaymentMethod.TRANSFER, PaymentMethod.CARD);
        assertThat(result.getNotes()).isEqualTo("Korekta opisu");
    }

    @Test
    void update_clearsNotes_whenBlankNotesArrive() {
        NamedInvoice existing = NamedInvoice.builder()
                .id(100L)
                .invoiceNumber("5/09/2026")
                .sequenceInMonth(5)
                .year(2026)
                .month(9)
                .issueDate(LocalDate.of(2026, 9, 9))
                .sourceVisitId(7L)
                .buyerName("Anna Kowalska")
                .notes("Stara uwaga")
                .client(client())
                .items(new ArrayList<>())
                .build();

        NamedInvoiceCreateDTO request = request(7L);
        request.setNotes("   ");

        when(namedInvoiceRepo.findByIdWithItems(100L)).thenReturn(Optional.of(existing));
        when(namedInvoiceRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        namedInvoiceService.update(100L, request);

        assertThat(existing.getNotes()).isNull();
    }

    private SaleItem voucherItem(String name, String price) {
        return SaleItem.builder()
                .name(name)
                .voucher(Voucher.builder().id(3L).build())
                .price(new BigDecimal(price))
                .build();
    }

    private SaleItem productItem(String name, String net, String vat) {
        return SaleItem.builder()
                .name(name)
                .product(Product.builder().id(9L).vatRate(VatRate.VAT_23).build())
                .netValue(new BigDecimal(net))
                .vatValue(new BigDecimal(vat))
                .price(new BigDecimal(net).add(new BigDecimal(vat)))
                .build();
    }

    private Visit visitWithSale(List<VisitItem> visitItems, List<SaleItem> saleItems) {
        return Visit.builder()
                .id(7L)
                .client(client())
                .date(LocalDate.of(2026, 9, 8))
                .items(new ArrayList<>(visitItems))
                .payments(new ArrayList<>())
                .sale(Sale.builder().id(11L).items(new ArrayList<>(saleItems)).build())
                .build();
    }

    private void stubDraftNumbering() {
        when(namedInvoiceRepo.findMaxSequenceInMonth(2026, 9)).thenReturn(4);
        when(issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(any(), any())).thenReturn(List.of());
    }

    @Test
    void buildDraft_includesVoucherLine_withServiceVatAndGrossFromPrice() {
        Visit visit = visitWithSale(List.of(), List.of(voucherItem("Voucher 200 zł", "200.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        stubDraftNumbering();

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getItems()).hasSize(1);
        NamedInvoiceItemDTO line = draft.getItems().get(0);
        assertThat(line.getSourceType()).isEqualTo("VOUCHER");
        assertThat(line.getName()).isEqualTo("Voucher 200 zł");
        assertThat(line.getVatRate()).isEqualTo(VatRate.VAT_8);
        assertThat(line.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(line.getGrossValue()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(line.getGrossValue().subtract(line.getNetValue()))
                .isEqualByComparingTo(new BigDecimal("14.81"));
    }

    @Test
    void buildDraft_includesBothProductAndVoucherLines() {
        Visit visit = visitWithSale(List.of(), List.of(
                productItem("Krem", "100.00", "23.00"),
                voucherItem("Voucher 150 zł", "150.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        stubDraftNumbering();

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getItems()).hasSize(2);
        assertThat(draft.getItems()).extracting(NamedInvoiceItemDTO::getSourceType)
                .containsExactly("PRODUCT", "VOUCHER");
    }

    @Test
    void buildDraft_skipsSaleItemWithoutProductAndVoucher() {
        SaleItem orphan = SaleItem.builder().name("Usunięty produkt").price(new BigDecimal("50.00")).build();
        Visit visit = visitWithSale(List.of(), List.of(orphan, voucherItem("Voucher 100 zł", "100.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        stubDraftNumbering();

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        assertThat(draft.getItems()).hasSize(1);
        assertThat(draft.getItems().get(0).getSourceType()).isEqualTo("VOUCHER");
    }

    @Test
    void buildDraft_grossSumMatchesVisitItemsAndSaleItems() {
        VisitItem visitItem = VisitItem.builder()
                .id(1L).name("Pedicure").price(new BigDecimal("180.00"))
                .finalPrice(new BigDecimal("180.00")).duration(60).boostItem(false).build();
        Visit visit = visitWithSale(List.of(visitItem), List.of(
                productItem("Krem", "100.00", "23.00"),
                voucherItem("Voucher 150 zł", "150.00")));

        when(visitRepo.findOneById(7L)).thenReturn(Optional.of(visit));
        stubDraftNumbering();

        NamedInvoiceDraftDTO draft = namedInvoiceService.buildDraft(7L);

        BigDecimal sum = draft.getItems().stream()
                .map(NamedInvoiceItemDTO::getGrossValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo(new BigDecimal("453.00"));
    }
}
