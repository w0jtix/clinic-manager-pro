import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowDown,
  Ban,
  ChevronDown,
  ChevronUp,
  FilePlus,
  LogIn,
  LucideIcon,
  NotebookPen,
  Pencil,
  SquarePen,
  UserPen,
} from "lucide-react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import {
  CARD_HISTORY_ACTION_LABELS,
  CardHistoryAction,
  CardHistoryChange,
  CardHistoryItem,
} from "../../models/client-card";
import ClientCardService from "../../services/ClientCardService";
import { formatDate, formatTimestamp } from "../../utils/dateUtils";
import DeviceTag, { UnknownDeviceTag } from "../DeviceTag";
import { useCardTheme } from "../../hooks/useCardTheme";

const PAGE_SIZE = 30;

const ACTION_STYLES: Partial<Record<CardHistoryAction, { icon: LucideIcon; tone: string }>> = {
  VIEW_CARD: { icon: LogIn, tone: "view" },
  CREATE_CARD: { icon: FilePlus, tone: "create" },
  CREATE_THERAPY_ENTRY: { icon: NotebookPen, tone: "create" },
  UPDATE_CLIENT_DATA: { icon: UserPen, tone: "update" },
  UPDATE_QUESTIONNAIRE: { icon: SquarePen, tone: "update" },
  UPDATE_DRAWING: { icon: Pencil, tone: "update" },
  UPDATE_THERAPY_ENTRY: { icon: SquarePen, tone: "update" },
  INVALIDATE_THERAPY_ENTRY: { icon: Ban, tone: "invalid" },
};

const valueClass = (change: CardHistoryChange, value: string | null, fallback: string): string => {
  if (change.type !== "YES_NO") return fallback;
  return value === "TAK" ? "chm-after" : "chm-before";
};

export interface CardHistoryModalProps {
  clientId: number;
  onClose: () => void;
}

export function CardHistoryModal({ clientId, onClose }: CardHistoryModalProps) {
  const { showAlert } = useAlert();
  const { theme: cardTheme } = useCardTheme();
  const [items, setItems] = useState<CardHistoryItem[]>([]);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(true);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const loadingRef = useRef<boolean>(false);

  const fetchPage = useCallback(
    (pageNum: number) => {
      if (loadingRef.current) return;
      loadingRef.current = true;
      setLoading(true);
      ClientCardService.getHistory(clientId, pageNum, PAGE_SIZE)
        .then((data) => {
          setItems((prev) => (pageNum === 0 ? data.content : [...prev, ...data.content]));
          setHasMore(!data.last);
          setPage(pageNum);
        })
        .catch((error) => {
          console.error("Error fetching card history: ", error);
          showAlert("Błąd pobierania historii karty!", AlertType.ERROR);
        })
        .finally(() => {
          loadingRef.current = false;
          setLoading(false);
        });
    },
    [clientId, showAlert]
  );

  useEffect(() => {
    fetchPage(0);
  }, [fetchPage]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom = target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore) {
        fetchPage(page + 1);
      }
    },
    [hasMore, page, fetchPage]
  );

  return (
    <Modal
      title="Historia karty"
      onClose={onClose}
      size="md"
      height="tall"
      className={cardTheme === "LIGHT" ? "cc-light" : ""}
      hideFooter
      bodyClassName="nested"
    >
      <div className="chm-list flex-column f-1 min-height-0 scrollbar" onScroll={handleScroll}>
        {!loading && items.length === 0 && (
          <span className="dl-dim italic chm-empty">Brak wpisów w historii</span>
        )}
        {items.map((item) => {
          const style = ACTION_STYLES[item.action];
          const Icon = style?.icon;
          const isCreateEntry = item.action === "CREATE_THERAPY_ENTRY";
          const isUpdateEntry = item.action === "UPDATE_THERAPY_ENTRY";
          const isInvalidateEntry = item.action === "INVALIDATE_THERAPY_ENTRY";
          const edited = item.editedEntry;
          const hasChanges = !!item.changes && item.changes.length > 0;
          const expanded = expandedId === item.id;
          return (
            <div key={item.id} className={`chm-row flex-column ${style?.tone ?? ""}`}>
              <div
                className="chm-head flex align-items-center space-between g-1 pointer"
                onClick={() => setExpandedId(expanded ? null : item.id)}
              >
                <span className="chm-main flex-column mw-0">
                  <span className="chm-action flex align-items-center g-05">
                    {Icon && <Icon className="chm-icon" />}
                    {CARD_HISTORY_ACTION_LABELS[item.action] ?? item.action}
                    {item.entryDate && !isCreateEntry && !isUpdateEntry && !isInvalidateEntry && (
                      <span className="chm-entry-date"> z {formatDate(item.entryDate)}</span>
                    )}
                  </span>
                  <span className="chm-meta flex align-items-center g-025 mw-0">
                    <span className="chm-meta">{item.userName ?? "-"}</span>
                    <span className="chm-meta">·</span>
                    {item.deviceName ? (
                      <DeviceTag name={item.deviceName} />
                    ) : (
                      <UnknownDeviceTag />
                    )}
                  </span>
                </span>
                <span className="chm-side flex align-items-center g-05">
                  <span className="chm-time tnum">{formatTimestamp(item.at)}</span>
                  {expanded ? <ChevronUp className="dl-icon" /> : <ChevronDown className="dl-icon" />}
                </span>
              </div>
              {expanded && (
                <div className="chm-details flex-column">
                  {isCreateEntry && item.createdEntry && (
                    <div className="chm-entry flex-column">
                      <span className="chm-change-label chm-entry-date-line">
                        Wpis z dnia {formatDate(item.createdEntry.entryDate)}
                      </span>
                      <span className="chm-entry-content">{item.createdEntry.content}</span>
                    </div>
                  )}
                  {isInvalidateEntry && item.invalidatedEntry && (
                    <div className="chm-entry flex-column">
                      <span className="flex align-items-center space-between g-05">
                        <span className="th-badge th-badge-invalid">Błędny wpis</span>
                        <span className="chm-change-label">
                          Wpis z dnia {formatDate(item.invalidatedEntry.entryDate)}
                        </span>
                      </span>
                      <span className="chm-entry-content old invalid">
                        {item.invalidatedEntry.content}
                      </span>
                    </div>
                  )}
                  {isUpdateEntry && edited && (
                    <div className="chm-entry flex-column">
                      <span className="chm-change-label chm-entry-date-line">
                        Wpis z dnia{" "}
                        {edited.beforeEntryDate !== edited.afterEntryDate && (
                          <>{formatDate(edited.beforeEntryDate)} → </>
                        )}
                        {formatDate(edited.afterEntryDate)}
                      </span>
                      <span className="chm-entry-content old">{edited.beforeContent}</span>
                      <ArrowDown className="chm-entry-arrow" />
                      <span className="chm-entry-content">{edited.afterContent}</span>
                    </div>
                  )}
                  {hasChanges &&
                    item.changes!.map((change, index) => (
                      <div key={`${item.id}-${index}`} className="chm-change">
                        <span className="chm-change-label">{change.label}</span>
                        <span className="chm-change-values">
                          <span className={valueClass(change, change.before, "chm-before")}>
                            {change.before ?? "brak"}
                          </span>
                          {" → "}
                          <span className={valueClass(change, change.after, "chm-after")}>
                            {change.after ?? "brak"}
                          </span>
                        </span>
                      </div>
                    ))}
                  <span className="chm-ip flex justify-end">
                    {item.deviceName ? <DeviceTag name={item.deviceName} /> : `IP: ${item.ipAddress ?? "-"}`}
                  </span>
                </div>
              )}
            </div>
          );
        })}
        {loading && <span className="dl-dim chm-empty">Ładowanie...</span>}
      </div>
    </Modal>
  );
}

export default CardHistoryModal;
