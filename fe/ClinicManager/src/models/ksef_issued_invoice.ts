import { VatRate } from "./vatrate";

export interface KsefIssuedInvoiceItem {
    id: number;
    ksefItemName: string;
    unit: string | null;
    quantity: number | null;
    unitNetPrice: number | null;
    netValue: number | null;
    unitGrossPrice: number | null;
    grossValue: number | null;
    vatRate: VatRate | null;
}

export interface KsefIssuedInvoice {
    id: number;
    ksefReferenceNumber: string;
    invoiceNumber: string;
    invoiceType: string;
    issueDate: string;
    paymentDueDate: string | null;
    sellerName: string | null;
    sellerNip: string | null;
    sellerAddress: string | null;
    buyerName: string | null;
    buyerNip: string | null;
    buyerAddress: string | null;
    netAmount: number;
    vatAmount: number;
    grossAmount: number;
    currency: string;
    correctedKsefNumber: string | null;
    fetchedAt: string;
    items: KsefIssuedInvoiceItem[];
}

export interface KsefIssuedInvoiceFilterDTO {
    keyword?: string | null;
    year?: number | null;
    month?: number | null;
}

export interface KsefIssuedInvoiceCount {
    count: number;
}

export const isIssuedCorrectionInvoice = (
    invoice: KsefIssuedInvoice
): boolean => invoice.invoiceType?.startsWith("KOR") ?? false;
