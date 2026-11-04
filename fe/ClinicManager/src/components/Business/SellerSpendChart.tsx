import { useMemo } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { KsefSpendChart } from "../../models/ksef_seller";
import { MONTH_LABELS_SHORT, MONTHS } from "../../utils/dateUtils";
import { CHART_INK } from "../../utils/chartPalette";
import { useResponsiveChart } from "../../hooks/useResponsiveChart";
import { formatAmount } from "../../utils/formatUtils";

export interface SellerSpendChartProps {
  data: KsefSpendChart | null;
  year: number;
  loading?: boolean;
  className?: string;
}

const ACCENT = "#fcb50c";

export function SellerSpendChart({
  data,
  year,
  loading = false,
  className = "",
}: SellerSpendChartProps) {
  const { margin: chartMargin, monthTickInterval } = useResponsiveChart({
    margin: { top: 16, right: 24, left: 8, bottom: 8 },
    mobileMargin: { right: 8, left: -8 },
  });
  const points = useMemo(
    () =>
      Array.from({ length: 12 }, (_, index) => {
        const point = data?.points.find((p) => p.month === index + 1);
        return {
          month: index + 1,
          gross: point?.gross ?? 0,
          invoiceCount: point?.invoiceCount ?? 0,
          manualExpenseCount: point?.manualExpenseCount ?? 0,
        };
      }),
    [data]
  );

  const hasSpend = points.some((point) => point.gross !== 0);

  const tooltipContent = ({ active, payload }: any) => {
    if (!active || !payload || payload.length === 0) return null;
    const point = payload[0].payload;
    return (
      <div className="chart-tooltip flex-column g-025">
        <p className="chart-tooltip-label">
          <span className="pc-y">
            {MONTHS[point.month - 1].name} {year}
          </span>
        </p>
        <p className="chart-tooltip-item flex g-1 space-between align-items-center">
          <span className="chart-tooltip-name">Wydatki</span>
          <span className="chart-tooltip-value tnum">{formatAmount(point.gross)} zł</span>
        </p>
        <p className="chart-tooltip-item flex g-1 space-between align-items-center">
          <span className="chart-tooltip-name">Faktury</span>
          <span className="chart-tooltip-value tnum">{point.invoiceCount}</span>
        </p>
        {point.manualExpenseCount > 0 && (
          <p className="chart-tooltip-item flex g-1 space-between align-items-center">
            <span className="chart-tooltip-name">Koszty ręczne</span>
            <span className="chart-tooltip-value tnum">{point.manualExpenseCount}</span>
          </p>
        )}
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
      <ResponsiveContainer width="100%" height={200}>
        <BarChart
          data={points}
          margin={chartMargin}
        >
          <CartesianGrid strokeDasharray="3 3" stroke={CHART_INK.grid} vertical={false} />
          <XAxis
            dataKey="month"
            tickFormatter={(value) => MONTH_LABELS_SHORT[value - 1] ?? ""}
            tick={{ fill: CHART_INK.tick, fontSize: 11 }}
            axisLine={{ stroke: CHART_INK.axis }}
            tickLine={false}
            tickMargin={8}
            interval={monthTickInterval}
          />
          <YAxis
            tick={{ fill: CHART_INK.tick, fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            width={52}
          />
          <Tooltip cursor={{ fill: "rgba(255,255,255,0.04)" }} content={tooltipContent} />
          <Bar dataKey="gross" fill={ACCENT} radius={[3, 3, 0, 0]} maxBarSize={38} />
        </BarChart>
      </ResponsiveContainer>
      {!hasSpend && <span className="pd-chart-empty italic">Brak faktur w tym roku</span>}
    </div>
  );
}

export default SellerSpendChart;
