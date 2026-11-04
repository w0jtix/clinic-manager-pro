import type { RoleType } from "./login";

export enum EmploymentType {
    QUARTER = "QUARTER",
    HALF = "HALF",
    THREE_QUARTERS = "THREE_QUARTERS",
    FULL = "FULL"
}

export interface Employee {
    id: number;
    name: string;
    lastName: string;   
    isDeleted?: boolean;
    employmentType: EmploymentType;
    bonusPercent: number;
    saleBonusPercent: number;
    accountRole?: RoleType | null;
    avatar?: string | null;
}


export interface NewEmployee {
    name?: string;
    lastName?: string;
    employmentType?: EmploymentType;
    bonusPercent?: number | null;
    saleBonusPercent?: number | null;
}



export interface BonusChangePoint {
    year: number;
    month: number;
    bonusPercent: number | null;
    bonusPercentChange: number | null;
    saleBonusPercent: number | null;
    saleBonusPercentChange: number | null;
}

export interface EmployeeBonusFilterDTO {
    employeeId?: number | null;
    month?: number | null;
    year?: number | null;
}

export interface BonusVisit {
    visitId: number;
    clientName: string;
    date: string;
    paymentsSum: number;
    voucherPaymentsSum: number;
    productsValue: number;
    adjustedRevenue: number;
}

export interface BonusProduct {
    productId: number;
    productName: string;
    brandName: string;
    quantitySold: number;
    totalBonus: number;
    noPurchaseHistory: boolean;
    fallbackPurchasePriceUsed: boolean;
    items: BonusProductItem[]
}

export interface BonusProductItem {
    visitId: number;
    purchaseNetPrice: number;
    saleNetPrice: number;
    margin: number;
    bonusPerUnit: number;
}

export interface EmployeeBonus {
    employeeId: number;
    employeeName: string;
    visits: BonusVisit[];
    monthlyServicesRevenue: number;
    boostCost: number;
    bonusThreshold: number;
    bonusPercent: number;
    bonusAmount: number;

    products: BonusProduct[];
    monthlyProductsRevenue: number;
    monthlyProductsMargin: number;
    monthlyProductsLoss: number;
    saleBonusPercent: number;
    productBonusAmount: number;
    prevMonthSaleBonus: number | null;
    twoMonthPrevSaleBonus: number | null;
}