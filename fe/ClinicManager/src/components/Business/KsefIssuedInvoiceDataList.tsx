import React from "react";
import { Eye, SquarePen, FileText, Trash2, Mail, MailCheck, MailWarning } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { KsefIssuedInvoice } from "../../models/ksef_issued_invoice";
import { NamedInvoice } from "../../models/named_invoice";
import { formatDate } from "../../utils/dateUtils";
import {
  getInvoiceSendStatus,
  invoiceSendTitle,
} from "../../utils/namedInvoiceUtils";

export type IssuedInvoiceSource = "KSEF" | "NAMED";

export interface IssuedInvoiceRow {
  rowKey: string;
  id: number;
  source: IssuedInvoiceSource;
  isCorrection: boolean;
  invoiceNumber: string;
  issueDate: string;
  buyerName: string | null;
  netAmount: number;
  vatAmount: number;
  grossAmount: number;
  named?: NamedInvoice;
}

export const toRowFromKsef = (invoice: KsefIssuedInvoice): IssuedInvoiceRow => ({
  rowKey: `ksef-${invoice.id}`,
  id: invoice.id,
  source: "KSEF",
  isCorrection: invoice.invoiceType?.startsWith("KOR") ?? false,
  invoiceNumber: invoice.invoiceNumber,
  issueDate: invoice.issueDate,
  buyerName: invoice.buyerName ?? invoice.buyerNip,
  netAmount: invoice.netAmount,
  vatAmount: invoice.vatAmount,
  grossAmount: invoice.grossAmount,
});

export const toRowFromNamed = (invoice: NamedInvoice): IssuedInvoiceRow => ({
  rowKey: `named-${invoice.id}`,
  id: invoice.id,
  source: "NAMED",
  isCorrection: false,
  invoiceNumber: invoice.invoiceNumber,
  issueDate: invoice.issueDate,
  buyerName: invoice.buyerName,
  netAmount: invoice.netAmount,
  vatAmount: invoice.vatAmount,
  grossAmount: invoice.grossAmount,
  named: invoice,
});

export interface KsefIssuedInvoiceDataListProps {
  items: IssuedInvoiceRow[];
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  onOpenPreview?: (row: IssuedInvoiceRow) => void;
  onOpenPdf?: (row: IssuedInvoiceRow) => void;
  onRemove?: (row: IssuedInvoiceRow) => void;
  onSendEmail?: (row: IssuedInvoiceRow) => void;
  className?: string;
}

export function KsefIssuedInvoiceDataList({
  items,
  onScroll,
  isLoading = false,
  onOpenPreview,
  onOpenPdf,
  onRemove,
  onSendEmail,
  className = "",
}: KsefIssuedInvoiceDataListProps) {
  const { isMobile, isNarrow } = useBreakpoint();

  const renderSendStatus = (item: IssuedInvoiceRow, className = "") =>
    item.source === "NAMED" && item.named ? (
      <ActionButton
        iconTitle={invoiceSendTitle(item.named)}
        className={className}
        onClick={(e) => {
          e.stopPropagation();
          onSendEmail?.(item);
        }}
        disableText={true}
        disableBorder={true}
      >
        {(() => {
          const status = getInvoiceSendStatus(item.named!);
          if (status === "SENT")
            return <MailCheck className="dl-icon c-green" />;
          if (status === "STALE")
            return <MailWarning className="dl-icon pc-y" />;
          return <Mail className="dl-icon" />;
        })()}
      </ActionButton>
    ) : null;

  const renderTypeBadge = (item: IssuedInvoiceRow) =>
    item.source === "NAMED" ? (
      <span className="ksef-badge named">Imienna</span>
    ) : item.isCorrection ? (
      <span className="ksef-badge">KOR</span>
    ) : (
      <span className="ksef-badge ksef-src">KSeF</span>
    );

  const mobileColumns: Column<IssuedInvoiceRow>[] = [
    {
      key: "buyer",
      grow: true,
      render: (item) => (
        <span className="exp-m-stack flex-column mw-0">
          <span className="dl-name">{item.buyerName ?? "-"}</span>
          <span className="exp-m-sub flex align-items-center g-05">
            {renderSendStatus(item, "kis-send")}
            {renderTypeBadge(item)}
            <span className="dl-dim">{item.invoiceNumber}</span>
            <span className="dl-dim">
              {item.issueDate ? formatDate(item.issueDate) : "-"}
            </span>
          </span>
        </span>
      ),
    },
    {
      key: "net",
      width: "4rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">{(item.netAmount ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "vat",
      width: "3.5rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">{(item.vatAmount ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "gross",
      width: "4.5rem",
      align: "end",
      render: (item) => (
        <span className="clx-form-value">
          {(item.grossAmount ?? 0).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "options",
      width: "1.9rem",
      align: "center",
      render: (item) => (
        <RowActionMenu
          items={[
            {
              label:
                item.source === "NAMED"
                  ? "Edytuj fakturę imienną"
                  : "Podgląd Faktury",
              icon:
                item.source === "NAMED" ? (
                  <SquarePen className="dl-icon c-blue" />
                ) : (
                  <Eye className="dl-icon" />
                ),
              onClick: () => onOpenPreview?.(item),
            },
            ...(item.source === "NAMED"
              ? [
                  {
                    label: "Otwórz PDF faktury",
                    icon: <FileText className="dl-icon pc-y" />,
                    onClick: () => onOpenPdf?.(item),
                  },
                  {
                    label: "Usuń fakturę imienną",
                    icon: <Trash2 className="dl-icon c-red" />,
                    onClick: () => onRemove?.(item),
                    danger: true,
                  },
                ]
              : []),
          ]}
        />
      ),
    },
  ];

  const columns: Column<IssuedInvoiceRow>[] = [
    {
      key: "send",
      header: "",
      width: "2.5rem",
      align: "center",
      render: (item) => renderSendStatus(item),
    },
    {
      key: "type",
      header: "",
      width: "6rem",
      align: "center",
      render: renderTypeBadge,
    },
    {
      key: "number",
      header: "Numer",
      width: "8rem",
      align: "start",
      render: (item) => <span className="dl-dim">{item.invoiceNumber}</span>,
    },
    {
      key: "buyer",
      header: "Nabywca",
      grow: true,
      render: (item) => <span className="dl-name">{item.buyerName ?? "-"}</span>,
    },
    {
      key: "issueDate",
      header: "Data",
      width: "7rem",
      align: "center",
      render: (item) => (
        <span className="dl-dim">
          {item.issueDate ? formatDate(item.issueDate) : "-"}
        </span>
      ),
    },
    {
      key: "net",
      header: "Netto",
      width: "6.5rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">{(item.netAmount ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "vat",
      header: "VAT",
      width: "6.5rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">{(item.vatAmount ?? 0).toFixed(2)} zł</span>
      ),
    },
    {
      key: "gross",
      header: "Brutto",
      width: "7rem",
      align: "end",
      render: (item) => (
        <span className="clx-form-value">
          {(item.grossAmount ?? 0).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "options",
      header: "Opcje",
      width: "7.5rem",
      align: "center",
      render: (item) => (
        <div className="flex align-items-center justify-center g-025">
          <ActionButton
            iconTitle={
              item.source === "NAMED"
                ? "Edytuj fakturę imienną"
                : "Podgląd Faktury"
            }
            onClick={(e) => {
              e.stopPropagation();
              onOpenPreview?.(item);
            }}
            disableText={true}
            disableBorder={true}
          >
            {item.source === "NAMED" ? (
              <SquarePen className="dl-icon c-blue" />
            ) : (
              <Eye className="dl-icon" />
            )}
          </ActionButton>
          {item.source === "NAMED" && (
            <ActionButton
              iconTitle="Otwórz PDF faktury"
              onClick={(e) => {
                e.stopPropagation();
                onOpenPdf?.(item);
              }}
              disableText={true}
              disableBorder={true}
            >
              <FileText className="dl-icon pc-y" />
            </ActionButton>
          )}
          {item.source === "NAMED" && (
            <ActionButton
              iconTitle="Usuń fakturę imienną"
              onClick={(e) => {
                e.stopPropagation();
                onRemove?.(item);
              }}
              disableText={true}
              disableBorder={true}
            >
              <Trash2 className="dl-icon c-red" />
            </ActionButton>
          )}
        </div>
      ),
    },
  ];

  return (
    <DataList<IssuedInvoiceRow>
      columns={
        isNarrow
          ? mobileColumns.filter((col) => col.key !== "net" && col.key !== "vat")
          : isMobile
            ? mobileColumns
            : columns
      }
      hideHeader={isMobile}
      items={items}
      getRowKey={(item) => item.rowKey}
      onRowClick={(item) => onOpenPreview?.(item)}
      isRowClickable={(item) => item.source === "KSEF"}
      onScroll={onScroll}
      isLoading={isLoading}
      emptyText="Brak faktur wystawionych za ten okres"
      className={`ksf-list ${className}`}
    />
  );
}

export default KsefIssuedInvoiceDataList;
