import React, {
  useCallback,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Rocket,
  Crown,
  Clock,
  UserX,
  HandCoins,
  Percent,
  ShoppingBag,
  Eye,
  Trash2,
  SquarePen,
  FileText,
  Mail,
  NotebookPen,
} from "lucide-react";
import { Visit } from "../../models/visit";
import { Employee } from "../../models/employee";
import { PaymentStatus } from "../../models/payment";
import { translatePaymentStatus } from "../../utils/paymentUtils";
import { formatWeekdayDayMonth } from "../../utils/dateUtils";
import { formatCurrency } from "../../utils/formatUtils";
import { ROLE_ORDER } from "../../utils/roles";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import RowActionMenu, { RowActionItem } from "../RowActionMenu";
import NamedInvoiceModal from "../Popups/NamedInvoiceModal";
import NamedInvoiceSendModal from "../Popups/NamedInvoiceSendModal";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import RemoveVisitModal from "../Popups/RemoveVisitModal";
import { openNamedInvoicePdf } from "../../utils/namedInvoiceUtils";
import { AVAILABLE_AVATARS } from "../../constants/avatars";

export interface VisitBoardProps {
  visits: Visit[];
  employees: Employee[];
  selectedEmployees: Employee[] | null;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  handleResetFiltersAndData?: () => void;
}

interface LaneDay {
  date: string;
  visits: Visit[];
}

interface Lane {
  employee: Employee;
  days: LaneDay[];
  count: number;
}

const STATUS_CLASS: Record<PaymentStatus, string> = {
  [PaymentStatus.PAID]: "active",
  [PaymentStatus.PARTIAL]: "onhold",
  [PaymentStatus.UNPAID]: "expired",
};

const roleRank = (employee: Employee): number =>
  employee.accountRole ? ROLE_ORDER.indexOf(employee.accountRole) : ROLE_ORDER.length;

const fullName = (employee: Employee): string =>
  `${employee.name} ${employee.lastName}`;

const sortEmployees = (employees: Employee[]): Employee[] =>
  [...employees].sort(
    (a, b) =>
      roleRank(a) - roleRank(b) ||
      fullName(a).localeCompare(fullName(b), "pl", { sensitivity: "base" }),
  );

const initials = (employee: Employee): string =>
  `${employee.name.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase();

interface VisitCardProps {
  visit: Visit;
  showFinance: boolean;
  showInvoice: boolean;
  menuItems: RowActionItem[];
  onPreview: () => void;
  onOpenInvoice: (namedInvoiceId: number) => void;
  onOpenTherapy: () => void;
  entryTabTitle: string;
}

function VisitCard({
  visit,
  showFinance,
  showInvoice,
  menuItems,
  onPreview,
  onOpenInvoice,
  onOpenTherapy,
  entryTabTitle,
}: VisitCardProps) {
  const rowRef = useRef<HTMLDivElement>(null);
  const measureRef = useRef<HTMLSpanElement>(null);
  const flagsRef = useRef<HTMLSpanElement>(null);
  const metaStatusRef = useRef<HTMLSpanElement>(null);
  const endRef = useRef<HTMLSpanElement>(null);
  const [wrapped, setWrapped] = useState<boolean>(false);

  const services = visit.items ?? [];
  const products = visit.sale?.items ?? [];

  const flags: React.ReactNode[] = [];
  if (visit.isBoost)
    flags.push(
      <span key="boost" className="flex" title="Wizyta objęta Boost">
        <Rocket className="dl-icon-1 icon-rocket" />
      </span>,
    );
  if (visit.isVip)
    flags.push(
      <span key="vip" className="flex" title="Wizyta VIP">
        <Crown className="dl-icon-1 c-blue" />
      </span>,
    );
  if (visit.delayTime != null)
    flags.push(
      <span key="delay" className="flex" title="Klient spóźnił się na Wizytę">
        <Clock className="dl-icon-1 icon-delay" />
      </span>,
    );
  if (visit.absence)
    flags.push(
      <span key="absence" className="flex" title="Nieobecność">
        <UserX className="dl-icon-1 c-red" />
      </span>,
    );
  if (visit.debtRedemptions.length > 0)
    flags.push(
      <span key="debt" className="flex" title="Klient spłacił zadłużenie">
        <HandCoins className="dl-icon-1 icon-debt" />
      </span>,
    );
  if (visit.serviceDiscounts.length > 0)
    flags.push(
      <span key="discount" className="flex" title="Wizyta objęta Rabatem">
        <Percent className="dl-icon-1 icon-discount" />
      </span>,
    );
  if (visit.sale != null)
    flags.push(
      <span key="sale" className="flex" title="Klient zakupił Produkty lub Voucher">
        <ShoppingBag className="dl-icon-1 icon-sale" />
      </span>,
    );
  if (showInvoice && visit.namedInvoiceId != null)
    flags.push(
      <span
        key="invoice"
        className="flex pointer"
        title="Wystawiono fakturę imienną - kliknij, aby otworzyć PDF"
        onClick={(e) => {
          e.stopPropagation();
          onOpenInvoice(visit.namedInvoiceId!);
        }}
      >
        <FileText className="dl-icon-1 pc-y" />
      </span>,
    );

  const hasFlags = flags.length > 0;
  const showStatus =
    visit.paymentStatus !== PaymentStatus.PAID ||
    visit.absence ||
    visit.debtRedemptions.length > 0;
  const metaStatus = showFinance && showStatus;
  const hasMeta = hasFlags || metaStatus;

  const status = (ref?: React.Ref<HTMLSpanElement>) => (
    <span ref={ref} className={`rv-status vb-status ${STATUS_CLASS[visit.paymentStatus]}`}>
      <span className="rv-status-text">{translatePaymentStatus(visit.paymentStatus)}</span>
    </span>
  );

  const meta = (
    <>
      {hasFlags && (
        <span ref={flagsRef} className="vb-flags flex align-items-center">
          {flags}
        </span>
      )}
      {metaStatus && status(metaStatusRef)}
    </>
  );

  const measure = useCallback(() => {
    const row = rowRef.current;
    const name = measureRef.current;
    const end = endRef.current;
    if (!row || !name || !end || !hasMeta) {
      setWrapped(false);
      return;
    }
    const gap = parseFloat(getComputedStyle(row).columnGap) || 0;
    const flagsWidth = flagsRef.current?.offsetWidth ?? 0;
    const statusWidth = metaStatus ? metaStatusRef.current?.offsetWidth ?? 0 : 0;
    const metaWidth =
      flagsWidth + statusWidth + (flagsWidth > 0 && statusWidth > 0 ? gap : 0);
    const needed = name.offsetWidth + gap + metaWidth + gap + end.offsetWidth;
    const next = needed > row.clientWidth;
    setWrapped((prev) => (prev === next ? prev : next));
  }, [hasMeta, metaStatus]);

  useLayoutEffect(() => {
    measure();
    const row = rowRef.current;
    if (!row || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => measure());
    observer.observe(row);
    return () => observer.disconnect();
  }, [measure, visit]);

  const clientName = `${visit.client.firstName} ${visit.client.lastName}`;

  return (
    <div className="vb-card-wrap flex-column">
      {visit.missingTherapyEntry && (
        <span className="vb-entry-tab pointer" title={entryTabTitle} onClick={onOpenTherapy}>
          Brak wpisu terapii
        </span>
      )}
    <div
      className={`vb-card flex-column pointer ${visit.missingTherapyEntry ? "with-tab" : ""}`}
      onClick={onPreview}
    >
      <div ref={rowRef} className="vb-row1 flex">
        <span ref={measureRef} className="vb-name vb-name-measure" aria-hidden="true">
          {clientName}
        </span>
        <Link
          to={`/client/${visit.client.id}`}
          className="vb-name pd-product-link"
          title="Otwórz stronę klienta"
          onClick={(e) => e.stopPropagation()}
        >
          {clientName}
        </Link>
        {hasMeta && !wrapped && (
          <span className="vb-meta flex align-items-center">{meta}</span>
        )}
        <span ref={endRef} className="vb-end flex align-items-center">
          {showFinance ? (
            <span className="vb-value nowrap">{formatCurrency(visit.totalValue)}</span>
          ) : (
            showStatus && status()
          )}
          <span className="vb-menu flex" onClick={(e) => e.stopPropagation()}>
            <RowActionMenu items={menuItems} />
          </span>
        </span>
      </div>
      {hasMeta && wrapped && (
        <div className="vb-meta vb-row2 flex align-items-center">{meta}</div>
      )}
      {(services.length > 0 || products.length > 0) && (
          <div className="vb-chips flex-column">
            {services.length > 0 && (
              <div className="vb-chip-grid">
                {services.map((item) => (
                  <span className="vb-chip flex align-items-center" key={`s-${item.id}`} title={item.name}>
                    <span
                      className="vb-dot"
                      style={{
                        backgroundColor: item.service?.category?.color
                          ? `rgb(${item.service.category.color})`
                          : "#8f76e6",
                      }}
                    />
                    <span className="vb-chip-name">{item.name}</span>
                  </span>
                ))}
              </div>
            )}
            {products.length > 0 && (
              <div className="vb-chip-grid">
                {products.map((item) => (
                  <span className="vb-chip flex align-items-center" key={`p-${item.id}`} title={item.name}>
                    <span
                      className="vb-dot"
                      style={{
                        backgroundColor: item.product?.category?.color
                          ? `rgb(${item.product.category.color})`
                          : "#ffffff99",
                      }}
                    />
                    <span className="vb-chip-name">{item.name}</span>
                  </span>
                ))}
              </div>
            )}
          </div>
        )}
    </div>
    </div>
  );
}

export function VisitBoard({
  visits,
  employees,
  selectedEmployees,
  onScroll,
  isLoading = false,
  handleResetFiltersAndData,
}: VisitBoardProps) {
  const { user } = useUser();
  const navigate = useNavigate();
  const { isMaster, isCompany } = useRoles();
  const { isCompact } = useBreakpoint();
  const { showAlert } = useAlert();
  const [invoiceVisitId, setInvoiceVisitId] = useState<number | null>(null);
  const [invoiceId, setInvoiceId] = useState<number | null>(null);
  const [sendInvoiceId, setSendInvoiceId] = useState<number | null>(null);
  const [previewVisitId, setPreviewVisitId] = useState<number | null>(null);
  const [removeVisitId, setRemoveVisitId] = useState<number | null>(null);
  const [activeLaneId, setActiveLaneId] = useState<number | null>(
    () => user?.employee?.id ?? null,
  );


  const showFinance = isCompany;

  const openInvoicePdf = useCallback(
    async (namedInvoiceId: number) => {
      const ok = await openNamedInvoicePdf(namedInvoiceId);
      if (!ok) showAlert("Błąd generowania PDF.", AlertType.ERROR);
    },
    [showAlert],
  );

  const canRemove = (visit: Visit): boolean =>
    visit.createdBy === user?.id || isMaster;

  const canAddEntry = (visit: Visit): boolean =>
    !!visit.missingTherapyEntry &&
    user?.employee?.id != null &&
    user.employee.id === visit.employee.id;

  const openTherapy = (visit: Visit) =>
    navigate(`/client-cards/${visit.client.id}`, {
      state: canAddEntry(visit)
        ? { tab: "therapy", backLabel: "Wizyty", newEntryDate: visit.date }
        : { tab: "therapy", backLabel: "Wizyty", highlightVisitDate: visit.date },
    });

  const menuItemsFor = (visit: Visit): RowActionItem[] => [
    {
      label: "Podgląd wizyty",
      icon: <Eye className="dl-icon" />,
      onClick: () => setPreviewVisitId(visit.id),
    },
    ...(canAddEntry(visit)
      ? [
          {
            label: "Dodaj wpis o wizycie",
            icon: <NotebookPen className="dl-icon pc-y" />,
            onClick: () => openTherapy(visit),
          },
        ]
      : []),
    ...(canRemove(visit)
      ? [
          {
            label: "Usuń wizytę",
            icon: <Trash2 className="dl-icon c-red" />,
            onClick: () => setRemoveVisitId(visit.id),
            danger: true,
            disabled: visit.namedInvoiceId != null,
            title:
              visit.namedInvoiceId != null
                ? "Wystawiono fakturę imienną. Najpierw usuń fakturę."
                : undefined,
          },
        ]
      : []),
    ...(isMaster && !visit.absence
      ? visit.namedInvoiceId != null
        ? [
            {
              label: "Podgląd faktury imiennej",
              icon: <FileText className="dl-icon" />,
              onClick: () => openInvoicePdf(visit.namedInvoiceId!),
            },
            {
              label: "Edytuj fakturę imienną",
              icon: <SquarePen className="dl-icon c-blue" />,
              onClick: () => setInvoiceId(visit.namedInvoiceId!),
            },
            {
              label: "Wyślij fakturę emailem",
              icon: <Mail className="dl-icon" />,
              onClick: () => setSendInvoiceId(visit.namedInvoiceId!),
            },
          ]
        : [
            {
              label: "Utwórz fakturę imienną",
              icon: <FileText className="dl-icon" />,
              onClick: () => setInvoiceVisitId(visit.id),
            },
          ]
      : []),
  ];

  const { lanes, dates } = useMemo(() => {
    const allDates = Array.from(new Set(visits.map((visit) => visit.date))).sort(
      (a, b) => b.localeCompare(a),
    );
    const ownEmployeeId = user?.employee?.id ?? null;
    const base = sortEmployees(
      selectedEmployees && selectedEmployees.length > 0 ? selectedEmployees : employees,
    ).sort(
      (a, b) => Number(b.id === ownEmployeeId) - Number(a.id === ownEmployeeId),
    );
    const baseIds = new Set(base.map((employee) => employee.id));
    const extra: Employee[] = [];
    visits.forEach((visit) => {
      if (!baseIds.has(visit.employee.id)) {
        baseIds.add(visit.employee.id);
        extra.push(visit.employee);
      }
    });
    const builtLanes: Lane[] = [...base, ...sortEmployees(extra)].map((employee) => {
      const own = visits.filter((visit) => visit.employee.id === employee.id);
      const days = allDates.map((date) => ({
        date,
        visits: own.filter((visit) => visit.date === date),
      }));
      return { employee, days, count: own.length };
    });
    return { lanes: builtLanes, dates: allDates };
  }, [visits, employees, selectedEmployees, user?.employee?.id]);

  const duplicatedNames = useMemo(() => {
    const counts = new Map<string, number>();
    lanes.forEach(({ employee }) => {
      const key = employee.name.toLocaleLowerCase("pl");
      counts.set(key, (counts.get(key) ?? 0) + 1);
    });
    return new Set(
      Array.from(counts.entries())
        .filter(([, count]) => count > 1)
        .map(([name]) => name),
    );
  }, [lanes]);

  const laneName = (employee: Employee): string =>
    duplicatedNames.has(employee.name.toLocaleLowerCase("pl"))
      ? fullName(employee)
      : employee.name;

  const renderDay = (day: LaneDay, hideEmpty: boolean) => {
    if (hideEmpty && day.visits.length === 0) return null;
    return (
      <div className="vb-day flex-column" key={day.date}>
        <div className="vb-day-head flex align-items-center">
          <span className="vb-day-date">{formatWeekdayDayMonth(day.date)}</span>
        </div>
        {day.visits.length === 0 ? (
          <div className="vb-empty">Brak wizyt</div>
        ) : (
          day.visits.map((visit) => (
            <VisitCard
              key={visit.id}
              visit={visit}
              showFinance={showFinance}
              showInvoice={isMaster}
              menuItems={menuItemsFor(visit)}
              onPreview={() => setPreviewVisitId(visit.id)}
              onOpenInvoice={openInvoicePdf}
              onOpenTherapy={() => openTherapy(visit)}
              entryTabTitle={canAddEntry(visit) ? "Dodaj wpis o wizycie" : "Pokaż wpisy klienta"}
            />
          ))
        )}
      </div>
    );
  };

  const renderAvatar = (employee: Employee, className: string) => (
    <span className={`vb-avatar ${className} flex align-items-center justify-center`}>
      {employee.avatar && AVAILABLE_AVATARS[employee.avatar] ? (
        <img
          src={AVAILABLE_AVATARS[employee.avatar]}
          alt=""
          className="vb-avatar-img"
        />
      ) : (
        initials(employee)
      )}
    </span>
  );

  const renderLaneHead = (lane: Lane) => (
    <div className="vb-lane-head flex align-items-center">
      {renderAvatar(lane.employee, "")}
      <span className="vb-lane-name mw-0">{laneName(lane.employee)}</span>
    </div>
  );

  const activeLane =
    lanes.find((lane) => lane.employee.id === activeLaneId) ?? lanes[0] ?? null;

  const isEmpty = !isLoading && visits.length === 0;

  return (
    <>
      {isCompact && lanes.length > 0 && (
        <div className="cat-seg-group vb-tabs flex align-items-center width-fit-content g-025">
          {lanes.map((lane) => (
            <button
              type="button"
              key={lane.employee.id}
              className={`cat-seg align-items-center pointer ${
                activeLane?.employee.id === lane.employee.id ? "active" : ""
              }`}
              onClick={() => setActiveLaneId(lane.employee.id)}
            >
              {renderAvatar(lane.employee, "vb-avatar-sm")}
              {laneName(lane.employee)}
            </button>
          ))}
        </div>
      )}
      <div className="vb-scroll scrollbar f-1" onScroll={onScroll}>
        {isEmpty ? (
          <span className="dl-empty flex f-1 align-items-center justify-center">Brak wizyt</span>
        ) : isCompact ? (
          activeLane && (
            <div className="vb-single flex-column">
              {activeLane.days.map((day) => renderDay(day, true))}
              {activeLane.count === 0 && !isLoading && (
                <div className="vb-empty">Brak wizyt</div>
              )}
            </div>
          )
        ) : (
          <div
            className="vb-board"
            style={{
              gridTemplateColumns: `repeat(${lanes.length}, minmax(var(--vb-lane-min), 1fr))`,
              gridTemplateRows: `auto repeat(${dates.length}, auto)`,
            }}
          >
            {lanes.map((lane) => (
              <div
                className="vb-lane"
                key={lane.employee.id}
                style={{ gridRow: `span ${dates.length + 1}` }}
              >
                {renderLaneHead(lane)}
                {lane.days.map((day) => renderDay(day, false))}
              </div>
            ))}
          </div>
        )}
        {isLoading && <span className="dl-loading">Ładowanie...</span>}
      </div>
      {invoiceVisitId != null && (
        <NamedInvoiceModal
          visitId={invoiceVisitId}
          onClose={() => setInvoiceVisitId(null)}
          onSaved={() => handleResetFiltersAndData?.()}
        />
      )}
      {invoiceId != null && (
        <NamedInvoiceModal invoiceId={invoiceId} onClose={() => setInvoiceId(null)} />
      )}
      {sendInvoiceId != null && (
        <NamedInvoiceSendModal
          invoiceId={sendInvoiceId}
          onClose={() => setSendInvoiceId(null)}
        />
      )}
      {previewVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
      {removeVisitId != null && (
        <RemoveVisitModal
          onClose={() => setRemoveVisitId(null)}
          handleResetFiltersAndData={handleResetFiltersAndData!}
          visitId={removeVisitId}
        />
      )}
    </>
  );
}

export default VisitBoard;
