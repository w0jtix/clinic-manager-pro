import React from "react";
import ActionButton from "./ActionButton";

export type CycleFilterTone = "green" | "red" | "yellow" | "blue" | "teal" | "grey";

export interface CycleFilterProps {
  label: string;
  value: string;
  tone?: CycleFilterTone | null;
  icon?: React.ReactNode;
  iconTitle?: string;
  onClick: () => void;
  className?: string;
}

const formatValue = (value: string): string =>
  value ? value.charAt(0).toUpperCase() + value.slice(1).toLowerCase() : value;

export function CycleFilter({
  label,
  value,
  tone = null,
  icon,
  iconTitle,
  onClick,
  className = "",
}: CycleFilterProps) {
  return (
    <div className={`cycle-filter flex align-items-center g-05 ${className}`}>
      <span className="cycle-filter-label nowrap">{label}</span>
      <ActionButton
        iconTitle={iconTitle ?? label}
        onClick={onClick}
        className={`cycle-filter-btn ${tone ? `cf-${tone}` : ""}`}
      >
        {icon}
        <span className="cycle-filter-value nowrap">{formatValue(value)}</span>
      </ActionButton>
    </div>
  );
}

export default CycleFilter;
