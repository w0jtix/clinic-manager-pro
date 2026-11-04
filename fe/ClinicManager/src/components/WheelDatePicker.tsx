import { useEffect, useMemo, useRef, useState } from "react";
import Modal from "./Modal";
import ActionButton from "./ActionButton";
import { MONTHS } from "../utils/dateUtils";

const ITEM_HEIGHT_PX = 40;
const SCROLL_SETTLE_MS = 180;
const MOMENTUM_FRICTION = 0.95;
const MOMENTUM_MIN_VELOCITY = 0.02;
const LOOP_COPIES = 9;

export interface WheelItem {
  value: number;
  label: string;
}

interface WheelColumnProps {
  items: WheelItem[];
  value: number;
  onChange: (value: number) => void;
  ariaLabel: string;
  loop?: boolean;
}

export function WheelColumn({ items, value, onChange, ariaLabel, loop = false }: WheelColumnProps) {
  const ref = useRef<HTMLDivElement>(null);
  const settleTimer = useRef<ReturnType<typeof setTimeout>>();
  const emittedByScrollRef = useRef<number | null>(null);
  const mountedRef = useRef<boolean>(false);
  const momentumFrame = useRef<number>(0);
  const suppressClickRef = useRef<boolean>(false);
  const dragRef = useRef<{
    startY: number;
    startScroll: number;
    lastY: number;
    lastTime: number;
    velocity: number;
    moved: boolean;
  } | null>(null);
  const lengthRef = useRef<number>(items.length);
  const index = Math.max(0, items.findIndex((item) => item.value === value));
  const count = items.length;
  const base = loop ? Math.floor(LOOP_COPIES / 2) * count : 0;
  const rendered = useMemo<WheelItem[]>(
    () => (loop ? Array.from({ length: LOOP_COPIES * count }, (_, i) => items[i % count]) : items),
    [loop, items, count]
  );

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const lengthChanged = lengthRef.current !== count;
    lengthRef.current = count;
    if (emittedByScrollRef.current === value && !lengthChanged) {
      emittedByScrollRef.current = null;
      return;
    }
    emittedByScrollRef.current = null;
    if (loop) {
      const current = Math.round(el.scrollTop / ITEM_HEIGHT_PX);
      if (!mountedRef.current || lengthChanged) {
        el.scrollTop = (base + index) * ITEM_HEIGHT_PX;
      } else if (current % count !== index) {
        let diff = (((index - (current % count)) % count) + count) % count;
        if (diff > count / 2) diff -= count;
        el.scrollTo({ top: (current + diff) * ITEM_HEIGHT_PX, behavior: "smooth" });
      }
      mountedRef.current = true;
      return;
    }
    const target = index * ITEM_HEIGHT_PX;
    if (Math.abs(el.scrollTop - target) > 1) {
      el.scrollTo({ top: target, behavior: mountedRef.current ? "smooth" : "auto" });
    }
    mountedRef.current = true;
  }, [index, count]);

  const recenter = (row: number) => {
    const el = ref.current;
    if (!el || !loop) return;
    const normalized = base + (row % count);
    if (row === normalized) return;
    el.style.scrollSnapType = "none";
    el.scrollTop = normalized * ITEM_HEIGHT_PX;
    requestAnimationFrame(() => {
      if (ref.current) ref.current.style.scrollSnapType = "";
    });
  };

  useEffect(
    () => () => {
      clearTimeout(settleTimer.current);
      cancelAnimationFrame(momentumFrame.current);
    },
    []
  );

  const snapToNearest = () => {
    const el = ref.current;
    if (!el) return;
    el.style.scrollSnapType = "";
    const target = Math.round(el.scrollTop / ITEM_HEIGHT_PX) * ITEM_HEIGHT_PX;
    el.scrollTo({ top: target, behavior: "smooth" });
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.pointerType !== "mouse" || e.button !== 0) return;
    const el = ref.current;
    if (!el) return;
    cancelAnimationFrame(momentumFrame.current);
    el.setPointerCapture(e.pointerId);
    el.style.scrollSnapType = "none";
    dragRef.current = {
      startY: e.clientY,
      startScroll: el.scrollTop,
      lastY: e.clientY,
      lastTime: performance.now(),
      velocity: 0,
      moved: false,
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    const el = ref.current;
    if (!drag || !el) return;
    const now = performance.now();
    const dt = Math.max(1, now - drag.lastTime);
    drag.velocity = (drag.lastY - e.clientY) / dt;
    drag.lastY = e.clientY;
    drag.lastTime = now;
    if (Math.abs(e.clientY - drag.startY) > 3) drag.moved = true;
    el.scrollTop = drag.startScroll - (e.clientY - drag.startY);
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    const el = ref.current;
    if (!drag || !el) return;
    el.releasePointerCapture(e.pointerId);
    dragRef.current = null;
    suppressClickRef.current = drag.moved;
    let velocity = performance.now() - drag.lastTime > 80 ? 0 : drag.velocity;
    let last = performance.now();
    const step = (now: number) => {
      const dt = now - last;
      last = now;
      el.scrollTop += velocity * dt;
      velocity *= Math.pow(MOMENTUM_FRICTION, dt / 16);
      if (Math.abs(velocity) > MOMENTUM_MIN_VELOCITY) {
        momentumFrame.current = requestAnimationFrame(step);
      } else {
        snapToNearest();
      }
    };
    momentumFrame.current = requestAnimationFrame(step);
  };

  const handleItemClick = (itemValue: number) => {
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      return;
    }
    onChange(itemValue);
  };

  const handleScroll = () => {
    clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(() => {
      const el = ref.current;
      if (!el) return;
      const next = Math.min(rendered.length - 1, Math.max(0, Math.round(el.scrollTop / ITEM_HEIGHT_PX)));
      if (rendered[next] && rendered[next].value !== value) {
        emittedByScrollRef.current = rendered[next].value;
        onChange(rendered[next].value);
      }
      recenter(next);
    }, SCROLL_SETTLE_MS);
  };

  return (
    <div
      ref={ref}
      className="wheel-column"
      onScroll={handleScroll}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      role="listbox"
      aria-label={ariaLabel}
    >
      <div className="wheel-spacer" />
      {rendered.map((item, row) => (
        <div
          key={loop ? row : item.value}
          role="option"
          aria-selected={item.value === value}
          className={`wheel-item ${item.value === value ? "selected" : ""}`}
          onClick={() => handleItemClick(item.value)}
        >
          {item.label}
        </div>
      ))}
      <div className="wheel-spacer" />
    </div>
  );
}

const pad = (value: number) => String(value).padStart(2, "0");

const daysInMonth = (year: number, month: number) => new Date(year, month, 0).getDate();

const YEARS_AHEAD = 5;

export interface WheelDatePickerProps {
  title: string;
  value: string | null;
  defaultYear?: number;
  minYear?: number;
  maxDate?: Date;
  onConfirm: (date: string) => void;
  onClear?: () => void;
  onClose: () => void;
}

export function WheelDatePicker({
  title,
  value,
  defaultYear,
  minYear = 1900,
  maxDate,
  onConfirm,
  onClear,
  onClose,
}: WheelDatePickerProps) {
  const initial = value ? new Date(value) : defaultYear ? new Date(defaultYear, 0, 1) : new Date();
  const [year, setYear] = useState<number>(initial.getFullYear());
  const [month, setMonth] = useState<number>(initial.getMonth() + 1);
  const [day, setDay] = useState<number>(initial.getDate());

  const maxYear = maxDate ? maxDate.getFullYear() : new Date().getFullYear() + YEARS_AHEAD;
  const years = useMemo<WheelItem[]>(
    () => Array.from({ length: maxYear - minYear + 1 }, (_, i) => ({ value: maxYear - i, label: String(maxYear - i) })),
    [minYear, maxYear]
  );
  const months = useMemo<WheelItem[]>(
    () => MONTHS.map((m, i) => ({ value: i + 1, label: m.name })),
    []
  );
  const days = useMemo<WheelItem[]>(
    () => Array.from({ length: daysInMonth(year, month) }, (_, i) => ({ value: i + 1, label: pad(i + 1) })),
    [year, month]
  );

  useEffect(() => {
    const max = daysInMonth(year, month);
    if (day > max) setDay(max);
  }, [year, month, day]);

  const selected = new Date(year, month - 1, day);
  const isFuture = !!maxDate && selected.getTime() > maxDate.getTime();

  return (
    <Modal
      title={title}
      onClose={onClose}
      size="sm"
      className="wheel-modal"
      footer={
        <>
          {onClear && (
            <ActionButton text="Wyczyść" onClick={onClear} className="cl-filter cc-discard-btn active" />
          )}
          <ActionButton
            text="Zatwierdź"
            className="cl-filter cc-save-btn active"
            onClick={() => onConfirm(`${year}-${pad(month)}-${pad(day)}`)}
            disabled={isFuture}
          />
        </>
      }
    >
      <div className="wheel-picker flex justify-center g-05">
        <div className="wheel-band" />
        <WheelColumn items={days} value={day} onChange={setDay} ariaLabel="Dzień" />
        <WheelColumn items={months} value={month} onChange={setMonth} ariaLabel="Miesiąc" />
        <WheelColumn items={years} value={year} onChange={setYear} ariaLabel="Rok" />
      </div>
      {isFuture && <span className="wheel-error c-red">Data nie może być z przyszłości.</span>}
    </Modal>
  );
}

export default WheelDatePicker;
