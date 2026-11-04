package com.clinic.clinicmanager.service.impl;

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
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.NamedInvoiceEmailService;
import com.clinic.clinicmanager.service.NamedInvoicePdfService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamedInvoiceEmailServiceImpl implements NamedInvoiceEmailService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String SUBJECT = "FV ClinicManager";
    private static final String BODY = """
            Dzień dobry,

            przesyłamy fakturę za ostatnią wizytę w ClinicManager.

            Pozdrawiamy,
            Gabinet ClinicManager
            """;

    private final NamedInvoiceRepo namedInvoiceRepo;
    private final ClientRepo clientRepo;
    private final StatSettingsRepo statSettingsRepo;
    private final NamedInvoicePdfService namedInvoicePdfService;
    private final JavaMailSender mailSender;
    private final AuditLogService auditLogService;
    private final InvoiceEmailHtmlBuilder htmlBuilder;

    @Value("${app.mail.from:}")
    private String from;

    @Override
    @Transactional
    public NamedInvoiceDTO send(Long invoiceId, SendNamedInvoiceRequest request) {
        String email = validateEmail(request != null ? request.getEmail() : null);

        NamedInvoice invoice = namedInvoiceRepo.findByIdWithItems(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("NamedInvoice not found with given id: " + invoiceId));

        StatSettings settings = statSettingsRepo.getSettings();
        String bcc = trimToNull(settings.getInvoiceEmailBcc());
        boolean updateClientEmail = request != null && request.isUpdateClientEmail();

        log.info("Sending named invoice {} (id={}) to {}, bcc={}, updateClientEmail={}",
                invoice.getInvoiceNumber(), invoiceId, maskEmail(email), bcc != null, updateClientEmail);
        if (bcc == null) {
            log.warn("invoiceEmailBcc is not configured - no copy of invoice {} will be sent",
                    invoice.getInvoiceNumber());
        }

        byte[] pdf = namedInvoicePdfService.generateInvoicePdf(invoiceId);
        log.debug("Generated PDF for named invoice {} - {} bytes", invoice.getInvoiceNumber(), pdf.length);

        long startedAt = System.currentTimeMillis();
        deliver(invoice, email, bcc, pdf, settings);
        log.info("Named invoice {} sent to {} in {} ms",
                invoice.getInvoiceNumber(), maskEmail(email), System.currentTimeMillis() - startedAt);

        Client client = invoice.getClient();
        if (updateClientEmail && client != null && !email.equalsIgnoreCase(orEmpty(client.getEmail()))) {
            String oldEmail = client.getEmail();
            client.setEmail(email);
            clientRepo.save(client);
            auditLogService.logUpdate("Client", client.getId(),
                    client.getFirstName() + " " + client.getLastName(),
                    emailSnapshot(oldEmail), emailSnapshot(email));
            log.info("Updated email of client id={} from {} to {}",
                    client.getId(), maskEmail(oldEmail), maskEmail(email));
        }

        LocalDateTime sentAt = LocalDateTime.now();
        namedInvoiceRepo.markSent(invoiceId, email, sentAt);

        auditLogService.logSend(
                "NamedInvoice",
                invoiceId,
                invoice.getInvoiceNumber(),
                Map.of(
                        "sentTo", maskEmail(email),
                        "emailSentAt", sentAt.toString(),
                        "bccUsed", bcc != null
                )
        );

        NamedInvoice refreshed = namedInvoiceRepo.findByIdWithItems(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("NamedInvoice not found with given id: " + invoiceId));
        return new NamedInvoiceDTO(refreshed, true);
    }

    private void deliver(NamedInvoice invoice, String email, String bcc, byte[] pdf, StatSettings settings) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(email);
            if (bcc != null) {
                helper.setBcc(bcc);
            }
            helper.setSubject(SUBJECT);

            InvoiceEmailHtmlBuilder.EmailHtml content = htmlBuilder.build(BODY, settings);
            if (content != null) {
                helper.setText(BODY, content.html());
            } else {
                helper.setText(BODY, false);
            }

            helper.addAttachment(attachmentName(invoice.getIssueDate()), new ByteArrayResource(pdf),
                    "application/pdf");

            mailSender.send(message);
        } catch (MailAuthenticationException e) {
            logFailure(invoice, email, e);
            throw new EmailSendException("Błąd uwierzytelnienia serwera poczty. Sprawdź konfigurację.", e);
        } catch (MailSendException e) {
            logFailure(invoice, email, e);
            throw new EmailSendException("Nie udało się wysłać faktury: " + rootMessage(e) + ".", e);
        } catch (Exception e) {
            logFailure(invoice, email, e);
            throw new EmailSendException("Nie udało się wysłać faktury. Spróbuj ponownie.", e);
        }
    }

    private void logFailure(NamedInvoice invoice, String email, Exception e) {
        log.error("Failed to send named invoice {} to {} - {}: {}",
                invoice.getInvoiceNumber(), maskEmail(email), e.getClass().getSimpleName(), e.getMessage(), e);
    }

    private String attachmentName(LocalDate issueDate) {
        LocalDate date = issueDate != null ? issueDate : LocalDate.now();
        return "FV ClinicManager " + FILE_DATE.format(date) + ".pdf";
    }

    private String validateEmail(String value) {
        String email = trimToNull(value);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Niepoprawny adres email.");
        }
        return email;
    }

    private Map<String, String> emailSnapshot(String email) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("email", maskEmail(email));
        return snapshot;
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
