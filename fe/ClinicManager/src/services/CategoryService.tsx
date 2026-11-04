
import { CategoryUsage, NewProductCategory, ProductCategory } from "../models/categories";
import { sendApiRequest } from "../components/send-api-request/SendApiRequest";

class CategoryService {
  static async getCategories(): Promise<ProductCategory[]> {
    return await sendApiRequest<ProductCategory[]>(`categories/all`, {
      method: "get",
      body: {},
      errorMessage: "Error fetching categories.",
    });
  }

  static async createCategory(
    category: NewProductCategory
  ): Promise<ProductCategory> {
    return await sendApiRequest<ProductCategory>("categories", {
      method: "post",
      body: category,
      errorMessage: "Error creating new Category.",
    });
  }

  static async updateCategory(
    id: string | number,
    category: NewProductCategory
  ): Promise<ProductCategory | undefined> {
    return await sendApiRequest<ProductCategory>(`categories/${id}`, {
      method: "put",
      body: category,
      errorMessage: "Error updating Category.",
    });
  }

  static async deleteCategory(id: string | number): Promise<void> {
    return await sendApiRequest<void>(`categories/${id}`, {
      method: "delete",
      errorMessage: "Error removing Category.",
    });
  }

  static async getUsage(): Promise<CategoryUsage[]> {
    return await sendApiRequest<CategoryUsage[]>(`categories/usage`, {
      method: "get",
      errorMessage: "Error fetching Category usage.",
    });
  }
}

export default CategoryService;
