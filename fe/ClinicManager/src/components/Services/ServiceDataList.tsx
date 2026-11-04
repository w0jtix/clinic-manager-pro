import React, { useCallback, useMemo } from "react";
import { SquarePen, Trash2, GripVertical } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { BaseService } from "../../models/service";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface ServiceDataListProps {
  items: BaseService[];
  isCompany?: boolean;
  setRemoveServiceId?: (serviceId: number | null) => void;
  setEditServiceId?: (serviceId: number | null) => void;
  onRowClick?: (service: BaseService) => void;
  reorderable?: boolean;
  onReorder?: (fromIndex: number, toIndex: number) => void;
  isLoading?: boolean;
  className?: string;
}

export function ServiceDataList({
  items,
  isCompany = false,
  setRemoveServiceId,
  setEditServiceId,
  onRowClick,
  reorderable = false,
  onReorder,
  isLoading = false,
  className = "",
}: ServiceDataListProps) {
  const { isMobile } = useBreakpoint();
  const canReorder = reorderable && !isMobile;

  const handleOnClickEdit = useCallback(
    (e: React.MouseEvent, item: BaseService) => {
      e.stopPropagation();
      setEditServiceId?.(item.id);
    },
    [setEditServiceId]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: BaseService) => {
      e.stopPropagation();
      setRemoveServiceId?.(item.id);
    },
    [setRemoveServiceId]
  );

  const columns = useMemo<Column<BaseService>[]>(() => {
    const base: Column<BaseService>[] = [
      ...(canReorder
        ? [
            {
              key: "handle",
              header: "",
              width: "2rem",
              align: "center" as const,
              render: () => (
                <span className="dl-drag-handle flex align-items-center justify-center" data-row-click-ignore>
                  <GripVertical className="dl-icon sv-drag-handle" />
                </span>
              ),
            },
          ]
        : []),
      {
        key: "name",
        header: "Nazwa",
        grow: true,
        render: (item) => (
          <span className="flex align-items-center g-05 dl-name">
            <span
              className="dl-dot"
              style={{
                backgroundColor: item.category?.color
                  ? `rgb(${item.category.color})`
                  : undefined,
                boxShadow: item.category?.color
                  ? `0 0 6px rgba(${item.category.color}, 0.5)`
                  : undefined,
              }}
            />
            {item.name}
          </span>
        ),
      },
      ...(isMobile
        ? []
        : [
            {
              key: "category",
              header: "Kategoria",
              width: "10rem",
              align: "center" as const,
              render: (item: BaseService) => (
                <span className="dl-dim">{item.category?.name}</span>
              ),
            },
          ]),
      {
        key: "duration",
        header: "Czas",
        width: isMobile ? "3.25rem" : "6rem",
        align: "center",
        render: (item) => <span className="dl-dim">{item.duration} min</span>,
      },
      {
        key: "price",
        header: "Cena",
        width: isMobile ? "2.75rem" : "6rem",
        align: "center",
        render: (item) => <span className="sv-price">{item.price} zł</span>,
      },
    ];

    if (!isCompany) return base;

    return [
      ...base,
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
                    onClick: () => setEditServiceId?.(item.id),
                  },
                  {
                    label: "Usuń",
                    icon: <Trash2 className="dl-icon c-red" />,
                    onClick: () => setRemoveServiceId?.(item.id),
                    danger: true,
                  },
                ]}
              />
            </div>
          ) : (
          <div className="flex align-items-center justify-center g-05">
            <ActionButton
              iconTitle={"Edytuj Usługę"}
              onClick={(e) => handleOnClickEdit(e, item)}
              disableText={true}
              disableBorder={true}
            >
              <SquarePen className="dl-icon c-blue" />
            </ActionButton>
            <ActionButton
              iconTitle={"Usuń Usługę"}
              onClick={(e) => handleOnClickRemove(e, item)}
              disableText={true}
              disableBorder={true}
            >
              <Trash2 className="dl-icon c-red" />
            </ActionButton>
          </div>
          ),
      },
    ];
  }, [
    isCompany,
    canReorder,
    isMobile,
    handleOnClickEdit,
    handleOnClickRemove,
    setEditServiceId,
    setRemoveServiceId,
  ]);

  return (
    <DataList<BaseService>
      columns={columns}
      items={items}
      getRowKey={(item) => item.id}
      onRowClick={onRowClick}
      isRowDraggable={() => canReorder}
      onReorder={canReorder ? onReorder : undefined}
      isLoading={isLoading}
      hideHeader={isMobile}
      emptyText="Brak usług"
      className={`${className} ${canReorder ? "reorderable" : ""}`}
    />
  );
}

export default ServiceDataList;
