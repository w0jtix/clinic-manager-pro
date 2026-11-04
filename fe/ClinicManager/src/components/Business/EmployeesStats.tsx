import { useState, useCallback, useEffect, useMemo } from "react";
import { useAlert } from "../Alert/AlertProvider";
import {
  EmployeeRevenueFilter,
  ChartMode,
  EmployeeStats,
  chartModeItems,
} from "../../models/statistics";
import StatisticsService from "../../services/StatisticsService";
import EmployeeService from "../../services/EmployeeService";
import { Employee, EmploymentType } from "../../models/employee";
import { AlertType } from "../../models/alert";
import EmployeeRevenueChart from "./EmployeeRevenueChart";
import EmployeeModal from "../Popups/EmployeeModal";
import DropdownSelect from "../DropdownSelect";
import ActionButton from "../ActionButton";
import { MONTHS } from "../../utils/dateUtils";
import { employeeColor } from "../../utils/statisticsUtils";
import { AVAILABLE_AVATARS } from "../../constants/avatars";
import {
  CirclePlus,
  Pencil,
  ListChecks,
  UserPlus,
  Package,
  TrendingUp,
  Ticket,
  Clock,
  Crown,
  Zap,
  HandCoins,
} from "lucide-react";
import SalaryHistoryModal from "../Popups/SalaryHistoryModal";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import PeriodPill from "../PeriodPill";
import { formatWholeAmount, formatWholeCurrency } from "../../utils/formatUtils";

const employmentTypeLabels: Record<EmploymentType, string> = {
  [EmploymentType.QUARTER]: "1/4 etatu",
  [EmploymentType.HALF]: "1/2 etatu",
  [EmploymentType.THREE_QUARTERS]: "3/4 etatu",
  [EmploymentType.FULL]: "Pełny etat",
};

export function EmployeesStats() {
  const { showAlert } = useAlert();
  const { isCompact, isNarrow } = useBreakpoint();

  const [statRequest, setStatRequest] = useState<EmployeeRevenueFilter>({
    mode: ChartMode.DAILY,
    month: new Date().getMonth() + 1,
    year: new Date().getFullYear(),
  });
  const [employeeStats, setEmployeeStats] = useState<EmployeeStats[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [modalOpen, setModalOpen] = useState(false);
  const [editEmployeeId, setEditEmployeeId] = useState<number | null>(null);
  const [salaryHistoryEmployee, setSalaryHistoryEmployee] = useState<{
    id: number;
    name: string;
  } | null>(null);

  const fetchEmployeeStats = useCallback(() => {
    StatisticsService.getEmployeeStats(statRequest)
      .then((data) => {
        setEmployeeStats(data);
        setSelectedIds(new Set(data.map((e) => e.id)));
      })
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error fetching Employee Stats: ", error);
      });
  }, [statRequest, showAlert]);

  const fetchEmployees = useCallback(() => {
    EmployeeService.getAllEmployees()
      .then((data) => setEmployees(data))
      .catch((error) => {
        console.error("Error fetching Employees: ", error);
      });
  }, []);

  useEffect(() => {
    fetchEmployeeStats();
  }, [fetchEmployeeStats]);

  useEffect(() => {
    fetchEmployees();
  }, [fetchEmployees]);

  const employeeById = useMemo(() => {
    const map = new Map<number, Employee>();
    employees.forEach((e) => map.set(e.id, e));
    return map;
  }, [employees]);
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const isDaily = statRequest.mode === ChartMode.DAILY;

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

  const periodModeItem =
    chartModeItems.find((m) => m.id === statRequest.mode) ?? chartModeItems[0];
  const periodMain = isDaily
    ? `${statRequest.month ? MONTHS[statRequest.month - 1].name : ""} ${statRequest.year ?? ""}`.trim()
    : `${statRequest.year ?? ""}`;
  const periodSub = periodModeItem.name.toLowerCase();

  const toggleSelected = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const openAdd = () => {
    setEditEmployeeId(null);
    setModalOpen(true);
  };
  const openEdit = (id: number) => {
    setEditEmployeeId(id);
    setModalOpen(true);
  };
  const handleSaved = () => {
    fetchEmployees();
    fetchEmployeeStats();
  };

  const selectedForChart = useMemo(
    () => (selectedIds.size > 0 ? Array.from(selectedIds) : undefined),
    [selectedIds]
  );

  const sortedEmployeeStats = useMemo(
    () => [...employeeStats].sort((a, b) => a.id - b.id),
    [employeeStats]
  );

  const goalRow = (
    label: string,
    value: number,
    goal: number,
    color: string
  ) => {
    const pct = goal > 0 ? Math.min(100, (value / goal) * 100) : 0;
    const achieved = goal > 0 && value >= goal;
    return (
      <div className="emp-goal">
        <div className="emp-goal-top flex align-items-baseline space-between">
          <span className="emp-goal-label">{label}</span>
          <span className={`emp-goal-val tnum ${achieved ? "achieved" : ""}`}>
            {formatWholeAmount(value)}{" "}
            <span className="emp-goal-goal">/ {formatWholeCurrency(goal)}</span>
          </span>
        </div>
        <div className="emp-goal-track">
          <span
            className="emp-goal-fill"
            style={{ width: `${pct}%`, backgroundColor: achieved ? "#17c47f" : color }}
          />
        </div>
      </div>
    );
  };

  const stat = (
    icon: React.ReactNode,
    label: string,
    value: React.ReactNode,
    valueClass = ""
  ) => (
    <div className="emp-st flex align-items-center space-between g-05">
      <span className="emp-st-l flex align-items-center g-05">
        {icon}
        {label}
      </span>
      <span className={`emp-st-v tnum ${valueClass}`}>{value}</span>
    </div>
  );

  const periodPill = (
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
  );

  return (
    <div className="emp-page mt-1 mb-1 align-items-start width-93">
      <div className="emp-side flex emp-side-left">{periodPill}</div>

      <div className="emp-center width-max flex-column align-items-center justify-self-center g-1">
        <section className="cs-timeline width-max flex justify-center">
        <EmployeeRevenueChart
          statRequest={statRequest}
          selectedEmployeeIds={selectedForChart}
        />
      </section>

      <section className="emp-grid width-max g-1">
        {sortedEmployeeStats.map((emp) => {
            const color = employeeColor(emp.id);
            const info = employeeById.get(emp.id);
            const fullName = info ? `${info.name} ${info.lastName}` : emp.name;
            const etat = info ? employmentTypeLabels[info.employmentType] : "";
            const on = selectedIds.has(emp.id);
            return (
              <div
                key={emp.id}
                className={`emp flex-column g-1 ${on ? "on" : "off"}`}
                style={{ "--emp": color } as React.CSSProperties}
                onClick={() => toggleSelected(emp.id)}
              >
                <div className="emp-head flex align-items-center g-075">
                  <div className="emp-avatar">
                    <img
                      src={
                        AVAILABLE_AVATARS[emp.avatar ?? "avatar1.png"] ??
                        AVAILABLE_AVATARS["avatar1.png"]
                      }
                      alt=""
                      className="emp-avatar-img"
                    />
                  </div>
                  <div className="emp-id f-1 min-width-0">
                    <div className="emp-name">{fullName}</div>
                    {etat && <div className="emp-etat">{etat}</div>}
                  </div>
                  <ActionButton
                    iconTitle="Historia Wynagrodzeń"
                    disableText={true}
                    onClick={(e) => {
                      e.stopPropagation();
                      setSalaryHistoryEmployee({ id: emp.id, name: fullName });
                    }}
                    className="emp-salary"
                  >
                    <HandCoins className="dl-icon" />
                  </ActionButton>
                  <button
                    className="emp-edit flex align-items-center justify-center pointer"
                    title="Edytuj pracownika"
                    onClick={(e) => {
                      e.stopPropagation();
                      openEdit(emp.id);
                    }}
                  >
                    <Pencil className="dl-icon" />
                  </button>
                </div>

                <div className="emp-goals flex-column g-075">
                  {goalRow("Utarg (usługi)", emp.servicesRevenue, emp.servicesRevenueGoal, color)}
                  {goalRow("Sprzedaż (produkty)", emp.productsRevenue, emp.productsRevenueGoal, color)}
                  {goalRow("Przychód", emp.totalRevenue, emp.totalRevenueGoal, color)}
                </div>

                <div className="emp-div" />

                <div className="emp-stats">
                  {stat(<ListChecks className="dl-icon" />, "Zrealizowane usługi", emp.servicesDone)}
                  {stat(<UserPlus className="dl-icon" />, "Nowi klienci", `+${emp.newClients}`, "pos")}
                  {stat(<Package className="dl-icon" />, "Sprzedane produkty", emp.productsSold)}
                  {stat(<TrendingUp className="dl-icon" />, "Konwersja klienta", `${emp.clientsSecondVisitConversion}%`)}
                  {stat(<Ticket className="dl-icon" />, "Sprzedane vouchery", emp.vouchersSold)}
                  {stat(<Clock className="dl-icon" />, "Czas pracy", `${emp.hoursWithClients} / ${emp.availableHours} h`)}
                  {stat(<Crown className="dl-icon" />, "Top usługa", <span className="emp-top" title={emp.topSellingServiceName}>{emp.topSellingServiceName || "-"}</span>)}
                  {stat(<Zap className="dl-icon" />, "Nowi klienci (Boost)", `+${emp.newBoostClients}`, "pos")}
                  {stat(<Crown className="dl-icon" />, "Top produkt", <span className="emp-top" title={emp.topSellingProductName}>{emp.topSellingProductName || "-"}</span>)}
                  {stat(<Zap className="dl-icon" />, "Konwersja (Boost)", `${emp.boostClientsSecondVisitConversion}%`)}
                </div>
              </div>
            );
          })}
        </section>
      </div>

      <div className="emp-side flex emp-side-right">
        <ActionButton
          text={isCompact ? "Pracownik" : "Dodaj pracownika"}
          iconTitle="Dodaj pracownika"
          onClick={openAdd}
          disableText={isNarrow}
          className={isNarrow ? "emp-add" : ""}
        >
          <CirclePlus className="dl-icon-1 c-green" aria-label="Dodaj pracownika" />
        </ActionButton>
      </div>

      {salaryHistoryEmployee && (
        <SalaryHistoryModal
          employeeId={salaryHistoryEmployee.id}
          employeeName={salaryHistoryEmployee.name}
          onClose={() => setSalaryHistoryEmployee(null)}
        />
      )}

      {modalOpen && (
        <EmployeeModal
          employeeId={editEmployeeId}
          onClose={() => setModalOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

export default EmployeesStats;
