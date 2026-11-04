import { describe, expect, it } from "vitest";
import {
  fromStoredItems,
  getDrawingConflict,
  hitsItem,
  isMarkerItem,
  isTextItem,
  MarkerItem,
  resolveEditableSheetSize,
  resolveEditableTemplateImageId,
  Stroke,
  strokeWidthAt,
  textFont,
  TextItem,
  toStoredItems,
} from "../../utils/drawingUtils";
import { DEFAULT_SHEET_SIZE, DrawingGroup } from "../../models/drawing";

const group = (overrides: Partial<DrawingGroup>): DrawingGroup => ({
  template: { id: 3, name: "Stopy", archived: false, image: null },
  currentSheet: null,
  closedSheets: [],
  ...overrides,
});

const stroke = (overrides: Partial<Stroke>): Stroke => ({
  kind: "stroke",
  width: 6,
  pressureSensitive: false,
  points: [],
  ...overrides,
});

describe("drawing sheet resolution", () => {
  it("uses the current sheet size and template image when a sheet exists", () => {
    const value = group({
      template: { id: 3, name: "Stopy", archived: false, image: { id: 9, width: 1000, height: 1400 } },
      currentSheet: {
        id: 5,
        sheetNumber: 1,
        templateId: 3,
        templateImageId: 7,
        width: 1200,
        height: 1600,
        closedAt: null,
        layers: [],
      },
    });
    expect(resolveEditableSheetSize(value)).toEqual({ width: 1200, height: 1600 });
    expect(resolveEditableTemplateImageId(value)).toBe(7);
  });

  it("falls back to the template image, then to the blank default size", () => {
    const withImage = group({
      template: { id: 3, name: "Stopy", archived: false, image: { id: 9, width: 1000, height: 1400 } },
    });
    expect(resolveEditableSheetSize(withImage)).toEqual({ width: 1000, height: 1400 });
    expect(resolveEditableTemplateImageId(withImage)).toBe(9);
    expect(resolveEditableSheetSize(group({}))).toEqual(DEFAULT_SHEET_SIZE);
    expect(resolveEditableTemplateImageId(group({}))).toBeNull();
  });
});

describe("drawing helpers", () => {
  it("scales the stroke width with pen pressure only for pressure-sensitive strokes", () => {
    expect(strokeWidthAt(stroke({}), 1)).toBe(6);
    expect(strokeWidthAt(stroke({ pressureSensitive: true }), 0)).toBeCloseTo(2.4);
    expect(strokeWidthAt(stroke({ pressureSensitive: true }), 1)).toBeCloseTo(9.6);
    expect(strokeWidthAt(stroke({ pressureSensitive: true }), 5)).toBeCloseTo(9.6);
  });


  it("tells text items apart from strokes and builds the canvas font", () => {
    const text: TextItem = { kind: "text", text: "Odcisk", x: 10, y: 20, size: 48, bold: true };
    expect(isTextItem(text)).toBe(true);
    expect(isTextItem(stroke({}))).toBe(false);
    expect(textFont(48, true, "Outfit")).toBe("700 48px Outfit");
    expect(textFont(32, false, "Outfit")).toBe("400 32px Outfit");
  });

  it("recognizes the drawing version conflict response", () => {
    expect(
      getDrawingConflict({ response: { status: 409, data: { code: "DRAWING_CONFLICT", currentVersion: 4 } } })
    ).toEqual({ code: "DRAWING_CONFLICT", currentVersion: 4 });
    expect(getDrawingConflict({ response: { status: 409, data: "Kartka jest zamknięta." } })).toBeNull();
    expect(getDrawingConflict(new Error("network"))).toBeNull();
  });
});

const marker = (overrides: Partial<MarkerItem>): MarkerItem => ({
  kind: "marker",
  markerId: 1,
  shape: "CIRCLE",
  color: "#e53935",
  filled: true,
  name: "Odcisk",
  x: 100,
  y: 100,
  size: 40,
  ...overrides,
});

const noCtx = {} as CanvasRenderingContext2D;

describe("drawing objects", () => {
  it("round-trips items through the stored format with rounded coordinates", () => {
    const items = [
      stroke({ points: [{ x: 1.234, y: 5.678, pressure: 0.456 }] }),
      marker({ x: 10.06, y: 20.04 }),
    ];
    const stored = toStoredItems(items);
    expect(stored[0]).toEqual({ kind: "stroke", width: 6, pressureSensitive: false, points: [[1.2, 5.7, 0.46]] });
    expect(stored[1]).toMatchObject({ kind: "marker", x: 10.1, y: 20 });
    const restored = fromStoredItems(stored);
    expect(restored[0]).toEqual(stroke({ points: [{ x: 1.2, y: 5.7, pressure: 0.46 }] }));
    expect(isMarkerItem(restored[1])).toBe(true);
  });

  it("hits a stroke near its segment and misses it further away", () => {
    const line = stroke({ width: 6, points: [{ x: 0, y: 0, pressure: 0.5 }, { x: 100, y: 0, pressure: 0.5 }] });
    expect(hitsItem(noCtx, line, { x: 50, y: 5 }, 4, "Outfit")).toBe(true);
    expect(hitsItem(noCtx, line, { x: 50, y: 20 }, 4, "Outfit")).toBe(false);
  });

  it("hits a marker within its box", () => {
    expect(hitsItem(noCtx, marker({}), { x: 115, y: 85 }, 2, "Outfit")).toBe(true);
    expect(hitsItem(noCtx, marker({}), { x: 140, y: 100 }, 2, "Outfit")).toBe(false);
  });
});
