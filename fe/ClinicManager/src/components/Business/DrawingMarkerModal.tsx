import { useRef, useState } from "react";
import Modal from "../Modal";
import TextInput from "../TextInput";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import {
  DRAWING_MARKER_NAME_MAX_LENGTH,
  DRAWING_MARKER_SHAPES,
  DrawingMarker,
  DrawingMarkerShape,
} from "../../models/drawing";
import CardSettingsService from "../../services/CardSettingsService";
import { MarkerShapeIcon } from "../ClientCards/DrawingLayers";
import { apiErrorMessage } from "../../utils/apiError";

export const MARKER_COLORS = [
  "#e53935",
  "#fb8c00",
  "#fdd835",
  "#43a047",
  "#00897b",
  "#1e88e5",
  "#3949ab",
  "#8e24aa",
  "#d81b60",
  "#6d4c41",
  "#546e7a",
  "#111111",
];

export interface DrawingMarkerModalProps {
  marker: DrawingMarker | null;
  onClose: () => void;
  onSaved: () => void;
}

export function DrawingMarkerModal({ marker, onClose, onSaved }: DrawingMarkerModalProps) {
  const { showAlert } = useAlert();
  const [name, setName] = useState<string>(marker?.name ?? "");
  const [shape, setShape] = useState<DrawingMarkerShape>(marker?.shape ?? "CIRCLE");
  const [color, setColor] = useState<string>(marker?.color ?? MARKER_COLORS[0]);
  const [filled, setFilled] = useState<boolean>(marker?.filled ?? true);
  const busyRef = useRef<boolean>(false);


  const handleSave = async () => {
    if (busyRef.current) return;
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > DRAWING_MARKER_NAME_MAX_LENGTH) {
      showAlert(
        `Nazwa oznaczenia jest wymagana (maksymalnie ${DRAWING_MARKER_NAME_MAX_LENGTH} znaków).`,
        AlertType.ERROR
      );
      return;
    }
    busyRef.current = true;
    const input = { name: trimmed, shape, color, filled };
    try {
      if (marker) {
        await CardSettingsService.updateDrawingMarker(marker.id, input);
        showAlert("Oznaczenie zapisane!", AlertType.SUCCESS);
      } else {
        await CardSettingsService.createDrawingMarker(input);
        showAlert("Dodano oznaczenie!", AlertType.SUCCESS);
      }
      onSaved();
      onClose();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się zapisać oznaczenia.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
    }
  };

  return (
    <Modal
      title={marker ? "Edytuj oznaczenie" : "Dodaj oznaczenie"}
      onClose={onClose}
      size="md"
      onConfirm={handleSave}
      dirtyValue={`${name}|${shape}|${color}|${filled}`}
    >
      <div className="st-mk-form flex-column g-15">
        <div className="st-mk-preview flex align-items-center g-075">
          <MarkerShapeIcon shape={shape} color={color} filled={filled} />
          <span className="st-mk-preview-name">{name.trim() || "Nazwa oznaczenia"}</span>
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwa:</span>
          <TextInput
            className="in-full"
            value={name}
            maxLength={DRAWING_MARKER_NAME_MAX_LENGTH}
            placeholder="np. Odcisk"
            onSelect={(value) => {
              if (typeof value === "string") setName(value);
            }}
          />
        </div>
        <div className="flex-column g-05">
          <span className="pf-label st-tpl-label">Kształt:</span>
          <div className="st-mk-shapes" role="radiogroup" aria-label="Kształt">
            {DRAWING_MARKER_SHAPES.map((option) => (
              <button
                key={option.value}
                type="button"
                role="radio"
                aria-checked={shape === option.value}
                title={option.label}
                className={`st-mk-shape flex align-items-center g-075 pointer ${
                  shape === option.value ? "active" : ""
                }`}
                onClick={() => setShape(option.value)}
              >
                <MarkerShapeIcon shape={option.value} color={color} filled={filled} />
                <span className="st-mk-shape-label">{option.label}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="flex align-items-center g-1 flex-wrap">
          <span className="pf-label st-tpl-label">Wygląd:</span>
          <div className="cat-seg-group flex align-items-center width-fit-content g-025">
            <button
              type="button"
              className={`cat-seg align-items-center pointer ${filled ? "active" : ""}`}
              onClick={() => setFilled(true)}
            >
              Wypełniony
            </button>
            <button
              type="button"
              className={`cat-seg align-items-center pointer ${!filled ? "active" : ""}`}
              onClick={() => setFilled(false)}
            >
              Kontur
            </button>
          </div>
        </div>
        <div className="flex-column g-1">
          <span className="pf-label st-tpl-label">Kolor:</span>
          <div className="st-mk-colors flex align-items-center flex-wrap g-05" role="radiogroup" aria-label="Kolor">
            {MARKER_COLORS.map((value) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={color === value}
                aria-label={value}
                className={`st-mk-color pointer ${color === value ? "active" : ""}`}
                style={{ backgroundColor: value }}
                onClick={() => setColor(value)}
              />
            ))}
            <div
              className={`st-mk-custom relative flex align-items-center g-05 ${
                MARKER_COLORS.includes(color) ? "" : "active"
              }`}
              title="Własny kolor"
            >
              <span className="st-mk-custom-swatch" style={{ backgroundColor: color }} />
              <span className="st-mk-custom-label">Własny</span>
              <input
                type="color"
                aria-label="Własny kolor"
                value={color}
                onChange={(e) => setColor(e.target.value.toLowerCase())}
              />
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
}

export default DrawingMarkerModal;
