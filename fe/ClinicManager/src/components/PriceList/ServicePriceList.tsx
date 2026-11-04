import { useMemo } from "react";
import { CornerDownRight } from "lucide-react";
import DataList, { Column } from "../DataList";
import { BaseService, ServiceVariant } from "../../models/service";
import { useBreakpoint } from "../../hooks/useBreakpoint";

type ServiceRow =
  | { kind: "service"; service: BaseService }
  | { kind: "variant"; service: BaseService; variant: ServiceVariant };

export interface ServicePriceListProps {
  items: BaseService[];
  isLoading?: boolean;
  className?: string;
}

export function ServicePriceList({
  items,
  isLoading = false,
  className = "",
}: ServicePriceListProps) {
  const { isMobile } = useBreakpoint();

  const rows = useMemo<ServiceRow[]>(() => {
    const flat: ServiceRow[] = [];
    items.forEach((service) => {
      flat.push({ kind: "service", service });
      (service.variants ?? [])
        .filter((v) => !v.isDeleted)
        .forEach((variant) =>
          flat.push({ kind: "variant", service, variant })
        );
    });
    return flat;
  }, [items]);

  const columns: Column<ServiceRow>[] = [
    {
      key: "name",
      header: "Nazwa",
      grow: true,
      render: (row) =>
        row.kind === "service" ? (
          <span className="dl-name flex align-items-center g-05">
            <span
              className="dl-dot"
              style={{
                backgroundColor: row.service.category?.color
                  ? `rgb(${row.service.category.color})`
                  : "rgba(255,255,255,0.2)",
                boxShadow: row.service.category?.color
                  ? `0 0 6px rgba(${row.service.category.color}, 0.5)`
                  : undefined,
              }}
            />
            {row.service.name}
          </span>
        ) : (
          <span className="pl-variant-name flex align-items-center g-05">
            <CornerDownRight
              className="dl-icon-1 pl-variant-arrow"
              style={{
                color: row.service.category?.color
                  ? `rgb(${row.service.category.color})`
                  : undefined,
              }}
            />
            {row.variant.name}
          </span>
        ),
    },
    {
      key: "price",
      header: "Cena",
      width: "6rem",
      align: "end",
      render: (row) =>
        row.kind === "service" ? (
          <span className="sv-price">{row.service.price} zł</span>
        ) : (
          <span className="pl-variant-price">{row.variant.price} zł</span>
        ),
    },
  ];

  return (
    <DataList<ServiceRow>
      columns={columns}
      items={rows}
      getRowKey={(row) =>
        row.kind === "service"
          ? `s-${row.service.id}`
          : `v-${row.service.id}-${row.variant.id}`
      }
      rowClassName={(row) => (row.kind === "variant" ? "pl-variant-row" : "")}
      isLoading={isLoading}
      hideHeader={isMobile}
      emptyText="Brak usług"
      className={className}
    />
  );
}

export default ServicePriceList;
