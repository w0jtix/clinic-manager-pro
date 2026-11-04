import React, { useState, useEffect, useRef } from "react";
import { SquarePen, Ticket, Trash2 } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import CostInput from "../CostInput";
import { NewSaleItem } from "../../models/sale";
import ProductVatTag, { MedicalMark } from "../ProductVatTag";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface CartProductDataListProps {
  items: NewSaleItem[];
  onRemoveByIndex: (index: number) => void;
  onPriceChange: (index: number, value: number) => void;
  className?: string;
}

export function CartProductDataList({
  items,
  onRemoveByIndex,
  onPriceChange,
  className = "",
}: CartProductDataListProps) {
  const { isMobile } = useBreakpoint();
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const prevLengthRef = useRef<number>(0);

  useEffect(() => {
    const lastIndex = items.length - 1;
    if (items.length > prevLengthRef.current && !items[lastIndex]?.product) {
      setEditingIndex(lastIndex);
    }
    prevLengthRef.current = items.length;
  }, [items]);

  const columns: Column<NewSaleItem>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (item) => {
        const isVoucher = !item.product;
        const color = item.product?.category?.color;
        return (
          <span className="flex align-items-center g-05 dl-name">
            {isVoucher ? (
              <Ticket className="dl-icon-1 icon-voucher" />
            ) : (
              <span
                className="dl-dot"
                style={{
                  backgroundColor: color ? `rgb(${color})` : undefined,
                  boxShadow: color ? `0 0 6px rgba(${color}, 0.5)` : undefined,
                }}
              />
            )}
            {isVoucher ? (
              "Voucher podarunkowy"
            ) : isMobile ? (
              <span className="vc-name-stack flex-column mw-0">
                <span className="flex align-items-center g-05 mw-0">
                  <MedicalMark vatRate={item.product?.vatRate} />
                  <span className="vc-name-text">{item.product?.name}</span>
                </span>
                <span className="vc-name-meta flex align-items-center">
                  <ProductVatTag vatRate={item.product?.vatRate} />
                </span>
              </span>
            ) : (
              <>
                <MedicalMark vatRate={item.product?.vatRate} />
                <span className="vc-name-text">{item.product?.name}</span>
                <ProductVatTag vatRate={item.product?.vatRate} />
              </>
            )}
          </span>
        );
      },
    },
    {
      key: "Cena",
      width: isMobile ? "5.5rem" : "9rem",
      align: "end",
      render: (item, index) =>
        editingIndex === index ? (
          <span className="flex align-items-center justify-end g-05">
            <CostInput
              initialValue={item.price ?? 0}
              onChange={(value) => onPriceChange(index, value)}
              className="in-sm"
            />
            {!isMobile && <span className="qv-span f10">zł</span>}
          </span>
        ) : (
          <span className="vc-price nowrap">{item.price ?? 0} zł</span>
        ),
    },
    {
      key: "Edytuj",
      width: isMobile ? "1.75rem" : "3.5rem",
      align: "center",
      render: (_item, index) => (
        <ActionButton
          iconTitle={
            editingIndex === index ? "Zakończ edycję ceny" : "Edytuj cenę"
          }
          disableText={true}
          disableBorder={true}
          className="vc-edit"
          onClick={(e: React.MouseEvent) => {
            e.stopPropagation();
            setEditingIndex((prev) => (prev === index ? null : index));
          }}
        >
          <SquarePen className="dl-icon vc-edit-icon c-blue" />
        </ActionButton>
      ),
    },
    {
      key: "",
      width: isMobile ? "1.75rem" : "3rem",
      align: "center",
      render: (_item, index) => (
        <ActionButton
          iconTitle="Usuń"
          disableText={true}
          disableBorder={true}
          onClick={(e: React.MouseEvent) => {
            e.stopPropagation();
            setEditingIndex(null);
            onRemoveByIndex(index);
          }}
        >
          <Trash2 className="dl-icon c-red" />
        </ActionButton>
      ),
    },
  ];

  return (
    <DataList<NewSaleItem>
      columns={columns}
      items={items}
      getRowKey={(item, index) => `${item.product?.id ?? "voucher"}-${index}`}
      hideHeader={true}
      emptyText="Brak produktów"
      className={`vm-cart-list ${className}`}
    />
  );
}

export default CartProductDataList;
