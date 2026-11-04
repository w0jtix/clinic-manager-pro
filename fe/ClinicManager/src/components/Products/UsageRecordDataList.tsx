import { useCallback, useMemo } from "react";
import { Trash2, CalendarX2, Webhook } from "lucide-react";
import {
  UsageReason,
  UsageRecord,
  getUsageReasonDisplay,
} from "../../models/usage-record";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import { formatDate } from "../../utils/dateUtils";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";
import { MedicalMark } from "../ProductVatTag";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface UsageRecordDataListProps {
  items: UsageRecord[];
  setRemoveUsageRecordId?: (usageRecordId: number | null) => void;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  className?: string;
}

export function UsageRecordDataList({
  items,
  setRemoveUsageRecordId,
  onScroll,
  isLoading = false,
  className = "",
}: UsageRecordDataListProps) {
  const { user } = useUser();
  const { isMaster } = useRoles();
  const { isMobile, isCompact } = useBreakpoint();

  const canRemove = useCallback(
    (item: UsageRecord): boolean =>
      item.createdBy === user?.id ||
      isMaster,
    [user?.id, isMaster]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: UsageRecord) => {
      e.stopPropagation();
      setRemoveUsageRecordId?.(item.id);
    },
    [setRemoveUsageRecordId]
  );

  const columns = useMemo<Column<UsageRecord>[]>(
    () => [
      {
        key: "product",
        header: "Produkt",
        grow: true,
        render: (item) => (
          <span className="flex align-items-center g-05 dl-name">
            <span
              className="dl-dot"
              style={{
                backgroundColor: item.product.category?.color
                  ? `rgb(${item.product.category.color})`
                  : undefined,
                boxShadow: item.product.category?.color
                  ? `0 0 6px rgba(${item.product.category.color}, 0.5)`
                  : undefined,
              }}
            />
            <MedicalMark vatRate={item.product.vatRate} />
            {item.product.name}
          </span>
        ),
      },
      {
        key: "employee",
        header: "Pracownik",
        width: isMobile ? "3.5rem" : "9rem",
        cellClassName: "dl-dim",
        align: "center",
        render: (item) => item.employee.name,
      },
      {
        key: "quantity",
        header: "Ilość",
        width: isMobile ? "1.75rem" : "5rem",
        align: "center",
        render: (item) => <span className="tnum">{item.quantity}</span>,
      },
      {
        key: "usageDate",
        header: "Data",
        width: isMobile ? "4rem" : "7rem",
        align: "center",
        cellClassName: "dl-dim",
        render: (item) => formatDate(item.usageDate),
      },
      {
        key: "usageReason",
        header: "Powód",
        width: isCompact ? "2rem" : isMobile ? "4.5rem" : "9rem",
        align: "center",
        render: (item) => {
          const outOfDate = item.usageReason === UsageReason.OUT_OF_DATE;

          if (isCompact) {
            return (
              <span
                className="flex align-items-center justify-center"
                title={getUsageReasonDisplay(item.usageReason)}
              >
                {outOfDate ? (
                  <CalendarX2 className="dl-icon-1 c-red" />
                ) : (
                  <Webhook className="dl-icon-1 c-yel" />
                )}
              </span>
            );
          }

          return (
            <span className={`ur-tag ${outOfDate ? "date" : "usage"}`}>
              {getUsageReasonDisplay(item.usageReason)}
            </span>
          );
        },
      },
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "2rem" : "4rem",
        align: "center",
        render: (item) =>
          canRemove(item) ? (
            <ActionButton
              iconTitle={"Usuń Zużycie Produktu"}
              onClick={(e) => handleOnClickRemove(e, item)}
              disableText={true}
              disableBorder={true}
            >
              <Trash2 className="dl-icon c-red" />
            </ActionButton>
          ) : null,
      },
    ],
    [canRemove, handleOnClickRemove, isMobile, isCompact]
  );

  return (
    <DataList<UsageRecord>
      columns={columns}
      items={items}
      getRowKey={(item) => item.id}
      onScroll={onScroll}
      isLoading={isLoading}
      hideHeader={isMobile}
      emptyText="Brak zużyć produktów"
      className={className}
    />
  );
}

export default UsageRecordDataList;
