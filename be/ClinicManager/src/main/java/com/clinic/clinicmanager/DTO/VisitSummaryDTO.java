package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.model.VisitItem;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitSummaryDTO {
    private Long id;
    private String employeeName;
    private List<VisitSummaryServiceDTO> services;
    private List<VisitSummaryProductDTO> products;
    private Boolean absence;
    private PaymentStatus paymentStatus;
    private BigDecimal totalValue;

    public VisitSummaryDTO(Visit visit, List<VisitSummaryProductDTO> products) {
        this.id = visit.getId();
        this.employeeName = visit.getEmployee() != null ? visit.getEmployee().getName() : null;
        this.services = visit.getItems() == null ? List.of() : visit.getItems().stream()
                .sorted(Comparator.comparing(VisitItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(VisitSummaryServiceDTO::new)
                .toList();
        this.products = products;
        this.absence = Boolean.TRUE.equals(visit.getAbsence());
        this.paymentStatus = visit.getPaymentStatus();
        this.totalValue = visit.getTotalValue();
    }
}
