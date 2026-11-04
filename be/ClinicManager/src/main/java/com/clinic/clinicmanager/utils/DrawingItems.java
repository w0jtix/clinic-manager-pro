package com.clinic.clinicmanager.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.clinic.clinicmanager.DTO.DrawItem;
import com.clinic.clinicmanager.DTO.DrawMarkerItem;
import com.clinic.clinicmanager.DTO.DrawStrokeItem;
import com.clinic.clinicmanager.DTO.DrawTextItem;
import com.clinic.clinicmanager.exceptions.ValidationException;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class DrawingItems {

    public static final int MAX_ITEMS = 5000;
    public static final int MAX_POINTS = 20000;
    public static final int MAX_TEXT_LENGTH = 500;
    public static final int MAX_NAME_LENGTH = 40;
    public static final double MAX_STROKE_WIDTH = 200;
    public static final double MAX_SIZE = 1000;
    public static final double EDGE_TOLERANCE = 50;
    public static final int MAX_CONTENT_BYTES = 3 * 1024 * 1024;
    public static final String INVALID_MESSAGE = "Nieprawidłowe dane rysunku.";
    public static final String TOO_MANY_ITEMS_MESSAGE = "Rysunek ma za dużo elementów (maksymalnie 5000).";
    public static final String TOO_MANY_POINTS_MESSAGE = "Kreska ma za dużo punktów (maksymalnie 20000).";
    public static final String OUT_OF_SHEET_MESSAGE = "Element rysunku wychodzi poza kartkę.";
    public static final String TEXT_LENGTH_MESSAGE = "Napis na rysunku musi mieć od 1 do 500 znaków.";
    public static final String MARKER_NAME_MESSAGE = "Nazwa oznaczenia na rysunku musi mieć od 1 do 40 znaków.";
    public static final String MARKER_COLOR_MESSAGE = "Nieprawidłowy kolor oznaczenia na rysunku.";
    public static final String TOO_LARGE_MESSAGE = "Rysunek jest za duży (maksymalnie 3 MB).";

    private static final ObjectWriter WRITER = new ObjectMapper().writerFor(new TypeReference<List<DrawItem>>() {
    });

    public static byte[] normalize(List<DrawItem> items, int width, int height) {
        if (items == null) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        if (items.size() > MAX_ITEMS) {
            throw new ValidationException(TOO_MANY_ITEMS_MESSAGE);
        }

        List<DrawItem> normalized = new ArrayList<>(items.size());
        for (DrawItem item : items) {
            normalized.add(switch (item) {
                case DrawStrokeItem stroke -> stroke(stroke, width, height);
                case DrawTextItem text -> text(text, width, height);
                case DrawMarkerItem marker -> marker(marker, width, height);
                case null -> throw new ValidationException(INVALID_MESSAGE);
            });
        }

        byte[] content;
        try {
            content = WRITER.writeValueAsBytes(normalized);
        } catch (JsonProcessingException e) {
            throw new ValidationException(INVALID_MESSAGE, e);
        }
        if (content.length > MAX_CONTENT_BYTES) {
            throw new ValidationException(TOO_LARGE_MESSAGE);
        }
        return content;
    }

    private static DrawStrokeItem stroke(DrawStrokeItem stroke, int width, int height) {
        double strokeWidth = bounded(stroke.width(), MAX_STROKE_WIDTH);
        boolean pressureSensitive = required(stroke.pressureSensitive());
        double[][] points = stroke.points();
        if (points == null || points.length == 0) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        if (points.length > MAX_POINTS) {
            throw new ValidationException(TOO_MANY_POINTS_MESSAGE);
        }

        double[][] copy = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            double[] point = points[i];
            if (point == null || point.length != 3) {
                throw new ValidationException(INVALID_MESSAGE);
            }
            double pressure = point[2];
            if (!Double.isFinite(pressure) || pressure < 0 || pressure > 1) {
                throw new ValidationException(INVALID_MESSAGE);
            }
            copy[i] = new double[]{coordinate(point[0], width), coordinate(point[1], height), pressure};
        }
        return new DrawStrokeItem(strokeWidth, pressureSensitive, copy);
    }

    private static DrawTextItem text(DrawTextItem text, int width, int height) {
        String value = text.text();
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException(TEXT_LENGTH_MESSAGE);
        }
        return new DrawTextItem(value, coordinate(text.x(), width), coordinate(text.y(), height),
                bounded(text.size(), MAX_SIZE), required(text.bold()));
    }

    private static DrawMarkerItem marker(DrawMarkerItem marker, int width, int height) {
        if (marker.shape() == null) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        String color = DrawingColors.normalizeHex(marker.color());
        if (color == null) {
            throw new ValidationException(MARKER_COLOR_MESSAGE);
        }
        String name = marker.name() == null ? "" : marker.name().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(MARKER_NAME_MESSAGE);
        }
        return new DrawMarkerItem(marker.markerId(), marker.shape(), color, required(marker.filled()), name,
                coordinate(marker.x(), width), coordinate(marker.y(), height), bounded(marker.size(), MAX_SIZE));
    }

    private static double coordinate(Double value, int sheetSide) {
        if (value == null || !Double.isFinite(value)) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        if (value < -EDGE_TOLERANCE || value > sheetSide + EDGE_TOLERANCE) {
            throw new ValidationException(OUT_OF_SHEET_MESSAGE);
        }
        return value;
    }

    private static double bounded(Double value, double max) {
        if (value == null || !Double.isFinite(value) || value <= 0 || value > max) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        return value;
    }

    private static boolean required(Boolean value) {
        if (value == null) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        return value;
    }
}
