package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.StatSettings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatSettingsDTO {
    private Long id;
    private Integer bonusThreshold;
    private Integer servicesRevenueGoal;
    private Integer productsRevenueGoal;
    private Set<Integer> saleBonusPayoutMonths;
    private String companyName;
    private String companyAddress;
    private String companyPostalCode;
    private String companyCity;
    private String companyNip;
    private String footerCompanyName;
    private String footerAddress;
    private String footerPostalCode;
    private String footerCity;
    private String companyPhone;
    private String companyWebsite;
    private String facebookUrl;
    private String instagramUrl;
    private String booksyUrl;
    private String invoiceEmailBcc;
    private boolean hasCompanyLogo;
    private boolean hasEmailFooterLogo;

    public StatSettingsDTO(StatSettings settings) {
        this(settings, false, false);
    }

    public StatSettingsDTO(StatSettings settings, boolean hasCompanyLogo, boolean hasEmailFooterLogo) {
        this.id = settings.getId();
        this.bonusThreshold = settings.getBonusThreshold();
        this.servicesRevenueGoal = settings.getServicesRevenueGoal();
        this.productsRevenueGoal = settings.getProductsRevenueGoal();
        this.saleBonusPayoutMonths = settings.getSaleBonusPayoutMonths();
        this.companyName = settings.getCompanyName();
        this.companyAddress = settings.getCompanyAddress();
        this.companyPostalCode = settings.getCompanyPostalCode();
        this.companyCity = settings.getCompanyCity();
        this.companyNip = settings.getCompanyNip();
        this.footerCompanyName = settings.getFooterCompanyName();
        this.footerAddress = settings.getFooterAddress();
        this.footerPostalCode = settings.getFooterPostalCode();
        this.footerCity = settings.getFooterCity();
        this.companyPhone = settings.getCompanyPhone();
        this.companyWebsite = settings.getCompanyWebsite();
        this.facebookUrl = settings.getFacebookUrl();
        this.instagramUrl = settings.getInstagramUrl();
        this.booksyUrl = settings.getBooksyUrl();
        this.invoiceEmailBcc = settings.getInvoiceEmailBcc();
        this.hasCompanyLogo = hasCompanyLogo;
        this.hasEmailFooterLogo = hasEmailFooterLogo;
    }

    public StatSettings toEntity() {
        return StatSettings.builder()
                .id(this.id)
                .bonusThreshold(this.bonusThreshold)
                .servicesRevenueGoal(this.servicesRevenueGoal)
                .productsRevenueGoal(this.productsRevenueGoal)
                .saleBonusPayoutMonths(this.saleBonusPayoutMonths)
                .companyName(this.companyName)
                .companyAddress(this.companyAddress)
                .companyPostalCode(this.companyPostalCode)
                .companyCity(this.companyCity)
                .companyNip(this.companyNip)
                .footerCompanyName(this.footerCompanyName)
                .footerAddress(this.footerAddress)
                .footerPostalCode(this.footerPostalCode)
                .footerCity(this.footerCity)
                .companyPhone(this.companyPhone)
                .companyWebsite(this.companyWebsite)
                .facebookUrl(this.facebookUrl)
                .instagramUrl(this.instagramUrl)
                .booksyUrl(this.booksyUrl)
                .invoiceEmailBcc(this.invoiceEmailBcc)
                .build();
    }
}
