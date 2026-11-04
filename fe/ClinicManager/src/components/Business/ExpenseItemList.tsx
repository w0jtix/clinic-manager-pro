import { useCallback, useMemo, useState, type CSSProperties, type ReactNode } from "react";
import { RotateCcw, Trash2, X } from "lucide-react";
import {
  ExpenseCategory, ExpenseCategoryCode, NewCompanyExpenseItem
} from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import CategoryMenu from "../CategoryMenu";
import CategoryShard from "./CategoryShard";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import { VatRate, getVatRateDisplay } from "../../models/vatrate";
import CostInput from "../CostInput";
import DigitInput from "../DigitInput";
import SelectVATButton from "../SelectVATButton";
import DropdownSelect from "../DropdownSelect";
import { Product } from "../../models/product";
import ProductModal from "../Popups/ProductModal";
import { useBreakpoint } from "../../hooks/useBreakpoint";

type CardField = "name" | "qty" | "vat" | "price" | "total";

const roundMoney = (value: number): number => Math.round(value * 100) / 100;

const roundUnitPrice = (value: number): number => Math.round(value * 10000) / 10000;

const isSameAmount = (a: number, b: number): boolean => Math.abs(a - b) < 0.001;
type CardLayout = "editable" | "template" | "preview";

const CARD_ROWS: Record<CardLayout, CardField[][]> = {
  editable: [["name", "qty"], ["price", "vat", "total"]],
  template: [["name", "qty", "vat"], ["price", "total"]],
  preview: [["name", "qty", "vat", "price"], ["total"]],
};

const NARROW_CARD_ROWS: Record<CardLayout, CardField[][]> = {
  editable: [["name"], ["qty", "vat"], ["price", "total"]],
  template: [["name", "qty", "vat"], ["price", "total"]],
  preview: [["name", "qty", "vat"], ["price", "total"]],
};

const CARD_FIELDS: Record<CardField, { label: string; className: string }> = {
  name: { label: "Nazwa:", className: "ni-m-name" },
  qty: { label: "Ilość:", className: "ni-m-qty" },
  vat: { label: "Vat:", className: "ni-m-vat" },
  price: { label: "Cena:", className: "ni-m-price" },
  total: { label: "Σ:", className: "ni-m-gross" },
};

export interface ExpenseItemListProps {
  className?: string;
  expenseItems: NewCompanyExpenseItem[];
  setExpenseItems: React.Dispatch<
    React.SetStateAction<NewCompanyExpenseItem[]>
  >;
  disabled?: boolean;
  preview?: boolean;
  fixedItemCount?: number;
  highlightProductId?: number | null;
  allProducts?: Product[];
  onProductCreated?: (product: Product) => void;
  productsMode?: boolean;
  mixedMode?: boolean;
  emptyAction?: React.ReactNode;
  currencySymbol?: string;
}

export function ExpenseItemList({
  className = "",
  expenseItems,
  setExpenseItems,
  disabled = false,
  preview = false,
  fixedItemCount = 0,
  highlightProductId = null,
  allProducts,
  onProductCreated,
  productsMode = false,
  mixedMode = false,
  emptyAction,
  currencySymbol,
}: ExpenseItemListProps) {
  const currencySuffix = currencySymbol ? ` (${currencySymbol})` : "";
  const { itemCategoryItems } = useExpenseCategories();
  const { isCompact, isNarrow } = useBreakpoint();
  const [categoryMenu, setCategoryMenu] = useState<{ index: number; anchor: HTMLElement } | null>(
    null
  );
  const handleItemCategory = useCallback((index: number, category: ExpenseCategoryCode) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) => {
        if (i !== index || ei.category === category) return ei;
        if (category === ExpenseCategory.PRODUCTS) {
          return { ...ei, category, productId: null, name: "" };
        }
        return {
          ...ei,
          category,
          productId: null,
          name: ei.category === ExpenseCategory.PRODUCTS ? "" : ei.name,
        };
      })
    );
  }, []);
  const handleToggleNotProduct = useCallback((index: number) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) =>
        i === index
          ? { ...ei, notProduct: !ei.notProduct, productId: null, name: "" }
          : ei
      )
    );
  }, []);
  const handleExpenseItemRemove = useCallback((index: number) => {
    setExpenseItems((prev) => prev.filter((_, i) => i !== index));
  }, []);
  const handleExpenseItemName = useCallback((index: number, name: string) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) => (i === index ? { ...ei, name } : ei))
    );
  }, []);
  const handleExpenseItemProductSelect = useCallback(
    (index: number, product: Product | null) => {
      setExpenseItems((prev) =>
        prev.map((ei, i) =>
          i === index
            ? { ...ei, name: product?.name ?? "", productId: product?.id ?? null }
            : ei
        )
      );
    },
    []
  );
  const handleUnitPriceChange = useCallback((index: number, value: number) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) =>
        i === index && !isSameAmount(ei.price, value)
          ? { ...ei, price: value, lockedTotal: null }
          : ei
      )
    );
  }, []);
  const handleTotalChange = useCallback((index: number, value: number) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) => {
        if (i !== index || isSameAmount(roundMoney(ei.price * ei.quantity), value)) return ei;
        return {
          ...ei,
          lockedTotal: value,
          price: ei.quantity > 0 ? roundUnitPrice(value / ei.quantity) : ei.price,
        };
      })
    );
  }, []);
  const handleQuantityChange = useCallback((index: number, quantity: number) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) => {
        if (i !== index) return ei;
        const price =
          ei.lockedTotal != null && quantity > 0
            ? roundUnitPrice(ei.lockedTotal / quantity)
            : ei.price;
        return { ...ei, quantity, price };
      })
    );
  }, []);
  const handleVatSelect = useCallback((index: number, selectedVAT: VatRate) => {
    setExpenseItems((prev) =>
      prev.map((ei, i) => (i === index ? { ...ei, vatRate: selectedVAT } : ei))
    );
  }, []);
  const productOptions = useMemo(
    () => (allProducts ?? []).map((p) => ({ ...p, color: p.category?.color })),
    [allProducts]
  );

  const calculateTotalPrice = (price: number, quantity: number): number => {
    const total = roundMoney(price * quantity);
    return isNaN(total) ? 0 : total;
  };

  return (
    <div
      className={`exi flex-column width-max ${
        (productsMode && !preview) || mixedMode ? "exi-products" : ""
      } ${preview ? "exi-preview" : ""} ${className}`}
    >
      {!isCompact && (
        <div className="exi-head">
          {mixedMode && preview ? (
            <span className="exi-col exi-cat">Kategoria</span>
          ) : (
            ((productsMode && !preview) || mixedMode) && <span className="exi-col exi-np" />
          )}
          <span className="exi-col exi-name">Nazwa</span>
          <span className="exi-col exi-unit">Cena jedn.{currencySuffix}</span>
          <span className="exi-col exi-qty">Ilość</span>
          <span className="exi-col exi-vat">VAT</span>
          <span className="exi-col exi-total">Cena{currencySuffix}</span>
          {!preview && <span className="exi-col exi-rm" />}
        </div>
      )}

      <div className="exi-list scrollbar flex-column">
        {expenseItems.length === 0 && (
          <div className="exi-empty-row">
            {emptyAction && <div className="exi-empty-action">{emptyAction}</div>}
            <span className="exi-empty italic">Brak pozycji</span>
          </div>
        )}
        {expenseItems.map((item, index) => {
          const selectedProduct =
            productOptions.find((p) => p.id === item.productId) ?? null;
          const locked = preview || index < fixedItemCount;
          const isNotProduct = productsMode && (locked ? !item.productId : !!item.notProduct);

          const nameField = locked ? (
            <span className="exi-name-locked flex align-items-center g-05 mw-0">
              <span
                className={`dl-name ${
                  highlightProductId != null &&
                  item.productId === highlightProductId
                    ? "pc-y"
                    : ""
                }`}
              >
                {item.name}
              </span>
              {isNotProduct && <span className="exi-not-product">nie produkt</span>}
            </span>
          ) : isNotProduct ? (
            <TextInput
              dropdown={false}
              value={item.name}
              placeholder="Nazwa (nie trafia na magazyn)"
              onSelect={(input) => {
                if (typeof input === "string") {
                  handleExpenseItemName(index, input);
                }
              }}
              disabled={disabled}
              className={isCompact ? "in-full" : "width-max"}
            />
          ) : allProducts && (!mixedMode || item.category === ExpenseCategory.PRODUCTS) ? (
            <DropdownSelect<Product>
              items={productOptions}
              value={selectedProduct}
              onChange={(selected) =>
                handleExpenseItemProductSelect(
                  index,
                  selected as Product | null
                )
              }
              getItemLabel={(p) => p.name}
              searchable={true}
              allowNew={true}
              showNewPopup={true}
              newItemComponent={ProductModal}
              newItemLabel="Dodaj produkt"
              newItemProps={{
                onReset: (createdProduct?: Product) => {
                  if (!createdProduct) return;
                  onProductCreated?.(createdProduct);
                  handleExpenseItemProductSelect(index, createdProduct);
                },
                initialVatRate: item.vatRate,
                hideStock: true,
                hidePurchaseInfo: true,
              }}
              allowColors={true}
              placeholder="Wybierz produkt..."
              searchPlaceholder="Szukaj produktu..."
              className="expense-product-dropdown"
            />
          ) : (
            <TextInput
              dropdown={false}
              value={item.name}
              onSelect={(input) => {
                if (typeof input === "string") {
                  handleExpenseItemName(index, input);
                }
              }}
              disabled={disabled}
              className={isCompact ? "in-full" : "width-max"}
            />
          );

          const unitField = preview ? (
            <span className="dl-dim">{item.price.toFixed(2)}</span>
          ) : (
            <CostInput
              selectedCost={item.price}
              onChange={(value) =>
                handleUnitPriceChange(index, parseFloat(value.toString()) || 0)
              }
              disabled={disabled}
              className="in-sm"
            />
          );

          const qtyField = locked ? (
            <span className="dl-dim">{item.quantity}</span>
          ) : (
            <DigitInput
              placeholder="1"
              value={item.quantity}
              onChange={(value) =>
                handleQuantityChange(index, parseInt(value ? value.toString() : "0") || 0)
              }
              disabled={disabled}
              className="in-xs"
            />
          );

          const vatField = locked ? (
            <span className="dl-dim">
              {item.vatRate ? getVatRateDisplay(item.vatRate) : "-"}
            </span>
          ) : (
            <SelectVATButton
              selectedVat={item.vatRate}
              onSelect={(selectedVAT) => handleVatSelect(index, selectedVAT)}
              disabled={disabled}
              portal={true}
            />
          );

          const totalField = preview ? (
            <span className="clx-form-value">
              {calculateTotalPrice(item.price, item.quantity).toFixed(2)} {currencySymbol ?? "zł"}
            </span>
          ) : (
            <CostInput
              selectedCost={calculateTotalPrice(item.price, item.quantity)}
              onChange={(value) =>
                handleTotalChange(index, parseFloat(value.toString()) || 0)
              }
              disabled={disabled}
              className="in-sm"
            />
          );

          const notProductButton = productsMode && !locked && (
            <ActionButton
              iconTitle={isNotProduct ? "Przywróć jako produkt" : "Nie jest produktem"}
              onClick={() => handleToggleNotProduct(index)}
              disableText={true}
              disableBorder={true}
              disabled={disabled}
            >
              {isNotProduct ? (
                <RotateCcw className="dl-icon" />
              ) : (
                <X className="dl-icon c-red" />
              )}
            </ActionButton>
          );

          const itemCategory = itemCategoryItems.find((c) => c.id === item.category);
          const categoryBadge = mixedMode && preview && (
            <span
              className={`exp-cat-badge exi-cat-badge ${itemCategory ? "colored" : ""}`}
              style={itemCategory ? ({ "--cat-rgb": itemCategory.color } as CSSProperties) : undefined}
              title={itemCategory?.name ?? "Brak kategorii"}
            >
              {itemCategory?.name ?? "Brak kategorii"}
            </span>
          );
          const shardField = mixedMode && !preview && (
            <CategoryShard
              color={itemCategory?.color}
              title={
                preview
                  ? itemCategory?.name ?? "Brak kategorii"
                  : itemCategory
                    ? `${itemCategory.name} - zmień kategorię`
                    : "Przypisz kategorię"
              }
              onClick={
                preview || locked
                  ? undefined
                  : (e) => {
                      e.stopPropagation();
                      const anchor = e.currentTarget as HTMLElement;
                      setCategoryMenu((prev) =>
                        prev?.index === index ? null : { index, anchor }
                      );
                    }
              }
            />
          );

          const removeButton = !preview && (
            <ActionButton
              iconTitle="Usuń pozycję"
              onClick={() => handleExpenseItemRemove(index)}
              disableText={true}
              disableBorder={true}
              disabled={disabled}
            >
              <Trash2 className="dl-icon c-red" />
            </ActionButton>
          );

          if (isCompact) {
            const layout: CardLayout = preview
              ? "preview"
              : locked
                ? "template"
                : "editable";
            const rows = (isNarrow ? NARROW_CARD_ROWS : CARD_ROWS)[layout];
            const values: Record<CardField, ReactNode> = {
              name: nameField,
              qty: qtyField,
              vat: vatField,
              price: unitField,
              total: totalField,
            };
            return (
              <div
                key={`exi-${index}`}
                className={`ni-row-mobile exi-row-m exi-card-${layout} ${
                  mixedMode ? "exi-row-mixed" : ""
                } flex align-items-center g-05 ${
                  index % 2 === 1 ? "ksef-items-row-alt" : ""
                }`}
              >
                {notProductButton && <span className="exi-np-m">{notProductButton}</span>}
                <span className="ni-m-index pc-y flex align-items-center g-025">
                  {shardField}
                  Pozycja {index + 1}.
                  {categoryBadge}
                </span>
                <div className="exi-card-body flex-column f-1 mw-0 g-05">
                  {rows.map((row) => (
                    <div
                      key={row.join("-")}
                      className="exi-card-row flex align-items-center g-05"
                    >
                      {row.map((field) => (
                        <label
                          key={field}
                          className={`ni-m-field ${CARD_FIELDS[field].className} flex align-items-center mw-0 g-05`}
                        >
                          <span className="ksef-party-label ni-label">
                            {CARD_FIELDS[field].label}
                          </span>
                          {values[field]}
                        </label>
                      ))}
                    </div>
                  ))}
                </div>
                {removeButton}
              </div>
            );
          }

          return (
            <div key={`exi-${index}`} className="exi-row">
              {productsMode && !preview && <div className="exi-col exi-np">{notProductButton}</div>}
              {mixedMode && preview && <div className="exi-col exi-cat">{categoryBadge}</div>}
              {mixedMode && !preview && <div className="exi-col exi-np">{shardField}</div>}
              <div className="exi-col exi-name">{nameField}</div>
              <div className="exi-col exi-unit">{unitField}</div>
              <div className="exi-col exi-qty">{qtyField}</div>
              <div className="exi-col exi-vat">{vatField}</div>
              <div className="exi-col exi-total">{totalField}</div>
              {!preview && <div className="exi-col exi-rm">{removeButton}</div>}
            </div>
          );
        })}
      </div>
      {categoryMenu && expenseItems[categoryMenu.index] && (
        <CategoryMenu
          anchor={categoryMenu.anchor}
          items={itemCategoryItems}
          selected={expenseItems[categoryMenu.index].category ?? null}
          onSelect={(category) => handleItemCategory(categoryMenu.index, category)}
          onClose={() => setCategoryMenu(null)}
          align="start"
        />
      )}
    </div>
  );
}

export default ExpenseItemList;
