import { JwtUser, LoginRequest } from "../models/login";
import { suppressUnsavedChangesPrompt } from "../utils/unsavedChangesPrompt";
import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { RoleType, User, Role } from "../models/login";
import { ChangePasswordRequest, ForceChangePasswordRequest, MessageResponse, RegisterRequest } from "../models/register";
import { MfaChallenge, isMfaChallenge } from "../models/mfa";

const RETURN_TO_KEY = "auth.returnTo";
const SYNC_THROTTLE_MS = 5000;

export const USER_UPDATED_EVENT = "clinicmanager:user-updated";

let lastSyncAt = 0;
let syncInFlight: Promise<void> | null = null;

function isUser(obj: any): obj is User {
  return (
    obj &&
    Array.isArray(obj.roles) &&
    obj.roles.length > 0 &&
    typeof obj.roles[0] === "object" &&
    "name" in obj.roles[0]
  );
}

class AuthService {

    async login(
        username: string,
        password: string
    ): Promise<JwtUser | MfaChallenge | undefined> {
        const response = await sendApiRequest<JwtUser | MfaChallenge>('auth/login', {
            method:"post",
            body: { username, password } as LoginRequest,
            skipAuthHeader: true,
            errorMessage: "Error while signing in."
        });

        if (isMfaChallenge(response)) {
            return response;
        }
        return this.storeLoggedUser(response);
    }

    async verifyMfa(mfaToken: string, code: string, rememberDevice: boolean): Promise<JwtUser | undefined> {
        const jwtUser = await sendApiRequest<JwtUser>('auth/mfa/verify', {
            method: "post",
            body: { mfaToken, code, rememberDevice },
            skipAuthHeader: true,
            errorMessage: "Error while verifying 2FA code.",
        });
        return this.storeLoggedUser(jwtUser);
    }

    async verifyBackupCode(mfaToken: string, code: string, rememberDevice: boolean): Promise<JwtUser | undefined> {
        const jwtUser = await sendApiRequest<JwtUser>('auth/mfa/verify-backup', {
            method: "post",
            body: { mfaToken, code, rememberDevice },
            skipAuthHeader: true,
            errorMessage: "Error while verifying backup code.",
        });
        return this.storeLoggedUser(jwtUser);
    }

    private storeLoggedUser(jwtUser: JwtUser | undefined): JwtUser | undefined {
        if (jwtUser?.token) {
            const stored: JwtUser = { ...jwtUser };
            delete stored.remainingBackupCodes;
            localStorage.setItem('user', JSON.stringify(stored));
        }
        return jwtUser;
    }

    async logout({ rememberLocation = false }: { rememberLocation?: boolean } = {}): Promise<void> {
        if (rememberLocation) {
            const path = window.location.pathname + window.location.search;
            if (path.startsWith("/") && !path.startsWith("/login")) {
                sessionStorage.setItem(RETURN_TO_KEY, path);
            }
        } else {
            sessionStorage.removeItem(RETURN_TO_KEY);
        }
        try {
            await sendApiRequest('auth/logout', { method: "post" });
        } catch (_) {
        } finally {
            localStorage.removeItem("user");
            suppressUnsavedChangesPrompt();
            window.location.replace("/login");
        }
    }

    async register(
        username: string,
        password: string,
        role: RoleType[],
        companyPin?: string,
    ): Promise<string> {
        return sendApiRequest<MessageResponse>('auth/register', {
            method: "post",
            body: { username, password, role, companyPin } as RegisterRequest,
            errorMessage: "Error while registering new user.",
        })
        .then((response) => {
            return response;
        })
        .then((message: MessageResponse) => {
            return message.message;
        });
    }

    async changePassword(
        oldPassword: string,
        newPassword: string
    ): Promise<string> {
        return sendApiRequest<MessageResponse>('auth/change-password', {
            method: "post",
            body: { oldPassword, newPassword } as ChangePasswordRequest,
            errorMessage: "Error while changing password.",
        })
        .then((response) => {
            return response;
        })
        .then((message: MessageResponse) => {
            return message.message;
        })
    }

    async forceChangePassword(
        userId: number,
        newPassword: string
    ): Promise<string> {
        return sendApiRequest<MessageResponse>('auth/force-change-password', {
            method: "post",
            body: { userId, newPassword } as ForceChangePasswordRequest,
            errorMessage: "Error while force chaning password.",
        })
        .then((response) => {
            return response;
        })
        .then((message: MessageResponse) => {
            return message.message;
        })
    }

    async elevate(pin: string): Promise<JwtUser> {
        const jwtUser = await sendApiRequest<JwtUser>('auth/elevate', {
            method: "post",
            body: { pin },
            errorMessage: "Error while enabling company mode.",
        });
        localStorage.setItem('user', JSON.stringify(jwtUser));
        return jwtUser;
    }

    async dropElevation(): Promise<JwtUser> {
        const jwtUser = await sendApiRequest<JwtUser>('auth/drop-elevation', {
            method: "post",
            errorMessage: "Error while disabling company mode.",
        });
        localStorage.setItem('user', JSON.stringify(jwtUser));
        return jwtUser;
    }

    syncCurrentUser(): Promise<void> {
        if (syncInFlight) return syncInFlight;
        const token = this.getCurrentUser()?.token;
        if (!token || Date.now() - lastSyncAt < SYNC_THROTTLE_MS) return Promise.resolve();
        lastSyncAt = Date.now();
        syncInFlight = sendApiRequest<Omit<JwtUser, "token" | "type">>('auth/me', {
            method: "get",
            errorMessage: "Error while syncing current user.",
        })
        .then((me) => {
            const current = this.getCurrentUser();
            if (!me || !current || current.token !== token) return;
            const next: JwtUser = {
                ...current,
                id: me.id,
                username: me.username,
                avatar: me.avatar,
                roles: me.roles,
                employee: me.employee,
                companyMode: me.companyMode ?? false,
            };
            if (JSON.stringify(next) === JSON.stringify(current)) return;
            localStorage.setItem("user", JSON.stringify(next));
            window.dispatchEvent(new Event(USER_UPDATED_EVENT));
        })
        .catch(() => {})
        .finally(() => {
            syncInFlight = null;
        });
        return syncInFlight;
    }

    consumeReturnTo(): string | null {
        const path = sessionStorage.getItem(RETURN_TO_KEY);
        sessionStorage.removeItem(RETURN_TO_KEY);
        return path && path.startsWith("/") && !path.startsWith("//") ? path : null;
    }

    getCurrentUser(): JwtUser | undefined {
        const user = localStorage.getItem("user");
        let result: JwtUser | undefined;
        if (user) {
            result = JSON.parse(user);
        }
        return result;
    }

    getAuthHeader(): { key: string; value: string } {
    
        const user = this.getCurrentUser();
        if(user?.token) {
            return { key: 'Authorization', value: user.type + ' ' + user.token };
        } else {
            return { key: 'Authorization', value: ''};
        }
    }

    setCurrentUser(user: JwtUser | User) {
    let jwtUser: JwtUser;

    if (isUser(user)) {
      const current = this.getCurrentUser();
      jwtUser = {
        token: current?.token || "",
        type: current?.type || "Bearer",
        id: user.id,
        username: user.username,
        avatar: user.avatar,
        roles: current?.roles ?? user.roles.map((r:Role) => r.name),
        employee: user.employee,
        companyMode: current?.companyMode ?? false,
      };
    } else {
      jwtUser = user as JwtUser;
    }
    localStorage.setItem("user", JSON.stringify(jwtUser));
    }
}

export default new AuthService();