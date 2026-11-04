package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.SendNamedInvoiceRequest;
import com.clinic.clinicmanager.exceptions.EmailSendException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.NamedInvoiceRepo;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.impl.InvoiceEmailHtmlBuilder;
import com.clinic.clinicmanager.service.impl.NamedInvoiceEmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamedInvoiceEmailServiceImplTest {

    private static final Long INVOICE_ID = 5L;
    private static final String EMAIL = "klient@example.com";

    @Mock NamedInvoiceRepo namedInvoiceRepo;
    @Mock ClientRepo clientRepo;
    @Mock StatSettingsRepo statSettingsRepo;
    @Mock NamedInvoicePdfService namedInvoicePdfService;
    @Mock JavaMailSender mailSender;
    @Mock AuditLogService auditLogService;

    private InvoiceEmailHtmlBuilder htmlBuilder;
    private NamedInvoiceEmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        htmlBuilder = new InvoiceEmailHtmlBuilder();
        ReflectionTestUtils.setField(htmlBuilder, "assetBaseUrl", "https://clinic.example.com");

        emailService = new NamedInvoiceEmailServiceImpl(namedInvoiceRepo, clientRepo, statSettingsRepo,
                namedInvoicePdfService, mailSender, auditLogService, htmlBuilder);
        ReflectionTestUtils.setField(emailService, "from", "gabinet@clinic.example.com");

        when(mailSender.createMimeMessage())
                .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        when(namedInvoicePdfService.generateInvoicePdf(anyLong())).thenReturn("pdf".getBytes(StandardCharsets.UTF_8));
        when(namedInvoiceRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.of(invoice(null)));
        when(statSettingsRepo.getSettings()).thenReturn(settingsWithFooter());
    }

    private NamedInvoice invoice(Client client) {
        return NamedInvoice.builder()
                .id(INVOICE_ID)
                .invoiceNumber("1/02/2026")
                .issueDate(LocalDate.of(2026, 2, 1))
                .client(client)
                .build();
    }

    private Client client(String email) {
        return Client.builder()
                .id(11L)
                .firstName("Anna")
                .lastName("Nowak")
                .email(email)
                .build();
    }

    private StatSettings settingsWithFooter() {
        StatSettings settings = StatSettings.builder().id(1L).build();
        settings.setFooterCompanyName("ClinicManager Maria Kowalska");
        settings.setFooterAddress("ul. Testowa 12/3");
        settings.setFooterPostalCode("61-001");
        settings.setFooterCity("Lublin");
        settings.setCompanyPhone("+48 61 000 00 00");
        return settings;
    }

    private SendNamedInvoiceRequest request(String email, boolean updateClientEmail) {
        SendNamedInvoiceRequest request = new SendNamedInvoiceRequest();
        request.setEmail(email);
        request.setUpdateClientEmail(updateClientEmail);
        return request;
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private String rawOf(MimeMessage message) throws Exception {
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private int countOf(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    @Test
    void send_throwsValidationException_whenEmailIsNull() {
        assertThrows(ValidationException.class, () -> emailService.send(INVOICE_ID, request(null, false)));

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(namedInvoiceRepo, never()).markSent(anyLong(), anyString(), any());
    }

    @Test
    void send_throwsValidationException_whenRequestIsNull() {
        assertThrows(ValidationException.class, () -> emailService.send(INVOICE_ID, null));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void send_throwsValidationException_whenEmailIsMalformed() {
        assertThrows(ValidationException.class, () -> emailService.send(INVOICE_ID, request("anna@localhost", false)));
        assertThrows(ValidationException.class, () -> emailService.send(INVOICE_ID, request("anna(at)example.com", false)));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void send_throwsResourceNotFoundException_whenInvoiceDoesNotExist() {
        when(namedInvoiceRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> emailService.send(INVOICE_ID, request(EMAIL, false)));

        verify(namedInvoicePdfService, never()).generateInvoicePdf(anyLong());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void send_marksInvoiceAsSent_andReturnsRefreshedDTO() {
        NamedInvoiceDTO result = emailService.send(INVOICE_ID, request(EMAIL, false));

        assertNotNull(result);
        assertEquals(INVOICE_ID, result.getId());
        verify(mailSender).send(any(MimeMessage.class));
        verify(namedInvoiceRepo).markSent(eq(INVOICE_ID), eq(EMAIL), any(LocalDateTime.class));
    }

    @Test
    void send_trimsEmailBeforeSending() {
        emailService.send(INVOICE_ID, request("  " + EMAIL + "  ", false));

        verify(namedInvoiceRepo).markSent(eq(INVOICE_ID), eq(EMAIL), any(LocalDateTime.class));
    }

    @Test
    void send_writesSendAuditWithMaskedRecipient() {
        emailService.send(INVOICE_ID, request(EMAIL, false));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logSend(eq("NamedInvoice"), eq(INVOICE_ID), eq("1/02/2026"), captor.capture());

        String audit = captor.getValue().toString();
        assertTrue(audit.contains("k***@example.com"));
        assertFalse(audit.contains("klient@example.com"));
    }

    @Test
    void send_readsStatSettingsOnlyOnce() {
        emailService.send(INVOICE_ID, request(EMAIL, false));

        verify(statSettingsRepo, times(1)).getSettings();
    }

    @Test
    void send_addsBcc_whenConfigured() throws Exception {
        StatSettings settings = settingsWithFooter();
        settings.setInvoiceEmailBcc("kopia@clinic.example.com");
        when(statSettingsRepo.getSettings()).thenReturn(settings);

        emailService.send(INVOICE_ID, request(EMAIL, false));

        assertTrue(rawOf(sentMessage()).contains("kopia@clinic.example.com"));
    }

    @Test
    void send_skipsBcc_whenBlank() throws Exception {
        StatSettings settings = settingsWithFooter();
        settings.setInvoiceEmailBcc("   ");
        when(statSettingsRepo.getSettings()).thenReturn(settings);

        emailService.send(INVOICE_ID, request(EMAIL, false));

        assertFalse(rawOf(sentMessage()).toLowerCase().contains("bcc:"));
    }

    @Test
    void send_attachesExactlyOnePdf_andNoInlineParts() throws Exception {
        emailService.send(INVOICE_ID, request(EMAIL, false));

        String raw = rawOf(sentMessage());

        assertEquals(1, countOf(raw, "Content-Disposition: attachment"));
        assertTrue(raw.contains("application/pdf"));
        assertFalse(raw.contains("Content-ID"));
        assertFalse(raw.contains("Content-Disposition: inline"));
    }

    @Test
    void send_buildsMultipartAlternative_whenFooterIsConfigured() throws Exception {
        emailService.send(INVOICE_ID, request(EMAIL, false));

        String raw = rawOf(sentMessage());

        assertTrue(raw.contains("multipart/alternative"));
        assertTrue(raw.contains("text/html"));
    }

    @Test
    void send_sendsPlainTextOnly_whenFooterCompanyNameIsMissing() throws Exception {
        when(statSettingsRepo.getSettings()).thenReturn(StatSettings.builder().id(1L).build());

        emailService.send(INVOICE_ID, request(EMAIL, false));

        String raw = rawOf(sentMessage());

        assertFalse(raw.contains("text/html"));
        assertEquals(1, countOf(raw, "Content-Disposition: attachment"));
    }

    @Test
    void send_usesFixedSubjectAndDatedAttachmentName() throws Exception {
        emailService.send(INVOICE_ID, request(EMAIL, false));

        String raw = rawOf(sentMessage());

        assertTrue(raw.contains("FV ClinicManager"));
        assertTrue(raw.contains("01.02.2026.pdf"));
    }

    @Test
    void send_updatesClientEmail_whenRequestedAndDifferent() {
        Client client = client("stary@example.com");
        when(namedInvoiceRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.of(invoice(client)));

        emailService.send(INVOICE_ID, request(EMAIL, true));

        assertEquals(EMAIL, client.getEmail());
        verify(clientRepo).save(client);
        verify(auditLogService).logUpdate(eq("Client"), eq(11L), eq("Anna Nowak"), any(), any());
    }

    @Test
    void send_setsClientEmail_whenClientHadNone() {
        Client client = client(null);
        when(namedInvoiceRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.of(invoice(client)));

        emailService.send(INVOICE_ID, request(EMAIL, true));

        assertEquals(EMAIL, client.getEmail());
        verify(clientRepo).save(client);
    }

    @Test
    void send_doesNotUpdateClientEmail_whenItOnlyDiffersByCase() {
        Client client = client("KLIENT@Example.com");
        when(namedInvoiceRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.of(invoice(client)));

        emailService.send(INVOICE_ID, request(EMAIL, true));

        verify(clientRepo, never()).save(any());
    }

    @Test
    void send_doesNotUpdateClientEmail_whenNotRequested() {
        Client client = client("stary@example.com");
        when(namedInvoiceRepo.findByIdWithItems(INVOICE_ID)).thenReturn(Optional.of(invoice(client)));

        emailService.send(INVOICE_ID, request(EMAIL, false));

        assertEquals("stary@example.com", client.getEmail());
        verify(clientRepo, never()).save(any());
    }

    @Test
    void send_wrapsAuthenticationFailure_andDoesNotMarkInvoiceAsSent() {
        doThrow(new MailAuthenticationException("bad credentials")).when(mailSender).send(any(MimeMessage.class));

        EmailSendException exception = assertThrows(EmailSendException.class,
                () -> emailService.send(INVOICE_ID, request(EMAIL, false)));

        assertTrue(exception.getMessage().contains("uwierzytelnienia"));
        verify(namedInvoiceRepo, never()).markSent(anyLong(), anyString(), any());
        verify(auditLogService, never()).logSend(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void send_wrapsSendFailure_andDoesNotMarkInvoiceAsSent() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        EmailSendException exception = assertThrows(EmailSendException.class,
                () -> emailService.send(INVOICE_ID, request(EMAIL, false)));

        assertTrue(exception.getMessage().contains("Nie udało się wysłać faktury"));
        verify(namedInvoiceRepo, never()).markSent(anyLong(), anyString(), any());
    }

    @Test
    void send_wrapsUnexpectedFailure() {
        doThrow(new IllegalStateException("boom")).when(mailSender).send(any(MimeMessage.class));

        EmailSendException exception = assertThrows(EmailSendException.class,
                () -> emailService.send(INVOICE_ID, request(EMAIL, false)));

        assertTrue(exception.getMessage().contains("Spróbuj ponownie"));
        verify(namedInvoiceRepo, never()).markSent(anyLong(), anyString(), any());
    }
}
