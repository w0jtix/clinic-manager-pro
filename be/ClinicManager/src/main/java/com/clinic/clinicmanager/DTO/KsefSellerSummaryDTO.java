package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KsefSellerSummaryDTO {

    private Long invoiceCount;
    private BigDecimal totalGross;
    private Long unpaidCount;
    private Long awaitingVerificationCount;
    private Long uncategorizedCount;
    private List<KsefCategoryFacetDTO> categoryFacets;
    private Long manualExpenseCount;
    private BigDecimal manualExpenseTotal;
}
