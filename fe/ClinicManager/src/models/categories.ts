export interface ProductCategory {
    id: number;
    name: string;
    color: string;
    system?: boolean;
    restored?: boolean | null;
}

export interface NewProductCategory {
    name: string;
    color: string;
}

export interface BaseServiceCategory {
    id: number;
    name: string;
    color: string;
    isDeleted?: boolean;
    restored?: boolean | null;
}

export interface CategoryUsage {
    categoryId: number;
    count: number;
}

export interface NewBaseServiceCategory {
    name: string;
    color: string;
}

