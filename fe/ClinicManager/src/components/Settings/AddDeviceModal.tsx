import { useEffect, useRef, useState } from "react";
import { Copy, KeyRound } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import DropdownSelect from "../DropdownSelect";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { ClinicDevice, RegistrationCode } from "../../models/device";
import DeviceService from "../../services/DeviceService";
import { apiErrorMessage } from "../../utils/apiError";
import { formatCountdown, secondsLeft } from "../../utils/deviceCodes";

export interface AddDeviceModalProps {
  activeDevices: ClinicDevice[];
  onCreated: (code: RegistrationCode) => void;
  onClose: () => void;
}

type AddMode = "new" | "replace";

export function AddDeviceModal({ activeDevices, onCreated, onClose }: AddDeviceModalProps) {
  const { showAlert } = useAlert();
  const [mode, setMode] = useState<AddMode>("new");
  const [name, setName] = useState<string>("");
  const [replaceDevice, setReplaceDevice] = useState<ClinicDevice | null>(null);
  const [created, setCreated] = useState<RegistrationCode | null>(null);
  const [now, setNow] = useState<number>(Date.now());
  const busyRef = useRef<boolean>(false);

  useEffect(() => {
    if (!created) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [created]);

  const changeMode = (next: AddMode) => {
    if (next === mode) return;
    setMode(next);
    setReplaceDevice(null);
  };

  const handleGenerate = async () => {
    if (busyRef.current) return;
    if (mode === "new" && !name.trim()) {
      showAlert("Podaj nazwę urządzenia.", AlertType.ERROR);
      return;
    }
    if (mode === "replace" && !replaceDevice) {
      showAlert("Wybierz urządzenie do zastąpienia.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    try {
      const code = await DeviceService.createCode(
        mode === "new" ? { name: name.trim() } : { replaceDeviceId: replaceDevice!.id }
      );
      setCreated(code);
      setNow(Date.now());
      onCreated(code);
      showAlert(`Wygenerowano kod dla: ${code.name ?? code.replaceDeviceName ?? ""}.`, AlertType.SUCCESS);
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się wygenerować kodu.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
    }
  };

  const handleCopy = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.code);
      showAlert("Skopiowano!", AlertType.SUCCESS);
    } catch {
      showAlert("Nie udało się skopiować kodu.", AlertType.ERROR);
    }
  };

  const left = created ? secondsLeft(created.expiresAt, now) : 0;

  return (
    <Modal
      title="Dodaj urządzenie"
      onClose={onClose}
      size="sm"
      className="dv-modal"
      footer={
        created ? (
          <ActionButton text="Gotowe" onClick={onClose} className="modal-button" />
        ) : (
          <ActionButton text="Wygeneruj kod" onClick={handleGenerate} className="modal-button">
            <KeyRound className="dl-icon-1 pc-y" />
          </ActionButton>
        )
      }
    >
      {created ? (
        <div className="flex-column g-1">
          <div className="dv-code-box flex-column align-items-center g-05">
            <span className="dv-code-meta">
              Dla: {created.name ?? ` ${created.replaceDeviceName ?? ""}`}
            </span>
            <span className="dv-code-copy relative">
              <span className="dv-code-big">{created.code}</span>
              <ActionButton
                text="Kopiuj"
                iconTitle="Kopiuj kod"
                disableText={true}
                disableBorder={true}
                onClick={handleCopy}
                className="dv-copy"
              >
                <Copy className="dl-icon dv-copy-icon" />
              </ActionButton>
            </span>
            <span className="dv-code-meta">
              {left > 0 ? `Ważny jeszcze ${formatCountdown(left)} · jednorazowy` : "Kod wygasł"}
            </span>
          </div>
          <p className="dv-code-meta dv-code-hint m-0">
            Na urządzeniu otwórz <code>/login/register-device</code> i wpisz kod. Adres podyktuj ręcznie, na ekranie
            logowania nie ma do niego odnośnika.
          </p>
        </div>
      ) : (
        <div className="flex-column g-1">
          <div className="cat-seg-group flex align-items-center width-fit-content g-025">
            <button
              type="button"
              className={`cat-seg align-items-center pointer ${mode === "new" ? "active" : ""}`}
              onClick={() => changeMode("new")}
            >
              Nowe urządzenie
            </button>
            <button
              type="button"
              className={`cat-seg align-items-center pointer ${mode === "replace" ? "active" : ""}`}
              onClick={() => changeMode("replace")}
              disabled={activeDevices.length === 0}
            >
              Zastąp istniejące
            </button>
          </div>
          {mode === "new" ? (
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Nazwa:</span>
              <TextInput
                className="in-full"
                value={name}
                maxLength={100}
                placeholder="np. iPad gabinet 3"
                onSelect={(value) => {
                  if (typeof value === "string") setName(value);
                }}
              />
            </div>
          ) : (
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Zastąp:</span>
              <DropdownSelect<ClinicDevice>
                items={activeDevices}
                value={replaceDevice}
                onChange={(value) => setReplaceDevice(Array.isArray(value) ? value[0] ?? null : value)}
                placeholder="Wybierz urządzenie"
                searchable={false}
                allowNew={false}
                multiple={false}
                className="dv-replace-select"
              />
            </div>
          )}
          <p className="dv-code-meta dv-code-hint m-0">
            {mode === "new"
              ? "Kod działa 15 minut i tylko raz."
              : "Nowe urządzenie przejmie nazwę, a stare zostanie unieważnione."}
          </p>
        </div>
      )}
    </Modal>
  );
}

export default AddDeviceModal;
