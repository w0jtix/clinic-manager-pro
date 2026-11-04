package com.clinic.clinicmanager.service.impl;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.NamedInvoiceItem;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.NamedInvoiceRepo;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.NamedInvoicePdfService;
import com.clinic.clinicmanager.utils.AmountInWordsUtils;
import com.clinic.clinicmanager.utils.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamedInvoicePdfServiceImpl implements NamedInvoicePdfService {

    private final NamedInvoiceRepo namedInvoiceRepo;
    private final StatSettingsRepo statSettingsRepo;

    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DASH = "-";
    private static final String CURRENCY = " PLN";
    private static final BigDecimal AMOUNT_DUE = BigDecimal.ZERO;
    private static final String PAID_STATUS = "Opłacona";
    private static final float LOGO_MAX_WIDTH = 150f;
    private static final float LOGO_MAX_HEIGHT = 75f;

    private static final Color LINE_COLOR = new Color(196, 196, 196);
    private static final Color HEADER_BG_COLOR = new Color(240, 240, 240);
    private static final Color BORDER_COLOR = new Color(200, 200, 200);

    private static final float BORDER_WIDTH = 0.5f;
    private static final int NAME_COLUMN = 1;
    private static final float FOOTER_ROW_PADDING = 9f;
    private static final float FOOTER_TIGHT_PADDING = 1f;

    private static final Font SECTION_FONT;
    private static final Font LABEL_FONT;
    private static final Font VALUE_FONT;
    private static final Font TABLE_HEADER_FONT;
    private static final Font CELL_FONT;
    private static final Font CELL_BOLD_FONT;
    private static final Font SMALL_FONT;

    static {
        try {
            BaseFont regularBase = BaseFont.createFont(
                    new ClassPathResource("fonts/LiberationSans-Regular.ttf").getURL().toString(),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BaseFont boldBase = BaseFont.createFont(
                    new ClassPathResource("fonts/LiberationSans-Bold.ttf").getURL().toString(),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            SECTION_FONT = new Font(boldBase, 9);
            LABEL_FONT = new Font(boldBase, 8);
            VALUE_FONT = new Font(regularBase, 8);
            TABLE_HEADER_FONT = new Font(boldBase, 7);
            CELL_FONT = new Font(regularBase, 7);
            CELL_BOLD_FONT = new Font(boldBase, 7);
            SMALL_FONT = new Font(regularBase, 6);
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Failed to load fonts from classpath", e);
        }
    }

    record VatSummaryRow(VatRate rate, BigDecimal net, BigDecimal vat, BigDecimal gross) {
    }

    record FooterRow(String label, String value) {
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long namedInvoiceId) {
        NamedInvoice invoice = namedInvoiceRepo.findByIdWithItems(namedInvoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "NamedInvoice not found with given id: " + namedInvoiceId));
        StatSettings company = statSettingsRepo.getSettings();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40f, 40f, 40f, 40f);

        try {
            PdfWriter.getInstance(document, outputStream);

            document.open();
            addHeader(document, invoice, company);
            addSeparator(document, 14f, 18f);
            addParties(document, company, invoice);
            addItemsTable(document, invoice);
            addSummary(document, invoice);
            addSeparator(document, 2f, 5f);
            addFooter(document, invoice);
        } catch (DocumentException e) {
            throw new RuntimeException("Error while generating PDF file: " + e.getMessage(), e);
        } finally {
            document.close();
        }

        return outputStream.toByteArray();
    }

    private void addHeader(Document document, NamedInvoice invoice, StatSettings company) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{70f, 30f});

        PdfPCell detailsCell = new PdfPCell();
        detailsCell.setBorder(Rectangle.NO_BORDER);
        detailsCell.setPadding(0f);

        Paragraph title = labelValue("Faktura numer ", nullToDash(invoice.getInvoiceNumber()));
        title.setSpacingAfter(14f);
        detailsCell.addElement(title);

        detailsCell.addElement(labelValue("Data wystawienia: ", formatDate(invoice.getIssueDate())));
        detailsCell.addElement(labelValue("Data sprzedaży: ", formatDate(invoice.getSaleDate())));
        detailsCell.addElement(labelValue(paymentLabel(invoice.getPaymentMethods()),
                paymentValue(invoice.getPaymentMethods())));

        Paragraph status = labelValue("Status: ", PAID_STATUS);
        status.setSpacingAfter(10f);
        detailsCell.addElement(status);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(0f);
        logoCell.setVerticalAlignment(Element.ALIGN_TOP);
        logoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Image logo = companyLogo(company);
        if (logo != null) {
            logo.setAlignment(Element.ALIGN_RIGHT);
            logoCell.addElement(logo);
        }

        table.addCell(detailsCell);
        table.addCell(logoCell);
        document.add(table);
    }

    private Image companyLogo(StatSettings company) {
        byte[] bytes = company.getCompanyLogo();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Image image = Image.getInstance(bytes);
            image.scaleToFit(LOGO_MAX_WIDTH, LOGO_MAX_HEIGHT);
            return image;
        } catch (Exception e) {
            log.warn("Skipping company logo on invoice PDF - image could not be decoded: {}", e.getMessage());
            return null;
        }
    }

    private Paragraph labelValue(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label, LABEL_FONT));
        paragraph.add(new Chunk(value, VALUE_FONT));
        paragraph.setLeading(13f);
        return paragraph;
    }

    private void addSeparator(Document document, float spacingBefore, float spacingAfter) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(spacingBefore);
        table.setSpacingAfter(spacingAfter);

        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setFixedHeight(12f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(LINE_COLOR);
        cell.setBorderWidthBottom(0.7f);
        cell.setPadding(0f);
        table.addCell(cell);

        document.add(table);
    }

    private void addParties(Document document, StatSettings company, NamedInvoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(16f);

        table.addCell(partyCell("Sprzedawca", List.of(
                orEmpty(company.getCompanyName()),
                company.getCompanyNip() != null ? "NIP: " + company.getCompanyNip() : "",
                orEmpty(company.getCompanyAddress()),
                joinPostalCity(company.getCompanyPostalCode(), company.getCompanyCity()))));

        table.addCell(partyCell("Nabywca", List.of(
                orEmpty(invoice.getBuyerName()),
                orEmpty(invoice.getBuyerAddress()),
                joinPostalCity(invoice.getBuyerPostalCode(), invoice.getBuyerCity()))));

        document.add(table);
    }

    private PdfPCell partyCell(String heading, List<String> lines) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingRight(12f);

        Paragraph headingParagraph = new Paragraph(heading, SECTION_FONT);
        headingParagraph.setSpacingAfter(4f);
        cell.addElement(headingParagraph);

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Paragraph paragraph = new Paragraph(line, VALUE_FONT);
            paragraph.setLeading(12f);
            cell.addElement(paragraph);
        }
        return cell;
    }

    private void addItemsTable(Document document, NamedInvoice invoice) throws DocumentException {
        List<NamedInvoiceItem> items = invoice.getItems() != null ? invoice.getItems() : List.of();

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{23f, 174f, 49f, 50f, 61f, 36f, 58f, 64f});
        table.setSpacingBefore(4f);

        String[] headers = {"LP", "Nazwa towaru / usługi", "Ilość (j.m.)", "Cena netto",
                "Wartość netto", "VAT %", "Wartość VAT", "Wartość brutto"};
        for (int column = 0; column < headers.length; column++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[column], TABLE_HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG_COLOR);
            cell.setBorderColor(BORDER_COLOR);
            cell.setBorderWidth(BORDER_WIDTH);
            cell.setHorizontalAlignment(column == NAME_COLUMN ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setNoWrap(true);
            cell.setPadding(5f);
            table.addCell(cell);
        }

        int rowNumber = 1;
        for (NamedInvoiceItem item : items) {
            table.addCell(bodyCell(String.valueOf(rowNumber++), Element.ALIGN_CENTER, false));
            table.addCell(bodyCell(item.getName(), Element.ALIGN_LEFT, false));
            table.addCell(bodyCell(quantityLabel(item.getQuantity()), Element.ALIGN_RIGHT, false));
            table.addCell(bodyCell(formatMoney(item.getUnitNetPrice()), Element.ALIGN_RIGHT, false));
            table.addCell(bodyCell(formatMoney(item.getNetValue()), Element.ALIGN_RIGHT, false));
            table.addCell(bodyCell(vatLabel(item.getVatRate()), Element.ALIGN_CENTER, false));
            table.addCell(bodyCell(formatMoney(itemVat(item)), Element.ALIGN_RIGHT, false));
            table.addCell(bodyCell(formatMoney(item.getGrossValue()), Element.ALIGN_RIGHT, false));
        }

        for (VatSummaryRow row : vatSummaryRows(items)) {
            addSummaryRow(table, "W tym", vatLabel(row.rate()), row, false);
        }
        addSummaryRow(table, "Razem", "", totalsRow(items), true);

        document.add(table);
    }

    private void addSummaryRow(PdfPTable table, String label, String vatLabel, VatSummaryRow row, boolean bold) {
        table.addCell(bodyCell("", Element.ALIGN_CENTER, bold));
        table.addCell(bodyCell("", Element.ALIGN_LEFT, bold));
        table.addCell(bodyCell("", Element.ALIGN_RIGHT, bold));
        table.addCell(bodyCell(label, Element.ALIGN_RIGHT, bold));
        table.addCell(bodyCell(formatMoney(row.net()), Element.ALIGN_RIGHT, bold));
        table.addCell(bodyCell(vatLabel, Element.ALIGN_CENTER, bold));
        table.addCell(bodyCell(formatMoney(row.vat()), Element.ALIGN_RIGHT, bold));
        table.addCell(bodyCell(formatMoney(row.gross()), Element.ALIGN_RIGHT, bold));
    }

    static List<VatSummaryRow> vatSummaryRows(List<NamedInvoiceItem> items) {
        Map<VatRate, BigDecimal[]> byRate = new LinkedHashMap<>();
        for (NamedInvoiceItem item : items) {
            BigDecimal[] sums = byRate.computeIfAbsent(item.getVatRate(),
                    rate -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(MoneyUtils.money(item.getNetValue()));
            sums[1] = sums[1].add(itemVat(item));
            sums[2] = sums[2].add(MoneyUtils.money(item.getGrossValue()));
        }

        List<VatSummaryRow> rows = new ArrayList<>();
        for (Map.Entry<VatRate, BigDecimal[]> entry : byRate.entrySet()) {
            BigDecimal[] sums = entry.getValue();
            rows.add(new VatSummaryRow(entry.getKey(), sums[0], sums[1], sums[2]));
        }
        return rows;
    }

    static VatSummaryRow totalsRow(List<NamedInvoiceItem> items) {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        for (NamedInvoiceItem item : items) {
            net = net.add(MoneyUtils.money(item.getNetValue()));
            vat = vat.add(itemVat(item));
            gross = gross.add(MoneyUtils.money(item.getGrossValue()));
        }
        return new VatSummaryRow(null, net, vat, gross);
    }

    private static BigDecimal itemVat(NamedInvoiceItem item) {
        return MoneyUtils.money(item.getGrossValue()).subtract(MoneyUtils.money(item.getNetValue()));
    }

    private void addSummary(Document document, NamedInvoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(42);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(14f);
        table.setSpacingAfter(4f);

        addTotalsLine(table, "Wartość netto", invoice.getNetAmount());
        addTotalsLine(table, "Wartość VAT", invoice.getVatAmount());
        addTotalsLine(table, "Wartość brutto", invoice.getGrossAmount());

        document.add(table);
    }

    private void addTotalsLine(PdfPTable table, String label, BigDecimal value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(3f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatMoney(value) + CURRENCY, VALUE_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(3f);
        table.addCell(valueCell);
    }

    private void addFooter(Document document, NamedInvoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{19f, 81f});

        List<FooterRow> rows = footerRows(invoice);
        for (int index = 0; index < rows.size(); index++) {
            boolean divider = index < rows.size() - 1;
            float paddingBottom = divider ? FOOTER_ROW_PADDING : FOOTER_TIGHT_PADDING;
            table.addCell(footerCell(rows.get(index).label(), LABEL_FONT, divider,
                    FOOTER_ROW_PADDING, paddingBottom));
            table.addCell(footerCell(rows.get(index).value(), VALUE_FONT, divider,
                    FOOTER_ROW_PADDING, paddingBottom));
        }

        table.addCell(footerCell("", SMALL_FONT, false, 0f, 0f));
        table.addCell(footerCell(amountDueInWords(), SMALL_FONT, false, 0f, 0f));

        document.add(table);
    }

    static String amountDueInWords() {
        return "Słownie: " + AmountInWordsUtils.amountInWords(AMOUNT_DUE);
    }

    static List<FooterRow> footerRows(NamedInvoice invoice) {
        List<FooterRow> rows = new ArrayList<>();
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            rows.add(new FooterRow("Uwagi:", invoice.getNotes()));
        }
        rows.add(new FooterRow("Kwota opłacona:", formatMoney(invoice.getGrossAmount()) + CURRENCY));
        rows.add(new FooterRow("Do zapłaty:", formatMoney(AMOUNT_DUE) + CURRENCY));
        return rows;
    }

    private PdfPCell footerCell(String text, Font font, boolean divider,
                                float paddingTop, float paddingBottom) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        if (divider) {
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColorBottom(LINE_COLOR);
            cell.setBorderWidthBottom(0.7f);
        } else {
            cell.setBorder(Rectangle.NO_BORDER);
        }
        cell.setPaddingTop(paddingTop);
        cell.setPaddingBottom(paddingBottom);
        return cell;
    }

    private PdfPCell bodyCell(String text, int alignment, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", bold ? CELL_BOLD_FONT : CELL_FONT));
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(BORDER_WIDTH);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4f);
        return cell;
    }

    static String formatMoney(BigDecimal value) {
        return String.format(PL, "%.2f", MoneyUtils.money(value));
    }

    static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : DASH;
    }

    static String paymentLabel(Set<PaymentMethod> methods) {
        return methods != null && methods.size() > 1 ? "Płatność dzielona: " : "Płatność: ";
    }

    static String paymentValue(Set<PaymentMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            return DASH;
        }
        return methods.stream()
                .map(NamedInvoicePdfServiceImpl::paymentMethodLabel)
                .collect(Collectors.joining(", "));
    }

    static String paymentMethodLabel(PaymentMethod method) {
        if (method == null) {
            return DASH;
        }
        return switch (method) {
            case CASH -> "Gotówka";
            case CARD -> "Karta płatnicza";
            case BLIK -> "Blik";
            case TRANSFER -> "Przelew";
            case VOUCHER -> "Voucher";
        };
    }

    static String vatLabel(VatRate vatRate) {
        if (vatRate == null) {
            return DASH;
        }
        if (vatRate.isNumeric()) {
            return String.valueOf((int) vatRate.getRate());
        }
        return vatRate == VatRate.VAT_ZW ? "zw" : "np";
    }

    static String quantityLabel(BigDecimal quantity) {
        return MoneyUtils.nz(quantity).stripTrailingZeros().toPlainString() + " (brak)";
    }

    private String joinPostalCity(String postalCode, String city) {
        String left = postalCode != null ? postalCode.trim() : "";
        String right = city != null ? city.trim() : "";
        return (left + " " + right).trim();
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : DASH;
    }
}
