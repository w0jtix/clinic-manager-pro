export interface StockChangeProduct {
    productId: number;
    productName: string;
    quantity: number;
    currentSupply: number;
    supplyAfter: number;
    sufficient: boolean;
    skipped: boolean;
}

export interface StockChangePreview {
    canApply: boolean;
    products: StockChangeProduct[];
}
