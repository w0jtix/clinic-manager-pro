import { useState } from "react";
import { MONTHS, MONTH_LABELS_SHORT } from "../../utils/dateUtils";
import { ClientSpendChart as ClientSpendChartData } from "../../models/client";
import { formatWholeAmount } from "../../utils/formatUtils";

export interface ClientSpendChartProps {
  data: ClientSpendChartData | null;
  loading?: boolean;
  showAmounts?: boolean;
}

export function ClientSpendChart({
  data,
  loading = false,
  showAmounts = true,
}: ClientSpendChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const visitCounts = data?.visitCounts;
  const useAmounts = showAmounts && data?.months != null;
  const months = useAmounts
    ? (data?.months as number[])
    : visitCounts ?? new Array<number>(12).fill(0);
  const max = Math.max(...months, 1);

  if (loading) {
    return <div className="cl-chart cl-chart-empty">Ładowanie...</div>;
  }

  return (
    <div className="cl-chart">
      <div className="cl-bars">
        {months.map((value, i) => {
          const empty = value <= 0;
          const visitCount = visitCounts?.[i];
          return (
            <div
              className="cl-bar-col relative"
              key={i}
              onMouseEnter={() => setHoveredIndex(i)}
              onMouseLeave={() => setHoveredIndex(null)}
            >
              {hoveredIndex === i && (useAmounts || visitCount != null) && (
                <div className="chart-tooltip cl-bar-tooltip absolute">
                  <p className="chart-tooltip-label">{MONTHS[i].name}</p>
                  {visitCount != null && (
                    <p className="chart-tooltip-item flex g-1 space-between align-items-center">
                      <span className="chart-tooltip-name">Wizyty</span>
                      <span className="chart-tooltip-value tnum">{visitCount}</span>
                    </p>
                  )}
                  {useAmounts && (
                    <p className="chart-tooltip-item flex g-1 space-between align-items-center">
                      <span className="chart-tooltip-name">Wydatki</span>
                      <span className="chart-tooltip-value tnum">
                        {formatWholeAmount(value)} zł
                      </span>
                    </p>
                  )}
                </div>
              )}
              <span
                className={`cl-bar ${empty ? "empty" : ""}`}
                style={{ height: empty ? "4%" : `${Math.max(8, (value / max) * 100)}%` }}
              />
              <span className="cl-bar-lbl">{MONTH_LABELS_SHORT[i]}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default ClientSpendChart;
