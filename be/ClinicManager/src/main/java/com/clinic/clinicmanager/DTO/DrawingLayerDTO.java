package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingLayerDTO {
    private Long id;
    private Long authorUserId;
    private String authorName;
    private String color;
    private Long version;
    private LocalDateTime updatedAt;
    private Long updatedByUserId;
    private String updatedByName;
}
