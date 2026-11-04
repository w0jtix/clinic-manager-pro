import { useMemo } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { SupplyChart as SupplyChartData } from "../../models/supply-history";
import { MONTH_LABELS_SHORT, formatDate } from "../../utils/dateUtils";
import { CHART_INK, CHART_SURFACE } from "../../utils/chartPalette";

export interface SupplyChartProps {
  data: SupplyChartData | null;
  year: number;
  loading?: boolean;
  className?: string;
}

const ACCENT = "#fcb50c";

const toIsoDate = (timestamp: number): string => {
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate()
  ).padStart(2, "0")}`;
};

export function SupplyChart({
  data,
  year,
  loading = false,
  className = "",
}: SupplyChartProps) {
  const yearStart = useMemo(() => new Date(year, 0, 1).getTime(), [year]);
  const yearEnd = useMemo(() => new Date(year, 11, 31).getTime(), [year]);

  const points = useMemo(() => {
    if (!data) return [];
    return [
      { ts: yearStart, supply: data.openingSupply },
      ...data.points.map((point) => ({
        ts: new Date(point.date).getTime(),
        supply: point.supply,
      })),
    ];
  }, [data, yearStart]);

  const monthTicks = useMemo(
    () => Array.from({ length: 12 }, (_, index) => new Date(year, index, 1).getTime()),
    [year]
  );

  const hasMovement = (data?.points.length ?? 0) > 0;

  const tooltipContent = ({ active, payload, label }: any) => {
    if (!active || !payload || payload.length === 0) return null;
    return (
      <div className="chart-tooltip flex-column g-025">
        <p className="chart-tooltip-label">
          <span className="pc-y">{formatDate(toIsoDate(Number(label)))}</span>
        </p>
        <p className="chart-tooltip-item flex g-1 space-between align-items-center">
          <span className="chart-tooltip-name">Stan</span>
          <span className="chart-tooltip-value tnum">
            {payload[0].value} szt.
          </span>
        </p>
      </div>
    );
  };

  if (loading) {
    return (
      <div className={`chart-loading flex justify-center align-items-center ${className}`}>
        Ładowanie...
      </div>
    );
  }

  return (
    <div className={`pd-chart ${className}`}>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={points} margin={{ top: 16, right: 24, left: 8, bottom: 8 }}>
          <defs>
            <linearGradient id="pd-supply-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="rgba(252,181,12,0.28)" />
              <stop offset="100%" stopColor="rgba(252,181,12,0)" />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke={CHART_INK.grid} vertical={false} />
          <XAxis
            dataKey="ts"
            type="number"
            scale="time"
            domain={[yearStart, yearEnd]}
            ticks={monthTicks}
            interval={0}
            tickFormatter={(value) =>
              MONTH_LABELS_SHORT[new Date(value).getMonth()] ?? ""
            }
            tick={{ fill: CHART_INK.tick, fontSize: 11 }}
            axisLine={{ stroke: CHART_INK.axis }}
            tickLine={false}
            tickMargin={8}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fill: CHART_INK.tick, fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            width={40}
          />
          <Tooltip
            cursor={{ stroke: CHART_INK.axis, strokeWidth: 1 }}
            content={tooltipContent}
          />
          <Line
            type="stepAfter"
            dataKey="supply"
            stroke={ACCENT}
            strokeWidth={2}
            fill="url(#pd-supply-fill)"
            dot={false}
            activeDot={{ r: 4, fill: ACCENT, stroke: CHART_SURFACE, strokeWidth: 2 }}
          />
        </LineChart>
      </ResponsiveContainer>
      {!hasMovement && (
        <span className="pd-chart-empty italic">Brak ruchu w tym roku</span>
      )}
    </div>
  );
}

export default SupplyChart;
