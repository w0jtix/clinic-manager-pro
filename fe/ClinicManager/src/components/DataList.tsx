import React, { useEffect, useLayoutEffect, useRef, useState } from "react";

export type ColumnAlign = "start" | "center" | "end";

const ROW_CLICK_IGNORE = "a, button, input, textarea, select, label, [data-row-click-ignore]";
const DRAG_HANDLE = ".dl-drag-handle";

export interface Column<T> {
  key: string;
  header?: string;
  width?: string;
  grow?: boolean;
  align?: ColumnAlign; 
  render?: (item: T, index: number) => React.ReactNode;
  cellClassName?: string;
  headerClassName?: string;
}

export interface DataListProps<T> {
  columns: Column<T>[];
  items: T[];
  getRowKey: (item: T, index: number) => React.Key;
  onRowClick?: (item: T) => void;
  isRowClickable?: (item: T) => boolean;
  isRowDisabled?: (item: T) => boolean;
  rowClassName?: (item: T, index: number) => string;
  groupBy?: (item: T, index: number) => React.Key;
  renderGroupHeader?: (item: T, groupKey: React.Key) => React.ReactNode;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  onReorder?: (fromIndex: number, toIndex: number) => void;
  isRowDraggable?: (item: T, index: number) => boolean;
  isLoading?: boolean;
  emptyText?: string;
  hideHeader?: boolean;
  className?: string;
}

const JUSTIFY: Record<ColumnAlign, React.CSSProperties["justifyContent"]> = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
};

export function DataList<T>({
  columns,
  items,
  getRowKey,
  onRowClick,
  isRowClickable,
  isRowDisabled,
  rowClassName,
  groupBy,
  renderGroupHeader,
  onScroll,
  onReorder,
  isRowDraggable,
  isLoading = false,
  emptyText = "Brak danych",
  hideHeader = false,
  className = "",
}: DataListProps<T>) {
  const cellStyle = (col: Column<T>): React.CSSProperties =>
    col.grow
      ? { flex: "1 1 0", justifyContent: JUSTIFY[col.align ?? "start"] }
      : { flex: `0 0 ${col.width ?? "auto"}`, justifyContent: JUSTIFY[col.align ?? "start"] };

  const rootRef = useRef<HTMLDivElement>(null);
  const scrollParentRef = useRef<HTMLElement | null>(null);
  const [scrollMode, setScrollMode] = useState<"inner" | "page">("inner");
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dropIndex, setDropIndex] = useState<number | null>(null);
  const [armedIndex, setArmedIndex] = useState<number | null>(null);

  const handleDragStart = (index: number) => {
    setDragIndex(index);
    setDropIndex(index);
  };

  const handleDragOver = (e: React.DragEvent, index: number) => {
    if (dragIndex === null) return;
    e.preventDefault();
    if (index !== dropIndex) setDropIndex(index);
  };

  const handleDrop = (e: React.DragEvent, index: number) => {
    e.preventDefault();
    if (dragIndex !== null && dragIndex !== index) {
      onReorder?.(dragIndex, index);
    }
    setDragIndex(null);
    setDropIndex(null);
    setArmedIndex(null);
  };

  const handleDragEnd = () => {
    setDragIndex(null);
    setDropIndex(null);
    setArmedIndex(null);
  };

  useLayoutEffect(() => {
    const el = rootRef.current;
    if (!el) return;
    const modal = el.closest(".modal");
    if (!modal) {
      scrollParentRef.current = el.closest(".dashboard") as HTMLElement | null;
      setScrollMode("page");
      return;
    }
    let node = el.parentElement;
    let scroller: HTMLElement | null = null;
    while (node && node !== modal) {
      const overflowY = getComputedStyle(node).overflowY;
      if (overflowY === "auto" || overflowY === "scroll") {
        scroller = node;
        break;
      }
      node = node.parentElement;
    }
    scrollParentRef.current = scroller;
    setScrollMode(scroller ? "page" : "inner");
  }, []);

  useEffect(() => {
    if (scrollMode !== "page" || !onScroll) return;
    const container = scrollParentRef.current;
    if (!container) return;
    const handler = () =>
      onScroll({ currentTarget: container } as unknown as React.UIEvent<HTMLDivElement>);
    container.addEventListener("scroll", handler, { passive: true });
    return () => container.removeEventListener("scroll", handler);
  }, [scrollMode, onScroll]);

  const isInner = scrollMode === "inner";

  return (
    <div
      ref={rootRef}
      className={`dl width-max flex-column ${isInner ? "dl-inner" : "dl-page"} ${className}`}
    >
      {!hideHeader && (
        <div className="dl-head flex align-items-center">
          {columns.map((col) => (
            <div
              key={col.key}
              className={`dl-head-cell flex align-items-center ${col.headerClassName ?? ""}`}
              style={cellStyle(col)}
            >
              {col.header ?? col.key}
            </div>
          ))}
        </div>
      )}

      <div
        className={`dl-scroll flex-column ${isInner ? "dl-scroll-inner" : "dl-scroll-page"}`}
        onScroll={isInner ? onScroll : undefined}
      >
        {!isLoading && items.length === 0 && <span className="dl-empty flex f-1 align-items-center justify-center">{emptyText}</span>}

        {items.map((item, index) => {
          const disabled = isRowDisabled?.(item) ?? false;
          const clickable =
            !disabled && onRowClick !== undefined && (isRowClickable?.(item) ?? true);
          const draggable = isRowDraggable?.(item, index) ?? false;
          const rowKey = getRowKey(item, index);
          const groupKey = groupBy?.(item, index);
          const showGroupHeader =
            groupBy !== undefined &&
            renderGroupHeader !== undefined &&
            (index === 0 || groupBy(items[index - 1], index - 1) !== groupKey);
          return (
            <React.Fragment key={rowKey}>
            {showGroupHeader && (
              <div className="dl-group-header flex align-items-center mt-2">
                {renderGroupHeader!(item, groupKey!)}
              </div>
            )}
            <div
              className={`dl-row flex align-items-center ${disabled ? "dl-row-disabled" : ""} ${
                clickable ? "pointer" : ""
              } ${draggable ? "dl-row-draggable" : ""} ${
                dragIndex === index ? "dl-row-dragging" : ""
              } ${
                dropIndex === index && dragIndex !== null && dragIndex !== index
                  ? dragIndex < index
                    ? "dl-row-drop-below"
                    : "dl-row-drop-above"
                  : ""
              } ${rowClassName?.(item, index) ?? ""}`}
              onClick={(e) => {
                if (!clickable) return;
                const target = e.target as HTMLElement;
                if (!e.currentTarget.contains(target)) return;
                const interactive = target.closest(ROW_CLICK_IGNORE);
                if (interactive && e.currentTarget.contains(interactive)) return;
                onRowClick?.(item);
              }}
              draggable={draggable && armedIndex === index}
              onPointerDown={
                draggable
                  ? (e) => setArmedIndex((e.target as HTMLElement).closest(DRAG_HANDLE) ? index : null)
                  : undefined
              }
              onPointerUp={draggable ? () => setArmedIndex(null) : undefined}
              onDragStart={draggable ? () => handleDragStart(index) : undefined}
              onDragOver={onReorder ? (e) => handleDragOver(e, index) : undefined}
              onDrop={onReorder ? (e) => handleDrop(e, index) : undefined}
              onDragEnd={draggable ? handleDragEnd : undefined}
            >
              {columns.map((col) => (
                <div
                  key={`${rowKey}-${col.key}`}
                  className={`dl-cell mw-0 flex align-items-center ${col.cellClassName ?? ""}`}
                  style={cellStyle(col)}
                >
                  {col.render
                    ? col.render(item, index)
                    : (item as unknown as Record<string, React.ReactNode>)[col.key]}
                </div>
              ))}
            </div>
            </React.Fragment>
          );
        })}

        {isLoading && <span className="dl-loading">Ładowanie...</span>}
      </div>
    </div>
  );
}

export default DataList;
