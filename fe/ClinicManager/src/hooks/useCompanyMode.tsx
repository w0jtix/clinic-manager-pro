import { useRef, useState } from "react";
import { KeyRound } from "lucide-react";
import Modal from "../components/Modal";
import PinInput from "../components/PinInput";
import AuthService from "../services/AuthService";
import { useUser } from "../components/User/UserProvider";
import { useAlert } from "../components/Alert/AlertProvider";
import { AlertType } from "../models/alert";
import { isCompanyModeActive, isMasterAccount } from "../utils/roles";
import { COMPANY_PIN_LENGTH } from "../utils/companyPin";
import { apiErrorMessage } from "../utils/apiError";

export function useCompanyMode() {
  const { user, refreshUser } = useUser();
  const { showAlert } = useAlert();
  const [pinModalOpen, setPinModalOpen] = useState<boolean>(false);
  const [pin, setPin] = useState<string>("");
  const [busy, setBusy] = useState<boolean>(false);
  const busyRef = useRef<boolean>(false);

  const available = isMasterAccount(user);
  const active = isCompanyModeActive(user);

  const closePinModal = () => {
    setPinModalOpen(false);
    setPin("");
  };

  const openPinModal = () => {
    if (busyRef.current) return;
    setPinModalOpen(true);
  };

  const handleElevate = async () => {
    if (busyRef.current) return;
    if (pin.length !== COMPANY_PIN_LENGTH) {
      showAlert("Wpisz 6-cyfrowy PIN.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      await AuthService.elevate(pin);
      refreshUser();
      closePinModal();
      showAlert("Przełączono na konto firmowe.", AlertType.SUCCESS);
    } catch (error) {
      setPin("");
      showAlert(apiErrorMessage(error) ?? "Nie udało się przełączyć na konto firmowe.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const dropCompanyMode = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      await AuthService.dropElevation();
      refreshUser();
      showAlert("Wrócono do konta pracowniczego.", AlertType.SUCCESS);
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się wrócić do konta pracowniczego.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const pinModal = pinModalOpen ? (
    <Modal
      title="Konto firmowe"
      onClose={closePinModal}
      size="sm"
      className="pin-modal"
      onConfirm={handleElevate}
      confirmText="Przełącz"
      confirmDisabled={busy}
    >
      <div className="pin-modal-body flex-column align-items-center justify-center g-15">
        <KeyRound className="pin-modal-icon pc-y" />
        <span className="pin-modal-text">Wpisz PIN trybu firmowego</span>
        <PinInput
          value={pin}
          onChange={setPin}
          length={COMPANY_PIN_LENGTH}
          autoFocus={true}
          onSubmit={handleElevate}
          revealable={false}
          className="pin-lg"
        />
      </div>
    </Modal>
  ) : null;

  return { available, active, busy, openPinModal, dropCompanyMode, pinModal };
}

export default useCompanyMode;
