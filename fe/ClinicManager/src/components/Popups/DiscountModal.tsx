import { useState, useCallback, useEffect } from "react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import DigitInput from "../DigitInput";
import DisplayClientsList from "../Clients/DisplayClientsList";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import { Client } from "../../models/client";
import { NewDiscount, Discount } from "../../models/visit";
import { validateDiscountForm } from "../../utils/validators";
import DiscountService from "../../services/DiscountService";
import ClientService from "../../services/ClientService";

export interface DiscountModalProps {
  onClose: () => void;
  discountId?: number | string | null;
  className?: string;
}

export function DiscountModal({
  onClose,
  discountId,
  className = "",
}: DiscountModalProps) {
  const [fetchedDiscount, setFetchedDiscount] = useState<Discount | null>(null);
  const [discountDTO, setDiscountDTO] = useState<NewDiscount>({
    name: "",
    percentageValue: 0,
    clients: [],
  });
  const { showAlert } = useAlert();

  const action = discountId ? Action.EDIT : Action.CREATE;

  const fetchDiscountById = async (discountId: number | string) => {
    DiscountService.getDiscountById(discountId)
      .then((data) => {
        setFetchedDiscount(data);
        setDiscountDTO((prev) => ({
          ...prev,
          name: data.name,
          percentageValue: data.percentageValue,
        }));
      })
      .catch((error) => {
        console.error("Error fetching Discount: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  const fetchClientsByDiscountId = async (): Promise<void> => {
    ClientService.getClients({ discountId })
      .then((data) => {
        const sortedClients = [...data].sort((a, b) =>
          a.firstName.localeCompare(b.firstName, "pl", { sensitivity: "base" })
        );
        setDiscountDTO((prev) => ({ ...prev, clients: sortedClients }));
      })
      .catch((error) => {
        setDiscountDTO((prev) => ({ ...prev, clients: [] }));
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Clients: ", error);
      });
  };

  useEffect(() => {
    if (discountId) {
      fetchDiscountById(discountId);
      fetchClientsByDiscountId();
    }
  }, [discountId]);

  const handleNameChange = useCallback((name: string) => {
    setDiscountDTO((prev) => ({ ...prev, name }));
  }, []);

  const handlePercentageValueChange = useCallback((value: number | null) => {
    setDiscountDTO((prev) => ({ ...prev, percentageValue: value ?? 0 }));
  }, []);

  const handleClientsChange = useCallback((selected: Client[]) => {
    setDiscountDTO((prev) => ({ ...prev, clients: selected }));
  }, []);

  const handleDiscountAction = useCallback(async () => {
    const error = validateDiscountForm(discountDTO, action, fetchedDiscount);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      if (action === Action.CREATE) {
        await DiscountService.createDiscount(discountDTO as NewDiscount);
        showAlert(`Rabat utworzony!`, AlertType.SUCCESS);
      } else if (action === Action.EDIT && fetchedDiscount) {
        await DiscountService.updateDiscount(
          fetchedDiscount.id,
          discountDTO as NewDiscount
        );
        showAlert(`Rabat zaktualizowany!`, AlertType.SUCCESS);
      }
      onClose();
    } catch (error) {
      showAlert(
        `Błąd ${
          action === Action.CREATE ? "tworzenia" : "aktualizacji"
        } rabatu!`,
        AlertType.ERROR
      );
    }
  }, [discountDTO, showAlert, fetchedDiscount, action, onClose]);

  return (
    <Modal
      dirtyValue={discountDTO}
      title={action === Action.CREATE ? "Nowy Rabat" : "Edytuj Rabat"}
      onClose={onClose}
      size="md"
      className={className}
      footer={
        <div className="flex align-items-center space-between width-max g-1">
          <span className="dc-hint">
            Klient może mieć przypisaną maksymalnie 1 zniżkę. Przypisanie
            Klienta do nowej zniżki zastąpi poprzednią.
          </span>
          <ActionButton
            text={"Zapisz"}
            className="modal-button"
            onClick={handleDiscountAction}
          />
        </div>
      }
    >
      <div className="flex-column g-075">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwa:</span>
          <TextInput
            dropdown={false}
            className="in-md"
            placeholder="Max 6 znaków"
            maxLength={6}
            value={discountDTO.name}
            onSelect={(value) => {
              if (typeof value === "string") handleNameChange(value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Wartość Rabatu [%]:</span>
          <DigitInput
            onChange={handlePercentageValueChange}
            value={discountDTO.percentageValue}
            max={100}
            placeholder={"0"}
            className="in-xs"
          />
        </div>

        <DisplayClientsList
          clients={discountDTO.clients ?? []}
          onClientsChange={handleClientsChange}
        />
      </div>
    </Modal>
  );
}

export default DiscountModal;
