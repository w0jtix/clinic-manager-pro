import { useEffect, useState } from "react";
import { TriangleAlert } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { ClinicDevice } from "../../models/device";
import DeviceService from "../../services/DeviceService";
import { formatRelativeDateTime } from "../../utils/dateUtils";

export interface EnableGateModalProps {
  onConfirm: () => void;
  onClose: () => void;
}

export function EnableGateModal({ onConfirm, onClose }: EnableGateModalProps) {
  const [devices, setDevices] = useState<ClinicDevice[] | null>(null);

  useEffect(() => {
    DeviceService.getDevices(false)
      .then((list) => setDevices(list.filter((device) => device.status === "ACTIVE")))
      .catch(() => setDevices([]));
  }, []);

  const empty = devices !== null && devices.length === 0;

  return (
    <Modal
      title="Włączyć bramkę dostępu?"
      onClose={onClose}
      size="md"
      footer={
        empty ? (
          <ActionButton text="Zamknij" disableImg={true} onClick={onClose} className="modal-button" />
        ) : (
          <ActionButton
            text="Włącz"
            disableImg={true}
            onClick={onConfirm}
            disabled={devices === null}
            className="modal-button"
          />
        )
      }
    >
      {devices === null ? null : empty ? (
        <div className="remove-modal-body flex-column align-items-center text-align-center g-075">
          <TriangleAlert className="dl-icon-1 c-red remove-modal-icon" />
          <p className="remove-modal-warning m-0" style={{ whiteSpace: "pre-line" }}>
            Nie można włączyć bramki - brak aktywnych urządzeń gabinetu.
            {"\n"}Najpierw zarejestruj urządzenie.
          </p>
        </div>
      ) : (
        <div className="flex-column g-1">
          <p className="dv-confirm-text m-0">
            Pracownicy zalogują się tylko z tych urządzeń:
          </p>
          <div className="ag-devices flex-column">
            {devices.map((device) => (
              <div key={device.id} className="ag-dev-row">
                <span className="ag-dev-name">{device.name}</span>
                <span className="ag-dev-dim">
                  {device.lastSeenAt ? formatRelativeDateTime(device.lastSeenAt) : "nigdy"}
                  {device.lastSeenUsername ? ` · ${device.lastSeenUsername}` : ""}
                </span>
                <span className="ag-dev-dim">{device.label ?? "-"}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </Modal>
  );
}

export default EnableGateModal;
