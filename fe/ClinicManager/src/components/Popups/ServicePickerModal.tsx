import React, { useCallback, useState } from "react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import ServiceVariantsModal from "./ServiceVariantsModal";
import { BaseService, ServiceVariant } from "../../models/service";

export interface ServicePick {
  service: BaseService;
  variant: ServiceVariant | null;
}

export interface ServicePickerModalProps {
  services: BaseService[];
  filters?: React.ReactNode;
  onConfirm: (picks: ServicePick[]) => void;
  onClose: () => void;
  className?: string;
}

export function ServicePickerModal({
  services,
  filters,
  onConfirm,
  onClose,
  className = "",
}: ServicePickerModalProps) {
  const [picks, setPicks] = useState<ServicePick[]>([]);
  const [variantFor, setVariantFor] = useState<BaseService | null>(null);

  const findPick = useCallback(
    (service: BaseService): ServicePick | undefined =>
      picks.find((p) => p.service.id === service.id),
    [picks]
  );

  const hasVariants = (service: BaseService): boolean =>
    (service.variants ?? []).filter((v) => !v.isDeleted).length > 0;

  const handleToggle = useCallback(
    (service: BaseService) => {
      const picked = picks.some((p) => p.service.id === service.id);
      if (picked) {
        setPicks((prev) => prev.filter((p) => p.service.id !== service.id));
        return;
      }
      setPicks((prev) => [...prev, { service, variant: null }]);
      if (hasVariants(service)) setVariantFor(service);
    },
    [picks]
  );

  const handleVariantSelect = useCallback(
    (variant: ServiceVariant | null) => {
      if (!variantFor) return;
      setPicks((prev) =>
        prev.map((p) =>
          p.service.id === variantFor.id ? { ...p, variant } : p
        )
      );
      setVariantFor(null);
    },
    [variantFor]
  );

  const columns: Column<BaseService>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (service) => {
        const pick = findPick(service);
        return (
          <span className="flex align-items-center g-05 mw-0">
            <span
              className="dl-dot"
              style={{
                backgroundColor: service.category?.color
                  ? `rgb(${service.category.color})`
                  : undefined,
                boxShadow: service.category?.color
                  ? `0 0 6px rgba(${service.category.color}, 0.5)`
                  : undefined,
              }}
            />
            <span className={`dl-name ${pick ? "pc-y" : ""}`}>
              {service.name}
            </span>
            {pick && hasVariants(service) && (
              <span
                className="sp-variant pointer nowrap"
                title="Zmień wariant"
                onClick={(e) => {
                  e.stopPropagation();
                  setVariantFor(service);
                }}
              >
                {pick.variant ? pick.variant.name : "podstawowy"}
              </span>
            )}
          </span>
        );
      },
    },
    {
      key: "Cena",
      width: "4.5rem",
      align: "end",
      render: (service) => {
        const pick = findPick(service);
        const price = pick?.variant ? pick.variant.price : service.price;
        return <span className={`sv-price nowrap ${pick ? "pc-y" : ""}`}>{price} zł</span>;
      },
    },
  ];

  return (
    <>
      <Modal
        title={`Wybierz Usługi (${picks.length})`}
        onClose={onClose}
        size="md"
        height="tall"
        bodyClassName="nested"
        confirmText={picks.length > 0 ? `Zatwierdź (${picks.length})` : "Zatwierdź"}
        onConfirm={() => {
          onConfirm(picks);
          onClose();
        }}
        className={className}
      >
        {filters && (
          <section className="sp-filters flex-column width-max g-05">
            {filters}
          </section>
        )}
        <DataList<BaseService>
          columns={columns}
          items={services}
          getRowKey={(service) => service.id}
          onRowClick={handleToggle}
          hideHeader={true}
          emptyText="Brak usług"
          className="sp-list"
        />
      </Modal>

      {variantFor && (
        <ServiceVariantsModal
          pendingService={variantFor}
          initialVariant={findPick(variantFor)?.variant}
          requireConfirm={true}
          onSelect={handleVariantSelect}
          onClose={() => setVariantFor(null)}
        />
      )}
    </>
  );
}

export default ServicePickerModal;
