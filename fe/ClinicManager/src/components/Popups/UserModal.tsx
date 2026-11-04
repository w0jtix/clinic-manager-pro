import { useState, useCallback } from "react";
import Modal from "../Modal";
import TextInput from "../TextInput";
import PinInput from "../PinInput";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import AuthService from "../../services/AuthService";
import { RegisterRequest } from "../../models/register";
import { RoleType } from "../../models/login";
import { validateUserForm } from "../../utils/validators";
import { COMPANY_PIN_LENGTH, validateNewCompanyPin } from "../../utils/companyPin";
import { apiErrorMessage } from "../../utils/apiError";

export interface UserModalProps {
  onClose: () => void;
  className?: string;
}

export function UserModal({ onClose, className = "" }: UserModalProps) {
  const [signupRequest, setSignupRequest] = useState<RegisterRequest>({
    username: "",
    password: "",
    role: [RoleType.ROLE_USER],
  });
  const [companyPin, setCompanyPin] = useState<string>("");
  const [companyPinConfirm, setCompanyPinConfirm] = useState<string>("");
  const { showAlert } = useAlert();
  const isMaster = signupRequest.role.includes(RoleType.ROLE_MASTER);

  const handleRole = useCallback((role: RoleType) => {
    setSignupRequest((prev) => ({ ...prev, role: [role] }));
  }, []);

  const handleUsername = useCallback((username: string) => {
    setSignupRequest((prev) => ({ ...prev, username }));
  }, []);

  const handlePassword = useCallback((password: string) => {
    setSignupRequest((prev) => ({ ...prev, password }));
  }, []);

  const handleCreateUser = useCallback(async () => {
    const error = validateUserForm(signupRequest);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    if (isMaster) {
      const pinError = validateNewCompanyPin(companyPin, companyPinConfirm);
      if (pinError) {
        showAlert(pinError, AlertType.ERROR);
        return;
      }
    }
    AuthService.register(
      signupRequest.username,
      signupRequest.password,
      signupRequest.role,
      isMaster ? companyPin : undefined
    )
      .then(() => {
        showAlert("Użytkownik utworzony!", AlertType.SUCCESS);
        onClose();
      })
      .catch((error) => {
        console.error("Error while creating new User: ", error);
        showAlert(apiErrorMessage(error) ?? "Błąd tworzenia nowego Użytkownika!", AlertType.ERROR);
      });
  }, [signupRequest, showAlert, onClose, isMaster, companyPin, companyPinConfirm]);

  const pinInput = (value: string, setter: (v: string) => void) => (
    <PinInput value={value} onChange={setter} length={COMPANY_PIN_LENGTH} />
  );

  return (
    <Modal
      dirtyValue={[signupRequest, companyPin]}
      title="Nowy Użytkownik"
      onClose={onClose}
      size="md"
      onConfirm={handleCreateUser}
      className={className}
    >
      <div className="flex-column g-075">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwa użytkownika:</span>
          <TextInput
            dropdown={false}
            className="in-full"
            value={signupRequest.username}
            onSelect={(username) => {
              if (typeof username === "string") handleUsername(username);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Hasło:</span>
          <TextInput
            dropdown={false}
            className="in-full"
            password={true}
            value={signupRequest.password}
            onSelect={(pw) => {
              if (typeof pw === "string") handlePassword(pw);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Rola:</span>
          <div className="cat-seg-group flex align-items-center width-fit-content g-025">
            {[RoleType.ROLE_MASTER, RoleType.ROLE_USER].map((role) => (
              <button
                key={role}
                type="button"
                className={`cat-seg align-items-center pointer ${
                  role === RoleType.ROLE_MASTER ? "cl-master" : ""
                } ${signupRequest.role.includes(role) ? "active" : ""}`}
                onClick={() => handleRole(role)}
              >
                {role.replace("ROLE_", "")}
              </button>
            ))}
          </div>
        </div>
        {isMaster && (
          <>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">PIN firmowy:</span>
              {pinInput(companyPin, setCompanyPin)}
            </div>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Powtórz PIN:</span>
              {pinInput(companyPinConfirm, setCompanyPinConfirm)}
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}

export default UserModal;
