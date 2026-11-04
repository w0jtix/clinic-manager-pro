import { useEffect, useState, useCallback } from "react";
import { RotateCw } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import LogPreviewModal from "./LogPreviewModal";
import { AlertType } from "../../models/alert";
import SearchBar from "../SearchBar";
import {
  AuditAction,
  AuditLog,
  AuditLogFilterDTO,
} from "../../models/audit_log";
import AuditLogService from "../../services/AuditLogService";
import LogDataList from "../Business/LogDataList";
import DropdownSelect from "../DropdownSelect";
import { User } from "../../models/login";
import UserService from "../../services/UserService";
// import DateInput from "../DateInput";
import { WheelDateRangeField } from "../WheelDateField";
import FilterDropdown from "../FilterDropdown";
import CycleFilter from "../CycleFilter";
import { useBreakpoint } from "../../hooks/useBreakpoint";

const EMPTY_FILTER: AuditLogFilterDTO = {
  entityType: null,
  keyword: "",
  action: null,
  performedBy: null,
  dateFrom: null,
  dateTo: null,
};

export interface LogsModalProps {
  onClose: () => void;
  className?: string;
}

export function LogsModal({ onClose, className = "" }: LogsModalProps) {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [previewLog, setPreviewLog] = useState<AuditLog | null>(null);
  const [filter, setFilter] = useState<AuditLogFilterDTO>(EMPTY_FILTER);
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchLogs = async (
    pageNum: number = 0,
    append: boolean = false
  ): Promise<void> => {
    setLoading(true);
    AuditLogService.getAuditLogs(filter, pageNum)
      .then((data) => {
        if (append) {
          setLogs((prev) => [...prev, ...data.content]);
        } else {
          setLogs(data.content);
        }
        setPage(pageNum);
        setHasMore(!data.last);
      })
      .catch((error) => {
        setLogs([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Logs: ", error);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const fetchUsers = async (): Promise<void> => {
    UserService.getAllUsers()
      .then((data) => setUsers(data))
      .catch((error) => {
        setUsers([]);
        console.error("Error fetching Users: ", error);
      });
  };

  const handleUserSelect = useCallback((value: User | User[] | null) => {
    const user = Array.isArray(value) ? value[0] : value;
    setSelectedUser(user);
    setFilter((prev) => ({
      ...prev,
      performedBy: user ? user.username : null,
    }));
  }, []);

  const handleKeywordChange = useCallback((newKeyword: string) => {
    setFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const handleDateRangeChange = useCallback(
    (dateFrom: string | null, dateTo?: string | null) => {
      setFilter((prev) => ({
        ...prev,
        dateFrom,
        dateTo: dateTo ?? null,
      }));
    },
    []
  );

  const toggleAction = useCallback(() => {
    setFilter((prev) => {
      let nextAction: AuditAction | null = null;

      if (prev.action === null) nextAction = AuditAction.CREATE;
      else if (prev.action === AuditAction.CREATE)
        nextAction = AuditAction.UPDATE;
      else if (prev.action === AuditAction.UPDATE)
        nextAction = AuditAction.DELETE;
      else if (prev.action === AuditAction.DELETE)
        nextAction = AuditAction.SEND;
      else if (prev.action === AuditAction.SEND)
        nextAction = AuditAction.SECURITY;
      else nextAction = null;

      return { ...prev, action: nextAction };
    });
  }, []);

  const handleResetFilters = useCallback(() => {
    setFilter(EMPTY_FILTER);
    setResetTriggered((prev) => !prev);
    setSelectedUser(null);
  }, []);

  useEffect(() => {
    fetchLogs();
    fetchUsers();
  }, []);

  useEffect(() => {
    fetchLogs();
  }, [filter]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;

      if (scrolledToBottom && hasMore && !loading) {
        fetchLogs(page + 1, true);
      }
    },
    [hasMore, loading, page, filter]
  );

  const actionButton = (
    <CycleFilter
      label="Akcja:"
      value={filter.action ?? "wszystkie"}
      tone={
        filter.action === null
          ? null
          : filter.action === AuditAction.CREATE
          ? "green"
          : filter.action === AuditAction.UPDATE
          ? "blue"
          : filter.action === AuditAction.SEND
          ? "teal"
          : filter.action === AuditAction.SECURITY
          ? "yellow"
          : "red"
      }
      iconTitle="Akcja"
      onClick={toggleAction}
    />
  );

  const userSelect = (
    <DropdownSelect<User>
      items={users}
      onChange={handleUserSelect}
      value={selectedUser}
      getItemLabel={(u) => u.username}
      placeholder="Użytkownik"
      searchable={false}
      multiple={false}
      allowNew={false}
    />
  );

  const dateInput = (
    <>
      {/* <DateInput
        allowRange={true}
        onChange={handleDateRangeChange}
        selectedDate={filter.dateFrom}
        selectedEndDate={filter.dateTo}
      /> */}
      <WheelDateRangeField
        from={filter.dateFrom}
        to={filter.dateTo}
        onChange={handleDateRangeChange}
      />
    </>
  );

  const searchBar = (
    <SearchBar
      onKeywordChange={handleKeywordChange}
      resetTriggered={resetTriggered}
    />
  );

  const resetButton = (
    <ActionButton
      disableText={true}
      iconTitle={"Resetuj filtry"}
      onClick={handleResetFilters}
      className={"reset"}
    >
      <RotateCw className="dl-icon icon-control" />
    </ActionButton>
  );

  return (
    <>
      <Modal
        title="Historia zmian"
        onClose={onClose}
        size="2xl"
        height="tall"
        bodyClassName="nested"
        className={`logs-modal ${className}`}
      >
        <section
          className={`modal-filters flex width-max align-items-center ${
            isMobile ? "g-05" : "space-between g-1"
          }`}
        >
          {isMobile ? (
            <>
              <FilterDropdown
                label="Filtry"
                className="log-filters"
                count={
                  [
                    filter.keyword !== "",
                    filter.dateFrom !== null,
                    filter.action !== null,
                    filter.performedBy !== null,
                  ].filter(Boolean).length
                }
              >
                {searchBar}
                <div className="fd-row flex justify-center">{dateInput}</div>
                <div className="fd-row flex align-items-center g-05">
                  {actionButton}
                  {userSelect}
                </div>
              </FilterDropdown>
              {resetButton}
            </>
          ) : (
            <>
              {actionButton}
              {userSelect}
              {dateInput}
              {searchBar}
              {resetButton}
            </>
          )}
        </section>

        <LogDataList
          logs={logs}
          onRowClick={(log) => setPreviewLog(log)}
          onScroll={handleScroll}
          isLoading={loading}
        />
      </Modal>

      {previewLog && (
        <LogPreviewModal
          log={previewLog}
          onClose={() => setPreviewLog(null)}
        />
      )}
    </>
  );
}

export default LogsModal;
