import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import {
  NamedInvoice,
  NamedInvoiceDraft,
  NewNamedInvoice,
  SendNamedInvoiceRequest,
} from "../models/named_invoice";

class NamedInvoiceService {

  static async getDraft(visitId: number): Promise<NamedInvoiceDraft> {
    return await sendApiRequest<NamedInvoiceDraft>(
      `named-invoices/draft/${visitId}`,
      {
        method: "get",
        errorMessage: "Error fetching named invoice draft.",
      }
    );
  }

  static async create(invoice: NewNamedInvoice): Promise<NamedInvoice> {
    return await sendApiRequest<NamedInvoice>("named-invoices", {
      method: "post",
      body: invoice,
      errorMessage: "Error creating named invoice.",
    });
  }

  static async update(
    id: number,
    invoice: NewNamedInvoice
  ): Promise<NamedInvoice> {
    return await sendApiRequest<NamedInvoice>(`named-invoices/${id}`, {
      method: "put",
      body: invoice,
      errorMessage: "Error updating named invoice.",
    });
  }

  static async delete(id: number): Promise<void> {
    return await sendApiRequest<void>(`named-invoices/${id}`, {
      method: "delete",
      errorMessage: "Error deleting named invoice.",
    });
  }

  static async getById(id: number): Promise<NamedInvoice> {
    return await sendApiRequest<NamedInvoice>(`named-invoices/${id}`, {
      method: "get",
      errorMessage: "Error fetching named invoice.",
    });
  }

  static async getByClient(
    clientId: number,
    page: number = 0,
    size: number = 30
  ): Promise<{ content: NamedInvoice[], totalPages: number, totalElements: number, last: boolean }> {
    return await sendApiRequest<{ content: NamedInvoice[], totalPages: number, totalElements: number, last: boolean }>(
      `named-invoices/by-client/${clientId}?page=${page}&size=${size}`,
      {
        method: "get",
        errorMessage: "Error fetching client named invoices.",
      }
    );
  }

  static async getPdf(id: number): Promise<Blob> {
    return await sendApiRequest<Blob>(`named-invoices/${id}/pdf`, {
      method: "get",
      responseType: "blob",
      errorMessage: "Error generating invoice PDF.",
    });
  }

  static async sendEmail(
    id: number,
    request: SendNamedInvoiceRequest
  ): Promise<NamedInvoice> {
    return await sendApiRequest<NamedInvoice>(`named-invoices/${id}/send`, {
      method: "post",
      body: request,
      errorMessage: "Error sending invoice email.",
    });
  }

  static async getByPeriod(
    year: number,
    month: number
  ): Promise<NamedInvoice[]> {
    return await sendApiRequest<NamedInvoice[]>(
      `named-invoices/by-period?year=${year}&month=${month}`,
      {
        method: "get",
        errorMessage: "Error fetching named invoices for period.",
      }
    );
  }

}

export default NamedInvoiceService;
