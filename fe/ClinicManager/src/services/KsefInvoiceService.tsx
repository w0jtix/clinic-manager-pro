import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import {
  KsefBookCorrectionRequest,
  KsefInvoice,
  KsefInvoiceCount,
  KsefInvoiceFilterDTO,
  KsefSyncStatus,
  KsefVerifyProductsRequest,
} from "../models/ksef_invoice";
import { ExpenseCategoryCode } from "../models/expense";
import { StockChangePreview } from "../models/stock_change";

class KsefInvoiceService {

  static async sync(): Promise<void> {
    return await sendApiRequest<void>("ksef/invoices/sync", {
      method: "post",
      errorMessage: "Error triggering KSeF sync.",
    });
  }

  static async getSyncStatus(): Promise<KsefSyncStatus> {
    return await sendApiRequest<KsefSyncStatus>("ksef/invoices/sync-status", {
      method: "get",
      errorMessage: "Error fetching KSeF sync status.",
    });
  }

  static async getCount(filter: KsefInvoiceFilterDTO): Promise<KsefInvoiceCount> {
    return await sendApiRequest<KsefInvoiceCount>("ksef/invoices/count", {
      method: "post",
      body: filter,
      errorMessage: "Error fetching KSeF invoice count.",
    });
  }

  static async getInvoices(
    filter: KsefInvoiceFilterDTO,
    page: number = 0,
    size: number = 20
  ): Promise<{ content: KsefInvoice[], totalPages: number, totalElements: number, last: boolean }> {
    return await sendApiRequest<{ content: KsefInvoice[], totalPages: number, totalElements: number, last: boolean }>(
      `ksef/invoices/search?page=${page}&size=${size}`,
      {
        method: "post",
        body: filter,
        errorMessage: "Error fetching KSeF invoices.",
      }
    );
  }

  static async getById(id: number): Promise<KsefInvoice> {
    return await sendApiRequest<KsefInvoice>(`ksef/invoices/${id}`, {
      method: "get",
      errorMessage: "Error fetching KSeF invoice.",
    });
  }

  static async togglePaid(id: number): Promise<KsefInvoice> {
    return await sendApiRequest<KsefInvoice>(`ksef/invoices/${id}/toggle-paid`, {
      method: "put",
      errorMessage: "Error toggling invoice paid status.",
    });
  }

  static async toggleProductInvoice(id: number): Promise<KsefInvoice> {
    return await sendApiRequest<KsefInvoice>(
      `ksef/invoices/${id}/toggle-product-invoice`,
      {
        method: "put",
        errorMessage: "Error toggling product invoice flag.",
      }
    );
  }

  static async setCategory(id: number, category: ExpenseCategoryCode, rememberForSeller: boolean = false): Promise<KsefInvoice> {
    return await sendApiRequest<KsefInvoice>(
      `ksef/invoices/${id}/category?category=${category}&rememberForSeller=${rememberForSeller}`,
      {
        method: "put",
        errorMessage: "Error setting category on KSeF invoice.",
      }
    );
  }

  static async verifyProducts(id: number, request: KsefVerifyProductsRequest): Promise<KsefInvoice> {
    return await sendApiRequest<KsefInvoice>(
      `ksef/invoices/${id}/verify-products`,
      {
        method: "put",
        body: request,
        errorMessage: "Error verifying products on KSeF invoice.",
      }
    );
  }

  static async bookCorrectionPreview(
    id: number,
    request: KsefBookCorrectionRequest
  ): Promise<StockChangePreview> {
    return await sendApiRequest<StockChangePreview>(
      `ksef/invoices/${id}/book-correction/preview`,
      {
        method: "post",
        body: request,
        errorMessage: "Error fetching correction stock preview.",
      }
    );
  }

  static async bookCorrection(id: number, request: KsefBookCorrectionRequest): Promise<KsefInvoice> {
    return await sendApiRequest<KsefInvoice>(
      `ksef/invoices/${id}/book-correction`,
      {
        method: "post",
        body: request,
        errorMessage: "Błąd księgowania korekty.",
      }
    );
  }

}

export default KsefInvoiceService;
