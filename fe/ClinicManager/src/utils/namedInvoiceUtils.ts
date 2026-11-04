import NamedInvoiceService from "../services/NamedInvoiceService";
import { formatTimestamp } from "./dateUtils";
import { openPdf } from "./pdfUtils";

export const openNamedInvoicePdf = (
  invoiceId: number,
  invoiceNumber?: string
): Promise<boolean> => {
  const filename = `Faktura ${(invoiceNumber ?? invoiceId)
    .toString()
    .replace(/\//g, "-")}.pdf`;
  return openPdf(() => NamedInvoiceService.getPdf(invoiceId), filename);
};

export type InvoiceSendStatus = "NOT_SENT" | "SENT" | "STALE";

export interface InvoiceSendState {
  emailSentAt: string | null;
  sentTo: string | null;
  updatedAt: string;
}

/**
 * STALE means the invoice was edited after the last successful send, so the
 * client is holding an older version of the document.
 */
export const getInvoiceSendStatus = (
  invoice: InvoiceSendState
): InvoiceSendStatus => {
  if (!invoice.emailSentAt) return "NOT_SENT";
  const sentAt = new Date(invoice.emailSentAt).getTime();
  const updatedAt = new Date(invoice.updatedAt).getTime();
  return updatedAt > sentAt ? "STALE" : "SENT";
};

export const invoiceSendTitle = (invoice: InvoiceSendState): string => {
  const status = getInvoiceSendStatus(invoice);
  if (status === "NOT_SENT") return "Wyślij fakturę emailem";

  const when = formatTimestamp(invoice.emailSentAt!);
  const where = invoice.sentTo ? ` na ${invoice.sentTo}` : "";
  if (status === "STALE") {
    return `Faktura zmieniona po wysyłce - klient ma starszą wersję. Wysłano ${when}${where}. Kliknij, aby wysłać ponownie.`;
  }
  return `Wysłano ${when}${where}. Kliknij, aby wysłać ponownie.`;
};

// Backend zwraca ConflictException/ValidationException z gotowym polskim
// komunikatem - pokazujemy go wprost, zamiast zastepowac ogolnym tekstem.
export const invoiceErrorMessage = (error: any, fallback: string): string => {
  const data = error?.response?.data;
  if (typeof data === "string" && data.trim() !== "") return data;
  if (data?.message) return data.message;
  return fallback;
};
