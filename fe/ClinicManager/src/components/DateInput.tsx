import { useCallback } from "react";
import { CalendarDays } from "lucide-react";
import { useState, useEffect } from "react";
import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import { pl } from "date-fns/locale";

export interface DateInputProps {
  onChange: (formattedDate: string | null, formattedEndDate?: string | null) => void;
  selectedDate?: Date | string | null;
  selectedEndDate?: Date | string | null;
  allowRange?: boolean;
  showPlaceholder?: boolean;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  maxDate?: Date;
  yearDropdownItemNumber?: number;
}

const formatForBackend = (date: Date | null): string | null => {
  if (!date) return null;

  return (
    date.getFullYear() +
    "-" +
    String(date.getMonth() + 1).padStart(2, "0") +
    "-" +
    String(date.getDate()).padStart(2, "0")
  );
};

const DateInput = ({
  onChange,
  selectedDate,
  selectedEndDate,
  allowRange = false,
  showPlaceholder = false,
  placeholder,
  disabled = false,
  className = "",
  maxDate,
  yearDropdownItemNumber = 50,
}: DateInputProps) => {
  const initialDate = selectedDate
    ? new Date(selectedDate)
    : showPlaceholder || allowRange
    ? null
    : new Date();

  const [orderDate, setOrderDate] = useState<Date | null>(initialDate);
  const [endDate, setEndDate] = useState<Date | null>(
    selectedEndDate ? new Date(selectedEndDate) : null
  );

  const handleDateChange = useCallback((date: Date | null) => {
    setOrderDate(date);
    onChange(formatForBackend(date));
  }, [onChange]);

  const handleRangeChange = useCallback(
    (dates: [Date | null, Date | null]) => {
      const [start, end] = dates;
      setOrderDate(start);
      setEndDate(end);
      onChange(formatForBackend(start), formatForBackend(end));
    },
    [onChange]
  );

  useEffect(() => {
    if (selectedDate === null) {
      setOrderDate(null);
    } else if (selectedDate) {
      const dateObj = new Date(selectedDate);
      if (!orderDate || dateObj.getTime() !== orderDate.getTime()) {
        setOrderDate(dateObj);
      }
    }
  }, [selectedDate, orderDate]);

  useEffect(() => {
    if (!allowRange) return;
    if (selectedEndDate === null) {
      setEndDate(null);
    } else if (selectedEndDate) {
      const dateObj = new Date(selectedEndDate);
      if (!endDate || dateObj.getTime() !== endDate.getTime()) {
        setEndDate(dateObj);
      }
    }
  }, [selectedEndDate, endDate, allowRange]);

  const getLabel = (): string => {
    if (!orderDate) return placeholder ?? (allowRange ? "Wybierz zakres" : "DD-MM-YYYY");

    const from = orderDate.toLocaleDateString("pl-PL");
    if (!allowRange) return from;

    return endDate ? `${from} - ${endDate.toLocaleDateString("pl-PL")}` : `${from} - ...`;
  };

  const rangeProps = allowRange
    ? {
        selectsRange: true as const,
        startDate: orderDate ?? undefined,
        endDate: endDate ?? undefined,
        onChange: handleRangeChange,
      }
    : {
        selected: orderDate,
        onChange: handleDateChange,
      };

  return (
    <div className={`input-date-component transparent border-none align-items-center ${className}`}>
      <DatePicker
        {...(rangeProps as any)}
        disabled={disabled}
        customInput={
          <button
            disabled={disabled}
            className={`custom-calendar-button ${allowRange ? "range" : ""} flex pointer transparent border-none align-items-center ${disabled ? "not-allowed" : ""} ${orderDate ? "selected" : ""}`}
          >
            <CalendarDays
              className={`dl-icon-1 calendar-icon pointer ${orderDate ? "pc-y" : ""}`}
            />
            <span className={`calendar-label flex justify-center f-1 ${orderDate ? "pc-y" : ""}`}>
              {getLabel()}
            </span>
          </button>
        }
        popperPlacement="bottom-start"
        dateFormat="dd-MM-yyyy"
        dateFormatCalendar="LLLL"
        className="date-custom-input text-align-center"
        calendarClassName="date-custom-calendar transparent"
        showYearDropdown
        scrollableYearDropdown
        yearDropdownItemNumber={yearDropdownItemNumber}
        maxDate={maxDate}
        locale={pl}
      />
    </div>
  );
};

export default DateInput;
