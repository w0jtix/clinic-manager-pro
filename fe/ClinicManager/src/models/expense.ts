import { VatRate } from "./vatrate";


export enum ExpenseCategory {
    RENT = "RENT",
    TAXES = "TAXES",
    ZUS = "ZUS",
    SALARY = "SALARY",
    PRODUCTS = "PRODUCTS",
    UNRELATED = "UNRELATED",
    MIXED = "MIXED"
}

export type ExpenseCategoryCode = string;

export interface ExpenseCategoryDTO {
    id: number;
    code: ExpenseCategoryCode;
    name: string;
    color: string;
    system: boolean;
    internal: boolean;
}

export interface NewExpenseCategory {
    name: string;
    color: string;
}

export interface ExpenseCategoryItem {
    id: ExpenseCategoryCode;
    name: string;
    color: string;
}

export const ITEM_FORBIDDEN_CATEGORIES: ExpenseCategoryCode[] = [
    ExpenseCategory.RENT,
    ExpenseCategory.SALARY,
    ExpenseCategory.ZUS,
    ExpenseCategory.TAXES,
    ExpenseCategory.MIXED,
];


export enum SalaryComponent {
    BASE = "BASE",
    BONUS = "BONUS",
    SALE_BONUS = "SALE_BONUS"
}

export function getSalaryComponentDisplay(component: SalaryComponent): string {
    switch (component) {
        case SalaryComponent.BASE:
            return "Wypłata";
        case SalaryComponent.BONUS:
            return "Premia";
        case SalaryComponent.SALE_BONUS:
            return "Premia Sprzedażowa";
        default:
            return component;
    }
}

export interface CompanyExpenseItem {
    id: number;
    name: string;
    quantity: number;
    vatRate: VatRate;
    price: number;
    productId: number | null;
    productName?: string;
    salaryComponent?: SalaryComponent | null;
    category?: ExpenseCategoryCode | null;
}

export interface NewCompanyExpenseItem {
    name: string;
    quantity: number;
    vatRate: VatRate | null;
    price: number;
    productId?: number | null;
    salaryComponent?: SalaryComponent | null;
    notProduct?: boolean;
    lockedTotal?: number | null;
    category?: ExpenseCategoryCode | null;
}

export type ExpenseCurrency = "PLN" | "EUR" | "USD";

export const EXPENSE_CURRENCIES: ExpenseCurrency[] = ["PLN", "EUR", "USD"];

export const CURRENCY_SYMBOLS: Record<ExpenseCurrency, string> = {
    PLN: "zł",
    EUR: "€",
    USD: "$",
};

export interface ExchangeRate {
    rateDate: string;
    mid: number;
    tableNo: string;
}

export interface ExchangeRateWindow {
    currency: ExpenseCurrency;
    date: string;
    windowFrom: string;
    windowTo: string;
    rates: ExchangeRate[];
    selected: ExchangeRate | null;
}

export interface ExpenseSeller {
    id?: number | null;
    nip: string;
    name: string;
    address: string;
}

export interface CompanyExpense {
    id: number;
    source: string;
    seller?: ExpenseSeller | null;
    expenseDate: string;
    category: ExpenseCategoryCode | null;
    awaitingProductVerification: boolean;
    sellerId?: number | null;
    sellerDefaultExpenseCategory: ExpenseCategoryCode | null;
    invoiceType: string | null;
    itemCategories?: ExpenseCategoryCode[] | null;
    employeeId?: number | null;
    invoiceNumber?: string | null;
    currency?: ExpenseCurrency | null;
    exchangeRate?: number | null;
    exchangeRateDate?: string | null;
    exchangeRateTable?: string | null;
    totalNetCurrency?: number | null;
    totalVatCurrency?: number | null;
    totalValueCurrency?: number | null;
    expenseItems: CompanyExpenseItem[];
    totalNet: number | null;
    totalVat: number | null;
    totalValue: number | null;
    sourceType: string;
}

export interface NewCompanyExpense {
    source: string;
    seller?: ExpenseSeller | null;
    expenseDate: string;
    category: ExpenseCategoryCode | null;
    employeeId?: number | null;
    invoiceNumber?: string | null;
    currency?: ExpenseCurrency | null;
    exchangeRate?: number | null;
    exchangeRateDate?: string | null;
    expenseItems: NewCompanyExpenseItem[];
}

export interface CompanyExpenseUpdate {
    expenseDate: string;
    source: string;
    category: ExpenseCategoryCode | null;
    seller: ExpenseSeller | null;
    invoiceNumber: string | null;
    currency: ExpenseCurrency;
}

export const EDIT_LOCKED_CATEGORIES: ExpenseCategoryCode[] = [
    ExpenseCategory.PRODUCTS,
    ExpenseCategory.MIXED,
    ExpenseCategory.SALARY,
    ExpenseCategory.RENT,
    ExpenseCategory.ZUS,
    ExpenseCategory.TAXES,
];


export interface ExpenseFilterDTO {
    categories?: ExpenseCategoryCode[] | null;    
    year?: number | null;
    month?: number | null;
}

export function getItemCategories(
    expense: Pick<CompanyExpense, "itemCategories"> & { expenseItems?: { category?: ExpenseCategoryCode | null }[] }
): ExpenseCategoryCode[] {
    if (expense.itemCategories && expense.itemCategories.length > 0) return expense.itemCategories;
    return [
        ...new Set(
            (expense.expenseItems ?? [])
                .map((item) => item.category)
                .filter((category): category is ExpenseCategoryCode => category != null)
        ),
    ];
}

export function hasProductItems(
    expense: Pick<CompanyExpense, "category" | "itemCategories"> & { expenseItems?: { category?: ExpenseCategoryCode | null }[] }
): boolean {
    if (expense.category === ExpenseCategory.PRODUCTS) return true;
    return (
        expense.category === ExpenseCategory.MIXED &&
        getItemCategories(expense).includes(ExpenseCategory.PRODUCTS)
    );
}
