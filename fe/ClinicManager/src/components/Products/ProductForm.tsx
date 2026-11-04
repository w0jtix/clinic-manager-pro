import React from "react";
import DigitInput from "../DigitInput";
import CostInput from "../CostInput";
import { useState, useEffect, useCallback } from "react";
import CategorySegments from "../CategorySegments";
import BrandService from "../../services/BrandService";
import TextInput from "../TextInput";
import { NewProduct, StockStatus, Unit } from "../../models/product";
import { STOCK_STATUS_LABELS, StockStatusIcon } from "../StockStatusTag";
import { Action } from "../../models/action";
import { Brand, KeywordDTO, NewBrand } from "../../models/brand";
import { ProductCategory } from "../../models/categories";
import CategoryService from "../../services/CategoryService";
import SelectVATButton from "../SelectVATButton";
import { VatRate, VAT_NUMERIC_VALUES } from "../../models/vatrate";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import ActionButton from "../ActionButton";
import { useRoles } from "../../hooks/useRoles";

type PurchasePriceMode = "net" | "gross";

const round2 = (value: number): number => Math.round(value * 100) / 100;

const toNet = (gross: number, vatRate: VatRate): number | null =>
  gross ? round2(gross / (1 + VAT_NUMERIC_VALUES[vatRate] / 100)) : null;

const toGross = (net: number, vatRate: VatRate): number =>
  round2(net * (1 + VAT_NUMERIC_VALUES[vatRate] / 100));

export interface ProductFormProps {
  action: Action;
  brandToCreate: NewBrand | null;
  setBrandToCreate: React.Dispatch<React.SetStateAction<NewBrand | null>>;
  className?: string;
  productDTO: NewProduct;
  setProductDTO: React.Dispatch<React.SetStateAction<NewProduct>>;
  hideStock?: boolean;
  hidePurchaseInfo?: boolean;
  purchaseInfoOnly?: boolean;
  productDefaults?: { vatRate?: VatRate | null; volume?: number; unit?: Unit };
}

export function ProductForm({
  action,
  className = "",
  brandToCreate,
  setBrandToCreate,
  productDTO,
  setProductDTO,
  hideStock = false,
  hidePurchaseInfo = false,
  purchaseInfoOnly = false,
  productDefaults,
}: ProductFormProps) {
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const { showAlert } = useAlert();
  const { isCompany } = useRoles();
  const [brandSuggestions, setBrandSuggestions] = useState<Brand[]>([]);
  const [purchaseMode, setPurchaseMode] = useState<PurchasePriceMode>("net");
  const [grossPurchasePrice, setGrossPurchasePrice] = useState<number>(0);

  const fetchCategories = async (): Promise<void> => {
    CategoryService.getCategories()
      .then((data) => {
        setCategories(data);
      })
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories:", error);
      });
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  const handleCategory = useCallback((categories: ProductCategory[] | null) => {
    setProductDTO((prev) => {
      const newCategory = categories ? categories[0] : null;
      const isProductCategory = !!newCategory?.system;

      return {
        ...prev,
        category: newCategory,
        sellingPrice: isProductCategory ? prev.sellingPrice : null,
        volume: isProductCategory ? prev.volume ?? productDefaults?.volume ?? null : null,
        unit: isProductCategory ? prev.unit ?? productDefaults?.unit ?? null : null,
        vatRate: isProductCategory
          ? productDefaults?.vatRate ?? prev.vatRate
          : VatRate.VAT_23,
        fallbackNetPurchasePrice: isProductCategory ? prev.fallbackNetPurchasePrice : null,
        fallbackVatRate: isProductCategory
          ? prev.fallbackVatRate ?? productDefaults?.vatRate ?? null
          : null,
      };
    });
  }, [productDefaults]);
  const handleProductName = useCallback((name: string) => {
    setProductDTO((prev) => ({
      ...prev,
      name: name,
    }));
  }, []);
  const handleSupply = useCallback((newSupply: number | null) => {
    setProductDTO((prev) => ({
      ...prev,
      supply: newSupply ?? 0,
    }));
  }, []);
  const handleLowStockThreshold = useCallback(
    (newThreshold: number | null) => {
      setProductDTO((prev) => ({
        ...prev,
        lowStockThreshold: newThreshold ?? 0,
      }));
    },
    []
  );
  const handleSellingPrice = useCallback((newSellingPrice: number | null) => {
    setProductDTO((prev) => ({
      ...prev,
      sellingPrice: newSellingPrice ?? 0,
    }));
  }, []);
  const handleVolume = useCallback((newVolume: number | null) => {
    setProductDTO((prev) => ({
      ...prev,
      volume: newVolume === 0 ? null : newVolume,
    }));
  }, []);
  const handleVatSelect = useCallback((selectedVat: VatRate) => {
    setProductDTO((prev) => ({
      ...prev,
      vatRate: selectedVat ?? VatRate.VAT_23,
    }))
  },[])
  const handleVatPurchaseSelect = useCallback(
    (selectedVat: VatRate) => {
      const rate = selectedVat ?? VatRate.VAT_23;
      setProductDTO((prev) => ({
        ...prev,
        fallbackVatRate: rate,
        fallbackNetPurchasePrice:
          purchaseMode === "gross"
            ? toNet(grossPurchasePrice, rate)
            : prev.fallbackNetPurchasePrice,
      }));
    },
    [purchaseMode, grossPurchasePrice]
  );
  const handlePurchasePrice = useCallback(
    (val: number) => {
      if (purchaseMode === "gross") {
        setGrossPurchasePrice(val);
        setProductDTO((prev) => ({
          ...prev,
          fallbackNetPurchasePrice: toNet(
            val,
            prev.fallbackVatRate ?? VatRate.VAT_23
          ),
        }));
        return;
      }
      setProductDTO((prev) => ({
        ...prev,
        fallbackNetPurchasePrice: val === 0 ? null : val,
      }));
    },
    [purchaseMode]
  );
  const handlePurchaseModeChange = useCallback(
    (mode: PurchasePriceMode) => {
      if (mode === purchaseMode) return;
      if (mode === "gross") {
        setGrossPurchasePrice(
          toGross(
            productDTO.fallbackNetPurchasePrice ?? 0,
            productDTO.fallbackVatRate ?? VatRate.VAT_23
          )
        );
      }
      setPurchaseMode(mode);
    },
    [purchaseMode, productDTO.fallbackNetPurchasePrice, productDTO.fallbackVatRate]
  );
  const handleDescription = useCallback((newDesc: string) => {
    setProductDTO((prev) => ({
      ...prev,
      description: newDesc,
    }));
  }, []);

  const handleBrandNameChange = useCallback(async (selected: string | Brand) => {
      if (typeof selected === "string") {
        const brandName = selected.trim();
        const filter: KeywordDTO = { keyword: brandName };
        let suggestions: Brand[] = [];

        if (brandName.length > 0) {
        try {
          suggestions = await BrandService.getBrands(filter);
        } catch (error) {
          showAlert("Błąd", AlertType.ERROR);
          console.error("Error fetching filtered products:", error);
        }
      }
      const exactMatch = suggestions.find(
        (p) => p.name.trim().toLowerCase() === brandName.toLowerCase()
      );

      if(exactMatch) {
        setProductDTO((prev) => ({
          ...prev,
          brand: exactMatch,
        }))
        setBrandToCreate(null);
      } else {
        setBrandToCreate(brandName ? { name: brandName } : null)
        setProductDTO((prev) => ({
          ...prev,
          brand: null,
        }))
      }
      setBrandSuggestions(exactMatch ? []: suggestions);
      } else {
        setProductDTO((prev) => ({
          ...prev,
          brand: selected,
        }))
        setBrandToCreate(null);
        setBrandSuggestions([]);
      }

  }, [showAlert]);

  const handleMlUnit = useCallback(() => {
    setProductDTO((prev) => ({
      ...prev,
      unit: prev.unit === Unit.ML ? null : Unit.ML,
    }));
  }, []);

  const handleGUnit = useCallback(() => {
    setProductDTO((prev) => ({
      ...prev,
      unit: prev.unit === Unit.G ? null : Unit.G,
    }));
  }, []);

  const purchasePriceRow = (
    <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15">
      <a className="product-form-input-title">Cena Zakupu:</a>
      <div className="flex align-items-center g-05">
        <div className="cs-seg flex align-items-center pf-price-mode">
          <button
            type="button"
            className={purchaseMode === "net" ? "on" : ""}
            onClick={() => handlePurchaseModeChange("net")}
          >
            Netto
          </button>
          <button
            type="button"
            className={purchaseMode === "gross" ? "on" : ""}
            onClick={() => handlePurchaseModeChange("gross")}
          >
            Brutto
          </button>
        </div>
        <CostInput
          onChange={handlePurchasePrice}
          selectedCost={
            purchaseMode === "gross"
              ? grossPurchasePrice
              : productDTO.fallbackNetPurchasePrice ?? undefined
          }
          className="in-sm"
        />
        
      </div>
    </li>
  );

  if (purchaseInfoOnly) {
    return (
      <div className={`product-form-container flex-column ${className}`}>
        <section className="product-form-core-section flex">
          <ul className="product-form-inputs-section width-95 flex-column p-0 mt-0 mb-0">
            {purchasePriceRow}
            <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15">
              <a className="product-form-input-title">VAT zakupu:</a>
              <SelectVATButton
                selectedVat={productDTO.fallbackVatRate ?? VatRate.VAT_23}
                onSelect={handleVatPurchaseSelect}
                className="product-form"
              />
            </li>
          </ul>
        </section>
      </div>
    );
  }

  return (
    <div
      className={`product-form-container flex-column ${action
        .toString()
        .toLowerCase()} ${className}`}
    >
      <section className="product-form-categories flex align-items-center mb-2 space-between">
        <a className="product-form-input-title">Kategoria:</a>
        <CategorySegments
          categories={categories}
          onSelect={(selected) =>
            handleCategory(selected as ProductCategory[] | null)
          }
          selectedCategories={productDTO.category ? [productDTO.category] : []}
          multiple={false}
          showAllSegment={false}
        />
      </section>
      <section className="product-form-core-section flex">
        <ul className="product-form-inputs-section width-95 flex-column p-0 mt-0 mb-0">
          <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15  name">
            <a className="product-form-input-title">Nazwa:</a>
            <TextInput
              dropdown={false}
              className="in-lg"
              value={productDTO.name}
              onSelect={(inputName) => {
                if (typeof inputName === "string") {
                  handleProductName(inputName);
                }
              }}
            />
          </li>
          <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
            <a className="product-form-input-title">Marka:</a>
            <TextInput
              key={`brand-input-${productDTO.brand && 'id' in productDTO.brand ? productDTO.brand.id : 'new'}`}
              dropdown={true}
              className="in-md"
              value={brandToCreate?.name ?? productDTO.brand?.name ?? ""}
              suggestions={brandSuggestions}
              onSelect={handleBrandNameChange}
            />
          </li>
          {!hideStock && (
            <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
              <a className="product-form-input-title">Produkty na stanie:</a>
              <DigitInput
                onChange={handleSupply}
                value={productDTO.supply}
                className="in-xs"
                max={999}
              />
            </li>
          )}
          {!hideStock && isCompany && (
            <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
              <span
                className="flex align-items-center g-05"
                title="Przy tym stanie (i poniżej) produkt oznaczany jest jako niski stan magazynowy."
              >
                <span className="sb-badge flex align-items-center g-025 low">
                  <StockStatusIcon status={StockStatus.LOW_STOCK} />
                  {STOCK_STATUS_LABELS[StockStatus.LOW_STOCK]}
                </span>
                <a className="product-form-input-title">Próg niskiego stanu:</a>
              </span>
              <DigitInput
                onChange={handleLowStockThreshold}
                value={productDTO.lowStockThreshold ?? 1}
                min={0}
                className="in-xs"
              />
            </li>
          )}
          {productDTO.category?.system && (
              <>
              <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
                <a className="product-form-input-title">Cena sprzedaży:</a>               
                <CostInput
                  onChange={handleSellingPrice}
                  selectedCost={productDTO.sellingPrice ?? 0}
                  className="in-sm"
                />
              </li>
              <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
                <a className="product-form-input-title">VAT sprzedaży:</a>
                <SelectVATButton
                  selectedVat={productDTO.vatRate ?? VatRate.VAT_23}
                  onSelect={handleVatSelect}
                  className="product-form"
                  portal
                />
              </li>
              <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
                <a className="product-form-input-title">Objętość produktu [ml/g]:</a>
                <div className="flex g-5px">
                  <DigitInput
                    onChange={handleVolume}
                    value={productDTO.volume ?? ""}
                    max={9999}
                    className="in-sm"
                  />
                  <ActionButton
                    disableImg ={true}
                    text={"ml"}
                    onClick={handleMlUnit}
                    className={`unit-b ${productDTO.unit === Unit.ML ? "active-g" : ""}`}
                  />
                  <ActionButton
                    disableImg ={true}
                    text={"g"}
                    onClick={handleGUnit}
                    className={`unit-b ${productDTO.unit === Unit.G ? "active-y" : ""}`}
                  />
                </div>
              </li>
              {isCompany && !hidePurchaseInfo && (
                <>
                {purchasePriceRow}
                <li className="popup-common-section-row flex align-items-center space-between g-10px mt-15 ">
                  <a className="product-form-input-title">VAT zakupu:</a>
                  <SelectVATButton
                    selectedVat={productDTO.fallbackVatRate ?? VatRate.VAT_23}
                    onSelect={handleVatPurchaseSelect}
                    className="product-form"
                    portal
                  />
                </li>
                </>
              )}
              </>
            )}
          <li className="popup-common-section-row space-between g-10px description flex-column align-items-start mt-15">
            <a className="product-form-input-title">Dodatkowe informacje:</a>
            <TextInput
              value={productDTO.description ?? ""}
              rows={4}
              multiline={true}
              onSelect={(newDesc) => {
                if (typeof newDesc === "string") {
                  handleDescription(newDesc);
                }
              }}
            />
          </li>
        </ul>
      </section>
    </div>
  );
}

export default ProductForm;
