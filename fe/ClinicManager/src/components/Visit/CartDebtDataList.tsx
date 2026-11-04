import { useState } from "react";
import { Circle, CircleCheck, Eye } from "lucide-react";
import DataList, { Column } from "../DataList";
import { ClientDebt, DebtType } from "../../models/debt";
import VisitPreviewModal from "../Popups/VisitPreviewModal";

export interface CartDebtDataListProps {
  items: ClientDebt[];
  selectedIds: number[];
  onSelect: (debt: ClientDebt) => void;
  className?: string;
}

export function CartDebtDataList({
  items,
  selectedIds,
  onSelect,
  className = "",
}: CartDebtDataListProps) {
  const [previewVisitId, setPreviewVisitId] = useState<string | number | null>(
    null
  );

  const columns: Column<ClientDebt>[] = [
    {
      key: "select",
      width: "2.5rem",
      align: "center",
      render: (item) =>
        selectedIds.includes(item.id) ? (
          <CircleCheck className="dl-icon-1 c-green" />
        ) : (
          <Circle className="dl-icon-1 vc-off" />
        ),
    },
    {
      key: "Przyczyna",
      grow: true,
      render: (item) =>
        item.type === DebtType.ABSENCE_FEE ? (
          <span className="rv-status absence">NIEOBECNOŚĆ</span>
        ) : (
          <span className="rv-status onhold">ZALEGŁOŚĆ</span>
        ),
    },
    {
      key: "Źródło",
      width: "9rem",
      align: "center",
      render: (item) =>
        item.sourceVisit != null ? (
          <span
            className="cd-source pointer flex align-items-center justify-center g-05"
            title="Podgląd Wizyty źródła"
            onClick={(e) => {
              e.stopPropagation();
              setPreviewVisitId(item.sourceVisit!.id);
            }}
          >
            <Eye className="dl-icon-1" />
            Źródło długu
          </span>
        ) : (
          <span className="dl-dim">Brak źródła</span>
        ),
    },
    {
      key: "Wartość",
      width: "6rem",
      align: "end",
      render: (item) => <span className="vc-price">{item.value} zł</span>,
    },
  ];

  return (
    <>
      <DataList<ClientDebt>
        columns={columns}
        items={items}
        getRowKey={(item) => item.id}
        onRowClick={onSelect}
        rowClassName={(item) =>
          selectedIds.includes(item.id) ? "vm-selected" : ""
        }
        hideHeader={true}
        emptyText="Brak długów"
        className={`vm-cart-list ${className}`}
      />
      {previewVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
    </>
  );
}

export default CartDebtDataList;
