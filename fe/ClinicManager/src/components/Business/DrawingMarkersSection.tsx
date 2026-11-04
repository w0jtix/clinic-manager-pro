import { useCallback, useEffect, useState } from "react";
import { ChevronDown, ChevronUp, GripVertical, Plus, RotateCcw, SquarePen, Trash2 } from "lucide-react";
import ActionButton from "../ActionButton";
import RemoveModal from "../Popups/RemoveModal";
import RowActionMenu from "../RowActionMenu";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { DrawingMarker, MAX_DRAWING_MARKERS } from "../../models/drawing";
import CardSettingsService from "../../services/CardSettingsService";
import { MarkerShapeIcon } from "../ClientCards/DrawingLayers";
import { apiErrorMessage } from "../../utils/apiError";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import DrawingMarkerModal from "./DrawingMarkerModal";

type EditTarget = { kind: "new" } | { kind: "edit"; marker: DrawingMarker };

export function DrawingMarkersSection() {
  const { showAlert } = useAlert();
  const [markers, setMarkers] = useState<DrawingMarker[] | null>(null);
  const [loadError, setLoadError] = useState<boolean>(false);
  const [editTarget, setEditTarget] = useState<EditTarget | null>(null);
  const [removeTarget, setRemoveTarget] = useState<DrawingMarker | null>(null);
  const [busy, setBusy] = useState<boolean>(false);
  const [armedId, setArmedId] = useState<number | null>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dropIndex, setDropIndex] = useState<number | null>(null);
  const { isMobile, isNarrow } = useBreakpoint();

  const load = useCallback(() => {
    setLoadError(false);
    return CardSettingsService.getDrawingMarkers()
      .then(setMarkers)
      .catch((error) => {
        console.error("Error fetching Drawing markers:", error);
        setLoadError(true);
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const move = async (from: number, to: number) => {
    if (!markers || busy || to < 0 || to >= markers.length) return;
    const previous = markers;
    const next = markers.slice();
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    setMarkers(next);
    setBusy(true);
    try {
      setMarkers(await CardSettingsService.reorderDrawingMarkers(next.map((marker) => marker.id)));
    } catch (error) {
      setMarkers(previous);
      showAlert(apiErrorMessage(error) ?? "Nie udało się zmienić kolejności.", AlertType.ERROR);
    } finally {
      setBusy(false);
    }
  };

  const endDrag = () => {
    setDragIndex(null);
    setDropIndex(null);
    setArmedId(null);
  };

  const dropClass = (index: number): string => {
    if (dragIndex === null || dropIndex !== index || dragIndex === index) return "";
    return dragIndex < index ? "drop-after" : "drop-before";
  };

  const handleRemove = async (marker: DrawingMarker) => {
    setBusy(true);
    try {
      await CardSettingsService.deleteDrawingMarker(marker.id);
      showAlert("Oznaczenie usunięte.", AlertType.SUCCESS);
      setRemoveTarget(null);
      load();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się usunąć oznaczenia.", AlertType.ERROR);
    } finally {
      setBusy(false);
    }
  };

  const count = markers?.length ?? 0;
  const canAdd = count < MAX_DRAWING_MARKERS;

  return (
    <section className="st-card st-drawing-markers flex-column g-1">
      <div className="flex align-items-center space-between g-05 flex-wrap">
        <div className="flex align-items-center g-05 flex-wrap">
          <span className="st-card-label">Oznaczenia na rysunku</span>
          <span className="st-tpl-count">{`${count} / ${MAX_DRAWING_MARKERS}`}</span>
        </div>
        {markers !== null && canAdd && (
          <ActionButton
            text="Dodaj oznaczenie"
            iconTitle="Dodaj oznaczenie"
            disableText={isNarrow}
            className="st-mk-add"
            onClick={() => setEditTarget({ kind: "new" })}
          >
            <Plus className="dl-icon-1 pc-y" />
          </ActionButton>
        )}
      </div>

      {loadError ? (
        <div className="flex align-items-center g-1 flex-wrap">
          <span className="cq-empty">Nie udało się wczytać oznaczeń.</span>
          <ActionButton text="Spróbuj ponownie" onClick={() => load()}>
            <RotateCcw className="dl-icon-1" />
          </ActionButton>
        </div>
      ) : markers === null ? (
        <span className="cq-empty italic">Wczytywanie oznaczeń…</span>
      ) : markers.length === 0 ? (
        <span className="cq-empty italic">Brak oznaczeń. Dodaj kształty, które podolog postawi na rysunku.</span>
      ) : (
        <div className="st-mk-list">
          {markers.map((marker, index) => (
            <div
              key={marker.id}
              className={`st-mk-row ${dragIndex === index ? "dragging" : ""} ${dropClass(index)}`}
              draggable={!isMobile && armedId === marker.id}
              onDragStart={(e) => {
                e.dataTransfer.effectAllowed = "move";
                setDragIndex(index);
              }}
              onDragOver={(e) => {
                if (dragIndex === null) return;
                e.preventDefault();
                setDropIndex(index);
              }}
              onDrop={(e) => {
                e.preventDefault();
                if (dragIndex !== null) move(dragIndex, index);
                endDrag();
              }}
              onDragEnd={endDrag}
            >
              {isMobile ? (
                <div className="st-mk-arrows flex align-items-center g-025">
                  <ActionButton
                    iconTitle="Przesuń wyżej"
                    disableText
                    disableBorder
                    disabled={index === 0 || busy}
                    onClick={() => move(index, index - 1)}
                    className="st-tpl-move"
                  >
                    <ChevronUp className="dl-icon" />
                  </ActionButton>
                  <ActionButton
                    iconTitle="Przesuń niżej"
                    disableText
                    disableBorder
                    disabled={index === markers.length - 1 || busy}
                    onClick={() => move(index, index + 1)}
                    className="st-tpl-move"
                  >
                    <ChevronDown className="dl-icon" />
                  </ActionButton>
                </div>
              ) : (
                <span
                  className="st-tpl-grip flex align-items-center"
                  title="Przeciągnij, aby zmienić kolejność"
                  onPointerDown={() => setArmedId(marker.id)}
                  onPointerUp={() => setArmedId(null)}
                >
                  <GripVertical className="dl-icon" />
                </span>
              )}
              <MarkerShapeIcon shape={marker.shape} color={marker.color} filled={marker.filled} />
              <span className="st-mk-name">{marker.name}</span>
              <RowActionMenu
                items={[
                  {
                    label: "Edytuj",
                    icon: <SquarePen className="dl-icon c-blue" />,
                    onClick: () => setEditTarget({ kind: "edit", marker }),
                  },
                  {
                    label: "Usuń",
                    icon: <Trash2 className="dl-icon c-red" />,
                    onClick: () => setRemoveTarget(marker),
                    danger: true,
                  },
                ]}
              />
            </div>
          ))}
        </div>
      )}

      {editTarget && (
        <DrawingMarkerModal
          marker={editTarget.kind === "edit" ? editTarget.marker : null}
          onClose={() => setEditTarget(null)}
          onSaved={load}
        />
      )}

      {removeTarget && (
        <RemoveModal
          onClose={() => setRemoveTarget(null)}
          warningText={`Oznaczenie „${removeTarget.name}” zniknie z wyboru na rysunku. Oznaczenia już postawione na kartkach zostaną bez zmian.`}
          handleRemove={() => handleRemove(removeTarget)}
        />
      )}
    </section>
  );
}

export default DrawingMarkersSection;
