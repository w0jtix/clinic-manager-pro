import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp } from "lucide-react";
import ActionButton from "./ActionButton";
import { WheelColumn, WheelItem } from "./WheelDatePicker";
import {
  MONTHS,
  MONTH_LABELS_SHORT,
  START_YEAR,
  getDaysInMonth,
  getWeekdayShort,
  getWeeksOfMonth,
  parseIsoDate,
  toIsoDate,
} from "../utils/dateUtils";

export type PeriodMode = "day" | "week" | "month";

export const PERIOD_MODES: { key: PeriodMode; label: string }[] = [
  { key: "day", label: "Dzień" },
  { key: "week", label: "Tydzień" },
  { key: "month", label: "Miesiąc" },
];

export interface PeriodRange {
  from: Date;
  to: Date;
}

const dayMonth = (date: Date): string =>
  `${date.getDate()}.${String(date.getMonth() + 1).padStart(2, "0")}`;

const weekLabel = (from: Date, to: Date): string =>
  `${getWeekdayShort(from)} ${dayMonth(from)} - ${getWeekdayShort(to)} ${dayMonth(to)}`;

export const getPeriodRange = (mode: PeriodMode, value: string): PeriodRange => {
  const date = parseIsoDate(value);
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  if (mode === "day") return { from: date, to: date };
  if (mode === "week") {
    const week =
      getWeeksOfMonth(year, month).find(
        (w) => date.getTime() >= w.start.getTime() && date.getTime() <= w.end.getTime(),
      ) ?? getWeeksOfMonth(year, month)[0];
    return { from: week.start, to: week.end };
  }
  return { from: new Date(year, month - 1, 1), to: new Date(year, month, 0) };
};

const periodLabel = (mode: PeriodMode, value: string): string => {
  const date = parseIsoDate(value);
  if (mode === "day") {
    const yearSuffix =
      date.getFullYear() !== new Date().getFullYear() ? ` ${date.getFullYear()}` : "";
    return `${getWeekdayShort(date)}, ${date.getDate()} ${MONTH_LABELS_SHORT[date.getMonth()]}${yearSuffix}`;
  }
  if (mode === "week") {
    const { from, to } = getPeriodRange(mode, value);
    return weekLabel(from, to);
  }
  return `${MONTHS[date.getMonth()].name} ${date.getFullYear()}`;
};

const shiftPeriod = (mode: PeriodMode, value: string, direction: 1 | -1): string => {
  const date = parseIsoDate(value);
  if (mode === "day") {
    return toIsoDate(new Date(date.getFullYear(), date.getMonth(), date.getDate() + direction));
  }
  if (mode === "week") {
    const { from, to } = getPeriodRange(mode, value);
    const edge = direction > 0 ? to : from;
    return toIsoDate(new Date(edge.getFullYear(), edge.getMonth(), edge.getDate() + direction));
  }
  return toIsoDate(new Date(date.getFullYear(), date.getMonth() + direction, 1));
};

interface WheelStepperProps {
  items: WheelItem[];
  value: number;
  onChange: (value: number) => void;
  ariaLabel: string;
}

function WheelStepper({ items, value, onChange, ariaLabel }: WheelStepperProps) {
  const index = items.findIndex((item) => item.value === value);
  const step = (direction: 1 | -1) => {
    const next = items[index + direction];
    if (next) onChange(next.value);
  };

  return (
    <div className="pn-step flex-column align-items-center">
      <ActionButton
        disableText
        disableBorder
        iconTitle="Wyżej"
        className="pn-step-btn"
        disabled={index <= 0}
        onClick={() => step(-1)}
      >
        <ChevronUp className="dl-icon" />
      </ActionButton>
      <WheelColumn items={items} value={value} onChange={onChange} ariaLabel={ariaLabel} />
      <ActionButton
        disableText
        disableBorder
        iconTitle="Niżej"
        className="pn-step-btn"
        disabled={index < 0 || index >= items.length - 1}
        onClick={() => step(1)}
      >
        <ChevronDown className="dl-icon" />
      </ActionButton>
    </div>
  );
}

export interface PeriodNavigatorProps {
  mode: PeriodMode;
  value: string;
  onChange: (value: string) => void;
  className?: string;
}

export function PeriodNavigator({ mode, value, onChange, className = "" }: PeriodNavigatorProps) {
  const [open, setOpen] = useState<boolean>(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const date = parseIsoDate(value);
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const today = new Date();

  const nextStart = getPeriodRange(mode, shiftPeriod(mode, value, 1)).from;
  const nextDisabled = nextStart.getTime() > today.getTime();

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (rootRef.current?.contains(event.target as Node)) return;
      setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const years = useMemo<WheelItem[]>(() => {
    const current = new Date().getFullYear();
    return Array.from({ length: current - START_YEAR + 1 }, (_, i) => ({
      value: START_YEAR + i,
      label: String(START_YEAR + i),
    }));
  }, []);

  const months = useMemo<WheelItem[]>(
    () => MONTHS.map((m) => ({ value: m.id, label: m.name })),
    [],
  );

  const days = useMemo<WheelItem[]>(
    () =>
      Array.from({ length: getDaysInMonth(year, month) }, (_, i) => ({
        value: i + 1,
        label: `${getWeekdayShort(new Date(year, month - 1, i + 1))} ${i + 1}`,
      })),
    [year, month],
  );

  const weeks = useMemo(() => getWeeksOfMonth(year, month), [year, month]);
  const weekItems = useMemo<WheelItem[]>(
    () => weeks.map((w) => ({ value: w.weekNumber, label: weekLabel(w.start, w.end) })),
    [weeks],
  );
  const currentWeek =
    weeks.find((w) => date.getTime() >= w.start.getTime() && date.getTime() <= w.end.getTime()) ??
    weeks[0];

  const changeMonthYear = (nextYear: number, nextMonth: number) => {
    const day = mode === "day" ? Math.min(date.getDate(), getDaysInMonth(nextYear, nextMonth)) : 1;
    onChange(toIsoDate(new Date(nextYear, nextMonth - 1, day)));
  };

  return (
    <div className={`pn-nav relative flex align-items-center ${className}`} ref={rootRef}>
      <ActionButton
        disableText
        iconTitle="Poprzedni okres"
        className="pn-arrow"
        onClick={() => onChange(shiftPeriod(mode, value, -1))}
      >
        <ChevronLeft className="dl-icon" />
      </ActionButton>
      <button
        type="button"
        className="cs-period-pill pn-pill flex align-items-center justify-center g-05 pointer"
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="cs-period-main pn-label">{periodLabel(mode, value)}</span>
        <ChevronDown className={`dl-icon-1 cs-period-caret ${open ? "open" : ""}`} />
      </button>
      <ActionButton
        disableText
        iconTitle="Następny okres"
        className="pn-arrow"
        disabled={nextDisabled}
        onClick={() => onChange(shiftPeriod(mode, value, 1))}
      >
        <ChevronRight className="dl-icon" />
      </ActionButton>
      {open && (
        <div className={`pn-panel pn-panel-${mode}`}>
          <div className="wheel-picker flex justify-center g-05">
            <div className="wheel-band" />
            {mode === "day" && (
              <WheelStepper
                items={days}
                value={date.getDate()}
                onChange={(day) => onChange(toIsoDate(new Date(year, month - 1, day)))}
                ariaLabel="Dzień"
              />
            )}
            {mode === "week" && (
              <WheelStepper
                items={weekItems}
                value={currentWeek.weekNumber}
                onChange={(weekNumber) => {
                  const week = weeks.find((w) => w.weekNumber === weekNumber);
                  if (week) onChange(toIsoDate(week.start));
                }}
                ariaLabel="Tydzień"
              />
            )}
            <WheelStepper
              items={months}
              value={month}
              onChange={(nextMonth) => changeMonthYear(year, nextMonth)}
              ariaLabel="Miesiąc"
            />
            <WheelStepper
              items={years}
              value={year}
              onChange={(nextYear) => changeMonthYear(nextYear, month)}
              ariaLabel="Rok"
            />
          </div>
        </div>
      )}
    </div>
  );
}

export default PeriodNavigator;
