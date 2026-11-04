export enum SupplyChangeSource {
    INITIAL_BALANCE = "INITIAL_BALANCE",
    PRODUCT_CREATED = "PRODUCT_CREATED",
    PRODUCT_FORM = "PRODUCT_FORM",
    PRODUCT_DELETED = "PRODUCT_DELETED",
    PRODUCT_RESTORED = "PRODUCT_RESTORED",
    COMPANY_EXPENSE = "COMPANY_EXPENSE",
    COMPANY_EXPENSE_DELETED = "COMPANY_EXPENSE_DELETED",
    KSEF_INVOICE = "KSEF_INVOICE",
    KSEF_CORRECTION = "KSEF_CORRECTION",
    USAGE_RECORD = "USAGE_RECORD",
    USAGE_REVERT = "USAGE_REVERT",
    INVENTORY_REPORT = "INVENTORY_REPORT",
    INVENTORY_REPORT_REVERT = "INVENTORY_REPORT_REVERT",
    VISIT_SALE = "VISIT_SALE",
    VISIT_DELETED = "VISIT_DELETED"
}

export interface SupplyHistoryEntry {
    id: number;
    delta: number;
    supplyAfter: number;
    source: SupplyChangeSource;
    sourceId: number | null;
    sourceLabel: string | null;
    occurredAt: string;
    performedBy: string;
    sourceExists: boolean;
}

export interface SupplyChartPoint {
    date: string;
    supply: number;
}

export interface SupplyChart {
    openingSupply: number;
    firstYear: number;
    lastYear: number;
    points: SupplyChartPoint[];
}

export interface ProductSupplySummary {
    currentSupply: number;
    totalIn: number;
    totalSold: number;
    totalUsed: number;
    totalAdjustments: number;
    avgNetPurchasePrice: number | null;
    fallbackPriceUsed: boolean;
    stockValue: number;
}

export type SupplySourceGroupId =
    | "KSEF"
    | "EXPENSE"
    | "INVENTORY"
    | "USAGE"
    | "SALE"
    | "FORM";

export interface SupplySourceGroup {
    id: SupplySourceGroupId;
    name: string;
    color: string;
    sources: SupplyChangeSource[];
}

export const supplySourceGroups: SupplySourceGroup[] = [
    {
        id: "KSEF",
        name: "Faktury KSeF",
        color: "23, 196, 127",
        sources: [SupplyChangeSource.KSEF_INVOICE, SupplyChangeSource.KSEF_CORRECTION],
    },
    {
        id: "EXPENSE",
        name: "Koszty",
        color: "217, 165, 49",
        sources: [SupplyChangeSource.COMPANY_EXPENSE, SupplyChangeSource.COMPANY_EXPENSE_DELETED],
    },
    {
        id: "INVENTORY",
        name: "Inwentaryzacja",
        color: "0, 193, 251",
        sources: [
            SupplyChangeSource.INVENTORY_REPORT,
            SupplyChangeSource.INVENTORY_REPORT_REVERT,
        ],
    },
    {
        id: "USAGE",
        name: "Zużycia",
        color: "250, 58, 63",
        sources: [SupplyChangeSource.USAGE_RECORD, SupplyChangeSource.USAGE_REVERT],
    },
    {
        id: "SALE",
        name: "Sprzedaż",
        color: "143, 118, 230",
        sources: [SupplyChangeSource.VISIT_SALE, SupplyChangeSource.VISIT_DELETED],
    },
    {
        id: "FORM",
        name: "Formularz",
        color: "255, 255, 255",
        sources: [
            SupplyChangeSource.INITIAL_BALANCE,
            SupplyChangeSource.PRODUCT_CREATED,
            SupplyChangeSource.PRODUCT_FORM,
            SupplyChangeSource.PRODUCT_DELETED,
            SupplyChangeSource.PRODUCT_RESTORED,
        ],
    },
];

export const getSupplySourceGroup = (
    source: SupplyChangeSource
): SupplySourceGroup =>
    supplySourceGroups.find((group) => group.sources.includes(source)) ??
    supplySourceGroups[supplySourceGroups.length - 1];

export function getSupplySourceDisplay(source: SupplyChangeSource): string {
    switch (source) {
        case SupplyChangeSource.INITIAL_BALANCE:
            return "Stan początkowy";
        case SupplyChangeSource.PRODUCT_CREATED:
            return "Utworzenie";
        case SupplyChangeSource.PRODUCT_FORM:
            return "Formularz";
        case SupplyChangeSource.PRODUCT_DELETED:
            return "Usunięcie produktu";
        case SupplyChangeSource.PRODUCT_RESTORED:
            return "Przywrócenie";
        case SupplyChangeSource.COMPANY_EXPENSE:
            return "Koszt";
        case SupplyChangeSource.COMPANY_EXPENSE_DELETED:
            return "Usunięcie kosztu";
        case SupplyChangeSource.KSEF_INVOICE:
            return "Faktura KSeF";
        case SupplyChangeSource.KSEF_CORRECTION:
            return "Korekta KSeF";
        case SupplyChangeSource.USAGE_RECORD:
            return "Zużycie";
        case SupplyChangeSource.USAGE_REVERT:
            return "Cofnięcie zużycia";
        case SupplyChangeSource.INVENTORY_REPORT:
            return "Inwentaryzacja";
        case SupplyChangeSource.INVENTORY_REPORT_REVERT:
            return "Cofnięcie inwentaryzacji";
        case SupplyChangeSource.VISIT_SALE:
            return "Sprzedaż";
        case SupplyChangeSource.VISIT_DELETED:
            return "Usunięcie wizyty";
        default:
            return source;
    }
}
