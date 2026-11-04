package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ksef_seller")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsefSeller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String nip;

    private String name;

    @Column(length = 500)
    private String address;

    private String email;

    private String phone;

    @Column(name = "auto_mark_products", nullable = false)
    @Builder.Default
    private Boolean autoMarkProducts = false;

    @Column(name = "default_expense_category", length = 40)
    private String defaultExpenseCategory;
}
