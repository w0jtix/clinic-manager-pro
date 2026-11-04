import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";

export interface FilterDropdownProps {
  label?: string;
  active?: boolean;
  count?: number;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
  className?: string;
}

// Collapses a page's filter row (search, category, brand, status...) into a
// single trigger, at every screen size - not just mobile. The panel sizes
// itself to its widest row via width:fit-content; every row keeps its own
// natural width rather than being stretched to 100%, since a percentage
// width on a flex child of a fit-content container is exactly the kind of
// circular sizing browsers resolve inconsistently (that's what made the
// category segments wrap one-per-line instead of sizing the panel).
export function FilterDropdown({
  label = "Filtry",
  active = false,
  count = 0,
  open: controlledOpen,
  onOpenChange,
  children,
  className = "",
}: FilterDropdownProps) {
  const [internalOpen, setInternalOpen] = useState<boolean>(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : internalOpen;

  const setOpen = useCallback(
    (next: boolean) => {
      if (!isControlled) setInternalOpen(next);
      onOpenChange?.(next);
    },
    [isControlled, onOpenChange]
  );

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (rootRef.current?.contains(target)) return;
      if (document.getElementById("portal-root")?.contains(target)) return;
      setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, setOpen]);

  return (
    <div className={`filter-dropdown relative ${className}`} ref={rootRef}>
      <button
        type="button"
        className={`dropdown-header fd-trigger flex space-between align-items-center transparent pointer ${
          active || count > 0 ? "active" : ""
        }`}
        onClick={() => setOpen(!open)}
      >
        <span className="dropdown-header-a flex f-1 align-items-center justify-center g-025">
          {label}
          {count > 0 && <span className="fd-count">{count}</span>}
        </span>
        <ChevronDown className={`arrow-down ${open ? "rotated" : ""}`} />
      </button>
      {open && (
        <div className="dropdown-menu fd-menu absolute mt-05 flex-column">
          {children}
        </div>
      )}
    </div>
  );
}

export default FilterDropdown;
