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
@Table(name = "drawing_template")
public class DrawingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "current_image_id")
    private Long currentImageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", updatable = false)
    private Long createdByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isArchived() {
        return Boolean.TRUE.equals(archived);
    }

    public void archive(LocalDateTime at) {
        this.archived = true;
        this.archivedAt = at;
        this.updatedAt = at;
    }

    public void restore(int displayOrder, LocalDateTime at) {
        this.archived = false;
        this.archivedAt = null;
        this.displayOrder = displayOrder;
        this.updatedAt = at;
    }
}
