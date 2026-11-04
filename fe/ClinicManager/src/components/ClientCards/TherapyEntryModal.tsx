import { useState } from "react";
import { Save } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import WheelDateField from "../WheelDateField";
import ClientCardService from "../../services/ClientCardService";
import { TherapyEntry, TimelineItem } from "../../models/therapy";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { formatDate } from "../../utils/dateUtils";
import {
  getTherapyConflict,
  getTherapyDayTaken,
  THERAPY_CONTENT_MAX_LENGTH,
  todayIso,
  validateTherapyEntry,
} from "../../utils/therapyUtils";

export type TherapyEntryEditorMode =
  | { kind: "create"; date: string }
  | { kind: "edit"; date: string; entry: TherapyEntry };

export interface TherapyEntryModalProps {
  clientId: number;
  mode: TherapyEntryEditorMode;
  onClose: () => void;
  onSaved: (item: TimelineItem) => void;
  onConflict: () => void;
}

export function TherapyEntryModal({ clientId, mode, onClose, onSaved, onConflict }: TherapyEntryModalProps) {
  const { showAlert } = useAlert();
  const initialContent = mode.kind === "edit" ? mode.entry.content : "";
  const [entryDate, setEntryDate] = useState<string | null>(mode.date);
  const [content, setContent] = useState<string>(initialContent);
  const [saving, setSaving] = useState<boolean>(false);
  const [confirmDiscard, setConfirmDiscard] = useState<boolean>(false);

  const dirty = entryDate !== mode.date || content !== initialContent;

  const requestClose = () => {
    if (dirty && !saving) {
      setConfirmDiscard(true);
    } else {
      onClose();
    }
  };

  const fail = (message: string) => showAlert(message, AlertType.ERROR);

  const handleSave = async () => {
    const validation = validateTherapyEntry(entryDate, content);
    if (validation) {
      fail(validation);
      return;
    }
    if (mode.kind === "edit" && !dirty) {
      onClose();
      return;
    }
    setSaving(true);
    try {
      const payload = { entryDate: entryDate!, content: content.trim() };
      const saved =
        mode.kind === "create"
          ? await ClientCardService.createTherapyEntry(clientId, payload)
          : await ClientCardService.updateTherapyEntry(clientId, mode.entry.id, {
              ...payload,
              expectedVersion: mode.entry.version,
            });
      showAlert(mode.kind === "create" ? "Wpis dodany!" : "Wpis zaktualizowany!", AlertType.SUCCESS);
      onSaved(saved);
    } catch (err) {
      console.error("Error saving Therapy entry:", err);
      const status = (err as any)?.response?.status;
      if (getTherapyDayTaken(err)) {
        fail(`W dniu ${formatDate(entryDate!)} jest już wpis tego klienta. Edytuj istniejący wpis albo wybierz inną datę.`);
      } else if (getTherapyConflict(err)) {
        fail("Wpis został zmieniony na innym urządzeniu. Lista zostanie odświeżona.");
        onConflict();
      } else if (status === 403) {
        fail("Możesz edytować tylko własne wpisy.");
      } else if (status === 409) {
        fail("Nie można zapisać tego wpisu.");
      } else {
        fail("Nie zapisano wpisu. Spróbuj ponownie.");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Modal
        title={mode.kind === "create" ? "Nowy wpis terapii" : "Edycja wpisu terapii"}
        onClose={requestClose}
        size="lg"
        closeOnOverlay={false}
        className="th-modal"
        footer={
          <>
            <ActionButton text="Wróć" onClick={requestClose} className="cc-back-btn" />
            <ActionButton
              text="Zapisz"
              onClick={handleSave}
              disabled={saving}
              className="cl-filter cc-save-btn active"
            >
              <Save className="dl-icon-1 c-green" />
            </ActionButton>
          </>
        }
      >
        <div className="th-form flex-column g-1">
          <div className="pf-row flex align-items-center g-1">
            <span className="pf-label">Data wizyty:</span>
            <WheelDateField
              value={entryDate}
              onChange={setEntryDate}
              maxDate={new Date(`${todayIso()}T00:00:00`)}
              clearable={false}
              disabled={saving}
            />
          </div>
          <div className="flex-column g-05">
            <span className="pf-label">Przebieg terapii:</span>
            <TextInput
              multiline={true}
              rows={14}
              className="in-full th-content-input"
              maxLength={THERAPY_CONTENT_MAX_LENGTH}
              disabled={saving}
              value={content}
              onSelect={(value) => {
                if (typeof value === "string") setContent(value);
              }}
            />
          </div>
        </div>
      </Modal>
      {confirmDiscard && (
        <Modal
          title="Odrzucić zmiany?"
          onClose={() => setConfirmDiscard(false)}
          size="sm"
          footer={
            <>
              <ActionButton text="Wróć" onClick={() => setConfirmDiscard(false)} className="cc-back-btn" />
              <ActionButton text="Odrzuć" onClick={onClose} className="cl-filter cc-discard-btn active" />
            </>
          }
        >
          <span>Wpis ma niezapisane zmiany. Zamknąć bez zapisywania?</span>
        </Modal>
      )}
    </>
  );
}

export default TherapyEntryModal;
