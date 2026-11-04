import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Archive, FilePlus2, PencilLine, RotateCcw, X } from "lucide-react";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import DrawingEditor, { DrawingOtherLayer, toLegendEntry } from "./DrawingEditor";
import { DrawingLegend, LayerCanvas, loadSheetItems, useLayerItems, useTemplateUrl } from "./DrawingLayers";
import ClientCardService from "../../services/ClientCardService";
import { ClientDrawing, DrawingGroup, DrawingLayer, DrawingMarker, StoredDrawItem } from "../../models/drawing";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { formatDate } from "../../utils/dateUtils";
import {
  DrawItem,
  getDrawingConflict,
  resolveEditableSheetSize,
  resolveEditableTemplateImageId,
} from "../../utils/drawingUtils";
import { apiErrorMessage } from "../../utils/apiError";
import { useRoles } from "../../hooks/useRoles";
import { useCardTheme } from "../../hooks/useCardTheme";
import { useLightPortals } from "../../hooks/useLightPortals";

export interface DrawingTabProps {
  clientId: number;
  readOnly: boolean;
  onDirtyChange: (dirty: boolean, save: (() => Promise<boolean>) | null) => void;
  openEditorOnLoad?: OpenSheet | null;
  onEditorOpened?: () => void;
}

interface SheetPreviewProps {
  clientId: number;
  sheetId: number | null;
  layers: DrawingLayer[];
  templateImageId: number | null;
  width: number;
  height: number;
  className?: string;
  onClick: () => void;
  children?: React.ReactNode;
}

function SheetPreview({
  clientId,
  sheetId,
  layers,
  templateImageId,
  width,
  height,
  className = "",
  onClick,
  children,
}: SheetPreviewProps) {
  const templateUrl = useTemplateUrl(templateImageId);
  const layerItems = useLayerItems(clientId, sheetId, layers);
  return (
    <button
      type="button"
      className={`dw-preview relative pointer ${className}`}
      style={{ aspectRatio: `${width} / ${height}` }}
      onClick={onClick}
    >
      {templateUrl && <img className="dw-template absolute" src={templateUrl} alt="" draggable={false} />}
      {layers.map((layer) =>
        layerItems[layer.id] ? (
          <LayerCanvas key={layer.id} items={layerItems[layer.id]} color={layer.color} width={width} height={height} />
        ) : null
      )}
      {children}
    </button>
  );
}

export type OpenSheet = { kind: "current" } | { kind: "closed"; sheetId: number };

const NO_ITEMS: DrawItem[] = [];

const selectionKey = (clientId: number) => `dw-template-${clientId}`;

const readSelection = (clientId: number): number | null => {
  try {
    const value = Number(sessionStorage.getItem(selectionKey(clientId)));
    return Number.isFinite(value) && value > 0 ? value : null;
  } catch {
    return null;
  }
};

export const writeDrawingSelection = (clientId: number, templateId: number) => {
  try {
    sessionStorage.setItem(selectionKey(clientId), String(templateId));
  } catch {
    return;
  }
};

const sheetCount = (group: DrawingGroup): number => (group.currentSheet ? 1 : 0) + group.closedSheets.length;

const hasDrawing = (group: DrawingGroup): boolean =>
  (group.currentSheet?.layers.length ?? 0) > 0 || group.closedSheets.length > 0;

interface TemplateSegmentProps {
  group: DrawingGroup;
  active: boolean;
  onSelect: (templateId: number) => void;
}

function TemplateSegment({ group, active, onSelect }: TemplateSegmentProps) {
  const count = sheetCount(group);
  return (
    <button
      type="button"
      className={`cat-seg align-items-center pointer ${active ? "active" : ""} ${
        group.template.archived ? "dw-seg-archived" : ""
      }`}
      aria-pressed={active}
      onClick={() => onSelect(group.template.id)}
    >
      {group.template.archived && <Archive className="dl-icon" />}
      {group.template.archived ? <span>{group.template.name}</span> : group.template.name}
      {count > 0 && <span className="dw-seg-count">{count}</span>}
    </button>
  );
}

function DrawingEmptyState() {
  const { isCompany } = useRoles();
  const navigate = useNavigate();
  return (
    <div className="dw-empty flex-column align-items-center justify-center g-075 text-align-center">
      <span className="dw-empty-icon flex align-items-center justify-center">
        <X className="dl-icon-1" />
      </span>
      <span className="dw-empty-title">Brak szablonów rysunku</span>
      {isCompany && (
        <>
          <span className="dw-empty-text">Dodaj szablon w ustawieniach.</span>
          <button
            type="button"
            className="dw-empty-link pointer"
            onClick={() => navigate("/my-company", { state: { module: "CompanySettings", settingsTab: "card" } })}
          >
            Przejdź do ustawień
          </button>
        </>
      )}
    </div>
  );
}

interface ClosedSheetsProps {
  clientId: number;
  group: DrawingGroup;
  onOpen: (sheetId: number) => void;
}

function ClosedSheets({ clientId, group, onOpen }: ClosedSheetsProps) {
  return (
    <div className="dw-closed flex-column g-05">
      <span className="dw-label">{`Poprzednie kartki - ${group.template.name} (tylko podgląd)`}</span>
      <div className="dw-closed-grid">
        {group.closedSheets.map((sheet) => (
          <div className="dw-closed-item flex-column g-025" key={sheet.id}>
            <SheetPreview
              clientId={clientId}
              sheetId={sheet.id}
              layers={sheet.layers}
              templateImageId={sheet.templateImageId}
              width={sheet.width}
              height={sheet.height}
              onClick={() => onOpen(sheet.id)}
            />
            <span className="dw-closed-meta text-align-center">
              {`Kartka ${sheet.sheetNumber}`}
              {sheet.closedAt ? ` · ${formatDate(sheet.closedAt)}` : ""}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export interface DrawingOverviewProps {
  clientId: number;
  onOpen: (templateId: number, sheet: OpenSheet) => void;
}

const hasCurrentDrawing = (group: DrawingGroup): boolean => (group.currentSheet?.layers.length ?? 0) > 0;

export function DrawingOverview({ clientId, onOpen }: DrawingOverviewProps) {
  const [drawing, setDrawing] = useState<ClientDrawing | null>(null);
  const [loadError, setLoadError] = useState<boolean>(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  useEffect(() => {
    ClientCardService.getDrawing(clientId)
      .then(setDrawing)
      .catch((error) => {
        console.error("Error fetching Drawing:", error);
        setLoadError(true);
      });
  }, [clientId]);

  const activeGroups = useMemo(() => drawing?.groups.filter((g) => !g.template.archived) ?? [], [drawing]);
  const group: DrawingGroup | null =
    activeGroups.find((g) => g.template.id === selectedId) ??
    activeGroups.find(hasCurrentDrawing) ??
    activeGroups[0] ??
    null;
  const size = group ? resolveEditableSheetSize(group) : null;
  const imageId = group ? resolveEditableTemplateImageId(group) : null;

  if (loadError) return <span className="cq-empty">Nie udało się wczytać rysunku.</span>;
  if (!drawing) return <span className="cq-empty italic">Wczytywanie rysunku…</span>;
  if (!group || !size) return null;

  const layers = group.currentSheet?.layers ?? [];
  return (
    <div className="dw-current dw-overview flex-column g-05">
      <span className="pf-label">Rysunek:</span>
      {activeGroups.length > 1 && (
        <div className="cat-seg-group dw-template-seg flex flex-wrap align-items-center g-025">
          {activeGroups.map((g) => (
            <TemplateSegment
              key={g.template.id}
              group={g}
              active={g.template.id === group.template.id}
              onSelect={setSelectedId}
            />
          ))}
        </div>
      )}
      <DrawingLegend entries={layers.map(toLegendEntry)} />
      <SheetPreview
        clientId={clientId}
        sheetId={group.currentSheet?.id ?? null}
        layers={layers}
        templateImageId={imageId}
        width={size.width}
        height={size.height}
        className="dw-preview-current"
        onClick={() => onOpen(group.template.id, { kind: "current" })}
      >
        <span className="dw-preview-hint absolute flex align-items-center g-025">
          <PencilLine className="dl-icon" />
          Otwórz rysunek
        </span>
      </SheetPreview>
      {group.closedSheets.length > 0 && (
        <ClosedSheets
          clientId={clientId}
          group={group}
          onOpen={(sheetId) => onOpen(group.template.id, { kind: "closed", sheetId })}
        />
      )}
    </div>
  );
}

export function DrawingTab({ clientId, readOnly, onDirtyChange, openEditorOnLoad = null, onEditorOpened }: DrawingTabProps) {
  const { showAlert } = useAlert();
  const [drawing, setDrawing] = useState<ClientDrawing | null>(null);
  const [loadError, setLoadError] = useState<boolean>(false);
  const [selectedId, setSelectedId] = useState<number | null>(() => readSelection(clientId));
  const [category, setCategory] = useState<"active" | "archived">("active");
  const [openSheet, setOpenSheet] = useState<OpenSheet | null>(null);
  const [editorItems, setEditorItems] = useState<Record<number, DrawItem[]>>({});
  const [markers, setMarkers] = useState<DrawingMarker[]>([]);
  const [confirmNewSheet, setConfirmNewSheet] = useState<boolean>(false);
  const [creating, setCreating] = useState<boolean>(false);
  const { theme: cardTheme } = useCardTheme();
  useLightPortals(cardTheme === "LIGHT" && confirmNewSheet);

  const load = useCallback(() => {
    setLoadError(false);
    return ClientCardService.getDrawing(clientId)
      .then(setDrawing)
      .catch((error) => {
        console.error("Error fetching Drawing:", error);
        setLoadError(true);
      });
  }, [clientId]);

  useEffect(() => {
    load();
  }, [load]);

  const activeGroups = useMemo(() => drawing?.groups.filter((g) => !g.template.archived) ?? [], [drawing]);
  const archivedGroups = useMemo(() => drawing?.groups.filter((g) => g.template.archived) ?? [], [drawing]);
  const categoryGroups = category === "archived" ? archivedGroups : activeGroups;
  const group: DrawingGroup | null =
    categoryGroups.find((g) => g.template.id === selectedId) ??
    categoryGroups.find(hasDrawing) ??
    categoryGroups[0] ??
    null;
  const groupArchived = group?.template.archived ?? false;

  useEffect(() => {
    if (category === "archived" && drawing && archivedGroups.length === 0) setCategory("active");
  }, [category, drawing, archivedGroups.length]);

  const selectTemplate = useCallback(
    (templateId: number) => {
      setSelectedId(templateId);
      writeDrawingSelection(clientId, templateId);
    },
    [clientId]
  );

  const me = drawing?.currentUser ?? null;
  const current = group?.currentSheet ?? null;
  const currentLayers = current?.layers ?? [];
  const editableSize = group ? resolveEditableSheetSize(group) : null;
  const editableImageId = group ? resolveEditableTemplateImageId(group) : null;
  const closedSheet =
    openSheet?.kind === "closed" ? group?.closedSheets.find((s) => s.id === openSheet.sheetId) ?? null : null;
  const openLayers = openSheet?.kind === "closed" ? closedSheet?.layers ?? [] : currentLayers;
  const editorTemplateUrl = useTemplateUrl(
    openSheet ? (openSheet.kind === "closed" ? closedSheet?.templateImageId ?? null : editableImageId) : null
  );
  const sheetReadOnly = readOnly || groupArchived;

  const openEditor = useCallback(
    async (target: OpenSheet) => {
      if (!group) return;
      const sheet =
        target.kind === "closed" ? group.closedSheets.find((s) => s.id === target.sheetId) ?? null : current;
      setEditorItems({});
      const markersRequest = ClientCardService.getDrawingMarkers().catch((error) => {
        console.error("Error fetching Drawing markers:", error);
        return [] as DrawingMarker[];
      });
      if (sheet && sheet.layers.length > 0) {
        try {
          setEditorItems(await loadSheetItems(clientId, sheet.id, sheet.layers));
        } catch {
          showAlert("Nie udało się wczytać rysunku.", AlertType.ERROR);
          return;
        }
      }
      if (sheet) {
        ClientCardService.logDrawingSheetView(clientId, sheet.id).catch((error) => {
          console.error("Error logging Drawing sheet view:", error);
        });
      }
      setMarkers(await markersRequest);
      setOpenSheet(target);
    },
    [group, current, clientId, showAlert]
  );

  useEffect(() => {
    if (!openEditorOnLoad || !drawing || !group || category !== "active") return;
    onEditorOpened?.();
    openEditor(openEditorOnLoad);
  }, [openEditorOnLoad, drawing, group, category, openEditor, onEditorOpened]);

  const closeEditor = useCallback(() => {
    setOpenSheet(null);
    setEditorItems({});
  }, []);

  const handleSave = useCallback(
    async (items: StoredDrawItem[]): Promise<boolean> => {
      if (!me || !group) return false;
      const ownLayer = current?.layers.find((layer) => layer.authorUserId === me.userId) ?? null;
      try {
        const saved = current
          ? await ClientCardService.saveDrawingLayerItems(clientId, current.id, me.userId, items, ownLayer?.version ?? 0)
          : await ClientCardService.createDrawingSheet(clientId, group.template.id, items, null);
        setDrawing(saved);
        showAlert("Rysunek zapisany!", AlertType.SUCCESS);
        return true;
      } catch (error) {
        console.error("Error saving Drawing:", error);
        if (getDrawingConflict(error)) {
          showAlert(
            "Rysunek został zmieniony na innym urządzeniu. Odrzuć zmiany i otwórz kartkę ponownie.",
            AlertType.ERROR
          );
        } else {
          showAlert(apiErrorMessage(error) ?? "Nie zapisano rysunku. Spróbuj ponownie.", AlertType.ERROR);
        }
        return false;
      }
    },
    [me, group, current, clientId, showAlert]
  );

  const handleNewSheet = useCallback(async () => {
    if (!current || !group) return;
    setCreating(true);
    try {
      const saved = await ClientCardService.createDrawingSheet(clientId, group.template.id, null, current.id);
      setDrawing(saved);
      showAlert("Dodano czystą kartę rysunku.", AlertType.SUCCESS);
    } catch (error) {
      console.error("Error creating Drawing sheet:", error);
      showAlert(apiErrorMessage(error) ?? "Nie udało się dodać nowej kartki.", AlertType.ERROR);
      load();
    } finally {
      setCreating(false);
      setConfirmNewSheet(false);
    }
  }, [clientId, group, current, showAlert, load]);

  if (loadError) {
    return (
      <div className="cc-empty flex-column align-items-center g-05">
        <span>Nie udało się wczytać rysunku.</span>
        <ActionButton text="Spróbuj ponownie" onClick={() => load()}>
          <RotateCcw className="dl-icon-1" />
        </ActionButton>
      </div>
    );
  }
  if (!drawing || !me) {
    return <span className="cq-empty italic">Wczytywanie rysunku…</span>;
  }
  const templateNav = drawing.groups.length > 0 && (
    <div className="dw-templates flex-column g-05">
      <div className="cat-seg-group dw-template-seg flex flex-wrap align-items-center g-025">
        {activeGroups.length > 0 ? (
          <>
            <span className="cat-seg-label">Szablony:</span>
            {activeGroups.map((g) => (
              <TemplateSegment
                key={g.template.id}
                group={g}
                active={category === "active" && g.template.id === group?.template.id}
                onSelect={(id) => {
                  setCategory("active");
                  selectTemplate(id);
                }}
              />
            ))}
          </>
        ) : (
          <button
            type="button"
            className={`cat-seg align-items-center pointer ${category === "active" ? "active" : ""}`}
            aria-pressed={category === "active"}
            onClick={() => setCategory("active")}
          >
            Dostępne szablony
          </button>
        )}
        {archivedGroups.length > 0 && (
          <button
            type="button"
            className={`cat-seg dw-archived-seg align-items-center pointer ${category === "archived" ? "active" : ""}`}
            aria-pressed={category === "archived"}
            onClick={() => setCategory(category === "archived" && activeGroups.length > 0 ? "active" : "archived")}
          >
            <Archive className="dl-icon" />
            Zarchiwizowane szablony
          </button>
        )}
      </div>
      {category === "archived" && archivedGroups.length > 0 && (
        <div className="cat-seg-group dw-template-seg flex flex-wrap align-items-center g-025">
          {archivedGroups.map((g) => (
            <TemplateSegment
              key={g.template.id}
              group={g}
              active={g.template.id === group?.template.id}
              onSelect={selectTemplate}
            />
          ))}
        </div>
      )}
    </div>
  );

  if (!group || !editableSize) {
    return (
      <div className="dw-tab flex-column g-1">
        {templateNav}
        <DrawingEmptyState />
      </div>
    );
  }

  const templateName = group.template.name;
  const isClosedView = openSheet?.kind === "closed";
  const ownLayer = openLayers.find((layer) => layer.authorUserId === me.userId) ?? null;
  const otherLayers: DrawingOtherLayer[] = openLayers
    .filter((layer) => layer.id !== ownLayer?.id && editorItems[layer.id])
    .map((layer) => ({ layer, items: editorItems[layer.id] }));
  const editorTitle = isClosedView
    ? `${templateName} - kartka ${closedSheet?.sheetNumber ?? ""} (zamknięta)`
    : `${templateName} - kartka ${current?.sheetNumber ?? 1}`;
  const canAddSheet = !!current && currentLayers.length > 0 && !creating;

  return (
    <div className="dw-tab flex-column g-1">
      {templateNav}

      {groupArchived && (
        <div className="dw-archived-note flex align-items-center g-05">
          <Archive className="dl-icon-1" />
          <span>Szablon zarchiwizowany. Kartki są tylko do podglądu.</span>
        </div>
      )}

      <div className="dw-current flex-column g-05">
        <div className="flex align-items-center space-between g-1 flex-wrap">
          <span className="dw-label">{`${templateName} - kartka ${current?.sheetNumber ?? 1}`}</span>
          {!sheetReadOnly && (
            <ActionButton
              text="Dodaj czystą kartę"
              onClick={() => setConfirmNewSheet(true)}
              disabled={!canAddSheet}
              className="dw-new-sheet"
            >
              <FilePlus2 className="dl-icon-1" />
            </ActionButton>
          )}
        </div>
        <DrawingLegend entries={currentLayers.map(toLegendEntry)} />
        <SheetPreview
          clientId={clientId}
          sheetId={current?.id ?? null}
          layers={currentLayers}
          templateImageId={editableImageId}
          width={editableSize.width}
          height={editableSize.height}
          className="dw-preview-current"
          onClick={() => openEditor({ kind: "current" })}
        >
          <span className="dw-preview-hint absolute flex align-items-center g-025">
            <PencilLine className="dl-icon" />
            {sheetReadOnly ? "Podgląd" : "Kliknij, aby rysować"}
          </span>
        </SheetPreview>
      </div>

      {group.closedSheets.length > 0 && (
        <ClosedSheets
          clientId={clientId}
          group={group}
          onOpen={(sheetId) => openEditor({ kind: "closed", sheetId })}
        />
      )}

      {openSheet && (
        <DrawingEditor
          title={editorTitle}
          width={isClosedView && closedSheet ? closedSheet.width : editableSize.width}
          height={isClosedView && closedSheet ? closedSheet.height : editableSize.height}
          templateUrl={editorTemplateUrl}
          ownName={me.name}
          ownItems={ownLayer ? editorItems[ownLayer.id] ?? NO_ITEMS : NO_ITEMS}
          otherLayers={otherLayers}
          markers={markers}
          readOnly={sheetReadOnly || isClosedView}
          footerText={
            isClosedView && closedSheet
              ? [
                  closedSheet.createdAt && `Otwarta ${formatDate(closedSheet.createdAt)}`,
                  closedSheet.closedAt && `Zarchiwizowana ${formatDate(closedSheet.closedAt)}`,
                ]
                  .filter(Boolean)
                  .join(" · ") || null
              : null
          }
          onClose={closeEditor}
          onSave={handleSave}
          onDirtyChange={onDirtyChange}
        />
      )}

      {confirmNewSheet && (
        <Modal
          title="Dodać czystą kartę rysunku?"
          onClose={() => setConfirmNewSheet(false)}
          size="sm"
          footer={
            <>
              <ActionButton text="Wróć" onClick={() => setConfirmNewSheet(false)} className="cc-back-btn" />
              <ActionButton
                text="Dodaj kartę"
                onClick={handleNewSheet}
                disabled={creating}
                className="cl-filter cc-save-btn active"
              >
                <FilePlus2 className="dl-icon-1 c-green" />
              </ActionButton>
            </>
          }
        >
          <span>
            {`Bieżąca kartka szablonu „${templateName}” zostanie zamknięta i będzie dostępna już tylko do podglądu. Rysowanie będzie kontynuowane na nowej, czystej kartce.`}
          </span>
        </Modal>
      )}
    </div>
  );
}

export default DrawingTab;
