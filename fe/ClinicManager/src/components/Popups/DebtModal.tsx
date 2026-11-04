import { useState, useCallback, useEffect } from "react";
import { UserX, HandCoins } from "lucide-react";
import Modal from "../Modal";
import DropdownSelect from "../DropdownSelect";
import CostInput from "../CostInput";
import TextInput from "../TextInput";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import { Client } from "../../models/client";
import { ClientDebt, DebtType, NewClientDebt } from "../../models/debt";
import { PaymentStatus } from "../../models/payment";
import { validateNoSourceClientDebtForm } from "../../utils/validators";
import ClientDebtService from "../../services/ClientDebtService";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface DebtModalProps {
  onClose: () => void;
  clients: Client[];
  debtId?: number | string | null;
  className?: string;
}

export function DebtModal({
  onClose,
  clients,
  debtId,
  className = "",
}: DebtModalProps) {
  const [fetchedDebt, setFetchedDebt] = useState<ClientDebt | null>(null);
  const [debtDTO, setDebtDTO] = useState<NewClientDebt>({
    type: DebtType.UNPAID,
    value: 0,
    client: undefined,
    createdAt: new Date().toISOString().split("T")[0],
    paymentStatus: PaymentStatus.UNPAID,
  });
  const { showAlert } = useAlert();
  const { isNarrow } = useBreakpoint();

  const action = debtId ? Action.EDIT : Action.CREATE;

  const fetchDebtById = async (debtId: number | string) => {
    ClientDebtService.getDebtById(debtId)
      .then((data) => {
        setFetchedDebt(data);
        setDebtDTO(data);
      })
      .catch((error) => {
        console.error("Error fetching Debt: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  useEffect(() => {
    if (debtId) {
      fetchDebtById(debtId);
    }
  }, [debtId]);

  const handleClientChange = useCallback((client: Client | Client[] | null) => {
    const selectedClient = Array.isArray(client) ? client[0] : client;
    setDebtDTO((prev) => ({ ...prev, client: selectedClient ?? undefined }));
  }, []);

  const handleDebtTypeChange = useCallback((type: DebtType) => {
    setDebtDTO((prev) => ({ ...prev, type }));
  }, []);

  const handleValueChange = useCallback((value: number) => {
    setDebtDTO((prev) => ({ ...prev, value }));
  }, []);

  const handleDebtAction = useCallback(async () => {
    const error = validateNoSourceClientDebtForm(debtDTO, action, fetchedDebt);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      if (action === Action.CREATE) {
        await ClientDebtService.createDebt(debtDTO as NewClientDebt);
        showAlert(
          `Dług klienta ${
            debtDTO.client?.firstName + " " + debtDTO.client?.lastName
          } utworzony!`,
          AlertType.SUCCESS
        );
      } else if (action === Action.EDIT && fetchedDebt) {
        await ClientDebtService.updateDebt(
          fetchedDebt.id,
          debtDTO as NewClientDebt
        );
        showAlert(`Dług zaktualizowany!`, AlertType.SUCCESS);
      }
      onClose();
    } catch (error) {
      showAlert(
        `Błąd ${action === Action.CREATE ? "tworzenia" : "aktualizacji"} długu!`,
        AlertType.ERROR
      );
    }
  }, [debtDTO, showAlert, fetchedDebt, action, onClose]);

  return (
    <Modal
      dirtyValue={debtDTO}
      title={action === Action.CREATE ? "Nowy Dług" : "Edytuj Dług"}
      onClose={onClose}
      size="md"
      onConfirm={handleDebtAction}
      className={`modal-overflow ${className}`}
    >
      <div className="flex-column g-075">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Przyczyna:</span>
          <div className="cat-seg-group dt-type-seg flex align-items-center width-fit-content g-025">
            <button
              type="button"
              title="Nieobecność"
              className={`cat-seg dt-absence align-items-center pointer ${
                debtDTO.type === DebtType.ABSENCE_FEE ? "active" : ""
              }`}
              onClick={() => handleDebtTypeChange(DebtType.ABSENCE_FEE)}
            >
              {isNarrow ? <UserX className="dl-icon-1" /> : "Nieobecność"}
            </button>
            <button
              type="button"
              title="Zaległość"
              className={`cat-seg dt-onhold align-items-center pointer ${
                debtDTO.type === DebtType.UNPAID ? "active" : ""
              }`}
              onClick={() => handleDebtTypeChange(DebtType.UNPAID)}
            >
              {isNarrow ? <HandCoins className="dl-icon-1" /> : "Zaległość"}
            </button>
          </div>
        </div>

        <div className="pf-row pf-client-row flex align-items-center g-1">
          <span className="pf-label">Klient:</span>
          <DropdownSelect<Client>
            items={clients}
            placeholder="Wybierz Klienta"
            value={debtDTO.client}
            getItemLabel={(c) => `${c.firstName} ${c.lastName}`}
            allowNew={false}
            onChange={handleClientChange}
            className="clients"
          />
        </div>

        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Wartość:</span>
          <CostInput
            onChange={handleValueChange}
            selectedCost={debtDTO.value ?? 0}
            className="in-sm"
          />
        </div>

        <div className="pf-row dt-reason-row flex-column g-1">
          <span className="pf-label">Przyczyna:</span>
          <TextInput
            value={debtDTO.reason ?? ""}
            onSelect={(v) =>
              setDebtDTO((prev) => ({ ...prev, reason: (v as string) || null }))
            }
            placeholder="opcjonalnie"
            className="in-full"
            multiline={true}
            rows={3}
            maxLength={500}
          />
        </div>
      </div>
    </Modal>
  );
}

export default DebtModal;
