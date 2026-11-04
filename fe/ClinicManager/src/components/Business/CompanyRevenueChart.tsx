import { useState, useEffect, useMemo, useCallback } from "react";
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
  ReferenceLine,
} from "recharts";
import {
  ChartMode,
  ChartDataPoint,
  EmployeeRevenueFilter,
  CompanyRevenue,
} from "../../models/statistics";
import { MONTHS, getDaysInMonth } from "../../utils/dateUtils";
import ChartTooltip from "./ChartTooltip";
import ChartCard from "./ChartCard";
import { SERIES_CONFIG } from "../../utils/statisticsUtils";
import { CHART_INK, CHART_SURFACE } from "../../utils/chartPalette";
import StatisticsService from "../../services/StatisticsService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import ChartTypeToggle, { type ChartType } from "./ChartTypeToggle";
import CumulativeToggle from "./CumulativeToggle";
import { useResponsiveChart } from "../../hooks/useResponsiveChart";

export interface CompanyRevenueChartProps {
  statRequest: EmployeeRevenueFilter;
}

export function CompanyRevenueChart({
  statRequest,
}: CompanyRevenueChartProps) {
  const [chartData, setChartData] = useState<ChartDataPoint[]>([]);
  const [chartType, setChartType] = useState<ChartType>("line");
  const [isCumulative, setIsCumulative] = useState(true);
  const [loading, setLoading] = useState(false);

  const { showAlert } = useAlert();
  const {
    margin: chartMargin,
    yAxisWidth,
    monthTickInterval,
  } = useResponsiveChart({
    margin: { top: 20, right: 40, left: 20, bottom: 10 },
    mobileMargin: { right: 8, left: 0 },
    narrowMargin: { bottom: 0 },
    yAxisWidth: 70,
    mobileYAxisWidth: 58,
  });
  const xAxisInterval =
    statRequest.mode === ChartMode.MONTHLY ? monthTickInterval : "preserveEnd";
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const [chartHeight, setChartHeight] = useState(() =>
    Math.round(Math.min(300, window.innerHeight / 3))
  );

  useEffect(() => {
    const handler = () => setChartHeight(
      Math.round(Math.min(300, window.innerHeight / 3))
    );
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  const processCompanyRevenueData = useCallback(
    (data: CompanyRevenue): ChartDataPoint[] => {
      const labels: string[] = [];

      if (statRequest.mode === ChartMode.MONTHLY) {
        for (let i = 0; i < 12; i++) {
          labels.push(MONTHS[i].name.substring(0, 3));
        }
      } else {
        const daysInMonth = getDaysInMonth(
          statRequest.year ?? currentYear,
          statRequest.month ?? currentMonth
        );
        for (let i = 1; i <= daysInMonth; i++) {
          labels.push(i.toString());
        }
      }

      return labels.map((label, index) => ({
        label,
        Przychód: data.revenueData[index] ?? null,
        Koszty: data.expensesData[index] ?? null,
        Dochód: data.incomeData[index] ?? null,
      }));
    },
    [statRequest.mode, statRequest.year, statRequest.month, currentYear, currentMonth]
  );

  const fetchCompanyRevenue = useCallback(() => {
    setLoading(true);
    StatisticsService.getCompanyRevenue(statRequest)
      .then((data) => {
        const processed = processCompanyRevenueData(data);
        setChartData(processed);
      })
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error fetching Company Revenue: ", error);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [statRequest, processCompanyRevenueData, showAlert]);

  useEffect(() => {
    fetchCompanyRevenue();
  }, [fetchCompanyRevenue]);

  const displayChartData = useMemo(() => {
    if (!isCumulative || chartType !== "line") return chartData;

    const cumulatives: Record<string, number> = {
      Przychód: 0,
      Koszty: 0,
      Dochód: 0,
    };

    return chartData.map((point) => {
      const newPoint: ChartDataPoint = { label: point.label };
      SERIES_CONFIG.forEach(({ key }) => {
        const value = point[key];
        if (value === null) {
          newPoint[key] = null;
        } else {
          cumulatives[key] += (value as number) || 0;
          newPoint[key] = cumulatives[key];
        }
      });
      return newPoint;
    });
  }, [chartData, isCumulative, chartType]);

  const yAxisTicks = useMemo(() => {
    const allValues = displayChartData
      .flatMap((point) => SERIES_CONFIG.map(({ key }) => point[key]))
      .filter((v): v is number => v !== null);

    if (allValues.length === 0) return [0];

    const maxValue = Math.max(...allValues);
    const minValue = Math.min(...allValues);
    const step = statRequest.mode === ChartMode.MONTHLY ? 5000 : 500;
    const roundedMin = Math.floor(minValue / step) * step;
    const minTick = roundedMin === minValue ? roundedMin - step : roundedMin;
    const roundedMax = Math.ceil(maxValue / step) * step;
    const maxTick = roundedMax === maxValue ? roundedMax + step : roundedMax;

    const ticks: number[] = [];
    for (let i = minTick; i <= maxTick; i += step) {
      ticks.push(i);
    }
    return ticks;
  }, [displayChartData, statRequest.mode]);

  const headerActions = (
    <>
      {chartType === "line" && (
        <CumulativeToggle
          active={isCumulative}
          onToggle={() => setIsCumulative((prev) => !prev)}
        />
      )}
      <ChartTypeToggle value={chartType} onChange={setChartType} />
    </>
  );

  return (
    <ChartCard
      className="company-revenue-chart width-max"
      title="Przychód · Koszty · Dochód"
      actions={headerActions}
    >
      <div className="chart-container">
        {loading ? (
          <div
            className="chart-loading flex justify-center align-items-center"
            style={{ height: chartHeight }}
          >
            Ładowanie...
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={chartHeight}>
            {chartType === "line" ? (
              <LineChart
                data={displayChartData}
                margin={chartMargin}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke={CHART_INK.grid}
                  vertical={false}
                />
                <XAxis
                  dataKey="label"
                  tick={{ fill: CHART_INK.tick, fontSize: 12 }}
                  axisLine={{ stroke: CHART_INK.axis }}
                  tickLine={false}
                  tickMargin={8}
                  interval={xAxisInterval}
                />
                <YAxis
                  tick={{ fill: CHART_INK.tick, fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(value) =>
                    `${value.toLocaleString("pl-PL")} zł`
                  }
                  ticks={yAxisTicks}
                  domain={[
                    yAxisTicks[0] || 0,
                    yAxisTicks[yAxisTicks.length - 1] || "auto",
                  ]}
                  width={yAxisWidth}
                  tickMargin={5}
                />
                <Tooltip
                  cursor={{ stroke: CHART_INK.axis, strokeWidth: 1 }}
                  content={
                    <ChartTooltip
                      mode={statRequest.mode}
                      month={statRequest.month ?? null}
                    />
                  }
                />
                {yAxisTicks[0] < 0 && (
                  <ReferenceLine
                    y={0}
                    stroke={CHART_INK.zeroLine}
                    strokeDasharray="3 3"
                  />
                )}
                <Legend
                  wrapperStyle={{ marginTop: "0.5rem" }}
                  formatter={(value) => (
                    <span className="chart-legend-label">{value}</span>
                  )}
                />
                {SERIES_CONFIG.map(({ key, color }) => (
                  <Line
                    key={key}
                    type="monotone"
                    dataKey={key}
                    stroke={color}
                    strokeWidth={2}
                    dot={{ r: 2.5, fill: color, strokeWidth: 0 }}
                    activeDot={{
                      r: 4,
                      fill: color,
                      stroke: CHART_SURFACE,
                      strokeWidth: 2,
                    }}
                    connectNulls={false}
                  />
                ))}
              </LineChart>
            ) : (
              <BarChart
                data={chartData}
                margin={chartMargin}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke={CHART_INK.grid}
                  vertical={false}
                />
                <XAxis
                  dataKey="label"
                  tick={{ fill: CHART_INK.tick, fontSize: 12 }}
                  axisLine={{ stroke: CHART_INK.axis }}
                  tickLine={false}
                  tickMargin={8}
                  interval={xAxisInterval}
                />
                <YAxis
                  tick={{ fill: CHART_INK.tick, fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(value) =>
                    `${value.toLocaleString("pl-PL")} zł`
                  }
                  ticks={yAxisTicks}
                  domain={[
                    yAxisTicks[0] || 0,
                    yAxisTicks[yAxisTicks.length - 1] || "auto",
                  ]}
                  width={yAxisWidth}
                  tickMargin={5}
                />
                <Tooltip
                  cursor={{ fill: "rgba(255,255,255,0.04)" }}
                  content={
                    <ChartTooltip
                      mode={statRequest.mode}
                      month={statRequest.month ?? null}
                    />
                  }
                  offset={60}
                />
                {yAxisTicks[0] < 0 && (
                  <ReferenceLine
                    y={0}
                    stroke={CHART_INK.zeroLine}
                    strokeDasharray="3 3"
                  />
                )}
                <Legend
                  wrapperStyle={{ marginTop: "0.5rem" }}
                  formatter={(value) => (
                    <span className="chart-legend-label">{value}</span>
                  )}
                />
                {SERIES_CONFIG.map(({ key, color }) => (
                  <Bar
                    key={key}
                    dataKey={key}
                    fill={color}
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

export default CompanyRevenueChart;
