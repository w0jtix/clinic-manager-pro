import { useState } from "react";
import { TriangleAlert } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import StepUpField from "./StepUpField";

export interface DisableGateModalProps {
  mfaEnabled: boolean;
  onConfirm: (secret: string, clear: () => void) => void;
  onClose: () => void;
}

export function DisableGateModal({ mfaEnabled, onConfirm, onClose }: DisableGateModalProps) {
  const [secret, setSecret] = useState<string>("");
  const submit = () => onConfirm(secret, () => setSecret(""));

  return (
    <Modal
      title="Na pewno?"
      onClose={onClose}
      size="sm"
      footer={<ActionButton text="Wyłącz" className="modal-button" onClick={submit} />}
    >
      <div className="remove-modal-body flex-column align-items-center text-align-center g-075">
        <TriangleAlert className="dl-icon-1 c-red remove-modal-icon" />
        <p className="remove-modal-warning m-0" style={{ whiteSpace: "pre-line" }}>
          {"Wyłączyć bramkę dostępu?\nPracownicy znów będą mogli logować się z każdego urządzenia i o każdej porze. Do chwilowego otwarcia lepiej użyć awaryjnego wstrzymania."}
        </p>
        <StepUpField mfaEnabled={mfaEnabled} value={secret} onChange={setSecret} onSubmit={submit} stacked={true} autoFocus={true} className="ag-stepup-modal" />
      </div>
    </Modal>
  );
}

export default DisableGateModal;
