import { useEffect, useRef, useState } from "react";
import ClientCardService from "../../services/ClientCardService";
import { DrawingLayer, DrawingMarkerShape } from "../../models/drawing";
import {
  DrawItem,
  fromStoredItems,
  getTemplateImageUrl,
  isLineMarker,
  markerPathData,
  markerStrokeWidth,
  renderItems,
  resolveDrawingFont,
} from "../../utils/drawingUtils";

const layerItemsCache = new Map<string, Promise<DrawItem[]>>();

const layerCacheKey = (layer: DrawingLayer): string => `${layer.id}:${layer.version}`;

export const loadLayerItems = (clientId: number, sheetId: number, layer: DrawingLayer): Promise<DrawItem[]> => {
  const key = layerCacheKey(layer);
  const cached = layerItemsCache.get(key);
  if (cached) return cached;
  const request = ClientCardService.getDrawingLayerItems(clientId, sheetId, layer.id)
    .then(fromStoredItems)
    .catch((error) => {
      layerItemsCache.delete(key);
      throw error;
    });
  layerItemsCache.set(key, request);
  return request;
};

export const loadSheetItems = async (
  clientId: number,
  sheetId: number,
  layers: DrawingLayer[]
): Promise<Record<number, DrawItem[]>> => {
  const entries = await Promise.all(
    layers.map(async (layer) => [layer.id, await loadLayerItems(clientId, sheetId, layer)] as const)
  );
  return Object.fromEntries(entries);
};

export function useLayerItems(
  clientId: number,
  sheetId: number | null,
  layers: DrawingLayer[]
): Record<number, DrawItem[]> {
  const [items, setItems] = useState<Record<number, DrawItem[]>>({});
  const layersKey = layers.map(layerCacheKey).join(",");

  useEffect(() => {
    setItems({});
    if (sheetId === null || layers.length === 0) return;
    let active = true;
    Promise.all(
      layers.map((layer) =>
        loadLayerItems(clientId, sheetId, layer)
          .then((value) => [layer.id, value] as const)
          .catch(() => null)
      )
    ).then((entries) => {
      if (!active) return;
      const next: Record<number, DrawItem[]> = {};
      entries.forEach((entry) => {
        if (entry) next[entry[0]] = entry[1];
      });
      setItems(next);
    });
    return () => {
      active = false;
    };
  }, [clientId, sheetId, layersKey]);

  return items;
}

export interface LayerCanvasProps {
  items: DrawItem[];
  color: string;
  width: number;
  height: number;
  showMarkerNames?: boolean;
  className?: string;
}

export function LayerCanvas({ items, color, width, height, showMarkerNames = true, className = "" }: LayerCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const family = resolveDrawingFont();
    const draw = () => {
      const ctx = canvasRef.current?.getContext("2d");
      if (!ctx) return;
      ctx.clearRect(0, 0, width, height);
      renderItems(ctx, items, color, family, showMarkerNames);
    };
    draw();
    let active = true;
    document.fonts?.ready.then(() => {
      if (active) draw();
    });
    return () => {
      active = false;
    };
  }, [items, color, width, height, showMarkerNames]);

  return <canvas ref={canvasRef} width={width} height={height} className={`dw-template absolute ${className}`} />;
}

export function useTemplateUrl(imageId: number | null): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    setUrl(null);
    if (imageId === null) return;
    let active = true;
    getTemplateImageUrl(imageId)
      .then((value) => {
        if (active) setUrl(value);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [imageId]);
  return url;
}

const ICON_SIZE = 20;

export interface MarkerShapeIconProps {
  shape: DrawingMarkerShape;
  color: string;
  filled: boolean;
  className?: string;
}

export function MarkerShapeIcon({ shape, color, filled, className = "" }: MarkerShapeIconProps) {
  const strokeWidth = markerStrokeWidth(shape, filled, ICON_SIZE);
  const extent = ICON_SIZE / 2 + strokeWidth / 2;
  return (
    <svg
      className={`dw-marker-icon ${className}`}
      viewBox={`${-extent} ${-extent} ${extent * 2} ${extent * 2}`}
      aria-hidden="true"
    >
      <path
        d={markerPathData(shape, ICON_SIZE)}
        fill={filled && !isLineMarker(shape) ? color : "none"}
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  );
}

export interface LegendEntry {
  key: number;
  color: string;
  name: string;
}

export interface DrawingLegendProps {
  entries: LegendEntry[];
  activeKey?: number | null;
  onSelect?: (key: number) => void;
}

export function DrawingLegend({
  entries,
  activeKey = null,
  onSelect,
}: DrawingLegendProps) {
  if (entries.length === 0) return null;
  return (
    <div className="dw-legends flex-column g-05">
      <div className="dw-legend flex align-items-center g-05">
        {entries.map((entry) =>
          onSelect ? (
            <button
              type="button"
              key={entry.key}
              className={`dw-legend-item clickable flex align-items-center g-025 pointer ${
                activeKey === entry.key ? "active" : activeKey !== null ? "dimmed" : ""
              }`}
              aria-pressed={activeKey === entry.key}
              title={activeKey === entry.key ? "Pokaż wszystkie zmiany" : `Pokaż tylko zmiany: ${entry.name}`}
              onClick={() => onSelect(entry.key)}
            >
              <span className="dw-legend-dot" style={{ backgroundColor: entry.color }} />
              <span className="dw-legend-name">{entry.name}</span>
            </button>
          ) : (
            <span className="dw-legend-item flex align-items-center g-025" key={entry.key}>
              <span className="dw-legend-dot" style={{ backgroundColor: entry.color }} />
              <span className="dw-legend-name">{entry.name}</span>
            </span>
          )
        )}
      </div>
    </div>
  );
}
