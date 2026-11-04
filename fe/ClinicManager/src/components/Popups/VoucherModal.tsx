import { useState, useCallback, useEffect } from "react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import DropdownSelect from "../DropdownSelect";
import CostInput from "../CostInput";
// import DateInput from "../DateInput";
import WheelDateField from "../WheelDateField";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import { Client } from "../../models/client";
import { NewVoucher, Voucher } from "../../models/voucher";
import { validateVoucherForm } from "../../utils/validators";
import VoucherService from "../../services/VoucherService";
import ClientService from "../../services/ClientService";

export interface VoucherModalProps {
  onClose: () => void;
  voucherId?: string | number;
  action: Action;
  className?: string;
}

export function VoucherModal({
  onClose,
  voucherId,
  action = Action.CREATE,
  className = "",
}: VoucherModalProps) {
  const [voucherDTO, setVoucherDTO] = useState<NewVoucher>({
    value: 0,
    client: undefined,
    issueDate: new Date().toISOString().split("T")[0],
    expiryDate: null,
  });
  const [fetchedVoucher, setFetchedVoucher] = useState<Voucher | null>(null);
  const [clients, setClients] = useState<Client[]>([]);
  const [voucherHasSaleReference, setVoucherHasSaleReference] =
    useState<boolean>(false);
  const { showAlert } = useAlert();

  const isDisplay = action === Action.DISPLAY;
  const displayedVoucher: NewVoucher | Voucher =
    isDisplay && fetchedVoucher ? fetchedVoucher : voucherDTO;

  const fetchVoucherById = async () => {
    if (!voucherId) return;
    VoucherService.getVoucherById(voucherId)
      .then((data) => {
        setFetchedVoucher(data);
        if (action === Action.EDIT) {
          setVoucherDTO({
            value: data.value,
            client: data.client,
            issueDate: data.issueDate,
            expiryDate: data.expiryDate,
          });
        }
      })
      .catch((error) => {
        setFetchedVoucher(null);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Voucher by voucherId.", error);
      });
  };

  const fetchClients = async (): Promise<void> => {
    ClientService.getClients()
      .then((data) => {
        const sortedClients = [...data].sort((a, b) =>
          a.firstName.localeCompare(b.firstName, "pl", { sensitivity: "base" })
        );
        setClients(sortedClients);
      })
      .catch((error) => {
        setClients([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Clients: ", error);
      });
  };

  const checkForReference = async () => {
    if (action === Action.EDIT && voucherId) {
      try {
        const hasReference = await VoucherService.hasSaleReference(voucherId);
        setVoucherHasSaleReference(Boolean(hasReference));
      } catch (error) {
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error checking reference:", error);
        setVoucherHasSaleReference(false);
      }
    } else {
      setVoucherHasSaleReference(false);
    }
  };

  useEffect(() => {
    if (voucherId) {
      fetchVoucherById();
    }
  }, [voucherId]);

  useEffect(() => {
    if (!isDisplay) {
      fetchClients();
      checkForReference();
    }
  }, [voucherId, isDisplay]);

  const handleClientChange = useCallback((client: Client | Client[] | null) => {
    const selectedClient = Array.isArray(client) ? client[0] : client;
    setVoucherDTO((prev) => ({ ...prev, client: selectedClient ?? undefined }));
  }, []);

  const handleIssueDateChange = useCallback((newDate: string | null) => {
    setVoucherDTO((prev) => ({
      ...prev,
      issueDate: newDate || new Date().toISOString(),
    }));
  }, []);

  const handleExpiryDateChange = useCallback((newDate: string | null) => {
    setVoucherDTO((prev) => ({
      ...prev,
      expiryDate: newDate || new Date().toISOString(),
    }));
  }, []);

  const handleValueChange = useCallback((value: number) => {
    setVoucherDTO((prev) => ({ ...prev, value }));
  }, []);

  const handleVoucherAction = useCallback(async () => {
    const error = validateVoucherForm(voucherDTO, action, fetchedVoucher);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      if (action === Action.CREATE) {
        await VoucherService.createVoucher(voucherDTO as NewVoucher);
        showAlert(
          `Voucher klienta ${
            voucherDTO.client?.firstName + " " + voucherDTO.client?.lastName
          } utworzony!`,
          AlertType.SUCCESS
        );
      } else if (action === Action.EDIT && fetchedVoucher) {
        await VoucherService.updateVoucher(
          fetchedVoucher.id,
          voucherDTO as NewVoucher
        );
        showAlert(`Voucher zaktualizowany!`, AlertType.SUCCESS);
      }
      onClose();
    } catch (error) {
      showAlert(
        `Błąd ${
          action === Action.CREATE ? "tworzenia" : "aktualizacji"
        } vouchera!`,
        AlertType.ERROR
      );
    }
  }, [voucherDTO, showAlert, fetchedVoucher, action, onClose]);

  return (
    <Modal
      dirtyValue={voucherDTO}
      title={
        action === Action.CREATE
          ? "Nowy Voucher"
          : action === Action.EDIT
          ? "Edytuj Voucher"
          : "Podgląd Vouchera"
      }
      onClose={onClose}
      size="md"
      onConfirm={isDisplay ? undefined : handleVoucherAction}
      footer={
        !isDisplay && action === Action.EDIT && !voucherHasSaleReference ? (
          <div className="flex align-items-center space-between width-max g-1">
            <span className="dc-hint">
              Podczas edycji Vouchera zarządzasz datami osobno.
            </span>
            <ActionButton
              text={"Zapisz"}
              className="modal-button"
              onClick={handleVoucherAction}
            />
          </div>
        ) : undefined
      }
      className={`modal-overflow ${className}`}
    >
      <div className="flex-column g-075">
        {!voucherHasSaleReference && (
          <div className="pf-row pf-client-row flex align-items-center g-1">
            <span className="pf-label">Klient:</span>
            <DropdownSelect<Client>
              items={clients}
              placeholder="Wybierz Klienta"
              value={displayedVoucher.client}
              getItemLabel={(c) => `${c.firstName} ${c.lastName}`}
              allowNew={false}
              onChange={handleClientChange}
              className="clients"
              disabled={isDisplay}
            />
          </div>
        )}

        {!voucherHasSaleReference && (
          <div className="pf-row flex align-items-center g-1">
            <span className="pf-label">Data zakupu:</span>
            {/* <DateInput
              onChange={handleIssueDateChange}
              selectedDate={displayedVoucher.issueDate ?? new Date()}
              disabled={isDisplay}
            /> */}
            <WheelDateField
              title="Data zakupu"
              value={displayedVoucher.issueDate ?? new Date()}
              onChange={handleIssueDateChange}
              disabled={isDisplay}
            />
          </div>
        )}

        {action === Action.EDIT && (
          <div className="pf-row flex align-items-center g-1">
            <span className="pf-label">Ważny do:</span>
            {/* <DateInput
              onChange={handleExpiryDateChange}
              selectedDate={displayedVoucher.expiryDate}
            /> */}
            <WheelDateField
              title="Ważny do"
              value={displayedVoucher.expiryDate}
              onChange={handleExpiryDateChange}
            />
          </div>
        )}

        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Wartość:</span>
          <CostInput
            onChange={handleValueChange}
            selectedCost={displayedVoucher.value ?? 0}
            disabled={isDisplay}
            className="in-sm"
          />
        </div>

      </div>
    </Modal>
  );
}

export default VoucherModal;
