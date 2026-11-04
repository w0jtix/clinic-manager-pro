import { describe, expect, it } from "vitest";
import {
  EditableQuestionnaireField,
  getItemLabel,
  getQuestionnaireConflict,
  groupIntoSections,
  isSameAnswers,
  toAnswers,
  toEditableFields,
  toFieldInputs,
  validateQuestionnaireFields,
} from "../../utils/questionnaireUtils";
import { QuestionnaireFieldType, QuestionnaireItem } from "../../models/questionnaire";

const field = (overrides: Partial<EditableQuestionnaireField>): EditableQuestionnaireField => ({
  key: "k",
  id: null,
  label: "Cukrzyca",
  labelEn: null,
  type: QuestionnaireFieldType.CHECKBOX,
  archived: false,
  hasAnswers: false,
  ...overrides,
});

const item = (overrides: Partial<QuestionnaireItem>): QuestionnaireItem => ({
  fieldId: 1,
  label: "Cukrzyca",
  labelEn: null,
  type: QuestionnaireFieldType.CHECKBOX,
  archived: false,
  checked: null,
  text: null,
  ...overrides,
});

describe("questionnaire fields", () => {
  it("orders active fields before archived ones by display order", () => {
    const editable = toEditableFields([
      { id: 1, label: "A", labelEn: null, type: QuestionnaireFieldType.TEXT, displayOrder: 0, archived: true, hasAnswers: true },
      { id: 2, label: "B", labelEn: null, type: QuestionnaireFieldType.CHECKBOX, displayOrder: 2, archived: false, hasAnswers: false },
      { id: 3, label: "C", labelEn: "C en", type: QuestionnaireFieldType.SECTION, displayOrder: 1, archived: false, hasAnswers: false },
    ]);
    expect(editable.map((f) => f.id)).toEqual([3, 2, 1]);
    expect(editable[0].labelEn).toBe("C en");
  });

  it("sends trimmed labels and empty translations as null, active fields first", () => {
    const inputs = toFieldInputs([
      field({ key: "a", id: 1, label: "Stare", labelEn: "  ", archived: true }),
      field({ key: "b", id: null, label: "  Nowe  ", labelEn: " New " }),
    ]);
    expect(inputs).toEqual([
      { id: null, label: "Nowe", labelEn: "New", type: QuestionnaireFieldType.CHECKBOX, archived: false },
      { id: 1, label: "Stare", labelEn: null, type: QuestionnaireFieldType.CHECKBOX, archived: true },
    ]);
  });

  it("rejects empty and duplicated labels among active fields", () => {
    expect(validateQuestionnaireFields([field({ label: " " })])).not.toBeNull();
    expect(
      validateQuestionnaireFields([field({ key: "a", label: "Cukrzyca" }), field({ key: "b", label: "cukrzyca " })])
    ).not.toBeNull();
    expect(
      validateQuestionnaireFields([
        field({ key: "a", label: "Cukrzyca" }),
        field({ key: "b", label: "Cukrzyca", archived: true, hasAnswers: true }),
      ])
    ).toBeNull();
  });

  it("checks duplicates separately for section titles and fields", () => {
    expect(
      validateQuestionnaireFields([
        field({ key: "a", label: "Inne", type: QuestionnaireFieldType.SECTION }),
        field({ key: "b", label: "Inne" }),
      ])
    ).toBeNull();
    expect(
      validateQuestionnaireFields([
        field({ key: "a", label: "Inne", type: QuestionnaireFieldType.SECTION }),
        field({ key: "b", label: "inne", type: QuestionnaireFieldType.SECTION }),
      ])
    ).toContain("Tytuł sekcji");
    expect(
      validateQuestionnaireFields([
        field({ key: "a", label: "Cukrzyca", labelEn: "Diabetes" }),
        field({ key: "b", label: "Nadciśnienie", labelEn: "diabetes" }),
      ])
    ).toContain("Nazwa angielska");
  });

  it("allows the same field in different sections but not twice in one section", () => {
    const section = (key: string, label: string) => field({ key, label, type: QuestionnaireFieldType.SECTION });
    expect(
      validateQuestionnaireFields([
        section("s1", "Wywiad"),
        field({ key: "a", label: "Brodawki wirusowe", labelEn: "Viral warts" }),
        section("s2", "Badanie podologiczne"),
        field({ key: "b", label: "Brodawki wirusowe", labelEn: "Viral warts" }),
      ])
    ).toBeNull();
    expect(
      validateQuestionnaireFields([
        section("s1", "Wywiad"),
        field({ key: "a", label: "Brodawki wirusowe" }),
        field({ key: "b", label: "brodawki wirusowe" }),
      ])
    ).toBe('Pole "brodawki wirusowe" występuje więcej niż raz w sekcji "Wywiad"!');
  });
});

describe("questionnaire answers", () => {
  it("maps only active non-section items and normalizes empty values", () => {
    const answers = toAnswers([
      item({ fieldId: 9, type: QuestionnaireFieldType.SECTION }),
      item({ fieldId: 1, checked: null }),
      item({ fieldId: 2, type: QuestionnaireFieldType.TEXT, text: "  " }),
      item({ fieldId: 3, type: QuestionnaireFieldType.TEXT, text: " leki " }),
      item({ fieldId: 4, archived: true, checked: true }),
    ]);
    expect(answers).toEqual([
      { fieldId: 1, checked: false, text: null },
      { fieldId: 2, checked: null, text: null },
      { fieldId: 3, checked: null, text: "leki" },
    ]);
  });

  it("treats unanswered checkbox as NIE and ignores surrounding whitespace", () => {
    expect(isSameAnswers([item({ checked: null })], [item({ checked: false })])).toBe(true);
    expect(
      isSameAnswers(
        [item({ type: QuestionnaireFieldType.TEXT, text: "abc " })],
        [item({ type: QuestionnaireFieldType.TEXT, text: "abc" })]
      )
    ).toBe(true);
    expect(isSameAnswers([item({ checked: true })], [item({ checked: false })])).toBe(false);
  });

  it("groups items under section titles and skips empty sections", () => {
    const sections = groupIntoSections([
      item({ fieldId: 1 }),
      item({ fieldId: 2, type: QuestionnaireFieldType.SECTION, label: "Pusta" }),
      item({ fieldId: 3, type: QuestionnaireFieldType.SECTION, label: "Choroby" }),
      item({ fieldId: 4 }),
      item({ fieldId: 5 }),
    ]);
    expect(sections.map((s) => [s.title?.fieldId ?? null, s.items.map((i) => i.fieldId)])).toEqual([
      [null, [1]],
      [3, [4, 5]],
    ]);
  });

  it("falls back to the Polish label when the translation is missing", () => {
    expect(getItemLabel({ label: "Cukrzyca", labelEn: "Diabetes" }, "en")).toBe("Diabetes");
    expect(getItemLabel({ label: "Cukrzyca", labelEn: null }, "en")).toBe("Cukrzyca");
    expect(getItemLabel({ label: "Cukrzyca", labelEn: "Diabetes" }, "pl")).toBe("Cukrzyca");
  });

  it("recognizes the version conflict response", () => {
    expect(
      getQuestionnaireConflict({ response: { status: 409, data: { code: "QUESTIONNAIRE_CONFLICT", currentVersion: 3 } } })
    ).toEqual({ code: "QUESTIONNAIRE_CONFLICT", currentVersion: 3 });
    expect(getQuestionnaireConflict({ response: { status: 409, data: "Client is deleted" } })).toBeNull();
    expect(getQuestionnaireConflict(new Error("network"))).toBeNull();
  });
});
