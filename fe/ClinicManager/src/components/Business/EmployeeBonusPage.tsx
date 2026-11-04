import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import DropdownSelect from "../DropdownSelect";
import {
  Employee,
  EmployeeBonus,
  EmployeeBonusFilterDTO,
} from "../../models/employee";
import { useState, useCallback, useEffect, useMemo } from "react";
import EmployeeService from "../../services/EmployeeService";
import { MONTHS, MONTH_LABELS_SHORT } from "../../utils/dateUtils";
import StatisticsService from "../../services/StatisticsService";
import BonusHistoryVisitList from "./BonusHistoryVisitList";
import BonusCalendar from "./BonusCalendar";
import ProductBonusList from "./ProductBonusList";
import ProductBonusChart from "./ProductBonusChart";
import StatSettingsService from "../../services/StatSettingsService";
import { StatSettings } from "../../models/business_settings";
import {
  TrendingUp,
  TrendingDown,
  Package,
  Coins,
  TriangleAlert,
  History,
  BookCheck,
} from "lucide-react";
import ActionButton from "../ActionButton";
import BonusChangeHistoryModal from "../Popups/BonusChangeHistoryModal";
import ExpenseModal from "../Popups/ExpenseModal";
import ExpenseService from "../../services/ExpenseService";
import { CompanyExpense, NewCompanyExpense } from "../../models/expense";
import { buildSalaryExpense, getPayoutPeriod } from "../../utils/expenseUtils";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import PeriodPill from "../PeriodPill";
import { formatAmount } from "../../utils/formatUtils";

const EmployeeBonusPage = () => {
  const { isCompact, isNarrow, isTablet } = useBreakpoint();
  const [employeeBonus, setEmployeeBonus] = useState<EmployeeBonus | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [filter, setFilter] = useState<EmployeeBonusFilterDTO>({
    employeeId: null,
    month: new Date().getMonth() + 1,
    year: new Date().getFullYear(),
  });
  const { showAlert } = useAlert();
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;

  const [statSettings, setStatSettings] = useState<StatSettings | null>(null);
  const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);
  const [salaryHistory, setSalaryHistory] = useState<CompanyExpense[]>([]);
  const [salaryDraft, setSalaryDraft] = useState<NewCompanyExpense | null>(null);
  const [view, setView] = useState<"list" | "graphic">("graphic");

  const fetchEmployees = async () => {
    EmployeeService.getAllEmployees()
      .then((data) => setEmployees(data))
      .catch((error) => {
        console.error("Error fetching Employees: ", error);
        showAlert("Błąd", AlertType.ERROR);
        setEmployees([]);
      });
  };
  const fetchEmployeeBonus = useCallback(async () => {
    if (!filter.employeeId) return;
    StatisticsService.getEmployeeBonus(filter)
      .then((data) => setEmployeeBonus(data))
      .catch((error) => {
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Employee Bonus: ", error);
      });
  }, [filter]);

  const fetchSalaryHistory = useCallback(async () => {
    if (!filter.employeeId) {
      setSalaryHistory([]);
      return;
    }
    ExpenseService.getSalaryHistory(filter.employeeId)
      .then((data) => setSalaryHistory(data ?? []))
      .catch((error) => {
        setSalaryHistory([]);
        console.error("Error fetching Salary history: ", error);
      });
  }, [filter.employeeId]);

  const handleEmployeeChange = useCallback(
    (employee: Employee | Employee[] | null) => {
      const selected = Array.isArray(employee) ? employee[0] : employee;
      setFilter((prev) => ({
        ...prev,
        employeeId: selected?.id ?? prev.employeeId,
      }));
    },
    []
  );
  const handleMonthChange = useCallback(
    (month: number | null) => {
      setFilter((prev) => ({ ...prev, month: month ?? prev.month }));
    },
    []
  );
  const handleYearChange = useCallback(
    (year: number | null) => {
      setFilter((prev) => {
        let month = prev.month;
        if (year === currentYear && prev.month && prev.month > currentMonth) {
          month = currentMonth;
        }
        return { ...prev, year: year ?? prev.year, month };
      });
    },
    [currentYear, currentMonth]
  );

  const quarterData = useMemo(() => {
    const payoutMonths = statSettings?.saleBonusPayoutMonths;
    if (!payoutMonths || payoutMonths.length === 0 || !filter.month) return null;
    const sorted = [...payoutMonths].sort((a, b) => a - b);
    const nextPayout = sorted.find((m) => m >= filter.month!) ?? sorted[0];
    const monthsUntil = (nextPayout - filter.month! + 12) % 12;
    const filledCount = 3 - monthsUntil;
    const qMonths = [nextPayout - 2, nextPayout - 1, nextPayout].map((m) =>
      m <= 0 ? m + 12 : m
    );
    return { qMonths, filledCount, payoutMonth: nextPayout };
  }, [statSettings, filter.month]);

  useEffect(() => {
    fetchEmployees();
    StatSettingsService.getSettings()
      .then(setStatSettings)
      .catch(() => console.error("Failed to fetch StatSettings"));
  }, []);
  useEffect(() => {
    setFilter((prev) => ({
      ...prev,
      employeeId:
        employees.length > 0
          ? employees.reduce((min, e) => (e.id < min ? e.id : min), employees[0].id)
          : null,
    }));
  }, [employees]);
  useEffect(() => {
    fetchEmployeeBonus();
  }, [filter]);
  useEffect(() => {
    fetchSalaryHistory();
  }, [fetchSalaryHistory]);


  const selectedEmployee = useMemo(
    () => employees.find((e) => e.id === filter.employeeId) ?? null,
    [employees, filter.employeeId]
  );
  const periodMain =
    filter.month && filter.year
      ? `${MONTHS[filter.month - 1].name} ${filter.year}`
      : "";
  const disburseDate = (month: number, year: number, numeric = false): string => {
    const m = month === 12 ? 1 : month + 1;
    const y = month === 12 ? year + 1 : year;
    return numeric
      ? `10.${String(m).padStart(2, "0")}.${y}`
      : `10 ${MONTHS[m - 1].name} ${y}`;
  };
  const disburseLabel =
    filter.month && filter.year
      ? disburseDate(filter.month, filter.year)
      : "";
  const payoutDisburseLabel =
    quarterData && filter.month && filter.year
      ? disburseDate(
          quarterData.payoutMonth,
          quarterData.payoutMonth >= filter.month ? filter.year : filter.year + 1,
          isNarrow
        )
      : "";

  const b = employeeBonus;
  const utargBonus = b?.bonusAmount ?? 0;

  const quarterSum = useMemo(() => {
    if (!quarterData || !b) return 0;
    let sum = 0;
    for (let i = 0; i < 3; i++) {
      if (i >= quarterData.filledCount) continue;
      const distBack = quarterData.filledCount - 1 - i;
      const val =
        distBack === 0
          ? b.productBonusAmount
          : distBack === 1
            ? (b.prevMonthSaleBonus ?? 0)
            : (b.twoMonthPrevSaleBonus ?? 0);
      sum += val ?? 0;
    }
    return sum;
  }, [quarterData, b]);

  const isPayout = quarterData?.filledCount === 3;
  const totalBonus = utargBonus + (isPayout ? quarterSum : 0);

  const canBookSalary = useMemo(() => {
    if (!b || !selectedEmployee || !filter.month || !filter.year) return false;
    const isPeriodClosed =
      filter.year < currentYear ||
      (filter.year === currentYear && filter.month < currentMonth);
    if (!isPeriodClosed) return false;
    const payout = getPayoutPeriod(filter.month, filter.year);
    return !salaryHistory.some((expense) => {
      const date = new Date(expense.expenseDate);
      return (
        date.getFullYear() === payout.year && date.getMonth() + 1 === payout.month
      );
    });
  }, [
    b,
    selectedEmployee,
    filter.month,
    filter.year,
    currentYear,
    currentMonth,
    salaryHistory,
  ]);

  const handleBookSalary = useCallback(async () => {
    if (!selectedEmployee || !filter.month || !filter.year) return;
    try {
      const draft = await buildSalaryExpense(
        selectedEmployee.id,
        filter.month,
        filter.year,
        utargBonus,
        isPayout ? quarterSum : null
      );
      setSalaryDraft(draft);
    } catch (error) {
      showAlert("Błąd przygotowania wypłaty!", AlertType.ERROR);
      console.error("Error building salary expense: ", error);
    }
  }, [
    selectedEmployee,
    filter.month,
    filter.year,
    utargBonus,
    isPayout,
    quarterSum,
    showAlert,
  ]);

  const hasFallbackPrice =
    b?.products.some(
      (p) => p.noPurchaseHistory && p.fallbackPurchasePriceUsed
    ) ?? false;
  const hasMissingPrice =
    b?.products.some(
      (p) => p.noPurchaseHistory && !p.fallbackPurchasePriceUsed
    ) ?? false;
  const hasLossSale =
    b?.products.some((p) => p.items.some((i) => i.margin < 0)) ?? false;

  return (
    <div className="pv-app mt-1 mb-1 flex-column width-93">
      <header className="pv-top flex space-between g-1">
        <div className="pv-filters flex align-items-center">
          <PeriodPill
            month={filter.month}
            year={filter.year}
            onMonthChange={handleMonthChange}
            onYearChange={handleYearChange}
            label={periodMain}
            sub={`- ${
              selectedEmployee
                ? `${selectedEmployee.name} ${selectedEmployee.lastName}`
                : "Pracownik"
            }`}
            className="pv-period"
          >
            <DropdownSelect<Employee>
              items={employees}
              value={selectedEmployee}
              onChange={handleEmployeeChange}
              searchable={false}
              allowNew={false}
              multiple={false}
              placeholder="Nie wybrano"
            />
          </PeriodPill>
        </div>
        <div className="pv-view-toggle flex align-items-center g-075">
          <div className="cs-seg flex align-items-center">
            <button
              type="button"
              className={view === "list" ? "on" : ""}
              onClick={() => setView("list")}
            >
              Lista
            </button>
            <button
              type="button"
              className={view === "graphic" ? "on" : ""}
              onClick={() => setView("graphic")}
            >
              Grafika
            </button>
          </div>
          <ActionButton
            text="Historia awansów pracownika"
            iconTitle="Historia awansów pracownika"
            onClick={() => setIsHistoryModalOpen(true)}
            disabled={!selectedEmployee}
            disableText={isCompact}
            className={`bch-open ${isCompact ? "bch-icon" : ""}`}
          >
            <History className="dl-icon-1 pc-y" />
          </ActionButton>
        </div>
      </header>

      <section className="pv-kpis g-1">
        <div className="pv-kpi flex-column g-05">
          <div className="pv-kpi-head g-05 flex align-items-center">
            <TrendingUp className="dl-icon-1" style={{ color: "#17c47f" }} />
            <span className="pv-kpi-label">Premia utarg</span>
          </div>
          <span className="pv-kpi-value green tnum">+ {formatAmount(utargBonus)} zł</span>
          <div className="pv-kpi-foot flex align-items-center">
            <span className="pv-badge align-items-center pay">
              {b?.bonusPercent ?? 0}%{!isNarrow && " od nadwyżki ponad próg"}
            </span>
          </div>
        </div>

        <div className={`pv-kpi flex-column g-05 ${!isPayout ? "pv-dim" : ""}`}>
          <div className="pv-kpi-head g-05 flex align-items-center">
            <Package className="dl-icon-1" style={{ color: "#17c47f" }} />
            <span className="pv-kpi-label">Premia produktowa</span>
          </div>
          <span className="pv-kpi-value green tnum">+ {formatAmount(quarterSum)} zł</span>
          <div className="pv-kpi-foot flex align-items-center">
            {isPayout ? (
              <span className="pv-badge align-items-center pay">
                {isNarrow ? "Wypłata" : "Wypłata kwartalna"}
              </span>
            ) : (
              <span className="pv-badge align-items-center hold">
                {isNarrow ? "W toku" : "Kwartał w toku"}
              </span>
            )}
          </div>
        </div>

        <div className="pv-kpi flex-column accent g-05">
          <div className="pv-kpi-head g-05 flex align-items-center">
            <Coins
              className="dl-icon-1"
              style={{ color: "var(--yellow-clinicmanager)" }}
            />
            <span className="pv-kpi-label">
              {isPayout ? "Premia łączna" : "Premia"}
            </span>
            {canBookSalary && (
              <ActionButton
                text="Księguj wypłatę"
                iconTitle="Utwórz koszt wypłaty za ten miesiąc"
                onClick={handleBookSalary}
                disableText={isTablet}
                className={`clx-btn pv-book-salary ${isTablet ? "pv-book-icon" : ""}`}
              >
                <BookCheck className="dl-icon-1 pc-y" />
              </ActionButton>
            )}
          </div>
          <span className="pv-kpi-value yellow tnum">+ {formatAmount(totalBonus)} zł</span>
          <div className="pv-kpi-foot flex align-items-center">
            <span className="pv-badge align-items-center accent">Do wypłaty {disburseLabel}</span>
          </div>
        </div>
      </section>

      <section className="pv-panels g-1 align-items-start">
        <div className="pv-card">
          <div className="pv-card-head flex align-items-center space-between">
            <span className="pv-card-title">
              Premia utarg
            </span>
            <span className="pv-card-pct">
              próg {formatAmount(b?.bonusThreshold)} zł ·{" "}
              <span className="pc-y">{b?.bonusPercent ?? 0}%</span>
            </span>
          </div>
          <div className="pv-eq">
            <span className="pv-eq-term">
              <span className="pv-eq-l align-self-center">Przychód</span>
              <span className="pv-eq-v text-align-center green tnum">
                {formatAmount(b?.monthlyServicesRevenue)}
              </span>
            </span>
            {b && b.boostCost > 0 && (
              <>
                <span className="pv-eq-op">−</span>
                <span className="pv-eq-term">
                  <span className="pv-eq-l align-self-center">Boost</span>
                  <span className="pv-eq-v text-align-center red tnum">{formatAmount(b.boostCost)}</span>
                </span>
              </>
            )}
            <span className="pv-eq-op">−</span>
            <span className="pv-eq-term">
              <span className="pv-eq-l align-self-center">Próg</span>
              <span className="pv-eq-v text-align-center red tnum">{formatAmount(b?.bonusThreshold)}</span>
            </span>
            <span className="pv-eq-op">×</span>
            <span className="pv-eq-term">
              <span className="pv-eq-l align-self-center">Stawka</span>
              <span className="pv-eq-v text-align-center white tnum">{b?.bonusPercent ?? 0}%</span>
            </span>
            <span className="pv-eq-res">
              <span className="pv-eq-l align-self-center">Premia utarg</span>
              <span className="pv-eq-v text-align-center yellow tnum">+ {formatAmount(utargBonus)} zł</span>
            </span>
          </div>
          {b ? (
            view === "graphic" ? (
              <BonusCalendar
                visits={b.visits}
                month={filter.month!}
                year={filter.year!}
              />
            ) : (
              <BonusHistoryVisitList
                visits={b.visits}
                month={filter.month!}
                year={filter.year!}
              />
            )
          ) : (
            <div className="pv-empty text-align-center">Wybierz pracownika</div>
          )}
        </div>

        <div className="pv-card">
          <div className="pv-card-head flex align-items-center space-between">
            <span className="pv-card-title y">
              Premia produktowa
            </span>
            <span className={`pv-card-pct ${!isPayout ? "pv-dim" : ""}`}>
              wypłata kwartalna ·{" "}
              <span className="pc-y">{b?.saleBonusPercent ?? 0}%</span>
            </span>
          </div>
          <div className={`pv-eq ${!isPayout ? "pv-eq-muted" : ""}`}>
            <span className="pv-eq-term">
              <span className="pv-eq-l align-self-center">Sprzedaż</span>
              <span className="pv-eq-v text-align-center white tnum">
                {formatAmount(b?.monthlyProductsRevenue)}
              </span>
            </span>
            <span className="pv-eq-div" />
            <span className="pv-eq-term">
              <span className="pv-eq-l align-self-center">Marża</span>
              <span className="pv-eq-v text-align-center green tnum">
                {formatAmount(b?.monthlyProductsMargin)}
              </span>
            </span>
            <span className="pv-eq-op">×</span>
            <span className="pv-eq-term">
              <span className="pv-eq-l align-self-center">Stawka</span>
              <span className="pv-eq-v text-align-center white tnum">
                {b?.saleBonusPercent ?? 0}%
              </span>
            </span>
            {(b?.monthlyProductsLoss ?? 0) > 0 && (
              <>
                <span className="pv-eq-op">−</span>
                <span className="pv-eq-term">
                  <span className="pv-eq-l align-self-center">Strata</span>
                  <span className="pv-eq-v text-align-center red tnum">
                    {formatAmount(b?.monthlyProductsLoss)}
                  </span>
                </span>
              </>
            )}
            <span className="pv-eq-res">
              <span className="pv-eq-l align-self-center">Premia (ten miesiąc)</span>
              <span className="pv-eq-v text-align-center green tnum">
                + {formatAmount(b?.productBonusAmount)} zł
              </span>
            </span>
          </div>
          {quarterData && (
            <div className={`pv-quarter ${!isPayout ? "pv-dim" : ""}`}>
              <div className="pv-quarter-row">
                {quarterData.qMonths.map((m, i) => {
                  const distBack = quarterData.filledCount - 1 - i;
                  const filled = i < quarterData.filledCount;
                  const value = filled
                    ? distBack === 0
                      ? b?.productBonusAmount
                      : distBack === 1
                        ? b?.prevMonthSaleBonus
                        : b?.twoMonthPrevSaleBonus
                    : null;
                  const payoutSeg = i === 2 && quarterData.filledCount === 3;
                  return (
                    <div className="pv-qseg flex-column align-items-center" key={i}>
                      <span className="pv-qseg-m">{MONTH_LABELS_SHORT[m - 1]}</span>
                      <span
                        className={`pv-qseg-box width-max text-align-center tnum ${payoutSeg ? "payout" : filled ? "filled" : ""}`}
                      >
                        {value != null ? formatAmount(value) : "-"}
                      </span>
                    </div>
                  );
                })}
              </div>
              <div className="pv-quarter-top flex space-between mt-05">
                <span className="pv-quarter-lbl">
                  Suma Kwartalna · wypłata {payoutDisburseLabel}
                </span>
                <span className="pv-quarter-sum pc-y tnum">Σ {formatAmount(quarterSum)} zł</span>
              </div>
            </div>
          )}
          {b ? (
            view === "graphic" ? (
              <ProductBonusChart
                items={b.products}
                onProductUpdated={fetchEmployeeBonus}
              />
            ) : (
              <ProductBonusList
                items={b.products}
                onProductUpdated={fetchEmployeeBonus}
              />
            )
          ) : (
            <div className="pv-empty text-align-center">Wybierz pracownika</div>
          )}
          {(hasFallbackPrice || hasMissingPrice || hasLossSale) && (
            <div className="pv-note flex align-items-center space-around">
              {hasFallbackPrice && (
                <div className="flex align-items-center g-05">
                  <TriangleAlert
                    className="dl-icon"
                    style={{ color: "var(--yellow-clinicmanager)" }}
                  />
                  Awaryjna cena zakupu · marża szacowana
                </div>
              )}
              {hasMissingPrice && (
                <div className="flex align-items-center g-05">
                  <TriangleAlert
                    className="dl-icon"
                    style={{ color: "var(--color-finance-red)" }}
                  />
                  Brak historii zakupu · premia niedostępna
                </div>
              )}
              {hasLossSale && (
                <div className="flex align-items-center g-05">
                  <TrendingDown
                    className="dl-icon"
                    style={{ color: "var(--color-finance-red)" }}
                  />
                  Sprzedaż ze stratą
                </div>
              )}
            </div>
          )}
        </div>
      </section>

      {salaryDraft && (
        <ExpenseModal
          initialExpense={salaryDraft}
          onClose={() => setSalaryDraft(null)}
          onReset={fetchSalaryHistory}
        />
      )}
      {isHistoryModalOpen && selectedEmployee && (
        <BonusChangeHistoryModal
          employeeId={selectedEmployee.id}
          employeeName={`${selectedEmployee.name} ${selectedEmployee.lastName}`}
          onClose={() => setIsHistoryModalOpen(false)}
        />
      )}
    </div>
  );
};

export default EmployeeBonusPage;
