import { RoleType } from "./login";

export interface RegisterRequest {
    username: string;
    password: string;
    role: RoleType[];
    companyPin?: string;
}

export interface ChangeCompanyPinRequest {
    currentPin: string;
    newPin: string;
}

export interface MessageResponse {
    message: string;
}

export interface ChangePasswordRequest {
    oldPassword: string;
    newPassword: string;
}

export interface ForceChangePasswordRequest {
    userId: number;
    newPassword: string;
}