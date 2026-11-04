import { Employee } from "../models/employee";

export interface LoginRequest {
    username: string;
    password: string;
}


export interface JwtUser {
    token: string;
    type: string;
    id: number;
    username: string;
    avatar: string;
    roles: RoleType[];
    employee: Employee | null;
    companyMode?: boolean;
    remainingBackupCodes?: number | null;
}


export interface User {
    id: number;
    username: string;
    avatar: string;
    roles: Role[];
    employee: Employee | null;
    hasCompanyPin?: boolean;
    companyPin?: string;
    mfaEnabled?: boolean;
    mfaEnabledAt?: string | null;
    trustedDevicesCount?: number;
}

export interface UserAvatar {
    id: number;
    avatar: string;
}

export enum RoleType {
    ROLE_USER = "ROLE_USER",
    ROLE_MASTER = "ROLE_MASTER",
    ROLE_COMPANY = "ROLE_COMPANY",
    ROLE_ADMIN = "ROLE_ADMIN",
}

export interface Role {
    id: number;
    name: RoleType;
}