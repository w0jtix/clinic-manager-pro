import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { Eye, Trash2, TriangleAlert, CircleAlert, Plus, SquarePen } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu, { RowActionItem } from "../RowActionMenu";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatDate } from "../../utils/dateUtils";
import {
  CompanyExpense, ExpenseCategory, ExpenseCategoryCode, getItemCategories, CURRENCY_SYMBOLS
} from "../../models/expense";

const isForeignExpense = (expense: CompanyExpense): boolean =>
  !!expense.currency && expense.currency !== "PLN";

const renderGross = (expense: CompanyExpense) => (
  <span className="flex-column align-items-end">
    <span className="clx-form-value">{(expense.totalValue ?? 0).toFixed(2)} zł</span>
    {isForeignExpense(expense) && expense.totalValueCurrency != null && (
      <span className="exp-amount-cur">
        {expense.totalValueCurrency.toFixed(2)} {CURRENCY_SYMBOLS[expense.currency!]}
      </span>
    )}
  </span>
);

const renderCurrencyTag = (expense: CompanyExpense) =>
  isForeignExpense(expense) ? <span className="exp-currency-tag">{expense.currency}</span> : null;
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { CategoryDots } from "./CategoryShard";
import { KSEF_FORBIDDEN_CATEGORIES } from "../../models/ksef_invoice";
import { SellerOriginPeriod } from "../../models/ksef_seller";

type ExpenseGroupKey = "NEEDS_CATEGORY" | "MANUAL" | "KSEF";

const getGroupKey = (expense: CompanyExpense): ExpenseGroupKey =>
  expense.category == null
    ? "NEEDS_CATEGORY"
    : expense.sourceType === "MANUAL"
      ? "MANUAL"
      : "KSEF";

const GROUP_TITLES: Record<ExpenseGroupKey, string> = {
  NEEDS_CATEGORY: "Wymagana akcja - przypisz kategorię",
  MANUAL: "Koszty wprowadzone ręcznie",
  KSEF: "Koszty wg KSeF",
};

export interface ExpenseDataListProps {
  expenses: CompanyExpense[];
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  setRemoveExpenseId: React.Dispatch<React.SetStateAction<number | null>>;
  onSelectCategory?: (expense: CompanyExpense, category: ExpenseCategoryCode) => void;
  onOpenPreview?: (id: number) => void;
  onEdit?: (id: number) => void;
  originPeriod?: SellerOriginPeriod;
  className?: string;
}

export function ExpenseDataList({
  expenses,
  onScroll,
  isLoading = false,
  setRemoveExpenseId,
  onSelectCategory,
  onOpenPreview,
  onEdit,
  originPeriod,
  className = "",
}: ExpenseDataListProps) {
  const { allItems: allExpenseCategoryItems, items: expenseCategoryItems } = useExpenseCategories();
  const [openCategoryMenuId, setOpenCategoryMenuId] = useState<number | null>(
    null
  );
  const { isMobile, isCompact } = useBreakpoint();

  useEffect(() => {
    if (openCategoryMenuId === null) return;
    const close = () => setOpenCategoryMenuId(null);
    document.addEventListener("click", close);
    return () => document.removeEventListener("click", close);
  }, [openCategoryMenuId]);

  const dotColor = useCallback((expense: CompanyExpense): string => {
    if (expense.awaitingProductVerification) return "var(--yellow-clinicmanager)";
    const color = expenseCategoryItems.find(
      (item) => item.id === expense.category
    )?.color;
    return color ? `rgb(${color})` : "rgba(255,255,255,0.2)";
  }, [expenseCategoryItems]);

  const renderCategoryMenu = (expense: CompanyExpense, alignStart = false) => (
    <div
      className={`exp-cat-menu absolute ${alignStart ? "start" : ""}`}
      onClick={(e) => e.stopPropagation()}
    >
      {expenseCategoryItems
        .filter((item) => !KSEF_FORBIDDEN_CATEGORIES.includes(item.id))
        .map((item) => (
          <div
            key={item.id}
            className="exp-cat-menu-item flex align-items-center g-05 pointer"
            onClick={(e) => {
              e.stopPropagation();
              setOpenCategoryMenuId(null);
              if (item.id !== expense.category) {
                onSelectCategory?.(expense, item.id);
              }
            }}
          >
            <span
              className="dl-dot"
              style={{ backgroundColor: `rgb(${item.color})` }}
            />
            <span>{item.name}</span>
          </div>
        ))}
    </div>
  );

  const rowActions = (expense: CompanyExpense): RowActionItem[] => {
    const isManual = expense.sourceType !== "KSEF";
    return [
      {
        label: isManual ? "Podgląd Kosztu" : "Podgląd Faktury",
        icon: <Eye className="dl-icon" />,
        onClick: () => onOpenPreview?.(expense.id),
      },
      ...(isManual && expense.category !== ExpenseCategory.SALARY && onEdit
        ? [
            {
              label: "Edytuj",
              icon: <SquarePen className="dl-icon c-blue" />,
              onClick: () => onEdit(expense.id),
            },
          ]
        : []),
      ...(isManual
        ? [
            {
              label: "Usuń Koszt",
              icon: <Trash2 className="dl-icon c-red" />,
              onClick: () => setRemoveExpenseId(expense.id),
              danger: true,
            },
          ]
        : []),
    ];
  };

  const renderSourceName = (expense: CompanyExpense) =>
    expense.sellerId ? (
      <Link
        to={`/ksef/seller/${expense.sellerId}`}
        state={{ module: "Expenses", period: originPeriod }}
        className="dl-name pd-product-link"
        title="Otwórz stronę dostawcy"
        onClick={(e) => e.stopPropagation()}
      >
        {expense.source}
      </Link>
    ) : (
      <span className="dl-name">{expense.source}</span>
    );

  const renderMobileCategory = (expense: CompanyExpense) => {
    if (expense.awaitingProductVerification) {
      return (
        <span className="exp-cat-badge exp-m-cat warn flex align-items-center g-025">
          <CircleAlert className="dl-icon-1 pc-y" />
          Weryfikacja
        </span>
      );
    }
    const catItem = allExpenseCategoryItems.find(
      (item) => item.id === expense.category
    );
    if (!catItem) return null;
    const badge = (
      <span
        className="exp-cat-badge exp-m-cat colored"
        style={{ "--cat-rgb": catItem.color } as React.CSSProperties}
      >
        {catItem.name}
      </span>
    );
    return expense.category === ExpenseCategory.MIXED ? (
      <>
        <CategoryDots categories={getItemCategories(expense)} />
        {badge}
      </>
    ) : (
      badge
    );
  };

  const mobileColumns: Column<CompanyExpense>[] = [
    {
      key: "source",
      grow: true,
      render: (expense) => (
        <span className="exp-m-source flex align-items-center g-05 mw-0">
          {getGroupKey(expense) === "NEEDS_CATEGORY" && (
            <span className="relative flex align-items-center">
              <ActionButton
                iconTitle="Przypisz kategorię"
                disableText={true}
                className="exp-cat-add"
                onClick={(e) => {
                  e.stopPropagation();
                  setOpenCategoryMenuId(
                    openCategoryMenuId === expense.id ? null : expense.id
                  );
                }}
              >
                <Plus className="dl-icon-1 exp-cat-add-icon" />
              </ActionButton>
              {openCategoryMenuId === expense.id &&
                renderCategoryMenu(expense, true)}
            </span>
          )}
          <span className="exp-m-stack flex-column mw-0">
            <span className="exp-m-name flex align-items-center mw-0">
              {renderCurrencyTag(expense)}
              {renderSourceName(expense)}
            </span>
            <span className="exp-m-sub flex align-items-center g-05">
              {expense.sourceType === "KSEF" && (
                <span className="exp-ksef-tag">KSEF</span>
              )}
              <span className="dl-dim">{formatDate(expense.expenseDate)}</span>
              {renderMobileCategory(expense)}
            </span>
          </span>
        </span>
      ),
    },
    {
      key: "net",
      width: "4rem",
      align: "end",
      render: (expense) => (
        <span className="dl-dim">{(expense.totalNet ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "vat",
      width: "3.5rem",
      align: "end",
      render: (expense) => (
        <span className="dl-dim">{(expense.totalVat ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "gross",
      width: "4.5rem",
      align: "end",
      render: renderGross,
    },
    {
      key: "options",
      width: "1.9rem",
      align: "center",
      render: (expense) => <RowActionMenu items={rowActions(expense)} />,
    },
  ];

  const desktopColumns: Column<CompanyExpense>[] = [
    {
      key: "category",
      header: "",
      width: "8rem",
      align: "center",
      render: (expense) => {
        if (expense.awaitingProductVerification) {
          return (
            <span className="exp-cat-badge warn flex align-items-center g-025">
              <CircleAlert className="dl-icon-1 pc-y" />
              Weryfikacja
            </span>
          );
        }

        const editable =
          expense.sourceType === "KSEF" &&
          expense.category !== ExpenseCategory.PRODUCTS &&
          !expense.invoiceType?.startsWith("KOR");

        if (!editable) {
          const staticCat = allExpenseCategoryItems.find(
            (item) => item.id === expense.category
          );
          if (!staticCat) {
            if (expense.invoiceType?.startsWith("KOR")) {
              return null;
            }
            return (
              <span
                className="dl-dot"
                style={{ backgroundColor: dotColor(expense) }}
              />
            );
          }
          const badge = (
            <span
              className="exp-cat-badge colored"
              style={{ "--cat-rgb": staticCat.color } as React.CSSProperties}
            >
              {staticCat.name}
            </span>
          );
          return expense.category === ExpenseCategory.MIXED ? (
            <span className="flex-column align-items-center g-025">
              {badge}
              <CategoryDots categories={getItemCategories(expense)} />
            </span>
          ) : (
            badge
          );
        }

        const catItem = expenseCategoryItems.find(
          (item) => item.id === expense.category
        );

        return (
          <div className="relative flex justify-center align-items-center">
            <span
              className={`exp-cat-badge pointer ${catItem ? "colored" : ""}`}
              style={
                catItem
                  ? ({ "--cat-rgb": catItem.color } as React.CSSProperties)
                  : undefined
              }
              title="Zmień kategorię"
              onClick={(e) => {
                e.stopPropagation();
                setOpenCategoryMenuId(
                  openCategoryMenuId === expense.id ? null : expense.id
                );
              }}
            >
              {catItem?.name ?? "Przypisz"}
            </span>
            {openCategoryMenuId === expense.id && renderCategoryMenu(expense)}
          </div>
        );
      },
    },
    {
      key: "source",
      header: "Dostawca",
      grow: true,
      headerClassName: "exp-source-head",
      render: (expense) => (
        <span className="exp-source flex align-items-center mw-0">
          <span className="exp-source-badge flex align-items-center">
            {expense.sourceType === "KSEF" && (
              <span className="exp-ksef-tag">KSEF</span>
            )}
          </span>
          {renderCurrencyTag(expense)}
          {renderSourceName(expense)}
        </span>
      ),
    },
    {
      key: "date",
      header: "Data",
      width: "7rem",
      align: "center",
      render: (expense) => (
        <span className="dl-dim">{formatDate(expense.expenseDate)}</span>
      ),
    },
    {
      key: "net",
      header: "Netto",
      width: "6.5rem",
      align: "end",
      render: (expense) => (
        <span className="dl-dim">{(expense.totalNet ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "vat",
      header: "VAT",
      width: "6.5rem",
      align: "end",
      render: (expense) => (
        <span className="dl-dim">{(expense.totalVat ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "gross",
      header: "Brutto",
      width: "7rem",
      align: "end",
      render: renderGross,
    },
    {
      key: "options",
      header: "Opcje",
      width: "5.5rem",
      align: "center",
      render: (expense) => (
        <span className="flex align-items-center justify-center" onClick={(e) => e.stopPropagation()}>
          <RowActionMenu items={rowActions(expense)} />
        </span>
      ),
    },
  ];

  return (
    <DataList<CompanyExpense>
      columns={
        isCompact
          ? mobileColumns.filter((col) => col.key !== "net" && col.key !== "vat")
          : isMobile
            ? mobileColumns
            : desktopColumns
      }
      hideHeader={isMobile}
      items={expenses}
      getRowKey={(expense) => `${expense.sourceType}-${expense.id}`}
      onRowClick={(expense) => onOpenPreview?.(expense.id)}
      groupBy={(expense) => getGroupKey(expense)}
      renderGroupHeader={(expense) => {
        const key = getGroupKey(expense);
        return (
          <span
            className={`exp-group-title flex align-items-center g-05 ${
              key === "NEEDS_CATEGORY" ? "warn" : ""
            }`}
          >
            {key === "NEEDS_CATEGORY" && <TriangleAlert className="dl-icon-1" />}
            {GROUP_TITLES[key]}
          </span>
        );
      }}
      rowClassName={(expense) =>
        expense.awaitingProductVerification ? "exp-row-warn" : ""
      }
      onScroll={onScroll}
      isLoading={isLoading}
      emptyText="Brak kosztów za ten okres"
      className={className}
    />
  );
}

export default ExpenseDataList;
