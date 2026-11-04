import React from "react";
import { Link } from "react-router-dom";
import { Eye } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { KsefInvoice, isCorrectionInvoice } from "../../models/ksef_invoice";
import { SellerOriginPeriod } from "../../models/ksef_seller";
import { formatDate } from "../../utils/dateUtils";

export interface KsefInvoiceDataListProps {
  items: KsefInvoice[];
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  onTogglePaid?: (id: number) => void;
  onOpenPreview?: (id: number) => void;
  originPeriod?: SellerOriginPeriod;
  className?: string;
}

export function KsefInvoiceDataList({
  items,
  onScroll,
  isLoading = false,
  onTogglePaid,
  onOpenPreview,
  originPeriod,
  className = "",
}: KsefInvoiceDataListProps) {
  const { isMobile, isNarrow } = useBreakpoint();

  const renderTypeBadge = (item: KsefInvoice) =>
    isCorrectionInvoice(item) ? (
      <span className="ksef-badge">KOR</span>
    ) : (
      <span className="ksef-badge vat">VAT</span>
    );

  const renderSellerName = (item: KsefInvoice) =>
    item.seller?.id ? (
      <Link
        to={`/ksef/seller/${item.seller.id}`}
        state={{ module: "KSeF", period: originPeriod }}
        className="dl-name pd-product-link"
        title="Otwórz stronę dostawcy"
        onClick={(e) => e.stopPropagation()}
      >
        {item.seller.name ?? item.seller.nip}
      </Link>
    ) : (
      <span className="dl-name">-</span>
    );

  const renderPaidStatus = (item: KsefInvoice) => (
    <span
      className={`rv-status ${item.isPaid ? "active" : "expired"} pointer`}
      title="Zmień status płatności"
      onClick={(e) => {
        e.stopPropagation();
        onTogglePaid?.(item.id);
      }}
    >
      {item.isPaid ? "OPŁACONA" : "NIEOPŁACONA"}
    </span>
  );

  const mobileColumns: Column<KsefInvoice>[] = [
    {
      key: "seller",
      grow: true,
      render: (item) => (
        <span className="exp-m-stack flex-column mw-0">
          {renderSellerName(item)}
          <span className="exp-m-sub flex align-items-center g-05">
            {renderTypeBadge(item)}
            <span className="dl-dim">
              {item.issueDate ? formatDate(item.issueDate) : "-"}
            </span>
            {!isCorrectionInvoice(item) && renderPaidStatus(item)}
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
              label: "Podgląd Faktury",
              icon: <Eye className="dl-icon" />,
              onClick: () => onOpenPreview?.(item.id),
            },
          ]}
        />
      ),
    },
  ];

  const columns: Column<KsefInvoice>[] = [
    {
      key: "type",
      header: "",
      width: "4rem",
      align: "center",
      render: renderTypeBadge,
    },
    {
      key: "seller",
      header: "Nazwa",
      grow: true,
      render: renderSellerName,
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
      width: "5rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">
          {(item.netAmount ?? 0).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "vat",
      header: "VAT",
      width: "6.5rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">
          {(item.vatAmount ?? 0).toFixed(2)} zł
        </span>
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
      key: "status",
      header: "Status",
      width: "9rem",
      align: "center",
      render: (item) =>
        isCorrectionInvoice(item) ? (
          <span className="dl-dim">-</span>
        ) : (
          renderPaidStatus(item)
        ),
    },
    {
      key: "options",
      header: "Opcje",
      width: "5rem",
      align: "center",
      render: (item) => (
        <ActionButton
          iconTitle="Podgląd Faktury"
          onClick={(e) => {
            e.stopPropagation();
            onOpenPreview?.(item.id);
          }}
          disableText={true}
          disableBorder={true}
        >
          <Eye className="dl-icon" />
        </ActionButton>
      ),
    },
  ];

  return (
    <DataList<KsefInvoice>
      columns={
        isNarrow
          ? mobileColumns.filter((col) => col.key !== "net" && col.key !== "vat")
          : isMobile
            ? mobileColumns
            : columns
      }
      hideHeader={isMobile}
      items={items}
      getRowKey={(item) => item.id}
      onRowClick={(item) => onOpenPreview?.(item.id)}
      onScroll={onScroll}
      isLoading={isLoading}
      emptyText="Brak faktur za ten okres"
      className={`ksf-list ${className}`}
    />
  );
}

export default KsefInvoiceDataList;
