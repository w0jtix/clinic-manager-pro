import { ExpenseCategory, ExpenseCategoryCode } from "./expense";
import { Product } from "./product";
import { KsefSeller } from "./ksef_invoice";

export interface KsefCategoryFacet {
    category: ExpenseCategoryCode;
    count: number;
}

export interface KsefSellerSummary {
    invoiceCount: number;
    totalGross: number;
    unpaidCount: number;
    awaitingVerificationCount: number;
    uncategorizedCount: number;
    categoryFacets: KsefCategoryFacet[];
    manualExpenseCount: number;
    manualExpenseTotal: number;
}

export interface KsefSpendChartPoint {
    month: number;
    gross: number;
    invoiceCount: number;
    manualExpenseCount: number;
}

export interface KsefSpendChart {
    firstYear: number;
    lastYear: number;
    points: KsefSpendChartPoint[];
}

export interface KsefSellerItemMapping {
    id: number;
    ksefItemName: string;
    product: Product | null;
}

export const UNCATEGORIZED_FILTER = "UNCATEGORIZED";
export const AWAITING_FILTER = "AWAITING";

export type SellerCategoryFilterId =
    | ExpenseCategoryCode
    | typeof UNCATEGORIZED_FILTER
    | typeof AWAITING_FILTER;

export type SellerOriginModule = "Expenses" | "KSeF" | "Product";

export interface SellerOriginPeriod {
    year?: number | null;
    month?: number | null;
}

export interface SellerPageOrigin {
    module: SellerOriginModule;
    invoiceId?: number;
    period?: SellerOriginPeriod;
    productId?: number;
}

export const SELLER_ORIGIN_LABELS: Record<SellerOriginModule, string> = {
    Expenses: "Koszty",
    Product: "Produkt",
    KSeF: "KSeF",
};

export function getEffectiveDefaultCategory(
    seller: KsefSeller | null
): ExpenseCategoryCode | null {
    if (!seller) return null;
    return seller.autoMarkProducts
        ? ExpenseCategory.PRODUCTS
        : seller.defaultExpenseCategory;
}
