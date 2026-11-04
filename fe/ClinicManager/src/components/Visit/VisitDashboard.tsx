import { useNavigationType } from "react-router-dom";
import { useAlert } from "../Alert/AlertProvider";
import {
  Rocket,
  Clock,
  UserX,
  Crown,
  Percent,
  ShoppingBag,
  RotateCw,
  CalendarPlus,
} from "lucide-react";
import { AlertType } from "../../models/alert";
import ActionButton from "../ActionButton";
import { useState, useEffect, useCallback, useRef } from "react";
import NavigationBar from "../NavigationBar";
import { VisitFilterDTO, Visit } from "../../models/visit";
import VisitService from "../../services/VisitService";
import ClientService from "../../services/ClientService";
import { Client } from "../../models/client";
import VisitBoard from "./VisitBoard";
import DropdownSelect from "../DropdownSelect";
import { Employee } from "../../models/employee";
import EmployeeService from "../../services/EmployeeService";
import RangeSlider from "../RangeSlider";
import { PaymentStatus } from "../../models/payment";
import VisitModal from "../Popups/VisitModal";
import { getPaymentStatusDisplay } from "../../models/payment";
import FilterDropdown from "../FilterDropdown";
import CycleFilter from "../CycleFilter";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import PeriodNavigator, {
  PERIOD_MODES,
  PeriodMode,
  getPeriodRange,
} from "../PeriodNavigator";
import { toIsoDate } from "../../utils/dateUtils";

const MAX_VISIT_VALUE = 1500;

const VISIT_FILTER_STORAGE_KEY = "visitDashboardState";

interface StoredVisitState {
  filter: VisitFilterDTO;
  selectedClients: Client[] | null;
  selectedEmployees: Employee[] | null;
  periodMode?: PeriodMode;
  periodDate?: string;
}

const DEFAULT_PERIOD_MODE: PeriodMode = "week";
const VISITS_PAGE_SIZE = 100;
const MAX_VISIT_PAGES = 20;

const getPeriodFilter =(mode: PeriodMode, date: string): Partial<VisitFilterDTO> => {
  const { from, to } = getPeriodRange(mode, date);
  return {
    month: from.getMonth() + 1,
    year: from.getFullYear(),
    dateFrom: mode === "month" ? null : toIsoDate(from),
    dateTo: mode === "month" ? null : toIsoDate(to),
  };
};

const getDefaultFilter = (): VisitFilterDTO => ({
  clientIds: null,
  serviceIds: null,
  employeeIds: null,
  isBoost: null,
  isVip: null,
  delayed: null,
  absence: null,
  hasDiscount: null,
  hasSale: null,
  paymentStatus: null,
  totalValueFrom: null,
  totalValueTo: null,
  ...getPeriodFilter(DEFAULT_PERIOD_MODE, toIsoDate(new Date())),
});

const loadStoredVisitState = (): StoredVisitState | null => {
  try {
    const raw = sessionStorage.getItem(VISIT_FILTER_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredVisitState) : null;
  } catch {
    return null;
  }
};

export function VisitDashboard() {
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();
  const [filtersOpen, setFiltersOpen] = useState<boolean>(false);
  // Filters are restored only when coming back (Powrot / browser back), which is
  // a POP navigation. Opening this page from the menu is a PUSH and has to start
  // with a clean filter instead of reusing the previous session.
  const navigationType = useNavigationType();
  const [restoredState] = useState<StoredVisitState | null>(() =>
    navigationType === "POP" ? loadStoredVisitState() : null,
  );
  const [visits, setVisits] = useState<Visit[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [selectedClients, setSelectedClients] = useState<Client[] | null>(
    () => restoredState?.selectedClients ?? null,
  );
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedEmployees, setSelectedEmployees] = useState<Employee[] | null>(
    () => restoredState?.selectedEmployees ?? null,
  );
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [filter, setFilter] = useState<VisitFilterDTO>(
    () => restoredState?.filter ?? getDefaultFilter(),
  );
  const [isAddNewVisitPopupOpen, setIsAddNewVisitPopupOpen] =
    useState<boolean>(false);
  const [periodMode, setPeriodMode] = useState<PeriodMode>(
    () => restoredState?.periodMode ?? DEFAULT_PERIOD_MODE,
  );
  const [periodDate, setPeriodDate] = useState<string>(
    () => restoredState?.periodDate ?? toIsoDate(new Date()),
  );

  const fetchRequestRef = useRef<number>(0);

  const fetchVisits = useCallback(async (visitFilter: VisitFilterDTO) => {
    const requestId = ++fetchRequestRef.current;
    setIsLoading(true);
    try {
      const loaded: Visit[] = [];
      for (let pageNum = 0; ; pageNum++) {
        const data = await VisitService.getVisits(visitFilter, pageNum, VISITS_PAGE_SIZE);
        if (requestId !== fetchRequestRef.current) return;
        loaded.push(...(data?.content ?? []));
        if (!data || data.last || pageNum + 1 >= MAX_VISIT_PAGES) break;
      }
      setVisits(loaded);
    } catch {
      if (requestId === fetchRequestRef.current) setVisits([]);
    } finally {
      if (requestId === fetchRequestRef.current) setIsLoading(false);
    }
  }, []);

  const fetchClients = async (): Promise<void> => {
    ClientService.getClients()
      .then((data) => {
        const sortedClients = [...data].sort((a, b) =>
          a.firstName.localeCompare(b.firstName, "pl", { sensitivity: "base" }),
        );
        setClients(sortedClients);
      })
      .catch(() => {
        setClients([]);
      });
  };
  const fetchEmployees = async () => {
    EmployeeService.getAllEmployees()
      .then((data) => {
        setEmployees(data);
      })
      .catch((error) => {
        console.error("Error fetching Employees: ", error);
        showAlert("Błąd", AlertType.ERROR);
        setEmployees([]);
      });
  };

  const handleResetFiltersAndData = useCallback(() => {
    setFilter(getDefaultFilter());
    setPeriodMode(DEFAULT_PERIOD_MODE);
    setPeriodDate(toIsoDate(new Date()));
    setSelectedClients(null);
    setSelectedEmployees(null);
  }, []);

  const handleClientsChange = useCallback(
    (clients: Client | Client[] | null) => {
      if (clients) {
        const selectedClients = Array.isArray(clients) ? clients : [clients];

        const clientIds = selectedClients.map((client) => client.id);

        setFilter((prev) => ({
          ...prev,
          clientIds: clientIds.length > 0 ? clientIds : null,
        }));
        setSelectedClients(selectedClients.length > 0 ? selectedClients : null);
      } else {
        setFilter((prev) => ({
          ...prev,
          clientIds: null,
        }));
        setSelectedClients(null);
      }
    },
    [],
  );
  const handleEmployeesChange = useCallback(
    (employees: Employee | Employee[] | null) => {
      if (employees) {
        const selectedEmployees = Array.isArray(employees)
          ? employees
          : [employees];

        const employeeIds = selectedEmployees.map((employee) => employee.id);

        setFilter((prev) => ({
          ...prev,
          employeeIds: employeeIds.length > 0 ? employeeIds : null,
        }));
        setSelectedEmployees(
          selectedEmployees.length > 0 ? selectedEmployees : null,
        );
      } else {
        setFilter((prev) => ({
          ...prev,
          employeeIds: null,
        }));
        setSelectedEmployees(null);
      }
    },
    [],
  );

  const handlePeriodChange = useCallback((mode: PeriodMode, date: string) => {
    setPeriodMode(mode);
    setPeriodDate(date);
    setFilter((prev) => ({ ...prev, ...getPeriodFilter(mode, date) }));
  }, []);

  const handleValueRangeChange = useCallback((from: number, to: number) => {
    setFilter((prevFilter) => ({
      ...prevFilter,
      totalValueFrom: from <= 0 ? null : from,
      totalValueTo: to >= MAX_VISIT_VALUE ? null : to,
    }));
  }, []);

  const toggleFilterPaymentStatus = useCallback(() => {
    setFilter((prev) => {
      let nextStatus: PaymentStatus | null = null;

      if (prev.paymentStatus === null) nextStatus = PaymentStatus.PAID;
      else if (prev.paymentStatus === PaymentStatus.PAID)
        nextStatus = PaymentStatus.PARTIAL;
      else if (prev.paymentStatus === PaymentStatus.PARTIAL)
        nextStatus = PaymentStatus.UNPAID;
      else nextStatus = null;

      return { ...prev, paymentStatus: nextStatus };
    });
  }, []);

  useEffect(() => {
    fetchVisits(filter);
  }, [filter, fetchVisits]);

  useEffect(() => {
    fetchClients();
    fetchEmployees();
  }, []);

  useEffect(() => {
    try {
      sessionStorage.setItem(
        VISIT_FILTER_STORAGE_KEY,
        JSON.stringify({ filter, selectedClients, selectedEmployees, periodMode, periodDate }),
      );
    } catch {
      // sessionStorage unavailable - ignore
    }
  }, [filter, selectedClients, selectedEmployees, periodMode, periodDate]);

  const toggleSegments: {
    key: "absence" | "delayed" | "isBoost" | "isVip" | "hasDiscount" | "hasSale";
    label: string;
    icon: React.ReactNode;
  }[] = [
    { key: "absence", label: "Nieobecność", icon: <UserX className="dl-icon-1 c-red" /> },
    { key: "delayed", label: "Spóźnienie", icon: <Clock className="dl-icon-1 icon-delay" /> },
    { key: "isBoost", label: "Boost", icon: <Rocket className="dl-icon-1 icon-rocket" /> },
    { key: "isVip", label: "VIP", icon: <Crown className="dl-icon-1 c-blue" /> },
    { key: "hasDiscount", label: "Zniżka", icon: <Percent className="dl-icon-1 icon-discount" /> },
    { key: "hasSale", label: "Sprzedaż", icon: <ShoppingBag className="dl-icon-1 icon-sale" /> },
  ];

  const renderToggleSegments = (withLabels: boolean) => (
    <div className="cat-seg-group vs-toggles align-items-center g-025">
      {toggleSegments.map((segment) => (
        <button
          type="button"
          key={segment.key}
          title={segment.label}
          className={`cat-seg align-items-center pointer ${
            filter[segment.key] ? "active" : ""
          }`}
          onClick={() =>
            setFilter((prev) => ({
              ...prev,
              [segment.key]: prev[segment.key] === true ? null : true,
            }))
          }
        >
          {segment.icon}
          {withLabels && segment.label}
        </button>
      ))}
    </div>
  );

  const periodModeIndex = PERIOD_MODES.findIndex((mode) => mode.key === periodMode);

  const periodModeField = (
    <CycleFilter
      label="Widok:"
      value={PERIOD_MODES[periodModeIndex].label}
      tone={null}
      iconTitle="Zmień widok"
      onClick={() =>
        handlePeriodChange(
          PERIOD_MODES[(periodModeIndex + 1) % PERIOD_MODES.length].key,
          periodDate,
        )
      }
    />
  );

  const paymentStatusField = (
    <CycleFilter
      label="Status:"
      value={
        filter.paymentStatus
          ? getPaymentStatusDisplay(filter.paymentStatus)
          : "wszystkie"
      }
      tone={
        filter.paymentStatus === PaymentStatus.PAID
          ? "green"
          : filter.paymentStatus === PaymentStatus.PARTIAL
            ? "yellow"
            : filter.paymentStatus === PaymentStatus.UNPAID
              ? "red"
              : null
      }
      iconTitle="Status płatności"
      onClick={toggleFilterPaymentStatus}
    />
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
    <div className="dashboard-panel width-max height-max flex-column align-items-center">
      <NavigationBar
        showSearchbar={false}
      />
      {isMobile ? (
        <>
        <section className="vs-toolbar width-90 flex align-items-center g-1 mt-1 mb-1">
          <FilterDropdown
            label="Filtry"
            className="vs-filters"
            open={filtersOpen}
            onOpenChange={setFiltersOpen}
            count={
              [
                filter.paymentStatus !== null,
                (filter.employeeIds?.length ?? 0) > 0,
                (filter.clientIds?.length ?? 0) > 0,
                filter.totalValueFrom !== null || filter.totalValueTo !== null,
                filter.absence === true,
                filter.delayed === true,
                filter.isBoost === true,
                filter.isVip === true,
              ].filter(Boolean).length
            }
          >
            <div className="fd-row flex align-items-center g-05 space-between">
              {periodModeField}
              {paymentStatusField}
            </div>
            <div className="fd-row vs-fd-people flex align-items-center g-05">
              <DropdownSelect<Employee>
                items={employees}
                onChange={handleEmployeesChange}
                value={selectedEmployees}
                placeholder="Pracownik"
                searchable={false}
                multiple={true}
                allowNew={false}
                className="visit-list"
              />
              <DropdownSelect<Client>
                items={clients}
                onChange={handleClientsChange}
                value={selectedClients}
                allowNew={false}
                multiple={true}
                getItemLabel={(c) => `${c.firstName} ${c.lastName}`}
                className="clients visit-list"
                placeholder="Klient"
              />
            </div>
            <RangeSlider
              min={0}
              max={MAX_VISIT_VALUE}
              step={10}
              valueFrom={filter.totalValueFrom ?? 0}
              valueTo={filter.totalValueTo ?? MAX_VISIT_VALUE}
              onChange={handleValueRangeChange}
              unit="zł"
              className="vs-value"
            />
            <div className="fd-row vs-fd-toggles flex align-items-center g-05">
              {renderToggleSegments(false)}
            </div>
          </FilterDropdown>
          <ActionButton
            disableText={true}
            iconTitle={"Resetuj filtry"}
            onClick={handleResetFiltersAndData}
            className="reset"
          >
            <RotateCw className="dl-icon icon-control" />
          </ActionButton>
          <ActionButton
            text={"Wizyta"}
            iconTitle={"Nowa Wizyta"}
            onClick={() => setIsAddNewVisitPopupOpen(true)}
            className="ml-auto"
          >
            <CalendarPlus className="dl-icon-1 c-green" />
          </ActionButton>
        </section>
        </>
      ) : (
        <>
        <section className="vs-toolbar width-90 flex align-items-center g-1 mt-1 mb-1">
          {periodModeField}
          <DropdownSelect<Employee>
            items={employees}
            onChange={handleEmployeesChange}
            value={selectedEmployees}
            placeholder="Pracownik"
            searchable={false}
            multiple={true}
            allowNew={false}
            className="visit-list"
          />
          <DropdownSelect<Client>
            items={clients}
            onChange={handleClientsChange}
            value={selectedClients}
            allowNew={false}
            multiple={true}
            getItemLabel={(c) => `${c.firstName} ${c.lastName}`}
            className="clients visit-list"
            placeholder="Klient"
          />
        
          <RangeSlider
            min={0}
            max={MAX_VISIT_VALUE}
            step={10}
            valueFrom={filter.totalValueFrom ?? 0}
            valueTo={filter.totalValueTo ?? MAX_VISIT_VALUE}
            onChange={handleValueRangeChange}
            unit="zł"
            className="vs-value"
          />
        
        </section>
        <section className="visit-filters width-90 flex align-items-center g-05 mb-1">
          {paymentStatusField}
          {renderToggleSegments(true)}
          <ActionButton
            disableText={true}
            iconTitle={"Resetuj filtry"}
            onClick={handleResetFiltersAndData}
            className="reset"
          >
            <RotateCw className="dl-icon icon-control" />
          </ActionButton>
          <ActionButton
            text={"Nowa Wizyta"}
            onClick={() => setIsAddNewVisitPopupOpen(true)}
            className="ml-auto"
          >
            <CalendarPlus className="dl-icon-1 c-green" />
          </ActionButton>
        </section>
        </>
      )}
      <section className="vs-period-row width-90 flex justify-center mb-1">
        <PeriodNavigator
          mode={periodMode}
          value={periodDate}
          onChange={(date) => handlePeriodChange(periodMode, date)}
        />
      </section>
      <div className="width-93 flex-column f-1 min-height-0 mb-2">
        <VisitBoard
          visits={visits}
          employees={employees}
          selectedEmployees={selectedEmployees}
          isLoading={isLoading}
          handleResetFiltersAndData={handleResetFiltersAndData}
        />
      </div>
      {isAddNewVisitPopupOpen && (
        <VisitModal
          onClose={() => setIsAddNewVisitPopupOpen(false)}
          onReset={() => fetchVisits(filter)}
        />
      )}
    </div>
    </div>
  );
}

export default VisitDashboard;
