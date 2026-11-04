import { useCallback, useState } from "react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { useUser } from "../User/UserProvider";
import { AlertType } from "../../models/alert";
import {
  NewUsageRecord,
  UsageRecordItem,
  UsageReason,
} from "../../models/usage-record";
import UsageRecordForm from "../Products/UsageRecordForm";
import { Employee } from "../../models/employee";
import { Product } from "../../models/product";
import UsageRecordService from "../../services/UsageRecordService";
import { validateUsageRecordsForm } from "../../utils/validators";

export interface UsageRecordModalProps {
  onClose: () => void;
  initialProduct?: Product | null;
  onCreated?: () => void;
  className?: string;
}

export function UsageRecordModal({
  onClose,
  initialProduct = null,
  onCreated,
  className = "",
}: UsageRecordModalProps) {
  const { user } = useUser();
  const [usageRecordItems, setUsageRecordItems] = useState<UsageRecordItem[]>(
    initialProduct
      ? [
          {
            product: initialProduct,
            quantity: 1,
            usageReason: UsageReason.REGULAR_USAGE,
          },
        ]
      : []
  );
  const [sharedFields, setSharedFields] = useState({
    employee: (user?.employee ?? null) as Employee | null,
    usageDate: new Date().toISOString().split("T")[0],
  });
  const [hasSupplyError, setHasSupplyError] = useState(false);
  const { showAlert } = useAlert();

  const handleCreateUsageRecord = useCallback(async () => {
    const validationError = validateUsageRecordsForm(
      usageRecordItems,
      sharedFields,
      hasSupplyError
    );
    if (validationError) {
      showAlert(validationError, AlertType.ERROR);
      return;
    }

    const usageRecords: NewUsageRecord[] = usageRecordItems.map((item) => ({
      product: item.product,
      employee: sharedFields.employee,
      usageDate: sharedFields.usageDate,
      quantity: item.quantity,
      usageReason: item.usageReason,
    }));

    try {
      await UsageRecordService.createUsageRecords(usageRecords);
      showAlert(
        `Utworzono ${usageRecords.length} rekordów zużycia`,
        AlertType.SUCCESS
      );
      onCreated?.();
      onClose();
    } catch (error) {
      console.error("Error creating usage records:", error);
      showAlert("Błąd podczas tworzenia rekordów zużycia", AlertType.ERROR);
    }
  }, [usageRecordItems, sharedFields, hasSupplyError, showAlert, onCreated, onClose]);

  return (
    <Modal
      dirtyValue={[usageRecordItems, sharedFields]}
      title="Nowe Zużycie Produktu"
      onClose={onClose}
      size="2xl"
      height="tall"
      bodyClassName="nested"
      onConfirm={handleCreateUsageRecord}
      className={className}
    >
      <UsageRecordForm
        usageRecordItems={usageRecordItems}
        setUsageRecordItems={setUsageRecordItems}
        sharedFields={sharedFields}
        setSharedFields={setSharedFields}
        hasSupplyError={hasSupplyError}
        setHasSupplyError={setHasSupplyError}
      />
    </Modal>
  );
}

export default UsageRecordModal;
