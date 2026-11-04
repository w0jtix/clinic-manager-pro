import { Link } from "react-router-dom";
import NavigationBar from "../NavigationBar";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import TextInput from "../TextInput";
import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import {
  Unlock,
  Lock,
  History,
  TriangleAlert,
  ArrowDownToLine,
  Coins,
  HandCoins,
  Wallet,
  Eye,
} from "lucide-react";
import { CashLedger } from "../../models/cash_ledger";
import CashLedgerService from "../../services/CashLedgerService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import CostInput from "../CostInput";
import { formatDate } from "../../utils/dateUtils";
import DataList, { Column } from "../DataList";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import VisitService from "../../services/VisitService";
import { Visit } from "../../models/visit";
import CashLedgerModal from "../Popups/CashLedgerModal";
import { Action } from "../../models/action";
import {
  validateOpenCashLedgerForm,
  validateCashLedgerForm,
} from "../../utils/validators";
import CashLedgerManage from "../Popups/CashLedgerManage";
import { useRoles } from "../../hooks/useRoles";
import RowActionMenu from "../RowActionMenu";
import { useBreakpoint } from "../../hooks/useBreakpoint";

const visitCashAmount = (visit: Visit): number =>
  visit.payments
    .filter((p) => p.method === "CASH")
    .reduce((sum, p) => sum + p.amount, 0);

export function CashLedgerDashboard() {
  const { showAlert } = useAlert();
  const { isMaster } = useRoles();
  const { isMobile, isCompact, isNarrow } = useBreakpoint();
  const [cashLedger, setCashLedger] = useState<CashLedger>({
    id: null,
    date: new Date().toISOString(),
    openingAmount: 0,
    deposit: 0,
    closingAmount: 0,
    cashOutAmount: 0,
    note: null,
  });
  const [baseOpeningAmount, setBaseOpeningAmount] = useState<number>(0);
  const baseOpeningAmountRef = useRef<number>(0);
  const [hasTodayLedger, setHasTodayLedger] = useState<boolean | null>(null);
  const [isOpeningFormVisible, setIsOpeningFormVisible] =
    useState<boolean>(false);
  const [showFillInfo, setShowFillInfo] = useState<boolean>(false);
  const [todayCashVisits, setTodayCashVisits] = useState<Visit[]>([]);
  const totalCashIncome = useMemo(
    () =>
      todayCashVisits
        .flatMap((v) => v.payments)
        .filter((p) => p.method === "CASH")
        .reduce((sum, p) => sum + p.amount, 0),
    [todayCashVisits]
  );
  const calculatedClosingAmount = useMemo(
    () =>
      cashLedger.openingAmount +
      cashLedger.deposit +
      totalCashIncome -
      cashLedger.cashOutAmount,
    [
      cashLedger.openingAmount,
      cashLedger.cashOutAmount,
      totalCashIncome,
      cashLedger.deposit,
    ]
  );
  const closingDiscrepancy = useMemo(
    () =>
      Number(
        ((cashLedger.closingAmount ?? 0) - calculatedClosingAmount).toFixed(2)
      ),
    [cashLedger.closingAmount, calculatedClosingAmount]
  );
  const [isCashLedgerHistoryPopupOpen, setIsCashLedgerHistoryPopupOpen] =
    useState<boolean>(false);
  const [confirmLockPopupOpen, setConfirmLockPopupOpen] =
    useState<boolean>(false);
  const [previewVisitId, setPreviewVisitId] = useState<string | number | null>(
    null
  );

  const fetchLastClosingAmount = async () => {
    CashLedgerService.getLastClosingAmount()
      .then((amount) => {
        const base = Array.isArray(amount) ? 0 : amount;
        baseOpeningAmountRef.current = base;
        setBaseOpeningAmount(base);
        setShowFillInfo(!Array.isArray(amount));
        setCashLedger((prev) => ({ ...prev, openingAmount: base }));
      })
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error fetching last closingAmount.", error);
      });
  };
  const fetchTodayLedger = async () => {
    CashLedgerService.getTodayLedger()
      .then((data) => {
        if (Array.isArray(data)) {
          setHasTodayLedger(false);
          fetchLastClosingAmount();
        } else {
          setCashLedger({ ...data, closingAmount: data.closingAmount ?? 0 });
          setHasTodayLedger(true);
          if (!data.isClosed) {
            fetchCashPaymentVisits(data.date);
          }
        }
      })
      .catch((error) => {
        showAlert("Błąd pobierania stanu dzisiejszej Kasetki.", AlertType.ERROR);
        console.error("Error fetching today Leger.", error);
      });
  };
  const fetchCashPaymentVisits = async (date: string) => {
    VisitService.getVisitsWithCashPaymentByDate(date)
      .then((data) => {
        setTodayCashVisits(data);
        const income = data
          .flatMap((v) => v.payments)
          .filter((p) => p.method === "CASH")
          .reduce((sum, p) => sum + p.amount, 0);
        setCashLedger((prev) => ({
          ...prev,
          closingAmount:
            prev.openingAmount + prev.deposit + income - prev.cashOutAmount,
        }));
      })
      .catch((error) => {
        showAlert("Błąd pobierania Wizyt!", AlertType.ERROR);
        console.error("Error fetching Cash Payment Visits.", error);
      });
  };
  const fetchLastOpenCashLedger = async () => {
    CashLedgerService.getLastOpenCashLedger()
      .then((data) => {
        if (!Array.isArray(data) && data) {
          setCashLedger({ ...data, closingAmount: data.closingAmount ?? 0 });
          setHasTodayLedger(true);
          fetchCashPaymentVisits(data.date);
        } else {
          fetchTodayLedger();
        }
      })
      .catch(() => {
        fetchTodayLedger();
      });
  };

  useEffect(() => {
    fetchLastOpenCashLedger();
  }, []);

  const handleOpeningAmountChange = useCallback(
    (value: number) => {
      setCashLedger((prev) => ({
        ...prev,
        openingAmount: value,
      }));
    },
    [totalCashIncome]
  );
  const handleDepositChange = useCallback((value: number) => {
    setCashLedger((prev) => ({
      ...prev,
      deposit: value,
    }));
  }, []);
  const handleOpenLedger = async () => {
    const error = validateOpenCashLedgerForm(cashLedger);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return null;
    }

    CashLedgerService.openCashLedger(cashLedger)
      .then(() => {
        showAlert("Kasetka została otwarta.", AlertType.SUCCESS);
        fetchCashPaymentVisits(cashLedger.date);
        setIsOpeningFormVisible(false);
        fetchTodayLedger();
      })
      .catch((error) => {
        showAlert("Błąd otwierania Kasetki.", AlertType.ERROR);
        console.error("Error opening CashLedger.", error);
      });
  };

  const handleCashOutAmountChange = useCallback(
    (value: number) => {
      setCashLedger((prev) => ({
        ...prev,
        cashOutAmount: value,
        closingAmount:
          prev.openingAmount + prev.deposit + totalCashIncome - value,
      }));
    },
    [totalCashIncome]
  );

  const handleCloseCashLedger = useCallback(async () => {
    if (cashLedger.id === null) return;
    const error = validateCashLedgerForm(cashLedger, null, Action.CREATE);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return null;
    }
    CashLedgerService.closeCashLedger(cashLedger.id, cashLedger)
      .then(() => {
        showAlert("Zamknięto Kasetkę!", AlertType.SUCCESS);
        setCashLedger({
          id: null,
          date: new Date().toISOString(),
          openingAmount: 0,
          deposit: 0,
          closingAmount: 0,
          cashOutAmount: 0,
          note: null,
        });
        fetchLastOpenCashLedger();
      })
      .catch((error) => {
        showAlert("Błąd zamykania Kasetki!", AlertType.ERROR);
        console.error("Error closing Cash Ledger.", error);
      });
  }, [cashLedger]);

  const isLedgerToday =
    cashLedger.date.slice(0, 10) === new Date().toISOString().slice(0, 10);

  const getLedgerStatus = (): {
    label: string;
    statusClass: string;
    icon: React.ReactNode;
  } => {
    if (hasTodayLedger === null)
      return { label: "", statusClass: "", icon: null };
    if (hasTodayLedger === false)
      return {
        label: "BRAK OTWARTEJ KASETKI",
        statusClass: "used",
        icon: null,
      };
    if (cashLedger.isClosed)
      return {
        label: "ZAMKNIĘTA",
        statusClass: "expired",
        icon: <Lock className="dl-icon-1" />,
      };
    if (!isLedgerToday)
      return {
        label: "NIEZAMKNIĘTA KASETKA",
        statusClass: "onhold",
        icon: <TriangleAlert className="dl-icon-1" />,
      };
    return {
      label: "OTWARTA",
      statusClass: "active",
      icon: <Unlock className="dl-icon-1" />,
    };
  };

  const cashVisitColumns: Column<Visit>[] = [
    {
      key: "employee",
      header: "Pracownik",
      width: "8rem",
      align: "start",
      render: (visit) => <span className="dl-dim">{visit.employee.name}</span>,
    },
    {
      key: "client",
      header: "Klient",
      grow: true,
      render: (visit) => (
        <Link
          to={`/client/${visit.client.id}`}
          className="dl-name pd-product-link"
          title="Otwórz stronę klienta"
          onClick={(e) => e.stopPropagation()}
        >
          {visit.client.firstName} {visit.client.lastName}
        </Link>
      ),
    },
    {
      key: "amount",
      header: "Gotówka",
      width: "7rem",
      align: "end",
      render: (visit) => (
        <span className="clx-income">
          +{visitCashAmount(visit).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "preview",
      header: "",
      width: "3rem",
      align: "center",
      render: (visit) => (
        <ActionButton
          iconTitle="Podgląd Wizyty"
          onClick={(e) => {
            e.stopPropagation();
            setPreviewVisitId(visit.id);
          }}
          disableText={true}
          disableBorder={true}
        >
          <Eye className="dl-icon" />
        </ActionButton>
      ),
    },
  ];

  const mobileCashVisitColumns: Column<Visit>[] = [
    {
      key: "client",
      grow: true,
      render: (visit) => (
        <span className="exp-m-stack flex-column mw-0">
          <Link
            to={`/client/${visit.client.id}`}
            className="dl-name pd-product-link"
            title="Otwórz stronę klienta"
            onClick={(e) => e.stopPropagation()}
          >
            {visit.client.firstName} {visit.client.lastName}
          </Link>
          <span className="dl-dim">{visit.employee.name}</span>
        </span>
      ),
    },
    {
      key: "amount",
      width: "5.5rem",
      align: "end",
      render: (visit) => (
        <span className="clx-income">
          +{visitCashAmount(visit).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "options",
      width: "1.9rem",
      align: "center",
      render: (visit) => (
        <RowActionMenu
          items={[
            {
              label: "Podgląd Wizyty",
              icon: <Eye className="dl-icon" />,
              onClick: () => setPreviewVisitId(visit.id),
            },
          ]}
        />
      ),
    },
  ];

  const status = getLedgerStatus();

  const renderContent = () => {
    if (hasTodayLedger === null) return null;

    if (!hasTodayLedger) {
      if (!isOpeningFormVisible) return null;
      return (
        <Modal
          title="Otwórz Kasetkę"
          onClose={() => setIsOpeningFormVisible(false)}
          size="sm"
          footer={
            <ActionButton
              text="Potwierdź"
              onClick={handleOpenLedger}
              className="modal-button"
            >
            </ActionButton>
          }
        >
          <div className="clx-open-modal flex-column g-1">
            <div className="clx-form-row flex align-items-center space-between">
              <div className="flex-column">
                <span className="clx-form-label">Saldo na otwarcie</span>
                {showFillInfo && (
                  <span className="clx-form-hint italic">
                    Z ostatniej zamkniętej Kasetki
                  </span>
                )}
              </div>
              <div className="flex g-05 align-items-center">
                <CostInput
                  selectedCost={baseOpeningAmount}
                  onChange={handleOpeningAmountChange}
                  className="in-sm"
                />
                <span className="clx-unit">zł</span>
              </div>
            </div>

            <div className="clx-form-row flex align-items-center space-between">
              <span className="clx-form-label">Depozyt</span>
              <div className="flex g-05 align-items-center">
                <CostInput
                  selectedCost={cashLedger.deposit}
                  onChange={handleDepositChange}
                  className="in-sm"
                />
                <span className="clx-unit">zł</span>
              </div>
            </div>

            <div className="clx-form-total flex align-items-center space-between">
              <span className="clx-form-label">Razem</span>
              <span className="clx-total-value">
                {(cashLedger.openingAmount + cashLedger.deposit).toFixed(2)} zł
              </span>
            </div>
          </div>
        </Modal>
      );
    }

    if (cashLedger.isClosed) {
      return (
        <div className="clx-empty align-self-center flex-column align-items-center justify-center g-1 f-1 mt-3">
          <Lock className="clx-empty-icon danger" />
          <h2 className="clx-empty-title">
            Kasetka z dzisiaj została zamknięta
          </h2>
        </div>
      );
    }

    const scoreboard = [
      {
        key: "open",
        icon: <Unlock className="dl-icon-1 pc-y" />,
        label: "Otwarcie",
        value: `${(cashLedger.openingAmount + cashLedger.deposit).toFixed(2)} zł`,
        valueClass: "pc-y",
      },
      {
        key: "deposit",
        icon: <ArrowDownToLine className="dl-icon-1" />,
        label: "W tym depozyt",
        value: `${cashLedger.deposit.toFixed(2)} zł`,
        valueClass: "",
      },
      {
        key: "income",
        icon: <Coins className="dl-icon-1 c-green" />,
        label: "Wizyty",
        value: `+${totalCashIncome.toFixed(2)} zł`,
        valueClass: "c-green",
      },
      {
        key: "cashout",
        icon: <HandCoins className="dl-icon-1 c-red" />,
        label: "Wypłata",
        value: `-${cashLedger.cashOutAmount.toFixed(2)} zł`,
        valueClass: "c-red",
      },
      {
        key: "closing",
        icon: <Wallet className="dl-icon-1 c-blue" />,
        label: "Saldo końcowe",
        value: `${(cashLedger.isClosed
          ? cashLedger.closingAmount ?? 0
          : calculatedClosingAmount
        ).toFixed(2)} zł`,
        valueClass: "c-blue",
      },
    ];

    return (
      <section className="clx-content width-90 flex-column f-1 min-height-0">
        <div className="clx-scoreboard width-max g-1">
          {scoreboard.map((card) => (
            <div key={card.key} className="clx-card flex-column g-05">
              <div className="clx-card-head flex align-items-center g-05">
                {card.icon}
                <span className="clx-card-label">{card.label}</span>
              </div>
              <span className={`clx-card-value ${card.valueClass}`}>
                {card.value}
              </span>
            </div>
          ))}
        </div>

        <div className="clx-core width-max f-1 min-height-0 flex g-2 mt-1 mb-1">
          <div className="clx-visits flex-column min-height-0 f-1">
            <span className="clx-section-title">
              {!isLedgerToday
                ? `Płatności gotówką - ${formatDate(cashLedger.date)}`
                : "Dzisiejsze płatności gotówką"}
            </span>
            <DataList<Visit>
              columns={isMobile ? mobileCashVisitColumns : cashVisitColumns}
              hideHeader={isMobile}
              items={todayCashVisits}
              getRowKey={(visit) => visit.id}
              onRowClick={(visit) => setPreviewVisitId(visit.id)}
              emptyText="Brak wizyt opłaconych gotówką"
              className="clx-list"
            />
          </div>

          <div className="clx-close-panel flex-column min-height-0">
            <div className="clx-form-row flex align-items-center space-between">
              <span className="clx-form-label">Wypłata</span>
              <div className="flex g-05 align-items-center">
                <CostInput
                  selectedCost={cashLedger.cashOutAmount}
                  onChange={handleCashOutAmountChange}
                  className="in-sm"
                />
                <span className="clx-unit">zł</span>
              </div>
            </div>

            <div className="clx-note flex-column min-height-0 g-05">
              <span className="clx-form-label">Notatka</span>
              <TextInput
                placeholder="Notatka stanu Kasetki..."
                multiline={true}
                rows={isNarrow ? 1 : isMobile ? 4 : 8}
                onSelect={(value) =>
                  setCashLedger((prev) => ({
                    ...prev,
                    note: value as string,
                  }))
                }
              />
            </div>

            <ActionButton
              text="Zatwierdź i zamknij"
              onClick={() => {
                if (closingDiscrepancy !== 0 && !cashLedger.note) {
                  showAlert(
                    "Rozbieżność salda wymaga notatki.",
                    AlertType.ERROR
                  );
                  return;
                }
                setConfirmLockPopupOpen(true);
              }}
              className="clx-btn danger align-self-center"
            >
              <Lock className="dl-icon-1 c-red" />
            </ActionButton>
          </div>
        </div>

        {confirmLockPopupOpen && (
          <CashLedgerModal
            onClose={() => setConfirmLockPopupOpen(false)}
            cashLedger={cashLedger}
            handleCloseCashLedger={handleCloseCashLedger}
          />
        )}

        {previewVisitId != null && (
          <VisitPreviewModal
            onClose={() => setPreviewVisitId(null)}
            visitId={previewVisitId}
          />
        )}
      </section>
    );
  };

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />
        <section className="clx-topbar width-90 flex align-self-center space-between align-items-center mt-1 mb-1">
          <div className="clx-topbar-meta flex g-2 align-items-center">
            <div className="clx-meta-item flex g-05 align-items-center">
              <span className="clx-meta-label">Data:</span>
              <span className={`clx-date pc-y ${isLedgerToday ? "" : "c-red"}`}>
                {formatDate(cashLedger.date)}
              </span>
            </div>
            {status.label && (
              <div className="clx-meta-item flex g-05 align-items-center">
                <span className="clx-meta-label">Status:</span>
                <span className={`rv-status ${status.statusClass} flex align-items-center g-05`}>
                  {status.icon}
                  <span className="rv-status-text">{status.label}</span>
                </span>
              </div>
            )}
          </div>

          {isMaster && (
            <ActionButton
              text="Historia Kasetki"
              iconTitle="Historia Kasetki"
              onClick={() => setIsCashLedgerHistoryPopupOpen(true)}
              disableText={isCompact}
              className={`clx-btn ${isCompact ? "clx-icon" : ""}`}
            >
              <History className="dl-icon-1 pc-y" />
            </ActionButton>
          )}
        </section>

        {hasTodayLedger === false && (
          <div className="clx-open-cta width-90 flex justify-center mt-1">
            <ActionButton
              text="Otwórz Kasetkę"
              onClick={() => setIsOpeningFormVisible(true)}
              className="clx-btn accent"
            >
              <Unlock className="dl-icon-1 c-green" />
            </ActionButton>
          </div>
        )}

        {renderContent()}

        {isCashLedgerHistoryPopupOpen && (
          <CashLedgerManage
            onClose={() => setIsCashLedgerHistoryPopupOpen(false)}
          />
        )}
      </div>
    </div>
  );
}

export default CashLedgerDashboard;
