import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { BackupCodesResponse, MfaSetup, MfaStatus, TrustedDevice } from "../models/mfa";
import { MessageResponse } from "../models/register";

class MfaService {

    static async getStatus(): Promise<MfaStatus> {
        return await sendApiRequest<MfaStatus>("user/me/mfa", {
            method: "get",
            errorMessage: "Error fetching 2FA status.",
        });
    }

    static async setup(): Promise<MfaSetup> {
        return await sendApiRequest<MfaSetup>("user/me/mfa/setup", {
            method: "post",
            errorMessage: "Error starting 2FA setup.",
        });
    }

    static async enable(code: string): Promise<BackupCodesResponse> {
        return await sendApiRequest<BackupCodesResponse>("user/me/mfa/enable", {
            method: "post",
            body: { code },
            errorMessage: "Error enabling 2FA.",
        });
    }

    static async disable(code: string): Promise<MessageResponse> {
        return await sendApiRequest<MessageResponse>("user/me/mfa/disable", {
            method: "post",
            body: { code },
            errorMessage: "Error disabling 2FA.",
        });
    }

    static async regenerateBackupCodes(code: string): Promise<BackupCodesResponse> {
        return await sendApiRequest<BackupCodesResponse>("user/me/mfa/backup-codes", {
            method: "post",
            body: { code },
            errorMessage: "Error generating backup codes.",
        });
    }

    static async confirmBackupCodes(): Promise<MessageResponse> {
        return await sendApiRequest<MessageResponse>("user/me/mfa/backup-codes/confirm", {
            method: "post",
            errorMessage: "Error confirming backup codes.",
        });
    }

    static async revokeDevice(id: number): Promise<void> {
        await sendApiRequest<void>(`user/me/mfa/trusted-devices/${id}`, {
            method: "delete",
            errorMessage: "Error revoking trusted device.",
        });
    }

    static async revokeAllDevices(): Promise<void> {
        await sendApiRequest<void>("user/me/mfa/trusted-devices", {
            method: "delete",
            errorMessage: "Error revoking trusted devices.",
        });
    }

    static async getUserTrustedDevices(userId: number): Promise<TrustedDevice[]> {
        return await sendApiRequest<TrustedDevice[]>(`user/${userId}/mfa/trusted-devices`, {
            method: "get",
            errorMessage: "Error fetching trusted devices.",
        });
    }

    static async resetForUser(userId: number): Promise<MessageResponse> {
        return await sendApiRequest<MessageResponse>(`user/${userId}/mfa/reset`, {
            method: "post",
            errorMessage: "Error resetting 2FA.",
        });
    }
}

export default MfaService;
