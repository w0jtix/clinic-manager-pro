import { ExpenseCategory, ExpenseCategoryCode } from "./expense";
import { Product } from "./product";
import { VatRate } from "./vatrate";

export const KSEF_FORBIDDEN_CATEGORIES: ExpenseCategoryCode[] = [
    ExpenseCategory.MIXED,
    ExpenseCategory.RENT,
    ExpenseCategory.TAXES,
    ExpenseCategory.ZUS,
    ExpenseCategory.SALARY,
];

export type KsefCorrectionMatchType =
    | "UU_ID"
    | "LINE"
    | "GTIN"
    | "NAME"
    | "AMBIGUOUS_CONSISTENT"
    | "AMBIGUOUS"
    | "NONE";

export interface KsefCorrectionMatch {
    matchType: KsefCorrectionMatchType;
    originalItemId: number | null;
    notProduct: boolean;
    suggestedProductId: number | null;
    suggestedQuantity: number | null;
    rounded: boolean;
    productDeleted: boolean;
}

export interface KsefInvoiceItem {
    id: number;
    ksefItemName: string;
    ksefGtin: string | null;
    unit: string | null;
    quantity: number | null;
    declaredQuantity: number | null;
    unitNetPrice: number | null;
    netValue: number | null;
    unitGrossPrice: number | null;
    grossValue: number | null;
    vatRate: VatRate | null;
    product: Product | null;
    suggestedProductId: number | null;
    lineNumber?: number | null;
    lineUuid?: string | null;
    correctionMatch?: KsefCorrectionMatch | null;
}

export interface KsefSeller {
    id: number;
    nip: string;
    name: string | null;
    address: string | null;
    email: string | null;
    phone: string | null;
    autoMarkProducts: boolean;
    defaultExpenseCategory: ExpenseCategoryCode | null;
}

export interface KsefCorrectionRef {
    id: number;
    ksefNumber: string;
    invoiceType: string;
}

export interface KsefInvoice {
    id: number;
    ksefReferenceNumber: string;
    invoiceNumber: string;
    invoiceType: string;
    issueDate: string;
    paymentDueDate: string | null;
    seller: KsefSeller;
    buyerNip: string | null;
    buyerName: string | null;
    buyerAddress: string | null;
    netAmount: number;
    vatAmount: number;
    grossAmount: number;
    currency: string;
    isPaid: boolean;
    awaitingProductVerification: boolean;
    category: ExpenseCategoryCode | null;
    correctedInvoiceId: number | null;
    correctedKsefNumber: string | null;
    corrections: KsefCorrectionRef[];
    paymentDate: string | null;
    paymentMethod: string | null;
    fetchedAt: string;
    items: KsefInvoiceItem[];
}

export interface KsefVerifyProductsItem {
    itemId: number;
    productId: number | null;
    quantity: number | null;
    rememberForSeller: boolean;
}

export interface KsefVerifyProductsRequest {
    items: KsefVerifyProductsItem[];
    rememberSeller: boolean;
}

export interface KsefBookCorrectionItem {
    itemId: number;
    productId: number | null;
    quantity: number;
}

export interface KsefBookCorrectionRequest {
    items: KsefBookCorrectionItem[];
}

export const isCorrectionInvoice = (inv: KsefInvoice): boolean =>
    inv.invoiceType?.startsWith("KOR") ?? false;

export interface KsefInvoiceFilterDTO {
    year?: number | null;
    month?:number | null;
    isPaid?: boolean | null;
    sellerId?: number | null;
    categories?: ExpenseCategoryCode[] | null;
    uncategorized?: boolean | null;
    awaitingVerification?: boolean | null;
}

export enum KsefSyncStatusType  {
    NEVER_SYNCED =  "NEVER_SYNCED",
    SUCCESS = "SUCCESS",
    PARTIAL_FAILURE = "PARTIAL_FAILURE",
};

export interface KsefMonthMismatch {
    year: number;
    month: number;
    localCount: number;
    ksefCount: number | null;
    missing: number;
}

export interface KsefSyncSummary {
    fullySynced: boolean;
    totalLocal: number;
    totalKsef: number | null;
    monthsWithMismatch: KsefMonthMismatch[];
}

export interface KsefSyncStatus {
    status: KsefSyncStatusType;
    lastSyncedAt: string | null;
    lastAttemptAt: string | null;
    lastNewCount: number;
    lastFailedCount: number;
    lastIssuedNewCount: number;
    lastIssuedFailedCount: number;
    rateLimitHit: boolean;
    summary: KsefSyncSummary;
}

export interface KsefInvoiceCount {
    localCount: number;
    ksefCount: number | null;
}

export interface KsefCredentials {
    nip: string | null;
    token: string;
}
