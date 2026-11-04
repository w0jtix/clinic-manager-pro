import React, { useCallback, useMemo } from "react";
import { SquarePen, Trash2 } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { Discount } from "../../models/visit";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRoles } from "../../hooks/useRoles";

export interface DiscountDataListProps {
  items: Discount[];
  setEditDiscountId?: (discountId: number | string | null) => void;
  setRemoveDiscountId?: (discountId: number | string | null) => void;
  isLoading?: boolean;
  className?: string;
}

export function DiscountDataList({
  items,
  setEditDiscountId,
  setRemoveDiscountId,
  isLoading = false,
  className = "",
}: DiscountDataListProps) {
  const { isMaster } = useRoles();
  const { isMobile } = useBreakpoint();

  const handleOnClickEdit = useCallback(
    (e: React.MouseEvent, item: Discount) => {
      e.stopPropagation();
      setEditDiscountId?.(item.id);
    },
    [setEditDiscountId]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: Discount) => {
      e.stopPropagation();
      setRemoveDiscountId?.(item.id);
    },
    [setRemoveDiscountId]
  );

  const columns = useMemo<Column<Discount>[]>(
    () => [
      {
        key: "name",
        header: "Nazwa",
        grow: true,
        render: (item) => <span className="dl-name">{item.name}</span>,
      },
      {
        key: "percentageValue",
        header: "Rabat",
        width: isMobile ? "2.5rem" : "6rem",
        align: "center",
        render: (item) => (
          <span className="dc-value">{item.percentageValue}%</span>
        ),
      },
      {
        key: "clientCount",
        header: "Klienci",
        width: isMobile ? "2rem" : "6rem",
        align: "center",
        render: (item) => <span className="dl-dim">{item.clientCount}</span>,
      },
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "1.9rem" : "6rem",
        align: "center",
        render: (item) =>
          isMobile ? (
            <div className="flex align-items-center justify-center">
              <RowActionMenu
                items={[
                  {
                    label: "Edytuj",
                    icon: <SquarePen className="dl-icon c-blue" />,
                    onClick: () => setEditDiscountId?.(item.id),
                  },
                  ...(isMaster
                    ? [
                        {
                          label: "Usuń",
                          icon: <Trash2 className="dl-icon c-red" />,
                          onClick: () => setRemoveDiscountId?.(item.id),
                          danger: true,
                        },
                      ]
                    : []),
                ]}
              />
            </div>
          ) : (
          <div className="flex align-items-center justify-center g-05">
            <ActionButton
              iconTitle={"Edytuj Rabat"}
              onClick={(e) => handleOnClickEdit(e, item)}
              disableText={true}
              disableBorder={true}
            >
              <SquarePen className="dl-icon c-blue" />
            </ActionButton>
            {isMaster && (
              <ActionButton
                iconTitle={"Usuń Rabat"}
                onClick={(e) => handleOnClickRemove(e, item)}
                disableText={true}
                disableBorder={true}
              >
                <Trash2 className="dl-icon c-red" />
              </ActionButton>
            )}
          </div>
          ),
      },
    ],
    [
      isMaster,
      isMobile,
      handleOnClickEdit,
      handleOnClickRemove,
      setEditDiscountId,
      setRemoveDiscountId,
    ]
  );

  return (
    <DataList<Discount>
      columns={columns}
      items={items}
      getRowKey={(item) => item.id}
      isLoading={isLoading}
      hideHeader={isMobile}
      emptyText="Brak rabatów"
      className={className}
    />
  );
}

export default DiscountDataList;
