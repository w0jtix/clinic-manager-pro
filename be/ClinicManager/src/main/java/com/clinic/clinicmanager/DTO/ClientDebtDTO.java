package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientDebt;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
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
public class ClientDebtDTO {
    private Long id;
    private ClientDTO client;
    private VisitDTO sourceVisit;
    private DebtType type;
    private BigDecimal value;
    private PaymentStatus paymentStatus;
    private LocalDate createdAt;
    private Long createdBy; // user id
    private String reason;


    public ClientDebtDTO(ClientDebt clientDebt) {
        this.id = clientDebt.getId();
        this.client = new ClientDTO(clientDebt.getClient());
        this.sourceVisit = clientDebt.getSourceVisit() != null ? new VisitDTO(clientDebt.getSourceVisit()) : null;
        this.type = clientDebt.getType();
        this.value = clientDebt.getValue();
        this.paymentStatus = clientDebt.getPaymentStatus();
        this.createdAt = clientDebt.getCreatedAt();
        this.createdBy = clientDebt.getCreatedByUserId();
        this.reason = clientDebt.getReason();
    }

    public ClientDebt toEntity() {
        return ClientDebt.builder()
                .id(this.id)
                .client(this.client != null ? this.client.toEntity() : null)
                .sourceVisit(this.sourceVisit != null ? this.sourceVisit.toEntity() : null)
                .type(this.type)
                .value(this.value)
                .paymentStatus(this.paymentStatus != null ? this.paymentStatus : PaymentStatus.UNPAID)
                .createdAt(this.createdAt)
                .createdByUserId(this.createdBy)
                .reason(this.reason)
                .build();
    }

    public ClientDebt toEntity(Client client) {
        return ClientDebt.builder()
                .id(this.id)
                .client(client)
                .sourceVisit(this.sourceVisit != null ? this.sourceVisit.toEntity() : null)
                .type(this.type)
                .value(this.value)
                .paymentStatus(this.paymentStatus)
                .createdByUserId(this.createdBy)
                .reason(this.reason)
                .build();
    }
}
