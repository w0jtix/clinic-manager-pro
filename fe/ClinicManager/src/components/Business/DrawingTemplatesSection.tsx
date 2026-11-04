import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Archive, ChevronDown, ChevronLeft, ChevronRight, GripVertical, SquarePen, Plus, RotateCcw, Trash2 } from "lucide-react";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import RemoveModal from "../Popups/RemoveModal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { DrawingTemplateSettings, MAX_ACTIVE_DRAWING_TEMPLATES } from "../../models/drawing";
import CardSettingsService from "../../services/CardSettingsService";
import { useTemplateUrl } from "../ClientCards/DrawingLayers";
import { apiErrorMessage } from "../../utils/apiError";
import { formatDate } from "../../utils/dateUtils";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import DrawingTemplateModal from "./DrawingTemplateModal";

type EditTarget = { kind: "new" } | { kind: "edit"; template: DrawingTemplateSettings };

function TemplateThumb({ template, className = "" }: { template: DrawingTemplateSettings; className?: string }) {
  const url = useTemplateUrl(template.image?.id ?? null);
  if (template.image && url) {
    return <img src={url} alt={`Szablon ${template.name}`} className={`st-tpl-thumb ${className}`} draggable={false} />;
  }
  return (
    <span className={`st-tpl-thumb st-tpl-blank italic flex align-items-center justify-center ${className}`}>
      {template.image ? "" : "Pusta kartka"}
    </span>
  );
}

const EMPTY_DRAG_IMAGE = (() => {
  if (typeof Image === "undefined") return null;
  const image = new Image();
  image.src = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
  return image;
})();

interface DragGhost {
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  width: number;
}

const moveItem = <T,>(list: T[], from: number, to: number): T[] => {
  const next = list.slice();
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
};

export function DrawingTemplatesSection() {
  const { showAlert } = useAlert();
  const [templates, setTemplates] = useState<DrawingTemplateSettings[] | null>(null);
  const [loadError, setLoadError] = useState<boolean>(false);
  const [editTarget, setEditTarget] = useState<EditTarget | null>(null);
  const [removeTarget, setRemoveTarget] = useState<DrawingTemplateSettings | null>(null);
  const [showArchived, setShowArchived] = useState<boolean>(false);
  const [armedId, setArmedId] = useState<number | null>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dropIndex, setDropIndex] = useState<number | null>(null);
  const [busy, setBusy] = useState<boolean>(false);
  const [ghost, setGhost] = useState<DragGhost | null>(null);
  const ghostRef = useRef<DragGhost | null>(null);
  const { isMobile } = useBreakpoint();

  const load = useCallback(() => {
    setLoadError(false);
    return CardSettingsService.getDrawingTemplates()
      .then(setTemplates)
      .catch((error) => {
        console.error("Error fetching Drawing templates:", error);
        setLoadError(true);
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const active = useMemo(
    () => (templates ?? []).filter((t) => !t.archived).sort((a, b) => a.displayOrder - b.displayOrder),
    [templates]
  );
  const archived = useMemo(() => (templates ?? []).filter((t) => t.archived), [templates]);

  const reorder = useCallback(
    async (from: number, to: number) => {
      if (busy || from === to || to < 0 || to >= active.length || !templates) return;
      const reordered = moveItem(active, from, to);
      const previous = templates;
      setTemplates([...reordered.map((t, i) => ({ ...t, displayOrder: i + 1 })), ...archived]);
      setBusy(true);
      try {
        setTemplates(await CardSettingsService.reorderDrawingTemplates(reordered.map((t) => t.id)));
      } catch (error) {
        setTemplates(previous);
        showAlert(apiErrorMessage(error) ?? "Nie udało się zmienić kolejności.", AlertType.ERROR);
      } finally {
        setBusy(false);
      }
    },
    [busy, active, archived, templates, showAlert]
  );

  const endDrag = () => {
    setDragIndex(null);
    setDropIndex(null);
    setArmedId(null);
    ghostRef.current = null;
    setGhost(null);
  };

  useEffect(() => {
    if (dragIndex === null) return;
    const follow = (e: DragEvent) => {
      const current = ghostRef.current;
      if (!current || (e.clientX === 0 && e.clientY === 0)) return;
      const next = { ...current, x: e.clientX, y: e.clientY };
      ghostRef.current = next;
      setGhost(next);
    };
    document.addEventListener("dragover", follow);
    return () => document.removeEventListener("dragover", follow);
  }, [dragIndex]);

  const dropClass = (index: number): string => {
    if (dragIndex === null || dropIndex !== index || dragIndex === index) return "";
    return dragIndex < index ? "drop-after" : "drop-before";
  };

  const renderCardBody = (template: DrawingTemplateSettings, index: number) => (
    <>
      <div className="st-tpl-head">
        {isMobile ? (
          <div className="st-tpl-arrows flex align-items-center g-025">
            <ActionButton
              iconTitle="Przesuń w lewo"
              disableText
              disabled={index === 0 || busy}
              onClick={() => reorder(index, index - 1)}
              className="st-tpl-move"
            >
              <ChevronLeft className="dl-icon" />
            </ActionButton>
            <ActionButton
              iconTitle="Przesuń w prawo"
              disableText
              disabled={index === active.length - 1 || busy}
              onClick={() => reorder(index, index + 1)}
              className="st-tpl-move"
            >
              <ChevronRight className="dl-icon" />
            </ActionButton>
          </div>
        ) : (
          <span
            className="st-tpl-grip flex align-items-center"
            title="Przeciągnij, aby zmienić kolejność"
            onPointerDown={() => setArmedId(template.id)}
            onPointerUp={() => setArmedId(null)}
          >
            <GripVertical className="dl-icon" />
          </span>
        )}
        <span className="st-tpl-name text-align-center">{template.name}</span>
        <div className="st-tpl-actions flex align-items-center g-025">
          <ActionButton
            iconTitle="Edytuj"
            disableText
            disableBorder
            onClick={() => setEditTarget({ kind: "edit", template })}
            className="st-tpl-icon"
          >
            <SquarePen className="dl-icon c-blue" />
          </ActionButton>
          <ActionButton
            iconTitle={template.clientCount > 0 ? "Archiwizuj" : "Usuń"}
            disableText
            disableBorder
            onClick={() => setRemoveTarget(template)}
            className="st-tpl-icon"
          >
            {template.clientCount > 0 ? (
              <Archive className="dl-icon pc-y" />
            ) : (
              <Trash2 className="dl-icon c-red" />
            )}
          </ActionButton>
        </div>
      </div>
      <TemplateThumb template={template} />
    </>
  );

  const handleRemove = async (template: DrawingTemplateSettings) => {
    setBusy(true);
    try {
      const result = await CardSettingsService.deleteDrawingTemplate(template.id);
      showAlert(result === "ARCHIVED" ? "Szablon zarchiwizowany." : "Szablon usunięty.", AlertType.SUCCESS);
      setRemoveTarget(null);
      load();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się usunąć szablonu.", AlertType.ERROR);
    } finally {
      setBusy(false);
    }
  };

  const handleRestore = async (template: DrawingTemplateSettings) => {
    setBusy(true);
    try {
      await CardSettingsService.restoreDrawingTemplate(template.id);
      showAlert("Szablon przywrócony.", AlertType.SUCCESS);
      load();
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się przywrócić szablonu.", AlertType.ERROR);
    } finally {
      setBusy(false);
    }
  };

  const canAdd = active.length < MAX_ACTIVE_DRAWING_TEMPLATES;

  return (
    <section className="st-card st-drawing-templates flex-column g-1">
      <div className="flex align-items-center g-05 flex-wrap">
        <span className="st-card-label">Szablony kart rysunku</span>
        <span className="st-tpl-count">{`${active.length} / ${MAX_ACTIVE_DRAWING_TEMPLATES} aktywnych`}</span>
      </div>

      {loadError ? (
        <div className="flex align-items-center g-1 flex-wrap">
          <span className="cq-empty">Nie udało się wczytać szablonów.</span>
          <ActionButton text="Spróbuj ponownie" onClick={() => load()}>
            <RotateCcw className="dl-icon-1" />
          </ActionButton>
        </div>
      ) : templates === null ? (
        <span className="cq-empty italic">Wczytywanie szablonów…</span>
      ) : (
        <div className="st-tpl-grid">
          {active.map((template, index) => (
            <div
              key={template.id}
              className={`st-tpl-card flex-column g-05 ${dragIndex === index ? "dragging" : ""} ${dropClass(index)}`}
              draggable={!isMobile && armedId === template.id}
              onDragStart={(e) => {
                e.dataTransfer.effectAllowed = "move";
                if (EMPTY_DRAG_IMAGE) e.dataTransfer.setDragImage(EMPTY_DRAG_IMAGE, 0, 0);
                const rect = e.currentTarget.getBoundingClientRect();
                const start = {
                  x: e.clientX,
                  y: e.clientY,
                  offsetX: e.clientX - rect.left,
                  offsetY: e.clientY - rect.top,
                  width: rect.width,
                };
                ghostRef.current = start;
                setGhost(start);
                setDragIndex(index);
              }}
              onDragOver={(e) => {
                if (dragIndex === null) return;
                e.preventDefault();
                setDropIndex(index);
              }}
              onDrop={(e) => {
                e.preventDefault();
                if (dragIndex !== null) reorder(dragIndex, index);
                endDrag();
              }}
              onDragEnd={endDrag}
            >
              {renderCardBody(template, index)}
            </div>
          ))}
          {canAdd && (
            <button
              type="button"
              className="st-tpl-add flex-column align-items-center justify-center g-05 pointer"
              onClick={() => setEditTarget({ kind: "new" })}
            >
              <span className="st-tpl-add-icon flex align-items-center justify-center">
                <Plus className="dl-icon-1" />
              </span>
              <span className="st-tpl-name">Dodaj szablon</span>
            </button>
          )}
        </div>
      )}

      {archived.length > 0 && (
        <div className="st-tpl-archived flex-column g-05">
          <button
            type="button"
            className="st-tpl-archived-toggle flex align-items-center g-05 pointer"
            aria-expanded={showArchived}
            onClick={() => setShowArchived((prev) => !prev)}
          >
            <Archive className="dl-icon" />
            {`Zarchiwizowane (${archived.length})`}
            <ChevronDown className={`dl-icon st-tpl-chevron ${showArchived ? "open" : ""}`} />
          </button>
          {showArchived &&
            archived.map((template) => (
              <div key={template.id} className="st-tpl-archived-row flex align-items-center g-1 flex-wrap">
                <TemplateThumb template={template} className="mini" />
                <div className="flex-column g-025 f-1">
                  <span className="st-tpl-name">{template.name}</span>
                  {template.archivedAt && (
                    <span className="st-tpl-meta flex align-items-center g-025" title="Zarchiwizowany">
                      <Archive className="dl-icon pc-y" />
                      {formatDate(template.archivedAt)}
                    </span>
                  )}
                </div>
                <ActionButton text="Przywróć" disabled={busy} onClick={() => handleRestore(template)}>
                  <RotateCcw className="dl-icon-1 pc-y" />
                </ActionButton>
              </div>
            ))}
        </div>
      )}

      {ghost && dragIndex !== null && active[dragIndex] && (
        <div
          className="st-tpl-card st-tpl-ghost flex-column g-05"
          style={{ width: ghost.width, left: ghost.x - ghost.offsetX, top: ghost.y - ghost.offsetY }}
          aria-hidden="true"
        >
          {renderCardBody(active[dragIndex], dragIndex)}
        </div>
      )}

      {editTarget && (
        <DrawingTemplateModal
          template={editTarget.kind === "edit" ? editTarget.template : null}
          onClose={() => setEditTarget(null)}
          onSaved={load}
        />
      )}

      {removeTarget && removeTarget.clientCount === 0 && (
        <RemoveModal
          onClose={() => setRemoveTarget(null)}
          warningText={`Szablon „${removeTarget.name}” nie był użyty w kartach klienta. Zostanie usunięty na stałe.`}
          handleRemove={() => handleRemove(removeTarget)}
        />
      )}

      {removeTarget && removeTarget.clientCount > 0 && (
        <Modal
          title="Zarchiwizować szablon?"
          onClose={() => setRemoveTarget(null)}
          size="sm"
          footer={
            <ActionButton
              text="Zarchiwizuj"
              disabled={busy}
              className="modal-button ag-btn ag-yellow"
              onClick={() => handleRemove(removeTarget)}
            />
          }
        >
          <div className="flex-column g-075">
            <span>
              Szablon został użyty w Karcie Klienta. Dostępna jest archiwizacja:
            </span>
            <ul className="st-tpl-list m-0">
              <li>znika z wyboru w karcie klienta,</li>
              <li>wszystkie rysunki zostają do podglądu, także otwarte,</li>
              <li>możliwe jest przywrócenie zarchiwizowanego szablonu.</li>
            </ul>
          </div>
        </Modal>
      )}
    </section>
  );
}

export default DrawingTemplatesSection;
