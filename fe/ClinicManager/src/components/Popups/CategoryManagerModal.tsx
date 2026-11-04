import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Lock, Plus, RotateCcw, SquarePen, Trash2 } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import RemoveModal from "./RemoveModal";
import CategoryModal from "./CategoryModal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import {
  BaseServiceCategory,
  CategoryUsage,
  NewBaseServiceCategory,
  NewProductCategory,
  ProductCategory,
} from "../../models/categories";
import CategoryService from "../../services/CategoryService";
import BaseServiceCategoryService from "../../services/BaseServiceCategoryService";
import ExpenseCategoryService from "../../services/ExpenseCategoryService";
import { ExpenseCategoryDTO } from "../../models/expense";
import { reloadExpenseCategories } from "../../hooks/useExpenseCategories";
import { validateCategoryForm } from "../../utils/validators";
import { extractCategoryErrorMessage } from "../../utils/errorHandler";
import { apiErrorMessage } from "../../utils/apiError";

export type CategoryKind = "product" | "service" | "expense";

type ManagedCategory = ProductCategory | BaseServiceCategory | ExpenseCategoryDTO;
type CategoryFormValue = ManagedCategory | NewProductCategory | NewBaseServiceCategory;
type EditTarget = { kind: "new" } | { kind: "edit"; category: ManagedCategory };

const KIND_CONFIG = {
  product: {
    title: "Kategorie produktów",
    forms: ["produkt", "produkty", "produktów"],
    showUsage: true,
    systemNote:
      "Kategoria systemowa: tu trafiają produkty, które sprzedajesz klientom. Służy do śledzenia stanów magazynowych i statystyk sprzedaży. Możesz zmienić nazwę i kolor, ale nie można jej usunąć.",
    api: CategoryService,
  },
  service: {
    title: "Kategorie usług",
    forms: ["usługa", "usługi", "usług"],
    showUsage: true,
    systemNote: "",
    api: BaseServiceCategoryService,
  },
  expense: {
    title: "Kategorie wydatków",
    forms: ["wydatek", "wydatki", "wydatków"],
    showUsage: false,
    systemNote: "",
    api: ExpenseCategoryService,
  },
} as const;

const pluralForm = (count: number, forms: readonly [string, string, string] | readonly string[]): string => {
  if (count === 1) return forms[0];
  const lastDigit = count % 10;
  const lastTwo = count % 100;
  return lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14) ? forms[1] : forms[2];
};

const SYSTEM_EDIT_RULE = "Możesz zmienić nazwę i kolor, ale nie można jej usunąć.";

const EXPENSE_SYSTEM_NOTES: Record<string, string> = {
  RENT: "Kategoria systemowa: czynsz za lokal. Szablon podpowiada kwotę z ostatniego wydatku.",
  TAXES: "Kategoria systemowa: szablon uzupełnia pozycje, a źródłem jest zawsze Urząd Skarbowy, bez sprzedawcy.",
  ZUS: "Kategoria systemowa: składki ZUS. Szablon podpowiada kwotę z ostatniego wydatku, źródło jest stałe, bez sprzedawcy.",
  SALARY: "Kategoria systemowa: wypłaty pracowników (wypłata, premia, premia sprzedażowa). Wydatek jest przypisany do pracownika i trafia do jego historii wypłat.",
  PRODUCTS: "Kategoria systemowa: zakupy produktów do magazynu. Pozycje są powiązane z produktami, a faktury KSeF z tą kategorią czekają na weryfikację produktów i aktualizują ceny zakupu.",
  UNRELATED: "Kategoria systemowa: wydatki niezwiązane z działalnością gabinetu. Są pomijane w statystykach.",
};

const isSystem = (category: ManagedCategory): boolean => "system" in category && !!category.system;

const isInternal = (category: ManagedCategory): boolean => "internal" in category && !!category.internal;

export interface CategoryManagerModalProps {
  kind: CategoryKind;
  onClose: () => void;
  onChanged: () => void;
}

export function CategoryManagerModal({ kind, onClose, onChanged }: CategoryManagerModalProps) {
  const { showAlert } = useAlert();
  const config = KIND_CONFIG[kind];
  const [allCategories, setAllCategories] = useState<ManagedCategory[] | null>(null);
  const [usage, setUsage] = useState<Record<number, number>>({});
  const [loadError, setLoadError] = useState<boolean>(false);
  const [editTarget, setEditTarget] = useState<EditTarget | null>(null);
  const [removeTarget, setRemoveTarget] = useState<ManagedCategory | null>(null);
  const busyRef = useRef<boolean>(false);

  const categories = useMemo(() => {
    if (!allCategories) return null;
    const visible = allCategories.filter((category) => !isInternal(category));
    return kind === "expense"
      ? [...visible.filter(isSystem), ...visible.filter((category) => !isSystem(category))]
      : visible;
  }, [allCategories, kind]);

  const load = useCallback(async () => {
    setLoadError(false);
    try {
      const [list, counts] = await Promise.all([config.api.getCategories(), config.api.getUsage()]);
      setAllCategories((list ?? []) as ManagedCategory[]);
      setUsage(Object.fromEntries((counts ?? []).map((item: CategoryUsage) => [item.categoryId, item.count])));
    } catch (error) {
      console.error("Error fetching Categories:", error);
      setLoadError(true);
    }
  }, [config]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = useCallback(() => {
    load();
    if (kind === "expense") reloadExpenseCategories();
    onChanged();
  }, [load, kind, onChanged]);

  const handleSave = useCallback(
    async (form: CategoryFormValue) => {
      if (busyRef.current) return;
      const editing = editTarget?.kind === "edit" ? editTarget.category : null;
      const action = editing ? Action.EDIT : Action.CREATE;
      const error = validateCategoryForm(form, editing, action, allCategories ?? []);
      if (error) {
        showAlert(error, AlertType.ERROR);
        return;
      }
      const payload = { name: form.name.trim(), color: form.color };
      busyRef.current = true;
      try {
        if (editing) {
          await config.api.updateCategory(editing.id, payload);
          showAlert(`Kategoria ${payload.name} zaktualizowana!`, AlertType.SUCCESS);
        } else {
          const saved = await config.api.createCategory(payload);
          showAlert(
            saved && "restored" in saved && saved.restored ? `Przywrócono kategorię „${saved.name}”.` : `Kategoria ${payload.name} utworzona!`,
            AlertType.SUCCESS
          );
        }
        setEditTarget(null);
        refresh();
      } catch (saveError) {
        console.error("Error saving Category:", saveError);
        showAlert(apiErrorMessage(saveError) ?? extractCategoryErrorMessage(saveError, action), AlertType.ERROR);
      } finally {
        busyRef.current = false;
      }
    },
    [editTarget, allCategories, config, showAlert, refresh]
  );

  const handleRemove = useCallback(async () => {
    if (!removeTarget || busyRef.current) return;
    busyRef.current = true;
    try {
      await config.api.deleteCategory(removeTarget.id);
      showAlert(`Kategoria ${removeTarget.name} usunięta.`, AlertType.SUCCESS);
      setRemoveTarget(null);
      refresh();
    } catch (removeError) {
      console.error("Error removing Category:", removeError);
      showAlert(apiErrorMessage(removeError) ?? "Nie udało się usunąć kategorii.", AlertType.ERROR);
    } finally {
      busyRef.current = false;
    }
  }, [removeTarget, config, showAlert, refresh]);

  const systemNoteOf = (category: ManagedCategory): string =>
    kind === "expense" && "code" in category
      ? `${EXPENSE_SYSTEM_NOTES[category.code] ?? "Kategoria systemowa."} ${SYSTEM_EDIT_RULE}`
      : config.systemNote;

  const usageLabel = (category: ManagedCategory): string => {
    const count = usage[category.id] ?? 0;
    return count === 0 ? `0 ${config.forms[2]}` : `${count} ${pluralForm(count, config.forms)}`;
  };

  return (
    <Modal title={config.title} onClose={onClose} size="md" bodyClassName="cm-modal-body" hideFooter>
      <div className="cm-body flex-column g-1">
        <div className="flex align-items-center justify-end g-05">
          <ActionButton text="Nowa kategoria" onClick={() => setEditTarget({ kind: "new" })}>
            <Plus className="dl-icon-1 pc-y" />
          </ActionButton>
        </div>

        {loadError ? (
          <div className="flex align-items-center g-1 flex-wrap">
            <span className="cq-empty">Nie udało się wczytać kategorii.</span>
            <ActionButton text="Spróbuj ponownie" onClick={() => load()}>
              <RotateCcw className="dl-icon-1" />
            </ActionButton>
          </div>
        ) : categories === null ? (
          <span className="cq-empty italic">Wczytywanie kategorii…</span>
        ) : categories.length === 0 ? (
          <span className="cq-empty italic">Brak kategorii.</span>
        ) : (
          <div className="cm-list flex-column">
            {categories.map((category) => {
              const system = isSystem(category);
              const count = usage[category.id] ?? 0;
              return (
                <div key={category.id} className={`cm-row ${config.showUsage ? "" : "no-usage"}`}>
                  <span className="dl-dot cm-dot" style={{ backgroundColor: `rgb(${category.color})` }} />
                  <span className="cm-name-wrap flex align-items-center g-05 mw-0">
                    <span className="cm-name">{category.name}</span>
                    {system && (
                      <span className="cm-system flex align-items-center g-025" title={systemNoteOf(category) || `Kategoria systemowa. ${SYSTEM_EDIT_RULE}`}>
                        <Lock className="dl-icon" />
                        systemowa
                      </span>
                    )}
                  </span>
                  {config.showUsage && (
                    <span className={`cm-usage ${count === 0 ? "empty" : ""}`}>{usageLabel(category)}</span>
                  )}
                  <RowActionMenu
                    items={[
                      {
                        label: "Edytuj",
                        icon: <SquarePen className="dl-icon c-blue" />,
                        onClick: () => setEditTarget({ kind: "edit", category }),
                      },
                      ...(system || count > 0
                        ? []
                        : [
                            {
                              label: "Usuń",
                              icon: <Trash2 className="dl-icon c-red" />,
                              onClick: () => setRemoveTarget(category),
                              danger: true,
                            },
                          ]),
                    ]}
                  />
                </div>
              );
            })}
          </div>
        )}
      </div>

      {editTarget && (
        <CategoryModal
          category={editTarget.kind === "edit" ? editTarget.category : null}
          takenColors={(allCategories ?? [])
            .filter((item) => editTarget.kind === "new" || item.id !== editTarget.category.id)
            .map((item) => item.color)}
          systemNote={editTarget.kind === "edit" ? systemNoteOf(editTarget.category) : ""}
          onClose={() => setEditTarget(null)}
          onConfirm={handleSave}
        />
      )}

      {removeTarget && (
        <RemoveModal
          onClose={() => setRemoveTarget(null)}
          handleRemove={handleRemove}
          warningText={`Kategoria „${removeTarget.name}” zostanie usunięta.`}
        />
      )}
    </Modal>
  );
}

export default CategoryManagerModal;
