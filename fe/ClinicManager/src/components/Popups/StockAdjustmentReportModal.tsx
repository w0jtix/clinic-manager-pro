import { useCallback, useState, useEffect } from "react";
import { TriangleAlert } from "lucide-react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { validateInventoryReportForm } from "../../utils/validators";
import InventoryReportForm from "../Products/InventoryReportForm";
import {
  InventoryReportItem,
  NewInventoryReport,
  NewInventoryReportItem,
} from "../../models/inventory_report";
import InventoryReportService from "../../services/InventoryReportService";
import { useRoles } from "../../hooks/useRoles";
import { Action } from "../../models/action";

export interface StockAdjustmentReportModalProps {
  onClose: () => void;
  onSuccess?: () => void;
  inventoryReportId?: number;
  className?: string;
}

export function StockAdjustmentReportModal({
  onClose,
  onSuccess,
  inventoryReportId,
  className = "",
}: StockAdjustmentReportModalProps) {
  const [inventoryReportItems, setInventoryReportItems] = useState<
    InventoryReportItem[] | NewInventoryReportItem[]
  >([]);
  const [fetchedItems, setFetchedItems] = useState<InventoryReportItem[] | null>(
    null
  );
  const [hasUnapproved, setHasUnapproved] = useState(false);
  const { showAlert } = useAlert();
  const { isMaster } = useRoles();

  const action = inventoryReportId ? Action.EDIT : Action.CREATE;
  const isBlocked = hasUnapproved && action === Action.CREATE;

  const checkApprovalStatus = useCallback(() => {
    InventoryReportService.areAllApproved()
      .then((allApproved) => setHasUnapproved(!allApproved))
      .catch((error) => {
        showAlert("Błąd!", AlertType.ERROR);
        console.error("Error checking approval status:", error);
      });
  }, []);

  const fetchInventoryReport = useCallback(
    (id: number) => {
      InventoryReportService.getInventoryReportById(id)
        .then((data) => {
          setInventoryReportItems(data.items);
          setFetchedItems(data.items);
        })
        .catch((error) => {
          console.error("Error fetching Inventory Report!", error);
          showAlert("Błąd podczas pobierania Raportu!", AlertType.ERROR);
        });
    },
    [showAlert]
  );

  useEffect(() => {
    if (action === Action.CREATE) {
      checkApprovalStatus();
    }
  }, [action, checkApprovalStatus]);

  useEffect(() => {
    if (inventoryReportId) {
      fetchInventoryReport(inventoryReportId);
    }
  }, [inventoryReportId, fetchInventoryReport]);

  const handleInventoryReportAction = useCallback(async () => {
    const validationError = validateInventoryReportForm(
      inventoryReportItems,
      action,
      fetchedItems
    );
    if (validationError) {
      showAlert(validationError, AlertType.ERROR);
      return;
    }
    const inventoryReport: NewInventoryReport = {
      items: inventoryReportItems as NewInventoryReportItem[],
    };

    try {
      if (action === Action.CREATE) {
        await InventoryReportService.createInventoryReport(inventoryReport);
        showAlert(`Utworzono Raport Stanu Magazynowego!`, AlertType.SUCCESS);
      } else {
        await InventoryReportService.updateInventoryReport(
          inventoryReportId!,
          inventoryReport
        );
        showAlert(`Zaktualizowano Raport Stanu Magazynowego!`, AlertType.SUCCESS);
      }
      onSuccess?.();
      onClose();
    } catch (error: any) {
      console.error(
        `Error ${action === Action.CREATE ? "creating" : "updating"} Inventory Report:`,
        error
      );
      const message =
        error?.response?.status === 409 && error?.response?.data
          ? error.response.data
          : `Błąd podczas ${
              action === Action.CREATE ? "tworzenia" : "aktualizacji"
            } Raportu Stanu Magazynowego!`;
      showAlert(message, AlertType.ERROR);
    }
  }, [
    inventoryReportItems,
    action,
    fetchedItems,
    inventoryReportId,
    showAlert,
    onSuccess,
    onClose,
  ]);

  return (
    <Modal
      dirtyValue={inventoryReportItems}
      title={
        action === Action.CREATE
          ? "Raport Spisu Magazynowego"
          : "Edytuj Raport Spisu Magazynowego"
      }
      onClose={onClose}
      size="2xl"
      height={isBlocked ? "auto" : "tall"}
      bodyClassName={isBlocked ? "" : "nested"}
      onConfirm={isBlocked ? undefined : handleInventoryReportAction}
      className={className}
    >
      {isBlocked ? (
        <div className="ir-blocked flex-column align-items-center text-align-center g-075">
          <TriangleAlert className="remove-modal-icon c-red" />
          <p className="remove-modal-warning m-0">
            {isMaster
              ? "W bazie danych są niezatwierdzone Raporty. By utworzyć kolejny Raport zatwierdź poprzednie!"
              : "Poprzedni Raport oczekuje na weryfikację. Kolejny Raport będzie można utworzyć po jego zatwierdzeniu."}
          </p>
        </div>
      ) : (
        <InventoryReportForm
          inventoryReportItems={inventoryReportItems}
          setInventoryReportItems={setInventoryReportItems}
        />
      )}
    </Modal>
  );
}

export default StockAdjustmentReportModal;
