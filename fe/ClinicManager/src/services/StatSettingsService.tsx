import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { StatSettings, NewStatSettings } from "../models/business_settings";

class StatSettingsService {
    static async getSettings(): Promise<StatSettings> {
        return await sendApiRequest<StatSettings> (`stat-settings`, {
            method: "get",
            body: {},
            errorMessage: "Error fetching StatSettings.",
        });
    }

    static async uploadLogo(file: File): Promise<void> {
        const formData = new FormData();
        formData.append("file", file);
        return await sendApiRequest<void>(`stat-settings/logo`, {
            method: "post",
            body: formData,
            errorMessage: "Error uploading company logo.",
        });
    }

    static async deleteLogo(): Promise<void> {
        return await sendApiRequest<void>(`stat-settings/logo`, {
            method: "delete",
            errorMessage: "Error deleting company logo.",
        });
    }

    static async getLogo(): Promise<Blob> {
        return await sendApiRequest<Blob>(`stat-settings/logo`, {
            method: "get",
            responseType: "blob",
            errorMessage: "Error fetching company logo.",
        });
    }

    static async uploadFooterLogo(file: File): Promise<void> {
        const formData = new FormData();
        formData.append("file", file);
        return await sendApiRequest<void>(`stat-settings/email-footer-logo`, {
            method: "post",
            body: formData,
            errorMessage: "Error uploading email footer logo.",
        });
    }

    static async deleteFooterLogo(): Promise<void> {
        return await sendApiRequest<void>(`stat-settings/email-footer-logo`, {
            method: "delete",
            errorMessage: "Error deleting email footer logo.",
        });
    }

    static async getFooterLogo(): Promise<Blob> {
        return await sendApiRequest<Blob>(`stat-settings/email-footer-logo`, {
            method: "get",
            responseType: "blob",
            errorMessage: "Error fetching email footer logo.",
        });
    }

    static async updateSettings(
        settings: NewStatSettings
    ): Promise <StatSettings> {
        return await sendApiRequest<StatSettings>(`stat-settings`, {
            method: "put",
            body: settings,
            errorMessage: "Error updating StatSettings.",
        });
    }
}
export default StatSettingsService;