import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { ClinicDevice, DeviceRegistrationResponse, NewRegistrationCode, RegistrationCode } from "../models/device";

class DeviceService {

    static async getDevices(includeRevoked = false): Promise<ClinicDevice[]> {
        return await sendApiRequest<ClinicDevice[]>(`devices?includeRevoked=${includeRevoked}`, {
            method: "get",
            errorMessage: "Error fetching devices.",
        });
    }

    static async getCodes(): Promise<RegistrationCode[]> {
        return await sendApiRequest<RegistrationCode[]>("devices/codes", {
            method: "get",
            errorMessage: "Error fetching registration codes.",
        });
    }

    static async createCode(request: NewRegistrationCode): Promise<RegistrationCode> {
        return await sendApiRequest<RegistrationCode>("devices/codes", {
            method: "post",
            body: request,
            errorMessage: "Error creating registration code.",
        });
    }

    static async cancelCode(id: number): Promise<void> {
        await sendApiRequest<void>(`devices/codes/${id}`, {
            method: "delete",
            errorMessage: "Error cancelling registration code.",
        });
    }

    static async renameDevice(id: number, name: string): Promise<ClinicDevice> {
        return await sendApiRequest<ClinicDevice>(`devices/${id}`, {
            method: "put",
            body: { name },
            errorMessage: "Error renaming device.",
        });
    }

    static async revokeDevice(id: number): Promise<void> {
        await sendApiRequest<void>(`devices/${id}`, {
            method: "delete",
            errorMessage: "Error revoking device.",
        });
    }

    static async deleteDevice(id: number): Promise<void> {
        await sendApiRequest<void>(`devices/${id}/permanent`, {
            method: "delete",
            errorMessage: "Error deleting device.",
        });
    }

    static async registerDevice(code: string): Promise<DeviceRegistrationResponse> {
        return await sendApiRequest<DeviceRegistrationResponse>("auth/device/register", {
            method: "post",
            body: { code },
            skipAuthHeader: true,
            errorMessage: "Error registering device.",
        });
    }
}

export default DeviceService;
