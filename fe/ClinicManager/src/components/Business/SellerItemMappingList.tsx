import { ArrowRight, Trash2 } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import { KsefSellerItemMapping } from "../../models/ksef_seller";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface SellerItemMappingListProps {
  mappings: KsefSellerItemMapping[];
  isLoading?: boolean;
  onRemove: (mapping: KsefSellerItemMapping) => void;
  className?: string;
}

export function SellerItemMappingList({
  mappings,
  isLoading = false,
  onRemove,
  className = "",
}: SellerItemMappingListProps) {
  const { isMobile } = useBreakpoint();
  const columns: Column<KsefSellerItemMapping>[] = [
    {
      key: "ksefItemName",
      header: "Pozycja z faktury",
      grow: true,
      render: (mapping) => (
        <span className="dl-dim mw-0">{mapping.ksefItemName}</span>
      ),
    },
    {
      key: "arrow",
      header: "",
      width: isMobile ? "1.25rem" : "2rem",
      align: "center",
      render: () => <ArrowRight className="dl-icon-1 dl-dim" />,
    },
    {
      key: "product",
      header: "Produkt",
      grow: true,
      cellClassName: "sd-map-product",
      headerClassName: "sd-map-product",
      render: (mapping) => (
        <span className="flex align-items-center g-05 mw-0">
          <span
            className="dl-dot"
            style={{
              backgroundColor: mapping.product?.category?.color
                ? `rgb(${mapping.product.category.color})`
                : "rgba(255,255,255,0.2)",
            }}
          />
          <span className="dl-name">{mapping.product?.name ?? "-"}</span>
        </span>
      ),
    },
    {
      key: "options",
      header: "Opcje",
      width: isMobile ? "1.9rem" : "5rem",
      align: "center",
      render: (mapping) => (
        <ActionButton
          iconTitle="Usuń przypisanie"
          onClick={(e) => {
            e.stopPropagation();
            onRemove(mapping);
          }}
          disableText={true}
          disableBorder={true}
        >
          <Trash2 className="dl-icon c-red" />
        </ActionButton>
      ),
    },
  ];

  return (
    <DataList<KsefSellerItemMapping>
      columns={columns}
      hideHeader={isMobile}
      items={mappings}
      getRowKey={(mapping) => mapping.id}
      isLoading={isLoading}
      emptyText="Brak zapamiętanych przypisań"
      className={`sd-map-list ${className}`}
    />
  );
}

export default SellerItemMappingList;
