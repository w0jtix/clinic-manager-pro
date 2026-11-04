import React from "react";
import { SquarePen, StickyNote } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import { CashLedger } from "../../models/cash_ledger";
import { formatDate } from "../../utils/dateUtils";
import RowActionMenu from "../RowActionMenu";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import LabeledRow from "../LabeledRow";

export interface CashLedgerDataListProps {
  items: CashLedger[];
  setSelectedCashLedger?: (cl: CashLedger) => void;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  className?: string;
}

export function CashLedgerDataList({
  items,
  setSelectedCashLedger,
  onScroll,
  isLoading = false,
  className = "",
}: CashLedgerDataListProps) {
  const { isMobile } = useBreakpoint();

  const userRow = (label: string, name?: string) => (
    <span className="clh-m-user flex align-items-center g-025 mw-0">
      <span className="clh-m-lbl">{label}</span>
      <span className="dl-dim clh-m-name">{name ?? "-"}</span>
    </span>
  );

  const mobileColumns: Column<CashLedger>[] = [
    {
      key: "date",
      width: "4.25rem",
      render: (item) => (
        <span className="dl-name clh-date">{formatDate(item.date)}</span>
      ),
    },
    {
      key: "users",
      grow: true,
      render: (item) => (
        <span className="exp-m-stack flex-column mw-0">
          {userRow("Otworzył:", item.createdBy?.name)}
          {userRow("Zamknął:", item.closedBy?.name)}
        </span>
      ),
    },
    {
      key: "amounts",
      width: "10rem",
      render: (item) => (
        <span className="sh-m-comp flex-column width-max">
          <LabeledRow label="Otwarcie">
            {(item.openingAmount + item.deposit).toFixed(2)} zł
          </LabeledRow>
          <LabeledRow label="Depozyt">{item.deposit.toFixed(2)} zł</LabeledRow>
          <LabeledRow
            label="Wypłacono"
            valueClassName={item.cashOutAmount > 0 ? "c-red" : ""}
          >
            {item.cashOutAmount > 0 ? "-" : ""}
            {item.cashOutAmount.toFixed(2)} zł
          </LabeledRow>
          <LabeledRow label="Saldo końcowe" valueClassName="c-blue">
            {(item.closingAmount ?? 0).toFixed(2)} zł
          </LabeledRow>
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
              label: "Edytuj Kasetkę",
              icon: <SquarePen className="dl-icon c-blue" />,
              onClick: () => setSelectedCashLedger?.(item),
            },
          ]}
        />
      ),
    },
    {
      key: "note",
      width: "100%",
      cellClassName: "clh-note-cell",
      render: (item) =>
        item.note && item.note.trim() !== "" ? (
          <span className="clh-note flex align-items-start g-05 mw-0">
            <StickyNote className="dl-icon pc-y" />
            <span className="clh-note-text">{item.note}</span>
          </span>
        ) : null,
    },
  ];

  const columns: Column<CashLedger>[] = [
    {
      key: "date",
      header: "Data",
      width: "8rem",
      align: "start",
      render: (item) => <span className="dl-name">{formatDate(item.date)}</span>,
    },
    {
      key: "openedBy",
      header: "Otworzył",
      width: "8rem",
      align: "start",
      render: (item) => <span className="dl-dim">{item.createdBy?.name}</span>,
    },
    {
      key: "opening",
      header: "Otwarcie",
      grow: true,
      align: "start",
      render: (item) => (
        <span className="clx-form-value">
          {(item.openingAmount + item.deposit).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "deposit",
      header: "Depozyt",
      width: "8rem",
      align: "end",
      render: (item) => (
        <span className="dl-dim">{item.deposit.toFixed(2)} zł</span>
      ),
    },
    {
      key: "cashout",
      header: "Wypłacono",
      width: "8rem",
      align: "end",
      render: (item) => (
        <span className={item.cashOutAmount > 0 ? "clx-form-value c-red" : "dl-dim"}>
          {item.cashOutAmount > 0 ? "-" : ""}
          {item.cashOutAmount.toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "closing",
      header: "Saldo końcowe",
      width: "8rem",
      align: "end",
      render: (item) => (
        <span className="clx-form-value c-blue">
          {(item.closingAmount ?? 0).toFixed(2)} zł
        </span>
      ),
    },
    {
      key: "closedBy",
      header: "Zamknął",
      width: "8rem",
      align: "center",
      render: (item) => <span className="dl-dim">{item.closedBy?.name}</span>,
    },
    {
      key: "note",
      header: "Notatka",
      width: "5rem",
      align: "center",
      render: (item) =>
        item.note && item.note.trim() !== "" ? (
          <span className="flex" title={item.note}>
            <StickyNote className="dl-icon-1 pc-y" />
          </span>
        ) : (
          <span className="dl-dim">-</span>
        ),
    },
    {
      key: "options",
      header: "Opcje",
      width: "5rem",
      align: "center",
      render: (item) => (
        <ActionButton
          iconTitle="Edytuj Kasetkę"
          onClick={(e) => {
            e.stopPropagation();
            setSelectedCashLedger?.(item);
          }}
          disableText={true}
          disableBorder={true}
        >
          <SquarePen className="dl-icon c-blue" />
        </ActionButton>
      ),
    },
  ];

  return (
    <DataList<CashLedger>
      columns={isMobile ? mobileColumns : columns}
      hideHeader={isMobile}
      items={items}
      getRowKey={(item) => item.id ?? item.date}
      onScroll={onScroll}
      isLoading={isLoading}
      emptyText="Brak Kasetek"
      className={`clh-list ${className}`}
    />
  );
}

export default CashLedgerDataList;
