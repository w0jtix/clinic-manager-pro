import { useCallback, useEffect, useState } from "react";
import { Archive, ChevronDown, ChevronUp, GripVertical, Heading, Plus, RotateCcw, Trash2 } from "lucide-react";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import ToggleButton from "../ToggleButton";
import CardSettingsService from "../../services/CardSettingsService";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { QuestionnaireFieldType, QuestionnaireSettings } from "../../models/questionnaire";
import {
  EditableQuestionnaireField,
  isSameFieldList,
  isSection,
  nextLocalFieldKey,
  QUESTIONNAIRE_LABEL_MAX_LENGTH,
  toEditableFields,
  toFieldInputs,
  validateQuestionnaireFields,
} from "../../utils/questionnaireUtils";

const TYPE_OPTIONS: { type: QuestionnaireFieldType; label: string }[] = [
  { type: QuestionnaireFieldType.CHECKBOX, label: "Tak / Nie" },
  { type: QuestionnaireFieldType.TEXT, label: "Tekst" },
];

export function useQuestionnaireFieldsEditor() {
  const [fields, setFields] = useState<EditableQuestionnaireField[]>([]);
  const [baseFields, setBaseFields] = useState<EditableQuestionnaireField[]>([]);
  const [englishEnabled, setEnglishEnabled] = useState<boolean>(false);
  const [baseEnglishEnabled, setBaseEnglishEnabled] = useState<boolean>(false);
  const [loaded, setLoaded] = useState<boolean>(false);
  const [loadError, setLoadError] = useState<boolean>(false);

  const apply = useCallback((settings: QuestionnaireSettings) => {
    const editable = toEditableFields(settings.fields);
    setFields(editable);
    setBaseFields(editable);
    setEnglishEnabled(settings.englishEnabled);
    setBaseEnglishEnabled(settings.englishEnabled);
  }, []);

  useEffect(() => {
    CardSettingsService.getQuestionnaireSettings()
      .then((data) => {
        apply(data);
        setLoaded(true);
        setLoadError(false);
      })
      .catch((error) => {
        console.error("Error fetching Questionnaire settings:", error);
        setLoadError(true);
      });
  }, [apply]);

  const dirty =
    loaded && (englishEnabled !== baseEnglishEnabled || !isSameFieldList(fields, baseFields));

  const validate = useCallback(() => validateQuestionnaireFields(fields), [fields]);

  const save = useCallback(async () => {
    const saved = await CardSettingsService.updateQuestionnaireSettings({
      englishEnabled,
      fields: toFieldInputs(fields),
    });
    apply(saved);
  }, [fields, englishEnabled, apply]);

  return {
    fields,
    setFields,
    englishEnabled,
    setEnglishEnabled,
    loaded,
    loadError,
    dirty,
    validate,
    save,
  };
}

export type QuestionnaireFieldsEditorState = ReturnType<typeof useQuestionnaireFieldsEditor>;

export interface QuestionnaireFieldsEditorProps {
  editor: QuestionnaireFieldsEditorState;
}

export function QuestionnaireEnglishToggle({ editor }: QuestionnaireFieldsEditorProps) {
  const { englishEnabled, setEnglishEnabled, loaded } = editor;
  if (!loaded) return null;

  return (
    <div className="qf-english flex align-items-center g-1 flex-wrap">
      <ToggleButton
        value={englishEnabled}
        onChange={setEnglishEnabled}
        prefix="Angielska wersja ankiety"
        className="qf-english-toggle"
      />
    </div>
  );
}

export function QuestionnaireFieldsEditor({ editor }: QuestionnaireFieldsEditorProps) {
  const { fields, setFields, englishEnabled, loaded, loadError } = editor;
  const [showArchived, setShowArchived] = useState<boolean>(false);
  const [armedKey, setArmedKey] = useState<string | null>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dropIndex, setDropIndex] = useState<number | null>(null);
  const { isMobile } = useBreakpoint();

  const active = fields.filter((f) => !f.archived);
  const archived = fields.filter((f) => f.archived);

  const updateField = useCallback(
    (key: string, patch: Partial<EditableQuestionnaireField>) => {
      setFields((prev) => prev.map((f) => (f.key === key ? { ...f, ...patch } : f)));
    },
    [setFields]
  );

  const withActiveOrder = useCallback(
    (prev: EditableQuestionnaireField[], nextActive: EditableQuestionnaireField[]) => {
      const activeKeys = new Set(nextActive.map((f) => f.key));
      return [...nextActive, ...prev.filter((f) => f.archived && !activeKeys.has(f.key))];
    },
    []
  );

  const moveField = useCallback(
    (index: number, direction: -1 | 1) => {
      setFields((prev) => {
        const nextActive = prev.filter((f) => !f.archived);
        const target = index + direction;
        if (target < 0 || target >= nextActive.length) return prev;
        [nextActive[index], nextActive[target]] = [nextActive[target], nextActive[index]];
        return withActiveOrder(prev, nextActive);
      });
    },
    [setFields, withActiveOrder]
  );

  const moveFieldTo = useCallback(
    (from: number, to: number) => {
      setFields((prev) => {
        const nextActive = prev.filter((f) => !f.archived);
        if (from === to || to < 0 || to >= nextActive.length) return prev;
        const [moved] = nextActive.splice(from, 1);
        nextActive.splice(to, 0, moved);
        return withActiveOrder(prev, nextActive);
      });
    },
    [setFields, withActiveOrder]
  );

  useEffect(() => {
    if (!armedKey || dragIndex !== null) return;
    const disarm = () => setArmedKey(null);
    window.addEventListener("mouseup", disarm);
    return () => window.removeEventListener("mouseup", disarm);
  }, [armedKey, dragIndex]);

  const resetDrag = useCallback(() => {
    setArmedKey(null);
    setDragIndex(null);
    setDropIndex(null);
  }, []);

  const addItem = useCallback(
    (type: QuestionnaireFieldType) => {
      setFields((prev) =>
        withActiveOrder(prev, [
          ...prev.filter((f) => !f.archived),
          {
            key: nextLocalFieldKey(),
            id: null,
            label: "",
            labelEn: null,
            type,
            archived: false,
            hasAnswers: false,
          },
        ])
      );
    },
    [setFields, withActiveOrder]
  );

  const removeField = useCallback(
    (field: EditableQuestionnaireField) => {
      if (field.hasAnswers) {
        updateField(field.key, { archived: true });
      } else {
        setFields((prev) => prev.filter((f) => f.key !== field.key));
      }
    },
    [setFields, updateField]
  );

  const restoreField = useCallback(
    (field: EditableQuestionnaireField) => {
      setFields((prev) =>
        withActiveOrder(prev, [...prev.filter((f) => !f.archived), { ...field, archived: false }])
      );
    },
    [setFields, withActiveOrder]
  );

  if (loadError) {
    return <span className="st-card-desc c-red">Nie udało się wczytać ustawień ankiety.</span>;
  }
  if (!loaded) {
    return <span className="st-card-desc">Wczytywanie…</span>;
  }

  return (
    <div className="qf-editor flex-column g-075">
      {active.length === 0 ? (
        <span className="qf-empty st-card-desc italic">
          Brak pól ankiety. Dodaj pierwsze pole albo tytuł sekcji.
        </span>
      ) : (
        <div className={`qf-table flex-column g-05 ${englishEnabled ? "qf-en" : ""}`}>
          <div className="qf-head">
            <span />
            <span>Treść</span>
            {englishEnabled && <span>Nazwa po angielsku</span>}
            <span>Typ odpowiedzi</span>
            <span />
          </div>
          {active.map((field, index) => {
            const section = isSection(field);
            return (
              <div
                key={field.key}
                className={`qf-row ${section ? "qf-section" : ""} ${dragIndex === index ? "dl-row-dragging" : ""} ${
                  dropIndex === index && dragIndex !== null && dragIndex !== index
                    ? dragIndex < index
                      ? "dl-row-drop-below"
                      : "dl-row-drop-above"
                    : ""
                }`}
                draggable={!isMobile && armedKey === field.key}
                onDragStart={(e) => {
                  e.dataTransfer.effectAllowed = "move";
                  e.dataTransfer.setData("text/plain", field.key);
                  setDragIndex(index);
                }}
                onDragOver={(e) => {
                  if (dragIndex === null) return;
                  e.preventDefault();
                  if (index !== dropIndex) setDropIndex(index);
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  if (dragIndex !== null) moveFieldTo(dragIndex, index);
                  resetDrag();
                }}
                onDragEnd={resetDrag}
              >
                {!isMobile ? (
                  <div
                    className="qf-order qf-drag flex align-items-center justify-center"
                    title="Przeciągnij, aby zmienić kolejność"
                    onMouseDown={() => setArmedKey(field.key)}
                    onMouseUp={() => setArmedKey(null)}
                  >
                    <GripVertical className="dl-icon sv-drag-handle" />
                  </div>
                ) : (
                <div className="qf-order flex align-items-center">
                  <ActionButton
                    iconTitle="Przesuń wyżej"
                    disableText={true}
                    disabled={index === 0}
                    className="qf-move"
                    onClick={() => moveField(index, -1)}
                  >
                    <ChevronUp className="dl-icon" />
                  </ActionButton>
                  <ActionButton
                    iconTitle="Przesuń niżej"
                    disableText={true}
                    disabled={index === active.length - 1}
                    className="qf-move"
                    onClick={() => moveField(index, 1)}
                  >
                    <ChevronDown className="dl-icon" />
                  </ActionButton>
                </div>
                )}
                <div className="qf-label">
                  <TextInput
                    dropdown={false}
                    className="in-full"
                    placeholder={section ? "Tytuł sekcji, np. Choroby i przeciwwskazania" : "np. Cukrzyca"}
                    maxLength={QUESTIONNAIRE_LABEL_MAX_LENGTH}
                    value={field.label}
                    onSelect={(value) => {
                      if (typeof value === "string") updateField(field.key, { label: value });
                    }}
                  />
                </div>
                {englishEnabled && (
                  <div className="qf-label-en">
                    <TextInput
                      dropdown={false}
                      className="in-full"
                      placeholder={section ? "Section title" : "e.g. Diabetes"}
                      maxLength={QUESTIONNAIRE_LABEL_MAX_LENGTH}
                      value={field.labelEn ?? ""}
                      onSelect={(value) => {
                        if (typeof value === "string") updateField(field.key, { labelEn: value });
                      }}
                    />
                  </div>
                )}
                {section ? (
                  <div className="qf-type qf-section-tag flex align-items-center">
                    <span>Tytuł sekcji</span>
                  </div>
                ) : (
                  <div
                    className="qf-type cat-seg-group flex align-items-center g-025"
                    title={field.hasAnswers ? "Pole ma odpowiedzi w kartach - typu nie można zmienić" : undefined}
                  >
                    {TYPE_OPTIONS.map((option) => (
                      <button
                        key={option.type}
                        type="button"
                        disabled={field.hasAnswers}
                        className={`cat-seg align-items-center justify-center pointer ${field.type === option.type ? "active" : ""}`}
                        onClick={() => updateField(field.key, { type: option.type })}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                )}
                <div className="qf-action flex justify-end">
                  {field.hasAnswers ? (
                    <ActionButton
                      iconTitle="Archiwizuj pole - odpowiedzi zostaną w kartach"
                      disableText={true}
                      disableBorder={true}
                      onClick={() => removeField(field)}
                    >
                      <Archive className="dl-icon pc-y" />
                    </ActionButton>
                  ) : (
                    <ActionButton
                      iconTitle={section ? "Usuń tytuł sekcji" : "Usuń pole"}
                      disableText={true}
                      disableBorder={true}
                      onClick={() => removeField(field)}
                    >
                      <Trash2 className="dl-icon c-red" />
                    </ActionButton>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className="flex align-items-center space-between g-1 flex-wrap">
        <div className="flex align-items-center g-075 flex-wrap">
          <ActionButton text="Dodaj pole" onClick={() => addItem(QuestionnaireFieldType.CHECKBOX)}>
            <Plus className="dl-icon-1 pc-y" />
          </ActionButton>
          <ActionButton text="Dodaj tytuł sekcji" onClick={() => addItem(QuestionnaireFieldType.SECTION)}>
            <Heading className="dl-icon-1 pc-y" />
          </ActionButton>
        </div>
        {archived.length > 0 && (
          <ActionButton
            text={`Zarchiwizowane pola (${archived.length})`}
            disableBorder={true}
            onClick={() => setShowArchived((prev) => !prev)}
          >
            {showArchived ? <ChevronUp className="dl-icon-1" /> : <ChevronDown className="dl-icon-1" />}
          </ActionButton>
        )}
      </div>

      {showArchived && archived.length > 0 && (
        <div className="qf-archived flex-column g-05">
          {archived.map((field) => (
            <div className="qf-archived-row flex align-items-center space-between g-1" key={field.key}>
              <span className="flex-column mw-0">
                <span className="qf-archived-label">{field.label}</span>
                <span className="qf-archived-type">
                  {TYPE_OPTIONS.find((o) => o.type === field.type)?.label}
                  {englishEnabled && field.labelEn ? ` · ${field.labelEn}` : ""}
                </span>
              </span>
              <ActionButton text="Przywróć" onClick={() => restoreField(field)}>
                <RotateCcw className="dl-icon-1 c-green" />
              </ActionButton>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default QuestionnaireFieldsEditor;
