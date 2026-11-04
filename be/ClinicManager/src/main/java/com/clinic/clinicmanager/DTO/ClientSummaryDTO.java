package com.clinic.clinicmanager.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientSummaryDTO {

    private long visitsCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal totalSpent;
    private long absencesCount;
    private long latenessCount;
    private ClientTopItemDTO topService;
    private ClientTopItemDTO topProduct;
    private boolean hasDebts;
    private BigDecimal debtTotal;
    private int activeVouchersCount;
    private ClientReviewInfoDTO googleReview;
    private ClientReviewInfoDTO booksyReview;
}
