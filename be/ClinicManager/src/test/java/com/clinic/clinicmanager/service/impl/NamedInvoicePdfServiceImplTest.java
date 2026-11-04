package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.NamedInvoiceItem;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.NamedInvoiceRepo;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.impl.NamedInvoicePdfServiceImpl.FooterRow;
import com.clinic.clinicmanager.service.impl.NamedInvoicePdfServiceImpl.VatSummaryRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamedInvoicePdfServiceImplTest {

    @Mock NamedInvoiceRepo namedInvoiceRepo;
    @Mock StatSettingsRepo statSettingsRepo;

    @InjectMocks NamedInvoicePdfServiceImpl pdfService;

    private NamedInvoiceItem item(String name, String net, String gross, VatRate rate) {
        return NamedInvoiceItem.builder()
                .name(name)
                .quantity(BigDecimal.ONE)
                .unitNetPrice(new BigDecimal(net))
                .netValue(new BigDecimal(net))
                .unitGrossPrice(new BigDecimal(gross))
                .grossValue(new BigDecimal(gross))
                .vatRate(rate)
                .build();
    }

    private NamedInvoice invoice(List<NamedInvoiceItem> items) {
        BigDecimal net = items.stream().map(NamedInvoiceItem::getNetValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = items.stream().map(NamedInvoiceItem::getGrossValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NamedInvoice invoice = NamedInvoice.builder()
                .id(1L)
                .invoiceNumber("5/09/2026")
                .sequenceInMonth(5)
                .year(2026)
                .month(9)
                .issueDate(LocalDate.of(2026, 9, 9))
                .saleDate(LocalDate.of(2026, 9, 8))
                .paymentMethods(new LinkedHashSet<>(List.of(PaymentMethod.CARD)))
                .client(Client.builder().id(1L).firstName("Anna").lastName("Kowalska").build())
                .sourceVisitId(7L)
                .buyerName("Anna Kowalska")
                .buyerAddress("Polna 1")
                .buyerPostalCode("30-100")
                .buyerCity("Kraków")
                .netAmount(net)
                .vatAmount(gross.subtract(net))
                .grossAmount(gross)
                .items(new ArrayList<>(items))
                .build();
        return invoice;
    }

    private StatSettings company() {
        return StatSettings.builder()
                .companyName("ClinicManager")
                .companyNip("1234567890")
                .companyAddress("Kwiatowa 5")
                .companyPostalCode("00-001")
                .companyCity("Warszawa")
                .build();
    }

    @Test
    void vatSummaryRows_createsOneRowPerVatRate() {
        List<NamedInvoiceItem> items = List.of(
                item("Zabieg", "100.00", "108.00", VatRate.VAT_8),
                item("Krem", "100.00", "123.00", VatRate.VAT_23),
                item("Peeling", "50.00", "54.00", VatRate.VAT_8));

        List<VatSummaryRow> rows = NamedInvoicePdfServiceImpl.vatSummaryRows(items);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(VatSummaryRow::rate)
                .containsExactlyInAnyOrder(VatRate.VAT_8, VatRate.VAT_23);

        VatSummaryRow vat8 = rows.stream().filter(row -> row.rate() == VatRate.VAT_8).findFirst().orElseThrow();
        assertThat(vat8.net()).isEqualByComparingTo("150.00");
        assertThat(vat8.vat()).isEqualByComparingTo("12.00");
        assertThat(vat8.gross()).isEqualByComparingTo("162.00");
    }

    @Test
    void vatSummaryRows_createsSingleRow_whenAllItemsShareVatRate() {
        List<NamedInvoiceItem> items = List.of(
                item("Zabieg", "100.00", "108.00", VatRate.VAT_8),
                item("Peeling", "50.00", "54.00", VatRate.VAT_8));

        List<VatSummaryRow> rows = NamedInvoicePdfServiceImpl.vatSummaryRows(items);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().rate()).isEqualTo(VatRate.VAT_8);
    }

    @Test
    void totalsRow_matchesInvoiceAmounts() {
        List<NamedInvoiceItem> items = List.of(
                item("Zabieg", "100.00", "108.00", VatRate.VAT_8),
                item("Krem", "100.00", "123.00", VatRate.VAT_23));
        NamedInvoice invoice = invoice(items);

        VatSummaryRow totals = NamedInvoicePdfServiceImpl.totalsRow(items);

        assertThat(totals.net()).isEqualByComparingTo(invoice.getNetAmount());
        assertThat(totals.vat()).isEqualByComparingTo(invoice.getVatAmount());
        assertThat(totals.gross()).isEqualByComparingTo(invoice.getGrossAmount());
    }

    @Test
    void footerRows_omitsNotesRow_whenNotesAreNull() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        invoice.setNotes(null);

        assertThat(NamedInvoicePdfServiceImpl.footerRows(invoice))
                .extracting(FooterRow::label)
                .containsExactly("Kwota opłacona:", "Do zapłaty:");
    }

    @Test
    void footerRows_omitsNotesRow_whenNotesAreBlank() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        invoice.setNotes("   ");

        assertThat(NamedInvoicePdfServiceImpl.footerRows(invoice))
                .extracting(FooterRow::label)
                .doesNotContain("Uwagi:");
    }

    @Test
    void footerRows_includesNotesRow_whenNotesArePresent() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        invoice.setNotes("Płatność z góry");

        List<FooterRow> rows = NamedInvoicePdfServiceImpl.footerRows(invoice);

        assertThat(rows).extracting(FooterRow::label).startsWith("Uwagi:");
        assertThat(rows.getFirst().value()).isEqualTo("Płatność z góry");
        assertThat(rows).extracting(FooterRow::value).contains("108,00 PLN", "0,00 PLN");
    }

    @Test
    void amountDueInWords_isZero_becauseInvoiceIsAlreadyPaid() {
        assertThat(NamedInvoicePdfServiceImpl.amountDueInWords()).isEqualTo("Słownie: zero PLN zero gr");
    }

    @Test
    void vatLabel_rendersNonNumericRatesAsText() {
        assertThat(NamedInvoicePdfServiceImpl.vatLabel(VatRate.VAT_ZW)).isEqualTo("zw");
        assertThat(NamedInvoicePdfServiceImpl.vatLabel(VatRate.VAT_NP)).isEqualTo("np");
        assertThat(NamedInvoicePdfServiceImpl.vatLabel(VatRate.VAT_8)).isEqualTo("8");
        assertThat(NamedInvoicePdfServiceImpl.vatLabel(VatRate.VAT_23)).isEqualTo("23");
        assertThat(NamedInvoicePdfServiceImpl.vatLabel(null)).isEqualTo("-");
    }

    @Test
    void paymentMethodLabel_rendersPolishNames_andDashForMissingMethod() {
        assertThat(NamedInvoicePdfServiceImpl.paymentMethodLabel(PaymentMethod.CASH)).isEqualTo("Gotówka");
        assertThat(NamedInvoicePdfServiceImpl.paymentMethodLabel(PaymentMethod.CARD)).isEqualTo("Karta płatnicza");
        assertThat(NamedInvoicePdfServiceImpl.paymentMethodLabel(PaymentMethod.BLIK)).isEqualTo("Blik");
        assertThat(NamedInvoicePdfServiceImpl.paymentMethodLabel(PaymentMethod.TRANSFER)).isEqualTo("Przelew");
        assertThat(NamedInvoicePdfServiceImpl.paymentMethodLabel(PaymentMethod.VOUCHER)).isEqualTo("Voucher");
        assertThat(NamedInvoicePdfServiceImpl.paymentMethodLabel(null)).isEqualTo("-");
    }

    @Test
    void paymentLabel_switchesToSplitPayment_whenMoreThanOneMethod() {
        assertThat(NamedInvoicePdfServiceImpl.paymentLabel(null)).isEqualTo("Płatność: ");
        assertThat(NamedInvoicePdfServiceImpl.paymentLabel(Set.of())).isEqualTo("Płatność: ");
        assertThat(NamedInvoicePdfServiceImpl.paymentLabel(Set.of(PaymentMethod.CARD))).isEqualTo("Płatność: ");
        assertThat(NamedInvoicePdfServiceImpl.paymentLabel(
                new LinkedHashSet<>(List.of(PaymentMethod.CARD, PaymentMethod.CASH))))
                .isEqualTo("Płatność dzielona: ");
    }

    @Test
    void paymentValue_joinsMethodsInSetOrder_andDashWhenEmpty() {
        assertThat(NamedInvoicePdfServiceImpl.paymentValue(null)).isEqualTo("-");
        assertThat(NamedInvoicePdfServiceImpl.paymentValue(new LinkedHashSet<>())).isEqualTo("-");
        assertThat(NamedInvoicePdfServiceImpl.paymentValue(
                new LinkedHashSet<>(List.of(PaymentMethod.CARD))))
                .isEqualTo("Karta płatnicza");
        assertThat(NamedInvoicePdfServiceImpl.paymentValue(
                new LinkedHashSet<>(List.of(PaymentMethod.CARD, PaymentMethod.CASH))))
                .isEqualTo("Karta płatnicza, Gotówka");
        assertThat(NamedInvoicePdfServiceImpl.paymentValue(
                new LinkedHashSet<>(List.of(PaymentMethod.CASH, PaymentMethod.CARD))))
                .isEqualTo("Gotówka, Karta płatnicza");
    }

    @Test
    void generateInvoicePdf_producesPdfBytes_forSplitPayment() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        invoice.setPaymentMethods(new LinkedHashSet<>(List.of(PaymentMethod.CARD, PaymentMethod.CASH)));

        when(namedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(statSettingsRepo.getSettings()).thenReturn(company());

        byte[] result = pdfService.generateInvoicePdf(1L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void formatDate_rendersIsoDate_andDashForMissingDate() {
        assertThat(NamedInvoicePdfServiceImpl.formatDate(LocalDate.of(2026, 9, 8))).isEqualTo("2026-09-08");
        assertThat(NamedInvoicePdfServiceImpl.formatDate(null)).isEqualTo("-");
    }

    @Test
    void quantityLabel_appendsMissingUnitMarker() {
        assertThat(NamedInvoicePdfServiceImpl.quantityLabel(BigDecimal.ONE)).isEqualTo("1 (brak)");
        assertThat(NamedInvoicePdfServiceImpl.quantityLabel(new BigDecimal("2.50"))).isEqualTo("2.5 (brak)");
    }

    @Test
    void generateInvoicePdf_producesPdfBytes_forInvoiceWithoutPaymentMethod() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        invoice.setPaymentMethods(new LinkedHashSet<>());
        invoice.setSaleDate(null);
        invoice.setNotes(null);

        when(namedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(statSettingsRepo.getSettings()).thenReturn(company());

        byte[] result = pdfService.generateInvoicePdf(1L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void generateInvoicePdf_producesPdfBytes_forFullyPopulatedInvoice() {
        NamedInvoice invoice = invoice(List.of(
                item("Zabieg", "100.00", "108.00", VatRate.VAT_8),
                item("Krem", "100.00", "123.00", VatRate.VAT_23),
                item("Konsultacja", "50.00", "50.00", VatRate.VAT_ZW)));
        invoice.setNotes("Płatność z góry");

        when(namedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(statSettingsRepo.getSettings()).thenReturn(company());

        byte[] result = pdfService.generateInvoicePdf(1L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void generateInvoicePdf_producesPdfBytes_whenCompanyLogoIsSet() throws IOException {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        StatSettings company = company();
        company.setCompanyLogo(pngBytes(600, 200));
        company.setCompanyLogoContentType("image/png");

        when(namedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(statSettingsRepo.getSettings()).thenReturn(company);

        byte[] result = pdfService.generateInvoicePdf(1L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void generateInvoicePdf_producesPdfBytes_whenCompanyLogoIsMissing() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));

        when(namedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(statSettingsRepo.getSettings()).thenReturn(company());

        byte[] result = pdfService.generateInvoicePdf(1L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void generateInvoicePdf_skipsLogo_whenStoredBytesAreCorrupted() {
        NamedInvoice invoice = invoice(List.of(item("Zabieg", "100.00", "108.00", VatRate.VAT_8)));
        StatSettings company = company();
        company.setCompanyLogo("to nie jest obrazek".getBytes(StandardCharsets.UTF_8));
        company.setCompanyLogoContentType("image/png");

        when(namedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(invoice));
        when(statSettingsRepo.getSettings()).thenReturn(company);

        byte[] result = pdfService.generateInvoicePdf(1L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
