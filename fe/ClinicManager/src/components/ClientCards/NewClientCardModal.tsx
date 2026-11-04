import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import Modal from "../Modal";
import TextInput from "../TextInput";
import DuplicateClientNotice from "./DuplicateClientNotice";
import BirthDateField from "./BirthDateField";
import RegulationsSegment from "./RegulationsSegment";
import ClientCardService from "../../services/ClientCardService";
import { NewClientCard } from "../../models/client-card";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useRoles } from "../../hooks/useRoles";
import { validateClientCardData } from "../../utils/validators";
import {
  buildDuplicateClientMessage,
  getDuplicateClientError,
} from "../../utils/clientDuplicateUtils";

export interface ClientCardPrefill {
  birthDate: string | null;
  phoneNumber: string | null;
}

export const parseInitialText = (text: string): Pick<NewClientCard, "firstName" | "lastName" | "phoneNumber"> => {
  const trimmed = text.trim();
  if (!trimmed) return { firstName: "", lastName: "", phoneNumber: null };
  if (!/\p{L}/u.test(trimmed)) {
    const digits = trimmed.replace(/\D/g, "");
    return { firstName: "", lastName: "", phoneNumber: digits || null };
  }
  const [firstName, ...rest] = trimmed.split(/\s+/);
  return { firstName, lastName: rest.join(" "), phoneNumber: null };
};

export interface NewClientCardModalProps {
  initialText?: string;
  onClose: () => void;
}

export function NewClientCardModal({ initialText = "", onClose }: NewClientCardModalProps) {
  const [card, setCard] = useState<NewClientCard>(() => ({
    ...parseInitialText(initialText),
    birthDate: null,
    signedRegulations: false,
  }));
  const [saving, setSaving] = useState<boolean>(false);
  const navigate = useNavigate();
  const { showAlert } = useAlert();
  const { isMaster } = useRoles();

  const update = useCallback(<K extends keyof NewClientCard>(key: K, value: NewClientCard[K]) => {
    setCard((prev) => ({ ...prev, [key]: value }));
  }, []);

  const openCard = useCallback(
    (clientId: number, prefill?: ClientCardPrefill) => {
      onClose();
      navigate(`/client-cards/${clientId}`, prefill ? { state: { prefill } } : undefined);
    },
    [onClose, navigate]
  );

  const handleUseExistingClient = useCallback(
    (clientId: number) => {
      openCard(clientId, { birthDate: card.birthDate, phoneNumber: card.phoneNumber });
    },
    [openCard, card.birthDate, card.phoneNumber]
  );

  const handleCreate = useCallback(async () => {
    if (saving) return;
    const error = validateClientCardData(card);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    setSaving(true);
    try {
      const created = await ClientCardService.createCard({
        ...card,
        firstName: card.firstName.trim(),
        lastName: card.lastName.trim(),
        phoneNumber: card.phoneNumber?.trim() || null,
      });
      showAlert(`Karta klienta ${created.firstName} ${created.lastName} utworzona!`, AlertType.SUCCESS);
      openCard(created.clientId);
    } catch (err) {
      console.error("Error creating Client Card:", err);
      const duplicate = getDuplicateClientError(err);
      showAlert(
        duplicate ? buildDuplicateClientMessage(duplicate, isMaster) : "Błąd tworzenia karty klienta.",
        AlertType.ERROR
      );
    } finally {
      setSaving(false);
    }
  }, [card, saving, showAlert, openCard, isMaster]);

  return (
    <Modal
      dirtyValue={card}
      title="Nowa karta pacjenta"
      onClose={onClose}
      size="md"
      onConfirm={handleCreate}
      confirmText="Utwórz klienta i kartę"
      confirmDisabled={saving}
      className="cc-new-modal"
    >
      <div className="flex-column g-075">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Imię:</span>
          <TextInput
            dropdown={false}
            className="in-full"
            value={card.firstName}
            onSelect={(value) => {
              if (typeof value === "string") update("firstName", value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwisko:</span>
          <TextInput
            dropdown={false}
            className="in-full"
            value={card.lastName}
            onSelect={(value) => {
              if (typeof value === "string") update("lastName", value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Data urodzenia:</span>
          <BirthDateField value={card.birthDate} onChange={(date) => update("birthDate", date)} />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nr kontaktowy:</span>
          <TextInput
            dropdown={false}
            className="in-full"
            numbersOnly={true}
            value={card.phoneNumber ?? ""}
            onSelect={(value) => {
              if (typeof value === "string") update("phoneNumber", value === "" ? null : value);
            }}
          />
        </div>
        <RegulationsSegment
          value={card.signedRegulations}
          onChange={(value) => update("signedRegulations", value)}
        />

        <DuplicateClientNotice
          firstName={card.firstName}
          lastName={card.lastName}
          phoneNumber={card.phoneNumber}
          onUseClient={handleUseExistingClient}
        />
      </div>
    </Modal>
  );
}

export default NewClientCardModal;
