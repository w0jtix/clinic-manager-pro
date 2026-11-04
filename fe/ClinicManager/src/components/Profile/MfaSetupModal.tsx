import { useEffect, useRef, useState } from "react";
import Modal from "../Modal";
import PinInput from "../PinInput";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { MFA_CODE_LENGTH, MfaSetup } from "../../models/mfa";
import MfaService from "../../services/MfaService";
import { apiErrorMessage } from "../../utils/apiError";
import { formatSecret } from "../../utils/mfa";

export interface MfaSetupModalProps {
  onEnabled: (backupCodes: string[]) => void;
  onClose: () => void;
}

export function MfaSetupModal({ onEnabled, onClose }: MfaSetupModalProps) {
  const { showAlert } = useAlert();
  const [setup, setSetup] = useState<MfaSetup | null>(null);
  const [code, setCode] = useState<string>("");
  const [busy, setBusy] = useState<boolean>(false);
  const busyRef = useRef<boolean>(false);
  const setupRequestedRef = useRef<boolean>(false);

  useEffect(() => {
    if (setupRequestedRef.current) return;
    setupRequestedRef.current = true;
    MfaService.setup()
      .then(setSetup)
      .catch((error) => {
        showAlert(apiErrorMessage(error) ?? "Nie udało się rozpocząć konfiguracji.", AlertType.ERROR);
        onClose();
      });
  }, []);

  const handleCopySecret = async () => {
    if (!setup) return;
    try {
      await navigator.clipboard.writeText(setup.secret);
      showAlert("Skopiowano klucz.", AlertType.SUCCESS);
    } catch {
      showAlert("Nie udało się skopiować klucza.", AlertType.ERROR);
    }
  };

  const handleEnable = async () => {
    if (busyRef.current || !setup) return;
    if (code.length !== MFA_CODE_LENGTH) {
      showAlert("Wpisz 6-cyfrowy kod z aplikacji.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      const response = await MfaService.enable(code);
      onEnabled(response.backupCodes);
    } catch (error) {
      setCode("");
      showAlert(apiErrorMessage(error) ?? "Nie udało się włączyć weryfikacji.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  return (
    <Modal
      title="Włącz weryfikację dwuetapową"
      onClose={onClose}
      size="md"
      className="mfa-modal"
      footer={<ActionButton text="Włącz" onClick={handleEnable} disabled={busy || !setup} className="modal-button" />}
    >
      <div className="mfa-steps flex-column g-15">
        <div className="mfa-step">
          <span className="mfa-step-n">1</span>
          <p className="mfa-text m-0">
            Zainstaluj aplikację uwierzytelniającą, np. Google Authenticator.
          </p>
        </div>
        <div className="mfa-step">
          <span className="mfa-step-n">2</span>
          <div className="flex-column g-1 mw-0">
            <p className="mfa-text m-0">Zeskanuj kod QR w aplikacji. Jeśli nie możesz skanować, wpisz klucz ręcznie.</p>
            <div className="mfa-qr flex align-items-center justify-center">
              {setup ? <img src={setup.qrDataUri} alt="Kod QR do aplikacji uwierzytelniającej" /> : <span className="mfa-text">Ładowanie...</span>}
            </div>
            <div className="flex-column g-05">
              <span className="mfa-label">Klucz do wpisania ręcznie</span>
              <code className="mfa-secret width-fit-content">{setup ? formatSecret(setup.secret) : "...."}</code>
              <div className="flex">
                <ActionButton text="Kopiuj klucz" disableImg={true} onClick={handleCopySecret} disabled={!setup} />
              </div>
            </div>
          </div>
        </div>
        <div className="mfa-step">
          <span className="mfa-step-n">3</span>
          <div className="flex-column g-1 mw-0">
            <p className="mfa-text m-0">Wpisz kod, który pokazała aplikacja.</p>
            <PinInput
              value={code}
              onChange={setCode}
              length={MFA_CODE_LENGTH}
              masked={false}
              revealable={false}
              onSubmit={handleEnable}
            />
          </div>
        </div>
      </div>
    </Modal>
  );
}

export default MfaSetupModal;
