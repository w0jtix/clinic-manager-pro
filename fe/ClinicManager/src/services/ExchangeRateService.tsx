import { ExchangeRateWindow, ExpenseCurrency } from "../models/expense";
import { sendApiRequest } from "../components/send-api-request/SendApiRequest";

class ExchangeRateService {

  static async getRates(currency: ExpenseCurrency, date: string): Promise<ExchangeRateWindow> {
    return await sendApiRequest<ExchangeRateWindow>(`exchange-rates?currency=${currency}&date=${date}`, {
      method: "get",
      errorMessage: "Error fetching exchange rates.",
    });
  }
}

export default ExchangeRateService;
