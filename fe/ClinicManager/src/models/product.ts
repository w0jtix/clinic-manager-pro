import { Brand, NewBrand } from "./brand";
import { ProductCategory } from "./categories";
import { VatRate } from "./vatrate";

export enum Unit {
    ML = "ML",
    G = "G"
}

export enum StockStatus {
    OK = "OK",
    LOW_STOCK = "LOW_STOCK",
    OUT_OF_STOCK = "OUT_OF_STOCK"
}

export interface Product {
    id: number;
    name: string;
    category: ProductCategory;
    brand: Brand;
    supply: number;
    lowStockThreshold: number;
    sellingPrice?: number | null;
    vatRate: VatRate;
    volume?: number | null;
    unit?: Unit | null;
    description: string;
    fallbackNetPurchasePrice?: number | null;
    fallbackVatRate?: VatRate | null;
    isDeleted: boolean;
}

export interface NewProduct {
    name: string;
    category: ProductCategory | null;
    brand: Brand | NewBrand | null;
    supply: number;
    lowStockThreshold?: number | null;
    sellingPrice?: number | null;
    vatRate?: VatRate | null;
    volume?: number | null;
    unit?: Unit | null;
    description?: string;   
    fallbackNetPurchasePrice?: number | null;
    fallbackVatRate?: VatRate | null;
    isDeleted?: boolean;
}

export interface ProductFilterDTO {
    productIds?: number[] | null;
    categoryIds?: number[] | null;
    brandIds?: number[] | null;
    keyword?: string;
    includeZero?: boolean;
    isDeleted?: boolean;
    stockStatus?: StockStatus | null;
}
