import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import ReactDOM from "react-dom";
import { EllipsisVertical } from "lucide-react";

export interface RowActionItem {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  danger?: boolean;
  title?: string;
}

export interface RowActionMenuProps {
  items: RowActionItem[];
  iconTitle?: string;
  className?: string;
  triggerIcon?: React.ReactNode;
  alwaysMenu?: boolean;
}

export function RowActionMenu({
  items,
  iconTitle = "Więcej opcji",
  className = "",
  triggerIcon,
  alwaysMenu = false,
}: RowActionMenuProps) {
  const [open, setOpen] = useState<boolean>(false);
  const [style, setStyle] = useState<React.CSSProperties | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const portalRoot = document.getElementById("portal-root");

  const updatePosition = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const gap = 6;
    const estimatedHeight = items.length * 38 + 12;
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < estimatedHeight && rect.top > spaceBelow;
    setStyle({
      position: "fixed",
      right: window.innerWidth - rect.right,
      top: openUp ? undefined : rect.bottom + gap,
      bottom: openUp ? window.innerHeight - rect.top + gap : undefined,
      zIndex: 1100,
    });
  }, [items.length]);

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    const onReposition = () => updatePosition();
    window.addEventListener("scroll", onReposition, true);
    window.addEventListener("resize", onReposition);
    return () => {
      window.removeEventListener("scroll", onReposition, true);
      window.removeEventListener("resize", onReposition);
    };
  }, [open, updatePosition]);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (
        triggerRef.current?.contains(target) ||
        menuRef.current?.contains(target)
      ) {
        return;
      }
      setOpen(false);
    };
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", handleClickOutside);
    document.addEventListener("keydown", handleEsc);
    return () => {
      document.removeEventListener("pointerdown", handleClickOutside);
      document.removeEventListener("keydown", handleEsc);
    };
  }, [open]);

  if (items.length === 0) return null;

  if (items.length === 1 && !alwaysMenu) {
    const only = items[0];
    return (
      <button
        type="button"
        className={`ram-trigger transparent border-none flex align-items-center justify-center pointer ${className}`}
        title={only.label}
        disabled={only.disabled}
        onClick={(e) => {
          e.stopPropagation();
          only.onClick();
        }}
      >
        {only.icon ?? <EllipsisVertical className="dl-icon" />}
      </button>
    );
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={`ram-trigger transparent border-none flex align-items-center justify-center pointer ${className}`}
        title={iconTitle}
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
      >
        {triggerIcon ?? <EllipsisVertical className="dl-icon" />}
      </button>
      {open &&
        portalRoot &&
        ReactDOM.createPortal(
          <div
            ref={menuRef}
            className="ram-menu flex-column"
            style={style ?? { position: "fixed", visibility: "hidden" }}
          >
            {items.map((item, i) => (
              <button
                key={i}
                type="button"
                className={`ram-item flex align-items-center g-05 pointer ${
                  item.danger ? "danger" : ""
                }`}
                disabled={item.disabled}
                title={item.title}
                onClick={(e) => {
                  e.stopPropagation();
                  setOpen(false);
                  item.onClick();
                }}
              >
                {item.icon}
                <span className="ram-item-label nowrap">{item.label}</span>
              </button>
            ))}
          </div>,
          portalRoot
        )}
    </>
  );
}

export default RowActionMenu;
