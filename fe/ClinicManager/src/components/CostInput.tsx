import React, { useCallback } from "react";
import { useState, useEffect, useRef } from "react";

export interface CostInputProps {
  initialValue?: number;
  onChange: (value: number) => void;
  selectedCost?: number;
  disabled?: boolean;
  placeholder?: string;
  min?: number;
  max?: number;
  className?: string;
}

const formatCost = (value: number): string =>
  value === 0 ? "" : (Math.round(value * 100) / 100).toFixed(2).replace(".", ",");

const CostInput = ({
  initialValue = 0.0,
  onChange,
  selectedCost,
  disabled = false,
  placeholder = "0,00",
  min = 0,
  max,
  className = "",
}: CostInputProps) => {
  const [cost, setCost] = useState<number>(selectedCost ?? initialValue);
  const [display, setDisplay] = useState<string>(formatCost(selectedCost ?? initialValue));
  const isEditing = useRef<boolean>(false);

  useEffect(() => {
    onChange(cost);
  }, [cost]);

  useEffect(() => {
    if (selectedCost !== undefined && selectedCost !== cost) {
      setCost(selectedCost);
      if (!isEditing.current) {
        setDisplay(formatCost(selectedCost));
      }
    }
  }, [selectedCost]);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>): void => {
      const inputValue = e.target.value.replace(".", ",");

      if (!/^\d*(,\d{0,2})?$/.test(inputValue)) {
        return;
      }
      setDisplay(inputValue);

      if (inputValue === "" || inputValue === ",") {
        setCost(0);
        return;
      }

      const newCost = parseFloat(inputValue.replace(",", "."));
      if (isNaN(newCost)) return;

      let constrainedCost = newCost;
      if (min !== undefined && constrainedCost < min) {
        constrainedCost = min;
      }
      if (max !== undefined && constrainedCost > max) {
        constrainedCost = max;
      }
      setCost(constrainedCost);
    },
    [min, max]
  );

  const handleFocus = useCallback((e: React.FocusEvent<HTMLInputElement>): void => {
    isEditing.current = true;
    const input = e.currentTarget;
    requestAnimationFrame(() => input.setSelectionRange(0, input.value.length));
  }, []);

  const handleBlur = useCallback((): void => {
    isEditing.current = false;
    setDisplay(formatCost(cost));
  }, [cost]);

  return (
    <div className={`digit-input-container ${disabled ? "disabled" : ""} ${className}`}>
      <input
        type="text"
        inputMode="decimal"
        className={`cost-input flex align-items-center space-between g-10px pointer transparent text-align-center ${disabled ? "disabled" : ""}  ${className}`}
        value={display}
        onChange={handleInputChange}
        onFocus={handleFocus}
        onBlur={handleBlur}
        placeholder={placeholder}
        disabled={disabled}
      />
    </div>
  );
};

export default CostInput;
