import React, { useMemo } from "react";
import { Eye } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import {
  SupplyChangeSource,
  SupplyHistoryEntry,
  getSupplySourceDisplay,
  getSupplySourceGroup,
} from "../../models/supply-history";
import { formatDate } from "../../utils/dateUtils";
import { useBreakpoint } from "../../hooks/useBreakpoint";

const DELETED_SOURCE_COLOR = "255, 70, 70";

export interface SupplyHistoryListProps {
  entries: SupplyHistoryEntry[];
  onPreview: (entry: SupplyHistoryEntry) => void;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  className?: string;
}

export function SupplyHistoryList({
  entries,
  onPreview,
  onScroll,
  isLoading = false,
  className = "",
}: SupplyHistoryListProps) {
  const { isMobile, isCompact } = useBreakpoint();

  const columns = useMemo<Column<SupplyHistoryEntry>[]>(() => {
    const renderChip = (entry: SupplyHistoryEntry) => {
      const group = getSupplySourceGroup(entry.source);
      const color =
        entry.source === SupplyChangeSource.COMPANY_EXPENSE_DELETED
          ? DELETED_SOURCE_COLOR
          : group.color;
      return (
        <span
          className={`pd-chip ${
            entry.source === SupplyChangeSource.KSEF_CORRECTION ? "correction" : ""
          }`}
          style={{ "--src-rgb": color } as React.CSSProperties}
        >
          <span className="dl-dot" />
          {getSupplySourceDisplay(entry.source)}
        </span>
      );
    };

    const renderDocument = (entry: SupplyHistoryEntry) => {
      const deleted = entry.sourceId != null && !entry.sourceExists;
      return (
        <span className="flex align-items-center g-05 mw-0">
          <span className={`dl-name ${deleted ? "pd-src-deleted" : ""}`}>
            {entry.sourceLabel ?? "-"}
          </span>
          {deleted && <span className="pd-src-deleted-tag italic">usunięty</span>}
        </span>
      );
    };

    const renderDelta = (entry: SupplyHistoryEntry) => {
      const isAdjustment = getSupplySourceGroup(entry.source).id === "INVENTORY";
      const tone = isAdjustment
        ? "c-blue"
        : entry.delta > 0
          ? "c-green"
          : "c-red";
      return (
        <span className={`tnum ${tone}`}>
          {entry.delta > 0 ? "+" : "−"}
          {Math.abs(entry.delta)}
        </span>
      );
    };

    const previewColumn: Column<SupplyHistoryEntry> = {
      key: "preview",
      header: "Podgląd",
      width: isCompact ? "1.9rem" : isMobile ? "3.5rem" : "5rem",
      align: "center",
      render: (entry) => {
        const hasDocument =
          entry.sourceId != null &&
          entry.sourceExists &&
          getSupplySourceGroup(entry.source).id !== "FORM";
        if (!hasDocument) return null;
        return (
          <ActionButton
            iconTitle="Podgląd dokumentu"
            disableText={true}
            disableBorder={true}
            onClick={(e: React.MouseEvent) => {
              e.stopPropagation();
              onPreview(entry);
            }}
          >
            <Eye className="dl-icon" />
          </ActionButton>
        );
      },
    };

    if (isCompact) {
      return [
        {
          key: "source",
          width: "2rem",
          align: "center",
          render: (entry) => (
            <span
              className="dl-dot"
              title={getSupplySourceDisplay(entry.source)}
              style={{
                backgroundColor: `rgb(${getSupplySourceGroup(entry.source).color})`,
              }}
            />
          ),
        },
        {
          key: "sourceLabel",
          grow: true,
          render: (entry) => (
            <span className="pd-hist-stack">
              {renderDocument(entry)}
              <span className="pd-hist-sub">
                <span className="tnum">{formatDate(entry.occurredAt)}</span>
                <span className="pd-hist-who">{entry.performedBy}</span>
              </span>
            </span>
          ),
        },
        {
          key: "delta",
          width: "2.5rem",
          align: "center",
          render: renderDelta,
        },
        previewColumn,
      ];
    }

    return [
      {
        key: "source",
        header: "Źródło",
        width: isMobile ? "10rem" : "12rem",
        render: renderChip,
      },
      {
        key: "occurredAt",
        header: "Data",
        width: "7rem",
        render: (entry) => (
          <span className="dl-dim">{formatDate(entry.occurredAt)}</span>
        ),
      },
      {
        key: "performedBy",
        header: "Wykonał",
        width: isMobile ? "6rem" : "9rem",
        render: (entry) => <span className="dl-dim">{entry.performedBy}</span>,
      },
      {
        key: "sourceLabel",
        header: "Dokument",
        grow: true,
        render: renderDocument,
      },
      {
        key: "delta",
        header: "Zmiana",
        width: isMobile ? "4.5rem" : "6rem",
        align: "end",
        render: renderDelta,
      },
      {
        key: "supplyAfter",
        header: "Stan po",
        width: isMobile ? "4.5rem" : "6rem",
        align: "end",
        render: (entry) => <span className="tnum dl-dim">{entry.supplyAfter}</span>,
      },
      previewColumn,
    ];
  }, [onPreview, isMobile, isCompact]);

  return (
    <DataList<SupplyHistoryEntry>
      columns={columns}
      items={entries}
      getRowKey={(entry) => entry.id}
      onRowClick={onPreview}
      isRowClickable={(entry) =>
        entry.sourceId != null &&
        entry.sourceExists &&
        getSupplySourceGroup(entry.source).id !== "FORM"
      }
      onScroll={onScroll}
      isLoading={isLoading}
      emptyText="Brak zmian stanu"
      hideHeader={isCompact}
      className={`pd-hist-list ${className}`}
    />
  );
}

export default SupplyHistoryList;
