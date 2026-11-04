import { useState } from "react";
import { CalendarDays, X } from "lucide-react";
import ActionButton from "./ActionButton";
import WheelDatePicker from "./WheelDatePicker";
import { formatDate } from "../utils/dateUtils";
import { useLightPortals } from "../hooks/useLightPortals";

type DateValue = Date | string | null | undefined;

const toIsoDate = (value: DateValue): string | null => {
  if (!value) return null;
  if (typeof value === "string") return value.slice(0, 10);
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
};

export interface WheelDateFieldProps {
  value: DateValue;
  onChange: (date: string | null) => void;
  title?: string;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  maxDate?: Date;
  defaultYear?: number;
  className?: string;
  lightPortal?: boolean;
}

export function WheelDateField({
  value,
  onChange,
  title = "Wybierz datę",
  placeholder = "Wybierz datę",
  disabled = false,
  clearable = false,
  maxDate,
  defaultYear,
  className = "",
  lightPortal = false,
}: WheelDateFieldProps) {
  const [open, setOpen] = useState<boolean>(false);
  useLightPortals(open && lightPortal);
  const iso = toIsoDate(value);

  return (
    <>
      <ActionButton
        text={iso ? formatDate(iso) : placeholder}
        iconTitle={title}
        disabled={disabled}
        onClick={() => setOpen(true)}
        className={`wheel-date-btn ${iso ? "filled" : ""} ${className}`}
      >
        <CalendarDays className={`dl-icon-1 ${iso ? "pc-y" : ""}`} />
      </ActionButton>
      {open && (
        <WheelDatePicker
          title={title}
          value={iso}
          maxDate={maxDate}
          defaultYear={defaultYear}
          onConfirm={(date) => {
            onChange(date);
            setOpen(false);
          }}
          onClear={
            clearable
              ? () => {
                  onChange(null);
                  setOpen(false);
                }
              : undefined
          }
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}

export interface WheelDateRangeFieldProps {
  from: DateValue;
  to: DateValue;
  onChange: (from: string | null, to: string | null) => void;
  disabled?: boolean;
  className?: string;
}

export function WheelDateRangeField({ from, to, onChange, disabled = false, className = "" }: WheelDateRangeFieldProps) {
  const fromIso = toIsoDate(from);
  const toIso = toIsoDate(to);

  return (
    <div className={`wheel-date-range flex align-items-center g-05 ${className}`}>
      <WheelDateField
        value={fromIso}
        title="Data od"
        placeholder="Od"
        disabled={disabled}
        clearable={true}
        maxDate={toIso ? new Date(toIso) : undefined}
        onChange={(date) => onChange(date, toIso)}
      />
      <span className="wheel-date-sep">-</span>
      <WheelDateField
        value={toIso}
        title="Data do"
        placeholder="Do"
        disabled={disabled}
        clearable={true}
        onChange={(date) => onChange(fromIso, date)}
      />
      {(fromIso || toIso) && (
        <ActionButton
          iconTitle="Wyczyść zakres"
          disableText={true}
          disableBorder={true}
          disabled={disabled}
          onClick={() => onChange(null, null)}
        >
          <X className="dl-icon-1" />
        </ActionButton>
      )}
    </div>
  );
}

export default WheelDateField;
