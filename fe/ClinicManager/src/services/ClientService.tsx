import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import {
    Client,
    ClientFilterDTO,
    ClientSpendChart,
    ClientSummary,
    NewClient,
} from "../models/client";
import { ClientDuplicateCheck, ClientIdentity } from "../models/client-card";

class ClientService {

    static async getClientById(clientId: number): Promise<Client> {
        return await sendApiRequest<Client>(`clients/${clientId}`, {
            method: "get",
            errorMessage: "Error fetching Client."
        })
    }

    static async getClientSummary(clientId: number): Promise<ClientSummary> {
        return await sendApiRequest<ClientSummary>(`clients/${clientId}/summary`, {
            method: "get",
            errorMessage: "Error fetching Client summary."
        })
    }

    static async getClientSpendChart(
        clientId: number,
        year: number
    ): Promise<ClientSpendChart> {
        return await sendApiRequest<ClientSpendChart>(
            `clients/${clientId}/spend-chart?year=${year}`,
            {
                method: "get",
                errorMessage: "Error fetching Client spend chart."
            }
        )
    }

    static async getClients(filter?: ClientFilterDTO): Promise<Client[]> {
        return await sendApiRequest<Client[]>(`clients/search`, {
            method: "post",
            body: filter ?? {keyword: ""},
            errorMessage: "Error fetching Clients."
        })
    }

    static async createClient(service: NewClient): Promise<Client> {
        return await sendApiRequest<Client>( `clients`, {
        method: "post",
        body: service,
        errorMessage: "Error creating new Client."
    });
    }

    static async updateClient(id: string | number, service: NewClient): Promise<Client> {
        return await sendApiRequest<Client> (`clients/${id}`, {
            method: "put",
            body: service,
            errorMessage: "Error updating Client."
        })
    }

    static async checkDuplicates(
        firstName: string,
        lastName: string,
        phoneNumber: string | null,
        excludeId?: number | null
    ): Promise<ClientDuplicateCheck> {
        const params = new URLSearchParams({ firstName, lastName });
        if (phoneNumber) params.append("phoneNumber", phoneNumber);
        if (excludeId != null) params.append("excludeId", String(excludeId));
        return await sendApiRequest<ClientDuplicateCheck>(`clients/duplicates?${params.toString()}`, {
            method: "get",
            errorMessage: "Error checking Client duplicates."
        })
    }

    static async restoreClient(id: number, identity?: ClientIdentity): Promise<Client> {
        return await sendApiRequest<Client>(`clients/${id}/restore`, {
            method: "post",
            body: identity ?? {},
            errorMessage: "Error restoring Client."
        })
    }

    static async deleteClient(id: string | number): Promise<void> {
        return await sendApiRequest<void>(`clients/${id}`, {
            method: "delete",
            errorMessage: "Error deleting Client."
        })
    }
}

export default ClientService;