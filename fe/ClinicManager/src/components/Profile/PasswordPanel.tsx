import { useState } from "react";
import { AlertType } from "../../models/alert";
import { useAlert } from "../Alert/AlertProvider";
import { validateChangePasswordForm } from "../../utils/validators";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import AuthService from "../../services/AuthService";

export interface PasswordPanelProps {
  onPasswordChanged?: () => void;
}

export function PasswordPanel({ onPasswordChanged }: PasswordPanelProps) {
  const { showAlert } = useAlert();
  const [oldPassword, setOldPassword] = useState<string>("");
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmPassword, setConfirmPassword] = useState<string>("");

  const clearFields = () => {
    setOldPassword("");
    setNewPassword("");
    setConfirmPassword("");
  };

  const handleSubmit = async () => {
    const error = validateChangePasswordForm(
      oldPassword,
      newPassword,
      confirmPassword
    );
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }

    AuthService.changePassword(oldPassword, newPassword)
      .then((message) => {
        if (message === "Old password doesn't match") {
          showAlert("Nieprawidłowe stare hasło", AlertType.ERROR);
        } else if (message === "Password changed successfully") {
          showAlert("Hasło zostało pomyślnie zmienione", AlertType.SUCCESS);
          onPasswordChanged?.();
        }
        clearFields();
      })
      .catch(() => {
        showAlert("Wystąpił błąd podczas zmiany hasła", AlertType.ERROR);
        clearFields();
      });
  };

  return (
    <section className="pf-panel pf-password flex-column g-1 height-fit-content">
      <h2 className="pf-panel-title m-0">Zmiana Hasła</h2>

      <div className="pf-panel-body flex-column g-075">
        <div className="pf-row flex align-items-center g-4">
          <span className="pf-label">Stare Hasło:</span>
          <TextInput
            password={true}
            className="in-full"
            value={oldPassword}
            onSelect={(value) => {
              if (typeof value === "string") setOldPassword(value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-4">
          <span className="pf-label">Nowe Hasło:</span>
          <TextInput
            password={true}
            className="in-full"
            value={newPassword}
            onSelect={(value) => {
              if (typeof value === "string") setNewPassword(value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-4">
          <span className="pf-label">Potwierdź Hasło:</span>
          <TextInput
            password={true}
            className="in-full"
            value={confirmPassword}
            onSelect={(value) => {
              if (typeof value === "string") setConfirmPassword(value);
            }}
          />
        </div>
      </div>

      <div className="flex justify-end">
        <ActionButton text="Zmień Hasło" onClick={handleSubmit} />
      </div>
    </section>
  );
}

export default PasswordPanel;
