import { CategoryUsage } from "../models/categories";
import { ExpenseCategoryDTO, NewExpenseCategory } from "../models/expense";
import { sendApiRequest } from "../components/send-api-request/SendApiRequest";

class ExpenseCategoryService {
  static async getCategories(): Promise<ExpenseCategoryDTO[]> {
    return await sendApiRequest<ExpenseCategoryDTO[]>(`expense-categories`, {
      method: "get",
      body: {},
      errorMessage: "Error fetching expense categories.",
    });
  }

  static async createCategory(category: NewExpenseCategory): Promise<ExpenseCategoryDTO> {
    return await sendApiRequest<ExpenseCategoryDTO>("expense-categories", {
      method: "post",
      body: category,
      errorMessage: "Error creating expense category.",
    });
  }

  static async updateCategory(
    id: string | number,
    category: NewExpenseCategory
  ): Promise<ExpenseCategoryDTO | undefined> {
    return await sendApiRequest<ExpenseCategoryDTO>(`expense-categories/${id}`, {
      method: "put",
      body: category,
      errorMessage: "Error updating expense category.",
    });
  }

  static async deleteCategory(id: string | number): Promise<void> {
    return await sendApiRequest<void>(`expense-categories/${id}`, {
      method: "delete",
      errorMessage: "Error removing expense category.",
    });
  }

  static async getUsage(): Promise<CategoryUsage[]> {
    return await sendApiRequest<CategoryUsage[]>(`expense-categories/usage`, {
      method: "get",
      errorMessage: "Error fetching expense category usage.",
    });
  }
}

export default ExpenseCategoryService;
