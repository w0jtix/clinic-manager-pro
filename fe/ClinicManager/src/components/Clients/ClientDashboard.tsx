import React, { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Phone,
  Mail,
  MailCheck,
  MailWarning,
  Zap,
  Percent,
  Flag,
  Check,
  CalendarPlus,
  SquarePen,
  CirclePlus,
  Ticket,
  HandCoins,
  UserRound,
  TriangleAlert,
  Trash2,
  Eye,
  FileText,
  MapPin,
  ClipboardList,
  UserX,
} from "lucide-react";
import RemoveModal from "../Popups/RemoveModal";
import NavigationBar from "../NavigationBar";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import ClientSpendChart from "./ClientSpendChart";
import ClientVisitList from "./ClientVisitList";
import VisitModal from "../Popups/VisitModal";
import ClientModal from "../Popups/ClientModal";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";
import ClientService from "../../services/ClientService";
import ClientNoteService from "../../services/ClientNoteService";
import ClientDebtService from "../../services/ClientDebtService";
import VisitService from "../../services/VisitService";
import VoucherService from "../../services/VoucherService";
import NamedInvoiceService from "../../services/NamedInvoiceService";
import { NamedInvoice } from "../../models/named_invoice";
import NamedInvoiceModal from "../Popups/NamedInvoiceModal";
import {
  openNamedInvoicePdf,
  getInvoiceSendStatus,
  invoiceSendTitle,
} from "../../utils/namedInvoiceUtils";
import NamedInvoiceSendModal from "../Popups/NamedInvoiceSendModal";
import {
  Client,
  ClientNote,
  ClientSpendChart as ClientSpendChartData,
  ClientSummary,
} from "../../models/client";
import { ClientDebt } from "../../models/debt";
import { Visit } from "../../models/visit";
import { VoucherStatus, Voucher } from "../../models/voucher";
import { formatDate } from "../../utils/dateUtils";
import googleIcon from "../../assets/google.png";
import booksyIcon from "../../assets/booksy.png";
import { AlertType } from "../../models/alert";
import { useAlert } from "../Alert/AlertProvider";
import { useIsMobile } from "../../hooks/useIsMobile";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatAmount } from "../../utils/formatUtils";

const VOUCHER_STATUS_LABEL: Record<VoucherStatus, string> = {
  [VoucherStatus.ACTIVE]: "Aktywny",
  [VoucherStatus.USED]: "Wykorzystany",
  [VoucherStatus.EXPIRED]: "Wygasły",
};
const VOUCHER_STATUS_CLASS: Record<VoucherStatus, string> = {
  [VoucherStatus.ACTIVE]: "active",
  [VoucherStatus.USED]: "used",
  [VoucherStatus.EXPIRED]: "used",
};

export function ClientDashboard() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useUser();
  const { isCompany, isMaster } = useRoles();
  const clientId = Number(id);
  const isValidId = Number.isInteger(clientId) && clientId > 0;
  const { isCompact } = useBreakpoint();
  const isNarrow500 = useIsMobile(501);

  const canDeleteNote = useCallback(
    (note: ClientNote): boolean =>
      note.createdByUserId === user?.id ||
      isMaster,
    [user?.id, isMaster],
  );

  const [notFound, setNotFound] = useState<boolean>(false);
  const [client, setClient] = useState<Client | null>(null);
  const [summary, setSummary] = useState<ClientSummary | null>(null);
  const [chart, setChart] = useState<ClientSpendChartData | null>(null);
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [visits, setVisits] = useState<Visit[]>([]);
  const [notes, setNotes] = useState<ClientNote[]>([]);
  const [debts, setDebts] = useState<ClientDebt[]>([]);
  const [vouchers, setVouchers] = useState<Voucher[]>([]);
  const [namedInvoices, setNamedInvoices] = useState<NamedInvoice[]>([]);
  const [editInvoiceId, setEditInvoiceId] = useState<number | null>(null);
  const [sendInvoice, setSendInvoice] = useState<NamedInvoice | null>(null);
  const [removeInvoiceId, setRemoveInvoiceId] = useState<number | null>(null);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [visitsLoading, setVisitsLoading] = useState<boolean>(true);
  const [chartLoading, setChartLoading] = useState<boolean>(true);
  const [visitModalOpen, setVisitModalOpen] = useState<boolean>(false);
  const [clientModalOpen, setClientModalOpen] = useState<boolean>(false);
  const [addNote, setAddNote] = useState<boolean>(false);
  const [removeNoteId, setRemoveNoteId] = useState<number | null>(null);
  const [debtSourceVisitId, setDebtSourceVisitId] = useState<number | null>(
    null,
  );
  const [selectedVoucherIdForVisit, setSelectedVoucherIdForVisit] = useState<
    string | number | null
  >(null);
  const [purchaseVisitId, setPurchaseVisitId] = useState<number | null>(null);
  const [highlightVoucherId, setHighlightVoucherId] = useState<number | null>(
    null,
  );
  const { showAlert } = useAlert();

  const fetchClient = useCallback(() => {
    ClientService.getClientById(clientId)
      .then((data) => {
        setClient(data);
        setNotFound(false);
      })
      .catch(() => {
        setClient(null);
        setNotFound(true);
      });
  }, [clientId]);

  const fetchSummary = useCallback(() => {
    ClientService.getClientSummary(clientId)
      .then(setSummary)
      .catch(() => setSummary(null));
  }, [clientId]);

  const fetchNotes = useCallback(() => {
    ClientNoteService.getClientNotesByClientId(clientId)
      .then((data) => setNotes(Array.isArray(data) ? data : []))
      .catch(() => setNotes([]));
  }, [clientId]);

  const fetchVouchers = useCallback(() => {
    VoucherService.getVouchersByClientId(clientId)
      .then((data) => setVouchers(Array.isArray(data) ? data : []))
      .catch(() => setVouchers([]));
  }, [clientId]);

  const fetchNamedInvoices = useCallback(() => {
    if (!isMaster) return;
    NamedInvoiceService.getByClient(clientId, 0, 30)
      .then((data) => setNamedInvoices(data?.content ?? []))
      .catch(() => setNamedInvoices([]));
  }, [clientId, isMaster]);

  const fetchDebts = useCallback(() => {
    ClientDebtService.getUnpaidDebtsByClientId(clientId)
      .then((data) => setDebts(Array.isArray(data) ? data : []))
      .catch(() => setDebts([]));
  }, [clientId]);

  const fetchChart = useCallback(
    (selectedYear: number) => {
      setChartLoading(true);
      ClientService.getClientSpendChart(clientId, selectedYear)
        .then(setChart)
        .catch(() => setChart(null))
        .finally(() => setChartLoading(false));
    },
    [clientId],
  );

  const fetchVisits = useCallback(
    (pageNum: number = 0, append: boolean = false) => {
      setVisitsLoading(true);
      VisitService.getVisitsByClientId(clientId, pageNum, 30)
        .then((data) => {
          const content = data?.content ?? [];
          setVisits((prev) => (append ? [...prev, ...content] : content));
          setHasMore(!data.last);
          setPage(pageNum);
        })
        .catch(() => {
          if (!append) setVisits([]);
          setHasMore(false);
        })
        .finally(() => setVisitsLoading(false));
    },
    [clientId],
  );

  // Wizyta niesie namedInvoiceId, ktore steruje ikona faktury na liscie -
  // kazda zmiana po stronie faktur musi odswiezyc rowniez wizyty.
  const refreshInvoiceData = useCallback(() => {
    fetchNamedInvoices();
    fetchVisits(0, false);
  }, [fetchNamedInvoices, fetchVisits]);

  const handleInvoiceRemove = useCallback(async (): Promise<void> => {
    if (removeInvoiceId === null) return;
    try {
      await NamedInvoiceService.delete(removeInvoiceId);
      showAlert("Faktura imienna usunięta!", AlertType.SUCCESS);
      setRemoveInvoiceId(null);
      refreshInvoiceData();
    } catch {
      showAlert("Błąd usuwania faktury!", AlertType.ERROR);
    }
  }, [removeInvoiceId, refreshInvoiceData, showAlert]);

  const handleNoteRemove = useCallback(async (): Promise<void> => {
    try {
      if (removeNoteId) {
        await ClientNoteService.removeClientNote(removeNoteId);
        showAlert("Pomyślnie usunięto Notatkę!", AlertType.SUCCESS);
        setRemoveNoteId(null);
        fetchNotes();
      }
    } catch (error) {
      showAlert("Błąd usuwania Notatki!", AlertType.ERROR);
    }
  }, [removeNoteId]);

  useEffect(() => {
    if (!isValidId) {
      setNotFound(true);
      return;
    }
    fetchClient();
    fetchSummary();
    fetchNotes();
    fetchDebts();
    fetchVisits(0, false);
    fetchVouchers();
    fetchNamedInvoices();
  }, [
    isValidId,
    fetchClient,
    fetchSummary,
    fetchNotes,
    fetchDebts,
    fetchVisits,
    fetchVouchers,
    fetchNamedInvoices,
  ]);

  useEffect(() => {
    if (!isValidId) return;
    fetchChart(year);
  }, [isValidId, year, fetchChart]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !visitsLoading) {
        fetchVisits(page + 1, true);
      }
    },
    [hasMore, visitsLoading, page, fetchVisits],
  );

  const handleGoBack = useCallback(() => {
    if (window.history.length > 1) navigate(-1);
    else navigate("/clients");
  }, [navigate]);

  const initials = client
    ? `${client.firstName?.[0] ?? ""}${client.lastName?.[0] ?? ""}`.toUpperCase()
    : "";

  const canGoBack = chart ? year > chart.firstYear : false;
  const canGoForward = chart ? year < chart.lastYear : false;
  // "Ulica X/X, XX-XXX Miasto" - kazdy czlon opcjonalny, przecinek tylko gdy
  // jest co rozdzielac.
  const addressLine = [
    client?.address,
    [client?.postalCode, client?.city].filter(Boolean).join(" "),
  ]
    .filter((part) => part && part.trim() !== "")
    .join(", ");

  const noRail =
    !notes?.length &&
    !debts?.length &&
    !vouchers?.length &&
    !namedInvoices?.length;

  if (notFound) {
    return (
      <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
        <div className="dashboard-panel width-max height-max flex-column align-items-center">
          <NavigationBar showSearchbar={false} />
          <section className="pd-missing flex-column align-items-center justify-center g-1 f-1">
            <UserRound className="pd-missing-icon pc-y" />
            <span className="pd-missing-title">Nie znaleziono klienta</span>
            <span className="dl-dim">
              Klient z ID #{id} nie istnieje lub został usunięty.
            </span>
            <ActionButton text="Powrót" iconTitle="Wróć" onClick={handleGoBack}>
              <ArrowLeft className="dl-icon-1 pc-y" />
            </ActionButton>
          </section>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <div className="cl-app width-93 flex-column g-1 mt-1 mb-2">
          <span className="cl-back pointer" onClick={handleGoBack}>
            <ArrowLeft className="dl-icon" />
            Powrót
          </span>

          <div className="cl-card cl-identity flex">
            <div className="cl-avatar flex align-items-center justify-center">
              {initials}
            </div>
            <div className="cl-id-main flex-column g-05">
              <div className="cl-id-name-row flex align-items-center">
                <span className="cl-id-name">
                  {client ? `${client.firstName} ${client.lastName}` : "-"}
                </span>
                <div className="cl-id-contact flex align-items-center">
                  {client?.phoneNumber && (
                    <span className="cl-contact">
                      <Phone className="dl-icon" />
                      {client.phoneNumber}
                    </span>
                  )}
                  {client?.email && (
                    <span className="cl-contact">
                      <Mail className="dl-icon" />
                      {client.email}
                    </span>
                  )}
                  {isMaster && addressLine && (
                    <span
                      className="cl-contact"
                      title="Adres do faktur imiennych"
                    >
                      <MapPin className="dl-icon" />
                      {addressLine}
                    </span>
                  )}
                </div>
              </div>

              <div className="cl-badges">
                {client?.isDeleted && (
                  <span className="cl-badge danger" title="Klient usunięty">
                    <UserX className="dl-icon-1 c-red" />
                    {!isCompact && "Klient usunięty"}
                  </span>
                )}
                {client?.boostClient && (
                  <span className="cl-badge boost" title="Boost">
                    <Zap className="dl-icon-1 c-boost" />
                    {!isCompact && "Boost"}
                  </span>
                )}
                {client?.discount && (
                  <span className="cl-badge vip">
                    <Percent className="dl-icon-1 c-green" />
                    {client.discount.name} -{client.discount.percentageValue}%
                  </span>
                )}
                {client?.redFlag && (
                  <span className="cl-badge flag" title="Red flag">
                    <Flag className="dl-icon-1 c-red" />
                    {!isCompact && "Red flag"}
                  </span>
                )}
                {client && (
                  <span
                    className={`cl-badge ${client.signedRegulations ? "ok" : "danger"}`}
                  >
                    {client.signedRegulations ? (
                      <Check className="dl-icon-1 c-green" />
                    ) : (
                      <TriangleAlert className="dl-icon-1 c-red" />
                    )}
                    {isCompact
                      ? "Regulamin"
                      : client.signedRegulations
                        ? "Regulamin podpisany"
                        : "Regulamin niepodpisany"}
                  </span>
                )}
              </div>

              {summary &&
                (() => {
                  const facts: React.ReactNode[] = [];
                  if (summary.hasDebts) {
                    facts.push(
                      <span className="cl-fact debt" key="debt">
                        <HandCoins className="dl-icon-1 c-f-red" />
                        {!isCompact && "Zadłużony "}
                        <b>{formatAmount(summary.debtTotal)} zł</b>
                      </span>,
                    );
                  }
                  if (summary.activeVouchersCount > 0) {
                    facts.push(
                      <span className="cl-fact" key="vouchers" title="Aktywne vouchery">
                        <Ticket className="dl-icon" />
                        <b className="vou">{summary.activeVouchersCount}</b>
                        {!isCompact && "aktywne vouchery"}
                      </span>,
                    );
                  }
                  if (summary.googleReview?.present) {
                    facts.push(
                      <span className="cl-fact align-items-center" key="google" title="Opinia Google">
                        <img
                          src={googleIcon}
                          alt="Google"
                          className="cl-rev-icon"
                        />
                        {!isNarrow500 && (isCompact ? "Opinia" : "Opinia Google")}
                        <span
                          className={`cl-vstat ${summary.googleReview.rewardUsed ? "used" : "active"}`}
                          title={
                            summary.googleReview.rewardUsed
                              ? "Rabat zrealizowany"
                              : "Rabat aktywny"
                          }
                        >
                          {summary.googleReview.rewardUsed
                            ? "Zrealizowany"
                            : "Aktywny"}
                        </span>
                      </span>,
                    );
                  }
                  if (summary.booksyReview?.present) {
                    facts.push(
                      <span className="cl-fact align-items-center" key="booksy" title="Opinia Booksy">
                        <img
                          src={booksyIcon}
                          alt="Booksy"
                          className="cl-rev-icon booksy"
                        />
                        {!isNarrow500 && (isCompact ? "Opinia" : "Opinia Booksy")}
                      </span>,
                    );
                  }
                  if (facts.length === 0) return null;
                  return (
                    <div className="cl-facts">
                      {facts.map((fact, i) => (
                        <React.Fragment key={i}>
                          {i > 0 && <span className="cl-fact-sep" />}
                          {fact}
                        </React.Fragment>
                      ))}
                    </div>
                  );
                })()}
            </div>

            <div className="cl-id-actions">
              {!client?.isDeleted && (
                <ActionButton
                  text="Nowa wizyta"
                  iconTitle="Nowa wizyta"
                  disableText={isCompact}
                  className="cl-id-btn"
                  onClick={() => setVisitModalOpen(true)}
                >
                  <CalendarPlus className="dl-icon-1 c-green" />
                </ActionButton>
              )}
              {(isMaster || !client?.isDeleted) && (
                <ActionButton
                  text="Karta klienta"
                  iconTitle="Zobacz kartę klienta"
                  disableText={isCompact}
                  className="cl-id-btn"
                  onClick={() => navigate(`/client-cards/${clientId}`)}
                >
                  <ClipboardList className="dl-icon-1 pc-y" />
                </ActionButton>
              )}
              {(isMaster || !client?.isDeleted) && (
                <ActionButton
                  text="Edycja"
                  iconTitle="Edycja"
                  disableText={isCompact}
                  className="cl-id-btn"
                  onClick={() => setClientModalOpen(true)}
                >
                  <SquarePen className="dl-icon-1 c-blue" />
                </ActionButton>
              )}
            </div>
          </div>

          <div className={`cl-kpis ${isCompany ? "" : "no-spent"}`}>
            <div className="cl-kpi">
              <span className="cl-kpi-label">Wizyty</span>
              <span className="cl-kpi-value tnum pc-y">
                {summary?.visitsCount ?? 0}
              </span>
            </div>
            {isCompany && (
              <div className="cl-kpi">
                <span className="cl-kpi-label">Wydane łącznie</span>
                <span className="cl-kpi-value green tnum">
                  {formatAmount(summary?.totalSpent)} zł
                </span>
              </div>
            )}
            <div className="cl-kpi">
              <span className="cl-kpi-label">Nieobecności</span>
              <span className="cl-kpi-value tnum c-f-red">
                {summary?.absencesCount ?? 0}
              </span>
            </div>
            <div className="cl-kpi">
              <span className="cl-kpi-label">Spóźnienia</span>
              <span className="cl-kpi-value tnum pc-y">
                {summary?.latenessCount ?? 0}
              </span>
            </div>
            <div className="cl-kpi wide">
              <span className="cl-kpi-label">Najchętniej kupowane</span>
              <div className="cl-fav">
                <span className="cl-fav-row">
                  <span
                    className="cl-dot"
                    style={{ backgroundColor: "#8f76e6" }}
                  />
                  <span className="cl-fav-name">
                    {summary?.topService?.name ?? "-"}
                  </span>
                  {summary?.topService && (
                    <span className="cl-fav-qty">
                      ×{summary.topService.quantity}
                    </span>
                  )}
                </span>
                {summary?.topProduct && (
                  <span className="cl-fav-row">
                    <span
                      className="cl-dot"
                      style={{ backgroundColor: "#d9a531" }}
                    />
                    <span className="cl-fav-name">
                      {summary.topProduct.name}
                    </span>
                    <span className="cl-fav-qty">
                      ×{summary.topProduct.quantity}
                    </span>
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className={`cl-cols ${noRail ? "no-rail" : ""}`}>
            <div className="cl-main flex-column g-1">
              <div className="cl-card cl-chart-card">
                <div className="cl-panel-head">
                  <span className="cl-panel-title pc-y">Wizyty klienta</span>
                  <div className="pd-year-nav flex align-items-center">
                    <ActionButton
                      iconTitle="Poprzedni rok"
                      disableText={true}
                      disableBorder={true}
                      disabled={!canGoBack}
                      onClick={() => setYear((y) => y - 1)}
                    >
                      <ChevronLeft className="dl-icon-1" />
                    </ActionButton>
                    <span className="pd-year tnum">{year}</span>
                    <ActionButton
                      iconTitle="Następny rok"
                      disableText={true}
                      disableBorder={true}
                      disabled={!canGoForward}
                      onClick={() => setYear((y) => y + 1)}
                    >
                      <ChevronRight className="dl-icon-1" />
                    </ActionButton>
                  </div>
                </div>
                <ClientSpendChart
                  data={chart}
                  loading={chartLoading}
                  showAmounts={isCompany}
                />
              </div>

              <div className="cl-card cl-history-card">
                <div className="cl-panel-head">
                  <span className="cl-panel-title pc-y">Historia wizyt</span>
                  <span className="cl-panel-sub">
                    {summary?.visitsCount ?? visits.length}
                  </span>
                </div>
                <ClientVisitList
                  visits={visits}
                  onScroll={handleScroll}
                  isLoading={visitsLoading}
                  onInvoiceCreated={refreshInvoiceData}
                />
              </div>
            </div>

            {!noRail && (
              <div className="cl-rail flex-column g-1">
                {notes && notes.length > 0 && (
                  <div className="cl-card cl-notes-card">
                    <div className="cl-panel-head">
                      <span className="cl-panel-title">Notatki</span>
                      {!client?.isDeleted && (
                        <ActionButton
                          text={isCompact ? "Notatka" : "Dodaj notatkę"}
                          iconTitle="Dodaj notatkę"
                          className="cl-btn-sm"
                          onClick={() => {
                            setAddNote(true);
                            setClientModalOpen(true);
                          }}
                        >
                          <CirclePlus className="dl-icon c-green" />
                        </ActionButton>
                      )}
                    </div>
                    <div className="cl-rail-body">
                      {notes.length === 0 && (
                        <span className="cl-empty mb-025">Brak notatek</span>
                      )}
                      {notes.map((note) => (
                        <div
                          className="cl-note flex space-between align-items-center"
                          key={note.id}
                        >
                          <div className="cl-note-header">
                            <span className="cl-note-text">{note.content}</span>
                            <span className="cl-note-meta">
                              {note.createdBy != null &&
                              note.createdByUserId != null
                                ? `${note.createdBy?.name ?? ""}`.trim()
                                : "System"}{" "}
                              · {formatDate(note.createdAt)}
                            </span>
                          </div>
                          {canDeleteNote(note) && (
                            <ActionButton
                              iconTitle="Usuń notatkę"
                              disableText={true}
                              disableBorder={true}
                              onClick={() => {
                                setRemoveNoteId(note.id);
                              }}
                            >
                              <Trash2 className="dl-icon c-red" />
                            </ActionButton>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {debts && debts.length > 0 && (
                  <div className="cl-card cl-debts-card">
                    <div className="cl-panel-head">
                      <span className="cl-panel-title">Zadłużenia</span>
                      <span className="cl-panel-sub">{debts.length}</span>
                    </div>
                    <div className="cl-rail-body">
                      {debts.length === 0 && (
                        <span className="cl-empty mb-025">Brak zadłużeń</span>
                      )}
                      {debts.map((debt) => (
                        <div className="cl-debt-row" key={debt.id}>
                          <span className="cl-debt-l">
                            {debt.sourceVisit && debt.sourceVisit.absence
                              ? `Nieobecność`
                              : "Zadłużenie"}
                            <span className="cl-sub">
                              {debt.createdAt ? formatDate(debt.createdAt) : ""}
                            </span>
                          </span>
                          <div className="cl-debt-r flex align-items-center g-05">
                            <span className="cl-debt-amt tnum">
                              {formatAmount(debt.value)} zł
                            </span>
                            {debt.sourceVisit != null && (
                              <ActionButton
                                iconTitle={"Pokaż Wizytę - źródło długu"}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setDebtSourceVisitId(debt.sourceVisit!.id);
                                }}
                                disableText={true}
                                disableBorder={true}
                              >
                                <Eye className="dl-icon" />
                              </ActionButton>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {vouchers.length > 0 && (
                  <div className="cl-card cl-vouchers-card">
                    <div className="cl-panel-head">
                      <span className="cl-panel-title">Vouchery</span>
                      <span className="cl-panel-sub">{vouchers.length}</span>
                    </div>
                    <div className="cl-rail-body">
                      {vouchers.map((voucher) => (
                        <div className="cl-vou-row" key={voucher.id}>
                          <span className="cl-vou-l">
                            <span className="cl-vou-name">
                              Voucher #{voucher.id}
                              <span
                                className={`cl-vstat ${VOUCHER_STATUS_CLASS[voucher.status]}`}
                              >
                                {VOUCHER_STATUS_LABEL[voucher.status]}
                              </span>
                            </span>
                            <span className="cl-sub">
                              ważny do {formatDate(voucher.expiryDate)}
                            </span>
                          </span>
                          <div className="cl-vou-r flex align-items-center g-05">
                            <span
                              className={`cl-vou-val tnum ${voucher.status === VoucherStatus.ACTIVE ? "active" : ""}`}
                            >
                              {formatAmount(voucher.value)} zł
                            </span>
                            {voucher.status != VoucherStatus.EXPIRED && (
                              <ActionButton
                                iconTitle={`Pokaż Wizytę ${voucher.status === VoucherStatus.USED ? "realizacji Vouchera" : "zakupu Vouchera"}`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setHighlightVoucherId(voucher.id);
                                  if (voucher.status === VoucherStatus.USED) {
                                    setSelectedVoucherIdForVisit(voucher.id);
                                  } else if (voucher.purchaseVisitId) {
                                    setPurchaseVisitId(voucher.purchaseVisitId);
                                  }
                                }}
                                disableText={true}
                                disableBorder={true}
                              >
                                <Eye className="dl-icon" />
                              </ActionButton>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {isMaster && namedInvoices.length > 0 && (
                  <div className="cl-card cl-invoices-card">
                    <div className="cl-panel-head">
                      <span className="cl-panel-title">Faktury imienne</span>
                      <span className="cl-panel-sub">
                        {namedInvoices.length}
                      </span>
                    </div>
                    <div className="cl-rail-body">
                      {namedInvoices.map((invoice) => (
                        <div className="cl-vou-row" key={invoice.id}>
                          <ActionButton
                            iconTitle={invoiceSendTitle(invoice)}
                            onClick={(e) => {
                              e.stopPropagation();
                              setSendInvoice(invoice);
                            }}
                            disableText={true}
                            disableBorder={true}
                            className="cl-inv-send"
                          >
                            {(() => {
                              const status = getInvoiceSendStatus(invoice);
                              if (status === "SENT")
                                return (
                                  <MailCheck className="dl-icon c-green" />
                                );
                              if (status === "STALE")
                                return <MailWarning className="dl-icon pc-y" />;
                              return <Mail className="dl-icon" />;
                            })()}
                          </ActionButton>
                          <span className="cl-vou-l">
                            <span className="cl-vou-name">
                              {invoice.invoiceNumber}
                            </span>
                            <span className="cl-sub">
                              {formatDate(invoice.issueDate)}
                            </span>
                          </span>
                          <div className="cl-vou-r flex align-items-center g-05">
                            <span className="cl-vou-val tnum">
                              {formatAmount(invoice.grossAmount)} zł
                            </span>
                            <RowActionMenu
                              items={[
                                {
                                  label: "Podgląd PDF",
                                  icon: <FileText className="dl-icon pc-y" />,
                                  onClick: async () => {
                                    const ok = await openNamedInvoicePdf(
                                      invoice.id,
                                      invoice.invoiceNumber,
                                    );
                                    if (!ok) {
                                      showAlert(
                                        "Błąd generowania PDF.",
                                        AlertType.ERROR,
                                      );
                                    }
                                  },
                                },
                                {
                                  label: "Edytuj fakturę",
                                  icon: <SquarePen className="dl-icon c-blue" />,
                                  onClick: () => setEditInvoiceId(invoice.id),
                                },
                                {
                                  label: "Usuń fakturę",
                                  icon: <Trash2 className="dl-icon c-red" />,
                                  onClick: () => setRemoveInvoiceId(invoice.id),
                                  danger: true,
                                },
                              ]}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {visitModalOpen && (
          <VisitModal
            onClose={() => setVisitModalOpen(false)}
            onReset={() => {
              fetchVisits(0, false);
              fetchSummary();
              fetchChart(year);
              fetchDebts();
            }}
            preselectedClientId={clientId}
          />
        )}

        {clientModalOpen && (
          <ClientModal
            selectedClientId={clientId}
            onClose={() => {
              setAddNote(false);
              setClientModalOpen(false);
            }}
            onReset={() => {
              fetchClient();
              fetchSummary();
            }}
            onNotesSaved={fetchNotes}
            autoAddNote={addNote}
          />
        )}
        {removeInvoiceId !== null && (
          <RemoveModal
            onClose={() => setRemoveInvoiceId(null)}
            warningText={"Faktura imienna zostanie trwale usunięta."}
            handleRemove={handleInvoiceRemove}
          />
        )}
        {editInvoiceId !== null && (
          <NamedInvoiceModal
            invoiceId={editInvoiceId}
            onClose={() => setEditInvoiceId(null)}
            onSaved={refreshInvoiceData}
          />
        )}
        {sendInvoice !== null && (
          <NamedInvoiceSendModal
            invoiceId={sendInvoice.id}
            invoice={sendInvoice}
            onClose={() => setSendInvoice(null)}
            onSent={() => fetchNamedInvoices()}
          />
        )}
        {removeNoteId && (
          <RemoveModal
            onClose={() => setRemoveNoteId(null)}
            warningText={"Notatka zostanie usunięta."}
            handleRemove={handleNoteRemove}
          />
        )}
        {debtSourceVisitId !== null && (
          <VisitPreviewModal
            onClose={() => setDebtSourceVisitId(null)}
            selectedSourceVisitIdForVisit={debtSourceVisitId}
          />
        )}
        {selectedVoucherIdForVisit !== null && (
          <VisitPreviewModal
            onClose={() => {
              setSelectedVoucherIdForVisit(null);
              setHighlightVoucherId(null);
            }}
            voucherId={selectedVoucherIdForVisit}
            highlightVoucherId={highlightVoucherId}
          />
        )}
        {purchaseVisitId && (
          <VisitPreviewModal
            onClose={() => {
              setPurchaseVisitId(null);
              setHighlightVoucherId(null);
            }}
            visitId={purchaseVisitId}
            highlightVoucherId={highlightVoucherId}
          />
        )}
      </div>
    </div>
  );
}

export default ClientDashboard;
