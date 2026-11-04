import React, { useState, useCallback, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { RefreshCw, RotateCw, Settings, FileText } from "lucide-react";
import {
  KsefInvoiceFilterDTO,
  KsefInvoice,
  KsefInvoiceCount,
  KsefSyncStatus,
  KsefSyncStatusType,
} from "../../models/ksef_invoice";
import { KsefSyncStatusTooltip } from "./KsefSyncStatusTooltip";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import KsefInvoiceService from "../../services/KsefInvoiceService";
import { KsefSettingsModal } from "../Popups/KsefSettingsModal";
import KsefInvoiceDataList from "./KsefInvoiceDataList";
import KsefInvoiceModal from "../Popups/KsefInvoiceModal";
import KsefIssuedInvoicesModal from "../Popups/KsefIssuedInvoicesModal";
import KsefIssuedInvoiceService from "../../services/KsefIssuedInvoiceService";
import NamedInvoiceService from "../../services/NamedInvoiceService";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";
import PeriodPill from "../PeriodPill";
import { useRoles } from "../../hooks/useRoles";

export function KsefInvoicesPage() {
  const { showAlert } = useAlert();
  const { isMobile, isCompact, isNarrow } = useBreakpoint();
  const location = useLocation();
  const navigate = useNavigate();
  const currentDate = new Date();
  const [invoices, setInvoices] = useState<KsefInvoice[]>([]);
  const [counter, setCounter] = useState<KsefInvoiceCount | null>(null);
  const [syncStatus, setSyncStatus] = useState<KsefSyncStatus>({
    status: KsefSyncStatusType.NEVER_SYNCED,
    lastSyncedAt: null,
    lastAttemptAt: null,
    lastNewCount: 0,
    lastFailedCount: 0,
    lastIssuedNewCount: 0,
    lastIssuedFailedCount: 0,
    rateLimitHit: false,
    summary: { fullySynced: false, totalLocal: 0, totalKsef: null, monthsWithMismatch: [] },
  });
  const [filter, setFilter] = useRestorableState<KsefInvoiceFilterDTO>(
    "ksef.filter",
    {
      year: currentDate.getFullYear(),
      month: currentDate.getMonth() + 1,
    },
    { force: !!(location.state as { period?: unknown } | null)?.period }
  );
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [syncing, setSyncing] = useState<boolean>(false);

  const [isKsefSettingsPopupOpen, setIsKsefSettingsPopupOpen] = useState<boolean>(false);
  const { isAdmin } = useRoles();
  const [isSyncStatusHovered, setIsSyncStatusHovered] = useState<boolean>(false);
  const [previewInvoiceId, setPreviewInvoiceId] = useRestorableState<number | null>(
    "ksef.previewInvoiceId",
    null,
    { skipOnReload: true }
  );
  const [issuedCount, setIssuedCount] = useState<number>(0);
  const [isIssuedInvoicesOpen, setIsIssuedInvoicesOpen] = useState<boolean>(false);
  const fetchInvoices = useCallback(
    async (pageNum: number = 0, append: boolean = false) => {
      setLoading(true);
      KsefInvoiceService.getInvoices(filter, pageNum, 30)
        .then((data) => {
          const content = data?.content || [];
          if (append) {
            setInvoices((prev) => [...prev, ...content]);
          } else {
            setInvoices(content);
          }
          setHasMore(!data.last);
          setPage(pageNum);
        })
        .catch((error) => {
          if (!append) setInvoices([]);
          setHasMore(false);
          showAlert("Błąd pobierania faktur", AlertType.ERROR);
          console.error("Error fetching KSeF invoices:", error);
        })
        .finally(() => setLoading(false));
    },
    [filter],
  );

  const fetchSyncStatus = useCallback(async () => {
    KsefInvoiceService.getSyncStatus()
      .then((data) => {
        setSyncStatus(data);
      })
      .catch((error) => {
        console.error("Error fetching KSeF sync status:", error);
      });
  }, []);

  const fetchCount = useCallback(async () => {
    KsefInvoiceService.getCount(filter)
      .then((data) => {
        setCounter(data);
      })
      .catch((error) => {
        console.error("Error fetching KSeF invoice count:", error);
      });
  }, [filter]);

  // Licznik na przycisku obejmuje obie serie - faktury z KSeF i imienne -
  // bo modal pokazuje je razem.
  const fetchIssuedCount = useCallback(async () => {
    const ksefCount = KsefIssuedInvoiceService.getCount({
      year: filter.year,
      month: filter.month,
    })
      .then((data) => data?.count ?? 0)
      .catch(() => 0);

    const namedCount =
      filter.year && filter.month
        ? NamedInvoiceService.getByPeriod(filter.year, filter.month)
            .then((data) => (Array.isArray(data) ? data.length : 0))
            .catch(() => 0)
        : Promise.resolve(0);

    const [ksef, named] = await Promise.all([ksefCount, namedCount]);
    setIssuedCount(ksef + named);
  }, [filter.year, filter.month]);

  const handleSync = useCallback(async () => {
    setSyncing(true);
    KsefInvoiceService.sync()
      .then(() => KsefInvoiceService.getSyncStatus())
      .then((status) => {
        setSyncStatus(status);
        const receivedNew = status.lastNewCount ?? 0;
        const issuedNew = status.lastIssuedNewCount ?? 0;
        const failed = (status.lastFailedCount ?? 0) + (status.lastIssuedFailedCount ?? 0);
        const newSummary = `otrzymane: ${receivedNew}, wystawione: ${issuedNew}`;
        if (status.rateLimitHit) {
          showAlert(
            `Pobrano nowe faktury (${newSummary}). Wyczerpano limit zapytań KSeF - spróbuj ponownie za godzinę.`,
            AlertType.INFO
          );
        } else if (status.status === KsefSyncStatusType.PARTIAL_FAILURE) {
          showAlert(
            `Synchronizacja częściowa - nowe faktury (${newSummary}), błędów: ${failed}.`,
            AlertType.INFO
          );
        } else {
          showAlert(
            `Synchronizacja zakończona! Nowe faktury - ${newSummary}.`,
            AlertType.SUCCESS
          );
        }
        fetchInvoices(0, false);
        fetchCount();
        fetchIssuedCount();
      })
      .catch((error) => {
        showAlert("Błąd synchronizacji z KSeF!", AlertType.ERROR);
        console.error("Error syncing KSeF:", error);
        fetchSyncStatus();
      })
      .finally(() => setSyncing(false));
  }, [fetchInvoices, fetchSyncStatus, fetchCount, fetchIssuedCount, showAlert]);

  const handleTogglePaid = useCallback((id: number) => {
    KsefInvoiceService.togglePaid(id)
      .then((updated) => {
        setInvoices((prev) => prev.map((inv) => inv.id === id ? updated : inv));
        showAlert("Pomyślnie zmieniono status faktury.", AlertType.SUCCESS);
      })
      .catch(() => showAlert("Błąd zmiany statusu faktury.", AlertType.ERROR));
  }, [showAlert]);



  const handleInvoicePaidChanged = useCallback((id: number, isPaid: boolean) => {
    setInvoices((prev) => prev.map((inv) => inv.id === id ? { ...inv, isPaid } : inv));
  }, []);


  const handleResetFiltersAndData = useCallback(() => {
    const now = new Date();
    setFilter({
      year: now.getFullYear(),
      month: now.getMonth() + 1,
    });
    setPage(0);
    setHasMore(true);
  }, []);

  useEffect(() => {
    fetchInvoices(0, false);
    setPage(0);
    setHasMore(true);
  }, [filter, fetchInvoices]);

  useEffect(() => {
    fetchCount();
  }, [fetchCount]);

  useEffect(() => {
    fetchIssuedCount();
  }, [fetchIssuedCount]);

  useEffect(() => {
    fetchSyncStatus();
  }, []);

  useEffect(() => {
    const state = location.state as {
      previewInvoiceId?: number;
      period?: { year?: number | null; month?: number | null };
    } | null;
    if (!state?.period && state?.previewInvoiceId == null) return;

    if (state.period) {
      setFilter((prev) => ({
        ...prev,
        year: state.period?.year ?? null,
        month: state.period?.month ?? null,
      }));
    }
    if (state.previewInvoiceId != null) {
      setPreviewInvoiceId(state.previewInvoiceId);
    }
    navigate(location.pathname, { replace: true, state: { module: "KSeF" } });
  }, [location, navigate]);


  const handleMonthChange = useCallback(
    (month: number | null) => {
      setFilter((prevFilter) => ({ ...prevFilter, month: month ?? null }));
    },
    [],
  );

  const handleYearChange = useCallback(
    (year: number | null) => {
      setFilter((prevFilter) => ({ ...prevFilter, year: year ?? null }));
    },
    [],
  );

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !loading) {
        fetchInvoices(page + 1, true);
      }
    },
    [hasMore, loading, page, fetchInvoices],
  );

  const complianceClass =
    counter && counter.ksefCount !== null
      ? counter.ksefCount === counter.localCount
        ? "c-green"
        : counter.ksefCount < counter.localCount
          ? "pc-y"
          : "c-red"
      : "";

  const statusClass =
    syncStatus?.status === KsefSyncStatusType.SUCCESS
      ? "active"
      : syncStatus?.status === KsefSyncStatusType.PARTIAL_FAILURE
        ? "onhold"
        : "used";

  const syncButton = (
    <ActionButton
      text={syncing ? "Synchronizowanie..." : "Synchronizuj"}
      onClick={handleSync}
      disabled={syncing}
      className="clx-btn sync"
    >
      <RefreshCw className={`dl-icon-1 pc-y ${syncing ? "ksf-spin" : ""}`} />
    </ActionButton>
  );

  const syncStatusBlock = (
    <div
      className="ksf-sync-status relative flex align-items-center g-1"
      onMouseEnter={() => setIsSyncStatusHovered(true)}
      onMouseLeave={() => setIsSyncStatusHovered(false)}
    >
      <div className="flex align-items-center g-05">
        <span className="clx-meta-label">Zgodność:</span>
        <span className={`ksf-value ${complianceClass}`}>
          {counter
            ? `${counter.localCount} / ${counter.ksefCount !== null ? counter.ksefCount : "-"}`
            : "- / -"}
        </span>
      </div>
      <div className="flex align-items-center g-05">
        <span className="clx-meta-label">Status:</span>
        <span className={`rv-status ${statusClass}`}>
          {syncStatus?.status === KsefSyncStatusType.NEVER_SYNCED && "BRAK SYNCHRONIZACJI"}
          {syncStatus?.status === KsefSyncStatusType.SUCCESS && "SUKCES"}
          {syncStatus?.status === KsefSyncStatusType.PARTIAL_FAILURE && "NIEPEŁNA"}
          {!syncStatus && "BRAK"}
        </span>
      </div>
      {isSyncStatusHovered && syncStatus && (
        <KsefSyncStatusTooltip syncStatus={syncStatus} />
      )}
    </div>
  );

  const periodPill = (
    <PeriodPill
      month={filter.month}
      year={filter.year}
      onMonthChange={handleMonthChange}
      onYearChange={handleYearChange}
    />
  );

  const resetButton = (
    <ActionButton
      iconTitle="Resetuj filtry"
      onClick={handleResetFiltersAndData}
      disableText={true}
      className="reset"
    >
      <RotateCw className="dl-icon icon-control" />
    </ActionButton>
  );

  const issuedButton = (
    <ActionButton
      iconTitle="Podgląd faktur wystawionych w wybranym okresie"
      onClick={() => setIsIssuedInvoicesOpen(true)}
      className="clx-btn ksf-issued"
    >
      <FileText className="dl-icon-1" />
      <div className="flex g-025">
      {!isNarrow && (
        <span className="ksf-issued-label nowrap">
          {isCompact ? "Wystawione -" : "Faktury wystawione -"}
        </span>
      )}
      <span className={`ksf-issued-count tnum ${issuedCount > 0 ? "has" : ""}`}>
        {issuedCount}
      </span>
      </div>
    </ActionButton>
  );

  const settingsButton = isAdmin && (
    <ActionButton
      text="Ustawienia KSeF"
      iconTitle="Ustawienia KSeF"
      onClick={() => setIsKsefSettingsPopupOpen(true)}
      disableText={isCompact}
      className={`clx-btn ${isCompact ? "ksf-icon-btn" : ""}`}
    >
      <Settings className="dl-icon-1" />
    </ActionButton>
  );

  return (
    <>
      {isMobile ? (
        <section className="ksf-toolbar width-90 flex-column align-self-center g-075 mt-1 mb-1">
          <div className="flex align-items-center space-between g-1">
            <div className="flex align-items-center g-1">
              {syncButton}
              {syncStatusBlock}
            </div>
            {settingsButton}
          </div>
          <div className="flex align-items-center space-between g-1">
            <div className="flex align-items-center g-05">
              {periodPill}
              {resetButton}
            </div>
            {issuedButton}
          </div>
        </section>
      ) : (
        <section className="ksf-toolbar width-90 flex align-self-center align-items-center space-between g-1 mt-1 mb-1">
          <div className="flex align-items-center g-1">
            {syncButton}
            {syncStatusBlock}
          </div>
          <div className="flex align-items-center g-1">
            {periodPill}
            {resetButton}
            {issuedButton}
            {settingsButton}
          </div>
        </section>
      )}
      <section className="width-90 flex-column f-1 align-items-center min-height-0 mb-1">
        <KsefInvoiceDataList
          items={invoices}
          onScroll={handleScroll}
          isLoading={loading}
          onTogglePaid={handleTogglePaid}
          onOpenPreview={setPreviewInvoiceId}
          originPeriod={{ year: filter.year, month: filter.month }}
        />
      </section>
      {isAdmin && isKsefSettingsPopupOpen && (
        <KsefSettingsModal
          onClose={() => setIsKsefSettingsPopupOpen(false)}
        />
      )}
      {isIssuedInvoicesOpen && (
        <KsefIssuedInvoicesModal
          year={filter.year}
          month={filter.month}
          onClose={() => {
            setIsIssuedInvoicesOpen(false);
            fetchIssuedCount();
          }}
        />
      )}
      {previewInvoiceId !== null && (
        <KsefInvoiceModal
          invoiceId={previewInvoiceId}
          onClose={() => setPreviewInvoiceId(null)}
          onPaidChanged={handleInvoicePaidChanged}
          originModule="KSeF"
          allowVerification={false}
          originPeriod={{ year: filter.year, month: filter.month }}
        />
      )}
    </>
  );
}

export default KsefInvoicesPage;
