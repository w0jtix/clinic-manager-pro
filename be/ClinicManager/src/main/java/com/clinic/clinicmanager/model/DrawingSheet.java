package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "drawing_sheet")
public class DrawingSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private ClientCard card;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(name = "template_image_id", updatable = false)
    private Long templateImageId;

    @Column(name = "sheet_number", nullable = false, updatable = false)
    private Integer sheetNumber;

    @Column(nullable = false, updatable = false)
    private Integer width;

    @Column(nullable = false, updatable = false)
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private DrawingSheetStatus status = DrawingSheetStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", updatable = false)
    private Long createdByUserId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    public boolean isOpen() {
        return status == DrawingSheetStatus.OPEN;
    }

    public void close(LocalDateTime at, Long userId) {
        this.status = DrawingSheetStatus.CLOSED;
        this.closedAt = at;
        this.closedByUserId = userId;
    }
}
