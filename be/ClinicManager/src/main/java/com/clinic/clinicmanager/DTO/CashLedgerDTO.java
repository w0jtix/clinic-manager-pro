package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.CashLedger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CashLedgerDTO {

    private Long id;
    private LocalDate date;
    private BigDecimal openingAmount;
    private BigDecimal deposit;
    private BigDecimal closingAmount;
    private BigDecimal cashOutAmount;
    private String note;
    private EmployeeSummaryDTO createdBy;
    private EmployeeSummaryDTO closedBy;
    private Boolean isClosed;

    public CashLedgerDTO(CashLedger entity) {
        this.id = entity.getId();
        this.date = entity.getDate();
        this.openingAmount = entity.getOpeningAmount();
        this.deposit = entity.getDeposit();
        this.closingAmount = entity.getClosingAmount();
        this.cashOutAmount = entity.getCashOutAmount();
        this.note = entity.getNote();
        this.createdBy = entity.getCreatedBy() != null ? new EmployeeSummaryDTO(entity.getCreatedBy()) : null;
        this.closedBy = entity.getClosedBy() != null ? new EmployeeSummaryDTO(entity.getClosedBy()) : null;
        this.isClosed = entity.getIsClosed();
    }

    public CashLedger toEntity() {
        return CashLedger.builder()
                .id(this.id)
                .date(this.date)
                .openingAmount(this.openingAmount)
                .deposit(this.deposit)
                .closingAmount(this.closingAmount)
                .cashOutAmount(this.cashOutAmount)
                .note(this.note)
                .build();
    }
}
