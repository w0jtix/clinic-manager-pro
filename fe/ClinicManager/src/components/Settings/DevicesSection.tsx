import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CirclePlus, Copy, SquarePen, Trash2, X } from "lucide-react";
import ActionButton from "../ActionButton";
import RemoveModal from "../Popups/RemoveModal";
import AddDeviceModal from "./AddDeviceModal";
import RenameDeviceModal from "./RenameDeviceModal";
import AccessGateSection from "./AccessGateSection";
import { useAlert } from "../Alert/AlertProvider";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRoles } from "../../hooks/useRoles";
import { AlertType } from "../../models/alert";
import { ClinicDevice, ClinicDeviceStatus, RegistrationCode } from "../../models/device";
import DeviceService from "../../services/DeviceService";
import { apiErrorMessage } from "../../utils/apiError";
import { formatDate, formatRelativeDateTime } from "../../utils/dateUtils";
import { CODE_LIFETIME_SECONDS, CODE_POLL_MS, formatCountdown, progressLevel, secondsLeft } from "../../utils/deviceCodes";

const STATUS_LABEL: Record<ClinicDeviceStatus, string> = {
  ACTIVE: "Aktywne",
  SUSPECTED_COPY: "Możliwa kopia",
  REVOKED: "Unieważnione",
};

const STATUS_CLASS: Record<ClinicDeviceStatus, string> = {
  ACTIVE: "ok",
  SUSPECTED_COPY: "warn",
  REVOKED: "bad",
};

export function DevicesSection() {
  const { showAlert } = useAlert();
  const { isMobile, isCompact } = useBreakpoint();
  const { isAdmin } = useRoles();
  const [devices, setDevices] = useState<ClinicDevice[]>([]);
  const [codes, setCodes] = useState<RegistrationCode[]>([]);
  const [showRevoked, setShowRevoked] = useState<boolean>(false);
  const [now, setNow] = useState<number>(Date.now());
  const [addOpen, setAddOpen] = useState<boolean>(false);
  const [renameTarget, setRenameTarget] = useState<ClinicDevice | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<ClinicDevice | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ClinicDevice | null>(null);
  const [modalCodeId, setModalCodeId] = useState<number | null>(null);
  const codesRef = useRef<RegistrationCode[]>([]);

  useEffect(() => {
    codesRef.current = codes;
  }, [codes]);

  const fetchDevices = useCallback(() => {
    DeviceService.getDevices(true)
      .then(setDevices)
      .catch((error) => showAlert(apiErrorMessage(error) ?? "Nie udało się pobrać urządzeń.", AlertType.ERROR));
  }, [showAlert]);

  const fetchCodes = useCallback(() => {
    DeviceService.getCodes()
      .then(setCodes)
      .catch((error) => showAlert(apiErrorMessage(error) ?? "Nie udało się pobrać kodów.", AlertType.ERROR));
  }, [showAlert]);

  useEffect(() => {
    fetchDevices();
    fetchCodes();
  }, [fetchDevices, fetchCodes]);

  useEffect(() => {
    if (codes.length === 0) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [codes.length]);

  const activeCodes = useMemo(() => codes.filter((code) => secondsLeft(code.expiresAt, now) > 0), [codes, now]);
  const hasActiveCode = activeCodes.length > 0;

  const pollCodes = useCallback(async () => {
    const previous = codesRef.current;
    let next: RegistrationCode[];
    try {
      next = await DeviceService.getCodes();
    } catch {
      return;
    }
    setCodes(next);
    const vanished = previous.filter(
      (code) => !next.some((item) => item.id === code.id) && secondsLeft(code.expiresAt, Date.now()) > 0
    );
    if (vanished.length === 0) return;
    let latestDevices: ClinicDevice[] = [];
    try {
      latestDevices = await DeviceService.getDevices(true);
      setDevices(latestDevices);
    } catch {
      return;
    }
    vanished.forEach((code) => {
      const expectedName = code.name ?? code.replaceDeviceName;
      const registered = latestDevices.some(
        (device) => device.name === expectedName && new Date(device.createdAt) >= new Date(code.createdAt)
      );
      if (registered) {
        showAlert(`Zarejestrowano urządzenie: ${expectedName}.`, AlertType.SUCCESS);
      }
    });
    if (modalCodeId !== null && vanished.some((code) => code.id === modalCodeId)) {
      setAddOpen(false);
      setModalCodeId(null);
    }
  }, [modalCodeId, showAlert]);

  useEffect(() => {
    if (!hasActiveCode) return;
    const timer = setInterval(pollCodes, CODE_POLL_MS);
    return () => clearInterval(timer);
  }, [hasActiveCode, pollCodes]);

  const activeDevices = useMemo(() => devices.filter((device) => device.status !== "REVOKED"), [devices]);
  const revokedDevices = useMemo(() => devices.filter((device) => device.status === "REVOKED"), [devices]);
  const visibleDevices = showRevoked ? revokedDevices : activeDevices;

  useEffect(() => {
    if (showRevoked && revokedDevices.length === 0) setShowRevoked(false);
  }, [showRevoked, revokedDevices.length]);

  const handleCopyCode = async (code: RegistrationCode) => {
    try {
      await navigator.clipboard.writeText(code.code);
      showAlert("Skopiowano!", AlertType.SUCCESS);
    } catch {
      showAlert("Nie udało się skopiować kodu.", AlertType.ERROR);
    }
  };

  const handleCancelCode = async (code: RegistrationCode) => {
    try {
      await DeviceService.cancelCode(code.id);
      setCodes((prev) => prev.filter((item) => item.id !== code.id));
      showAlert("Anulowano kod.", AlertType.SUCCESS);
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się anulować kodu.", AlertType.ERROR);
    }
  };

  const handleRevoke = async () => {
    const target = revokeTarget;
    setRevokeTarget(null);
    if (!target) return;
    try {
      await DeviceService.revokeDevice(target.id);
      showAlert(`Unieważniono urządzenie ${target.name}.`, AlertType.SUCCESS);
      fetchDevices();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się unieważnić urządzenia.", AlertType.ERROR);
    }
  };

  const handleDelete = async () => {
    const target = deleteTarget;
    setDeleteTarget(null);
    if (!target) return;
    try {
      await DeviceService.deleteDevice(target.id);
      showAlert(`Usunięto urządzenie ${target.name}.`, AlertType.SUCCESS);
      fetchDevices();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się usunąć urządzenia.", AlertType.ERROR);
    }
  };

  const statusTabs = revokedDevices.length > 0 && (
    <div className="cat-seg-group flex align-items-center width-fit-content g-025">
      <button
        type="button"
        className={`cat-seg align-items-center pointer ${!showRevoked ? "active" : ""}`}
        onClick={() => setShowRevoked(false)}
      >
        Aktywne ({activeDevices.length})
      </button>
      <button
        type="button"
        className={`cat-seg align-items-center pointer ${showRevoked ? "active" : ""}`}
        onClick={() => setShowRevoked(true)}
      >
        Unieważnione ({revokedDevices.length})
      </button>
    </div>
  );

  return (
    <div className="dv-section flex-column g-15">
      {isAdmin && <AccessGateSection />}

      {activeCodes.length > 0 && (
        <section className="pf-panel flex-column g-1">
          <h2 className="pf-panel-title m-0">Aktywny kod rejestracji</h2>
          <div className="flex-column g-05">
            {activeCodes.map((code) => {
              const left = secondsLeft(code.expiresAt, now);
              return (
                <div key={code.id} className="dv-code-row">
                  <span className="dv-code-for mw-0">
                    {code.name ?? `Zastąpienie: ${code.replaceDeviceName ?? ""}`}
                    <small> · wygenerował: {code.createdBy}</small>
                  </span>
                  <span className="dv-code-value flex align-items-center g-025">
                    jednorazowy kod: <code>{code.code}</code>
                    <ActionButton
                      text="Kopiuj"
                      iconTitle="Kopiuj kod"
                      disableText={true}
                      disableBorder={true}
                      onClick={() => handleCopyCode(code)}
                      className="dv-copy"
                    >
                      <Copy className="dl-icon dv-copy-icon" />
                    </ActionButton>
                  </span>
                  <span className="dv-progress flex align-items-center g-05">
                    <span className="dv-progress-bar">
                      <span
                        className={`dv-progress-fill ${progressLevel(left)}`}
                        style={{ width: `${(left / CODE_LIFETIME_SECONDS) * 100}%` }}
                      />
                    </span>
                    <span className="dv-progress-time">{formatCountdown(left)}</span>
                  </span>
                  <ActionButton
                    text="Anuluj"
                    iconTitle="Anuluj kod"
                    disableText={true}
                    onClick={() => handleCancelCode(code)}
                    className="pf-device-trash"
                  >
                    <X className="dl-icon c-red" />
                  </ActionButton>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section className="pf-panel flex-column g-1">
        <div className="flex align-items-center space-between g-1 flex-wrap">
          <h2 className="pf-panel-title m-0">Urządzenia gabinetu</h2>
          <div className="flex align-items-center g-1 flex-wrap">
            {!isCompact && statusTabs}
            <ActionButton
              text={isMobile ? "Urządzenie" : "Dodaj urządzenie"}
              iconTitle={hasActiveCode ? "Najpierw użyj albo anuluj aktywny kod rejestracji" : "Dodaj urządzenie"}
              onClick={() => setAddOpen(true)}
              disabled={hasActiveCode}
            >
              <CirclePlus className="dl-icon-1 c-green" />
            </ActionButton>
          </div>
        </div>
        {isCompact && statusTabs}

        {visibleDevices.length === 0 ? (
          <span className="pf-devices-empty">
            {showRevoked ? "Brak unieważnionych urządzeń." : "Brak zarejestrowanych urządzeń."}
          </span>
        ) : (
          <div className="dv-list scrollbar">
            {!isCompact && (
              <div className="dv-head">
                <span>Urządzenie</span>
                <span>Zarejestrowano</span>
                <span>Ostatnio widziane</span>
                <span>Przeglądarka</span>
                <span>Status</span>
                <span />
              </div>
            )}
            {visibleDevices.map((device) => {
              const registered = (
                <>
                  {formatDate(device.createdAt)}
                  <small>{device.registeredBy ? `przez ${device.registeredBy}` : "-"}</small>
                </>
              );
              const lastSeen = (
                <>
                  {device.lastSeenAt ? formatRelativeDateTime(device.lastSeenAt) : "nigdy"}
                  <small>{device.lastSeenUsername ?? "-"}</small>
                </>
              );
              return (
                <div key={device.id} className="dv-row">
                  <span className="dv-cell-main flex-column mw-0">
                    <span>{device.name}</span>
                    <small>
                      {device.replacedByName ? `zastąpione przez: ${device.replacedByName}` : `IP ${device.lastIp ?? "-"}`}
                    </small>
                  </span>
                  {isCompact ? (
                    <span className="dv-cell-details flex-column g-025">
                      <span className="dv-detail">
                        <span className="dv-detail-label">Zarejestrowano:</span> {registered}
                      </span>
                      <span className="dv-detail">
                        <span className="dv-detail-label">Ostatnio widziane:</span> {lastSeen}
                      </span>
                      <span className="dv-detail">
                        <span className="dv-detail-label">Przeglądarka:</span> {device.label ?? "-"}
                      </span>
                    </span>
                  ) : (
                    <>
                      <span className="dv-cell-sub flex-column">{registered}</span>
                      <span className="dv-cell-sub flex-column">{lastSeen}</span>
                      <span className="dv-cell-dim">{device.label ?? "-"}</span>
                    </>
                  )}
                  <span className={`dv-pill ${STATUS_CLASS[device.status]}`}>{STATUS_LABEL[device.status]}</span>
                  <span className="dv-actions flex justify-end g-025">
                    {device.status === "REVOKED" ? (
                      <ActionButton
                        text="Usuń"
                        iconTitle="Usuń urządzenie na stałe"
                        disableText={true}
                        onClick={() => setDeleteTarget(device)}
                        className="pf-device-trash"
                      >
                        <Trash2 className="dl-icon c-red" />
                      </ActionButton>
                    ) : (
                      <>
                        <ActionButton
                          text="Zmień nazwę"
                          iconTitle="Zmień nazwę"
                          disableText={true}
                          onClick={() => setRenameTarget(device)}
                          className="pf-device-trash"
                        >
                          <SquarePen className="dl-icon c-blue" />
                        </ActionButton>
                        <ActionButton
                          text="Unieważnij"
                          iconTitle="Unieważnij urządzenie"
                          disableText={true}
                          onClick={() => setRevokeTarget(device)}
                          className="pf-device-trash"
                        >
                          <Trash2 className="dl-icon c-red" />
                        </ActionButton>
                      </>
                    )}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {addOpen && (
        <AddDeviceModal
          activeDevices={activeDevices}
          onCreated={(code) => {
            setModalCodeId(code.id);
            setCodes((prev) => [code, ...prev.filter((item) => item.id !== code.id)]);
            setNow(Date.now());
          }}
          onClose={() => {
            setAddOpen(false);
            setModalCodeId(null);
          }}
        />
      )}
      {renameTarget && (
        <RenameDeviceModal device={renameTarget} onSaved={fetchDevices} onClose={() => setRenameTarget(null)} />
      )}
      {deleteTarget && (
        <RemoveModal
          warningText={`Trwale usunąć urządzenie ${deleteTarget.name}?\nHistoria karty i logi zostaną zachowane. Tej operacji nie można cofnąć.`}
          confirmText="Usuń"
          handleRemove={handleDelete}
          onClose={() => setDeleteTarget(null)}
        />
      )}
      {revokeTarget && (
        <RemoveModal
          warningText={`Unieważnić urządzenie ${revokeTarget.name}?\nŻeby znów z niego korzystać, trzeba je zarejestrować nowym kodem.`}
          confirmText="Unieważnij"
          handleRemove={handleRevoke}
          onClose={() => setRevokeTarget(null)}
        />
      )}
    </div>
  );
}

export default DevicesSection;
