import { useEffect, useState, useCallback } from "react";
import { CirclePlus, RotateCw } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import RemoveModal from "./RemoveModal";
import { AlertType } from "../../models/alert";
import {
  getUsageReasonDisplay,
  UsageRecord,
  UsageRecordFilterDTO,
  UsageReason,
} from "../../models/usage-record";
import UsageRecordService from "../../services/UsageRecordService";
import UsageRecordDataList from "../Products/UsageRecordDataList";
import UsageRecordModal from "./UsageRecordModal";
import SearchBar from "../SearchBar";
// import DateInput from "../DateInput";
import { WheelDateRangeField } from "../WheelDateField";
import DropdownSelect from "../DropdownSelect";
import FilterDropdown from "../FilterDropdown";
import CycleFilter from "../CycleFilter";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { Employee } from "../../models/employee";
import EmployeeService from "../../services/EmployeeService";

export interface UsageRecordsModalProps {
  onClose: () => void;
  className?: string;
}

export function UsageRecordsModal({
  onClose,
  className = "",
}: UsageRecordsModalProps) {
  const [isAddNewUsageRecordPopupOpen, setIsAddNewUsageRecordPopupOpen] =
    useState<boolean>(false);
  const [removeUsageRecordId, setRemoveUsageRecordId] = useState<number | null>(
    null
  );
  const [usageRecords, setUsageRecords] = useState<UsageRecord[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selectedEmployees, setSelectedEmployees] = useState<Employee[] | null>(
    null
  );
  const [filter, setFilter] = useState<UsageRecordFilterDTO>({
    keyword: "",
    employeeIds: null,
    usageReason: null,
    startDate: null,
    endDate: null,
  });
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isCompact } = useBreakpoint();

  const fetchUsageRecords = async (
    pageNum: number = 0,
    append: boolean = false
  ): Promise<void> => {
    setLoading(true);
    UsageRecordService.getUsageRecords(filter, pageNum, 30)
      .then((data) => {
        const content = data?.content || [];
        if (append) {
          setUsageRecords((prev) => [...prev, ...content]);
        } else {
          setUsageRecords(content);
        }

        setHasMore(!data.last);
        setPage(pageNum);
      })
      .catch((error) => {
        if (!append) setUsageRecords([]);
        setHasMore(false);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching UsageRecords: ", error);
      })
      .finally(() => {
        setLoading(false);
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

  const handleKeywordChange = useCallback((newKeyword: string) => {
    setFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const toggleStatus = () => {
    setFilter((prev) => {
      let nextStatus: UsageReason | null = null;

      if (prev.usageReason === null) nextStatus = UsageReason.REGULAR_USAGE;
      else if (prev.usageReason === UsageReason.REGULAR_USAGE)
        nextStatus = UsageReason.OUT_OF_DATE;
      else nextStatus = null;

      return { ...prev, usageReason: nextStatus };
    });
  };

  const handleUsageRecordRemove = useCallback(async (): Promise<void> => {
    try {
      if (removeUsageRecordId) {
        await UsageRecordService.deleteUsageRecord(removeUsageRecordId);
        showAlert("Pomyślnie usunięto Zużycie Produktu!", AlertType.SUCCESS);
        setRemoveUsageRecordId(null);
        fetchUsageRecords();
      }
    } catch (error) {
      showAlert("Błąd usuwania Zużycia Produktu!", AlertType.ERROR);
    }
  }, [removeUsageRecordId]);

  const handleDateRangeChange = useCallback(
    (newStartDate: string | null, newEndDate?: string | null) => {
      setFilter((prevFilter) => ({
        ...prevFilter,
        startDate: newStartDate,
        endDate: newEndDate ?? null,
      }));
    },
    []
  );

  const handleEmployeeChange = useCallback(
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
          selectedEmployees.length > 0 ? selectedEmployees : null
        );
      } else {
        setFilter((prev) => ({
          ...prev,
          employeeIds: null,
        }));
        setSelectedEmployees(null);
      }
    },
    []
  );

  const handleResetFiltersAndData = useCallback(() => {
    setFilter({
      keyword: "",
      employeeIds: null,
      usageReason: null,
      startDate: null,
      endDate: null,
    });
    setSelectedEmployees([]);
    setResetTriggered((prev) => !prev);
    setPage(0);
    setHasMore(true);
  }, []);

  useEffect(() => {
    fetchUsageRecords();
    fetchEmployees();
  }, []);

  useEffect(() => {
    fetchUsageRecords();
  }, [filter]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;

      if (scrolledToBottom && hasMore && !loading) {
        fetchUsageRecords(page + 1, true);
      }
    },
    [hasMore, loading, page, filter]
  );

  return (
    <>
      <Modal
        title="Zarządzaj Zużyciem Produktów"
        onClose={onClose}
        size="xl"
        height="tall"
        bodyClassName="nested"
        className={className}
        headerExtra={
          <ActionButton
            text={"Nowe Zużycie"}
            disableText={isCompact}
            iconTitle={"Nowe Zużycie"}
            className="ur-new"
            onClick={() => setIsAddNewUsageRecordPopupOpen(true)}
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        }
      >
        <section className="modal-filters flex width-max align-items-center g-1">
          <FilterDropdown
            label="Filtry"
            className="ur-filters"
            count={
              [
                filter.keyword !== "",
                (filter.employeeIds?.length ?? 0) > 0,
                !!filter.startDate,
                !!filter.usageReason,
              ].filter(Boolean).length
            }
          >
            <SearchBar
              onKeywordChange={handleKeywordChange}
              resetTriggered={resetTriggered}
            />
            <DropdownSelect<Employee>
              items={employees}
              onChange={handleEmployeeChange}
              value={selectedEmployees}
              placeholder="Pracownik"
              searchable={false}
              multiple={true}
              allowNew={false}
              className="visit-list"
            />
            {/* <DateInput
              allowRange={true}
              onChange={handleDateRangeChange}
              selectedDate={filter.startDate || null}
              selectedEndDate={filter.endDate || null}
            /> */}
            <WheelDateRangeField
              from={filter.startDate || null}
              to={filter.endDate || null}
              onChange={handleDateRangeChange}
            />
            <CycleFilter
              label="Powód:"
              value={
                filter.usageReason
                  ? getUsageReasonDisplay(filter.usageReason)
                  : "wszystkie"
              }
              tone={
                !filter.usageReason
                  ? null
                  : filter.usageReason === UsageReason.OUT_OF_DATE
                  ? "red"
                  : "yellow"
              }
              iconTitle="Powód"
              onClick={toggleStatus}
            />
          </FilterDropdown>
          <ActionButton
            disableText={true}
            iconTitle={"Resetuj filtry"}
            onClick={handleResetFiltersAndData}
            className="reset"
          >
            <RotateCw className="dl-icon icon-control" />
          </ActionButton>
        </section>
        <UsageRecordDataList
          className="ur-dl"
          items={usageRecords}
          setRemoveUsageRecordId={setRemoveUsageRecordId}
          onScroll={handleScroll}
          isLoading={loading}
        />
      </Modal>

      {isAddNewUsageRecordPopupOpen && (
        <UsageRecordModal
          onClose={() => {
            setIsAddNewUsageRecordPopupOpen(false);
            handleResetFiltersAndData();
          }}
        />
      )}
      {removeUsageRecordId != null && (
        <RemoveModal
          onClose={() => setRemoveUsageRecordId(null)}
          handleRemove={handleUsageRecordRemove}
          warningText={"Usunięcie spowoduje przywrócenie produktu do stanu przed zużyciem."}
        />
      )}
    </>
  );
}

export default UsageRecordsModal;
