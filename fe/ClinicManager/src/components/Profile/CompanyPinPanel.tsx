import { useState } from "react";
import ActionButton from "../ActionButton";
import PinInput from "../PinInput";
import UserService from "../../services/UserService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { COMPANY_PIN_LENGTH, validateNewCompanyPin } from "../../utils/companyPin";
import { apiErrorMessage } from "../../utils/apiError";

export function CompanyPinPanel() {
  const { showAlert } = useAlert();
  const [currentPin, setCurrentPin] = useState<string>("");
  const [newPin, setNewPin] = useState<string>("");
  const [confirmPin, setConfirmPin] = useState<string>("");
  const [busy, setBusy] = useState<boolean>(false);

  const clearNewPin = () => {
    setNewPin("");
    setConfirmPin("");
  };

  const handleSubmit = async () => {
    if (currentPin.length !== COMPANY_PIN_LENGTH) {
      showAlert("Wpisz obecny 6-cyfrowy PIN.", AlertType.ERROR);
      return;
    }
    const error = validateNewCompanyPin(newPin, confirmPin);
    if (error) {
      showAlert(error, AlertType.ERROR);
      clearNewPin();
      return;
    }
    setBusy(true);
    try {
      const response = await UserService.changeOwnCompanyPin(currentPin, newPin);
      showAlert(response?.message ?? "PIN trybu firmowego został zmieniony.", AlertType.SUCCESS);
      setCurrentPin("");
      clearNewPin();
    } catch (err) {
      showAlert(apiErrorMessage(err) ?? "Nie udało się zmienić PIN-u.", AlertType.ERROR);
      setCurrentPin("");
      clearNewPin();
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="pf-panel pf-password pf-pin-panel flex-column g-1 height-fit-content">
      <h2 className="pf-panel-title m-0">Zmiana PIN-u</h2>

      <div className="pf-panel-body flex-column g-075">
        <div className="pf-row flex align-items-center space-between g-1">
          <span className="pf-label">Obecny PIN:</span>
          <PinInput value={currentPin} onChange={setCurrentPin} length={COMPANY_PIN_LENGTH} />
        </div>
        <div className="pf-row flex align-items-center space-between g-1">
          <span className="pf-label">Nowy PIN:</span>
          <PinInput value={newPin} onChange={setNewPin} length={COMPANY_PIN_LENGTH} />
        </div>
        <div className="pf-row flex align-items-center space-between g-1">
          <span className="pf-label">Powtórz PIN:</span>
          <PinInput value={confirmPin} onChange={setConfirmPin} length={COMPANY_PIN_LENGTH} />
        </div>
      </div>

      <div className="flex justify-end">
        <ActionButton text="Zmień PIN" onClick={handleSubmit} disabled={busy} />
      </div>
    </section>
  );
}

export default CompanyPinPanel;
