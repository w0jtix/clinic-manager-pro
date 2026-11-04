import React from "react";

export interface LabeledRowProps {
  label: React.ReactNode;
  children: React.ReactNode;
  labelClassName?: string;
  valueClassName?: string;
  className?: string;
}

export function LabeledRow({
  label,
  children,
  labelClassName = "",
  valueClassName = "",
  className = "",
}: LabeledRowProps) {
  return (
    <span className={`sh-m-row flex align-items-center space-between g-05 ${className}`}>
      <span className={`sh-m-label ${labelClassName}`}>{label}</span>
      <span className={`sh-m-value tnum ${valueClassName}`}>{children}</span>
    </span>
  );
}

export default LabeledRow;
