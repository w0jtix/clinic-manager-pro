package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.model.StatSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class InvoiceEmailHtmlBuilder {

    private static final String FONT = "'Liberation Sans',Arial,Helvetica,sans-serif";
    private static final String TEXT_ICON_STYLE =
            "display:inline-block;border:0;vertical-align:-2px;margin-right:5px;";

    @Value("${app.mail.asset-base-url:}")
    private String assetBaseUrl;

    public record EmailHtml(String html) {
    }

    public EmailHtml build(String textBody, StatSettings settings) {
        if (settings == null || isBlank(settings.getFooterCompanyName())) {
            return null;
        }

        String footer = buildFooter(settings);

        String html = """
                <table width="100%%" cellpadding="0" cellspacing="0" border="0">
                 <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#ffffff;">

                   <tr><td style="padding:34px 36px 26px;font-family:%s;font-size:15px;line-height:1.65;color:#1f2328;">
                %s   </td></tr>

                   <tr><td style="height:1px;background:#e5e7eb;font-size:0;line-height:0;">&nbsp;</td></tr>

                %s  </table>
                 </td></tr>
                </table>
                """.formatted(FONT, renderBody(textBody), footer);

        return new EmailHtml(html);
    }

    private String renderBody(String textBody) {
        String normalized = textBody != null ? textBody.replace("\r\n", "\n").trim() : "";
        String[] paragraphs = normalized.split("\n\\s*\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            String content = String.join("<br>", escapeLines(paragraph.split("\n")));
            String margin = i == paragraphs.length - 1 ? "0" : "0 0 16px";
            sb.append("     <p style=\"margin:").append(margin).append(";\">").append(content).append("</p>\n");
        }
        return sb.toString();
    }

    private List<String> escapeLines(String[] lines) {
        List<String> escaped = new ArrayList<>(lines.length);
        for (String line : lines) {
            escaped.add(escape(line.trim()));
        }
        return escaped;
    }

    private String buildFooter(StatSettings settings) {
        StringBuilder cells = new StringBuilder();

        byte[] logo = settings.getEmailFooterLogo();
        boolean hasLogo = logo != null && logo.length > 0;
        if (hasLogo) {
            cells.append("""
                          <td width="250" valign="top" style="width:250px;">
                            <img src="%s" alt="%s" width="220" style="display:block;border:0;width:220px;height:auto;">
                          </td>
                    """.formatted(escape(footerLogoUrl(logo)), escape(settings.getFooterCompanyName())));
        }

        String textCellWidth = hasLogo ? "" : "width:528px;";
        cells.append("""
                      <td valign="top" style="%spadding-top:16px;font-family:%s;">
                %s          </td>
                """.formatted(textCellWidth, FONT, buildCompanyDetails(settings)));

        return """
                   <tr><td style="padding:34px 36px 24px;">
                    <table width="100%%" cellpadding="0" cellspacing="0" border="0">
                     <tr>
                %s     </tr>
                    </table>
                   </td></tr>
                """.formatted(cells);
    }

    private String buildCompanyDetails(StatSettings settings) {
        StringBuilder sb = new StringBuilder();
        sb.append("            <p style=\"margin:0 0 10px;font-size:15px;font-weight:bold;color:#1f2328;\">")
                .append(escape(settings.getFooterCompanyName()))
                .append("</p>\n");

        String address = formatAddress(settings);
        if (address != null) {
            sb.append("            <p style=\"margin:0 0 3px;font-size:13px;line-height:1.5;color:#4b525c;\">")
                    .append("<img src=\"").append(escape(iconUrl("location.png")))
                    .append("\" alt=\"\" width=\"14\" height=\"14\" style=\"")
                    .append(TEXT_ICON_STYLE).append("\">")
                    .append(escape(address))
                    .append("</p>\n");
        }

        String contactLine = buildContactLine(settings);
        if (contactLine != null) {
            sb.append("            <p style=\"margin:0 0 3px;font-size:13px;line-height:1.5;color:#4b525c;\">")
                    .append(contactLine)
                    .append("</p>\n");
        }

        String socials = buildSocials(settings);
        if (socials != null) {
            sb.append(socials);
        }
        return sb.toString();
    }

    private String buildContactLine(StatSettings settings) {
        List<String> parts = new ArrayList<>();

        String phone = trimToNull(settings.getCompanyPhone());
        if (phone != null) {
            parts.add("<img src=\"" + escape(iconUrl("phone.png"))
                    + "\" alt=\"\" width=\"14\" height=\"14\" style=\"" + TEXT_ICON_STYLE + "\">"
                    + "<a href=\"tel:" + escape(telHref(phone)) + "\" style=\"color:#4b525c;text-decoration:none;\">"
                    + escape(phone) + "</a>");
        }

        String website = trimToNull(settings.getCompanyWebsite());
        if (website != null) {
            parts.add("<img src=\"" + escape(iconUrl("website.png"))
                    + "\" alt=\"\" width=\"14\" height=\"14\" style=\"" + TEXT_ICON_STYLE + "\">"
                    + "<a href=\"" + escape(withScheme(website)) + "\" style=\"color:#1f2328;text-decoration:underline;\">"
                    + escape(websiteLabel(website)) + "</a>");
        }

        if (parts.isEmpty()) {
            return null;
        }
        return String.join("<span style=\"display:inline-block;width:7px;\"></span>", parts);
    }

    private String buildSocials(StatSettings settings) {
        StringBuilder sb = new StringBuilder();
        appendSocial(sb, settings.getFacebookUrl(), "facebook.png", "Facebook");
        appendSocial(sb, settings.getInstagramUrl(), "instagram.png", "Instagram");
        appendSocial(sb, settings.getBooksyUrl(), "booksy.png", "Booksy");

        if (sb.length() == 0) {
            return null;
        }
        return "            <div style=\"padding-top:7px;\">\n" + sb + "            </div>\n";
    }

    private void appendSocial(StringBuilder sb, String url, String fileName, String label) {
        String value = trimToNull(url);
        if (value == null) {
            return;
        }
        sb.append("              <a href=\"").append(escape(withScheme(value)))
                .append("\" style=\"display:inline-block;margin-right:7px;text-decoration:none;vertical-align:middle;\">")
                .append("<img src=\"").append(escape(iconUrl(fileName))).append("\" alt=\"").append(escape(label))
                .append("\" width=\"20\" height=\"20\" style=\"display:block;border:0;\"></a>\n");
    }

    private String iconUrl(String fileName) {
        return baseUrl() + "/email/" + fileName;
    }

    private String footerLogoUrl(byte[] logo) {
        return baseUrl() + "/api/public/email-footer-logo?v=" + Integer.toHexString(Arrays.hashCode(logo));
    }

    private String baseUrl() {
        String base = trimToNull(assetBaseUrl);
        if (base == null) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String formatAddress(StatSettings settings) {
        String street = trimToNull(settings.getFooterAddress());
        String postalCode = trimToNull(settings.getFooterPostalCode());
        String city = trimToNull(settings.getFooterCity());

        String locality = postalCode != null && city != null
                ? postalCode + " " + city
                : (postalCode != null ? postalCode : city);

        if (street != null && locality != null) {
            return street + ", " + locality;
        }
        return street != null ? street : locality;
    }

    private String telHref(String phone) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (Character.isDigit(c) || (c == '+' && sb.length() == 0)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String withScheme(String url) {
        return url.contains("://") ? url : "https://" + url;
    }

    private String websiteLabel(String website) {
        String label = website.replaceFirst("(?i)^https?://", "");
        while (label.endsWith("/")) {
            label = label.substring(0, label.length() - 1);
        }
        return label;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return trimToNull(value) == null;
    }
}
