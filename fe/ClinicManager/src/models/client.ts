import { Employee } from "./employee";
import { Discount } from "./visit";

export interface ClientNote {
    id:number;
    client: Client,
    content: string;
    createdAt: string;
    createdBy: Employee;
    createdByUserId: number | null;
}

export interface NewClientNote {
    content: string;
    client: Client;
    createdAt: string;
    createdBy: Employee | null;
}

export interface Client {
    id: number;
    firstName: string;
    lastName: string;
    phoneNumber: string | null;
    birthDate?: string | null;
    email: string | null;
    address: string | null;
    postalCode: string | null;
    city: string | null;
    signedRegulations: boolean;
    boostClient: boolean;
    redFlag: boolean;
    discount: Discount | null;
    hasDebts?: boolean;
    isDeleted?: boolean;
    visitsCount?: number;
    hasActiveVoucher?: boolean;
    hasGoogleReview?: boolean;
    hasBooksyReview?: boolean;
    hasActiveGoogleReview?: boolean;
    createdBy: number | null;
}

export interface NewClient {
    id?: number;
    firstName: string;
    lastName: string;
    phoneNumber: string | null;
    birthDate?: string | null;
    email?: string | null;
    address?: string | null;
    postalCode?: string | null;
    city?: string | null;
    signedRegulations: boolean;
    boostClient: boolean;
    redFlag: boolean;
    discount?: Discount | null;
}

export interface ClientTopItem {
    name: string;
    quantity: number;
}

export interface ClientReviewFact {
    present: boolean;
    rewardUsed?: boolean;
}

export interface ClientSummary {
    visitsCount: number;
    totalSpent?: number | null;
    absencesCount: number;
    latenessCount: number;
    topService: ClientTopItem | null;
    topProduct: ClientTopItem | null;
    hasDebts: boolean;
    debtTotal: number;
    activeVouchersCount: number;
    googleReview: ClientReviewFact;
    booksyReview: ClientReviewFact;
}

export interface ClientSpendChart {
    year: number;
    firstYear: number;
    lastYear: number;
    months: number[] | null;
    visitCounts?: number[];
}

export interface ClientFilterDTO {
    keyword?: string | null;
    boostClient?: boolean| null;
    signedRegulations?: boolean | null;
    hasDebts?: boolean | null;
    discountId?: number | string | null;
}