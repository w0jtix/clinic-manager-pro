import { useEffect, useState, useCallback } from "react";
import { ChevronDown } from "lucide-react";
import Modal from "../Modal";
import TextInput from "../TextInput";
import PinInput from "../PinInput";
import ActionButton from "../ActionButton";
import DropdownSelect from "../DropdownSelect";
import { AVAILABLE_AVATARS } from "../../constants/avatars";
import { User, Role, RoleType } from "../../models/login";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import AuthService from "../../services/AuthService";
import UserService from "../../services/UserService";
import {
  validateForceChangePasswordForm,
  validateUpdateUser,
} from "../../utils/validators";
import { Employee } from "../../models/employee";
import { COMPANY_PIN_LENGTH, validateNewCompanyPin } from "../../utils/companyPin";
import { apiErrorMessage } from "../../utils/apiError";
import { sortByRoleHierarchy } from "../../utils/roles";
import MfaService from "../../services/MfaService";
import { formatDate, formatRelativeDateTime } from "../../utils/dateUtils";
import { TrustedDevice } from "../../models/mfa";

export interface EditUserModalProps {
  onClose: () => void;
  userId: number | string | null;
  availableRoles: Role[];
  employees: Employee[];
  refreshUserList: () => void;
  isAdmin?: boolean;
  className?: string;
}

export function EditUserModal({
  onClose,
  userId,
  availableRoles,
  employees,
  refreshUserList,
  isAdmin = false,
  className = "",
}: EditUserModalProps) {
  const { showAlert } = useAlert();
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmPassword, setConfirmPassword] = useState<string>("");
  const [showForceChangePw, setShowForceChangePw] = useState<boolean>(false);
  const [updatedUser, setUpdatedUser] = useState<User | null>(null);
  const [fetchedUser, setFetchedUser] = useState<User | null>(null);
  const [companyPin, setCompanyPin] = useState<string>("");
  const [companyPinConfirm, setCompanyPinConfirm] = useState<string>("");
  const [showPinChange, setShowPinChange] = useState<boolean>(false);
  const [newPin, setNewPin] = useState<string>("");
  const [newPinConfirm, setNewPinConfirm] = useState<string>("");
  const [confirmMfaReset, setConfirmMfaReset] = useState<boolean>(false);
  const [mfaResetting, setMfaResetting] = useState<boolean>(false);
  const [devicesOpen, setDevicesOpen] = useState<boolean>(false);
  const [devices, setDevices] = useState<TrustedDevice[] | null>(null);

  const isMasterRole = (u: User | null) =>
    u?.roles?.some((r) => r.name === RoleType.ROLE_MASTER) ?? false;
  const needsInitialPin =
    isAdmin && isMasterRole(updatedUser) && !(isMasterRole(fetchedUser) && fetchedUser?.hasCompanyPin);
  const canChangePin =
    isAdmin && isMasterRole(fetchedUser) && isMasterRole(updatedUser) && !!fetchedUser?.hasCompanyPin;

  const fetchUserById = async (userId: number | string) => {
    UserService.getUserById(userId)
      .then((data) => {
        setUpdatedUser(data);
        setFetchedUser(data);
      })
      .catch((error) => {
        console.error("Error fetching user: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  useEffect(() => {
    if (userId !== null) {
      fetchUserById(userId);
    }
  }, []);

  const selectRole = useCallback((role: Role) => {
    if (role.name === RoleType.ROLE_ADMIN) return;
    setUpdatedUser((prev) => {
      if (!prev || prev.roles.some((r) => r.name === RoleType.ROLE_ADMIN)) return prev;
      return { ...prev, roles: [role] };
    });
    setCompanyPin("");
    setCompanyPinConfirm("");
    setNewPin("");
    setNewPinConfirm("");
    setShowPinChange(false);
  }, []);

  const selectedBusinessRole = (u: User | null): RoleType | null => {
    if (!u?.roles) return null;
    if (u.roles.some((r) => r.name === RoleType.ROLE_ADMIN)) return RoleType.ROLE_ADMIN;
    if (u.roles.some((r) => r.name === RoleType.ROLE_MASTER)) return RoleType.ROLE_MASTER;
    if (u.roles.some((r) => r.name === RoleType.ROLE_USER)) return RoleType.ROLE_USER;
    return null;
  };

  const handleEmployeeSelect = useCallback(
    (value: Employee | Employee[] | null) => {
      setUpdatedUser((prev) => {
        if (!prev) return prev;
        const selectedEmployee = Array.isArray(value) ? value[0] : value;
        return { ...prev, employee: selectedEmployee };
      });
    },
    []
  );

  const pinChangeRequested = canChangePin && showPinChange && (newPin !== "" || newPinConfirm !== "");
  const passwordChangeRequested =
    isAdmin && showForceChangePw && (newPassword !== "" || confirmPassword !== "");

  const userHasChanges = (): boolean => {
    if (!updatedUser || !fetchedUser) return false;
    if (!isAdmin) {
      return (updatedUser.employee?.id ?? null) !== (fetchedUser.employee?.id ?? null);
    }
    return validateUpdateUser(updatedUser, fetchedUser) !== "Brak zmian!";
  };

  const validateAll = (): string | null => {
    if (!updatedUser || !fetchedUser) return "Brak danych użytkownika do aktualizacji!";
    const saveUser = userHasChanges();
    if (!saveUser && !pinChangeRequested && !passwordChangeRequested) return "Brak zmian!";
    if (saveUser && isAdmin) {
      const error = validateUpdateUser(updatedUser, fetchedUser);
      if (error) return error;
      if (needsInitialPin) {
        const pinError = validateNewCompanyPin(companyPin, companyPinConfirm);
        if (pinError) return pinError;
      }
    }
    if (pinChangeRequested) {
      const pinError = validateNewCompanyPin(newPin, newPinConfirm);
      if (pinError) return pinError;
    }
    if (passwordChangeRequested) {
      const passwordError = validateForceChangePasswordForm(newPassword, confirmPassword);
      if (passwordError) return passwordError;
    }
    return null;
  };

  const handleSave = async () => {
    const error = validateAll();
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    if (!updatedUser || !fetchedUser) return;

    const done: string[] = [];
    try {
      if (userHasChanges()) {
        const payload: User = needsInitialPin ? { ...updatedUser, companyPin } : updatedUser;
        const data = await UserService.updateUser(updatedUser.id, payload);
        if (!data) throw new Error("Brak danych po zapisie użytkownika.");
        done.push("dane konta");
      }
      if (pinChangeRequested) {
        await UserService.setCompanyPin(fetchedUser.id, newPin);
        done.push("PIN");
      }
      if (passwordChangeRequested) {
        const message = await AuthService.forceChangePassword(fetchedUser.id, newPassword);
        if (message !== "Password changed successfully") throw new Error(message);
        done.push("hasło");
      }
      refreshUserList();
      onClose();
      showAlert(`Użytkownik ${fetchedUser.username} zaktualizowany!`, AlertType.SUCCESS);
    } catch (err) {
      if (done.length > 0) refreshUserList();
      const reason = apiErrorMessage(err) ?? (err instanceof Error && err.message ? err.message : "Błąd aktualizacji!");
      const saved = done.length > 0 ? ` Zapisano: ${done.join(", ")}.` : "";
      showAlert(`${reason}${saved}`, AlertType.ERROR);
    }
  };

  const targetIsAdmin = fetchedUser?.roles?.some((r) => r.name === RoleType.ROLE_ADMIN) ?? false;
  const canResetMfa = isAdmin && !targetIsAdmin && !!fetchedUser?.mfaEnabled;

  const toggleDevices = async () => {
    if (devicesOpen) {
      setDevicesOpen(false);
      return;
    }
    if (devices !== null) {
      setDevicesOpen(true);
      return;
    }
    if (!fetchedUser) return;
    try {
      setDevices(await MfaService.getUserTrustedDevices(fetchedUser.id));
      setDevicesOpen(true);
    } catch (err) {
      showAlert(apiErrorMessage(err) ?? "Nie udało się pobrać zaufanych urządzeń.", AlertType.ERROR);
    }
  };

  const handleMfaReset = async () => {
    if (!fetchedUser || mfaResetting) return;
    setMfaResetting(true);
    try {
      const response = await MfaService.resetForUser(fetchedUser.id);
      showAlert(response?.message ?? "Zresetowano weryfikację dwuetapową.", AlertType.SUCCESS);
      setConfirmMfaReset(false);
      setDevicesOpen(false);
      setDevices([]);
      setFetchedUser((prev) => (prev ? { ...prev, mfaEnabled: false, mfaEnabledAt: null, trustedDevicesCount: 0 } : prev));
      refreshUserList();
    } catch (err) {
      showAlert(apiErrorMessage(err) ?? "Nie udało się zresetować weryfikacji.", AlertType.ERROR);
    } finally {
      setMfaResetting(false);
    }
  };

  const pinInput = (value: string, setter: (v: string) => void) => (
    <PinInput value={value} onChange={setter} length={COMPANY_PIN_LENGTH} />
  );

  if (!fetchedUser) {
    return null;
  }

  return (
    <Modal
      dirtyValue={[newPassword, confirmPassword, updatedUser, companyPin, newPin]}
      title="Edytuj Użytkownika"
      onClose={onClose}
      size="md"
      onConfirm={handleSave}
      className={className}
    >
      <div className="flex-column g-1">
        <div className="flex align-items-center g-075">
          <img
            className="pf-avatar-sm"
            src={AVAILABLE_AVATARS[fetchedUser.avatar] ?? AVAILABLE_AVATARS["avatar1.png"]}
            alt={fetchedUser.username}
          />
          <span className="pf-value">{fetchedUser.username}</span>
        </div>

        {isAdmin && (
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Role Użytkownika:</span>
          <div className="cat-seg-group flex align-items-center width-fit-content g-025">
            {sortByRoleHierarchy(availableRoles, (r) => r.name).map((role) => (
              <button
                key={role.id}
                type="button"
                className={`cat-seg align-items-center ${
                  role.name === RoleType.ROLE_MASTER ? "cl-master" : ""
                } ${
                  role.name === RoleType.ROLE_ADMIN ? "not-allowed" : "pointer"
                } ${selectedBusinessRole(updatedUser) === role.name ? "active" : ""}`}
                onClick={() => selectRole(role)}
                disabled={role.name === RoleType.ROLE_ADMIN || selectedBusinessRole(updatedUser) === RoleType.ROLE_ADMIN}
              >
                {role.name.replace("ROLE_", "")}
              </button>
            ))}
          </div>
        </div>
        )}

        {needsInitialPin && (
          <div className="pf-force-pw flex-column g-075">
            <span className="pf-pin-hint">Rola MASTER wymaga PIN-u trybu firmowego</span>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">PIN:</span>
              {pinInput(companyPin, setCompanyPin)}
            </div>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Powtórz PIN:</span>
              {pinInput(companyPinConfirm, setCompanyPinConfirm)}
            </div>
          </div>
        )}

        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Pracownik:</span>
          <DropdownSelect<Employee>
            items={employees}
            onChange={handleEmployeeSelect}
            value={updatedUser?.employee}
            placeholder="Nie wybrano"
            multiple={false}
            searchable={false}
            allowNew={false}
          />
        </div>

        {canChangePin && (
          <ActionButton
            text="Zmień PIN trybu firmowego"
            onClick={() => setShowPinChange((prev) => !prev)}
            className="pf-toggle"
          >
            <ChevronDown
              className={`dl-icon-1 pf-toggle-arrow ${showPinChange ? "rotated" : ""}`}
            />
          </ActionButton>
        )}

        {canChangePin && showPinChange && (
          <div className="pf-force-pw flex-column g-075">
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Nowy PIN:</span>
              {pinInput(newPin, setNewPin)}
            </div>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Powtórz PIN:</span>
              {pinInput(newPinConfirm, setNewPinConfirm)}
            </div>
          </div>
        )}

        {canResetMfa && (
          <div className="pf-force-pw pf-mfa-box flex-column g-075">
            <div className="pf-row flex align-items-center space-between g-1">
              <span className="pf-label">Weryfikacja dwuetapowa:</span>
              <span className="pf-mfa-state on">
                Włączona{fetchedUser.mfaEnabledAt ? ` od ${formatDate(fetchedUser.mfaEnabledAt)}` : ""}
              </span>
            </div>
            {(fetchedUser.trustedDevicesCount ?? 0) > 0 ? (
              <div
                className="pf-row eu-devices-toggle flex align-items-center space-between g-1 pointer"
                role="button"
                tabIndex={0}
                aria-expanded={devicesOpen}
                onClick={toggleDevices}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    toggleDevices();
                  }
                }}
              >
                <span className="pf-label flex align-items-center g-025">
                  <ChevronDown className={`dl-icon-1 pf-toggle-arrow ${devicesOpen ? "rotated" : ""}`} />
                  Zaufane urządzenia:
                </span>
                <span className="pf-value">{fetchedUser.trustedDevicesCount}</span>
              </div>
            ) : (
              <div className="pf-row flex align-items-center space-between g-1">
                <span className="pf-label">Zaufane urządzenia:</span>
                <span className="pf-value">0</span>
              </div>
            )}
            {devicesOpen && devices !== null && (
              <div className="eu-devices flex-column">
                {devices.length === 0 ? (
                  <span className="pf-pin-hint">Brak aktywnych zaufanych urządzeń.</span>
                ) : (
                  devices.map((device) => (
                    <div key={device.id} className="eu-device-row">
                      <span className="pf-device-name flex-column mw-0">
                        <span>{device.label}</span>
                        {device.lastIp && <small>IP {device.lastIp}</small>}
                      </span>
                      <span className="eu-device-dates">
                        <span className="eu-device-date-label">Dodane:</span>
                        <span>{formatDate(device.createdAt)}</span>
                        <span className="eu-device-date-label">Ostatnio użyte:</span>
                        <span>{formatRelativeDateTime(device.lastUsedAt)}</span>
                        <span className="eu-device-date-label">Wygasa:</span>
                        <span>{formatDate(device.expiresAt)}</span>
                      </span>
                    </div>
                  ))
                )}
              </div>
            )}
            {confirmMfaReset ? (
              <div className="pf-mfa-confirm flex-column g-075">
                <span className="pf-pin-hint">
                  Reset wyłączy 2FA na tym koncie i usunie klucz, kody zapasowe i zaufane urządzenia. Następne logowanie wymaga tylko hasła.
                </span>
                <div className="flex justify-end g-05">
                  <ActionButton text="Anuluj" disableImg={true} onClick={() => setConfirmMfaReset(false)} />
                  <ActionButton
                    text="Resetuj"
                    disableImg={true}
                    onClick={handleMfaReset}
                    disabled={mfaResetting}
                    className="pf-mfa-off"
                  />
                </div>
              </div>
            ) : (
              <div className="flex justify-end">
                <ActionButton text="Resetuj 2FA" disableImg={true} onClick={() => setConfirmMfaReset(true)} className="pf-mfa-off" />
              </div>
            )}
          </div>
        )}

        {isAdmin && (
        <>
        <ActionButton
          text="Wymuś zmianę hasła"
          onClick={() => setShowForceChangePw((prev) => !prev)}
          className="pf-toggle"
        >
          <ChevronDown
            className={`dl-icon-1 pf-toggle-arrow ${
              showForceChangePw ? "rotated" : ""
            }`}
          />
        </ActionButton>

        {showForceChangePw && (
          <div className="pf-force-pw flex-column g-075">
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
        )}
        </>
        )}
      </div>
    </Modal>
  );
}

export default EditUserModal;
