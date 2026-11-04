import { useEffect, useMemo, useState } from "react";
import { Clock } from "lucide-react";
import Modal from "./Modal";
import ActionButton from "./ActionButton";
import { WheelColumn, WheelItem } from "./WheelDatePicker";

const pad = (value: number) => String(value).padStart(2, "0");

const digits = (count: number): WheelItem[] =>
  Array.from({ length: count }, (_, i) => ({ value: i, label: String(i) }));

const parseTime = (value: string | null, fallback: string): [number, number] => {
  const [hours, minutes] = (value ?? fallback).split(":").map(Number);
  return [
    Number.isFinite(hours) ? Math.min(Math.max(hours, 0), 23) : 0,
    Number.isFinite(minutes) ? Math.min(Math.max(minutes, 0), 59) : 0,
  ];
};

export interface TimeFieldProps {
  value: string | null;
  onChange: (value: string) => void;
  title: string;
  fallback?: string;
  disabled?: boolean;
  className?: string;
}

export function TimeField({ value, onChange, title, fallback = "08:00", disabled = false, className = "" }: TimeFieldProps) {
  const [open, setOpen] = useState<boolean>(false);
  const [hourTens, setHourTens] = useState<number>(0);
  const [hourUnits, setHourUnits] = useState<number>(0);
  const [minuteTens, setMinuteTens] = useState<number>(0);
  const [minuteUnits, setMinuteUnits] = useState<number>(0);

  const hourTensItems = useMemo(() => digits(3), []);
  const hourUnitsItems = useMemo(() => digits(hourTens === 2 ? 4 : 10), [hourTens]);
  const minuteTensItems = useMemo(() => digits(6), []);
  const minuteUnitsItems = useMemo(() => digits(10), []);

  useEffect(() => {
    if (hourTens === 2 && hourUnits > 3) setHourUnits(3);
  }, [hourTens, hourUnits]);

  const openPicker = () => {
    if (disabled) return;
    const [hours, minutes] = parseTime(value, fallback);
    setHourTens(Math.floor(hours / 10));
    setHourUnits(hours % 10);
    setMinuteTens(Math.floor(minutes / 10));
    setMinuteUnits(minutes % 10);
    setOpen(true);
  };

  const confirm = () => {
    onChange(`${pad(hourTens * 10 + hourUnits)}:${pad(minuteTens * 10 + minuteUnits)}`);
    setOpen(false);
  };

  return (
    <>
      <button
        type="button"
        disabled={disabled}
        onClick={openPicker}
        className={`time-field flex align-items-center g-05 pointer transparent ${disabled ? "not-allowed" : ""} ${className}`}
      >
        <Clock className="time-field-icon" />
        <span className="time-field-value">{value ?? "--:--"}</span>
      </button>
      {open && (
        <Modal
          title={title}
          onClose={() => setOpen(false)}
          size="sm"
          className="wheel-modal"
          footer={<ActionButton text="Zatwierdź" className="cl-filter tf-confirm-btn active" onClick={confirm} />}
        >
          <div className="wheel-picker wheel-time flex justify-center g-05">
            <div className="wheel-band" />
            <WheelColumn items={hourTensItems} value={hourTens} onChange={setHourTens} ariaLabel="Godzina - dziesiątki" loop={true} />
            <WheelColumn items={hourUnitsItems} value={hourUnits} onChange={setHourUnits} ariaLabel="Godzina - jedności" loop={true} />
            <span className="wheel-time-sep flex align-items-center">:</span>
            <WheelColumn items={minuteTensItems} value={minuteTens} onChange={setMinuteTens} ariaLabel="Minuty - dziesiątki" loop={true} />
            <WheelColumn items={minuteUnitsItems} value={minuteUnits} onChange={setMinuteUnits} ariaLabel="Minuty - jedności" loop={true} />
          </div>
        </Modal>
      )}
    </>
  );
}

export default TimeField;
