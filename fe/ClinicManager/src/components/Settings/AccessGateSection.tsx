import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Lock, Pause, PauseCircle, Play, Save, ShieldCheck, ShieldOff, LucideIcon } from "lucide-react";
import ActionButton from "../ActionButton";
import ToggleButton from "../ToggleButton";
import TimeField from "../TimeField";
import EnableGateModal from "./EnableGateModal";
import DisableGateModal from "./DisableGateModal";
import StepUpField, { stepUpError, toStepUp } from "./StepUpField";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import {
  AccessControl,
  GatePauseDuration,
  STEP_UP_INVALID,
  STEP_UP_REQUIRED,
  WEEKDAY_LABELS,
  WorkingDay,
} from "../../models/access-control";
import AccessControlService from "../../services/AccessControlService";
import MfaService from "../../services/MfaService";
import { apiErrorCode, apiErrorMessage } from "../../utils/apiError";
import { formatRelativeDateTime } from "../../utils/dateUtils";

const PAUSE_OPTIONS: { duration: GatePauseDuration; label: string }[] = [
  { duration: "HOUR_1", label: "1 h" },
  { duration: "HOUR_4", label: "4 h" },
  { duration: "END_OF_DAY", label: "Do końca dnia" },
];

const DEFAULT_START = "07:30";
const DEFAULT_END = "20:30";
const PAUSE_TICK_MS = 30000;

const sortDays = (hours: WorkingDay[]): WorkingDay[] =>
  [1, 2, 3, 4, 5, 6, 7].map(
    (day) => hours.find((item) => item.dayOfWeek === day) ?? { dayOfWeek: day, open: false, startTime: null, endTime: null }
  );

const sameHours = (a: WorkingDay[], b: WorkingDay[]): boolean =>
  a.length === b.length &&
  a.every(
    (day, index) =>
      day.open === b[index].open &&
      (!day.open || (day.startTime === b[index].startTime && day.endTime === b[index].endTime))
  );

const validateHours = (hoursEnabled: boolean, hours: WorkingDay[]): string | null => {
  if (!hoursEnabled) return null;
  for (const day of hours) {
    if (!day.open) continue;
    if (!day.startTime || !day.endTime) {
      return `${WEEKDAY_LABELS[day.dayOfWeek]}: podaj godzinę otwarcia i zamknięcia.`;
    }
    if (day.startTime >= day.endTime) {
      return `${WEEKDAY_LABELS[day.dayOfWeek]}: godzina rozpoczęcia musi być wcześniejsza niż zakończenia.`;
    }
  }
  if (!hours.some((day) => day.open)) {
    return "Zaznacz przynajmniej jeden dzień pracy albo wyłącz ograniczenie godzin.";
  }
  return null;
};

export function AccessGateSection() {
  const { showAlert } = useAlert();
  const [state, setState] = useState<AccessControl | null>(null);
  const [hoursEnabled, setHoursEnabled] = useState<boolean>(false);
  const [hours, setHours] = useState<WorkingDay[]>(sortDays([]));
  const [formVersion, setFormVersion] = useState<number>(0);
  const [mfaEnabled, setMfaEnabled] = useState<boolean>(false);
  const [enableOpen, setEnableOpen] = useState<boolean>(false);
  const [disableOpen, setDisableOpen] = useState<boolean>(false);
  const [pauseOpen, setPauseOpen] = useState<boolean>(false);
  const [pauseDuration, setPauseDuration] = useState<GatePauseDuration>("HOUR_1");
  const [pauseSecret, setPauseSecret] = useState<string>("");
  const [now, setNow] = useState<number>(Date.now());
  const busyRef = useRef<boolean>(false);

  const applyState = useCallback((data: AccessControl) => {
    setState(data);
    setHoursEnabled(data.hoursEnabled);
    setHours(sortDays(data.hours ?? []));
    setFormVersion((prev) => prev + 1);
    setNow(Date.now());
  }, []);

  useEffect(() => {
    AccessControlService.get()
      .then(applyState)
      .catch((error) => showAlert(apiErrorMessage(error) ?? "Nie udało się pobrać ustawień bramki.", AlertType.ERROR));
  }, [applyState, showAlert]);

  const refreshMfaStatus = useCallback(() => {
    MfaService.getStatus()
      .then((status) => setMfaEnabled(!!status?.enabled))
      .catch(() => setMfaEnabled(false));
  }, []);

  useEffect(() => {
    refreshMfaStatus();
  }, [refreshMfaStatus]);

  const pausedUntilMs = state?.pausedUntil ? new Date(state.pausedUntil).getTime() : null;
  const isPaused = !!state?.gateEnabled && pausedUntilMs !== null && pausedUntilMs > now;

  useEffect(() => {
    if (!isPaused) return;
    const timer = setInterval(() => setNow(Date.now()), PAUSE_TICK_MS);
    return () => clearInterval(timer);
  }, [isPaused]);

  const hoursDirty = useMemo(() => {
    if (!state) return false;
    return hoursEnabled !== state.hoursEnabled || !sameHours(hours, sortDays(state.hours ?? []));
  }, [state, hoursEnabled, hours]);

  const run = async (
    action: () => Promise<AccessControl>,
    success: string,
    failure: string,
    onStepUpInvalid?: () => void
  ): Promise<boolean> => {
    if (busyRef.current) return false;
    busyRef.current = true;
    try {
      applyState(await action());
      showAlert(success, AlertType.SUCCESS);
      return true;
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? failure, AlertType.ERROR);
      const code = apiErrorCode(error);
      if (code === STEP_UP_INVALID) onStepUpInvalid?.();
      if (code === STEP_UP_REQUIRED) {
        onStepUpInvalid?.();
        refreshMfaStatus();
      }
      return false;
    } finally {
      busyRef.current = false;
    }
  };

  const handleEnable = async () => {
    if (await run(() => AccessControlService.setGate(true), "Włączono bramkę dostępu.", "Nie udało się włączyć bramki.")) {
      setEnableOpen(false);
    }
  };

  const handleDisable = async (secret: string, clear: () => void) => {
    const error = stepUpError(mfaEnabled, secret);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    const ok = await run(
      () => AccessControlService.setGate(false, toStepUp(mfaEnabled, secret)),
      "Wyłączono bramkę dostępu.",
      "Nie udało się wyłączyć bramki.",
      clear
    );
    if (ok) setDisableOpen(false);
  };

  const togglePause = () => {
    if (pauseOpen) {
      setPauseDuration("HOUR_1");
      setPauseSecret("");
    }
    setPauseOpen(!pauseOpen);
  };

  const handlePause = async () => {
    const error = stepUpError(mfaEnabled, pauseSecret);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    const ok = await run(
      () => AccessControlService.pause(pauseDuration, toStepUp(mfaEnabled, pauseSecret)),
      "Wstrzymano bramkę.",
      "Nie udało się wstrzymać bramki.",
      () => setPauseSecret("")
    );
    if (ok) {
      setPauseOpen(false);
      setPauseSecret("");
    }
  };

  const handleResume = () =>
    run(() => AccessControlService.resume(), "Wznowiono bramkę.", "Nie udało się wznowić bramki.");

  const updateDay = (dayOfWeek: number, patch: Partial<WorkingDay>) =>
    setHours((prev) => prev.map((day) => (day.dayOfWeek === dayOfWeek ? { ...day, ...patch } : day)));

  const toggleDay = (day: WorkingDay, open: boolean) =>
    updateDay(day.dayOfWeek, {
      open,
      startTime: open ? day.startTime ?? DEFAULT_START : day.startTime,
      endTime: open ? day.endTime ?? DEFAULT_END : day.endTime,
    });

  const handleSaveHours = () => {
    if (!hoursDirty) {
      showAlert("Brak zmian do zapisania.", AlertType.INFO);
      return;
    }
    const error = validateHours(hoursEnabled, hours);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    run(
      () =>
        AccessControlService.updateHours({
          hoursEnabled,
          hours: hours.map((day) => (day.open ? day : { ...day, startTime: null, endTime: null })),
        }),
      "Zapisano godziny pracy.",
      "Nie udało się zapisać godzin pracy."
    );
  };

  if (!state) return null;

  const status: { label: string; className: string; Icon: LucideIcon } = !state.gateEnabled
    ? { label: "Wyłączona", className: "bad", Icon: ShieldOff }
    : isPaused
      ? { label: "Wstrzymana", className: "warn", Icon: PauseCircle }
      : { label: "Aktywna", className: "ok", Icon: ShieldCheck };

  return (
    <>
      <section className="pf-panel flex-column g-1">
        <div className="flex align-items-center space-between g-1 flex-wrap">
          <div className="flex align-items-center g-075">
            <h2 className="pf-panel-title m-0">Bramka dostępu</h2>
            <span className={`dv-pill ag-status flex align-items-center g-025 ${status.className}`}>
              <status.Icon className="ag-status-icon" />
              {status.label}
            </span>
          </div>
          {state.gateEnabled ? (
            <ActionButton text="Wyłącz" onClick={() => setDisableOpen(true)} className="ag-btn ag-red">
              <ShieldOff className="dl-icon-1 c-red" />
            </ActionButton>
          ) : (
            <ActionButton text="Włącz" onClick={() => setEnableOpen(true)} className="pf-company-enter">
              <ShieldCheck className="dl-icon-1" />
            </ActionButton>
          )}
        </div>
        {isPaused ? (
          <p className="dv-code-meta ag-paused-info m-0">
            Wstrzymana do{" "}
            <b>
              {formatRelativeDateTime(state.pausedUntil)}
              {state.pausedBy ? ` (${state.pausedBy})` : ""}
            </b>
            . Pracownicy logują się z każdego urządzenia.
          </p>
        ) : (
          <p className="dv-code-meta m-0">
            Pracownicy logują się tylko z aktywnych urządzeń
            {state.hoursEnabled ? " w wybranych godzinach pracy" : ""}.
          </p>
        )}

        {state.gateEnabled &&
          (isPaused ? (
            <div className="flex">
              <ActionButton text="Wznów" onClick={handleResume} className="ag-btn ag-green">
                <Play className="dl-icon-1 c-green" />
              </ActionButton>
            </div>
          ) : (
            <div className="ag-pause flex-column g-075">
              <button
                type="button"
                className="ag-pause-toggle flex align-items-center g-05 pointer"
                onClick={togglePause}
              >
                <ChevronDown className={`dl-icon ag-caret ${pauseOpen ? "open" : ""}`} />
                Awaryjne wstrzymanie
              </button>
              {pauseOpen && (
                <div className="ag-pause-body flex align-items-center flex-wrap">
                  <div className="cat-seg-group flex align-items-center width-fit-content g-025">
                    {PAUSE_OPTIONS.map((option) => (
                      <button
                        key={option.duration}
                        type="button"
                        className={`cat-seg align-items-center pointer ${pauseDuration === option.duration ? "active" : ""}`}
                        onClick={() => setPauseDuration(option.duration)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                  <StepUpField mfaEnabled={mfaEnabled} value={pauseSecret} onChange={setPauseSecret} onSubmit={handlePause} />
                  <ActionButton text="Wstrzymaj" onClick={handlePause} className="ag-btn ag-yellow">
                    <Pause className="dl-icon-1 pc-y" />
                  </ActionButton>
                </div>
              )}
            </div>
          ))}
      </section>

      <section className={`pf-panel flex-column g-1 ${state.gateEnabled ? "" : "ag-locked"}`}>
        <div className="flex align-items-center space-between g-1 flex-wrap">
          <div className="flex align-items-center g-075">
            <h2 className="pf-panel-title m-0">Godziny pracy</h2>
            <ToggleButton
              key={`hours-${formVersion}`}
              disabled={!state.gateEnabled}
              value={hoursEnabled}
              onChange={setHoursEnabled}
              className="ag-toggle-sm"
            />
          </div>
          <ActionButton
            text="Zapisz"
            onClick={handleSaveHours}
            disabled={!state.gateEnabled}
            className={hoursDirty ? "ag-btn ag-yellow" : ""}
          >
            <Save className="dl-icon-1 pc-y" />
          </ActionButton>
        </div>
        {!state.gateEnabled && (
          <span className="ag-locked-note flex align-items-center g-05">
            <Lock className="dl-icon-1" />
            Godziny pracy działają tylko przy włączonej bramce.
          </span>
        )}
        {hoursEnabled && (
          <div className="ag-hours flex-column">
            {hours.map((day) => (
              <div key={day.dayOfWeek} className={`ag-day ${day.open ? "" : "closed"}`}>
                <span className="ag-day-name">{WEEKDAY_LABELS[day.dayOfWeek]}</span>
                <ToggleButton
                  key={`day-${day.dayOfWeek}-${formVersion}`}
                  disabled={!state.gateEnabled}
                  value={day.open}
                  onChange={(open) => toggleDay(day, open)}
                  className="ag-toggle-sm"
                />
                {day.open ? (
                  <span className="ag-day-range flex align-items-center g-05">
                    <TimeField
                      title={`${WEEKDAY_LABELS[day.dayOfWeek]} - otwarcie`}
                      value={day.startTime}
                      fallback={DEFAULT_START}
                      disabled={!state.gateEnabled}
                      onChange={(value) => updateDay(day.dayOfWeek, { startTime: value })}
                    />
                    <span className="ag-dash">-</span>
                    <TimeField
                      title={`${WEEKDAY_LABELS[day.dayOfWeek]} - zamknięcie`}
                      value={day.endTime}
                      fallback={DEFAULT_END}
                      disabled={!state.gateEnabled}
                      onChange={(value) => updateDay(day.dayOfWeek, { endTime: value })}
                    />
                  </span>
                ) : (
                  <span className="ag-day-closed">zamknięte</span>
                )}
              </div>
            ))}
          </div>
        )}
        <p className="dv-code-meta w-h m-0">
          Godziny dostępu dla USER do aplikacji. Sesja rozpoczęta przed zamknięciem trwa do wygaśnięcia (max 5 h).
        </p>
      </section>

      {enableOpen && <EnableGateModal onConfirm={handleEnable} onClose={() => setEnableOpen(false)} />}
      {disableOpen && (
        <DisableGateModal mfaEnabled={mfaEnabled} onConfirm={handleDisable} onClose={() => setDisableOpen(false)} />
      )}
    </>
  );
}

export default AccessGateSection;
