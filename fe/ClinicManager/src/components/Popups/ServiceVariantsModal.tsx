import { useState } from "react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import { BaseService, ServiceVariant } from "../../models/service";

type VariantRow = ServiceVariant & { isBaseVariant?: boolean };

const rowKey = (row: VariantRow): string =>
  row.isBaseVariant ? "base" : `v-${row.id}`;

export interface ServiceVariantsModalProps {
  onClose: () => void;
  pendingService: BaseService;
  onSelect: (variant: ServiceVariant | null) => void;
  initialVariant?: ServiceVariant | null;
  requireConfirm?: boolean;
  className?: string;
}

export function ServiceVariantsModal({
  onClose,
  pendingService,
  onSelect,
  initialVariant,
  requireConfirm = false,
  className = "",
}: ServiceVariantsModalProps) {
  const baseVariant: VariantRow = {
    id: pendingService.id,
    name: pendingService.name,
    price: pendingService.price,
    duration: pendingService.duration,
    isBaseVariant: true,
  };

  const allVariants: VariantRow[] = [
    baseVariant,
    ...(pendingService.variants ?? []),
  ];

  const [stagedKey, setStagedKey] = useState<string | null>(() => {
    if (initialVariant === undefined) return null;
    if (initialVariant === null) return rowKey(baseVariant);
    return rowKey(initialVariant as VariantRow);
  });

  const handleSelect = (variant: VariantRow) => {
    if (requireConfirm) {
      setStagedKey(rowKey(variant));
      return;
    }
    onSelect(variant.isBaseVariant ? null : variant);
  };

  const handleConfirm = () => {
    const staged = allVariants.find((v) => rowKey(v) === stagedKey);
    if (!staged) return;
    onSelect(staged.isBaseVariant ? null : staged);
    onClose();
  };

  const columns: Column<VariantRow>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (item) => (
        <span
          className={`dl-name sv-name flex align-items-center g-05 ${
            requireConfirm && rowKey(item) === stagedKey ? "pc-y" : ""
          }`}
        >
          {item.name}
          {item.isBaseVariant && <span className="dl-dim">(podstawowy)</span>}
        </span>
      ),
    },
    {
      key: "Czas",
      width: "3rem",
      align: "center",
      render: (item) => <span className="dl-dim">{item.duration} min</span>,
    },
    {
      key: "Cena",
      width: "5rem",
      align: "end",
      render: (item) => (
        <span
          className={`vc-price svm-price pc-y fw-bold ${
            requireConfirm && rowKey(item) === stagedKey ? "selected" : ""
          }`}
        >
          {item.price} zł
        </span>
      ),
    },
  ];

  return (
    <Modal
      title="Wybierz Wariant Usługi"
      onClose={onClose}
      size="md"
      height="auto"
      className={className}
      {...(requireConfirm
        ? {
            confirmText: "Zatwierdź",
            confirmDisabled: stagedKey === null,
            onConfirm: handleConfirm,
          }
        : {})}
    >
      <DataList<VariantRow>
        columns={columns}
        items={allVariants}
        getRowKey={(item, index) => `${item.id}-${index}`}
        onRowClick={handleSelect}
        hideHeader={true}
        emptyText="Brak wariantów"
        className="vm-cart-list"
      />
    </Modal>
  );
}

export default ServiceVariantsModal;
