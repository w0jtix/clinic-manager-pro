import React from "react";
import { Download, FilePlusCorner } from "lucide-react";
import { useState, useCallback, useEffect } from "react";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import ProductReportModal from "../Popups/ProductReportModal";
import StockAdjustmentReportModal from "../Popups/StockAdjustmentReportModal";
import NavigationBar from "../NavigationBar";
import InventoryReportService from "../../services/InventoryReportService";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import {
  InventoryReport,
  InventoryReportFilterDTO,
} from "../../models/inventory_report";
import { AlertType } from "../../models/alert";
import InventoryReportDataList from "./InventoryReportDataList";
import DropdownSelect from "../DropdownSelect";
import { Employee } from "../../models/employee";
import EmployeeService from "../../services/EmployeeService";
import PeriodPill from "../PeriodPill";

export function InventoryReportDashboard() {
  const { isMobile } = useBreakpoint();
  const [isCurrentInventoryPopupOpen, setIsCurrentInventoryPopupOpen] =
    useState<boolean>(false);
  const [isProductReportPopupOpen, setIsProductReportPopupOpen] =
    useState<boolean>(false);
  const [inventoryReports, setInventoryReports] = useState<InventoryReport[]>(
    [],
  );
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [filter, setFilter] = useState<InventoryReportFilterDTO>({
    employeeId: null,
    year: new Date().getFullYear(),
    month: null,
  });
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const { showAlert } = useAlert();

  const fetchInventoryReports = useCallback(
    async (pageNum: number = 0, append: boolean = false) => {
      setLoading(true);
      InventoryReportService.getInventoryReports(filter, pageNum, 30)
        .then((data) => {
          const content = data?.content || [];
          if (append) {
            setInventoryReports((prev) => [...prev, ...content]);
          } else {
            setInventoryReports(content);
          }

          setHasMore(!data.last);
          setPage(pageNum);
          setLoading(false);
        })
        .catch((error) => {
          if (!append) setInventoryReports([]);
          setLoading(false);
          showAlert("Błąd", AlertType.ERROR);
          console.error("Error fetching categories:", error);
        });
    },
    [filter],
  );
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

  const handleEmployeeChange = useCallback(
      (employee: Employee | Employee[] | null) => {
        const selected = Array.isArray(employee) ? employee[0] : employee;
        setFilter((prev) => ({
          ...prev,
          employeeId: selected?.id ?? null,
        }));
      },
      []
    );
  const handleYearChange = useCallback(
    (year: number | null) => {
      setFilter((prev) => {
        let newMonth = prev.month;
        if (year === currentYear && prev.month && prev.month > currentMonth) {
          newMonth = currentMonth;
        }
        return {
          ...prev,
          year: year ?? prev.year,
          month: newMonth,
        };
      });
    },
    [currentYear, currentMonth],
  );
  const handleMonthChange = useCallback(
    (month: number | null) => {
      setFilter((prev) => ({
        ...prev,
        month: month ?? null,
      }));
    },
    [],
  );

  useEffect(() => {
    fetchEmployees();
    fetchInventoryReports(0, false);
    setPage(0);
    setHasMore(true);
  }, [filter]);


  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100; // 100px b4 end of the list

      if (scrolledToBottom && hasMore && !loading) {
        fetchInventoryReports(page + 1, true);
      }
    },
    [hasMore, loading, page, filter],
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
    <div className="dashboard-panel width-max height-max flex-column align-items-center">
      <NavigationBar showSearchbar={false} />

      <section className="products-action-buttons ir-toolbar width-90 flex align-self-center space-between mt-1 mb-1">
        <div className="pab-left flex align-items-center g-25">
          <div className="flex g-1 align-items-center">
            <DropdownSelect<Employee>
              items={employees}
              onChange={handleEmployeeChange}
              value={
                filter.employeeId
                  ? employees.find((e) => e.id === filter.employeeId) ?? null
                  : null
              }
              placeholder="Pracownik"
              searchable={false}
              multiple={false}
              allowNew={false}
              className="visit-list"
            />
          </div>
          <PeriodPill
            month={filter.month}
            year={filter.year}
            onMonthChange={handleMonthChange}
            onYearChange={handleYearChange}
            sub={!filter.month ? "- Miesiąc" : undefined}
            monthPlaceholder="Miesiąc"
            yearPlaceholder="Rok"
          />
        </div>
        <div className="pab-right flex g-25 justify-end">
          <ActionButton
            text={isMobile ? "Wygeneruj PDF" : "Wygeneruj PDF Stanu Magazynowego"}
            iconTitle={"Wygeneruj PDF Stanu Magazynowego"}
            onClick={() => setIsCurrentInventoryPopupOpen(true)}
          >
            <Download className="dl-icon-1 c-blue" aria-label="Wygeneruj PDF" />
          </ActionButton>
          <ActionButton
            text={isMobile ? "Raport" : "Utwórz Raport Inwentaryzacyjny"}
            iconTitle={"Utwórz Raport Inwentaryzacyjny"}
            onClick={() => setIsProductReportPopupOpen(true)}
          >
            <FilePlusCorner className="dl-icon-1 c-yel" aria-label="Utwórz Raport" />
          </ActionButton>
        </div>
      </section>
        <InventoryReportDataList
          className="ir-list"
          inventoryReports={inventoryReports}
          onScroll={handleScroll}
          onSuccess={() => fetchInventoryReports(0, false)}
          isLoading={loading}
        />
      {isCurrentInventoryPopupOpen && (
        <ProductReportModal
          onClose={() => setIsCurrentInventoryPopupOpen(false)}
        />
      )}
      {isProductReportPopupOpen && (
        <StockAdjustmentReportModal
          onClose={() => setIsProductReportPopupOpen(false)}
          onSuccess={() => fetchInventoryReports(0, false)}
        />
      )}
    </div>
     </div>
  );
}

export default InventoryReportDashboard;
