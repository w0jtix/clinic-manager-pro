package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ksef_sync_period")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KsefSyncPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private long ksefCount;

    @Column(nullable = false)
    private LocalDateTime syncedAt;
}

