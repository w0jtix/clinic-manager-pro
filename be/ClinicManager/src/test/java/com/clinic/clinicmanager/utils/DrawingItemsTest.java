package com.clinic.clinicmanager.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.DrawItem;
import com.clinic.clinicmanager.DTO.DrawMarkerItem;
import com.clinic.clinicmanager.DTO.DrawStrokeItem;
import com.clinic.clinicmanager.DTO.DrawTextItem;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrawingItemsTest {

    private static final int WIDTH = 100;
    private static final int HEIGHT = 200;
    private static final String INVALID = "Nieprawidłowe dane rysunku.";
    private static final String OUT_OF_SHEET = "Element rysunku wychodzi poza kartkę.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static DrawStrokeItem stroke(double[]... points) {
        return new DrawStrokeItem(4.0, true, points);
    }

    private static DrawTextItem text(String value) {
        return new DrawTextItem(value, 10.0, 10.0, 12.0, false);
    }

    private static DrawMarkerItem marker(String color, String name) {
        return new DrawMarkerItem(null, DrawingMarkerShape.DIAMOND, color, true, name, 10.0, 10.0, 20.0);
    }

    private JsonNode normalize(List<DrawItem> items) throws Exception {
        return objectMapper.readTree(DrawingItems.normalize(items, WIDTH, HEIGHT));
    }

    private static void assertRejected(List<DrawItem> items, String message) {
        assertThatThrownBy(() -> DrawingItems.normalize(items, WIDTH, HEIGHT))
                .isInstanceOf(ValidationException.class)
                .hasMessage(message);
    }

    @Test
    void normalize_writesKindAndFields_lowercasesColor_andTrimsMarkerName() throws Exception {
        JsonNode result = normalize(List.of(
                stroke(new double[]{1, 2, 0.25}),
                new DrawTextItem("Ból", 5.0, 6.0, 30.0, true),
                new DrawMarkerItem(8L, DrawingMarkerShape.HEXAGON, "#AbCdEf", false, "  Modzel  ", 7.0, 8.0, 15.0)));

        assertThat(result.toString()).isEqualTo("[{\"kind\":\"stroke\",\"width\":4.0,\"pressureSensitive\":true,"
                + "\"points\":[[1.0,2.0,0.25]]},"
                + "{\"kind\":\"text\",\"text\":\"Ból\",\"x\":5.0,\"y\":6.0,\"size\":30.0,\"bold\":true},"
                + "{\"kind\":\"marker\",\"markerId\":8,\"shape\":\"HEXAGON\",\"color\":\"#abcdef\",\"filled\":false,"
                + "\"name\":\"Modzel\",\"x\":7.0,\"y\":8.0,\"size\":15.0}]");
    }

    @Test
    void normalize_isUtf8_andDeterministic() {
        List<DrawItem> items = List.of(text("Źdźbło"), marker("#FF0000", "Modzel"));

        byte[] first = DrawingItems.normalize(items, WIDTH, HEIGHT);
        byte[] second = DrawingItems.normalize(List.of(text("Źdźbło"), marker("#ff0000", " Modzel")), WIDTH, HEIGHT);

        assertThat(first).isEqualTo(second);
        assertThat(new String(first, StandardCharsets.UTF_8)).contains("Źdźbło");
    }

    @Test
    void normalize_emptyList_isEmptyArray() {
        assertThat(new String(DrawingItems.normalize(List.of(), WIDTH, HEIGHT), StandardCharsets.UTF_8)).isEqualTo("[]");
    }

    @Test
    void normalize_markerWithoutDefinitionId_isAllowed() throws Exception {
        assertThat(normalize(List.of(marker("#000000", "Inne"))).get(0).get("markerId").isNull()).isTrue();
    }

    @Test
    void normalize_acceptsEdgeToleranceOf50() {
        List<DrawItem> items = List.of(
                stroke(new double[]{-50, -50, 0}, new double[]{150, 250, 1}),
                new DrawTextItem("A", 150.0, 250.0, 1000.0, false),
                new DrawMarkerItem(null, DrawingMarkerShape.CROSS, "#000000", false, "x".repeat(40), -50.0, 250.0, 1000.0),
                new DrawStrokeItem(200.0, false, new double[][]{{0, 0, 0}}));

        assertThat(DrawingItems.normalize(items, WIDTH, HEIGHT)).isNotEmpty();
    }

    @Test
    void normalize_rejectsCoordinatesBeyondTolerance() {
        assertRejected(List.of(stroke(new double[]{150.01, 10, 0.5})), OUT_OF_SHEET);
        assertRejected(List.of(stroke(new double[]{10, -50.01, 0.5})), OUT_OF_SHEET);
        assertRejected(List.of(new DrawTextItem("A", 10.0, 250.5, 12.0, false)), OUT_OF_SHEET);
        assertRejected(List.of(new DrawMarkerItem(null, DrawingMarkerShape.CIRCLE, "#000000", true, "A",
                -51.0, 10.0, 12.0)), OUT_OF_SHEET);
        assertRejected(List.of(new DrawTextItem("A", WIDTH + 100.0, 10.0, 12.0, false)), OUT_OF_SHEET);
    }

    @Test
    void normalize_rejectsNonFiniteOrMissingNumbers() {
        assertRejected(List.of(stroke(new double[]{Double.NaN, 10, 0.5})), INVALID);
        assertRejected(List.of(stroke(new double[]{10, Double.POSITIVE_INFINITY, 0.5})), INVALID);
        assertRejected(List.of(stroke(new double[]{10, 10, Double.NaN})), INVALID);
        assertRejected(List.of(new DrawTextItem("A", null, 10.0, 12.0, false)), INVALID);
        assertRejected(List.of(new DrawTextItem("A", 10.0, 10.0, Double.NaN, false)), INVALID);
        assertRejected(List.of(new DrawStrokeItem(Double.POSITIVE_INFINITY, true, new double[][]{{1, 1, 1}})), INVALID);
    }

    @Test
    void normalize_rejectsInvalidStroke() {
        assertRejected(List.of(new DrawStrokeItem(0.0, true, new double[][]{{1, 1, 1}})), INVALID);
        assertRejected(List.of(new DrawStrokeItem(200.01, true, new double[][]{{1, 1, 1}})), INVALID);
        assertRejected(List.of(new DrawStrokeItem(null, true, new double[][]{{1, 1, 1}})), INVALID);
        assertRejected(List.of(new DrawStrokeItem(2.0, null, new double[][]{{1, 1, 1}})), INVALID);
        assertRejected(List.of(new DrawStrokeItem(2.0, true, null)), INVALID);
        assertRejected(List.of(new DrawStrokeItem(2.0, true, new double[0][])), INVALID);
        assertRejected(List.of(new DrawStrokeItem(2.0, true, new double[][]{{1, 1}})), INVALID);
        assertRejected(List.of(new DrawStrokeItem(2.0, true, new double[][]{{1, 1, 1, 1}})), INVALID);
        assertRejected(List.of(new DrawStrokeItem(2.0, true, new double[][]{null})), INVALID);
        assertRejected(List.of(stroke(new double[]{1, 1, 1.01})), INVALID);
        assertRejected(List.of(stroke(new double[]{1, 1, -0.01})), INVALID);
    }

    @Test
    void normalize_strokePointLimit_is20000() {
        double[][] max = new double[DrawingItems.MAX_POINTS][];
        Arrays.fill(max, new double[]{1, 1, 1});
        double[][] over = new double[DrawingItems.MAX_POINTS + 1][];
        Arrays.fill(over, new double[]{1, 1, 1});

        assertThat(DrawingItems.normalize(List.of(new DrawStrokeItem(2.0, true, max)), WIDTH, HEIGHT)).isNotEmpty();
        assertRejected(List.of(new DrawStrokeItem(2.0, true, over)), "Kreska ma za dużo punktów (maksymalnie 20000).");
    }

    @Test
    void normalize_rejectsInvalidText() {
        String message = "Napis na rysunku musi mieć od 1 do 500 znaków.";
        assertThat(DrawingItems.normalize(List.of(text("x".repeat(500))), WIDTH, HEIGHT)).isNotEmpty();
        assertRejected(List.of(text("x".repeat(501))), message);
        assertRejected(List.of(text("")), message);
        assertRejected(List.of(text("   ")), message);
        assertRejected(List.of(text(null)), message);
        assertRejected(List.of(new DrawTextItem("A", 10.0, 10.0, 1000.01, false)), INVALID);
        assertRejected(List.of(new DrawTextItem("A", 10.0, 10.0, 0.0, false)), INVALID);
        assertRejected(List.of(new DrawTextItem("A", 10.0, 10.0, 12.0, null)), INVALID);
    }

    @Test
    void normalize_rejectsInvalidMarker() {
        String colorMessage = "Nieprawidłowy kolor oznaczenia na rysunku.";
        String nameMessage = "Nazwa oznaczenia na rysunku musi mieć od 1 do 40 znaków.";
        assertRejected(List.of(marker("abc", "Modzel")), colorMessage);
        assertRejected(List.of(marker("red", "Modzel")), colorMessage);
        assertRejected(List.of(marker("#ff00000", "Modzel")), colorMessage);
        assertRejected(List.of(marker(null, "Modzel")), colorMessage);
        assertRejected(List.of(marker("#ff0000", "")), nameMessage);
        assertRejected(List.of(marker("#ff0000", null)), nameMessage);
        assertRejected(List.of(marker("#ff0000", "x".repeat(41))), nameMessage);
        assertRejected(List.of(new DrawMarkerItem(null, null, "#ff0000", true, "A", 10.0, 10.0, 20.0)), INVALID);
        assertRejected(List.of(new DrawMarkerItem(null, DrawingMarkerShape.CIRCLE, "#ff0000", null, "A",
                10.0, 10.0, 20.0)), INVALID);
        assertRejected(List.of(new DrawMarkerItem(null, DrawingMarkerShape.CIRCLE, "#ff0000", true, "A",
                10.0, 10.0, 1001.0)), INVALID);
    }

    @Test
    void normalize_rejectsNullListOrNullItem() {
        assertRejected(null, INVALID);
        assertRejected(Collections.singletonList(null), INVALID);
    }

    @Test
    void normalize_itemLimit_is5000() {
        List<DrawItem> max = new ArrayList<>(Collections.nCopies(DrawingItems.MAX_ITEMS, text("A")));
        List<DrawItem> over = new ArrayList<>(Collections.nCopies(DrawingItems.MAX_ITEMS + 1, text("A")));

        assertThat(DrawingItems.normalize(max, WIDTH, HEIGHT)).isNotEmpty();
        assertRejected(over, "Rysunek ma za dużo elementów (maksymalnie 5000).");
    }

    @Test
    void normalize_rejectsContentOver3Mb() {
        double[][] points = new double[DrawingItems.MAX_POINTS][];
        Arrays.fill(points, new double[]{12.5, 12.5, 0.5});
        List<DrawItem> items = new ArrayList<>(Collections.nCopies(12, new DrawStrokeItem(2.0, true, points)));

        assertRejected(items, "Rysunek jest za duży (maksymalnie 3 MB).");
    }
}
