import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { RotateCcw, TriangleAlert, X } from "lucide-react";
import ActionButton from "../ActionButton";
import ClientService from "../../services/ClientService";
import { ClientDuplicateCheck, ClientMatch } from "../../models/client-card";
import { useRoles } from "../../hooks/useRoles";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { formatDate } from "../../utils/dateUtils";
import { buildDuplicateClientMessage, getDuplicateClientError } from "../../utils/clientDuplicateUtils";

const DEBOUNCE_MS = 400;

const REASON_LABELS: Record<ClientMatch["matchReason"], string> = {
  EXACT: "Identyczne dane",
  SAME_NAME: "Imię i nazwisko",
  SAME_PHONE: "Ten sam telefon",
};

export interface DuplicateClientNoticeProps {
  firstName: string;
  lastName: string;
  phoneNumber: string | null;
  excludeId?: number | null;
  onUseClient?: (clientId: number) => void;
  onExactMatchChange?: (match: ClientMatch | null) => void;
  dismissible?: boolean;
  className?: string;
}

export function DuplicateClientNotice({
  firstName,
  lastName,
  phoneNumber,
  excludeId = null,
  onUseClient,
  onExactMatchChange,
  dismissible = false,
  className = "",
}: DuplicateClientNoticeProps) {
  const [result, setResult] = useState<ClientDuplicateCheck | null>(null);
  const [dismissedKey, setDismissedKey] = useState<string | null>(null);
  const [restoring, setRestoring] = useState<boolean>(false);
  const { isMaster } = useRoles();
  const { showAlert } = useAlert();

  useEffect(() => {
    const first = firstName.trim();
    const last = lastName.trim();
    if (first.length < 2 || last.length < 2) {
      setResult(null);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      ClientService.checkDuplicates(first, last, phoneNumber, excludeId)
        .then((data) => {
          if (!cancelled) setResult(data ?? null);
        })
        .catch(() => {
          if (!cancelled) setResult(null);
        });
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [firstName, lastName, phoneNumber, excludeId]);

  const exact = result?.exactMatch ?? null;

  useEffect(() => {
    onExactMatchChange?.(exact);
  }, [exact?.id]);

  const handleRestore = async (match: ClientMatch) => {
    if (restoring) return;
    setRestoring(true);
    try {
      await ClientService.restoreClient(match.id, {
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        phoneNumber,
      });
      if (isMaster) showAlert(`Klient ${match.firstName} ${match.lastName} przywrócony!`, AlertType.SUCCESS);
      onUseClient?.(match.id);
    } catch (error) {
      const duplicate = getDuplicateClientError(error);
      showAlert(
        duplicate ? buildDuplicateClientMessage(duplicate, isMaster) : "Nie udało się otworzyć klienta.",
        AlertType.ERROR
      );
    } finally {
      setRestoring(false);
    }
  };

  const isStrongMatch = (match: ClientMatch) =>
    !(match.isDeleted && isMaster) && match.matchReason !== "SAME_PHONE";

  const renderUseAction = (match: ClientMatch) => {
    const pickClass = isStrongMatch(match) ? "dup-pick" : "";
    if (match.isDeleted) {
      if (!onUseClient) return null;
      if (isMaster) {
        return (
          <ActionButton
            text="Przywróć"
            iconTitle="Przywróć klienta"
            onClick={() => handleRestore(match)}
            disabled={restoring}
            className="dup-restore"
          >
            <RotateCcw className="dl-icon-1 c-red" />
          </ActionButton>
        );
      }
      return (
        <ActionButton
          text="To ten klient"
          onClick={() => handleRestore(match)}
          disabled={restoring}
          className={pickClass}
        />
      );
    }
    if (onUseClient) {
      return (
        <ActionButton
          text="To ten klient"
          onClick={() => onUseClient(match.id)}
          className={pickClass}
        />
      );
    }
    return (
      <Link to={`/client/${match.id}`} className="dup-link" target="_blank" rel="noopener noreferrer">
        Zobacz klienta
      </Link>
    );
  };

  const similar = result?.similar ?? [];
  if (!exact && similar.length === 0) return null;

  const matchesKey = [exact?.id, ...similar.map((match) => match.id)].join(",");
  if (dismissible && dismissedKey === matchesKey) return null;

  const closeButton = dismissible && (
    <ActionButton
      iconTitle="Zamknij"
      disableText
      disableBorder
      className="dup-close"
      onClick={() => setDismissedKey(matchesKey)}
    >
      <X className="dl-icon" />
    </ActionButton>
  );

  const renderRow = (match: ClientMatch) => {
    const deleted = match.isDeleted && isMaster;
    const fields = [
      { label: "Data ur.", value: match.birthDate ? formatDate(match.birthDate) : "-" },
      { label: "Telefon", value: match.phoneNumber || "brak" },
      { label: "Ostatnia wizyta", value: match.lastVisitDate ? formatDate(match.lastVisitDate) : "-" },
    ];
    return (
      <div
        key={match.id}
        className={`dup-row ${isStrongMatch(match) ? "strong" : ""} ${deleted ? "deleted" : ""}`}
      >
        <div className="dup-who flex-column g-05 mw-0">
          <span className="flex align-items-center g-05 mw-0">
            <span className="dup-name">
              {match.firstName} {match.lastName}
            </span>
            <span
              className={`dup-reason ${
                deleted ? "deleted" : match.matchReason === "SAME_PHONE" ? "" : "strong"
              }`}
            >
              {deleted ? "Usunięty" : REASON_LABELS[match.matchReason]}
            </span>
          </span>
          <div className="dup-fields">
            {fields.map((field) => (
              <span key={field.label} className="dup-field flex-column mw-0">
                <span className="dup-field-label">{field.label}</span>
                <span className="dup-field-value">{field.value}</span>
              </span>
            ))}
          </div>
        </div>
        {renderUseAction(match)}
      </div>
    );
  };

  const exactHint = exact
    ? onUseClient
      ? !exact.phoneNumber
        ? "Wybierz go albo wyróżnij nowego klienta numerem telefonu lub drugim imieniem."
        : "Wybierz go albo wyróżnij nowego klienta drugim imieniem lub dopiskiem, np. „(syn)”."
      : !exact.phoneNumber
        ? "Wyróżnij klienta numerem telefonu lub drugim imieniem."
        : "Wyróżnij klienta drugim imieniem lub dopiskiem, np. „(syn)”."
    : null;

  return (
    <div className={`dup-notice flex-column g-05 ${className}`}>
      {exact && (
        <div className="dup-exact flex-column">
          <span className="dup-head flex align-items-start g-05">
            <TriangleAlert className="dl-icon-1 pc-y" />
            <span className="flex-column">
              <span>Klient o tych danych już istnieje.</span>
              <span className="dup-head-hint">{exactHint}</span>
            </span>
            {closeButton}
          </span>
          {renderRow(exact)}
        </div>
      )}
      {similar.length > 0 && (
        <div className="dup-similar flex-column">
          <span className="dup-head flex align-items-start g-05">
            <TriangleAlert className="dl-icon-1 pc-y" />
            <span>Podobni klienci w bazie.</span>
            {!exact && closeButton}
          </span>
          <div className="flex-column g-05">{similar.map((match) => renderRow(match))}</div>
        </div>
      )}
    </div>
  );
}

export default DuplicateClientNotice;
