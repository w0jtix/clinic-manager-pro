import { useCallback, useEffect, useState } from "react";
import { TriangleAlert } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { InventoryReport } from "../../models/inventory_report";
import InventoryReportService from "../../services/InventoryReportService";
import { getSupplyChange } from "../../utils/inventoryReportUtils";
import { useRoles } from "../../hooks/useRoles";
import { MedicalMark } from "../ProductVatTag";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface InventoryReportPreviewModalProps {
  inventoryReportId: number;
  onClose: () => void;
  onApproved?: () => void;
  className?: string;
}

export function InventoryReportPreviewModal({
  inventoryReportId,
  onClose,
  onApproved,
  className = "",
}: InventoryReportPreviewModalProps) {
  const [inventoryReport, setInventoryReport] = useState<InventoryReport | null>(
    null
  );
  const [isConfirming, setIsConfirming] = useState<boolean>(false);
  const { isNarrow } = useBreakpoint();
  const { isMaster } = useRoles();
  const { showAlert } = useAlert();

  const canApprove = isMaster && inventoryReport != null && !inventoryReport.approved;

  const fetchInventoryReport = useCallback(
    (id: number) => {
      InventoryReportService.getInventoryReportById(id)
        .then((data) => setInventoryReport(data))
        .catch((error) => {
          console.error("Error fetching Inventory Report!", error);
          showAlert("Błąd podczas pobierania Raportu!", AlertType.ERROR);
        });
    },
    [showAlert]
  );

  useEffect(() => {
    fetchInventoryReport(inventoryReportId);
  }, [inventoryReportId, fetchInventoryReport]);

  const handleApprove = useCallback(() => {
    InventoryReportService.approveReport(inventoryReportId)
      .then(() => {
        showAlert("Pomyślnie zatwierdzono Raport!", AlertType.SUCCESS);
        onApproved?.();
        onClose();
      })
      .catch((error) => {
        showAlert("Błąd podczas zatwierdzania Raportu!", AlertType.ERROR);
        console.error("Error while approving Inventory Report: ", error);
      });
  }, [inventoryReportId, showAlert, onApproved, onClose]);

  return (
    <Modal
      title={`Raport Inwentaryzacyjny #${inventoryReportId}`}
      onClose={onClose}
      size="lg"
      height="auto"
      bodyClassName="nested"
      className={className}
      footer={
        canApprove ? (
          isConfirming ? (
            <div className="ir-confirm flex align-items-center justify-end width-max g-075">
              <TriangleAlert className="dl-icon-1 c-red" />
              <span className="ir-confirm-text f-1">
                Raportu nie będzie można edytować ani usunąć.
              </span>
              <ActionButton
                text={"Anuluj"}
                className="modal-button"
                onClick={() => setIsConfirming(false)}
              />
              <ActionButton
                text={"Tak, zatwierdź"}
                className="modal-button confirm"
                onClick={handleApprove}
              />
            </div>
          ) : (
            <ActionButton
              text={"Zatwierdź Raport"}
              className="modal-button confirm"
              onClick={() => setIsConfirming(true)}
            />
          )
        ) : undefined
      }
    >
      <div className="ur-list scrollbar flex-column f-1 min-height-0">
        {(inventoryReport?.items ?? []).map((item) => {
          const change = getSupplyChange(item);
          return (
            <div
              key={item.product.id}
              className={`ur-item ir-item width-max ${
                isNarrow ? "flex-column g-05" : "flex align-items-center g-075"
              }`}
            >
              {isNarrow ? (
                <>
                  <div className="flex align-items-center width-max g-05 mw-0">
                    <span
                      className="dl-dot"
                      style={{
                        backgroundColor: item.product.category?.color
                          ? `rgb(${item.product.category.color})`
                          : undefined,
                        boxShadow: item.product.category?.color
                          ? `0 0 6px rgba(${item.product.category.color}, 0.5)`
                          : undefined,
                      }}
                    />
                    <MedicalMark vatRate={item.product.vatRate} />
                    <span className="ur-name">{item.product.name}</span>
                    <span className="ur-brand f-1">
                      {item.product.brand.name}
                    </span>
                  </div>
                  <div className="ir-item-meta flex align-items-center">
                    <div className="flex align-items-center g-05">
                      <span className="ir-label">Było:</span>
                      <span className="ir-value">{item.supplyBefore}</span>
                    </div>
                    <div className="flex align-items-center g-05">
                      <span className="ir-label">Jest:</span>
                      <span className="ir-value">{item.supplyAfter}</span>
                    </div>
                    <span className={`ir-change ${change.className}`}>
                      {change.label}
                    </span>
                  </div>
                </>
              ) : (
                <>
                  <span
                    className="dl-dot"
                    style={{
                      backgroundColor: item.product.category?.color
                        ? `rgb(${item.product.category.color})`
                        : undefined,
                      boxShadow: item.product.category?.color
                        ? `0 0 6px rgba(${item.product.category.color}, 0.5)`
                        : undefined,
                    }}
                  />
                  <MedicalMark vatRate={item.product.vatRate} />
                  <span className="ur-name">{item.product.name}</span>
                  <span className="ur-brand f-1">
                    {item.product.brand.name}
                  </span>
                  <span className="ir-label">Było:</span>
                  <span className="ir-value">{item.supplyBefore}</span>
                  <span className="ir-label">Jest:</span>
                  <span className="ir-value">{item.supplyAfter}</span>
                  <span className={`ir-change ${change.className}`}>
                    {change.label}
                  </span>
                </>
              )}
            </div>
          );
        })}
      </div>
    </Modal>
  );
}

export default InventoryReportPreviewModal;
