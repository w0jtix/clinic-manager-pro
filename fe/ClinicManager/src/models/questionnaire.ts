export enum QuestionnaireFieldType {
  CHECKBOX = "CHECKBOX",
  TEXT = "TEXT",
  SECTION = "SECTION",
}

export interface QuestionnaireFieldSetting {
  id: number;
  label: string;
  labelEn: string | null;
  type: QuestionnaireFieldType;
  displayOrder: number;
  archived: boolean;
  hasAnswers: boolean;
}

export interface QuestionnaireFieldInput {
  id: number | null;
  label: string;
  labelEn: string | null;
  type: QuestionnaireFieldType;
  archived: boolean;
}

export interface QuestionnaireSettings {
  englishEnabled: boolean;
  fields: QuestionnaireFieldSetting[];
}

export interface QuestionnaireSettingsInput {
  englishEnabled: boolean;
  fields: QuestionnaireFieldInput[];
}

export interface QuestionnaireItem {
  fieldId: number;
  label: string;
  labelEn: string | null;
  type: QuestionnaireFieldType;
  archived: boolean;
  checked: boolean | null;
  text: string | null;
}

export interface Questionnaire {
  version: number;
  updatedAt: string | null;
  englishEnabled: boolean;
  items: QuestionnaireItem[];
}

export interface QuestionnaireAnswer {
  fieldId: number;
  checked: boolean | null;
  text: string | null;
}

export interface QuestionnaireUpdate {
  expectedVersion: number;
  answers: QuestionnaireAnswer[];
}

export interface QuestionnaireConflictError {
  code: "QUESTIONNAIRE_CONFLICT";
  currentVersion: number;
}
