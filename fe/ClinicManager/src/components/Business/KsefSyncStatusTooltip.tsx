import { KsefSyncStatus, KsefSyncStatusType } from "../../models/ksef_invoice";
import { formatTimestamp, MONTHS } from "../../utils/dateUtils";

interface KsefSyncStatusTooltipProps {
  syncStatus: KsefSyncStatus;
}

const STATUS_LABEL: Record<KsefSyncStatusType, string> = {
  [KsefSyncStatusType.NEVER_SYNCED]: "Brak synchronizacji",
  [KsefSyncStatusType.SUCCESS]: "Zsynchronizowano",
  [KsefSyncStatusType.PARTIAL_FAILURE]: "Niepełna synchronizacja",
};

export function KsefSyncStatusTooltip({ syncStatus }: KsefSyncStatusTooltipProps) {
  const { summary } = syncStatus;

  const titleClass =
    syncStatus.status === KsefSyncStatusType.SUCCESS
      ? "c-green"
      : syncStatus.status === KsefSyncStatusType.PARTIAL_FAILURE
        ? "pc-y"
        : "c-red";

  return (
    <div className="kst-tooltip">
      <div className={`kst-title ${titleClass}`}>
        {STATUS_LABEL[syncStatus.status]}
      </div>

      <div className="kst-row">
        <span>Ostatnia synchronizacja</span>
        <span className="kst-value pc-y">
          {syncStatus.lastSyncedAt ? formatTimestamp(syncStatus.lastSyncedAt) : "-"}
        </span>
      </div>
      <div className="kst-row">
        <span>Ostatnia próba</span>
        <span className="kst-value pc-y">
          {syncStatus.lastAttemptAt ? formatTimestamp(syncStatus.lastAttemptAt) : "-"}
        </span>
      </div>
      <div className="kst-row">
        <span>Nowe faktury</span>
        <span className="kst-value c-green">{syncStatus.lastNewCount}</span>
      </div>
      <div className="kst-row">
        <span>Nieudane</span>
        <span className="kst-value c-red">{syncStatus.lastFailedCount}</span>
      </div>

      <div className="kst-divider" />

      <div className="kst-row">
        <span>Wystawione - nowe</span>
        <span className="kst-value c-green">
          {syncStatus.lastIssuedNewCount ?? 0}
        </span>
      </div>
      <div className="kst-row">
        <span>Wystawione - nieudane</span>
        <span className="kst-value c-red">
          {syncStatus.lastIssuedFailedCount ?? 0}
        </span>
      </div>

      {summary && (
        <>
          <div className="kst-divider" />

          <div className="kst-row">
            <span>Pobrano łącznie</span>
            <span className={`kst-value ${summary.fullySynced ? "c-green" : "pc-y"}`}>
              {summary.totalLocal} /{" "}
              {summary.totalKsef !== null ? summary.totalKsef : "-"}
            </span>
          </div>

          {summary.monthsWithMismatch.length > 0 && (
            <>
              <div className="kst-mismatch-header pc-y">Brakujące faktury:</div>
              {summary.monthsWithMismatch.map((m) => {
                const monthName =
                  MONTHS.find((mo) => mo.id === m.month)?.name ?? `${m.month}`;
                return (
                  <div key={`${m.year}-${m.month}`} className="kst-row">
                    <span>
                      {monthName} {m.year}
                    </span>
                    <span className="kst-value c-red">-{m.missing}</span>
                  </div>
                );
              })}
            </>
          )}
        </>
      )}

      {syncStatus.rateLimitHit && (
        <div className="kst-note italic">
          Wyczerpano limit zapytań KSeF, reszta faktur zostanie pobrana przy
          kolejnej synchronizacji. Spróbuj za godzinę.
        </div>
      )}
    </div>
  );
}

export default KsefSyncStatusTooltip;
