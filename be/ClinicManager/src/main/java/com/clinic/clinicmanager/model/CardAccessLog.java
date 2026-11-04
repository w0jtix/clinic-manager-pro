package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.CardAccessAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "card_access_log")
public class CardAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime at;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CardAccessAction action;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "device_name", length = 100)
    private String deviceName;
}
