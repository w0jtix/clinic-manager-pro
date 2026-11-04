import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { CompanyExpense, ExpenseCategoryCode } from "../models/expense";
import { KsefSeller } from "../models/ksef_invoice";
import {
  KsefSellerItemMapping,
  KsefSellerSummary,
  KsefSpendChart,
} from "../models/ksef_seller";

class KsefSellerService {

  static async getById(sellerId: number): Promise<KsefSeller> {
    return await sendApiRequest<KsefSeller>(`ksef/sellers/${sellerId}`, {
      method: "get",
      errorMessage: "Error fetching KSeF seller.",
    });
  }

  static async getByNip(nip: string): Promise<KsefSeller | null> {
    try {
      return await sendApiRequest<KsefSeller>(`ksef/sellers/by-nip/${nip}`, {
        method: "get",
        errorMessage: "Error fetching KSeF seller by NIP.",
      });
    } catch (error: any) {
      if (error?.response?.status === 404) return null;
      throw error;
    }
  }

  static async getExpenses(
    sellerId: number,
    page: number = 0,
    size: number = 20
  ): Promise<{ content: CompanyExpense[]; totalPages: number; totalElements: number; last: boolean }> {
    return await sendApiRequest<{ content: CompanyExpense[]; totalPages: number; totalElements: number; last: boolean }>(
      `ksef/sellers/${sellerId}/expenses?page=${page}&size=${size}`,
      {
        method: "get",
        errorMessage: "Error fetching seller expenses.",
      }
    );
  }

  static async getSummary(sellerId: number): Promise<KsefSellerSummary> {
    return await sendApiRequest<KsefSellerSummary>(`ksef/sellers/${sellerId}/summary`, {
      method: "get",
      errorMessage: "Error fetching KSeF seller summary.",
    });
  }

  static async getSpendChart(sellerId: number, year: number): Promise<KsefSpendChart> {
    return await sendApiRequest<KsefSpendChart>(
      `ksef/sellers/${sellerId}/spend-chart?year=${year}`,
      {
        method: "get",
        errorMessage: "Error fetching KSeF seller spend chart.",
      }
    );
  }

  static async getItemMappings(sellerId: number): Promise<KsefSellerItemMapping[]> {
    return await sendApiRequest<KsefSellerItemMapping[]>(
      `ksef/sellers/${sellerId}/item-mappings`,
      {
        method: "get",
        errorMessage: "Error fetching KSeF seller item mappings.",
      }
    );
  }

  static async deleteItemMapping(sellerId: number, mappingId: number): Promise<void> {
    return await sendApiRequest<void>(
      `ksef/sellers/${sellerId}/item-mappings/${mappingId}`,
      {
        method: "delete",
        errorMessage: "Error deleting KSeF seller item mapping.",
      }
    );
  }

  static async setDefaultCategory(
    sellerId: number,
    category: ExpenseCategoryCode | null
  ): Promise<void> {
    return await sendApiRequest<void>(`ksef/sellers/${sellerId}/default-category`, {
      method: "put",
      body: { category },
      errorMessage: "Error setting KSeF seller default category.",
    });
  }

  static async removeAutoMark(sellerId: number): Promise<void> {
    return await sendApiRequest<void>(`ksef/sellers/${sellerId}/remove-auto-mark`, {
      method: "put",
      errorMessage: "Error removing seller auto mark.",
    });
  }
}

export default KsefSellerService;
