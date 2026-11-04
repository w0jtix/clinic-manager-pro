import React, { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";
import DropdownSelect from "./DropdownSelect";
import { MONTHS, getYears } from "../utils/dateUtils";

type PeriodOption = { id: number; name: string };

const pickId = (selected: PeriodOption | PeriodOption[] | null): number | null =>
  (Array.isArray(selected) ? selected[0]?.id : selected?.id) ?? null;

export interface PeriodPillProps {
  month: number | null | undefined;
  year: number | null | undefined;
  onMonthChange: (month: number | null) => void;
  onYearChange: (year: number | null) => void;
  label?: React.ReactNode;
  sub?: React.ReactNode;
  showYear?: boolean;
  monthPlaceholder?: string;
  yearPlaceholder?: string;
  children?: React.ReactNode;
  className?: string;
}

export function PeriodPill({
  month,
  year,
  onMonthChange,
  onYearChange,
  label,
  sub,
  showYear = true,
  monthPlaceholder = "Wybierz",
  yearPlaceholder = "Wybierz",
  children,
  className = "",
}: PeriodPillProps) {
  const [open, setOpen] = useState<boolean>(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const years = useMemo(() => getYears(), []);

  const disabledMonthIds = useMemo(() => {
    const now = new Date();
    if (year !== now.getFullYear()) return [];
    return MONTHS.filter((m) => m.id > now.getMonth() + 1).map((m) => m.id);
  }, [year]);

  useEffect(() => {
    if (!open) return;
    const handleMouseDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (rootRef.current?.contains(target)) return;
      if (document.getElementById("portal-root")?.contains(target)) return;
      setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", handleMouseDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handleMouseDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const defaultLabel =
    month && year ? `${MONTHS[month - 1].name} ${year}` : `${year ?? ""}`;

  return (
    <div className={`cs-period relative ${className}`} ref={rootRef}>
      <button
        type="button"
        className="cs-period-pill flex align-items-center g-05 pointer"
        onClick={() => setOpen((v) => !v)}
      >
        <span className="cs-period-dot" />
        <span className="cs-period-main">{label ?? defaultLabel}</span>
        {sub && <span className="cs-period-sub">{sub}</span>}
        <ChevronDown
          className={`dl-icon-1 cs-period-caret ${open ? "open" : ""}`}
        />
      </button>
      {open && (
        <div className="cs-period-panel flex-column g-05">
          {children}
          <DropdownSelect
            items={MONTHS}
            value={month ? (MONTHS.find((m) => m.id === month) ?? null) : null}
            onChange={(selected) => {
              onMonthChange(pickId(selected));
              setOpen(false);
            }}
            searchable={false}
            allowNew={false}
            placeholder={monthPlaceholder}
            className="expense-month"
            disabledItemIds={disabledMonthIds}
          />
          {showYear && (
            <DropdownSelect
              items={years}
              value={year ? (years.find((y) => y.id === year) ?? null) : null}
              onChange={(selected) => onYearChange(pickId(selected))}
              searchable={false}
              allowNew={false}
              placeholder={yearPlaceholder}
              className="expense-year"
            />
          )}
        </div>
      )}
    </div>
  );
}

export default PeriodPill;
