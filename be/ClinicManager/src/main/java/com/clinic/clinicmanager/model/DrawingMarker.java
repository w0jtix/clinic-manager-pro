package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "drawing_marker")
public class DrawingMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrawingMarkerShape shape;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    @Builder.Default
    private Boolean filled = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", updatable = false)
    private Long createdByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isFilled() {
        return Boolean.TRUE.equals(filled);
    }
}
