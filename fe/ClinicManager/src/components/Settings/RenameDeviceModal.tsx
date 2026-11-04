import { useRef, useState } from "react";
import Modal from "../Modal";
import TextInput from "../TextInput";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { ClinicDevice } from "../../models/device";
import DeviceService from "../../services/DeviceService";
import { apiErrorMessage } from "../../utils/apiError";

export interface RenameDeviceModalProps {
  device: ClinicDevice;
  onSaved: () => void;
  onClose: () => void;
}

export function RenameDeviceModal({ device, onSaved, onClose }: RenameDeviceModalProps) {
  const { showAlert } = useAlert();
  const [name, setName] = useState<string>(device.name);
  const busyRef = useRef<boolean>(false);

  const handleSave = async () => {
    if (busyRef.current) return;
    const trimmed = name.trim();
    if (!trimmed) {
      showAlert("Podaj nazwę urządzenia.", AlertType.ERROR);
      return;
    }
    if (trimmed === device.name) {
      onClose();
      return;
    }
    busyRef.current = true;
    try {
      await DeviceService.renameDevice(device.id, trimmed);
      showAlert("Zmieniono nazwę urządzenia.", AlertType.SUCCESS);
      onSaved();
      onClose();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się zmienić nazwy.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
    }
  };

  return (
    <Modal title="Zmień nazwę urządzenia" onClose={onClose} size="sm" onConfirm={handleSave} dirtyValue={name}>
      <div className="flex-column g-075">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwa:</span>
          <TextInput
            className="in-full"
            value={name}
            maxLength={100}
            onSelect={(value) => {
              if (typeof value === "string") setName(value);
            }}
          />
        </div>
      </div>
    </Modal>
  );
}

export default RenameDeviceModal;
