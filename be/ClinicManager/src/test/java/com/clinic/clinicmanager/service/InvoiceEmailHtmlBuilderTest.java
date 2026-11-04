package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.service.impl.InvoiceEmailHtmlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceEmailHtmlBuilderTest {

    private static final String BASE_URL = "https://clinic.example.com";
    private static final String BODY = """
            Dzień dobry,

            przesyłam fakturę za wizytę w ClinicManager.

            Pozdrawiam,
            Maria ClinicManager
            """;

    private InvoiceEmailHtmlBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new InvoiceEmailHtmlBuilder();
        ReflectionTestUtils.setField(builder, "assetBaseUrl", BASE_URL);
    }

    private StatSettings fullSettings() {
        StatSettings settings = StatSettings.builder().id(1L).build();
        settings.setFooterCompanyName("ClinicManager Maria Kowalska");
        settings.setFooterAddress("ul. Testowa 12/3");
        settings.setFooterPostalCode("61-001");
        settings.setFooterCity("Lublin");
        settings.setCompanyPhone("+48 61 000 00 00");
        settings.setCompanyWebsite("https://clinic.example.com/");
        settings.setFacebookUrl("https://facebook.com/clinicmanager");
        settings.setInstagramUrl("https://instagram.com/clinicmanager");
        settings.setBooksyUrl("https://booksy.com/clinicmanager");
        settings.setEmailFooterLogo(new byte[]{1, 2, 3});
        settings.setEmailFooterLogoContentType("image/png");
        return settings;
    }

    private StatSettings nameOnlySettings() {
        StatSettings settings = StatSettings.builder().id(1L).build();
        settings.setFooterCompanyName("ClinicManager");
        return settings;
    }

    private String html(StatSettings settings) {
        InvoiceEmailHtmlBuilder.EmailHtml result = builder.build(BODY, settings);
        assertNotNull(result);
        return result.html();
    }

    @Test
    void build_returnsNull_whenSettingsAreNull() {
        assertNull(builder.build(BODY, null));
    }

    @Test
    void build_returnsNull_whenFooterCompanyNameIsMissing() {
        StatSettings settings = fullSettings();
        settings.setFooterCompanyName(null);

        assertNull(builder.build(BODY, settings));
    }

    @Test
    void build_returnsNull_whenFooterCompanyNameIsBlank() {
        StatSettings settings = fullSettings();
        settings.setFooterCompanyName("   ");

        assertNull(builder.build(BODY, settings));
    }

    @Test
    void build_rendersTextBodyAsParagraphs() {
        String html = html(fullSettings());

        assertTrue(html.contains("<p style=\"margin:0 0 16px;\">Dzień dobry,</p>"));
        assertTrue(html.contains("<p style=\"margin:0;\">Pozdrawiam,<br>Maria ClinicManager</p>"));
    }

    @Test
    void build_usesFooterFields_andIgnoresInvoiceCompanyFields() {
        StatSettings settings = fullSettings();
        settings.setCompanyName("Firma z faktury");
        settings.setCompanyAddress("ul. Fakturowa 1");
        settings.setCompanyPostalCode("00-000");
        settings.setCompanyCity("Warszawa");

        String html = html(settings);

        assertTrue(html.contains("ClinicManager Maria Kowalska"));
        assertTrue(html.contains("ul. Testowa 12/3, 61-001 Lublin"));
        assertFalse(html.contains("Firma z faktury"));
        assertFalse(html.contains("Fakturowa"));
        assertFalse(html.contains("Warszawa"));
    }

    @Test
    void build_rendersLogoColumn_withVersionedPublicUrl() {
        StatSettings settings = fullSettings();
        String expectedHash = Integer.toHexString(Arrays.hashCode(settings.getEmailFooterLogo()));

        String html = html(settings);

        assertTrue(html.contains(BASE_URL + "/api/public/email-footer-logo?v=" + expectedHash));
        assertTrue(html.contains("width=\"250\""));
    }

    @Test
    void build_changesLogoUrl_whenLogoBytesChange() {
        StatSettings first = fullSettings();
        StatSettings second = fullSettings();
        second.setEmailFooterLogo(new byte[]{9, 9, 9, 9});

        assertNotEquals(logoUrl(html(first)), logoUrl(html(second)));
    }

    private String logoUrl(String html) {
        int start = html.indexOf("/api/public/email-footer-logo?v=");
        assertTrue(start > 0);
        return html.substring(start, html.indexOf('"', start));
    }

    @Test
    void build_skipsLogoColumn_andWidensText_whenLogoIsMissing() {
        StatSettings settings = fullSettings();
        settings.setEmailFooterLogo(null);

        String html = html(settings);

        assertFalse(html.contains("/api/public/email-footer-logo"));
        assertFalse(html.contains("width=\"250\""));
        assertTrue(html.contains("width:528px;"));
    }

    @Test
    void build_skipsLogoColumn_whenLogoIsEmpty() {
        StatSettings settings = fullSettings();
        settings.setEmailFooterLogo(new byte[0]);

        assertFalse(html(settings).contains("/api/public/email-footer-logo"));
    }

    @Test
    void build_usesHostedIconUrls_insteadOfCid() {
        String html = html(fullSettings());

        assertFalse(html.contains("cid:"));
        assertTrue(html.contains(BASE_URL + "/email/location.png"));
        assertTrue(html.contains(BASE_URL + "/email/phone.png"));
        assertTrue(html.contains(BASE_URL + "/email/website.png"));
        assertTrue(html.contains(BASE_URL + "/email/facebook.png"));
        assertTrue(html.contains(BASE_URL + "/email/instagram.png"));
        assertTrue(html.contains(BASE_URL + "/email/booksy.png"));
    }

    @Test
    void build_stripsTrailingSlashFromAssetBaseUrl() {
        ReflectionTestUtils.setField(builder, "assetBaseUrl", BASE_URL + "/");

        String html = html(fullSettings());

        assertTrue(html.contains(BASE_URL + "/email/location.png"));
        assertFalse(html.contains("//email/"));
    }

    @Test
    void build_skipsAddressLine_whenAllAddressFieldsAreMissing() {
        StatSettings settings = fullSettings();
        settings.setFooterAddress(null);
        settings.setFooterPostalCode(null);
        settings.setFooterCity(null);

        assertFalse(html(settings).contains("/email/location.png"));
    }

    @Test
    void build_rendersAddressWithoutComma_whenOnlyStreetIsPresent() {
        StatSettings settings = fullSettings();
        settings.setFooterPostalCode(null);
        settings.setFooterCity(null);

        String html = html(settings);

        assertTrue(html.contains(">ul. Testowa 12/3</p>"));
    }

    @Test
    void build_rendersAddressWithoutComma_whenStreetIsMissing() {
        StatSettings settings = fullSettings();
        settings.setFooterAddress(null);

        String html = html(settings);

        assertTrue(html.contains(">61-001 Lublin</p>"));
    }

    @Test
    void build_normalizesPhoneForTelHref_andKeepsFormattedLabel() {
        String html = html(fullSettings());

        assertTrue(html.contains("href=\"tel:+48610000000\""));
        assertTrue(html.contains(">+48 61 000 00 00</a>"));
    }

    @Test
    void build_skipsPhone_whenNotConfigured() {
        StatSettings settings = fullSettings();
        settings.setCompanyPhone(null);

        String html = html(settings);

        assertFalse(html.contains("/email/phone.png"));
        assertFalse(html.contains("tel:"));
        assertTrue(html.contains("/email/website.png"));
    }

    @Test
    void build_rendersWebsiteLabelWithoutSchemeAndTrailingSlash() {
        String html = html(fullSettings());

        assertTrue(html.contains("href=\"https://clinic.example.com/\""));
        assertTrue(html.contains(">clinic.example.com</a>"));
    }

    @Test
    void build_addsHttpsToWebsiteHref_whenSchemeIsMissing() {
        StatSettings settings = fullSettings();
        settings.setCompanyWebsite("clinic.example.com");

        String html = html(settings);

        assertTrue(html.contains("href=\"https://clinic.example.com\""));
    }

    @Test
    void build_skipsContactLine_whenPhoneAndWebsiteAreMissing() {
        StatSettings settings = fullSettings();
        settings.setCompanyPhone(null);
        settings.setCompanyWebsite(null);

        String html = html(settings);

        assertFalse(html.contains("/email/phone.png"));
        assertFalse(html.contains("/email/website.png"));
        assertTrue(html.contains("/email/location.png"));
    }

    @Test
    void build_skipsSingleSocialIcon_whenItsUrlIsMissing() {
        StatSettings settings = fullSettings();
        settings.setInstagramUrl(null);

        String html = html(settings);

        assertTrue(html.contains("/email/facebook.png"));
        assertFalse(html.contains("/email/instagram.png"));
        assertTrue(html.contains("/email/booksy.png"));
    }

    @Test
    void build_skipsSocialRow_whenAllSocialUrlsAreMissing() {
        StatSettings settings = fullSettings();
        settings.setFacebookUrl(null);
        settings.setInstagramUrl(null);
        settings.setBooksyUrl(null);

        String html = html(settings);

        assertFalse(html.contains("padding-top:7px;"));
        assertTrue(html.contains("ClinicManager Maria Kowalska"));
    }

    @Test
    void build_addsHttpsToSocialHref_whenSchemeIsMissing() {
        StatSettings settings = fullSettings();
        settings.setFacebookUrl("facebook.com/clinicmanager");

        assertTrue(html(settings).contains("href=\"https://facebook.com/clinicmanager\""));
    }

    @Test
    void build_rendersNameOnly_whenEverythingElseIsMissing() {
        String html = html(nameOnlySettings());

        assertTrue(html.contains(">ClinicManager</p>"));
        assertFalse(html.contains("/email/"));
        assertFalse(html.contains("/api/public/email-footer-logo"));
    }

    @Test
    void build_usesTextIconsWithEmptyAlt_andNamedAltForSocialIcons() {
        String html = html(fullSettings());

        assertTrue(html.contains("/email/location.png\" alt=\"\""));
        assertTrue(html.contains("/email/phone.png\" alt=\"\""));
        assertTrue(html.contains("/email/facebook.png\" alt=\"Facebook\""));
        assertTrue(html.contains("/email/instagram.png\" alt=\"Instagram\""));
        assertTrue(html.contains("/email/booksy.png\" alt=\"Booksy\""));
    }

    @Test
    void build_escapesHtmlSpecialCharactersInSettings() {
        StatSettings settings = nameOnlySettings();
        settings.setFooterCompanyName("Gabinet <b>\"Stopa\"</b> & Syn");

        String html = html(settings);

        assertTrue(html.contains("Gabinet &lt;b&gt;&quot;Stopa&quot;&lt;/b&gt; &amp; Syn"));
        assertFalse(html.contains("<b>"));
    }

    @Test
    void build_keepsLayoutContract_forOutlook() {
        String html = html(fullSettings());

        assertTrue(html.contains("<table width=\"600\""));
        assertTrue(html.contains("align=\"center\""));
        assertFalse(html.contains("display:flex"));
        assertFalse(html.contains("display:grid"));
        assertFalse(html.contains("border-radius"));
        assertFalse(html.contains("<style"));
    }
}
