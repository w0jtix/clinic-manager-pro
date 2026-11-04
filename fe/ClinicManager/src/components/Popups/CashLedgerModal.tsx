import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { CashLedger } from "../../models/cash_ledger";
import CashLedgerForm from "../CashLedger/CashLedgerForm";
import { Action } from "../../models/action";

export interface CashLedgerModalProps {
  onClose: () => void;
  cashLedger?: CashLedger;
  setCashLedger?: React.Dispatch<React.SetStateAction<CashLedger | null>>;
  handleCloseCashLedger: () => void;
  className?: string;
  action?: Action;
}

export function CashLedgerModal({
  onClose,
  cashLedger,
  setCashLedger,
  handleCloseCashLedger,
  className = "",
  action = Action.DISPLAY,
}: CashLedgerModalProps) {
  return (
    <Modal
      dirtyValue={cashLedger}
      title={action === Action.EDIT ? "Edytuj Kasetkę" : "Sprawdź zgodność"}
      onClose={onClose}
      size="sm"
      className={className}
      footer={
        <ActionButton
          text="Zatwierdź"
          onClick={handleCloseCashLedger}
          className="modal-button"
        />
      }
    >
      <CashLedgerForm
        cashLedger={cashLedger as CashLedger}
        setCashLedger={setCashLedger}
        action={action}
      />
    </Modal>
  );
}

export default CashLedgerModal;
