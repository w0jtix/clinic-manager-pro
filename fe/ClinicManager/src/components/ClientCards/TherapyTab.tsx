import { useCallback, useEffect, useRef, useState } from "react";
import { Ban, Eye, History, Plus, RotateCcw, SquarePen, UserX } from "lucide-react";
import ActionButton from "../ActionButton";
import ToggleButton from "../ToggleButton";
import RowActionMenu, { RowActionItem } from "../RowActionMenu";
import Modal from "../Modal";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import TherapyEntryModal, { TherapyEntryEditorMode } from "./TherapyEntryModal";
import ClientCardService from "../../services/ClientCardService";
import VisitService from "../../services/VisitService";
import { PaymentStatus } from "../../models/payment";
import { TherapyEntry, TherapyEntryRevision, TimelineItem, VisitSummary } from "../../models/therapy";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { formatDate } from "../../utils/dateUtils";
import { useCardTheme } from "../../hooks/useCardTheme";
import { useLightPortals } from "../../hooks/useLightPortals";
import {
  ABSENCE_STATUS_LABELS,
  mergeTimelinePages,
  summarizeVisits,
  timelineItemKey,
  todayIso,
} from "../../utils/therapyUtils";

export interface TherapyTabProps {
  clientId: number;
  readOnly: boolean;
  openEntryDate?: string | null;
  highlightVisitDate?: string | null;
  onOpenEntryHandled?: () => void;
}

function VisitChips({ visits, label }: { visits: VisitSummary[]; label?: string }) {
  const { services, products } = summarizeVisits(visits);
  if (services.length === 0 && products.length === 0 && !label) return null;
  return (
    <span className="th-chips flex align-items-center flex-wrap">
      {label && <span className="th-chips-label">{label}</span>}
      {services.map((service) => (
        <span className="cl-chip" key={`s-${service.name}`}>
          <span className="cl-dot" style={{ backgroundColor: service.color ? `rgb(${service.color})` : "#8f76e6" }} />
          <span className="cl-chip-name">{service.name}</span>
        </span>
      ))}
      {products.map((product) => (
        <span className="cl-chip" key={`p-${product.name}`}>
          <span className="cl-dot" style={{ backgroundColor: product.color ? `rgb(${product.color})` : "var(--chip-dot-neutral)" }} />
          <span className="cl-chip-name">
            {product.quantity > 1 ? `${product.name} ×${product.quantity}` : product.name}
          </span>
        </span>
      ))}
    </span>
  );
}

function VisitDetails({ visits }: { visits: VisitSummary[] }) {
  if (visits.length <= 1) return <VisitChips visits={visits} />;
  return (
    <span className="th-visit-groups flex-column">
      {visits.map((visit, index) => (
        <VisitChips
          key={visit.id}
          visits={[visit]}
          label={`Wizyta ${index + 1}${visit.employeeName ? ` (${visit.employeeName})` : ""}:`}
        />
      ))}
    </span>
  );
}

function ClampedContent({ text, expanded, onToggle }: { text: string; expanded: boolean; onToggle: () => void }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [overflowing, setOverflowing] = useState<boolean>(false);

  useEffect(() => {
    const element = ref.current;
    if (!element || expanded) return;
    const measure = () => setOverflowing(element.scrollHeight > element.clientHeight + 1);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [text, expanded]);

  const clickable = expanded || overflowing;
  return (
    <span
      ref={ref}
      className={`th-content ${expanded ? "" : "clamped"} ${clickable ? "pointer" : ""}`}
      title={clickable ? (expanded ? "Kliknij, aby zwinąć" : "Kliknij, aby rozwinąć") : undefined}
      onClick={(e) => {
        if (!clickable) return;
        e.stopPropagation();
        onToggle();
      }}
    >
      {text}
    </span>
  );
}

export function TherapyTab({
  clientId,
  readOnly,
  openEntryDate,
  highlightVisitDate,
  onOpenEntryHandled,
}: TherapyTabProps) {
  const { showAlert } = useAlert();
  const stackedLayout = true;
  const [items, setItems] = useState<TimelineItem[]>([]);
  const [nextBeforeDate, setNextBeforeDate] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [loaded, setLoaded] = useState<boolean>(false);
  const [loadError, setLoadError] = useState<boolean>(false);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [highlightDate] = useState<string | null>(() => openEntryDate ?? highlightVisitDate ?? null);
  const [showVisitsWithoutEntry, setShowVisitsWithoutEntry] = useState<boolean>(() => !!highlightDate);
  const [editor, setEditor] = useState<TherapyEntryEditorMode | null>(null);
  const [previewVisitId, setPreviewVisitId] = useState<number | null>(null);
  const [invalidateTarget, setInvalidateTarget] = useState<{ entry: TherapyEntry; date: string } | null>(null);
  const [invalidating, setInvalidating] = useState<boolean>(false);
  const [historyTarget, setHistoryTarget] = useState<TherapyEntry | null>(null);
  const { theme: cardTheme } = useCardTheme();
  useLightPortals(
    cardTheme === "LIGHT" &&
      (editor !== null || previewVisitId !== null || invalidateTarget !== null || historyTarget !== null)
  );
  const [revisions, setRevisions] = useState<TherapyEntryRevision[] | null>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const loadingRef = useRef<boolean>(false);

  const loadPage = useCallback(
    async (beforeDate: string | null, reset: boolean) => {
      if (loadingRef.current) return;
      loadingRef.current = true;
      setLoading(true);
      try {
        const page = await ClientCardService.getTimeline(clientId, beforeDate);
        setItems((prev) => (reset ? page.items : mergeTimelinePages(prev, page.items)));
        setNextBeforeDate(page.nextBeforeDate);
        setLoadError(false);
        setLoaded(true);
      } catch (error) {
        console.error("Error fetching Therapy timeline:", error);
        setLoadError(true);
      } finally {
        loadingRef.current = false;
        setLoading(false);
      }
    },
    [clientId]
  );

  const reload = useCallback(() => {
    setExpandedKey(null);
    return loadPage(null, true);
  }, [loadPage]);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    if (!openEntryDate) return;
    if (!readOnly) setEditor({ kind: "create", date: openEntryDate });
    onOpenEntryHandled?.();
  }, [openEntryDate]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !nextBeforeDate) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadPage(nextBeforeDate, false);
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [nextBeforeDate, loadPage]);

  const handleSaved = useCallback(() => {
    setEditor(null);
    reload();
  }, [reload]);

  const handleInvalidate = useCallback(async () => {
    if (!invalidateTarget) return;
    setInvalidating(true);
    try {
      await ClientCardService.invalidateTherapyEntry(clientId, invalidateTarget.entry.id);
      showAlert("Wpis oznaczony jako błędny.", AlertType.SUCCESS);
      setInvalidateTarget(null);
      reload();
    } catch (error) {
      console.error("Error invalidating Therapy entry:", error);
      showAlert("Nie udało się oznaczyć wpisu jako błędnego.", AlertType.ERROR);
    } finally {
      setInvalidating(false);
    }
  }, [clientId, invalidateTarget, reload, showAlert]);

  const openHistory = useCallback(
    (entry: TherapyEntry) => {
      setHistoryTarget(entry);
      setRevisions(null);
      ClientCardService.getTherapyEntryRevisions(clientId, entry.id)
        .then(setRevisions)
        .catch(() => {
          showAlert("Nie udało się wczytać historii wpisu.", AlertType.ERROR);
          setHistoryTarget(null);
        });
    },
    [clientId, showAlert]
  );

  const previewIconButtons = (visits: VisitSummary[]) =>
    visits.map((visit) => (
      <ActionButton
        key={visit.id}
        iconTitle={visit.employeeName ? `Podgląd wizyty (${visit.employeeName})` : "Podgląd wizyty"}
        disableText={true}
        disableBorder={true}
        onClick={(e) => {
          e.stopPropagation();
          setPreviewVisitId(visit.id);
        }}
      >
        <Eye className="dl-icon" />
      </ActionButton>
    ));

  const previewMenuItems = (visits: VisitSummary[]): RowActionItem[] =>
    visits.map((visit) => ({
      label: visits.length > 1 && visit.employeeName ? `Podgląd wizyty (${visit.employeeName})` : "Podgląd wizyty",
      icon: <Eye className="dl-icon" />,
      onClick: () => setPreviewVisitId(visit.id),
    }));

  const employeeNames = (visits: VisitSummary[]) =>
    [...new Set(visits.map((visit) => visit.employeeName).filter(Boolean))].join(", ") || "-";

  const dateCells = (date: string, who: string, sub?: React.ReactNode) => (
    <span className="th-date-stack flex-column">
      <span className="th-vdate">{formatDate(date)}</span>
      <span className="th-vsub">{who}</span>
      {sub}
    </span>
  );

  const renderEntry = (item: TimelineItem, entry: TherapyEntry) => {
    const key = timelineItemKey(item);
    const expanded = expandedKey === key;
    const editedLink = entry.edited && (
      <ActionButton
        text="edytowano"
        iconTitle="Pokaż historię zmian"
        disableBorder={true}
        className="th-edited"
        onClick={(e) => {
          e.stopPropagation();
          openHistory(entry);
        }}
      >
        <History className="dl-icon" />
      </ActionButton>
    );
    const toggleExpanded = () => setExpandedKey((prev) => (prev === key ? null : key));
    const canPreview = !entry.invalid && item.visits.length > 0;
    const menuItems: RowActionItem[] = [
      ...(canPreview ? previewMenuItems(item.visits) : []),
      ...(!readOnly && entry.canEdit
        ? [
            {
              label: "Edytuj",
              icon: <SquarePen className="dl-icon c-blue" />,
              onClick: () => setEditor({ kind: "edit", date: item.date, entry }),
            },
            {
              label: "Błędny wpis",
              icon: <Ban className="dl-icon c-red" />,
              danger: true,
              onClick: () => setInvalidateTarget({ entry, date: item.date }),
            },
          ]
        : []),
    ];
    const badge = entry.invalid && <span className="th-badge th-badge-invalid">Błędny wpis</span>;
    const content = entry.invalid ? (
      <ClampedContent text={entry.content} expanded={expanded} onToggle={toggleExpanded} />
    ) : (
      <span className="th-content">{entry.content}</span>
    );
    const actions = (
      <span className="th-vactions flex align-items-center justify-end" onClick={(e) => e.stopPropagation()}>
        {menuItems.length > 0 && <RowActionMenu items={menuItems} />}
      </span>
    );
    const rowClass = `th-vrow th-entry ${canPreview ? "pointer" : ""} ${entry.invalid ? "invalid" : ""} ${
      expanded ? "expanded" : ""
    } ${stackedLayout ? "th-vrow-compact" : ""}`;
    const openPreview = () => {
      if (canPreview) setPreviewVisitId(item.visits[0].id);
    };

    if (stackedLayout) {
      return (
        <div key={key} className={rowClass} onClick={openPreview}>
          <span className="th-vcontent flex-column g-025">
            <span className="th-headline flex align-items-center g-05 flex-wrap">
              <span className="th-vdate">{formatDate(item.date)}</span>
              <span className="th-vsub">{entry.authorName}</span>
              {editedLink}
              {badge}
            </span>
            <VisitDetails visits={item.visits} />
            {content}
          </span>
          {actions}
        </div>
      );
    }

    return (
      <div key={key} className={rowClass} onClick={openPreview}>
        {dateCells(item.date, entry.authorName, editedLink)}
        <span className="th-vcontent flex-column g-025">
          {badge}
          <VisitDetails visits={item.visits} />
          {content}
        </span>
        {actions}
      </div>
    );
  };

  const noEntryBadge = <span className="th-badge th-badge-muted">Wizyta bez wpisu</span>;

  const renderVisit = (item: TimelineItem) => (
    <div
      key={timelineItemKey(item)}
      className={`th-vrow th-visit pointer ${stackedLayout ? "th-vrow-compact" : ""} ${
        highlightDate === item.date ? "th-highlight" : ""
      }`}
      onClick={() =>
        readOnly ? setPreviewVisitId(item.visits[0]?.id ?? null) : setEditor({ kind: "create", date: item.date })
      }
    >
      {!stackedLayout && dateCells(item.date, employeeNames(item.visits))}
      <span className="th-vcontent flex-column g-025">
        {stackedLayout && (
          <span className="th-headline flex align-items-center g-05 flex-wrap">
            <span className="th-vdate">{formatDate(item.date)}</span>
            <span className="th-vsub">{employeeNames(item.visits)}</span>
            {noEntryBadge}
          </span>
        )}
        {!stackedLayout && noEntryBadge}
        <VisitDetails visits={item.visits} />
      </span>
      <span className="th-vactions flex align-items-center justify-end">
        {!readOnly && (
          <ActionButton
            iconTitle="Dodaj wpis"
            disableText={true}
            disableBorder={true}
            onClick={(e) => {
              e.stopPropagation();
              setEditor({ kind: "create", date: item.date });
            }}
          >
            <Plus className="dl-icon pc-y" />
          </ActionButton>
        )}
        {item.visits.length > 1 ? (
          <span onClick={(e) => e.stopPropagation()}>
            <RowActionMenu
              items={previewMenuItems(item.visits)}
              iconTitle="Podgląd wizyty"
              triggerIcon={<Eye className="dl-icon" />}
            />
          </span>
        ) : (
          previewIconButtons(item.visits)
        )}
      </span>
    </div>
  );

  const openSettlementVisit = (absenceVisitId: number) => {
    VisitService.findVisitByDebtSourceVisitId(absenceVisitId)
      .then((settlement) => setPreviewVisitId(settlement.id))
      .catch((error) => {
        if ((error as any)?.response?.status === 404) {
          setPreviewVisitId(absenceVisitId);
        } else {
          console.error("Error fetching settlement visit:", error);
          showAlert("Nie udało się znaleźć wizyty spłaty.", AlertType.ERROR);
        }
      });
  };

  const renderAbsence = (item: TimelineItem) => {
    const visit = item.visits[0];
    if (!visit) return null;
    return (
      <div
        key={timelineItemKey(item)}
        className="th-absence-card pointer flex align-items-center space-between g-075"
        onClick={() => setPreviewVisitId(visit.id)}
      >
        <span className={`flex g-05 mw-0 ${stackedLayout ? "flex-column" : "align-items-center flex-wrap"}`}>
          {stackedLayout ? (
            <span className="th-headline flex align-items-center g-05">
              <span className="th-vdate">{formatDate(item.date)}</span>
              <span className="th-vsub">{visit.employeeName ?? "-"}</span>
            </span>
          ) : (
            dateCells(item.date, visit.employeeName ?? "-")
          )}
          <span className="flex align-items-center g-05 flex-wrap">
          <span className="th-absence-label flex align-items-center g-025">
            <UserX className="dl-icon c-red" />
            Nieobecność
          </span>
          <span
            className={`th-badge th-pay ${visit.paymentStatus.toLowerCase()} ${
              visit.paymentStatus === PaymentStatus.PAID ? "pointer" : ""
            }`}
            title={visit.paymentStatus === PaymentStatus.PAID ? "Pokaż wizytę, na której spłacono nieobecność" : undefined}
            onClick={(e) => {
              if (visit.paymentStatus !== PaymentStatus.PAID) return;
              e.stopPropagation();
              openSettlementVisit(visit.id);
            }}
          >
            {ABSENCE_STATUS_LABELS[visit.paymentStatus]} · {visit.totalValue.toFixed(2)} zł
          </span>
          </span>
        </span>
        <span className="th-vactions flex align-items-center justify-end" onClick={(e) => e.stopPropagation()}>
          <RowActionMenu items={previewMenuItems([visit])} />
        </span>
      </div>
    );
  };

  const visibleItems = showVisitsWithoutEntry ? items : items.filter((item) => item.type !== "VISIT");

  if (loadError && !loaded) {
    return (
      <div className="cc-empty flex-column align-items-center g-05">
        <span>Nie udało się wczytać wpisów.</span>
        <ActionButton text="Spróbuj ponownie" onClick={() => reload()}>
          <RotateCcw className="dl-icon-1" />
        </ActionButton>
      </div>
    );
  }

  return (
    <div className="th-tab flex-column g-075">
      <div className="th-toolbar flex align-items-center space-between g-1 flex-wrap">
        <ToggleButton
          value={showVisitsWithoutEntry}
          onChange={setShowVisitsWithoutEntry}
          suffix="Wizyty bez wpisu"
          className="qf-english-toggle"
        />
        {!readOnly && (
          <ActionButton text="Dodaj wpis" onClick={() => setEditor({ kind: "create", date: todayIso() })}>
            <Plus className="dl-icon-1 pc-y" />
          </ActionButton>
        )}
      </div>

      {loaded && visibleItems.length === 0 && <span className="cq-empty italic">Brak wpisów.</span>}

      <div className="th-list flex-column">
        {visibleItems.map((item) => {
          if (item.type === "ENTRY" && item.entry) return renderEntry(item, item.entry);
          if (item.type === "ABSENCE") return renderAbsence(item);
          return renderVisit(item);
        })}
      </div>

      {nextBeforeDate && <div ref={sentinelRef} className="th-sentinel" />}
      {loading && <span className="cq-empty italic">Wczytywanie…</span>}

      {editor && (
        <TherapyEntryModal
          clientId={clientId}
          mode={editor}
          onClose={() => setEditor(null)}
          onSaved={handleSaved}
          onConflict={() => {
            setEditor(null);
            reload();
          }}
        />
      )}

      {previewVisitId !== null && (
        <VisitPreviewModal visitId={previewVisitId} onClose={() => setPreviewVisitId(null)} />
      )}

      {invalidateTarget && (
        <Modal
          title="Oznaczyć wpis jako błędny?"
          onClose={() => setInvalidateTarget(null)}
          size="sm"
          footer={
            <>
              <ActionButton text="Wróć" onClick={() => setInvalidateTarget(null)} className="cc-back-btn" />
              <ActionButton
                text="Błędny wpis"
                onClick={handleInvalidate}
                disabled={invalidating}
                className="cl-filter cc-discard-btn active"
              >
                <Ban className="dl-icon-1 c-red" />
              </ActionButton>
            </>
          }
        >
          <span>
            Wpis z dnia {formatDate(invalidateTarget.date)} zostanie oznaczony jako błędny.
            Wpisu nie będzie można edytować ani cofnąć oznaczenia.
          </span>
        </Modal>
      )}

      {historyTarget && (
        <Modal title="Historia zmian wpisu" onClose={() => setHistoryTarget(null)} size="md" hideFooter={true}>
          {revisions === null ? (
            <span className="cq-empty italic">Wczytywanie…</span>
          ) : (
            <div className="th-history flex-column g-075">
              <div className="th-history-item current flex-column g-025">
                <span className="th-history-meta">Aktualna wersja · {formatDate(historyTarget.updatedAt ?? historyTarget.createdAt)}</span>
                <p className="th-content">{historyTarget.content}</p>
              </div>
              {revisions.map((revision, index) => (
                <div className="th-history-item flex-column g-025" key={`${revision.changedAt}-${index}`}>
                  <span className="th-history-meta">
                    {revision.changedByName} · {formatDate(revision.previousEntryDate)} 
                  </span>
                  <p className="th-content">{revision.previousContent}</p>
                </div>
              ))}
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}

export default TherapyTab;
