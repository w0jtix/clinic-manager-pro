import { useRef, useState } from "react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { apiErrorMessage } from "../../utils/apiError";

export interface BackupCodesModalProps {
  codes: string[];
  username: string;
  onConfirm?: () => Promise<void>;
  onAbort?: () => void;
  onClose: () => void;
}

export function BackupCodesModal({ codes, username, onConfirm, onAbort, onClose }: BackupCodesModalProps) {
  const { showAlert } = useAlert();
  const [saved, setSaved] = useState<boolean>(false);
  const [busy, setBusy] = useState<boolean>(false);
  const busyRef = useRef<boolean>(false);

  const fileContent = [
    `ClinicManager - kody zapasowe (${username})`,
    `Wygenerowano: ${new Date().toLocaleString("pl-PL")}`,
    "Każdy kod działa tylko raz.",
    "",
    ...codes,
  ].join("\n");

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(codes.join("\n"));
      showAlert("Skopiowano kody zapasowe.", AlertType.SUCCESS);
    } catch {
      showAlert("Nie udało się skopiować kodów.", AlertType.ERROR);
    }
  };

  const handleDownload = () => {
    const blob = new Blob([fileContent], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `clinicmanagerpro-kody-zapasowe-${username}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleClose = async () => {
    if (!saved) {
      showAlert("Zaznacz, że zapisałeś kody zapasowe.", AlertType.ERROR);
      return;
    }
    if (busyRef.current) return;
    if (!onConfirm) {
      onClose();
      return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      await onConfirm();
      onClose();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się zapisać nowych kodów.", AlertType.ERROR);
      onAbort?.();
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const handleCancel = () => {
    showAlert("Nie zapisano nowych kodów. Dotychczasowe kody nadal działają.", AlertType.INFO);
    onAbort?.();
  };

  const cancellable = !!onConfirm;

  return (
    <Modal
      title="Weryfikacja dwuetapowa"
      onClose={cancellable ? handleCancel : handleClose}
      size="sm"
      className="mfa-modal"
      closeOnOverlay={cancellable}
      closeOnEsc={cancellable}
      hideFooter={true}
    >
      <div className="mfa-codes-body flex-column g-1">
        <h3 className="mfa-title m-0">Zapisz kody zapasowe</h3>
        <p className="mfa-text centered m-0">
          Użyjesz ich, gdy nie będziesz mieć telefonu. Każdy kod działa raz. Po zamknięciu tego okna nie zobaczysz ich ponownie.
        </p>
        <div className="mfa-codes">
          {codes.map((code) => (
            <span key={code}>{code}</span>
          ))}
        </div>
        <div className="flex justify-center g-05 flex-wrap">
          <ActionButton text="Kopiuj kody" disableImg={true} onClick={handleCopy} />
          <ActionButton text="Pobierz .txt" disableImg={true} onClick={handleDownload} />
        </div>
        <div className="mfa-warn">Przechowuj kody poza telefonem z aplikacją, np. wydrukowane w bezpiecznym miejscu.</div>
        <label className="lg-remember flex align-items-center g-05" htmlFor="mfa-codes-saved">
          <input
            id="mfa-codes-saved"
            type="checkbox"
            className="app-checkbox"
            checked={saved}
            onChange={(e) => setSaved(e.target.checked)}
          />
          Zapisałem kody zapasowe
        </label>
        <ActionButton text="Gotowe" disableImg={true} onClick={handleClose} disabled={!saved || busy} className="lg-submit" />
      </div>
    </Modal>
  );
}

export default BackupCodesModal;
