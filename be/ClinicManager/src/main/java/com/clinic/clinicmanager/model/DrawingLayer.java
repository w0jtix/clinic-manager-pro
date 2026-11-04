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
@Table(name = "drawing_layer")
public class DrawingLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id", nullable = false, updatable = false)
    private DrawingSheet sheet;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "file_key", nullable = false, length = 200)
    private String fileKey;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(nullable = false)
    @Builder.Default
    private Long version = 1L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
}
