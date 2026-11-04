import React from "react";
import { Rocket, Snowflake, Trash2 } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import { NewVisitItem, Visit } from "../../models/visit";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface CartServiceDataListProps {
  items: NewVisitItem[];
  visitPreview?: Visit | null;
  isBoost?: boolean;
  discountMode?: boolean;
  onRemoveByIndex: (index: number) => void;
  onManageBoostByIndex?: (index: number) => void;
  onFreezePrice?: (index: number) => void;
  className?: string;
}

export function CartServiceDataList({
  items,
  visitPreview,
  isBoost = false,
  discountMode = false,
  onRemoveByIndex,
  onManageBoostByIndex,
  onFreezePrice,
  className = "",
}: CartServiceDataListProps) {
  const { isMobile } = useBreakpoint();
  const previewItems = visitPreview?.items ?? null;

  const basePriceFor = (item: NewVisitItem): number =>
    item.finalPrice ?? item.serviceVariant?.price ?? item.service?.price ?? 0;

  const finalPriceFor = (index: number): number | null =>
    previewItems && previewItems.length === items.length
      ? previewItems[index]?.finalPrice ?? null
      : null;

  const columns: Column<NewVisitItem>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (item, index) => (
        <span className="flex align-items-center g-05 dl-name">
          {isBoost && onManageBoostByIndex && (
            <ActionButton
              iconTitle={
                item.boostItem ? "Usługa Boost - wyłącz" : "Usługa Boost - włącz"
              }
              disableText={true}
              disableBorder={true}
              className="vc-boost"
              onClick={(e: React.MouseEvent) => {
                e.stopPropagation();
                onManageBoostByIndex(index);
              }}
            >
              <Rocket
                className={`dl-icon ${item.boostItem ? "icon-rocket" : "vc-off"}`}
              />
            </ActionButton>
          )}
          <span
            className="dl-dot"
            style={{
              backgroundColor: item.service?.category?.color
                ? `rgb(${item.service.category.color})`
                : undefined,
              boxShadow: item.service?.category?.color
                ? `0 0 6px rgba(${item.service.category.color}, 0.5)`
                : undefined,
            }}
          />
          {isMobile && item.serviceVariant ? (
            <span className="vc-name-stack flex-column mw-0">
              <span className={`vc-name-text ${item.boostItem ? "vc-boost-name" : ""}`}>
                {item.service?.name}
              </span>
              <span className="vc-name-meta dl-dim">
                {item.serviceVariant.name}
              </span>
            </span>
          ) : (
            <span className={`vc-name-text ${item.boostItem ? "vc-boost-name" : ""}`}>
              {item.serviceVariant
                ? `${item.service?.name} - ${item.serviceVariant.name}`
                : item.service?.name}
            </span>
          )}
        </span>
      ),
    },
    {
      key: "Cena",
      width: isMobile ? "3.75rem" : "9rem",
      align: "end",
      render: (item, index) => {
        const base = basePriceFor(item);
        const frozen = item.finalPrice != null;
        const final = finalPriceFor(index);
        const showFinal = discountMode && final != null && final !== base;

        return (
          <span
            className={`flex align-items-center justify-center ${
              isMobile ? "flex-column g-025" : "g-05"
            }`}
          >
            <span
              className={`vc-price nowrap ${frozen ? "frozen" : ""} ${
                showFinal ? "struck" : ""
              }`}
            >
              {base} zł
            </span>
            {showFinal && (
              <span className="vc-price-final nowrap">{final} zł</span>
            )}
          </span>
        );
      },
    },
  ];

  if (onFreezePrice) {
    columns.push({
      key: "Cena stała",
      width: isMobile ? "1.75rem" : "3.5rem",
      align: "center",
      render: (item, index) => (
        <ActionButton
          iconTitle={
            item.finalPrice != null
              ? "Cena zamrożona - kliknij, aby rabaty znów obejmowały tę usługę"
              : "Zamroź cenę - rabaty nie obejmą tej usługi"
          }
          disableText={true}
          disableBorder={true}
          className="vc-freeze"
          onClick={(e: React.MouseEvent) => {
            e.stopPropagation();
            onFreezePrice(index);
          }}
        >
          <Snowflake
            className={
              item.finalPrice != null
                ? "dl-icon-1 vc-frozen-icon"
                : "dl-icon vc-off"
            }
          />
        </ActionButton>
      ),
    });
  }

  columns.push({
    key: "",
    width: isMobile ? "1.75rem" : "3rem",
    align: "center",
    render: (_item, index) => (
      <ActionButton
        iconTitle="Usuń Usługę"
        disableText={true}
        disableBorder={true}
        onClick={(e: React.MouseEvent) => {
          e.stopPropagation();
          onRemoveByIndex(index);
        }}
      >
        <Trash2 className="dl-icon c-red" />
      </ActionButton>
    ),
  });

  return (
    <DataList<NewVisitItem>
      columns={columns}
      items={items}
      getRowKey={(item, index) => `${item.service?.id}-${index}`}
      hideHeader={true}
      emptyText="Brak usług"
      className={`vm-cart-list ${className}`}
    />
  );
}

export default CartServiceDataList;
