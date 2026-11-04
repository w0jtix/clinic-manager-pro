package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "stat_settings")
public class StatSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Integer bonusThreshold = 1000;

    @Column(nullable = false)
    @Builder.Default
    private Integer servicesRevenueGoal = 3000;

    @Column(nullable = false)
    @Builder.Default
    private Integer productsRevenueGoal = 500;

    @Column(name = "company_name", nullable = true)
    private String companyName;

    @Column(name = "company_address", nullable = true)
    private String companyAddress;

    @Column(name = "company_postal_code", nullable = true)
    private String companyPostalCode;

    @Column(name = "company_city", nullable = true)
    private String companyCity;

    @Column(name = "company_nip", nullable = true)
    private String companyNip;

    @Column(name = "invoice_email_bcc", nullable = true)
    private String invoiceEmailBcc;

    @Column(name = "footer_company_name", nullable = true)
    private String footerCompanyName;

    @Column(name = "footer_address", nullable = true)
    private String footerAddress;

    @Column(name = "footer_postal_code", nullable = true)
    private String footerPostalCode;

    @Column(name = "footer_city", nullable = true)
    private String footerCity;

    @Column(name = "company_phone", nullable = true)
    private String companyPhone;

    @Column(name = "company_website", nullable = true)
    private String companyWebsite;

    @Column(name = "facebook_url", nullable = true)
    private String facebookUrl;

    @Column(name = "instagram_url", nullable = true)
    private String instagramUrl;

    @Column(name = "booksy_url", nullable = true)
    private String booksyUrl;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "company_logo")
    private byte[] companyLogo;

    @Column(name = "company_logo_content_type", length = 50)
    private String companyLogoContentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "email_footer_logo")
    private byte[] emailFooterLogo;

    @Column(name = "email_footer_logo_content_type", length = 50)
    private String emailFooterLogoContentType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stat_settings_sale_bonus_payout_months", joinColumns = @JoinColumn(name = "stat_settings_id"))
    @Column(name = "month")
    @Builder.Default
    private Set<Integer> saleBonusPayoutMonths = Set.of(1, 4, 7, 10);

    @PrePersist
    @PreUpdate
    private void validateSaleBonusPayoutMonths() {
        if (saleBonusPayoutMonths == null || saleBonusPayoutMonths.isEmpty()) {
            return;
        }

        int firstModulo = saleBonusPayoutMonths.iterator().next() % 3;
        boolean allSameModulo = saleBonusPayoutMonths.stream()
                .allMatch(month -> month % 3 == firstModulo);

        if (!allSameModulo) {
            throw new IllegalArgumentException("Sale bonus payout months must be evenly spaced (every 3 months)");
        }
    }
}
