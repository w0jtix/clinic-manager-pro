import { useMemo, useState } from "react";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { CompanyExpense, SalaryComponent } from "../../models/expense";
import { MONTHS, MONTH_LABELS_SHORT } from "../../utils/dateUtils";
import { CATEGORICAL, CHART_INK, CHART_SURFACE } from "../../utils/chartPalette";
import ChartCard from "./ChartCard";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import ChartTypeToggle, { type ChartType } from "./ChartTypeToggle";
import { useResponsiveChart } from "../../hooks/useResponsiveChart";
import { formatWholeCurrency } from "../../utils/formatUtils";

export interface SalaryHistoryChartProps {
  payouts: CompanyExpense[];
  loading?: boolean;
  className?: string;
}

const SERIES = [
  { key: "Razem", color: CATEGORICAL[0] },
  { key: "Podstawa", color: CATEGORICAL[1] },
  { key: "Premia", color: CATEGORICAL[2] },
  { key: "Premia Sprzedażowa", color: CATEGORICAL[3] },
];

const COMPONENT_KEYS: Record<SalaryComponent, string> = {
  [SalaryComponent.BASE]: "Podstawa",
  [SalaryComponent.BONUS]: "Premia",
  [SalaryComponent.SALE_BONUS]: "Premia Sprzedażowa",
};

const PAYOUT_DAY = 10;

const periodKey = (year: number, month: number): string =>
  `${year}-${String(month).padStart(2, "0")}`;

const formatPeriod = (key: string): string => {
  const [year, month] = key.split("-");
  return `${MONTHS[Number(month) - 1]?.name ?? ""} ${year}`;
};

const formatSalaryFor = (key: string): string => {
  const [year, month] = key.split("-").map(Number);
  const forMonth = month === 1 ? 12 : month - 1;
  const forYear = month === 1 ? year - 1 : year;
  return `${String(forMonth).padStart(2, "0")}.${forYear}`;
};

export function SalaryHistoryChart({
  payouts,
  loading = false,
  className = "",
}: SalaryHistoryChartProps) {
  const [chartType, setChartType] = useState<ChartType>("line");
  const { isCompact } = useBreakpoint();
  const { margin: chartMargin, yAxisWidth } = useResponsiveChart({
    margin: { top: 20, right: 30, left: 10, bottom: 10 },
    mobileMargin: { right: 8, left: -8 },
    yAxisWidth: 70,
    mobileYAxisWidth: 58,
  });
  const [activeSeries, setActiveSeries] = useState<string[]>(["Razem"]);

  const data = useMemo(() => {
    if (payouts.length === 0) return [];

    const byPeriod = new Map<string, Record<string, number>>();
    let firstYear = Infinity;
    let firstMonth = 1;

    payouts.forEach((expense) => {
      const date = new Date(expense.expenseDate);
      const year = date.getFullYear();
      const month = date.getMonth() + 1;
      const key = periodKey(year, month);
      const bucket = byPeriod.get(key) ?? {
        Podstawa: 0,
        Premia: 0,
        "Premia Sprzedażowa": 0,
      };
      expense.expenseItems.forEach((item) => {
        if (!item.salaryComponent) return;
        bucket[COMPONENT_KEYS[item.salaryComponent]] += item.price * item.quantity;
      });
      byPeriod.set(key, bucket);
      if (year < firstYear || (year === firstYear && month < firstMonth)) {
        firstYear = year;
        firstMonth = month;
      }
    });

    const now = new Date();
    let lastYear = now.getFullYear();
    let lastMonth = now.getMonth() + 1;
    if (now.getDate() < PAYOUT_DAY) {
      lastMonth -= 1;
      if (lastMonth === 0) {
        lastMonth = 12;
        lastYear -= 1;
      }
    }
    const latestBooked = [...byPeriod.keys()].sort().pop();
    if (latestBooked && latestBooked > periodKey(lastYear, lastMonth)) {
      const [bookedYear, bookedMonth] = latestBooked.split("-").map(Number);
      lastYear = bookedYear;
      lastMonth = bookedMonth;
    }

    const points = [];
    let year = firstYear;
    let month = firstMonth;
    while (year < lastYear || (year === lastYear && month <= lastMonth)) {
      const key = periodKey(year, month);
      const bucket = byPeriod.get(key);
      const base = bucket?.Podstawa ?? 0;
      const bonus = bucket?.Premia ?? 0;
      const saleBonus = bucket?.["Premia Sprzedażowa"] ?? 0;
      points.push({
        key,
        Razem: base + bonus + saleBonus,
        Podstawa: base,
        Premia: bonus,
        "Premia Sprzedażowa": saleBonus,
      });
      month += 1;
      if (month > 12) {
        month = 1;
        year += 1;
      }
    }
    return points;
  }, [payouts]);

  const toggleSeries = (key: string) => {
    setActiveSeries((prev) => {
      if (prev.includes(key)) {
        return prev.length === 1 ? prev : prev.filter((s) => s !== key);
      }
      return SERIES.filter((s) => s.key === key || prev.includes(s.key)).map(
        (s) => s.key
      );
    });
  };

  const firstKey = data[0]?.key;

  const renderTick = ({ x, y, payload }: any) => {
    const [year, month] = String(payload.value).split("-");
    const monthIndex = Number(month) - 1;
    const showYear = Number(month) === 1 || payload.value === firstKey;
    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={0}
          dy={12}
          textAnchor="middle"
          fill={CHART_INK.tick}
          fontSize={11}
        >
          {MONTH_LABELS_SHORT[monthIndex]}
        </text>
        {showYear && (
          <text
            x={0}
            dy={26}
            textAnchor="middle"
            fill={CHART_INK.label}
            fontSize={11}
            fontWeight={600}
          >
            {year}
          </text>
        )}
      </g>
    );
  };

  const headerActions = (
    <>
      <div className="cat-seg-group flex align-items-center g-025">
        {SERIES.map((series) => (
          <button
            type="button"
            key={series.key}
            className={`cat-seg align-items-center pointer ${
              activeSeries.includes(series.key) ? "active" : ""
            }`}
            onClick={() => toggleSeries(series.key)}
          >
            <span
              className="dl-dot"
              style={{ backgroundColor: series.color }}
            />
            {isCompact && series.key === "Premia Sprzedażowa"
              ? "Sprzedaż"
              : series.key}
          </button>
        ))}
      </div>
      <ChartTypeToggle value={chartType} onChange={setChartType} />
    </>
  );

  const visibleSeries = SERIES.filter((s) => activeSeries.includes(s.key));

  const tooltipContent = ({ active, payload, label }: any) => {
    if (!active || !payload || payload.length === 0) return null;
    return (
      <div className="chart-tooltip flex-column g-025">
        <p className="chart-tooltip-label flex-column g-025">
          <span className="chart-tooltip-header pc-y">{formatPeriod(String(label))}</span>
          <span className="chart-tooltip-sub">
            Wypłata za {formatSalaryFor(String(label))}
          </span>
        </p>
        {payload.map((entry: any) => (
          <p
            key={entry.name}
            className="chart-tooltip-item flex g-1 space-between align-items-center"
          >
            <span className="chart-tooltip-name flex align-items-center g-05">
              <span
                className="chart-tooltip-swatch"
                style={{ backgroundColor: entry.color }}
              />
              {entry.name}
            </span>
            <span className="chart-tooltip-value tnum">
              {formatWholeCurrency(entry.value)}
            </span>
          </p>
        ))}
      </div>
    );
  };

  return (
    <ChartCard
      className={`salary-history-chart width-max ${className}`}
      actions={headerActions}
    >
      <div className="chart-container">
        {loading ? (
          <div className="chart-loading flex justify-center align-items-center" style={{ height: 260 }}>
            Ładowanie...
          </div>
        ) : data.length === 0 ? (
          <div className="chart-loading flex justify-center align-items-center" style={{ height: 260 }}>
            Brak danych
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            {chartType === "line" ? (
              <LineChart data={data} margin={chartMargin}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_INK.grid} vertical={false} />
                <XAxis
                  dataKey="key"
                  tick={renderTick}
                  height={40}
                  axisLine={{ stroke: CHART_INK.axis }}
                  tickLine={false}
                  interval={0}
                />
                <YAxis
                  tick={{ fill: CHART_INK.tick, fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={formatWholeCurrency}
                  width={yAxisWidth}
                  tickMargin={5}
                />
                <Legend
                  wrapperStyle={{ marginTop: "0.5rem" }}
                  formatter={(value) => (
                    <span className="chart-legend-label">{value}</span>
                  )}
                />
                <Tooltip
                  cursor={{ stroke: CHART_INK.axis, strokeWidth: 1 }}
                  content={tooltipContent}
                />
                {visibleSeries.map((series) => (
                  <Line
                    key={series.key}
                    type="monotone"
                    dataKey={series.key}
                    stroke={series.color}
                    strokeWidth={2}
                    dot={{ r: 2.5, fill: series.color, strokeWidth: 0 }}
                    activeDot={{
                      r: 4,
                      fill: series.color,
                      stroke: CHART_SURFACE,
                      strokeWidth: 2,
                    }}
                  />
                ))}
              </LineChart>
            ) : (
              <BarChart data={data} margin={chartMargin}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_INK.grid} vertical={false} />
                <XAxis
                  dataKey="key"
                  tick={renderTick}
                  height={40}
                  axisLine={{ stroke: CHART_INK.axis }}
                  tickLine={false}
                  interval={0}
                />
                <YAxis
                  tick={{ fill: CHART_INK.tick, fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={formatWholeCurrency}
                  width={yAxisWidth}
                  tickMargin={5}
                />
                <Legend
                  wrapperStyle={{ marginTop: "0.5rem" }}
                  formatter={(value) => (
                    <span className="chart-legend-label">{value}</span>
                  )}
                />
                <Tooltip
                  cursor={{ fill: "rgba(255,255,255,0.04)" }}
                  content={tooltipContent}
                  offset={40}
                />
                {visibleSeries.map((series) => (
                  <Bar
                    key={series.key}
                    dataKey={series.key}
                    fill={series.color}
                    radius={[3, 3, 0, 0]}
                  />
                ))}
              </BarChart>
            )}
          </ResponsiveContainer>
        )}
      </div>
    </ChartCard>
  );
}

export default SalaryHistoryChart;
