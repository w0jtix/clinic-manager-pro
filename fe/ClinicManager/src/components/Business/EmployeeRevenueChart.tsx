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
} from "recharts";
import {
  ChartMode,
  FrontendEmployeeRevenueSeries,
  ChartDataPoint,
  EmployeeRevenueFilter,
  EmployeeRevenue,
} from "../../models/statistics";
import { processEmployeeRevenueResponse } from "../../utils/statisticsUtils";
import ChartTooltip from "./ChartTooltip";
import ChartCard from "./ChartCard";
import { CHART_INK, CHART_SURFACE } from "../../utils/chartPalette";
import StatisticsService from "../../services/StatisticsService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import ChartTypeToggle, { type ChartType } from "./ChartTypeToggle";
import CumulativeToggle from "./CumulativeToggle";
import { useResponsiveChart } from "../../hooks/useResponsiveChart";

export interface EmployeeRevenueChartProps {
  selectedEmployeeIds?: number[];
  statRequest: EmployeeRevenueFilter;
}

export function EmployeeRevenueChart({
  selectedEmployeeIds,
  statRequest,
}: EmployeeRevenueChartProps) {
  const [rawData, setRawData] = useState<EmployeeRevenue | null>(null);
  const [chartType, setChartType] = useState<ChartType>("line");
  const [isCumulative, setIsCumulative] = useState(true);
  const [loading, setLoading] = useState(false);
  const [chartHeight, setChartHeight] = useState(() =>
    Math.round(Math.min(300, window.innerHeight / 3))
  );

  const { showAlert } = useAlert();
  const { margin: chartMargin, yAxisWidth } = useResponsiveChart({
    margin: { top: 20, right: 40, left: 20, bottom: 10 },
    mobileMargin: { right: 8, left: 0 },
    narrowMargin: { bottom: 0 },
    yAxisWidth: 64,
    mobileYAxisWidth: 52,
  });

  useEffect(() => {
    const handler = () =>
      setChartHeight(Math.round(Math.min(300, window.innerHeight / 3)));
    window.addEventListener("resize", handler);
    return () => window.removeEventListener("resize", handler);
  }, []);

  const fetchEmployeeRevenues = useCallback(() => {
    setLoading(true);
    StatisticsService.getEmployeeRevenue(statRequest)
      .then((backendData) => setRawData(backendData))
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error fetching Employee Revenue Statistics: ", error);
      })
      .finally(() => setLoading(false));
  }, [statRequest, showAlert]);

  useEffect(() => {
    fetchEmployeeRevenues();
  }, [fetchEmployeeRevenues]);

  const { chartData, series } = useMemo<{
    chartData: ChartDataPoint[];
    series: FrontendEmployeeRevenueSeries[];
  }>(() => {
    if (!rawData) return { chartData: [], series: [] };
    let filteredData = rawData;
    if (selectedEmployeeIds && selectedEmployeeIds.length > 0) {
      filteredData = {
        ...rawData,
        series: rawData.series.filter((s) =>
          selectedEmployeeIds.includes(s.employeeId)
        ),
      };
    }
    return processEmployeeRevenueResponse(filteredData, statRequest);
  }, [rawData, selectedEmployeeIds, statRequest]);

  const allData = useMemo<{
    chartData: ChartDataPoint[];
    series: FrontendEmployeeRevenueSeries[];
  }>(() => {
    if (!rawData) return { chartData: [], series: [] };
    return processEmployeeRevenueResponse(rawData, statRequest);
  }, [rawData, statRequest]);

  const displayChartData = useMemo(() => {
    if (!isCumulative || chartType !== "line") return chartData;
    const employeeNames = series.map((s) => s.employeeName);
    const cumulatives: Record<string, number> = {};
    employeeNames.forEach((name) => (cumulatives[name] = 0));
    return chartData.map((point) => {
      const newPoint: ChartDataPoint = { label: point.label };
      employeeNames.forEach((name) => {
        const value = point[name];
        if (value === null) {
          newPoint[name] = null;
        } else {
          cumulatives[name] += (value as number) || 0;
          newPoint[name] = cumulatives[name];
        }
      });
      return newPoint;
    });
  }, [chartData, series, isCumulative, chartType]);

  const axisSeries = useMemo(() => {
    const { chartData: allChart, series: allSeries } = allData;
    if (!isCumulative || chartType !== "line") return allSeries;
    return allSeries.map((s) => {
      const cumulativeData: (number | null)[] = [];
      let cumulative = 0;
      allChart.forEach((point) => {
        const value = point[s.employeeName];
        if (value === null) {
          cumulativeData.push(null);
        } else {
          cumulative += (value as number) || 0;
          cumulativeData.push(cumulative);
        }
      });
      return { ...s, data: cumulativeData };
    });
  }, [allData, isCumulative, chartType]);

  const yAxisTicks = useMemo(() => {
    if (axisSeries.length === 0) return [0];
    const allValues = axisSeries
      .flatMap((s) => s.data)
      .filter((v): v is number => v !== null);
    if (allValues.length === 0) return [0];
    const maxValue = Math.max(...allValues);
    const minValue = Math.min(...allValues);
    const step = statRequest.mode === ChartMode.MONTHLY ? 2000 : 100;
    const roundedMin = Math.floor(minValue / step) * step;
    const minTick = Math.max(0, roundedMin === minValue ? roundedMin - step : roundedMin);
    const roundedMax = Math.ceil(maxValue / step) * step;
    const maxTick = roundedMax === maxValue ? roundedMax + step : roundedMax;
    const ticks: number[] = [];
    for (let i = minTick; i <= maxTick; i += step) ticks.push(i);
    return ticks;
  }, [axisSeries, statRequest.mode]);

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
      className="employee-revenue-chart width-max"
      title="Przychód pracowników"
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
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_INK.grid} vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={{ fill: CHART_INK.tick, fontSize: 12 }}
                  axisLine={{ stroke: CHART_INK.axis }}
                  tickLine={false}
                  tickMargin={8}
                />
                <YAxis
                  tick={{ fill: CHART_INK.tick, fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(value) => `${value.toLocaleString("pl-PL")} zł`}
                  ticks={yAxisTicks}
                  domain={[yAxisTicks[0] || 0, yAxisTicks[yAxisTicks.length - 1] || "auto"]}
                  width={yAxisWidth}
                  tickMargin={5}
                />
                <Tooltip
                  cursor={{ stroke: CHART_INK.axis, strokeWidth: 1 }}
                  content={<ChartTooltip mode={statRequest.mode} month={statRequest.month ?? null} />}
                />
                <Legend
                  wrapperStyle={{ marginTop: "0.5rem" }}
                  formatter={(value) => <span className="chart-legend-label">{value}</span>}
                />
                {series.map((s) => (
                  <Line
                    key={s.employeeId}
                    type="monotone"
                    dataKey={s.employeeName}
                    stroke={s.color}
                    strokeWidth={2}
                    dot={{ r: 2.5, fill: s.color, strokeWidth: 0 }}
                    activeDot={{ r: 4, fill: s.color, stroke: CHART_SURFACE, strokeWidth: 2 }}
                    connectNulls={false}
                  />
                ))}
              </LineChart>
            ) : (
              <BarChart
                data={chartData}
                margin={chartMargin}
              >
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_INK.grid} vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={{ fill: CHART_INK.tick, fontSize: 12 }}
                  axisLine={{ stroke: CHART_INK.axis }}
                  tickLine={false}
                  tickMargin={8}
                />
                <YAxis
                  tick={{ fill: CHART_INK.tick, fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(value) => `${value.toLocaleString("pl-PL")} zł`}
                  ticks={yAxisTicks}
                  domain={[yAxisTicks[0] || 0, yAxisTicks[yAxisTicks.length - 1] || "auto"]}
                  width={yAxisWidth}
                  tickMargin={5}
                />
                <Tooltip
                  cursor={{ fill: "rgba(255,255,255,0.04)" }}
                  content={<ChartTooltip mode={statRequest.mode} month={statRequest.month ?? null} />}
                  offset={60}
                />
                <Legend
                  wrapperStyle={{ marginTop: "0.5rem" }}
                  formatter={(value) => <span className="chart-legend-label">{value}</span>}
                />
                {series.map((s) => (
                  <Bar key={s.employeeId} dataKey={s.employeeName} fill={s.color} radius={[3, 3, 0, 0]} />
                ))}
              </BarChart>
            )}
          </ResponsiveContainer>
        )}
      </div>
    </ChartCard>
  );
}

export default EmployeeRevenueChart;
