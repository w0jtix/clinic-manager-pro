import { useEffect, useRef, useState } from "react";
import { Upload, X } from "lucide-react";
import Modal from "../Modal";
import TextInput from "../TextInput";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { DrawingTemplateSettings } from "../../models/drawing";
import CardSettingsService from "../../services/CardSettingsService";
import { useTemplateUrl } from "../ClientCards/DrawingLayers";
import { apiErrorMessage } from "../../utils/apiError";

const MAX_NAME_LENGTH = 60;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
const ALLOWED_TYPES = ["image/png", "image/jpeg"];

export const clientCountLabel = (count: number): string =>
  count === 1 ? "kartki u 1 klienta" : `kartki u ${count} klientów`;

export interface DrawingTemplateModalProps {
  template: DrawingTemplateSettings | null;
  onClose: () => void;
  onSaved: () => void;
}

export function DrawingTemplateModal({ template, onClose, onSaved }: DrawingTemplateModalProps) {
  const { showAlert } = useAlert();
  const [name, setName] = useState<string>(template?.name ?? "");
  const [file, setFile] = useState<File | null>(null);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [removeImage, setRemoveImage] = useState<boolean>(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const busyRef = useRef<boolean>(false);
  const currentUrl = useTemplateUrl(template?.image?.id ?? null);

  useEffect(() => {
    if (!file) {
      setFileUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setFileUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const previewUrl = fileUrl ?? (removeImage ? null : currentUrl);
  const hasImage = !!file || (!removeImage && !!template?.image);
  const backgroundChanged = !!file || removeImage;

  const handleFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0] ?? null;
    event.target.value = "";
    if (!selected) return;
    if (!ALLOWED_TYPES.includes(selected.type)) {
      showAlert("Dozwolony format to PNG lub JPG.", AlertType.ERROR);
      return;
    }
    if (selected.size > MAX_FILE_BYTES) {
      showAlert("Plik jest za duży (maksymalnie 2 MB).", AlertType.ERROR);
      return;
    }
    setFile(selected);
    setRemoveImage(false);
  };

  const clearImage = () => {
    setFile(null);
    setRemoveImage(!!template?.image);
  };

  const handleSave = async () => {
    if (busyRef.current) return;
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > MAX_NAME_LENGTH) {
      showAlert(`Nazwa szablonu jest wymagana (maksymalnie ${MAX_NAME_LENGTH} znaków).`, AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    try {
      if (template) {
        await CardSettingsService.updateDrawingTemplate(template.id, trimmed, file, removeImage);
        showAlert("Szablon zapisany!", AlertType.SUCCESS);
      } else {
        await CardSettingsService.createDrawingTemplate(trimmed, file);
        showAlert("Dodano szablon!", AlertType.SUCCESS);
      }
      onSaved();
      onClose();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się zapisać szablonu.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
    }
  };

  return (
    <Modal
      title={template ? "Edytuj szablon" : "Dodaj szablon"}
      onClose={onClose}
      size="md"
      onConfirm={handleSave}
      dirtyValue={`${name}|${file ? `${file.name}:${file.size}` : ""}|${removeImage}`}
    >
      <div className="st-tpl-form flex-column g-2">
        <div className="flex-column g-025">
          <div className="pf-row flex align-items-center g-1">
            <span className="pf-label">Nazwa:</span>
            <TextInput
              className="in-full"
              value={name}
              maxLength={MAX_NAME_LENGTH}
              onSelect={(value) => {
                if (typeof value === "string") setName(value);
              }}
            />
          </div>
        </div>
        <div className="flex-column g-15">
          <span className="pf-label st-tpl-label">Tło szablonu:</span>
          <div className="st-template-row flex g-1">
            {previewUrl ? (
              <img src={previewUrl} alt="Tło kartki" className="st-template-preview st-tpl-modal-preview" />
            ) : (
              <span className="st-template-empty st-tpl-modal-preview italic flex align-items-center justify-center">
                Pusta kartka
              </span>
            )}
            <div className="flex-column g-075">
              <input ref={inputRef} type="file" accept="image/png,image/jpeg" hidden onChange={handleFile} />
              <ActionButton
                text={hasImage ? "Zmień plik" : "Wybierz plik"}
                iconTitle="Obraz konturu (PNG lub JPG, do 2 MB)"
                onClick={() => inputRef.current?.click()}
              >
                <Upload className="dl-icon-1 pc-y" />
              </ActionButton>
              {hasImage && (
                <ActionButton text="Usuń obraz" onClick={clearImage}>
                  <X className="dl-icon-1 c-red" />
                </ActionButton>
              )}
              <span className="st-card-desc">PNG lub JPG do 2 MB, w pionie, 3:4, 1536×2048 px.</span>
            </div>
          </div>
        </div>
        {template && template.clientCount > 0 && backgroundChanged && (
          <span className="st-tpl-note">
            Nowe tło dostaną nowe kartki oraz puste kartki, na których nic nie narysowano. Kartki z rysunkiem
            zachowają obecny kontur.
          </span>
        )}
      </div>
    </Modal>
  );
}

export default DrawingTemplateModal;
