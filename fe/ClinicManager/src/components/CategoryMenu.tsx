import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ExpenseCategoryCode } from "../models/expense";

export interface CategoryMenuItem {
  id: ExpenseCategoryCode;
  name: string;
  color: string;
}

export interface CategoryMenuProps {
  anchor: HTMLElement | null;
  items: CategoryMenuItem[];
  selected?: ExpenseCategoryCode | null;
  onSelect: (category: ExpenseCategoryCode) => void;
  onClose: () => void;
  align?: "center" | "start";
}

export function CategoryMenu({
  anchor,
  items,
  selected = null,
  onSelect,
  onClose,
  align = "center",
}: CategoryMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);

  const place = useCallback(() => {
    const menu = menuRef.current;
    if (!anchor || !menu) return;
    const rect = anchor.getBoundingClientRect();
    const width = menu.offsetWidth;
    const height = menu.offsetHeight;
    const preferredLeft =
      align === "start" ? rect.left : rect.left + rect.width / 2 - width / 2;
    const left = Math.min(Math.max(8, preferredLeft), window.innerWidth - width - 8);
    const openUpwards =
      rect.bottom + 6 + height > window.innerHeight && rect.top - 6 - height > 0;
    const top = openUpwards ? rect.top - 6 - height : rect.bottom + 6;
    setPosition({ top, left });
  }, [anchor, align]);

  useLayoutEffect(() => {
    place();
  }, [place, items.length]);

  useEffect(() => {
    const onDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (menuRef.current?.contains(target) || anchor?.contains(target)) return;
      onClose();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    const onReposition = () => place();

    document.addEventListener("pointerdown", onDown);
    document.addEventListener("keydown", onKey);
    window.addEventListener("scroll", onReposition, true);
    window.addEventListener("resize", onReposition);
    return () => {
      document.removeEventListener("pointerdown", onDown);
      document.removeEventListener("keydown", onKey);
      window.removeEventListener("scroll", onReposition, true);
      window.removeEventListener("resize", onReposition);
    };
  }, [anchor, onClose, place]);

  if (!anchor) return null;

  return createPortal(
    <div
      ref={menuRef}
      className="cm-menu flex-column"
      style={{
        top: position?.top ?? 0,
        left: position?.left ?? 0,
        visibility: position ? "visible" : "hidden",
      }}
      onClick={(e: React.MouseEvent) => e.stopPropagation()}
    >
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`cm-menu-item flex align-items-center g-05 pointer ${
            item.id === selected ? "active" : ""
          }`}
          onClick={() => {
            onClose();
            if (item.id !== selected) onSelect(item.id);
          }}
        >
          <span className="dl-dot" style={{ backgroundColor: `rgb(${item.color})` }} />
          <span>{item.name}</span>
        </button>
      ))}
    </div>,
    document.body
  );
}

export default CategoryMenu;
