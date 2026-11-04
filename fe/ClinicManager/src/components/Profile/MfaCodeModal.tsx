import { useRef, useState } from "react";
import Modal from "../Modal";
import PinInput from "../PinInput";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { MFA_CODE_LENGTH } from "../../models/mfa";
import { apiErrorMessage } from "../../utils/apiError";

export interface MfaCodeModalProps {
  title: string;
  description: string;
  confirmText: string;
  danger?: boolean;
  onSubmit: (code: string) => Promise<void>;
  onClose: () => void;
}

export function MfaCodeModal({ title, description, confirmText, danger = false, onSubmit, onClose }: MfaCodeModalProps) {
  const { showAlert } = useAlert();
  const [code, setCode] = useState<string>("");
  const [busy, setBusy] = useState<boolean>(false);
  const busyRef = useRef<boolean>(false);

  const handleSubmit = async () => {
    if (busyRef.current) return;
    if (code.length !== MFA_CODE_LENGTH) {
      showAlert("Wpisz 6-cyfrowy kod z aplikacji.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      await onSubmit(code);
    } catch (error) {
      setCode("");
      showAlert(apiErrorMessage(error) ?? "Nie udało się zweryfikować kodu.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  return (
    <Modal
      title={title}
      onClose={onClose}
      size="sm"
      className="mfa-modal"
      footer={
        <ActionButton
          text={confirmText}
          onClick={handleSubmit}
          disabled={busy}
          className={`modal-button ${danger ? "mfa-danger" : ""}`}
        />
      }
    >
      <div className="mfa-code-body flex-column align-items-center g-15">
        <p className="mfa-text m-0">{description}</p>
        <PinInput
          value={code}
          onChange={setCode}
          length={MFA_CODE_LENGTH}
          masked={false}
          revealable={false}
          autoFocus={true}
          onSubmit={handleSubmit}
          className="pin-lg"
        />
      </div>
    </Modal>
  );
}

export default MfaCodeModal;
