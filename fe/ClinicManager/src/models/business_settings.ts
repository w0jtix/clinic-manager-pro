export interface StatSettings {
    id: number;
    bonusThreshold: number;
    servicesRevenueGoal: number;
    productsRevenueGoal: number;
    saleBonusPayoutMonths: number[];
    companyName: string | null;
    companyAddress: string | null;
    companyPostalCode: string | null;
    companyCity: string | null;
    companyNip: string | null;
    invoiceEmailBcc: string | null;
    companyPhone: string | null;
    companyWebsite: string | null;
    facebookUrl: string | null;
    instagramUrl: string | null;
    booksyUrl: string | null;
    footerCompanyName: string | null;
    footerAddress: string | null;
    footerPostalCode: string | null;
    footerCity: string | null;
    hasCompanyLogo: boolean;
    hasEmailFooterLogo: boolean;
}

export interface NewStatSettings {
    bonusThreshold: number;
    servicesRevenueGoal: number;
    productsRevenueGoal: number;
    saleBonusPayoutMonths: number[];
    companyName: string | null;
    companyAddress: string | null;
    companyPostalCode: string | null;
    companyCity: string | null;
    companyNip: string | null;
    invoiceEmailBcc: string | null;
    companyPhone: string | null;
    companyWebsite: string | null;
    facebookUrl: string | null;
    instagramUrl: string | null;
    booksyUrl: string | null;
    footerCompanyName: string | null;
    footerAddress: string | null;
    footerPostalCode: string | null;
    footerCity: string | null;
}