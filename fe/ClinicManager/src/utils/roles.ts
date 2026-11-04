import { JwtUser, RoleType } from "../models/login";

export const ROLE_ORDER: RoleType[] = [
  RoleType.ROLE_ADMIN,
  RoleType.ROLE_COMPANY,
  RoleType.ROLE_MASTER,
  RoleType.ROLE_USER,
];

export const sortByRoleHierarchy = <T>(items: T[], getRole: (item: T) => RoleType): T[] =>
  [...items].sort((a, b) => ROLE_ORDER.indexOf(getRole(a)) - ROLE_ORDER.indexOf(getRole(b)));

export const hasRole = (user: JwtUser | undefined | null, role: RoleType): boolean =>
  user?.roles?.includes(role) ?? false;

export const isMasterAccount = (user: JwtUser | undefined | null): boolean =>
  hasRole(user, RoleType.ROLE_MASTER) && !hasRole(user, RoleType.ROLE_ADMIN);

export const isCompanyModeActive = (user: JwtUser | undefined | null): boolean =>
  isMasterAccount(user) && user?.companyMode === true;
