import { useEffect, useState } from "react";
import Modal from "../Modal";
import UsageRecordDataList from "../Products/UsageRecordDataList";
import UsageRecordService from "../../services/UsageRecordService";
import { UsageRecord } from "../../models/usage-record";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";

export interface UsageRecordPreviewModalProps {
  usageRecordId: number;
  onClose: () => void;
  className?: string;
}

export function UsageRecordPreviewModal({
  usageRecordId,
  onClose,
  className = "",
}: UsageRecordPreviewModalProps) {
  const [record, setRecord] = useState<UsageRecord | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();

  useEffect(() => {
    setLoading(true);
    UsageRecordService.getUsageRecordById(usageRecordId)
      .then((data) => setRecord(data))
      .catch((error) => {
        setRecord(null);
        showAlert("Błąd pobierania zużycia!", AlertType.ERROR);
        console.error("Error fetching usage record: ", error);
      })
      .finally(() => setLoading(false));
  }, [usageRecordId]);

  return (
    <Modal title="Zużycie produktu" onClose={onClose} size="xl" className={className}>
      <UsageRecordDataList
        items={record ? [record] : []}
        isLoading={loading}
        className="urp-list"
      />
    </Modal>
  );
}

export default UsageRecordPreviewModal;
