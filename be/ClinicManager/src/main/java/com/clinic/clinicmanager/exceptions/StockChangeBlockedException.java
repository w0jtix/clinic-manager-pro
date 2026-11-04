package com.clinic.clinicmanager.exceptions;

import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import lombok.Getter;

@Getter
public class StockChangeBlockedException extends RuntimeException {

    private final transient StockChangePreviewDTO preview;

    public StockChangeBlockedException(String message, StockChangePreviewDTO preview) {
        super(message);
        this.preview = preview;
    }
}
