import ClientCardService from "../services/ClientCardService";
import {
  DEFAULT_SHEET_SIZE,
  DrawingConflictError,
  DrawingGroup,
  DrawingMarkerShape,
  StoredDrawItem,
} from "../models/drawing";

export interface SheetSize {
  width: number;
  height: number;
}

export interface StrokePoint {
  x: number;
  y: number;
  pressure: number;
}

export interface Stroke {
  kind: "stroke";
  width: number;
  pressureSensitive: boolean;
  points: StrokePoint[];
}

export const resolveEditableSheetSize = (group: DrawingGroup): SheetSize => {
  if (group.currentSheet) {
    return { width: group.currentSheet.width, height: group.currentSheet.height };
  }
  if (group.template.image) {
    return { width: group.template.image.width, height: group.template.image.height };
  }
  return DEFAULT_SHEET_SIZE;
};

export const resolveEditableTemplateImageId = (group: DrawingGroup): number | null =>
  group.currentSheet ? group.currentSheet.templateImageId : group.template.image?.id ?? null;

export const getDrawingConflict = (error: unknown): DrawingConflictError | null => {
  const response = (error as { response?: { status?: number; data?: unknown } } | null)?.response;
  const data = response?.data as Partial<DrawingConflictError> | undefined;
  return response?.status === 409 && data && typeof data === "object" && data.code === "DRAWING_CONFLICT"
    ? (data as DrawingConflictError)
    : null;
};

const templateUrlCache = new Map<number, Promise<string>>();

export const getTemplateImageUrl = (imageId: number): Promise<string> => {
  const cached = templateUrlCache.get(imageId);
  if (cached) return cached;
  const request = ClientCardService.getDrawingTemplateImage(imageId)
    .then((blob) => URL.createObjectURL(blob))
    .catch((error) => {
      templateUrlCache.delete(imageId);
      throw error;
    });
  templateUrlCache.set(imageId, request);
  return request;
};

export const resolveDrawingFont = (): string =>
  getComputedStyle(document.documentElement).getPropertyValue("--font-h2-outfit").trim() || "Outfit, sans-serif";

export const strokeWidthAt = (stroke: Stroke, pressure: number): number =>
  stroke.pressureSensitive ? stroke.width * (0.4 + Math.min(Math.max(pressure, 0), 1) * 1.2) : stroke.width;

export const drawStroke = (ctx: CanvasRenderingContext2D, stroke: Stroke, color: string, fromIndex = 0): void => {
  const { points } = stroke;
  if (points.length === 0) return;
  ctx.save();
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  if (points.length === 1) {
    const [point] = points;
    ctx.beginPath();
    ctx.arc(point.x, point.y, strokeWidthAt(stroke, point.pressure) / 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    return;
  }
  for (let i = Math.max(1, fromIndex); i < points.length; i++) {
    const prev = points[i - 1];
    const point = points[i];
    ctx.beginPath();
    ctx.lineWidth = strokeWidthAt(stroke, (prev.pressure + point.pressure) / 2);
    ctx.moveTo(prev.x, prev.y);
    ctx.lineTo(point.x, point.y);
    ctx.stroke();
  }
  ctx.restore();
};

export const TEXT_LINE_HEIGHT = 1.25;
export const TEXT_SIZE_RATIOS = [0.025, 0.04, 0.06];
export const MARKER_SIZE_RATIOS = [0.0225, 0.03, 0.045];

export interface TextItem {
  kind: "text";
  text: string;
  x: number;
  y: number;
  size: number;
  bold: boolean;
}

export interface MarkerItem {
  kind: "marker";
  markerId: number | null;
  shape: DrawingMarkerShape;
  color: string;
  filled: boolean;
  name: string;
  x: number;
  y: number;
  size: number;
}

export type DrawItem = Stroke | TextItem | MarkerItem;

export const isTextItem = (item: DrawItem): item is TextItem => item.kind === "text";

export const isMarkerItem = (item: DrawItem): item is MarkerItem => item.kind === "marker";

export const textFont = (size: number, bold: boolean, family: string): string =>
  `${bold ? 700 : 400} ${size}px ${family}`;

export const measureTextBlock = (
  ctx: CanvasRenderingContext2D,
  text: string,
  size: number,
  bold: boolean,
  family: string
): { width: number; height: number } => {
  ctx.save();
  ctx.font = textFont(size, bold, family);
  const lines = text.split("\n");
  const width = Math.max(...lines.map((line) => ctx.measureText(line || " ").width));
  ctx.restore();
  return { width, height: lines.length * size * TEXT_LINE_HEIGHT };
};

export const drawTextItem = (ctx: CanvasRenderingContext2D, item: TextItem, color: string, family: string): void => {
  ctx.save();
  ctx.font = textFont(item.size, item.bold, family);
  ctx.fillStyle = color;
  ctx.textBaseline = "top";
  item.text.split("\n").forEach((line, index) => {
    ctx.fillText(line, item.x, item.y + index * item.size * TEXT_LINE_HEIGHT + item.size * (TEXT_LINE_HEIGHT - 1) * 0.5);
  });
  ctx.restore();
};

export const isLineMarker = (shape: DrawingMarkerShape): boolean => shape === "CROSS" || shape === "PLUS";

const round = (value: number, digits: number): number => {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
};

export const markerPathData = (shape: DrawingMarkerShape, size: number): string => {
  const r = size / 2;
  const h = round(r * 0.866, 3);
  const c = r * 0.75;
  switch (shape) {
    case "CIRCLE":
      return `M ${-r} 0 A ${r} ${r} 0 1 0 ${r} 0 A ${r} ${r} 0 1 0 ${-r} 0 Z`;
    case "SQUARE":
      return `M ${-r} ${-r} H ${r} V ${r} H ${-r} Z`;
    case "RECTANGLE":
      return `M ${-r} ${-r * 0.6} H ${r} V ${r * 0.6} H ${-r} Z`;
    case "TRIANGLE":
      return `M 0 ${-r} L ${r} ${r * 0.8} L ${-r} ${r * 0.8} Z`;
    case "DIAMOND":
      return `M 0 ${-r} L ${r} 0 L 0 ${r} L ${-r} 0 Z`;
    case "HEXAGON":
      return `M ${r} 0 L ${r / 2} ${h} L ${-r / 2} ${h} L ${-r} 0 L ${-r / 2} ${-h} L ${r / 2} ${-h} Z`;
    case "CROSS":
      return `M ${-c} ${-c} L ${c} ${c} M ${c} ${-c} L ${-c} ${c}`;
    case "PLUS":
      return `M 0 ${-r} L 0 ${r} M ${-r} 0 L ${r} 0`;
  }
};

export const markerStrokeWidth = (shape: DrawingMarkerShape, filled: boolean, size: number): number =>
  isLineMarker(shape) && filled ? size * 0.2 : size * 0.11;

export const drawMarker = (ctx: CanvasRenderingContext2D, marker: MarkerItem): void => {
  ctx.save();
  ctx.translate(marker.x, marker.y);
  const path = new Path2D(markerPathData(marker.shape, marker.size));
  ctx.lineJoin = "round";
  ctx.lineCap = "round";
  ctx.strokeStyle = marker.color;
  ctx.fillStyle = marker.color;
  ctx.lineWidth = markerStrokeWidth(marker.shape, marker.filled, marker.size);
  if (marker.filled && !isLineMarker(marker.shape)) ctx.fill(path);
  ctx.stroke(path);
  ctx.restore();
};

export const drawMarkerLabel = (ctx: CanvasRenderingContext2D, marker: MarkerItem, family: string): void => {
  if (!marker.name) return;
  const presets = MARKER_SIZE_RATIOS.map((ratio) => Math.round(ctx.canvas.width * ratio));
  const preset = presets.reduce((best, value) =>
    Math.abs(value - marker.size) < Math.abs(best - marker.size) ? value : best
  );
  const size = preset * 0.6;
  ctx.save();
  ctx.font = textFont(size, true, family);
  ctx.textBaseline = "middle";
  ctx.lineJoin = "round";
  ctx.lineWidth = size * 0.3;
  ctx.strokeStyle = "#ffffff";
  ctx.fillStyle = marker.color;
  const x = marker.x + marker.size / 2 + marker.size * 0.3;
  const lines = wrapLabel(ctx, marker.name, ctx.canvas.width - x - size * 0.3);
  const lineHeight = size * TEXT_LINE_HEIGHT;
  const top = marker.y - ((lines.length - 1) * lineHeight) / 2;
  lines.forEach((line, index) => {
    ctx.strokeText(line, x, top + index * lineHeight);
    ctx.fillText(line, x, top + index * lineHeight);
  });
  ctx.restore();
};

const wrapLabel = (ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] => {
  const words = text.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  words.forEach((word) => {
    const last = lines[lines.length - 1];
    const candidate = last ? `${last} ${word}` : word;
    if (last && ctx.measureText(candidate).width > maxWidth) {
      lines.push(word);
    } else if (last) {
      lines[lines.length - 1] = candidate;
    } else {
      lines.push(word);
    }
  });
  return lines;
};

export const drawItem = (ctx: CanvasRenderingContext2D, item: DrawItem, color: string, family: string): void => {
  if (isTextItem(item)) {
    drawTextItem(ctx, item, color, family);
  } else if (isMarkerItem(item)) {
    drawMarker(ctx, item);
  } else {
    drawStroke(ctx, item, color);
  }
};

export const renderItems = (
  ctx: CanvasRenderingContext2D,
  items: DrawItem[],
  color: string,
  family: string,
  showMarkerNames = false
): void => {
  items.filter((item) => !isMarkerItem(item)).forEach((item) => drawItem(ctx, item, color, family));
  const markers = items.filter(isMarkerItem);
  markers.forEach((item) => drawMarker(ctx, item));
  if (showMarkerNames) markers.forEach((item) => drawMarkerLabel(ctx, item, family));
};

export const toStoredItems = (items: DrawItem[]): StoredDrawItem[] =>
  items.map((item): StoredDrawItem => {
    if (isTextItem(item) || isMarkerItem(item)) {
      return { ...item, x: round(item.x, 1), y: round(item.y, 1), size: round(item.size, 1) };
    }
    return {
      kind: "stroke",
      width: round(item.width, 2),
      pressureSensitive: item.pressureSensitive,
      points: item.points.map((p) => [round(p.x, 1), round(p.y, 1), round(p.pressure, 2)]),
    };
  });

export const fromStoredItems = (items: StoredDrawItem[]): DrawItem[] =>
  items.map((item): DrawItem => {
    if (item.kind === "stroke") {
      return {
        kind: "stroke",
        width: item.width,
        pressureSensitive: item.pressureSensitive,
        points: item.points.map(([x, y, pressure]) => ({ x, y, pressure })),
      };
    }
    return { ...item };
  });

const distanceToSegment = (p: { x: number; y: number }, a: StrokePoint, b: StrokePoint): number => {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lengthSq = dx * dx + dy * dy;
  const t = lengthSq === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq));
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
};

export const hitsItem = (
  ctx: CanvasRenderingContext2D,
  item: DrawItem,
  point: { x: number; y: number },
  tolerance: number,
  family: string
): boolean => {
  if (isMarkerItem(item)) {
    const reach = item.size / 2 + tolerance;
    return Math.abs(point.x - item.x) <= reach && Math.abs(point.y - item.y) <= reach;
  }
  if (isTextItem(item)) {
    const block = measureTextBlock(ctx, item.text, item.size, item.bold, family);
    return (
      point.x >= item.x - tolerance &&
      point.x <= item.x + block.width + tolerance &&
      point.y >= item.y - tolerance &&
      point.y <= item.y + block.height + tolerance
    );
  }
  const { points } = item;
  const reach = item.width * (item.pressureSensitive ? 0.8 : 0.5) + tolerance;
  if (points.length === 1) return Math.hypot(point.x - points[0].x, point.y - points[0].y) <= reach;
  for (let i = 1; i < points.length; i++) {
    if (distanceToSegment(point, points[i - 1], points[i]) <= reach) return true;
  }
  return false;
};
