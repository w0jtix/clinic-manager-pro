import React, { useState } from "react";
import { SquarePen } from "lucide-react";
import TextInput from "../TextInput";
import CostInput from "../CostInput";
import ActionButton from "../ActionButton";
import { CashLedger } from "../../models/cash_ledger";
import { formatDate } from "../../utils/dateUtils";
import { Action } from "../../models/action";

export interface CashLedgerFormProps {
  cashLedger: CashLedger;
  setCashLedger?: React.Dispatch<React.SetStateAction<CashLedger | null>>;
  action?: Action;
}

const CashLedgerForm = ({
  cashLedger,
  setCashLedger,
  action = Action.DISPLAY,
}: CashLedgerFormProps) => {
  const [formUI, setFormUI] = useState({
    showOpeningAmountInput: false,
    showDepositInput: false,
    showCashOutAmountInput: false,
    showNoteInput: false,
  });

  const isEdit = action === Action.EDIT;

  // Suma gotówki (wpływy z wizyt) jest stała - liczona raz z pierwotnego stanu.
  // Saldo końcowe jest pochodne: opening + deposit + income - cashOut.
  const [cashIncome] = useState<number>(
    () =>
      (cashLedger.closingAmount ?? 0) -
      cashLedger.openingAmount -
      cashLedger.deposit +
      cashLedger.cashOutAmount
  );

  const derivedClosing =
    cashLedger.openingAmount +
    cashLedger.deposit +
    cashIncome -
    cashLedger.cashOutAmount;

  const EditToggle = ({
    title,
    onClick,
  }: {
    title: string;
    onClick: () => void;
  }) => (
    <ActionButton
      iconTitle={title}
      onClick={onClick}
      disableText={true}
      disableBorder={true}
    >
      <SquarePen className="dl-icon c-blue" />
    </ActionButton>
  );

  return (
    <div className="clx-cf width-max flex-column g-075">
      <div className="clx-form-row flex align-items-center space-between">
        <span className="clx-form-label">Data Kasetki</span>
        <span className="clx-form-value">{formatDate(cashLedger.date)}</span>
      </div>

      <div className="flex-column g-05">
        <div className="clx-form-row flex align-items-center space-between">
          <span className="clx-form-label">Stan początkowy</span>
          <div className="flex g-05 align-items-center">
            <span className="clx-form-value pc-y">
              {(cashLedger.openingAmount + cashLedger.deposit).toFixed(2)} zł
            </span>
            {isEdit && (
              <EditToggle
                title="Edytuj Stan początkowy"
                onClick={() =>
                  setFormUI((prev) => ({
                    ...prev,
                    showOpeningAmountInput: !prev.showOpeningAmountInput,
                  }))
                }
              />
            )}
          </div>
        </div>
        {formUI.showOpeningAmountInput && (
          <div className="flex justify-end">
            <CostInput
              selectedCost={cashLedger.openingAmount}
              onChange={(v) =>
                setCashLedger?.((prev) => ({
                  ...prev!,
                  openingAmount: v,
                  closingAmount:
                    v + prev!.deposit + cashIncome - prev!.cashOutAmount,
                }))
              }
              className="in-sm"
            />
          </div>
        )}
      </div>

      <div className="flex-column g-05">
        <div className="clx-form-row flex align-items-center space-between">
          <span className="clx-form-label">W tym depozyt</span>
          <div className="flex g-05 align-items-center">
            <span className="clx-form-value">
              {cashLedger.deposit.toFixed(2)} zł
            </span>
            {isEdit && (
              <EditToggle
                title="Edytuj Depozyt"
                onClick={() =>
                  setFormUI((prev) => ({
                    ...prev,
                    showDepositInput: !prev.showDepositInput,
                  }))
                }
              />
            )}
          </div>
        </div>
        {formUI.showDepositInput && (
          <div className="flex justify-end">
            <CostInput
              selectedCost={cashLedger.deposit}
              onChange={(v) =>
                setCashLedger?.((prev) => ({
                  ...prev!,
                  deposit: v,
                  closingAmount:
                    prev!.openingAmount + v + cashIncome - prev!.cashOutAmount,
                }))
              }
              className="in-sm"
            />
          </div>
        )}
      </div>

      <div className="clx-form-row flex align-items-center space-between">
        <span className="clx-form-label">Suma gotówki</span>
        <span className="clx-form-value c-green">
          +{cashIncome.toFixed(2)} zł
        </span>
      </div>

      <div className="flex-column g-05">
        <div className="clx-form-row flex align-items-center space-between">
          <span className="clx-form-label">Wypłacono</span>
          <div className="flex g-05 align-items-center">
            <span className="clx-form-value c-red">
              -{cashLedger.cashOutAmount.toFixed(2)} zł
            </span>
            {isEdit && (
              <EditToggle
                title="Edytuj Wypłatę"
                onClick={() =>
                  setFormUI((prev) => ({
                    ...prev,
                    showCashOutAmountInput: !prev.showCashOutAmountInput,
                  }))
                }
              />
            )}
          </div>
        </div>
        {formUI.showCashOutAmountInput && (
          <div className="flex justify-end">
            <CostInput
              selectedCost={cashLedger.cashOutAmount}
              onChange={(v) =>
                setCashLedger?.((prev) => ({
                  ...prev!,
                  cashOutAmount: v,
                  closingAmount:
                    prev!.openingAmount + prev!.deposit + cashIncome - v,
                }))
              }
              className="in-sm"
            />
          </div>
        )}
      </div>

      <div className="clx-form-row flex align-items-center space-between clx-cf-total">
        <span className="clx-form-label">Saldo końcowe</span>
        <span className="clx-form-value c-blue">
          {derivedClosing.toFixed(2)} zł
        </span>
      </div>

      {((cashLedger.note && cashLedger.note.trim() != "") || isEdit) && (
        <div className="flex-column g-025">
          <div className="flex g-05 align-items-center">
            <span className="clx-form-label">Notatka</span>
            {isEdit && (
              <EditToggle
                title="Edytuj Notatkę"
                onClick={() =>
                  setFormUI((prev) => ({
                    ...prev,
                    showNoteInput: !prev.showNoteInput,
                  }))
                }
              />
            )}
          </div>
          {formUI.showNoteInput ? (
            <TextInput
              placeholder="Notatka..."
              multiline={true}
              rows={2}
              value={cashLedger.note ?? ""}
              onSelect={(v) =>
                setCashLedger?.((prev) => ({ ...prev!, note: v as string }))
              }
            />
          ) : (
            cashLedger.note &&
            cashLedger.note.trim() !== "" && (
              <span className="clx-cf-note italic">{cashLedger.note}</span>
            )
          )}
        </div>
      )}
    </div>
  );
};

export default CashLedgerForm;
