import React from "react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { TriangleAlert } from "lucide-react";

export interface RemoveModalProps {
  onClose: () => void;
  warningText?: React.ReactNode;
  handleRemove: () => void;
  confirmText?: string;
  className?: string;
}

export function RemoveModal({
  onClose,
  warningText = "",
  handleRemove,
  confirmText = "Usuń",
  className = "",
}: RemoveModalProps) {
  return (
    <Modal
      title="Na pewno?"
      onClose={onClose}
      size="sm"
      className={className}
      footer={
        <ActionButton
          text={confirmText}
          className="modal-button danger"
          onClick={handleRemove}
        />
      }
    >
      <div className="remove-modal-body flex-column align-items-center text-align-center g-075">
        <TriangleAlert className="dl-icon-1 c-red remove-modal-icon" />
        {warningText && (
          <p className="remove-modal-warning m-0" style={{ whiteSpace: "pre-line" }}>
            {warningText}
          </p>
        )}
      </div>
    </Modal>
  );
}

export default RemoveModal;
