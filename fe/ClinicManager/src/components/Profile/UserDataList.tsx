import { useMemo } from "react";
import { SquarePen, ShieldCheck } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import { AVAILABLE_AVATARS } from "../../constants/avatars";
import { User } from "../../models/login";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { sortByRoleHierarchy } from "../../utils/roles";

export interface UserDataListProps {
  users: User[];
  currentUserId?: number | string;
  onEdit: (userId: number | string) => void;
  showRoles?: boolean;
  showMfa?: boolean;
  canEdit?: (user: User) => boolean;
  isLoading?: boolean;
  className?: string;
}

export function UserDataList({
  users,
  currentUserId,
  onEdit,
  showRoles = true,
  showMfa = false,
  canEdit,
  isLoading = false,
  className = "",
}: UserDataListProps) {
  const { isMobile, isCompact } = useBreakpoint();

  const columns = useMemo<Column<User>[]>(
    () => [
      {
        key: "username",
        header: "Użytkownik",
        grow: true,
        render: (u) => (
          <span className="flex align-items-center g-075">
            <img
              src={AVAILABLE_AVATARS[u.avatar] ?? AVAILABLE_AVATARS["avatar1.png"]}
              alt={u.username}
              className="pf-avatar-sm"
            />
            <span className="dl-name">{u.username}</span>
          </span>
        ),
      },
      {
        key: "employee",
        header: "Pracownik",
        width: isCompact ? "3.5rem" : isMobile ? "5rem" : "10rem",
        align: "center",
        render: (u) => (
          <span className="dl-dim">{u.employee?.name ?? "—"}</span>
        ),
      },
      ...(showRoles ? [{
        key: "roles",
        header: "Role",
        width: isCompact ? "4rem" : isMobile ? "6rem" : "12rem",
        align: "center",
        render: (u) => (
          <span
            className={`flex align-items-center justify-center ${
              isMobile ? "flex-column g-025" : "g-05"
            }`}
          >
            {sortByRoleHierarchy(u.roles ?? [], (r) => r.name).map((role) => (
              <span
                key={role.id}
                className={`pf-role ${
                  role.name === "ROLE_ADMIN" ? "admin" : role.name === "ROLE_MASTER" ? "master" : ""
                }`}
              >
                {role.name.replace("ROLE_", "")}
              </span>
            ))}
          </span>
        ),
      } as Column<User>] : []),
      ...(showMfa ? [{
        key: "mfa",
        header: "2FA",
        width: isMobile ? "2rem" : "4rem",
        align: "center",
        render: (u) =>
          u.mfaEnabled ? (
            <span className="flex align-items-center justify-center" title="Weryfikacja dwuetapowa włączona">
              <ShieldCheck className="dl-icon c-green" />
            </span>
          ) : null,
      } as Column<User>] : []),
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "2rem" : "5rem",
        align: "center",
        render: (u) =>
          u.id !== currentUserId && (canEdit ? canEdit(u) : true) ? (
            <ActionButton
              iconTitle={"Edytuj Użytkownika"}
              onClick={() => onEdit(u.id)}
              disableText={true}
              disableBorder={true}
            >
              <SquarePen className="dl-icon c-blue" />
            </ActionButton>
          ) : null,
      },
    ],
    [currentUserId, onEdit, isMobile, isCompact, showRoles, showMfa, canEdit]
  );

  return (
    <DataList<User>
      columns={columns}
      items={users}
      getRowKey={(u) => u.id}
      isLoading={isLoading}
      hideHeader={isMobile}
      emptyText="Brak użytkowników"
      className={className}
    />
  );
}

export default UserDataList;
