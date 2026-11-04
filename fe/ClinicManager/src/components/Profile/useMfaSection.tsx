import { useCallback, useEffect, useState } from "react";
import { ShieldCheck, ShieldOff, Lock, Trash2 } from "lucide-react";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import ActionButton from "../ActionButton";
import MfaSetupModal from "./MfaSetupModal";
import BackupCodesModal from "./BackupCodesModal";
import MfaCodeModal from "./MfaCodeModal";
import RemoveModal from "../Popups/RemoveModal";
import { useAlert } from "../Alert/AlertProvider";
import { useUser } from "../User/UserProvider";
import { AlertType } from "../../models/alert";
import { MfaStatus, TrustedDevice } from "../../models/mfa";
import MfaService from "../../services/MfaService";
import { apiErrorMessage } from "../../utils/apiError";
import { formatDate, formatRelativeDateTime } from "../../utils/dateUtils";

const BACKUP_CODES_TOTAL = 10;

type MfaModal = "setup" | "codes" | "disable" | "regenerate" | null;


export function useMfaSection(active: boolean) {
  const { showAlert } = useAlert();
  const { user } = useUser();
  const { isCompact } = useBreakpoint();
  const [status, setStatus] = useState<MfaStatus | null>(null);
  const [modal, setModal] = useState<MfaModal>(null);
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const [codesSuccessMessage, setCodesSuccessMessage] = useState<string>("");
  const [codesNeedConfirm, setCodesNeedConfirm] = useState<boolean>(false);
  const [revokeTarget, setRevokeTarget] = useState<TrustedDevice | "all" | null>(null);

  const fetchStatus = useCallback(() => {
    MfaService.getStatus()
      .then(setStatus)
      .catch((error) => {
        showAlert(apiErrorMessage(error) ?? "Nie udało się pobrać ustawień weryfikacji.", AlertType.ERROR);
      });
  }, [showAlert]);

  useEffect(() => {
    if (active) {
      fetchStatus();
    } else {
      setStatus(null);
      setModal(null);
    }
  }, [active, fetchStatus]);

  const showCodes = (codes: string[], message: string, needConfirm = false) => {
    setBackupCodes(codes);
    setCodesNeedConfirm(needConfirm);
    setCodesSuccessMessage(message);
    setModal("codes");
  };

  const handleCodesClosed = () => {
    setModal(null);
    setBackupCodes([]);
    showAlert(codesSuccessMessage, AlertType.SUCCESS);
    fetchStatus();
  };

  const handleConfirmCodes = async () => {
    await MfaService.confirmBackupCodes();
  };

  const handleCodesAborted = () => {
    setModal(null);
    setBackupCodes([]);
    fetchStatus();
  };

  const handleDisable = async (code: string) => {
    await MfaService.disable(code);
    setModal(null);
    showAlert("Weryfikacja dwuetapowa została wyłączona.", AlertType.SUCCESS);
    fetchStatus();
  };

  const handleRegenerate = async (code: string) => {
    const response = await MfaService.regenerateBackupCodes(code);
    showCodes(response.backupCodes, "Zapisano nowe kody zapasowe.", true);
  };

  const handleConfirmRevoke = () => {
    const target = revokeTarget;
    setRevokeTarget(null);
    if (target === "all") {
      handleRevokeAll();
    } else if (target) {
      handleRevoke(target);
    }
  };

  const handleRevoke = async (device: TrustedDevice) => {
    try {
      await MfaService.revokeDevice(device.id);
      showAlert("Urządzenie zostało usunięte z zaufanych.", AlertType.SUCCESS);
      fetchStatus();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się usunąć urządzenia.", AlertType.ERROR);
    }
  };

  const handleRevokeAll = async () => {
    try {
      await MfaService.revokeAllDevices();
      showAlert("Usunięto wszystkie zaufane urządzenia.", AlertType.SUCCESS);
      fetchStatus();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się usunąć urządzeń.", AlertType.ERROR);
    }
  };

  if (!active || !status) return { enabled: false, tile: null, devicesPanel: null, modals: null, refresh: fetchStatus };

  const devices = status.trustedDevices ?? [];

  const tile = (
    <section className="pf-panel pf-mfa-panel flex-column g-1">
      <div className="flex align-items-center space-between g-05 flex-wrap">
        <h2 className="pf-panel-title m-0">Weryfikacja dwuetapowa</h2>
        <span className={`dv-pill ag-status flex align-items-center g-025 ${status.enabled ? "ok" : "bad"}`}>
          {status.enabled ? <ShieldCheck className="ag-status-icon" /> : <ShieldOff className="ag-status-icon" />}
          {status.enabled ? "Włączona" : "Wyłączona"}
        </span>
      </div>

      {status.enabled ? (
        <dl className="pf-mfa-kv m-0">
          <dt>Włączona</dt>
          <dd>{status.enabledAt ? formatDate(status.enabledAt) : "-"}</dd>
          <dt>Kody zapasowe</dt>
          <dd>
          {status.remainingBackupCodes} z {BACKUP_CODES_TOTAL}
          <span className="pf-mfa-dots" aria-hidden="true">
            {Array.from({ length: BACKUP_CODES_TOTAL }, (_, i) => (
              <i key={i} className={i < status.remainingBackupCodes ? "" : "used"} />
            ))}
          </span>
        </dd>
          <dt>Zaufane urządzenia</dt>
          <dd>{devices.length}</dd>
        </dl>
      ) : (
        <div className="pf-mfa-hint flex align-items-start g-05">
          <Lock className="dl-icon pc-y" />
          <span>
            Zalecana dla kont z dostępem do danych firmy. Przy logowaniu, oprócz hasła, podasz kod z aplikacji w telefonie.
          </span>
        </div>
      )}

      <div className="flex justify-end g-05 flex-wrap">
        {status.enabled ? (
          <>
            <ActionButton text="Nowe kody zapasowe" disableImg={true} onClick={() => setModal("regenerate")} />
            <ActionButton text="Wyłącz 2FA" disableImg={true} onClick={() => setModal("disable")} className="pf-mfa-off" />
          </>
        ) : (
          <ActionButton text="Włącz 2FA" onClick={() => setModal("setup")} className="ag-btn ag-green">
            <ShieldCheck className="dl-icon-1 c-green" />
          </ActionButton>
        )}
      </div>
    </section>
  );

  const devicesPanel = status.enabled ? (
    <section className="pf-panel pf-devices-panel flex-column g-1">
      <div className="flex align-items-center space-between g-05 flex-wrap">
        <h2 className="pf-panel-title m-0">Zaufane urządzenia</h2>
        {devices.length > 0 && (
          <ActionButton text="Usuń wszystkie" disableImg={true} onClick={() => setRevokeTarget("all")} className="pf-mfa-off" />
        )}
      </div>
      {devices.length === 0 ? (
        <span className="pf-devices-empty">Brak zaufanych urządzeń. Każde logowanie wymaga kodu.</span>
      ) : (
        <div className="pf-devices scrollbar">
          {!isCompact && (
            <div className="pf-devices-head">
              <span>Urządzenie</span>
              <span>Dodane</span>
              <span>Ostatnio użyte</span>
              <span>Wygasa</span>
              <span />
            </div>
          )}
          {devices.map((device) => (
            <div key={device.id} className="pf-devices-row">
              <span className="pf-device-name flex-column mw-0">
                <span className="flex align-items-center g-05 flex-wrap">
                  {device.label}
                  {device.current && <span className="pf-device-tag">to urządzenie</span>}
                </span>
                {device.lastIp && <small>IP {device.lastIp}</small>}
              </span>
              {isCompact ? (
                <span className="pf-device-dates">
                  <span className="pf-device-date-label">Dodane:</span>
                  <span>{formatDate(device.createdAt)}</span>
                  <span className="pf-device-date-label">Ostatnio użyte:</span>
                  <span>{formatRelativeDateTime(device.lastUsedAt)}</span>
                  <span className="pf-device-date-label">Wygasa:</span>
                  <span>{formatDate(device.expiresAt)}</span>
                </span>
              ) : (
                <>
                  <span className="pf-device-dim">{formatDate(device.createdAt)}</span>
                  <span className="pf-device-dim">{formatRelativeDateTime(device.lastUsedAt)}</span>
                  <span className="pf-device-dim">{formatDate(device.expiresAt)}</span>
                </>
              )}
              <span className="flex justify-end">
                <ActionButton
                  text="Usuń"
                  iconTitle="Usuń urządzenie"
                  disableText={true}
                  onClick={() => setRevokeTarget(device)}
                  className="pf-device-trash"
                >
                  <Trash2 className="dl-icon c-red" />
                </ActionButton>
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  ) : null;

  const modals = (
    <>
    {revokeTarget && (
      <RemoveModal
        warningText={
          revokeTarget === "all"
            ? "Usunąć wszystkie zaufane urządzenia?\nNa każdym z nich następne logowanie będzie wymagało kodu."
            : `Usunąć urządzenie ${revokeTarget.label} z zaufanych?\nNastępne logowanie na nim będzie wymagało kodu.`
        }
        handleRemove={handleConfirmRevoke}
        onClose={() => setRevokeTarget(null)}
      />
    )}
    {modal === "setup" && (
      <MfaSetupModal
        onClose={() => setModal(null)}
        onEnabled={(codes) => showCodes(codes, "Weryfikacja dwuetapowa została włączona.")}
      />
    )}
    {modal === "codes" && (
      <BackupCodesModal
        codes={backupCodes}
        username={user?.username ?? ""}
        onConfirm={codesNeedConfirm ? handleConfirmCodes : undefined}
        onAbort={handleCodesAborted}
        onClose={handleCodesClosed}
      />
    )}
    {modal === "disable" && (
      <MfaCodeModal
        title="Wyłącz weryfikację dwuetapową"
        description="Wpisz aktualny kod z aplikacji. Wyłączenie usunie klucz, kody zapasowe i zaufane urządzenia tego konta. Logowanie będzie wymagało tylko hasła."
        confirmText="Wyłącz"
        danger={true}
        onSubmit={handleDisable}
        onClose={() => setModal(null)}
      />
    )}
    {modal === "regenerate" && (
      <MfaCodeModal
        title="Nowe kody zapasowe"
        description="Wpisz aktualny kod z aplikacji. Nowe kody zastąpią wszystkie dotychczasowe, także nieużyte."
        confirmText="Wygeneruj"
        onSubmit={handleRegenerate}
        onClose={() => setModal(null)}
      />
    )}
    </>
  );

  return { enabled: status.enabled, tile, devicesPanel, modals, refresh: fetchStatus };
}

export default useMfaSection;