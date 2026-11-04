import React from "react";

export type KpiTone = "neutral" | "positive" | "negative" | "accent";

export interface KpiDelta {
  label: string;
  percent: number;
  title?: string;
}

export interface KpiTileProps {
  label: string;
  value: string;
  tone?: KpiTone;
  icon?: React.ReactNode;
  deltas?: KpiDelta[];
  className?: string;
}

const formatPercent = (percent: number): string => {
  const rounded = Math.round(percent * 10) / 10;
  const sign = rounded > 0 ? "+" : "";
  return `${sign}${rounded.toLocaleString("pl-PL")}%`;
};

const deltaTone = (percent: number): string =>
  percent > 0 ? "positive" : percent < 0 ? "negative" : "flat";

export function KpiTile({
  label,
  value,
  tone = "neutral",
  icon,
  deltas,
  className = "",
}: KpiTileProps) {
  return (
    <div className={`kpi-tile flex-column ${className}`}>
      <div className="kpi-head flex align-items-center g-05">
        {icon && <span className="kpi-icon flex align-items-center">{icon}</span>}
        <span className="kpi-label">{label}</span>
      </div>
      <span className={`kpi-value tnum kpi-${tone}`}>{value}</span>
      {deltas && deltas.length > 0 && (
        <div className="kpi-deltas flex-column g-025">
          {deltas.map((delta, index) => (
            <div
              key={index}
              className="kpi-delta flex align-items-center g-05"
              title={delta.title}
            >
              <span className={`kpi-delta-value tnum kpi-${deltaTone(delta.percent)}`}>
                {delta.percent > 0 ? "▲" : delta.percent < 0 ? "▼" : "–"}{" "}
                {formatPercent(Math.abs(delta.percent))}
              </span>
              <span className="kpi-delta-label">{delta.label}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default KpiTile;
