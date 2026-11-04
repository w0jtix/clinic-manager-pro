import { CompanyExpense, CompanyExpenseUpdate, ExpenseCategoryCode, ExpenseFilterDTO, NewCompanyExpense } from "../models/expense";
import { StockChangePreview } from "../models/stock_change";
import { sendApiRequest } from "../components/send-api-request/SendApiRequest";

class ExpenseService {

  static async getExpenseById(expenseId: number): Promise<CompanyExpense> {
    return await sendApiRequest<CompanyExpense>(`company-expenses/${expenseId}`, {
      method: "get",
      errorMessage: "Error fetching Expenses."
    })
  }

  static async getLatestExpenseByCategory(category: ExpenseCategoryCode): Promise<CompanyExpense> {
    return await sendApiRequest<CompanyExpense>(`company-expenses/latest?category=${category}`, {
      method: "get",
      errorMessage: "Error fetching Lastest Expense."
    })
  }

  static async getExpenses(
    filter: ExpenseFilterDTO,
    page: number = 0, 
    size: number = 30
  ): Promise<{ content: CompanyExpense[], totalPages: number, totalElements: number, last: boolean }> {
    return await sendApiRequest<{ content: CompanyExpense[], totalPages: number, totalElements: number, last: boolean }>(`company-expenses/search?page=${page}&size=${size}`, {
      method: "post",
      body: filter,
      errorMessage: "Error fetching Expenses."
    })
  }

  static async getExpensePreview(expense: NewCompanyExpense): Promise<CompanyExpense> {
      return await sendApiRequest<CompanyExpense>(`company-expenses/preview`, {
        method: "post",
        body: expense,
        errorMessage: "Error fetching Expense preview.",
      });
    }

  static async getSalaryHistory(employeeId: number): Promise<CompanyExpense[]> {
    return await sendApiRequest<CompanyExpense[]>(`company-expenses/salary-history/${employeeId}`, {
      method: "get",
      errorMessage: "Error fetching Salary history.",
    })
  }

  static async createExpense(expense: NewCompanyExpense): Promise<CompanyExpense> {
    return await sendApiRequest<CompanyExpense>('company-expenses', {
      method: "post",
      body: expense,
      errorMessage: "Error creating new Expense.",
    })
  }

  static async updateExpense(id: number, expense: CompanyExpenseUpdate): Promise<CompanyExpense> {
    return await sendApiRequest<CompanyExpense>(`company-expenses/${id}`, {
      method: "put",
      body: expense,
      errorMessage: "Error updating Expense.",
    });
  }

  static async getDeletionPreview(id: number): Promise<StockChangePreview> {
    return await sendApiRequest<StockChangePreview>(`company-expenses/${id}/deletion-preview`, {
      method: "get",
      errorMessage: "Error fetching Expense deletion preview.",
    });
  }

  static async deleteExpense(id: number): Promise<void> {
    return await sendApiRequest<void>(`company-expenses/${id}`, {
      method: "delete",
      errorMessage: "Error removing Expense.",
    })
  }
}

export default ExpenseService;
