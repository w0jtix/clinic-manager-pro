import {
  QuestionnaireAnswer,
  QuestionnaireConflictError,
  QuestionnaireFieldInput,
  QuestionnaireFieldSetting,
  QuestionnaireFieldType,
  QuestionnaireItem,
} from "../models/questionnaire";

export const QUESTIONNAIRE_LABEL_MAX_LENGTH = 300;
export const QUESTIONNAIRE_TEXT_MAX_LENGTH = 2000;
export const QUESTIONNAIRE_MAX_FIELDS = 150;

export type QuestionnaireLanguage = "pl" | "en";

export interface EditableQuestionnaireField extends QuestionnaireFieldInput {
  key: string;
  hasAnswers: boolean;
}

export interface QuestionnaireSection {
  title: QuestionnaireItem | null;
  items: QuestionnaireItem[];
}

let localKeySequence = 0;

export const nextLocalFieldKey = (): string => `new-${++localKeySequence}`;

export const isSection = (field: { type: QuestionnaireFieldType }): boolean =>
  field.type === QuestionnaireFieldType.SECTION;

export const toEditableFields = (fields: QuestionnaireFieldSetting[]): EditableQuestionnaireField[] =>
  [...fields]
    .sort((a, b) => Number(a.archived) - Number(b.archived) || a.displayOrder - b.displayOrder)
    .map((field) => ({
      key: `field-${field.id}`,
      id: field.id,
      label: field.label,
      labelEn: field.labelEn,
      type: field.type,
      archived: field.archived,
      hasAnswers: field.hasAnswers,
    }));

export const toFieldInputs = (fields: EditableQuestionnaireField[]): QuestionnaireFieldInput[] =>
  [...fields.filter((f) => !f.archived), ...fields.filter((f) => f.archived)].map((field) => ({
    id: field.id,
    label: field.label.trim(),
    labelEn: field.labelEn?.trim() || null,
    type: field.type,
    archived: field.archived,
  }));

export const isSameFieldList = (
  a: EditableQuestionnaireField[],
  b: EditableQuestionnaireField[]
): boolean => JSON.stringify(toFieldInputs(a)) === JSON.stringify(toFieldInputs(b));

const findDuplicate = (values: string[]): string | null => {
  const seen = new Set<string>();
  for (const value of values) {
    const key = value.toLowerCase();
    if (seen.has(key)) return value;
    seen.add(key);
  }
  return null;
};

export const validateQuestionnaireFields = (fields: EditableQuestionnaireField[]): string | null => {
  if (fields.length > QUESTIONNAIRE_MAX_FIELDS) {
    return `Ankieta może mieć maksymalnie ${QUESTIONNAIRE_MAX_FIELDS} elementów (pola i tytuły sekcji)!`;
  }
  const active = fields.filter((f) => !f.archived);
  if (active.some((f) => isSection(f) && f.label.trim() === "")) {
    return "Uzupełnij treść każdego tytułu sekcji!";
  }
  if (active.some((f) => f.label.trim() === "")) {
    return "Uzupełnij treść każdego pola ankiety!";
  }
  if (fields.some((f) => f.label.trim().length > QUESTIONNAIRE_LABEL_MAX_LENGTH)) {
    return `Treść pola ankiety może mieć maksymalnie ${QUESTIONNAIRE_LABEL_MAX_LENGTH} znaków!`;
  }
  if (fields.some((f) => (f.labelEn?.trim().length ?? 0) > QUESTIONNAIRE_LABEL_MAX_LENGTH)) {
    return `Nazwa angielska może mieć maksymalnie ${QUESTIONNAIRE_LABEL_MAX_LENGTH} znaków!`;
  }
  const sections = active.filter(isSection);
  const duplicatedSection = findDuplicate(sections.map((f) => f.label.trim()));
  if (duplicatedSection) {
    return `Tytuł sekcji "${duplicatedSection}" występuje w ankiecie więcej niż raz!`;
  }
  const duplicatedSectionEn = findDuplicate(sections.map((f) => f.labelEn?.trim() ?? "").filter((v) => v !== ""));
  if (duplicatedSectionEn) {
    return `Nazwa angielska "${duplicatedSectionEn}" występuje w ankiecie więcej niż raz!`;
  }
  for (const group of groupFieldsBySection(active)) {
    const where = group.title ? `w sekcji "${group.title.trim()}"` : "przed pierwszym tytułem sekcji";
    const duplicatedQuestion = findDuplicate(group.fields.map((f) => f.label.trim()));
    if (duplicatedQuestion) {
      return `Pole "${duplicatedQuestion}" występuje więcej niż raz ${where}!`;
    }
    const duplicatedEn = findDuplicate(group.fields.map((f) => f.labelEn?.trim() ?? "").filter((v) => v !== ""));
    if (duplicatedEn) {
      return `Nazwa angielska "${duplicatedEn}" występuje więcej niż raz ${where}!`;
    }
  }
  return null;
};

const groupFieldsBySection = (
  active: EditableQuestionnaireField[]
): { title: string | null; fields: EditableQuestionnaireField[] }[] => {
  const groups: { title: string | null; fields: EditableQuestionnaireField[] }[] = [{ title: null, fields: [] }];
  for (const field of active) {
    if (isSection(field)) {
      groups.push({ title: field.label, fields: [] });
    } else {
      groups[groups.length - 1].fields.push(field);
    }
  }
  return groups;
};

export const toAnswers = (items: QuestionnaireItem[]): QuestionnaireAnswer[] =>
  items
    .filter((item) => !item.archived && !isSection(item))
    .map((item) =>
      item.type === QuestionnaireFieldType.CHECKBOX
        ? { fieldId: item.fieldId, checked: !!item.checked, text: null }
        : { fieldId: item.fieldId, checked: null, text: item.text?.trim() || null }
    );

export const isSameAnswers = (a: QuestionnaireItem[], b: QuestionnaireItem[]): boolean =>
  JSON.stringify(toAnswers(a)) === JSON.stringify(toAnswers(b));

export const validateQuestionnaireAnswers = (items: QuestionnaireItem[]): string | null =>
  items.some((item) => !item.archived && (item.text?.length ?? 0) > QUESTIONNAIRE_TEXT_MAX_LENGTH)
    ? `Odpowiedź może mieć maksymalnie ${QUESTIONNAIRE_TEXT_MAX_LENGTH} znaków!`
    : null;

export const groupIntoSections = (items: QuestionnaireItem[]): QuestionnaireSection[] => {
  const sections: QuestionnaireSection[] = [];
  let current: QuestionnaireSection = { title: null, items: [] };
  for (const item of items) {
    if (isSection(item)) {
      if (current.items.length > 0) sections.push(current);
      current = { title: item, items: [] };
    } else {
      current.items.push(item);
    }
  }
  if (current.items.length > 0) sections.push(current);
  return sections;
};

export const getItemLabel = (
  item: { label: string; labelEn: string | null },
  language: QuestionnaireLanguage
): string => (language === "en" && item.labelEn?.trim() ? item.labelEn : item.label);

export const getQuestionnaireConflict = (error: unknown): QuestionnaireConflictError | null => {
  const response = (error as { response?: { status?: number; data?: unknown } } | null)?.response;
  const data = response?.data as Partial<QuestionnaireConflictError> | undefined;
  return response?.status === 409 && data && typeof data === "object" && data.code === "QUESTIONNAIRE_CONFLICT"
    ? (data as QuestionnaireConflictError)
    : null;
};
