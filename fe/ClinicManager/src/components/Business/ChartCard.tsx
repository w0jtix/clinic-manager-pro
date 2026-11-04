import React from "react";

export interface ChartCardProps {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
  bodyClassName?: string;
  children: React.ReactNode;
}

export function ChartCard({
  title,
  subtitle,
  actions,
  className = "",
  bodyClassName = "",
  children,
}: ChartCardProps) {
  const hasHeader = title !== undefined || actions !== undefined;
  return (
    <div className={`chart-card flex-column ${className}`}>
      {hasHeader && (
        <header className="chart-card-header flex align-items-center space-between g-1">
          <div className="chart-card-heading flex-column">
            {title !== undefined && (
              <span className="chart-card-title pc-y">{title}</span>
            )}
            {subtitle !== undefined && (
              <span className="chart-card-subtitle">{subtitle}</span>
            )}
          </div>
          {actions !== undefined && (
            <div className="chart-card-actions flex align-items-center g-05">
              {actions}
            </div>
          )}
        </header>
      )}
      <div className={`chart-card-body ${bodyClassName}`}>{children}</div>
    </div>
  );
}

export default ChartCard;
