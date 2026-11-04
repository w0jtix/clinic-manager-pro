import { VatRate } from "./vatrate";
import { PaymentMethod } from "./payment";

export interface NamedInvoiceItem {
    id?: number | null;
    name: string;
    quantity: number;
    unitNetPrice: number;
    netValue: number;
    unitGrossPrice: number;
    grossValue: number;
    vatRate: VatRate;
    sourceType?: string | null;
}

export interface NamedInvoice {
    id: number;
    invoiceNumber: string;
    sequenceInMonth: number;
    year: number;
    month: number;
    issueDate: string;
    saleDate: string | null;
    paymentMethods: PaymentMethod[];
    notes: string | null;
    clientId: number;
    sourceVisitId: number;
    buyerName: string;
    buyerAddress: string | null;
    buyerPostalCode: string | null;
    buyerCity: string | null;
    netAmount: number;
    vatAmount: number;
    grossAmount: number;
    emailSentAt: string | null;
    sentTo: string | null;
    clientEmail: string | null;
    createdAt: string;
    updatedAt: string;
    createdByUserId: number;
    items: NamedInvoiceItem[];
}

export interface NamedInvoiceDraft {
    suggestedInvoiceNumber: string;
    suggestedSequenceInMonth: number;
    year: number;
    month: number;
    issueDate: string;
    saleDate: string | null;
    paymentMethods: PaymentMethod[];
    notes: string | null;
    clientId: number;
    sourceVisitId: number;
    buyerName: string | null;
    buyerAddress: string | null;
    buyerPostalCode: string | null;
    buyerCity: string | null;
    clientEmail: string | null;
    items: NamedInvoiceItem[];
}

export interface NewNamedInvoice {
    clientId: number;
    sourceVisitId: number;
    invoiceNumber: string;
    sequenceInMonth: number;
    year: number;
    month: number;
    issueDate: string;
    saleDate: string | null;
    paymentMethods: PaymentMethod[];
    notes: string | null;
    buyerName: string;
    buyerAddress: string | null;
    buyerPostalCode: string | null;
    buyerCity: string | null;
    items: NamedInvoiceItem[];
}

export interface SendNamedInvoiceRequest {
    email: string;
    updateClientEmail: boolean;
}
