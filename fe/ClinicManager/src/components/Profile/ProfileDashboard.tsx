import { useState, useEffect, useCallback } from "react";
import { CirclePlus, ImageUp } from "lucide-react";
import AuthService from "../../services/AuthService";
import { AVAILABLE_AVATARS } from "../../constants/avatars";
import ActionButton from "../ActionButton";
import NavigationBar from "../NavigationBar";
import PasswordPanel from "./PasswordPanel";
import UserDataList from "./UserDataList";
import AvatarModal from "../Popups/AvatarModal";
import { User, Role, JwtUser, RoleType } from "../../models/login";
import UserService from "../../services/UserService";
import EditUserModal from "../Popups/EditUserModal";
import RoleService from "../../services/RoleService";
import { AlertType } from "../../models/alert";
import { useAlert } from "../Alert/AlertProvider";
import { validateUpdateUser } from "../../utils/validators";
import { useUser } from "../User/UserProvider";
import DropdownSelect from "../DropdownSelect";
import EmployeeService from "../../services/EmployeeService";
import { Employee } from "../../models/employee";
import UserModal from "../Popups/UserModal";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import CompanyModePanel from "./CompanyModePanel";
import CompanyPinPanel from "./CompanyPinPanel";
import useMfaSection from "./useMfaSection";
import { apiErrorMessage } from "../../utils/apiError";
import { isCompanyModeActive, isMasterAccount } from "../../utils/roles";
import { useRoles } from "../../hooks/useRoles";

export function ProfileDashboard() {
  const { user, refreshUser } = useUser();
  const { showAlert } = useAlert();
  const { isAdmin, isCompany } = useRoles();
  const showCompanyMode = isMasterAccount(user);
  const showCompanyPin = isCompanyModeActive(user);
  const showMfa = isAdmin || showCompanyPin;
  const mfa = useMfaSection(showMfa);
  const { isMobile } = useBreakpoint();

  const [isAvatarModalOpen, setIsAvatarModalOpen] = useState<boolean>(false);
  const [users, setUsers] = useState<User[]>([]);
  const [availableRoles, setAvailableRoles] = useState<Role[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [editUserId, setEditUserId] = useState<number | string | null>(null);
  const [updatedUser, setUpdatedUser] = useState<User | null>(null);
  const [savedUser, setSavedUser] = useState<User | null>(null);
  const [isCreateUserPopupOpen, setIsCreateUserPopupOpen] =
    useState<boolean>(false);

  const fetchUsers = async () => {
    try {
      const response = await UserService.getAllUsers();
      setUsers(response);
    } catch (err) {
      showAlert("Błąd", AlertType.ERROR);
      console.error("Błąd podczas pobierania użytkowników!", err);
    }
  };

  const fetchEmployees = async () => {
    EmployeeService.getAllEmployees()
      .then((data) => setEmployees(data))
      .catch((error) => {
        setEmployees([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching employees:", error);
      });
  };

  const fetchRoles = async () => {
    try {
      const response = await RoleService.getAllRoles();
      setAvailableRoles(response);
    } catch (err) {
      showAlert("Błąd", AlertType.ERROR);
      console.error("Błąd podczas pobierania roli!", err);
    }
  };

  const fetchCurrentUser = async () => {
    const jwtUser = AuthService.getCurrentUser() as JwtUser;
    if (!jwtUser?.id) return;

    try {
      const userData = await UserService.getCurrentUser();
      setUpdatedUser(userData);
      setSavedUser(userData);
    } catch (error) {
      console.error("Error fetching current user:", error);
      showAlert("Błąd pobierania danych użytkownika!", AlertType.ERROR);
    }
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

  const handleAvatarChange = useCallback((avatar: string) => {
    setUpdatedUser((prev) => (prev ? { ...prev, avatar } : prev));
    setIsAvatarModalOpen(false);
  }, []);

  const handleEditUser = async () => {
    if (!updatedUser) return;
    try {
      const data = await UserService.updateUser(updatedUser.id, updatedUser);

      if (!data) {
        showAlert("Błąd aktualizacji - brak danych!", AlertType.ERROR);
        return;
      }

      AuthService.setCurrentUser(data);
      refreshUser();
      setSavedUser(data);

      if (isCompany) {
        await fetchUsers();
      }

      showAlert(
        `Użytkownik ${updatedUser.username} zaktualizowany!`,
        AlertType.SUCCESS
      );
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Błąd aktualizacji!", AlertType.ERROR);
    }
  };

  const handleValidateUpdatedUser = () => {
    if (!updatedUser) {
      showAlert("Brak danych użytkownika do aktualizacji!", AlertType.ERROR);
      return;
    }
    const error = validateUpdateUser(updatedUser, savedUser);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }

    handleEditUser();
  };

  useEffect(() => {
    refreshUser();
    fetchCurrentUser();

    if (isCompany) {
      fetchUsers();
      fetchEmployees();
    }
    if (isAdmin) {
      fetchRoles();
    }
  }, [isCompany, isAdmin]);

  const avatarSrc = updatedUser?.avatar
    ? AVAILABLE_AVATARS[updatedUser.avatar]
    : AVAILABLE_AVATARS["avatar1.png"];

  const profilePanel = (
    <section className="pf-panel flex-column g-1">
      <h2 className="pf-panel-title m-0">Profil</h2>

      <div className="pf-panel-body flex align-items-center g-2">
        <div className="pf-avatar-block flex-column align-items-center g-075">
          <img src={avatarSrc} alt="Avatar" className="pf-avatar" />
          <ActionButton
            text={"Zmień Avatar"}
            onClick={() => setIsAvatarModalOpen(true)}
          >
            <ImageUp className="dl-icon-1" />
          </ActionButton>
        </div>

        <div className="pf-fields align-self-start f-1 flex-column g-075">
          <div className="pf-row flex align-items-center g-1">
            <span className="pf-label">Nazwa użytkownika:</span>
            <span className="pf-value">{user?.username}</span>
          </div>
          {isCompany && (
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
          )}
        </div>
      </div>

      <div className="flex justify-end">
        <ActionButton
          text={"Zapisz Zmiany"}
          onClick={handleValidateUpdatedUser}
        />
      </div>
    </section>
  );

  const usersPanel = isCompany ? (
    <section
      className={`pf-panel flex-column g-1 ${
        isMobile ? "" : "f-1 min-height-0"
      }`}
    >
      <div className="flex align-items-center space-between g-1">
        <h2 className="pf-panel-title m-0">Użytkownicy</h2>
        {isAdmin && (
          <ActionButton
            text={isMobile ? "Użytkownik" : "Nowy Użytkownik"}
            iconTitle={"Nowy Użytkownik"}
            onClick={() => setIsCreateUserPopupOpen(true)}
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        )}
      </div>

      <UserDataList
        users={users}
        currentUserId={user?.id}
        onEdit={(userId) => setEditUserId(userId)}
        showRoles={isCompany}
        showMfa={isAdmin}
        canEdit={(u) => isAdmin || !(u.roles ?? []).some((r) => r.name === RoleType.ROLE_ADMIN)}
      />
    </section>
  ) : null;

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <div className="pf-content width-93 flex-column f-1 min-height-0 g-15 mt-1 mb-2">
          {isMobile ? (
            <>
              {showCompanyMode && <CompanyModePanel />}
              {profilePanel}
              {mfa.tile}
              {mfa.devicesPanel}
              {usersPanel}
              <PasswordPanel onPasswordChanged={showMfa ? mfa.refresh : undefined} />
              {showCompanyPin && <CompanyPinPanel />}
            </>
          ) : (
            <>
              <div
                className={`pf-top ${showCompanyMode ? "pf-top-company" : ""} ${
                  showCompanyPin ? "pf-top-pin" : ""
                } ${isAdmin && mfa.tile ? "pf-top-mfa" : ""}`}
              >
                {showCompanyMode && <CompanyModePanel />}
                {profilePanel}
                {isAdmin && mfa.tile}
                <PasswordPanel onPasswordChanged={showMfa ? mfa.refresh : undefined} />
                {showCompanyPin && <CompanyPinPanel />}
              </div>
              {isAdmin
                ? mfa.devicesPanel
                : mfa.tile && (
                    <div className="pf-mfa-row flex g-15">
                      {mfa.tile}
                      {mfa.devicesPanel}
                    </div>
                  )}
              {usersPanel}
            </>
          )}
        </div>

        {mfa.modals}
        {isAvatarModalOpen && (
          <AvatarModal
            currentAvatar={updatedUser?.avatar}
            onSelect={handleAvatarChange}
            onClose={() => setIsAvatarModalOpen(false)}
          />
        )}
        {editUserId != null && (
          <EditUserModal
            onClose={() => setEditUserId(null)}
            userId={editUserId}
            availableRoles={availableRoles}
            refreshUserList={fetchUsers}
            employees={employees}
            isAdmin={isAdmin}
          />
        )}
        {isCreateUserPopupOpen && (
          <UserModal
            onClose={() => {
              setIsCreateUserPopupOpen(false);
              fetchUsers();
            }}
          />
        )}
      </div>
    </div>
  );
}

export default ProfileDashboard;
