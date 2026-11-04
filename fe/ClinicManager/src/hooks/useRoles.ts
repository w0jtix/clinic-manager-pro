import { useUser } from "../components/User/UserProvider";
import { RoleType } from "../models/login";
import { hasRole } from "../utils/roles";

export function useRoles() {
  const { user } = useUser();
  return {
    isAdmin: hasRole(user, RoleType.ROLE_ADMIN),
    isCompany: hasRole(user, RoleType.ROLE_COMPANY),
    isMaster: hasRole(user, RoleType.ROLE_MASTER),
  };
}
