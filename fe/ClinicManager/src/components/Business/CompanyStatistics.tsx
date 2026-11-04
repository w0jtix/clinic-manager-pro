import { useState, useCallback, useEffect, useMemo } from "react";
import { useAlert } from "../Alert/AlertProvider";
import {
  EmployeeRevenueFilter,
  ChartMode,
  CompanyStats,
  CompanyFinancialSummary,
} from "../../models/statistics";
import StatisticsService from "../../services/StatisticsService";
import { AlertType } from "../../models/alert";
import CompanyRevenueChart from "./CompanyRevenueChart";
import ChartCard from "./ChartCard";
import { KpiDelta } from "./KpiTile";
import {
  TrendingUp,
  TrendingDown,
  Wallet,
  Gauge,
  Receipt,
} from "lucide-react";
import DropdownSelect from "../DropdownSelect";
import { MONTHS } from "../../utils/dateUtils";
import { chartModeItems } from "../../models/statistics";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { FINANCE, SPLIT, CHART_INK, CHART_SURFACE } from "../../utils/chartPalette";
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Tooltip,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ReferenceLine,
} from "recharts";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import PeriodPill from "../PeriodPill";
import { formatWholeCurrency } from "../../utils/formatUtils";

const OFF_BOOK_HINT =
  "Wpływy z wizyt nieewidencjonowanych. Kwota zawiera się w Przychodzie - nie jest doliczana osobno.";

interface MiniDatum {
  name: string;
  color: string;
  amount: number;
  percentLabel?: string;
  [key: string]: string | number | undefined;
}

function MiniTooltip({ active, payload }: any) {
  if (!active || !payload || !payload.length) return null;
  const entry = payload.find((p: any) => p.dataKey !== "base") ?? payload[0];
  const d = entry.payload as MiniDatum;
  return (
    <div className="chart-tooltip flex-column g-025">
      <p className="chart-tooltip-item flex g-1 space-between align-items-center">
        <span className="chart-tooltip-name flex align-items-center g-05">
          <span className="chart-tooltip-swatch" style={{ backgroundColor: d.color }} />
          {d.name}
        </span>
        <span className="chart-tooltip-value tnum">{formatWholeCurrency(d.amount)}</span>
      </p>
    </div>
  );
}

export function CompanyStatistics() {
  const { items: expenseCategoryItems } = useExpenseCategories();
  const { showAlert } = useAlert();
  const { isNarrow } = useBreakpoint();

  const [statRequest, setStatRequest] = useState<EmployeeRevenueFilter>({
    mode: ChartMode.DAILY,
    month: new Date().getMonth() + 1,
    year: new Date().getFullYear(),
  });
  const [companyStats, setCompanyStats] = useState<CompanyStats | null>(null);
  const [companySummary, setCompanySummary] =
    useState<CompanyFinancialSummary | null>(null);
  const [unit, setUnit] = useState<"zł" | "%">("%");

  const fetchCompanyStats = useCallback(async () => {
    StatisticsService.getCompanyStats(statRequest)
      .then((data) => setCompanyStats(data))
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error fetching Company Stats: ", error);
      });
  }, [statRequest, showAlert]);

  const fetchCompanySummary = useCallback(async () => {
    StatisticsService.getCompanyFinancialSummary(statRequest)
      .then((data) => setCompanySummary(data))
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error fetching Company Financial Summary: ", error);
      });
  }, [statRequest, showAlert]);

  useEffect(() => {
    fetchCompanyStats();
  }, [fetchCompanyStats]);

  useEffect(() => {
    fetchCompanySummary();
  }, [fetchCompanySummary]);
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;

  const handleYearChange = useCallback(
    (year: number | null) => {
      setStatRequest((prev) => {
        let newMonth = prev.month;
        if (year === currentYear && prev.month && prev.month > currentMonth) {
          newMonth = currentMonth;
        }
        return { ...prev, year: year ?? prev.year, month: newMonth };
      });
    },
    [currentYear, currentMonth]
  );

  const handleMonthChange = useCallback(
    (month: number | null) => {
      setStatRequest((prev) => ({ ...prev, month: month ?? prev.month }));
    },
    []
  );

  const handleChartModeChange = useCallback((newMode: ChartMode) => {
    setStatRequest((prev) => ({ ...prev, mode: newMode }));
  }, []);

  const isDaily = statRequest.mode === ChartMode.DAILY;
  const comparisonDate = new Date(
    statRequest.year!,
    (statRequest.month ?? 1) - 1,
    1
  );


  const periodModeItem =
    chartModeItems.find((m) => m.id === statRequest.mode) ?? chartModeItems[0];
  const periodMain = isDaily
    ? `${statRequest.month ? MONTHS[statRequest.month - 1].name : ""} ${statRequest.year ?? ""}`.trim()
    : `${statRequest.year ?? ""}`;
  const periodSub = periodModeItem.name.toLowerCase();

  const cmpLabel = (date: Date) =>
    isNarrow
      ? `vs ${String(date.getMonth() + 1).padStart(2, "0")}.${date.getFullYear()}`
      : `vs ${MONTHS[date.getMonth()].name.toLowerCase()} ${date.getFullYear()}`;

  const buildDeltas = (prevPeriod: number, lastYear: number): KpiDelta[] => {
    if (!isDaily || !companySummary) return [];
    const prevMonthDate = new Date(
      comparisonDate.getFullYear(),
      comparisonDate.getMonth() - 1,
      1
    );
    const prevYearDate = new Date(
      comparisonDate.getFullYear() - 1,
      comparisonDate.getMonth(),
      1
    );
    return [
      {
        label: cmpLabel(prevMonthDate),
        percent: prevPeriod,
        title: "Porównanie do poprzedniego miesiąca",
      },
      {
        label: cmpLabel(prevYearDate),
        percent: lastYear,
        title: "Porównanie do tego samego miesiąca poprzedniego roku",
      },
    ];
  };

  const revenueSplit = useMemo<MiniDatum[]>(() => {
    if (!companyStats) return [];
    return [
      {
        name: "Usługi",
        color: SPLIT.services,
        amount: companyStats.servicesRevenue,
        percentLabel: `${companyStats.servicesRevenueShare}%`,
      },
      {
        name: "Produkty",
        color: SPLIT.products,
        amount: companyStats.productsRevenue,
        percentLabel: `${companyStats.productsRevenueShare}%`,
      },
    ];
  }, [companyStats]);

  const revenueTotal = useMemo(
    () => revenueSplit.reduce((sum, d) => sum + d.amount, 0),
    [revenueSplit]
  );

  const expensesBars = useMemo(() => {
    return expenseCategoryItems
      .map((category) => {
        const found = companyStats?.expensesByCategory?.find(
          (e) => e.category === category.id
        );
        return {
          id: category.id,
          name: category.name,
          color: category.color,
          amount: found?.amount ?? 0,
          sharePercent: found?.sharePercent ?? 0,
        };
      })
      .filter((bar) => bar.amount > 0)
      .sort((a, b) => b.amount - a.amount);
  }, [companyStats, expenseCategoryItems]);

  const expensesMax = useMemo(
    () => Math.max(1, ...expensesBars.map((e) => e.amount)),
    [expensesBars]
  );

  const waterfall = useMemo<(MiniDatum & { base: number; hint?: string })[]>(() => {
    if (!companySummary) return [];
    const offBookPercent =
      companySummary.currentRevenue > 0
        ? +(
            (companySummary.currentOffTheBookRevenue /
              companySummary.currentRevenue) *
            100
          ).toFixed(1)
        : 0;
    const incomeNegative = companySummary.currentIncome < 0;
    return [
      {
        name: "Przychód",
        base: 0,
        amount: companySummary.currentRevenue,
        color: FINANCE.revenue,
        percentLabel: "100%",
      },
      {
        name: "Wpływy",
        base: 0,
        amount: companySummary.currentOffTheBookRevenue,
        color: FINANCE.offBook,
        percentLabel: `${offBookPercent}%`,
        hint: OFF_BOOK_HINT,
      },
      {
        name: "Koszty",
        base: companySummary.currentExpenses > 0 ? companySummary.currentIncome : 0,
        amount: companySummary.currentExpenses,
        color: FINANCE.costs,
        percentLabel: `${companyStats?.costShareInRevenue ?? 0}%`,
      },
      {
        name: "Dochód",
        base: 0,
        amount: companySummary.currentIncome,
        color: incomeNegative ? FINANCE.incomeNegative : FINANCE.income,
        percentLabel: `${companyStats?.profitabilityPercent ?? 0}%`,
      },
    ];
  }, [companySummary, companyStats]);

  const profit = companyStats?.profitabilityPercent ?? 0;
  const profitClamped = Math.max(-100, Math.min(100, profit));
  const knobLeft = ((profitClamped + 100) / 200) * 100;

  const incomeTone = !companySummary
    ? "neutral"
    : companySummary.currentIncome > 0
      ? "positive"
      : companySummary.currentIncome < 0
        ? "negative"
        : "neutral";

  const legendValue = (amount: number, percentLabel?: string) =>
    unit === "%" ? percentLabel ?? "0%" : formatWholeCurrency(amount);

  const unitToggle = (
    <div className="cs-unit-toggle flex">
      <button
        className={unit === "zł" ? "on" : ""}
        onClick={() => setUnit("zł")}
      >
        PLN
      </button>
      <button className={unit === "%" ? "on" : ""} onClick={() => setUnit("%")}>
        %
      </button>
    </div>
  );

  const hasData = !!companyStats || !!companySummary;

  const dochodColor =
    incomeTone === "positive"
      ? FINANCE.income
      : incomeTone === "negative"
        ? FINANCE.incomeNegative
        : undefined;

  const renderComparisons = (deltas: KpiDelta[]) => {
    if (deltas.length === 0) return null;
    return (
      <div className="cs-kpi-cmp flex-column">
        {deltas.map((d, i) => {
          const cls = d.percent > 0 ? "up" : d.percent < 0 ? "down" : "flat";
          const arrow = d.percent > 0 ? "▲" : d.percent < 0 ? "▼" : "–";
          const value = Math.round(Math.abs(d.percent) * 10) / 10;
          return (
            <div
              key={i}
              className="cs-cmp flex align-items-center g-05"
              title={d.title}
            >
              <span className={`cs-cmp-pct tnum ${cls}`}>
                {arrow} {value.toLocaleString("pl-PL")}%
              </span>
              <span className="cs-cmp-lbl">{d.label}</span>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="cs-page width-93 mt-1 mb-1">
      <div className="cs-side cs-side-left">
        <PeriodPill
          month={statRequest.month}
          year={statRequest.year}
          onMonthChange={handleMonthChange}
          onYearChange={handleYearChange}
          label={periodMain}
          sub={`· ${periodSub}`}
          showYear={isDaily}
        >
          <DropdownSelect
            items={chartModeItems}
            value={
              chartModeItems.find((r) => r.id === statRequest.mode) ||
              chartModeItems[0]
            }
            onChange={(selected) => {
              const mode = Array.isArray(selected) ? selected[0]?.id : selected?.id;
              if (mode) handleChartModeChange(mode);
            }}
            searchable={false}
            allowNew={false}
            multiple={false}
            className=""
          />
        </PeriodPill>
      </div>

      <div className="cs-center">
      <section className="cs-kpis width-max">
        <div className="cs-kpi flex-column g-05">
          <div className="cs-kpi-head flex align-items-center g-05">
              <TrendingUp className="dl-icon-1 c-blue" />
              <span className="cs-kpi-label">Przychód</span>
            </div>
            <span className="cs-kpi-value tnum">
              {companySummary ? formatWholeCurrency(companySummary.currentRevenue) : "---"}
            </span>
          {renderComparisons(
            buildDeltas(
              companySummary?.revenueChangeVsPrevPeriod ?? 0,
              companySummary?.revenueChangeVsLastYear ?? 0
            )
          )}
        </div>

        <div className="cs-kpi flex-column g-05">
          <div className="cs-kpi-head flex align-items-center g-05">
              <TrendingDown className="dl-icon-1 c-red" />
              <span className="cs-kpi-label">Koszty</span>
            </div>
            <span className="cs-kpi-value tnum">
              {companySummary ? formatWholeCurrency(companySummary.currentExpenses) : "---"}
            </span>
          {renderComparisons(
            buildDeltas(
              companySummary?.expensesChangeVsPrevPeriod ?? 0,
              companySummary?.expensesChangeVsLastYear ?? 0
            )
          )}
        </div>

        <div className="cs-kpi flex-column g-05">
          <div className="cs-kpi-head flex align-items-center g-05">
              <Wallet className="dl-icon-1 c-green" />
              <span className="cs-kpi-label">Dochód</span>
            </div>
            <span className="cs-kpi-value tnum" style={{ color: dochodColor }}>
              {companySummary ? formatWholeCurrency(companySummary.currentIncome) : "---"}
            </span>
          {renderComparisons(
            buildDeltas(
              companySummary?.incomeChangeVsPrevPeriod ?? 0,
              companySummary?.incomeChangeVsLastYear ?? 0
            )
          )}
        </div>

        <div className="cs-kpi cs-kpi-meter flex-column g-05">
          <div className="cs-kpi-head flex align-items-center g-05">
            <Gauge className="dl-icon-1 pc-y" />
            <span className="cs-kpi-label">Rentowność</span>
          </div>
          <span
            className="cs-kpi-value tnum"
            style={{ color: profit >= 0 ? FINANCE.income : FINANCE.incomeNegative }}
          >
            {companyStats ? `${profit}%` : "---"}
          </span>
          <div className="cs-meter-wrap f-1 flex-column justify-center g-05">
            <div className="cs-meter-track">
              <div className="cs-meter-knob" style={{ left: `${knobLeft}%` }} />
            </div>
            <div className="cs-meter-scale flex space-between">
              <span>-100%</span>
              <span>0</span>
              <span>+100%</span>
            </div>
          </div>
        </div>
      </section>

      <ChartCard
        className="cs-structure width-max mt-1"
        title="Struktura przychodów i kosztów"
        actions={unitToggle}
      >
        {!hasData ? (
          <div className="cs-empty flex align-items-center justify-center">
            Brak danych
          </div>
        ) : (
          <div className="cs-triple">
            <div className="cs-mini flex-column">
              <span className="cs-mini-title">Przychód wg źródła</span>
              <div className="cs-donut-body flex align-items-center justify-center">
                <div className="cs-donut relative">
                  <PieChart width={170} height={170}>
                    <Pie
                      data={revenueSplit}
                      dataKey="amount"
                      cx="50%"
                      cy="50%"
                      innerRadius={61}
                      outerRadius={81}
                      paddingAngle={3}
                      startAngle={90}
                      endAngle={-270}
                      stroke={CHART_SURFACE}
                      strokeWidth={2}
                    >
                      {revenueSplit.map((d, i) => (
                        <Cell key={i} fill={d.color} />
                      ))}
                    </Pie>
                    <Tooltip content={<MiniTooltip />} />
                  </PieChart>
                  <div className="cs-donut-center flex-column align-items-center">
                    <span className="cs-donut-total tnum">
                      {Math.round(revenueTotal).toLocaleString("pl-PL")}
                    </span>
                    <span className="cs-donut-cap">PRZYCHÓD zł</span>
                  </div>
                </div>
                <div className="cs-legend g-05 flex-column">
                  {revenueSplit.map((d) => (
                    <div className="cs-legend-item flex align-items-center g-05" key={d.name}>
                      <span className="cs-legend-sw" style={{ backgroundColor: d.color }} />
                      <span className="cs-legend-nm">{d.name}</span>
                      <span className="cs-legend-vl tnum">
                        {legendValue(d.amount, d.percentLabel)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="cs-mini cs-col-div flex-column">
              <span className="cs-mini-title">Koszty wg kategorii</span>
              <div className="cs-hbars flex-column width-max">
                {expensesBars.length === 0 && (
                  <div className="cs-empty flex align-items-center justify-center">Brak kosztów</div>
                )}
                {expensesBars.map((e) => (
                  <div className="cs-hbar-row align-items-center" key={e.id}>
                    <span className="cs-hbar-name" title={e.name}>
                      {e.name}
                    </span>
                    <span className="cs-hbar-track">
                      <span
                        className="cs-hbar-fill"
                        style={{
                          width: `${(e.amount / expensesMax) * 100}%`,
                          backgroundColor: `rgb(${e.color})`,
                        }}
                      />
                    </span>
                    <span className="cs-hbar-val tnum">
                      {unit === "%" ? `${e.sharePercent}%` : formatWholeCurrency(e.amount)}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="cs-mini cs-col-div flex-column">
              <span className="cs-mini-title">Od przychodu do dochodu</span>
              <div className="cs-waterfall width-max">
                <ResponsiveContainer width="100%" height={150}>
                  <BarChart
                    data={waterfall}
                    layout="vertical"
                    margin={{ top: 6, right: 8, left: 4, bottom: 6 }}
                    barSize={17}
                  >
                    <YAxis dataKey="name" type="category" hide />
                    <XAxis type="number" hide domain={[0, "dataMax"]} />
                    <Tooltip cursor={{ fill: "rgba(255,255,255,0.04)" }} content={<MiniTooltip />} />
                    <ReferenceLine x={0} stroke={CHART_INK.zeroLine} strokeWidth={1} />
                    <Bar dataKey="base" stackId="wf" fill="transparent" />
                    <Bar dataKey="amount" stackId="wf" radius={[0, 3, 3, 0]}>
                      {waterfall.map((d, i) => (
                        <Cell key={i} fill={d.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="cs-legend g-05 flex-column">
                {waterfall.map((d) => (
                  <div
                    className="cs-legend-item flex align-items-center g-05"
                    key={d.name}
                    title={d.hint}
                  >
                    <span className="cs-legend-sw" style={{ backgroundColor: d.color }} />
                    <span className="cs-legend-nm flex align-items-center g-025">
                      {d.name}
                      {d.hint && <Receipt className="dl-icon-1 cs-legend-icon" />}
                    </span>
                    <span className="cs-legend-vl tnum">
                      {legendValue(d.amount, d.percentLabel)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </ChartCard>

      <section className="cs-timeline width-max mt-1 flex justify-center">
        <CompanyRevenueChart statRequest={statRequest} />
      </section>
      </div>

      <div className="cs-side cs-side-right" />
    </div>
  );
}

export default CompanyStatistics;
