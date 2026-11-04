import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import ReactDOM from "react-dom";
import {
  Bold,
  Check,
  ChevronLeft,
  Eraser,
  Eye,
  EyeOff,
  Hand,
  Minimize2,
  Minus,
  Move,
  Plus,
  Redo2,
  Save,
  Shapes,
  Trash2,
  Type,
  Undo2,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import { DrawingLegend, LayerCanvas, LegendEntry, MarkerShapeIcon } from "./DrawingLayers";
import { DrawingLayer, DrawingMarker, OWN_STROKE_COLOR, StoredDrawItem } from "../../models/drawing";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import {
  DrawItem,
  drawStroke,
  hitsItem,
  isMarkerItem,
  isTextItem,
  MARKER_SIZE_RATIOS,
  MarkerItem,
  measureTextBlock,
  renderItems,
  resolveDrawingFont,
  Stroke,
  StrokePoint,
  TEXT_LINE_HEIGHT,
  TEXT_SIZE_RATIOS,
  TextItem,
  toStoredItems,
} from "../../utils/drawingUtils";

const STROKE_WIDTHS = [3, 6, 12];
const ERASER_TOLERANCE_PX = 14;
const TAP_SLOP_PX = 6;
const OWN_LAYER_KEY = -1;
const MARKER_NAMES_STORAGE_KEY = "dw-marker-names-visible";
const SIZE_STEP = 1.2;
const SIZE_MIN_FACTOR = 0.5;
const SIZE_MAX_FACTOR = 2.5;
const ARROW_PAN_STEP = 0.1;
const ARROW_PAN_FAST = 0.3;
const ARROW_DIRECTIONS: Record<string, { x: number; y: number }> = {
  ArrowUp: { x: 0, y: -1 },
  ArrowDown: { x: 0, y: 1 },
  ArrowLeft: { x: -1, y: 0 },
  ArrowRight: { x: 1, y: 0 },
};
const MAX_ZOOM = 5;
const ZOOM_STEP = 1.25;
const MIN_INPUT_FONT_PX = 16;
const KEYBOARD_CLOSE_THRESHOLD = 120;
const TEXT_HANDLE_OFFSET_REM = 1.6;

interface View {
  zoom: number;
  x: number;
  y: number;
}

interface PointerInfo {
  x: number;
  y: number;
  type: string;
}

const IDENTITY_VIEW: View = { zoom: 1, x: 0, y: 0 };

interface Size {
  width: number;
  height: number;
}

const minZoomFor = (sheet: Size, frame: Size): number =>
  sheet.width > 0 && sheet.height > 0 ? Math.min(1, frame.width / sheet.width, frame.height / sheet.height) : 1;

const clampAxis = (offset: number, sheetLength: number, frameLength: number): number =>
  sheetLength <= frameLength
    ? (frameLength - sheetLength) / 2
    : Math.min(0, Math.max(frameLength - sheetLength, offset));

const clampToFrame = (next: View, sheet: Size, frame: Size): View => {
  const zoom = Math.min(Math.max(next.zoom, minZoomFor(sheet, frame)), MAX_ZOOM);
  return {
    zoom,
    x: clampAxis(next.x, sheet.width * zoom, frame.width),
    y: clampAxis(next.y, sheet.height * zoom, frame.height),
  };
};

const canPan = (view: View, sheet: Size, frame: Size): boolean =>
  sheet.width * view.zoom > frame.width + 1 || sheet.height * view.zoom > frame.height + 1;

type Tool = "pen" | "eraser" | "text" | "marker" | "hand";

interface PendingText {
  x: number;
  y: number;
  text: string;
}

const readMarkerNamesVisible = (): boolean => {
  try {
    return localStorage.getItem(MARKER_NAMES_STORAGE_KEY) !== "0";
  } catch {
    return true;
  }
};

const writeMarkerNamesVisible = (visible: boolean) => {
  try {
    localStorage.setItem(MARKER_NAMES_STORAGE_KEY, visible ? "1" : "0");
  } catch {
    return;
  }
};

export interface DrawingOtherLayer {
  layer: DrawingLayer;
  items: DrawItem[];
}

export interface DrawingEditorProps {
  title: string;
  width: number;
  height: number;
  templateUrl: string | null;
  ownName: string;
  ownItems: DrawItem[];
  otherLayers: DrawingOtherLayer[];
  markers: DrawingMarker[];
  readOnly?: boolean;
  footerText?: string | null;
  onClose: () => void;
  onSave?: (items: StoredDrawItem[]) => Promise<boolean>;
  onDirtyChange?: (dirty: boolean, save: (() => Promise<boolean>) | null) => void;
}

const sameStrokes = (a: DrawItem[], b: DrawItem[]): boolean =>
  a.length === b.length && a.every((stroke, index) => stroke === b[index]);

export const toLegendEntry = (layer: DrawingLayer): LegendEntry => ({
  key: layer.id,
  color: layer.color,
  name: layer.authorName,
});

export function DrawingEditor({
  title,
  width,
  height,
  templateUrl,
  ownName,
  ownItems,
  otherLayers,
  markers,
  readOnly = false,
  footerText = null,
  onClose,
  onSave,
  onDirtyChange,
}: DrawingEditorProps) {
  const { isMobile } = useBreakpoint();
  const stageRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const itemsRef = useRef<DrawItem[]>([]);
  const savedItemsRef = useRef<DrawItem[]>([]);
  const undoRef = useRef<DrawItem[][]>([]);
  const redoRef = useRef<DrawItem[][]>([]);
  const eraseBaseRef = useRef<DrawItem[] | null>(null);
  const liftRef = useRef<{ base: DrawItem[]; index: number } | null>(null);
  const markerDragRef = useRef<{
    pointerId: number;
    start: { x: number; y: number };
    origin: { x: number; y: number };
    last: { x: number; y: number };
    pinched: boolean;
  } | null>(null);
  const markerPinchRef = useRef<{
    pointerId: number;
    last: { x: number; y: number };
    startDistance: number;
    startSize: number;
  } | null>(null);
  const fontFamilyRef = useRef<string>(resolveDrawingFont());
  const textAreaRef = useRef<HTMLTextAreaElement>(null);
  const textDragRef = useRef<{ pointerId: number; start: { x: number; y: number }; origin: { x: number; y: number } } | null>(null);
  const currentStrokeRef = useRef<Stroke | null>(null);
  const tapRef = useRef<{ pointerId: number; x: number; y: number; point: { x: number; y: number } } | null>(null);
  const activePointerRef = useRef<number | null>(null);
  const viewportRef = useRef<HTMLDivElement>(null);
  const pointersRef = useRef<Map<number, PointerInfo>>(new Map());
  const gestureRef = useRef<{ distance: number; mid: { x: number; y: number }; view: View } | null>(null);
  const panRef = useRef<{ pointerId: number; start: { x: number; y: number }; view: View } | null>(null);
  const [view, setView] = useState<View>(IDENTITY_VIEW);
  const viewRef = useRef<View>(IDENTITY_VIEW);
  viewRef.current = view;
  const spaceHeldRef = useRef<boolean>(false);
  const [spaceHeld, setSpaceHeld] = useState<boolean>(false);
  const [displaySize, setDisplaySize] = useState<Size>({ width: 0, height: 0 });
  const displaySizeRef = useRef<Size>({ width: 0, height: 0 });
  const [frameSize, setFrameSize] = useState<Size>({ width: 0, height: 0 });
  const frameSizeRef = useRef<Size>({ width: 0, height: 0 });
  const [widthIndex, setWidthIndex] = useState<number>(1);
  const [tool, setTool] = useState<Tool>("pen");
  const eraser = tool === "eraser";
  const [textSize, setTextSize] = useState<number>(() => Math.round(width * TEXT_SIZE_RATIOS[1]));
  const [textBold, setTextBold] = useState<boolean>(false);
  const [pendingText, setPendingText] = useState<PendingText | null>(null);
  const pendingTextRef = useRef<PendingText | null>(null);
  pendingTextRef.current = pendingText;
  const [markerSizePx, setMarkerSizePx] = useState<number>(() => Math.round(width * MARKER_SIZE_RATIOS[1]));
  const [selectedMarkerId, setSelectedMarkerId] = useState<number | null>(null);
  const [markerPickerOpen, setMarkerPickerOpen] = useState<boolean>(false);
  const shapesButtonRef = useRef<HTMLButtonElement>(null);
  const pickerAnchorRef = useRef<HTMLElement | null>(null);
  const pickerRef = useRef<HTMLDivElement>(null);
  const [pendingMarker, setPendingMarker] = useState<MarkerItem | null>(null);
  const pendingMarkerRef = useRef<MarkerItem | null>(null);
  pendingMarkerRef.current = pendingMarker;
  const [markerNamesVisible, setMarkerNamesVisible] = useState<boolean>(readMarkerNamesVisible);
  const markerNamesVisibleRef = useRef<boolean>(markerNamesVisible);
  const [, setRevision] = useState<number>(0);
  const [saving, setSaving] = useState<boolean>(false);
  const [confirmLeave, setConfirmLeave] = useState<boolean>(false);

  const hasPendingText = !!pendingText && pendingText.text.trim() !== "";
  const dirty =
    !readOnly && (hasPendingText || pendingMarker !== null || !sameStrokes(itemsRef.current, savedItemsRef.current));
  const canUndo = undoRef.current.length > 0;
  const canRedo = redoRef.current.length > 0;
  const [soloLayerId, setSoloLayerId] = useState<number | null>(null);
  const canDraw = !readOnly && (soloLayerId === null || soloLayerId === OWN_LAYER_KEY);
  const otherEntries: LegendEntry[] = otherLayers.map(({ layer }) => toLegendEntry(layer));
  const legendEntries: LegendEntry[] =
    otherEntries.length > 0
      ? [{ key: OWN_LAYER_KEY, color: OWN_STROKE_COLOR, name: `${ownName} (Ja)` }, ...otherEntries]
      : [];
  const hasMarkers =
    itemsRef.current.some(isMarkerItem) ||
    pendingMarker !== null ||
    otherLayers.some((other) => other.items.some(isMarkerItem));
  const showLegend = legendEntries.length > 0;
  const selectedMarker = markers.find((marker) => marker.id === selectedMarkerId) ?? null;

  const bump = () => setRevision((prev) => prev + 1);

  const redraw = useCallback(() => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    ctx.clearRect(0, 0, width, height);
    renderItems(ctx, itemsRef.current, OWN_STROKE_COLOR, fontFamilyRef.current, markerNamesVisibleRef.current);
  }, [width, height]);

  useEffect(() => {
    markerNamesVisibleRef.current = markerNamesVisible;
    redraw();
  }, [markerNamesVisible, redraw]);

  useEffect(() => {
    const family = fontFamilyRef.current;
    if (!document.fonts?.load) return;
    Promise.all([document.fonts.load(`400 48px ${family}`), document.fonts.load(`700 48px ${family}`)])
      .then(() => redraw())
      .catch(() => undefined);
  }, [redraw]);

  useEffect(() => {
    itemsRef.current = ownItems;
    savedItemsRef.current = ownItems;
    undoRef.current = [];
    redoRef.current = [];
    eraseBaseRef.current = null;
    liftRef.current = null;
    setPendingMarker(null);
    bump();
    redraw();
  }, [ownItems, redraw]);

  const applyItems = useCallback(
    (next: DrawItem[], base: DrawItem[] = itemsRef.current) => {
      undoRef.current = [...undoRef.current, base];
      redoRef.current = [];
      itemsRef.current = next;
      redraw();
      bump();
    },
    [redraw]
  );

  useLayoutEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const fit = () => {
      const scale = Math.min(stage.clientWidth / width, stage.clientHeight / height);
      const next = { width: Math.floor(width * scale), height: Math.floor(height * scale) };
      const frame = {
        width: Math.min(next.width, stage.clientWidth),
        height: Math.min(next.height, stage.clientHeight),
      };
      const previous = displaySizeRef.current;
      const previousFrame = frameSizeRef.current;
      if (
        previous.width === next.width &&
        previous.height === next.height &&
        previousFrame.width === frame.width &&
        previousFrame.height === frame.height
      ) {
        return;
      }
      const ratio = previous.width > 0 ? next.width / previous.width : 1;
      displaySizeRef.current = next;
      frameSizeRef.current = frame;
      setDisplaySize(next);
      setFrameSize(frame);
      setView((prev) => clampToFrame({ zoom: prev.zoom, x: prev.x * ratio, y: prev.y * ratio }, next, frame));
    };
    fit();
    const observer = new ResizeObserver(fit);
    observer.observe(stage);
    return () => observer.disconnect();
  }, [width, height]);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.add("dw-editor-open");
    return () => root.classList.remove("dw-editor-open");
  }, []);

  const toPoint = (event: PointerEvent): StrokePoint => {
    const rect = canvasRef.current!.getBoundingClientRect();
    return {
      x: ((event.clientX - rect.left) * width) / rect.width,
      y: ((event.clientY - rect.top) * height) / rect.height,
      pressure: event.pointerType === "pen" ? event.pressure || 0.5 : 0.5,
    };
  };

  const minZoom = minZoomFor(displaySize, frameSize);

  const clampView = useCallback(
    (next: View): View => clampToFrame(next, displaySize, frameSize),
    [displaySize, frameSize]
  );

  const homeView = (): View => clampToFrame(IDENTITY_VIEW, displaySizeRef.current, frameSizeRef.current);

  const viewCanPan = (target: View): boolean => canPan(target, displaySizeRef.current, frameSizeRef.current);

  const viewportPoint = (clientX: number, clientY: number) => {
    const rect = viewportRef.current!.getBoundingClientRect();
    return { x: clientX - rect.left, y: clientY - rect.top };
  };

  const zoomAround = (point: { x: number; y: number }, from: View, targetZoom: number): View => {
    const zoom = Math.min(Math.max(targetZoom, minZoom), MAX_ZOOM);
    return clampView({
      zoom,
      x: point.x - ((point.x - from.x) * zoom) / from.zoom,
      y: point.y - ((point.y - from.y) * zoom) / from.zoom,
    });
  };

  const touchPointers = () => [...pointersRef.current.values()].filter((p) => p.type === "touch");


  const fitTextInSheet = useCallback(
    (pending: PendingText): PendingText => {
      const ctx = canvasRef.current?.getContext("2d");
      if (!ctx) return pending;
      const block = measureTextBlock(ctx, pending.text || " ", textSize, textBold, fontFamilyRef.current);
      const boxWidth = block.width + textSize * 0.6;
      const displayScale = displaySizeRef.current.width > 0 ? displaySizeRef.current.width / width : 1;
      const rootFontPx = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;
      const handleExtent = (TEXT_HANDLE_OFFSET_REM * rootFontPx) / displayScale;
      return {
        ...pending,
        x: Math.max(0, Math.min(pending.x, width - boxWidth - handleExtent)),
        y: Math.max(0, Math.min(pending.y, height - block.height - handleExtent)),
      };
    },
    [textSize, textBold, width, height]
  );

  useEffect(() => {
    setPendingText((prev) => (prev ? fitTextInSheet(prev) : prev));
  }, [fitTextInSheet]);

  const resetHiddenScroll = (event: React.UIEvent<HTMLDivElement>) => {
    const element = event.currentTarget;
    if (element.scrollLeft !== 0) element.scrollLeft = 0;
    if (element.scrollTop !== 0) element.scrollTop = 0;
  };

  const commitPendingText = useCallback((): void => {
    const pending = pendingTextRef.current;
    pendingTextRef.current = null;
    setPendingText(null);
    if (!pending) return;
    setTool("pen");
    const lift = liftRef.current;
    liftRef.current = null;
    const text = pending.text.replace(/\s+$/, "");
    if (text.trim() === "") {
      if (lift) applyItems(itemsRef.current, lift.base);
      return;
    }
    const item: TextItem = {
      kind: "text",
      text,
      x: pending.x,
      y: pending.y,
      size: textSize,
      bold: textBold,
    };
    if (!lift) {
      applyItems([...itemsRef.current, item]);
      return;
    }
    const original = lift.base[lift.index];
    if (
      isTextItem(original) &&
      original.text === item.text &&
      original.x === item.x &&
      original.y === item.y &&
      original.size === item.size &&
      original.bold === item.bold
    ) {
      itemsRef.current = lift.base;
      redraw();
      bump();
      return;
    }
    const next = [...itemsRef.current];
    next.splice(lift.index, 0, item);
    applyItems(next, lift.base);
  }, [textSize, textBold, applyItems, redraw]);

  const commitPendingTextRef = useRef(commitPendingText);
  commitPendingTextRef.current = commitPendingText;

  const cancelPendingText = () => {
    pendingTextRef.current = null;
    setPendingText(null);
    setTool("pen");
    const lift = liftRef.current;
    liftRef.current = null;
    if (!lift) return;
    itemsRef.current = lift.base;
    redraw();
    bump();
  };

  const deletePendingText = () => {
    const lift = liftRef.current;
    pendingTextRef.current = null;
    setPendingText(null);
    setTool("pen");
    liftRef.current = null;
    if (lift) applyItems(itemsRef.current, lift.base);
  };

  const hitTolerance = (): number => {
    const displayScale = displaySizeRef.current.width > 0 ? displaySizeRef.current.width / width : 1;
    return ERASER_TOLERANCE_PX / (displayScale * viewRef.current.zoom);
  };

  const markerSize = (index: number): number => Math.round(width * MARKER_SIZE_RATIOS[index]);

  const clampMarker = (marker: MarkerItem): MarkerItem => ({
    ...marker,
    x: Math.max(0, Math.min(width, marker.x)),
    y: Math.max(0, Math.min(height, marker.y)),
  });

  const visibleCenter = (): { x: number; y: number } => {
    const displayScale = displaySizeRef.current.width > 0 ? displaySizeRef.current.width / width : 1;
    const current = viewRef.current;
    const frame = frameSizeRef.current;
    return {
      x: (frame.width / 2 - current.x) / current.zoom / displayScale,
      y: (frame.height / 2 - current.y) / current.zoom / displayScale,
    };
  };

  const createPendingMarker = (definition: DrawingMarker, point: { x: number; y: number }) => {
    setPendingMarker(
      clampMarker({
        kind: "marker",
        markerId: definition.id,
        shape: definition.shape,
        color: definition.color,
        filled: definition.filled,
        name: definition.name,
        x: point.x,
        y: point.y,
        size: markerSizePx,
      })
    );
  };

  const commitPendingMarker = useCallback((): void => {
    const pending = pendingMarkerRef.current;
    if (!pending) return;
    pendingMarkerRef.current = null;
    setPendingMarker(null);
    const lift = liftRef.current;
    liftRef.current = null;
    if (lift && lift.base[lift.index] === pending) {
      itemsRef.current = lift.base;
      redraw();
      bump();
      setTool("pen");
      return;
    }
    const next = [...itemsRef.current];
    next.splice(lift ? lift.index : next.length, 0, pending);
    applyItems(next, lift ? lift.base : itemsRef.current);
    setTool("pen");
  }, [applyItems, redraw]);

  const commitPendingMarkerRef = useRef(commitPendingMarker);
  commitPendingMarkerRef.current = commitPendingMarker;

  const cancelPendingMarker = () => {
    if (pendingMarkerRef.current) setTool("pen");
    pendingMarkerRef.current = null;
    setPendingMarker(null);
    const lift = liftRef.current;
    liftRef.current = null;
    if (!lift) return;
    itemsRef.current = lift.base;
    redraw();
    bump();
  };

  const deletePendingMarker = () => {
    const lift = liftRef.current;
    pendingMarkerRef.current = null;
    setPendingMarker(null);
    liftRef.current = null;
    if (lift) applyItems(itemsRef.current, lift.base);
    setTool("pen");
  };

  const findObjectAt = (point: { x: number; y: number }, kinds: DrawItem["kind"][]): number => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return -1;
    const tolerance = hitTolerance();
    const items = itemsRef.current;
    for (let i = items.length - 1; i >= 0; i--) {
      const item = items[i];
      if (kinds.includes(item.kind) && hitsItem(ctx, item, point, tolerance, fontFamilyRef.current)) return i;
    }
    return -1;
  };

  const liftObject = (index: number) => {
    const items = itemsRef.current;
    const item = items[index];
    if (!item || item.kind === "stroke") return;
    liftRef.current = { base: items, index };
    itemsRef.current = items.filter((_, i) => i !== index);
    redraw();
    bump();
    if (isMarkerItem(item)) {
      pendingMarkerRef.current = item;
      setMarkerSizePx(item.size);
      setPendingMarker(item);
      setTool("marker");
      return;
    }
    setTextSize(item.size);
    setTextBold(item.bold);
    setTool("text");
    const pending = { x: item.x, y: item.y, text: item.text };
    pendingTextRef.current = pending;
    setPendingText(pending);
  };

  const liftMarkerAt = (point: { x: number; y: number }): boolean => {
    const index = findObjectAt(point, ["marker"]);
    if (index < 0) return false;
    liftObject(index);
    return true;
  };

  const chooseMarker = (definition: DrawingMarker) => {
    setSelectedMarkerId(definition.id);
    setMarkerPickerOpen(false);
    setTool("marker");
    const pending = pendingMarkerRef.current;
    if (pending) {
      setPendingMarker({
        ...pending,
        markerId: definition.id,
        shape: definition.shape,
        color: definition.color,
        filled: definition.filled,
        name: definition.name,
      });
      return;
    }
    createPendingMarker(definition, visibleCenter());
  };

  const applyMarkerSize = (size: number) => {
    setMarkerSizePx(size);
    setPendingMarker((prev) => (prev ? { ...prev, size } : prev));
  };

  const markerSizeMin = Math.round(markerSize(0) * SIZE_MIN_FACTOR);
  const markerSizeMax = Math.round(markerSize(MARKER_SIZE_RATIOS.length - 1) * SIZE_MAX_FACTOR);

  const resizePinchedMarker = () => {
    const drag = markerDragRef.current;
    const pinch = markerPinchRef.current;
    if (!drag || !pinch || pinch.startDistance <= 0) return;
    const distance = Math.hypot(drag.last.x - pinch.last.x, drag.last.y - pinch.last.y);
    const size = Math.round((pinch.startSize * distance) / pinch.startDistance);
    applyMarkerSize(Math.max(markerSizeMin, Math.min(markerSizeMax, size)));
  };
  const resizePinchedMarkerRef = useRef(resizePinchedMarker);
  resizePinchedMarkerRef.current = resizePinchedMarker;

  const hasPendingMarker = pendingMarker !== null;
  useEffect(() => {
    if (!hasPendingMarker) return;
    const handleDown = (event: PointerEvent) => {
      const drag = markerDragRef.current;
      const marker = pendingMarkerRef.current;
      if (event.pointerType !== "touch" || !drag || !marker || drag.pointerId === event.pointerId) return;
      if (markerPinchRef.current) return;
      event.preventDefault();
      event.stopPropagation();
      drag.pinched = true;
      markerPinchRef.current = {
        pointerId: event.pointerId,
        last: { x: event.clientX, y: event.clientY },
        startDistance: Math.hypot(drag.last.x - event.clientX, drag.last.y - event.clientY),
        startSize: marker.size,
      };
    };
    const handleMove = (event: PointerEvent) => {
      const pinch = markerPinchRef.current;
      if (!pinch || pinch.pointerId !== event.pointerId) return;
      event.preventDefault();
      event.stopPropagation();
      pinch.last = { x: event.clientX, y: event.clientY };
      resizePinchedMarkerRef.current();
    };
    const handleUp = (event: PointerEvent) => {
      const pinch = markerPinchRef.current;
      if (!pinch || pinch.pointerId !== event.pointerId) return;
      event.stopPropagation();
      markerPinchRef.current = null;
      const drag = markerDragRef.current;
      const marker = pendingMarkerRef.current;
      if (drag && marker) {
        drag.start = drag.last;
        drag.origin = { x: marker.x, y: marker.y };
      }
    };
    window.addEventListener("pointerdown", handleDown, true);
    window.addEventListener("pointermove", handleMove, true);
    window.addEventListener("pointerup", handleUp, true);
    window.addEventListener("pointercancel", handleUp, true);
    return () => {
      window.removeEventListener("pointerdown", handleDown, true);
      window.removeEventListener("pointermove", handleMove, true);
      window.removeEventListener("pointerup", handleUp, true);
      window.removeEventListener("pointercancel", handleUp, true);
    };
  }, [hasPendingMarker]);
  const textSizeMin = Math.round(width * TEXT_SIZE_RATIOS[0] * SIZE_MIN_FACTOR);
  const textSizeMax = Math.round(width * TEXT_SIZE_RATIOS[TEXT_SIZE_RATIOS.length - 1] * SIZE_MAX_FACTOR);

  const stepSize = (size: number, direction: 1 | -1, min: number, max: number): number =>
    Math.max(min, Math.min(max, Math.round(direction > 0 ? size * SIZE_STEP : size / SIZE_STEP)));

  const openMarkerPicker = (anchor?: HTMLElement | null) => {
    pickerAnchorRef.current = anchor ?? shapesButtonRef.current;
    setMarkerPickerOpen(true);
  };

  useEffect(() => {
    if (!markerPickerOpen) return;
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (pickerRef.current?.contains(target) || pickerAnchorRef.current?.contains(target)) return;
      setMarkerPickerOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMarkerPickerOpen(false);
    };
    document.addEventListener("pointerdown", handlePointerDown, true);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown, true);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [markerPickerOpen]);

  const toggleSoloLayer = (layerId: number) => {
    commitPendingTextRef.current();
    commitPendingMarkerRef.current();
    setSoloLayerId((prev) => (prev === layerId ? null : layerId));
  };

  const toggleMarkerNames = () => {
    setMarkerNamesVisible((prev) => {
      writeMarkerNamesVisible(!prev);
      return !prev;
    });
  };

  const selectTool = (next: Tool) => {
    const wasActive = tool === next;
    commitPendingTextRef.current();
    commitPendingMarkerRef.current();
    setTool(wasActive ? "pen" : next);
    if (next === "marker" && !wasActive) {
      openMarkerPicker();
    } else {
      setMarkerPickerOpen(false);
    }
  };

  useEffect(() => {
    if (pendingText) textAreaRef.current?.focus();
  }, [pendingText === null]);

  useEffect(() => {
    const viewport = window.visualViewport;
    if (!isMobile || !viewport || pendingText === null) return;
    let lastHeight = viewport.height;
    const handleResize = () => {
      if (viewport.height - lastHeight > KEYBOARD_CLOSE_THRESHOLD) {
        setView(clampToFrame(IDENTITY_VIEW, displaySizeRef.current, frameSizeRef.current));
      }
      lastHeight = viewport.height;
    };
    viewport.addEventListener("resize", handleResize);
    return () => viewport.removeEventListener("resize", handleResize);
  }, [isMobile, pendingText === null]);

  const eraseAt = (points: StrokePoint[]) => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    const tolerance = hitTolerance();
    const current = itemsRef.current;
    const next = current.filter(
      (item) => !points.some((point) => hitsItem(ctx, item, point, tolerance, fontFamilyRef.current))
    );
    if (next.length === current.length) return;
    itemsRef.current = next;
    redraw();
    bump();
  };

  const cancelStroke = () => {
    const eraseBase = eraseBaseRef.current;
    if (eraseBase) {
      eraseBaseRef.current = null;
      activePointerRef.current = null;
      itemsRef.current = eraseBase;
      redraw();
      bump();
      return;
    }
    if (!currentStrokeRef.current) return;
    currentStrokeRef.current = null;
    activePointerRef.current = null;
    redraw();
  };

  const startGesture = () => {
    const [a, b] = touchPointers();
    const mid = viewportPoint((a.x + b.x) / 2, (a.y + b.y) / 2);
    gestureRef.current = { distance: Math.hypot(a.x - b.x, a.y - b.y) || 1, mid, view: viewRef.current };
    panRef.current = null;
  };

  const updateGesture = () => {
    const gesture = gestureRef.current;
    const touches = touchPointers();
    if (!gesture || touches.length < 2) return;
    const [a, b] = touches;
    const distance = Math.hypot(a.x - b.x, a.y - b.y) || 1;
    const mid = viewportPoint((a.x + b.x) / 2, (a.y + b.y) / 2);
    const zoom = Math.min(Math.max((gesture.view.zoom * distance) / gesture.distance, minZoom), MAX_ZOOM);
    setView(
      clampView({
        zoom,
        x: mid.x - ((gesture.mid.x - gesture.view.x) * zoom) / gesture.view.zoom,
        y: mid.y - ((gesture.mid.y - gesture.view.y) * zoom) / gesture.view.zoom,
      })
    );
  };

  const zoomByStep = (direction: 1 | -1) => {
    const center = { x: frameSize.width / 2, y: frameSize.height / 2 };
    setView((prev) => zoomAround(center, prev, direction > 0 ? prev.zoom * ZOOM_STEP : prev.zoom / ZOOM_STEP));
  };

  useEffect(() => {
    if (isMobile) return;
    const isTyping = (target: EventTarget | null) =>
      target instanceof HTMLElement && (target.isContentEditable || ["INPUT", "TEXTAREA"].includes(target.tagName));
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.code !== "Space" || isTyping(event.target)) return;
      event.preventDefault();
      if (!spaceHeldRef.current) {
        spaceHeldRef.current = true;
        setSpaceHeld(true);
      }
    };
    const handleKeyUp = (event: KeyboardEvent) => {
      if (event.code !== "Space") return;
      spaceHeldRef.current = false;
      setSpaceHeld(false);
    };
    const handleBlur = () => {
      spaceHeldRef.current = false;
      setSpaceHeld(false);
    };
    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("keyup", handleKeyUp);
    window.addEventListener("blur", handleBlur);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
      window.removeEventListener("blur", handleBlur);
    };
  }, [isMobile]);

  useEffect(() => {
    if (isMobile) return;
    const isTyping = (target: EventTarget | null) =>
      target instanceof HTMLElement && (target.isContentEditable || ["INPUT", "TEXTAREA"].includes(target.tagName));
    const handleArrows = (event: KeyboardEvent) => {
      const direction = ARROW_DIRECTIONS[event.key];
      if (!direction || isTyping(event.target) || event.ctrlKey || event.metaKey || event.altKey) return;
      const frame = frameSizeRef.current;
      const step = (event.shiftKey ? ARROW_PAN_FAST : ARROW_PAN_STEP) * Math.min(frame.width, frame.height);
      event.preventDefault();
      setView((prev) =>
        clampToFrame(
          { zoom: prev.zoom, x: prev.x - direction.x * step, y: prev.y - direction.y * step },
          displaySizeRef.current,
          frame
        )
      );
    };
    window.addEventListener("keydown", handleArrows);
    return () => window.removeEventListener("keydown", handleArrows);
  }, [isMobile]);

  const sheetPannable = canPan(view, displaySize, frameSize);

  useEffect(() => {
    if (tool === "hand" && !sheetPannable) setTool("pen");
  }, [tool, sheetPannable]);

  const handleWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    if (!viewportRef.current) return;
    const point = viewportPoint(event.clientX, event.clientY);
    const factor = event.deltaY < 0 ? 1.15 : 1 / 1.15;
    setView((prev) => zoomAround(point, prev, prev.zoom * factor));
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLCanvasElement>) => {
    pointersRef.current.set(event.pointerId, {
      x: event.clientX,
      y: event.clientY,
      type: event.pointerType,
    });
    if (event.pointerType === "touch" && touchPointers().length === 2) {
      event.preventDefault();
      event.currentTarget.setPointerCapture(event.pointerId);
      cancelStroke();
      startGesture();
      return;
    }
    if (gestureRef.current) return;
    if (tool === "hand") {
      if (viewCanPan(viewRef.current)) {
        event.preventDefault();
        event.currentTarget.setPointerCapture(event.pointerId);
        panRef.current = {
          pointerId: event.pointerId,
          start: { x: event.clientX, y: event.clientY },
          view: viewRef.current,
        };
      }
      return;
    }
    if (spaceHeldRef.current && event.pointerType === "mouse") {
      if (viewCanPan(viewRef.current)) {
        event.preventDefault();
        event.currentTarget.setPointerCapture(event.pointerId);
        panRef.current = {
          pointerId: event.pointerId,
          start: { x: event.clientX, y: event.clientY },
          view: viewRef.current,
        };
      }
      return;
    }
    if (!canDraw) {
      if (viewCanPan(viewRef.current) && event.pointerType !== "pen") {
        event.preventDefault();
        event.currentTarget.setPointerCapture(event.pointerId);
        panRef.current = {
          pointerId: event.pointerId,
          start: { x: event.clientX, y: event.clientY },
          view: viewRef.current,
        };
      }
      return;
    }
    if (activePointerRef.current !== null) return;
    if (event.pointerType === "mouse" && event.button !== 0) return;
    if (tool === "text") {
      event.preventDefault();
      if (pendingTextRef.current) {
        if (viewRef.current.zoom > 1) {
          textAreaRef.current?.blur();
          setView(homeView());
          return;
        }
        commitPendingText();
        return;
      }
      const point = toPoint(event.nativeEvent);
      const textIndex = findObjectAt(point, ["text"]);
      if (textIndex >= 0) {
        liftObject(textIndex);
        return;
      }
      setPendingText(fitTextInSheet({ x: point.x, y: point.y, text: "" }));
      return;
    }
    if (tool === "marker") {
      event.preventDefault();
      const point = toPoint(event.nativeEvent);
      const pending = pendingMarkerRef.current;
      if (pending) {
        setPendingMarker(clampMarker({ ...pending, x: point.x, y: point.y }));
        return;
      }
      if (liftMarkerAt(point)) return;
      if (selectedMarker) {
        createPendingMarker(selectedMarker, point);
        return;
      }
      openMarkerPicker();
      return;
    }
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    activePointerRef.current = event.pointerId;
    if (eraser) {
      eraseBaseRef.current = itemsRef.current;
      eraseAt([toPoint(event.nativeEvent)]);
      return;
    }
    const startPoint = toPoint(event.nativeEvent);
    tapRef.current = findObjectAt(startPoint, ["marker", "text"]) >= 0
      ? { pointerId: event.pointerId, x: event.clientX, y: event.clientY, point: startPoint }
      : null;
    const stroke: Stroke = {
      kind: "stroke",
      width: STROKE_WIDTHS[widthIndex],
      pressureSensitive: event.pointerType === "pen",
      points: [toPoint(event.nativeEvent)],
    };
    currentStrokeRef.current = stroke;
    const ctx = canvasRef.current?.getContext("2d");
    if (ctx) drawStroke(ctx, stroke, OWN_STROKE_COLOR);
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const tracked = pointersRef.current.get(event.pointerId);
    if (tracked) {
      tracked.x = event.clientX;
      tracked.y = event.clientY;
    }
    if (gestureRef.current) {
      event.preventDefault();
      updateGesture();
      return;
    }
    const pan = panRef.current;
    if (pan && pan.pointerId === event.pointerId) {
      event.preventDefault();
      setView(
        clampView({
          zoom: pan.view.zoom,
          x: pan.view.x + event.clientX - pan.start.x,
          y: pan.view.y + event.clientY - pan.start.y,
        })
      );
      return;
    }
    if (event.pointerId !== activePointerRef.current) return;
    const native = event.nativeEvent;
    const events = typeof native.getCoalescedEvents === "function" ? native.getCoalescedEvents() : [native];
    const points = (events.length > 0 ? events : [native]).map((e) => toPoint(e));
    if (eraseBaseRef.current) {
      event.preventDefault();
      eraseAt(points);
      return;
    }
    const stroke = currentStrokeRef.current;
    if (!stroke) return;
    event.preventDefault();
    const from = stroke.points.length;
    stroke.points.push(...points);
    const ctx = canvasRef.current?.getContext("2d");
    if (ctx) drawStroke(ctx, stroke, OWN_STROKE_COLOR, from);
  };

  const finishStroke = (event: React.PointerEvent<HTMLCanvasElement>) => {
    pointersRef.current.delete(event.pointerId);
    if (gestureRef.current && touchPointers().length < 2) gestureRef.current = null;
    if (panRef.current?.pointerId === event.pointerId) panRef.current = null;
    if (event.pointerId !== activePointerRef.current) return;
    activePointerRef.current = null;
    const eraseBase = eraseBaseRef.current;
    if (eraseBase) {
      eraseBaseRef.current = null;
      if (itemsRef.current !== eraseBase) {
        undoRef.current = [...undoRef.current, eraseBase];
        redoRef.current = [];
        bump();
      }
      return;
    }
    const stroke = currentStrokeRef.current;
    currentStrokeRef.current = null;
    const tap = tapRef.current;
    tapRef.current = null;
    if (!stroke) return;
    if (
      tap &&
      tap.pointerId === event.pointerId &&
      Math.hypot(event.clientX - tap.x, event.clientY - tap.y) <= TAP_SLOP_PX
    ) {
      redraw();
      const index = findObjectAt(tap.point, ["marker", "text"]);
      if (index >= 0) {
        liftObject(index);
        return;
      }
    }
    applyItems([...itemsRef.current, stroke]);
  };

  const undo = () => {
    cancelPendingMarker();
    const previous = undoRef.current[undoRef.current.length - 1];
    if (!previous) return;
    undoRef.current = undoRef.current.slice(0, -1);
    redoRef.current = [...redoRef.current, itemsRef.current];
    itemsRef.current = previous;
    redraw();
    bump();
  };

  const redo = () => {
    cancelPendingMarker();
    const next = redoRef.current[redoRef.current.length - 1];
    if (!next) return;
    redoRef.current = redoRef.current.slice(0, -1);
    undoRef.current = [...undoRef.current, itemsRef.current];
    itemsRef.current = next;
    redraw();
    bump();
  };

  const save = useCallback(async (): Promise<boolean> => {
    if (!onSave) return true;
    commitPendingTextRef.current();
    commitPendingMarkerRef.current();
    if (sameStrokes(itemsRef.current, savedItemsRef.current)) return true;
    const snapshot = itemsRef.current;
    setSaving(true);
    try {
      const ok = await onSave(toStoredItems(snapshot));
      if (ok) {
        savedItemsRef.current = snapshot;
        bump();
      }
      return ok;
    } finally {
      setSaving(false);
    }
  }, [onSave]);

  const saveRef = useRef(save);
  saveRef.current = save;

  useEffect(() => {
    onDirtyChange?.(dirty, dirty ? () => saveRef.current() : null);
  }, [dirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange?.(false, null), [onDirtyChange]);

  useEffect(() => {
    if (!dirty) return;
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [dirty]);

  const requestClose = () => {
    if (dirty) {
      commitPendingTextRef.current();
      commitPendingMarkerRef.current();
      setConfirmLeave(true);
    } else {
      onClose();
    }
  };

  const topRow = (showLegend || footerText) && (
    <div className="dw-top-row">
      {showLegend && (
        <DrawingLegend entries={legendEntries} activeKey={soloLayerId} onSelect={toggleSoloLayer} />
      )}
      {footerText && <span className="dw-sheet-dates">{footerText}</span>}
    </div>
  );

  const tools = !readOnly && (
    <div className={`dw-tools ${isMobile ? "dw-tools-row" : ""} flex-column align-items-center g-075`}>
      {tool !== "text" && tool !== "marker" && (
        <div className="flex-column align-items-center g-05">
          {STROKE_WIDTHS.map((value, index) => (
            <button
              key={value}
              type="button"
              className={`dw-width pointer ${widthIndex === index ? "active" : ""}`}
              title={`Grubość ${index + 1}`}
              onClick={() => setWidthIndex(index)}
            >
              <span style={{ width: `${4 + index * 4}px`, height: `${4 + index * 4}px` }} />
            </button>
          ))}
        </div>
      )}
      {tool === "text" && (
        <div className="flex-column align-items-center g-05">
          {TEXT_SIZE_RATIOS.map((ratio, index) => (
            <button
              key={ratio}
              type="button"
              className={`dw-width dw-text-size pointer ${
                textSize === Math.round(width * ratio) ? "active" : ""
              }`}
              title={["Mały tekst", "Średni tekst", "Duży tekst"][index]}
              onClick={() => setTextSize(Math.round(width * ratio))}
            >
              <span className="dw-text-size-label" style={{
                  fontSize: `${[0.55, 0.85, 1.2][index]}rem`,
                  width: `${[1, 1.3, 1.65][index]}rem`,
                  height: `${[1, 1.3, 1.65][index]}rem`,
                }}>
                A
              </span>
            </button>
          ))}
          <button
            type="button"
            className="dw-tool dw-size-step pointer flex align-items-center justify-center"
            title="Pomniejsz tekst"
            aria-label="Pomniejsz tekst"
            disabled={textSize <= textSizeMin}
            onClick={() => setTextSize((prev) => stepSize(prev, -1, textSizeMin, textSizeMax))}
          >
            <Minus className="dl-icon-1" />
          </button>
          <button
            type="button"
            className="dw-tool dw-size-step pointer flex align-items-center justify-center"
            title="Powiększ tekst"
            aria-label="Powiększ tekst"
            disabled={textSize >= textSizeMax}
            onClick={() => setTextSize((prev) => stepSize(prev, 1, textSizeMin, textSizeMax))}
          >
            <Plus className="dl-icon-1" />
          </button>
          <button
            type="button"
            className={`dw-width pointer ${textBold ? "active" : ""}`}
            title="Pogrubienie"
            onClick={() => setTextBold((prev) => !prev)}
          >
            <Bold className="dl-icon-1" />
          </button>
        </div>
      )}
      {tool === "marker" && (
        <div className="flex-column align-items-center g-05">
          {MARKER_SIZE_RATIOS.map((ratio, index) => (
            <button
              key={ratio}
              type="button"
              className={`dw-width dw-marker-size pointer ${markerSizePx === markerSize(index) ? "active" : ""}`}
              title={["Małe oznaczenie", "Średnie oznaczenie", "Duże oznaczenie"][index]}
              onClick={() => applyMarkerSize(markerSize(index))}
            >
              <span style={{ width: `${6 + index * 4}px`, height: `${6 + index * 4}px` }} />
            </button>
          ))}
          <button
            type="button"
            className="dw-tool dw-size-step pointer flex align-items-center justify-center"
            title="Pomniejsz oznaczenie"
            aria-label="Pomniejsz oznaczenie"
            disabled={markerSizePx <= markerSizeMin}
            onClick={() => applyMarkerSize(stepSize(markerSizePx, -1, markerSizeMin, markerSizeMax))}
          >
            <Minus className="dl-icon-1" />
          </button>
          <button
            type="button"
            className="dw-tool dw-size-step pointer flex align-items-center justify-center"
            title="Powiększ oznaczenie"
            aria-label="Powiększ oznaczenie"
            disabled={markerSizePx >= markerSizeMax}
            onClick={() => applyMarkerSize(stepSize(markerSizePx, 1, markerSizeMin, markerSizeMax))}
          >
            <Plus className="dl-icon-1" />
          </button>
        </div>
      )}
      <span className="dw-divider" />
      <button
        type="button"
        className={`dw-tool pointer flex align-items-center justify-center ${eraser ? "active" : ""}`}
        title="Gumka"
        aria-label="Gumka"
        onClick={() => selectTool("eraser")}
      >
        <Eraser className="dl-icon-1" />
      </button>
      <button
        type="button"
        className={`dw-tool pointer flex align-items-center justify-center ${tool === "text" ? "active" : ""}`}
        title="Tekst - dotknij kartki, aby dodać napis"
        aria-label="Tekst"
        onClick={() => selectTool("text")}
      >
        <Type className="dl-icon-1" />
      </button>
      <button
        type="button"
        className={`dw-tool pointer flex align-items-center justify-center ${tool === "marker" ? "active" : ""}`}
        title="Oznaczenia - wybierz z listy i dotknij kartki, aby postawić"
        aria-label="Oznaczenia"
        ref={shapesButtonRef}
        onClick={() => selectTool("marker")}
      >
        <Shapes className="dl-icon-1" />
      </button>
      {!isMobile && sheetPannable && (
        <button
          type="button"
          className={`dw-tool pointer flex align-items-center justify-center ${tool === "hand" ? "active" : ""}`}
          title="Przesuwanie kartki - przeciągnij, aby przesunąć przybliżoną kartkę (skróty: przytrzymaj spację albo strzałki)"
          aria-label="Przesuwanie kartki"
          onClick={() => selectTool("hand")}
        >
          <Hand className="dl-icon-1" />
        </button>
      )}
      {pendingMarker && (
        <div className="flex-column align-items-center g-05">
          <ActionButton
            iconTitle="Wstaw oznaczenie"
            disableText={true}
            onClick={commitPendingMarker}
            className="cl-filter cc-save-btn active"
          >
            <Check className="dl-icon-1 c-green" />
          </ActionButton>
          {liftRef.current && (
            <ActionButton iconTitle="Usuń oznaczenie" disableText={true} onClick={deletePendingMarker}>
              <Trash2 className="dl-icon-1 c-red" />
            </ActionButton>
          )}
          <ActionButton iconTitle="Anuluj" disableText={true} onClick={cancelPendingMarker}>
            <X className="dl-icon-1" />
          </ActionButton>
        </div>
      )}
      {pendingText && (
        <div className="flex-column align-items-center g-05">
          <ActionButton
            iconTitle="Wstaw napis"
            disableText={true}
            onClick={commitPendingText}
            disabled={pendingText.text.trim() === ""}
            className="cl-filter cc-save-btn active"
          >
            <Check className="dl-icon-1 c-green" />
          </ActionButton>
          {liftRef.current && (
            <ActionButton iconTitle="Usuń napis" disableText={true} onClick={deletePendingText}>
              <Trash2 className="dl-icon-1 c-red" />
            </ActionButton>
          )}
          <ActionButton iconTitle="Anuluj napis" disableText={true} onClick={cancelPendingText}>
            <X className="dl-icon-1" />
          </ActionButton>
        </div>
      )}
    </div>
  );

  const portalRoot = document.getElementById("portal-root");
  const editor = (
    <div className="dw-editor cc-light flex" role="dialog" aria-label={title}>
      {!isMobile && tools}

      <div className="dw-main relative flex-column f-1 mw-0 g-05">
        <div className="dw-main-top flex align-items-center space-between g-05">
          <div className="dw-nav flex align-items-center g-05">
            <ActionButton iconTitle="Wróć" disableText={true} onClick={requestClose} className="cc-back-btn">
              <ChevronLeft className="dl-icon-1" />
            </ActionButton>
            {hasMarkers && (
              <ActionButton
                iconTitle={markerNamesVisible ? "Ukryj nazwy oznaczeń" : "Pokaż nazwy oznaczeń"}
                disableText={true}
                onClick={toggleMarkerNames}
              >
                {markerNamesVisible ? <EyeOff className="dl-icon-1" /> : <Eye className="dl-icon-1" />}
              </ActionButton>
            )}
          </div>
          {!readOnly && (
            <div className="dw-actions flex align-items-center g-05">
              <ActionButton iconTitle="Cofnij" disableText={true} disabled={!canUndo} onClick={undo}>
                <Undo2 className="dl-icon-1" />
              </ActionButton>
              <ActionButton iconTitle="Przywróć" disableText={true} disabled={!canRedo} onClick={redo}>
                <Redo2 className="dl-icon-1" />
              </ActionButton>
              <ActionButton
                iconTitle="Zapisz"
                disableText={true}
                onClick={() => save()}
                disabled={saving || !dirty}
                className={`cl-filter cc-save-btn ${dirty && !saving ? "active" : ""}`}
              >
                <Save className="dl-icon-1 c-green" />
              </ActionButton>
            </div>
          )}
        </div>

        {isMobile && tools}

        <div className="dw-stage flex align-items-center justify-center" ref={stageRef} onScroll={resetHiddenScroll}>
          <div className="dw-zoom absolute flex align-items-center g-05">
            {!isMobile && (
              <>
                <ActionButton
                  iconTitle="Oddal"
                  disableText={true}
                  disabled={view.zoom <= minZoom}
                  onClick={() => zoomByStep(-1)}
                >
                  <ZoomOut className="dl-icon-1" />
                </ActionButton>
                <ActionButton
                  iconTitle="Przybliż (przytrzymaj spację i przeciągnij, aby przesuwać)"
                  disableText={true}
                  disabled={view.zoom >= MAX_ZOOM}
                  onClick={() => zoomByStep(1)}
                >
                  <ZoomIn className="dl-icon-1" />
                </ActionButton>
              </>
            )}
            <ActionButton
              iconTitle="Dopasuj kartkę"
              disableText={true}
              onClick={() => setView(homeView())}
              disabled={view.zoom === 1}
              className={`dw-fit ${view.zoom !== 1 ? "" : "dw-fit-hidden"}`}
            >
              <Minimize2 className="dl-icon-1" />
            </ActionButton>
          </div>
          {topRow}
          <div className="dw-sheet-wrap relative">
            <div
              className={`dw-viewport relative ${(spaceHeld || tool === "hand") && sheetPannable ? "panning" : ""}`}
              ref={viewportRef}
              style={{ width: frameSize.width, height: frameSize.height }}
              onWheel={handleWheel}
              onScroll={resetHiddenScroll}
            >
              <div
                className="dw-sheet absolute"
                style={{
                  width: displaySize.width,
                  height: displaySize.height,
                  transform: `translate(${view.x}px, ${view.y}px) scale(${view.zoom})`,
                }}
              >
              {templateUrl && <img className="dw-template absolute" src={templateUrl} alt="" draggable={false} />}
              {otherLayers.map(({ layer, items }) => (
                <LayerCanvas
                  key={layer.id}
                  items={items}
                  color={layer.color}
                  width={width}
                  height={height}
                  showMarkerNames={markerNamesVisible}
                  className={soloLayerId !== null && soloLayerId !== layer.id ? "dw-layer-hidden" : ""}
                />
              ))}
              <canvas
                ref={canvasRef}
                width={width}
                height={height}
                className={`dw-canvas absolute ${canDraw ? "" : "read-only"} ${
                  soloLayerId !== null && soloLayerId !== OWN_LAYER_KEY ? "dw-layer-hidden" : ""
                }`}
                onPointerDown={handlePointerDown}
                onPointerMove={handlePointerMove}
                onPointerUp={finishStroke}
                onPointerCancel={finishStroke}
              />
              {pendingText && displaySize.width > 0 && (() => {
                const scale = displaySize.width / width;
                const ctx = canvasRef.current?.getContext("2d");
                const block = ctx
                  ? measureTextBlock(ctx, pendingText.text || " ", textSize, textBold, fontFamilyRef.current)
                  : { width: textSize, height: textSize * TEXT_LINE_HEIGHT };
                const fontPx = textSize * scale;
                const inputScale = Math.min(1, fontPx / MIN_INPUT_FONT_PX);
                const boxWidth = block.width * scale + fontPx * 0.6;
                const boxHeight = block.height * scale;
                return (
                  <div
                    className="dw-text-box absolute"
                    style={{ left: pendingText.x * scale, top: pendingText.y * scale, width: boxWidth, height: boxHeight }}
                  >
                    <span
                      className="dw-text-move flex align-items-center justify-center"
                      title="Przeciągnij, aby przesunąć napis, albo dotknij, aby zatwierdzić"
                      onPointerDown={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        e.currentTarget.setPointerCapture(e.pointerId);
                        textDragRef.current = {
                          pointerId: e.pointerId,
                          start: { x: e.clientX, y: e.clientY },
                          origin: { x: pendingText.x, y: pendingText.y },
                        };
                      }}
                      onPointerMove={(e) => {
                        const drag = textDragRef.current;
                        if (!drag || drag.pointerId !== e.pointerId) return;
                        const factor = scale * viewRef.current.zoom;
                        setPendingText((prev) =>
                          prev
                            ? fitTextInSheet({
                                ...prev,
                                x: drag.origin.x + (e.clientX - drag.start.x) / factor,
                                y: drag.origin.y + (e.clientY - drag.start.y) / factor,
                              })
                            : prev
                        );
                      }}
                      onPointerUp={(e) => {
                        const drag = textDragRef.current;
                        textDragRef.current = null;
                        if (drag && Math.hypot(e.clientX - drag.start.x, e.clientY - drag.start.y) <= TAP_SLOP_PX) {
                          commitPendingText();
                        }
                      }}
                      onPointerCancel={() => {
                        textDragRef.current = null;
                      }}
                    >
                      <Move className="dl-icon" />
                    </span>
                    <textarea
                      ref={textAreaRef}
                      className="dw-text-input"
                      value={pendingText.text}
                      rows={1}
                      spellCheck={false}
                      placeholder="Napis"
                      style={{
                        width: boxWidth / inputScale,
                        height: boxHeight / inputScale,
                        fontSize: fontPx / inputScale,
                        transform: inputScale < 1 ? `scale(${inputScale})` : undefined,
                        fontWeight: textBold ? 700 : 400,
                        lineHeight: TEXT_LINE_HEIGHT,
                        fontFamily: fontFamilyRef.current,
                      }}
                      onChange={(e) => {
                        const text = e.target.value;
                        setPendingText((prev) => (prev ? fitTextInSheet({ ...prev, text }) : prev));
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Escape") {
                          e.preventDefault();
                          cancelPendingText();
                        }
                      }}
                    />
                  </div>
                );
              })()}
              {pendingMarker && displaySize.width > 0 && (() => {
                const scale = displaySize.width / width;
                const boxSize = pendingMarker.size * scale;
                return (
                  <div
                    className="dw-marker-pending absolute flex align-items-center justify-center"
                    title="Przeciągnij, aby przesunąć oznaczenie, albo dotknij, aby zatwierdzić"
                    style={{
                      left: pendingMarker.x * scale - boxSize / 2,
                      top: pendingMarker.y * scale - boxSize / 2,
                      width: boxSize,
                      height: boxSize,
                    }}
                    onPointerDown={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      e.currentTarget.setPointerCapture(e.pointerId);
                      if (markerDragRef.current) return;
                      markerDragRef.current = {
                        pointerId: e.pointerId,
                        start: { x: e.clientX, y: e.clientY },
                        origin: { x: pendingMarker.x, y: pendingMarker.y },
                        last: { x: e.clientX, y: e.clientY },
                        pinched: false,
                      };
                    }}
                    onPointerMove={(e) => {
                      const drag = markerDragRef.current;
                      if (!drag || drag.pointerId !== e.pointerId) return;
                      drag.last = { x: e.clientX, y: e.clientY };
                      if (markerPinchRef.current) {
                        resizePinchedMarker();
                        return;
                      }
                      const factor = scale * viewRef.current.zoom;
                      setPendingMarker((prev) =>
                        prev
                          ? clampMarker({
                              ...prev,
                              x: drag.origin.x + (e.clientX - drag.start.x) / factor,
                              y: drag.origin.y + (e.clientY - drag.start.y) / factor,
                            })
                          : prev
                      );
                    }}
                    onPointerUp={(e) => {
                      const drag = markerDragRef.current;
                      if (!drag || drag.pointerId !== e.pointerId) return;
                      markerDragRef.current = null;
                      markerPinchRef.current = null;
                      if (!drag.pinched && Math.hypot(e.clientX - drag.start.x, e.clientY - drag.start.y) <= TAP_SLOP_PX) {
                        commitPendingMarker();
                      }
                    }}
                    onPointerCancel={() => {
                      markerDragRef.current = null;
                      markerPinchRef.current = null;
                    }}
                  >
                    <MarkerShapeIcon shape={pendingMarker.shape} color={pendingMarker.color} filled={pendingMarker.filled} />
                  </div>
                );
              })()}
              </div>
            </div>
          </div>
        </div>
      </div>
      {markerPickerOpen && (() => {
        const rect = pickerAnchorRef.current?.getBoundingClientRect();
        if (!rect) return null;
        const style = isMobile
          ? { top: rect.bottom + 8, left: Math.max(8, Math.min(rect.left, window.innerWidth - 248)) }
          : { top: rect.top, left: rect.right + 8 };
        return (
          <div className="dw-marker-popover" ref={pickerRef} style={style} role="listbox" aria-label="Oznaczenia">
            {markers.length === 0 ? (
              <span className="dw-marker-empty">Brak oznaczeń. Dodaj je w ustawieniach karty klienta.</span>
            ) : (
              <div className="dw-marker-list flex-column g-025">
                {markers.map((marker) => (
                  <button
                    key={marker.id}
                    type="button"
                    role="option"
                    aria-selected={marker.id === selectedMarkerId}
                    className={`dw-marker-option flex align-items-center g-075 pointer ${
                      marker.id === selectedMarkerId ? "active" : ""
                    }`}
                    onClick={() => chooseMarker(marker)}
                  >
                    <MarkerShapeIcon shape={marker.shape} color={marker.color} filled={marker.filled} />
                    <span className="dw-marker-option-name">{marker.name}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        );
      })()}
    </div>
  );

  return (
    <>
      {portalRoot ? ReactDOM.createPortal(editor, portalRoot) : editor}
      {confirmLeave && (
        <Modal
          title="Masz niezapisane zmiany"
          onClose={() => setConfirmLeave(false)}
          size="sm"
          footer={
            <>
              <ActionButton text="Wróć" onClick={() => setConfirmLeave(false)} className="cc-back-btn" />
              <ActionButton text="Odrzuć" onClick={onClose} className="cl-filter cc-discard-btn active" />
              <ActionButton
                text="Zapisz"
                className="cl-filter cc-save-btn active"
                onClick={async () => {
                  setConfirmLeave(false);
                  if (await save()) onClose();
                }}
              >
                <Save className="dl-icon-1 c-green" />
              </ActionButton>
            </>
          }
        >
          <span>Zapisać zmiany w rysunku przed wyjściem?</span>
        </Modal>
      )}

    </>
  );
}

export default DrawingEditor;
