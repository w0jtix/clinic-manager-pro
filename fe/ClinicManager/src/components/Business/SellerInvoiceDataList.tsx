import React, { useMemo, useState } from "react";
import { CircleAlert, Eye, Hand } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import CategoryMenu from "../CategoryMenu";
import { formatDate } from "../../utils/dateUtils";
import {
  CompanyExpense, ExpenseCategory, ExpenseCategoryCode, getItemCategories
} from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { CategoryDots } from "./CategoryShard";
import {
  KSEF_FORBIDDEN_CATEGORIES,
  KsefInvoice,
  isCorrectionInvoice,
} from "../../models/ksef_invoice";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import LabeledRow from "../LabeledRow";

type SellerRow =
  | { kind: "invoice"; date: string; invoice: KsefInvoice }
  | { kind: "manual"; date: string; expense: CompanyExpense };

export interface SellerInvoiceDataListProps {
  invoices: KsefInvoice[];
  manualExpenses?: CompanyExpense[];
  onOpenExpensePreview?: (id: number) => void;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  onSelectCategory?: (invoice: KsefInvoice, category: ExpenseCategoryCode) => void;
  onTogglePaid?: (id: number) => void;
  onOpenPreview?: (id: number) => void;
  className?: string;
}

export function SellerInvoiceDataList({
  invoices,
  manualExpenses = [],
  onOpenExpensePreview,
  onScroll,
  isLoading = false,
  onSelectCategory,
  onTogglePaid,
  onOpenPreview,
  className = "",
}: SellerInvoiceDataListProps) {
  const { allItems: allExpenseCategoryItems, items: expenseCategoryItems } = useExpenseCategories();
  const [menu, setMenu] = useState<{ id: number; anchor: HTMLElement } | null>(null);
  const { isMobile } = useBreakpoint();

  const menuItems = useMemo(
    () =>
      expenseCategoryItems.filter(
        (item) => !KSEF_FORBIDDEN_CATEGORIES.includes(item.id)
      ),
    [expenseCategoryItems]
  );

  const menuInvoice = menu ? invoices.find((inv) => inv.id === menu.id) ?? null : null;

  const rows = useMemo<SellerRow[]>(
    () =>
      [
        ...invoices.map((invoice): SellerRow => ({
          kind: "invoice",
          date: invoice.issueDate,
          invoice,
        })),
        ...manualExpenses.map((expense): SellerRow => ({
          kind: "manual",
          date: expense.expenseDate,
          expense,
        })),
      ].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0)),
    [invoices, manualExpenses]
  );

  const manualBadge = (
    <span className="exp-cat-badge manual flex align-items-center" title="Koszt wprowadzony ręcznie">
      <Hand className="dl-icon-1" />
    </span>
  );

  const renderManualCategory = (expense: CompanyExpense) => {
    const categoryItem = allExpenseCategoryItems.find((item) => item.id === expense.category);
    if (!categoryItem) return <span className="dl-dim">-</span>;
    const badge = (
      <span
        className="exp-cat-badge colored"
        style={{ "--cat-rgb": categoryItem.color } as React.CSSProperties}
      >
        {categoryItem.name}
      </span>
    );
    return expense.category === ExpenseCategory.MIXED ? (
      <span className={`${isMobile ? "flex" : "flex-column"} align-items-center g-025`}>
        {badge}
        <CategoryDots categories={getItemCategories(expense)} />
      </span>
    ) : (
      badge
    );
  };

  const renderCategory = (invoice: KsefInvoice) => {
    if (invoice.awaitingProductVerification) {
      return (
        <span className="exp-cat-badge warn flex align-items-center g-025">
          <CircleAlert className="dl-icon-1 pc-y" />
          Weryfikacja
        </span>
      );
    }

    const categoryItem = expenseCategoryItems.find(
      (item) => item.id === invoice.category
    );
    const editable =
      !isCorrectionInvoice(invoice) && invoice.category !== ExpenseCategory.PRODUCTS;

    if (!editable) {
      return categoryItem ? (
        <span
          className="exp-cat-badge colored"
          style={{ "--cat-rgb": categoryItem.color } as React.CSSProperties}
        >
          {categoryItem.name}
        </span>
      ) : (
        <span className="dl-dim">-</span>
      );
    }

    return (
      <button
        type="button"
        className={`exp-cat-badge pointer ${categoryItem ? "colored" : ""}`}
        style={
          categoryItem
            ? ({ "--cat-rgb": categoryItem.color } as React.CSSProperties)
            : undefined
        }
        title="Zmień kategorię"
        onClick={(e) => {
          e.stopPropagation();
          const anchor = e.currentTarget;
          setMenu((prev) =>
            prev?.id === invoice.id ? null : { id: invoice.id, anchor }
          );
        }}
      >
        {categoryItem?.name ?? "Przypisz"}
      </button>
    );
  };

  const renderType = (invoice: KsefInvoice) =>
    isCorrectionInvoice(invoice) ? (
      <span className="ksef-badge">KOR</span>
    ) : (
      <span className="ksef-badge vat">VAT</span>
    );

  const renderStatus = (invoice: KsefInvoice) =>
    isCorrectionInvoice(invoice) ? null : (
      <span
        className={`rv-status ${invoice.isPaid ? "active" : "expired"} pointer`}
        title="Zmień status płatności"
        onClick={(e) => {
          e.stopPropagation();
          onTogglePaid?.(invoice.id);
        }}
      >
        {invoice.isPaid ? "OPŁACONA" : "NIEOPŁACONA"}
      </span>
    );

  const previewButton = (row: SellerRow) => (
    <ActionButton
      iconTitle={row.kind === "invoice" ? "Podgląd Faktury" : "Podgląd Kosztu"}
      onClick={(e) => {
        e.stopPropagation();
        if (row.kind === "invoice") onOpenPreview?.(row.invoice.id);
        else onOpenExpensePreview?.(row.expense.id);
      }}
      disableText={true}
      disableBorder={true}
    >
      <Eye className="dl-icon" />
    </ActionButton>
  );

  const amounts = (row: SellerRow) =>
    row.kind === "invoice"
      ? {
          net: row.invoice.netAmount ?? 0,
          vat: row.invoice.vatAmount ?? 0,
          gross: row.invoice.grossAmount ?? 0,
        }
      : {
          net: row.expense.totalNet ?? 0,
          vat: row.expense.totalVat ?? 0,
          gross: row.expense.totalValue ?? 0,
        };

  const mobileColumns: Column<SellerRow>[] = [
    {
      key: "main",
      grow: true,
      render: (row) =>
        row.kind === "invoice" ? (
          <span className="exp-m-stack sd-inv-m flex-column mw-0">
            <span className="flex align-items-center g-05 mw-0">
              <span className="dl-name sd-inv-m-num">{row.invoice.invoiceNumber}</span>
              <span className="dl-dim">{formatDate(row.invoice.issueDate)}</span>
            </span>
            <span className="sd-inv-m-badges flex flex-wrap align-items-center g-05">
              {renderType(row.invoice)}
              {renderStatus(row.invoice)}
              {renderCategory(row.invoice)}
            </span>
          </span>
        ) : (
          <span className="exp-m-stack sd-inv-m flex-column mw-0">
            <span className="flex align-items-center g-05 mw-0">
              <span className="dl-dim">{formatDate(row.expense.expenseDate)}</span>
            </span>
            <span className="sd-inv-m-badges flex flex-wrap align-items-center g-05">
              {manualBadge}
              {renderManualCategory(row.expense)}
            </span>
          </span>
        ),
    },
    {
      key: "amounts",
      width: "8.5rem",
      render: (row) => {
        const { net, vat, gross } = amounts(row);
        return (
          <span className="sh-m-comp flex-column width-max">
            <LabeledRow label="Netto" valueClassName="sd-inv-m-dim">
              {net.toFixed(2)} zł
            </LabeledRow>
            <LabeledRow label="VAT" valueClassName="sd-inv-m-dim">
              {vat.toFixed(2)} zł
            </LabeledRow>
            <LabeledRow label="Brutto" valueClassName="sd-inv-m-gross">
              {gross.toFixed(2)} zł
            </LabeledRow>
          </span>
        );
      },
    },
    {
      key: "options",
      width: "1.9rem",
      align: "center",
      render: previewButton,
    },
  ];

  const columns: Column<SellerRow>[] = [
    {
      key: "category",
      header: "Kategoria",
      width: "9rem",
      align: "center",
      render: (row) =>
        row.kind === "invoice" ? (
          renderCategory(row.invoice)
        ) : (
          <span className="flex align-items-center justify-center g-025">
            {manualBadge}
            {renderManualCategory(row.expense)}
          </span>
        ),
    },
    {
      key: "date",
      header: "Data",
      width: "7rem",
      align: "center",
      render: (row) => <span className="dl-dim">{formatDate(row.date)}</span>,
    },
    {
      key: "type",
      header: "Typ",
      width: "4rem",
      align: "center",
      render: (row) =>
        row.kind === "invoice" ? renderType(row.invoice) : <span className="dl-dim">-</span>,
    },
    {
      key: "number",
      header: "Numer",
      grow: true,
      render: (row) =>
        row.kind === "invoice" ? (
          <span className="flex-column mw-0">
            <span className="dl-name">{row.invoice.invoiceNumber}</span>
            {row.invoice.correctedKsefNumber && (
              <span className="sd-sub">do {row.invoice.correctedKsefNumber}</span>
            )}
          </span>
        ) : (
          <span className="dl-dim">-</span>
        ),
    },
    {
      key: "net",
      header: "Netto",
      width: "6.5rem",
      align: "end",
      render: (row) => <span className="dl-dim">{amounts(row).net.toFixed(2)} zł</span>,
    },
    {
      key: "vat",
      header: "VAT",
      width: "6.5rem",
      align: "end",
      render: (row) => <span className="dl-dim">{amounts(row).vat.toFixed(2)} zł</span>,
    },
    {
      key: "gross",
      header: "Brutto",
      width: "7rem",
      align: "end",
      render: (row) => (
        <span className="clx-form-value">{amounts(row).gross.toFixed(2)} zł</span>
      ),
    },
    {
      key: "status",
      header: "Status",
      width: "9rem",
      align: "center",
      render: (row) =>
        row.kind === "manual" || isCorrectionInvoice(row.invoice) ? (
          <span className="dl-dim">-</span>
        ) : (
          renderStatus(row.invoice)
        ),
    },
    {
      key: "options",
      header: "Podgląd",
      width: "5rem",
      align: "center",
      render: previewButton,
    },
  ];

  return (
    <>
      <DataList<SellerRow>
        columns={isMobile ? mobileColumns : columns}
        hideHeader={isMobile}
        items={rows}
        onRowClick={(row) =>
          row.kind === "invoice"
            ? onOpenPreview?.(row.invoice.id)
            : onOpenExpensePreview?.(row.expense.id)
        }
        getRowKey={(row) =>
          row.kind === "invoice" ? `inv-${row.invoice.id}` : `exp-${row.expense.id}`
        }
        rowClassName={(row) =>
          row.kind === "invoice" && row.invoice.awaitingProductVerification
            ? "exp-row-warn"
            : ""
        }
        onScroll={onScroll}
        isLoading={isLoading}
        emptyText="Brak faktur i kosztów od tego dostawcy"
        className={`sd-inv-list ${className}`}
      />
      {menu && menuInvoice && (
        <CategoryMenu
          anchor={menu.anchor}
          items={menuItems}
          selected={menuInvoice.category}
          onSelect={(category) => onSelectCategory?.(menuInvoice, category)}
          onClose={() => setMenu(null)}
        />
      )}
    </>
  );
}

export default SellerInvoiceDataList;
