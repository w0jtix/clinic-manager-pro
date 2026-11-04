import { useState } from "react";
import { Archive } from "lucide-react";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import { QuestionnaireFieldType, QuestionnaireItem } from "../../models/questionnaire";
import {
  getItemLabel,
  groupIntoSections,
  QUESTIONNAIRE_TEXT_MAX_LENGTH,
  QuestionnaireLanguage,
} from "../../utils/questionnaireUtils";
import { formatDate } from "../../utils/dateUtils";

export interface QuestionnaireFormProps {
  items: QuestionnaireItem[];
  language: QuestionnaireLanguage;
  disabled?: boolean;
  onChange: (fieldId: number, patch: Pick<QuestionnaireItem, "checked" | "text">) => void;
}

const ANSWER_LABELS: Record<QuestionnaireLanguage, { no: string; yes: string }> = {
  pl: { no: "NIE", yes: "TAK" },
  en: { no: "NO", yes: "YES" },
};

export const formatUpdatedAt = (value: string): string => {
  const date = new Date(value);
  const time = date.toLocaleTimeString("pl-PL", { hour: "2-digit", minute: "2-digit" });
  return `${formatDate(value)} ${time}`;
};

export interface QuestionnaireLanguageToggleProps {
  language: QuestionnaireLanguage;
  onChange: (language: QuestionnaireLanguage) => void;
}

export function QuestionnaireLanguageToggle({ language, onChange }: QuestionnaireLanguageToggleProps) {
  return (
    <div className="cat-seg-group cq-lang flex align-items-center g-025">
      {(["pl", "en"] as QuestionnaireLanguage[]).map((option) => (
        <button
          key={option}
          type="button"
          className={`cat-seg align-items-center justify-center pointer ${language === option ? "active" : ""}`}
          onClick={() => onChange(option)}
        >
          {option.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

export function QuestionnaireForm({ items, language, disabled = false, onChange }: QuestionnaireFormProps) {
  const [showArchived, setShowArchived] = useState<boolean>(false);
  const lang = language;
  const answerLabels = ANSWER_LABELS[lang];
  const sections = groupIntoSections(items.filter((item) => !item.archived));
  const archived = items.filter((item) => item.archived);

  const renderItem = (item: QuestionnaireItem) =>
    item.type === QuestionnaireFieldType.CHECKBOX ? (
      <div className="cq-row cq-check flex align-items-center space-between g-1" key={item.fieldId}>
        <span className="cq-label">{getItemLabel(item, lang)}</span>
        <div className="cat-seg-group cq-seg flex align-items-center g-025">
          <button
            type="button"
            disabled={disabled}
            className={`cat-seg cl-no align-items-center justify-center pointer ${!item.checked ? "active" : ""}`}
            onClick={() => onChange(item.fieldId, { checked: false, text: null })}
          >
            {answerLabels.no}
          </button>
          <button
            type="button"
            disabled={disabled}
            className={`cat-seg cl-yes align-items-center justify-center pointer ${item.checked ? "active" : ""}`}
            onClick={() => onChange(item.fieldId, { checked: true, text: null })}
          >
            {answerLabels.yes}
          </button>
        </div>
      </div>
    ) : (
      <div className="cq-row cq-text flex-column g-05" key={item.fieldId}>
        <span className="cq-label">{getItemLabel(item, lang)}</span>
        <TextInput
          multiline={true}
          rows={3}
          disabled={disabled}
          className="in-full"
          maxLength={QUESTIONNAIRE_TEXT_MAX_LENGTH}
          value={item.text ?? ""}
          onSelect={(value) => {
            if (typeof value === "string") onChange(item.fieldId, { checked: null, text: value });
          }}
        />
      </div>
    );

  return (
    <div className="cc-form cq-form flex-column g-075">
      {archived.length > 0 && (
        <div className="cq-toolbar flex align-items-center justify-end g-1 flex-wrap">
          <ActionButton
            text="Pokaż zarchiwizowane pola ankiety"
            onClick={() => setShowArchived((prev) => !prev)}
            className={`cl-filter cq-archived-toggle ${showArchived ? "active" : ""}`}
          >
            <Archive className="dl-icon-1 cq-archived-icon" />
          </ActionButton>
        </div>
      )}

      {sections.length === 0 && <span className="cq-empty italic">Ankieta jest pusta.</span>}

      {sections.map((section, index) => (
        <div className="cq-section flex-column g-05" key={section.title?.fieldId ?? `untitled-${index}`}>
          {section.title && <span className="cq-section-title">{getItemLabel(section.title, lang)}</span>}
          <div className="cq-grid">{section.items.map(renderItem)}</div>
        </div>
      ))}

      {showArchived && archived.length > 0 && (
        <div className="cq-archived flex-column g-05">
          <span className="cq-archived-title">Zarchiwizowane</span>
          {archived.map((item) => (
            <div className="cq-archived-row flex align-items-center space-between g-1" key={item.fieldId}>
              <span className="flex-column g-025 mw-0">
                <span className="cq-label">{getItemLabel(item, lang)}</span>
                {item.type !== QuestionnaireFieldType.CHECKBOX && (
                  <span className="cq-archived-value">{item.text}</span>
                )}
                <span className="cq-archived-badge flex align-items-center g-025">
                  <Archive className="dl-icon" />
                  Zarchiwizowane
                </span>
              </span>
              {item.type === QuestionnaireFieldType.CHECKBOX && (
                <div className="cat-seg-group cq-seg cq-archived-answer relative flex align-items-center g-025">
                  <span className="cat-seg" aria-hidden="true">{answerLabels.no}</span>
                  <span className="cat-seg" aria-hidden="true">{answerLabels.yes}</span>
                  <span className="cq-archived-value absolute flex align-items-center justify-center">
                    {item.checked ? answerLabels.yes : answerLabels.no}
                  </span>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default QuestionnaireForm;
