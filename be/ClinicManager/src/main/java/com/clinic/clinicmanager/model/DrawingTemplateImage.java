package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "drawing_template_image")
public class DrawingTemplateImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(nullable = false, updatable = false)
    private byte[] image;

    @Column(nullable = false, updatable = false)
    private Integer width;

    @Column(nullable = false, updatable = false)
    private Integer height;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private Integer sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", updatable = false)
    private Long createdByUserId;
}
