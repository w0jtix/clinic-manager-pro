import React, { useCallback, useMemo, useState } from "react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import { MedicalMark } from "../ProductVatTag";
import { Product } from "../../models/product";

export interface ProductPickerModalProps {
  products: Product[];
  filters?: React.ReactNode;
  isLoading?: boolean;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  onConfirm: (products: Product[]) => void;
  onClose: () => void;
  className?: string;
}

export function ProductPickerModal({
  products,
  filters,
  isLoading = false,
  onScroll,
  onConfirm,
  onClose,
  className = "",
}: ProductPickerModalProps) {
  const [picked, setPicked] = useState<Product[]>([]);

  const pickedIds = useMemo(
    () => new Set(picked.map((p) => p.id)),
    [picked]
  );

  const handleToggle = useCallback((product: Product) => {
    setPicked((prev) =>
      prev.some((p) => p.id === product.id)
        ? prev.filter((p) => p.id !== product.id)
        : [...prev, product]
    );
  }, []);

  const columns: Column<Product>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (product) => (
        <span className="flex align-items-center g-05 mw-0">
          <span
            className="dl-dot"
            style={{
              backgroundColor: product.category?.color
                ? `rgb(${product.category.color})`
                : "rgba(255,255,255,0.2)",
              boxShadow: product.category?.color
                ? `0 0 6px rgba(${product.category.color}, 0.5)`
                : undefined,
            }}
          />
          <span className="vc-name-stack flex-column mw-0">
            <span className="flex align-items-center g-05 mw-0">
              <MedicalMark vatRate={product.vatRate} />
              <span className={`dl-name ${pickedIds.has(product.id) ? "pc-y" : ""}`}>
                {product.name}
              </span>
            </span>
            <span className={`vc-name-meta dl-dim tnum ${pickedIds.has(product.id) ? "pc-y" : ""}`}>
              Stan: {product.supply}
            </span>
          </span>
        </span>
      ),
    },
    {
      key: "Cena",
      width: "4.5rem",
      align: "end",
      render: (product) => (
        <span
          className={`sv-price nowrap ${
            pickedIds.has(product.id) ? "pc-y" : ""
          }`}
        >
          {product.sellingPrice ?? 0} zł
        </span>
      ),
    },
  ];

  return (
    <Modal
      title={`Wybierz Produkty (${picked.length})`}
      onClose={onClose}
      size="md"
      height="tall"
      bodyClassName="nested"
      confirmText={picked.length > 0 ? `Zatwierdź (${picked.length})` : "Zatwierdź"}
      onConfirm={() => {
        onConfirm(picked);
        onClose();
      }}
      className={className}
    >
      {filters && (
        <section className="sp-filters flex-column width-max g-05">
          {filters}
        </section>
      )}
      <DataList<Product>
        columns={columns}
        items={products}
        getRowKey={(product, index) => `${product.id}-${index}`}
        onRowClick={handleToggle}
        onScroll={onScroll}
        isLoading={isLoading}
        hideHeader={true}
        emptyText="Brak produktów"
        className="sp-list"
      />
    </Modal>
  );
}

export default ProductPickerModal;
