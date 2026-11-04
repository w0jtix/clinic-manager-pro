import Modal from "../Modal";
import ActionButton from "../ActionButton";

interface KsefRememberCategoryModalProps {
  sellerName: string;
  categoryName: string;
  categoryColor?: string;
  onConfirm: () => void;
  onCancel: () => void;
  onClose: () => void;
}

export function KsefRememberCategoryModal({
  sellerName,
  categoryName,
  categoryColor,
  onConfirm,
  onCancel,
  onClose,
}: KsefRememberCategoryModalProps) {
  return (
    <Modal
      title={sellerName || "Zapamiętać kategorię?"}
      onClose={onClose}
      size="md"
      footer={
        <>
          <ActionButton text="Pomiń" onClick={onCancel} className="modal-button" />
          <ActionButton
            text="Zatwierdź"
            onClick={onConfirm}
            className="modal-button"
          >
          </ActionButton>
        </>
      }
    >
      <div className="krc-body flex-column align-items-center text-align-center g-075">
        <span className="krc-question">
          Kolejne faktury od tego dostawcy zostaną automatycznie przypisane do
          kategorii:
        </span>
        <div className="flex align-items-center g-05">
          <span
            className="dl-dot"
            style={{ backgroundColor: `rgb(${categoryColor})` }}
          />
          <span className="dl-name">{categoryName}</span>
        </div>
      </div>
    </Modal>
  );
}

export default KsefRememberCategoryModal;
