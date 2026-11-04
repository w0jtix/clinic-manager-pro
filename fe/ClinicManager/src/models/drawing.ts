export interface DrawingTemplateImageInfo {
  id: number;
  width: number;
  height: number;
  sizeBytes: number;
}

export interface DrawingTemplateSettings {
  id: number;
  name: string;
  displayOrder: number;
  archived: boolean;
  archivedAt: string | null;
  image: DrawingTemplateImageInfo | null;
  clientCount: number;
}

export interface DrawingTemplateImageRef {
  id: number;
  width: number;
  height: number;
}

export interface DrawingTemplateRef {
  id: number;
  name: string;
  archived: boolean;
  image: DrawingTemplateImageRef | null;
}

export type DrawingTemplateDeleteResult = "DELETED" | "ARCHIVED";

export const MAX_ACTIVE_DRAWING_TEMPLATES = 10;

export interface DrawingUser {
  userId: number;
  name: string;
  color: string;
}

export interface DrawingLayer {
  id: number;
  authorUserId: number;
  authorName: string;
  color: string;
  version: number;
  updatedAt: string | null;
  updatedByUserId: number | null;
  updatedByName: string | null;
}

export interface DrawingSheet {
  id: number;
  sheetNumber: number;
  templateId: number;
  templateImageId: number | null;
  width: number;
  height: number;
  createdAt: string;
  closedAt: string | null;
  layers: DrawingLayer[];
}

export interface DrawingGroup {
  template: DrawingTemplateRef;
  currentSheet: DrawingSheet | null;
  closedSheets: DrawingSheet[];
}

export interface ClientDrawing {
  currentUser: DrawingUser;
  groups: DrawingGroup[];
}

export interface DrawingConflictError {
  code: "DRAWING_CONFLICT";
  currentVersion: number;
}

export const DEFAULT_SHEET_SIZE = { width: 1536, height: 2048 };

export type DrawingMarkerShape =
  | "CIRCLE"
  | "SQUARE"
  | "RECTANGLE"
  | "TRIANGLE"
  | "DIAMOND"
  | "HEXAGON"
  | "CROSS"
  | "PLUS";

export const DRAWING_MARKER_SHAPES: { value: DrawingMarkerShape; label: string }[] = [
  { value: "CIRCLE", label: "Koło" },
  { value: "SQUARE", label: "Kwadrat" },
  { value: "RECTANGLE", label: "Prostokąt" },
  { value: "TRIANGLE", label: "Trójkąt" },
  { value: "DIAMOND", label: "Romb" },
  { value: "HEXAGON", label: "Sześciokąt" },
  { value: "CROSS", label: "Krzyżyk" },
  { value: "PLUS", label: "Plus" },
];

export interface DrawingMarker {
  id: number;
  name: string;
  shape: DrawingMarkerShape;
  color: string;
  filled: boolean;
  displayOrder: number;
}

export type DrawingMarkerInput = Pick<DrawingMarker, "name" | "shape" | "color" | "filled">;

export const MAX_DRAWING_MARKERS = 30;
export const DRAWING_MARKER_NAME_MAX_LENGTH = 40;

export interface StoredStroke {
  kind: "stroke";
  width: number;
  pressureSensitive: boolean;
  points: [number, number, number][];
}

export interface StoredText {
  kind: "text";
  text: string;
  x: number;
  y: number;
  size: number;
  bold: boolean;
}

export interface StoredMarker {
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

export type StoredDrawItem = StoredStroke | StoredText | StoredMarker;

export const OWN_STROKE_COLOR = "#111111";
