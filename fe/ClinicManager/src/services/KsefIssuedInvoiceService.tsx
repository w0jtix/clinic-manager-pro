import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import {
  KsefIssuedInvoice,
  KsefIssuedInvoiceCount,
  KsefIssuedInvoiceFilterDTO,
} from "../models/ksef_issued_invoice";

class KsefIssuedInvoiceService {

  static async getCount(
    filter: KsefIssuedInvoiceFilterDTO
  ): Promise<KsefIssuedInvoiceCount> {
    return await sendApiRequest<KsefIssuedInvoiceCount>(
      "ksef/issued-invoices/count",
      {
        method: "post",
        body: filter,
        errorMessage: "Error fetching issued invoice count.",
      }
    );
  }

  static async getInvoices(
    filter: KsefIssuedInvoiceFilterDTO,
    page: number = 0,
    size: number = 30
  ): Promise<{ content: KsefIssuedInvoice[], totalPages: number, totalElements: number, last: boolean }> {
    return await sendApiRequest<{ content: KsefIssuedInvoice[], totalPages: number, totalElements: number, last: boolean }>(
      `ksef/issued-invoices/search?page=${page}&size=${size}`,
      {
        method: "post",
        body: filter,
        errorMessage: "Error fetching issued invoices.",
      }
    );
  }

  static async getById(id: number): Promise<KsefIssuedInvoice> {
    return await sendApiRequest<KsefIssuedInvoice>(
      `ksef/issued-invoices/${id}`,
      {
        method: "get",
        errorMessage: "Error fetching issued invoice.",
      }
    );
  }

}

export default KsefIssuedInvoiceService;
