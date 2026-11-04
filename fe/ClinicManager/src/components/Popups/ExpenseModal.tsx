import { useState, useCallback, useEffect } from "react";
import { SquarePen } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { AlertType } from "../../models/alert";
import { validateExpenseForm, validateExpenseHeader } from "../../utils/validators";
import { useAlert } from "../Alert/AlertProvider";
import { ExpenseCategory, NewCompanyExpense } from "../../models/expense";
import ExpenseService from "../../services/ExpenseService";
import ExpenseForm from "../Business/ExpenseForm";
import { SellerOriginModule, SellerOriginPeriod } from "../../models/ksef_seller";
import { formatDate } from "../../utils/dateUtils";

export interface ExpenseModalProps {
  onClose: () => void;
  onReset: () => void;
  expenseId?: number | null;
  initialExpense?: NewCompanyExpense;
  highlightProductId?: number | null;
  originPeriod?: SellerOriginPeriod;
  originModule?: SellerOriginModule;
  originProductId?: number;
  sellerLink?: boolean;
  startInEdit?: boolean;
  className?: string;
}

const todayIso = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(
    now.getDate()
  ).padStart(2, "0")}`;
};

const backendMessage = (error: any): string | null =>
  error?.response?.status >= 400 &&
  error?.response?.status < 500 &&
  typeof error.response.data === "string"
    ? error.response.data
    : null;

export function ExpenseModal({
  onClose,
  onReset,
  expenseId,
  initialExpense,
  highlightProductId = null,
  originPeriod,
  originModule,
  originProductId,
  sellerLink = true,
  startInEdit = false,
  className = "",
}: ExpenseModalProps) {
  const [expenseDTO, setExpenseDTO] = useState<NewCompanyExpense>(
    initialExpense ?? {
      source: "",
      expenseDate: new Date().toISOString().split("T")[0],
      category: null,
      expenseItems: [],
    }
  );
  const [savedDTO, setSavedDTO] = useState<NewCompanyExpense | null>(null);
  const [editing, setEditing] = useState<boolean>(false);
  const [futureDateConfirm, setFutureDateConfirm] = useState<boolean>(false);
  const [rateReady, setRateReady] = useState<boolean>(true);
  const { showAlert } = useAlert();

  const isPreview = expenseId != null;
  const canEdit =
    isPreview && savedDTO != null && savedDTO.category !== ExpenseCategory.SALARY;

  const fetchExpenseById = async (expenseId: number) => {
    ExpenseService.getExpenseById(expenseId)
      .then((data) => {
        setExpenseDTO(data);
        setSavedDTO(data);
        if (startInEdit && data.category !== ExpenseCategory.SALARY) setEditing(true);
      })
      .catch((error) => {
        console.error("Error fetching Expense by id: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  const createExpense = useCallback(async () => {
    try {
      const newExpense = expenseDTO as NewCompanyExpense;
      await ExpenseService.createExpense({
        ...newExpense,
        seller: newExpense.seller
          ? { ...newExpense.seller, name: newExpense.source.trim() }
          : null,
        invoiceNumber: newExpense.invoiceNumber?.trim() || null,
        currency: newExpense.currency ?? "PLN",
        expenseItems: newExpense.expenseItems.map(({ notProduct, lockedTotal, ...item }) => {
          if (newExpense.category === ExpenseCategory.MIXED) {
            return item.category === ExpenseCategory.PRODUCTS
              ? item
              : { ...item, name: item.name.trim(), productId: null };
          }
          return notProduct
            ? { ...item, name: item.name.trim(), productId: null, category: null }
            : { ...item, category: null };
        }),
      });
      showAlert("Koszt został utworzony!", AlertType.SUCCESS);
      onReset();
      onClose();
    } catch (error: any) {
      console.error("Error creating expense:", error);
      showAlert(backendMessage(error) ?? "Błąd tworzenia kosztu.", AlertType.ERROR);
    }
  }, [expenseDTO, onReset, onClose, showAlert]);

  const updateExpense = useCallback(async () => {
    if (expenseId == null) return;
    try {
      const updated = await ExpenseService.updateExpense(expenseId, {
        expenseDate: expenseDTO.expenseDate,
        source: expenseDTO.source.trim(),
        category: expenseDTO.category,
        seller: expenseDTO.seller
          ? { ...expenseDTO.seller, name: expenseDTO.source.trim() }
          : null,
        invoiceNumber: expenseDTO.invoiceNumber?.trim() || null,
        currency: expenseDTO.currency ?? "PLN",
      });
      setExpenseDTO(updated);
      setSavedDTO(updated);
      setEditing(false);
      showAlert("Koszt został zaktualizowany!", AlertType.SUCCESS);
      onReset();
    } catch (error: any) {
      console.error("Error updating expense:", error);
      showAlert(backendMessage(error) ?? "Błąd aktualizacji kosztu.", AlertType.ERROR);
    }
  }, [expenseId, expenseDTO, onReset, showAlert]);

  const save = useCallback(() => {
    setFutureDateConfirm(false);
    if (editing) {
      updateExpense();
    } else {
      createExpense();
    }
  }, [editing, updateExpense, createExpense]);

  const handleExpenseAction = useCallback(() => {
    if (
      expenseDTO.currency &&
      expenseDTO.currency !== "PLN" &&
      expenseDTO.expenseDate > todayIso()
    ) {
      showAlert("Kurs NBP nie jest dostępny dla daty z przyszłości.", AlertType.ERROR);
      return;
    }
    if (!rateReady) {
      showAlert("Poczekaj na kurs NBP.", AlertType.ERROR);
      return;
    }
    const error = editing ? validateExpenseHeader(expenseDTO) : validateExpenseForm(expenseDTO);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    if (expenseDTO.expenseDate > todayIso()) {
      setFutureDateConfirm(true);
      return;
    }
    save();
  }, [editing, expenseDTO, save, showAlert, rateReady]);

  const handleCancelEdit = useCallback(() => {
    if (savedDTO) setExpenseDTO(savedDTO);
    setEditing(false);
  }, [savedDTO]);

  useEffect(() => {
    if (expenseId) {
      fetchExpenseById(expenseId);
    }
  }, [expenseId]);

  return (
    <>
      <Modal
        title={editing ? "Edycja Kosztu" : isPreview ? "Podgląd Kosztu" : "Wprowadź Koszt"}
        onClose={onClose}
        size="lg"
        height={"tall"}
        className={`exf-modal modal-overflow ${className}`}
        bodyClassName="exf-body"
        onConfirm={handleExpenseAction}
        confirmText={editing ? "Zapisz zmiany" : "Zapisz"}
        hideFooter={isPreview && !editing}
        dirtyValue={isPreview ? undefined : expenseDTO}
        dirty={
          editing &&
          savedDTO != null &&
          JSON.stringify(expenseDTO) !== JSON.stringify(savedDTO)
        }
        footer={
          editing ? (
            <>
              <ActionButton text="Anuluj" className="modal-button" onClick={handleCancelEdit} />
              <ActionButton
                text="Zapisz zmiany"
                className="modal-button"
                onClick={handleExpenseAction}
              />
            </>
          ) : undefined
        }
        headerExtra={
          canEdit && !editing ? (
            <ActionButton
              text="Edytuj"
              iconTitle="Edytuj dane kosztu"
              disableBorder={true}
              onClick={() => setEditing(true)}
            >
              <SquarePen className="dl-icon c-blue" />
            </ActionButton>
          ) : undefined
        }
      >
        <ExpenseForm
          preview={isPreview}
          headerEditable={editing}
          originalCategory={savedDTO?.category ?? null}
          highlightProductId={highlightProductId}
          originPeriod={originPeriod}
          originModule={originModule}
          originProductId={originProductId}
          sellerLink={sellerLink}
          expenseDTO={expenseDTO}
          setExpenseDTO={setExpenseDTO}
          onRateReadyChange={setRateReady}
        />
      </Modal>
      {futureDateConfirm && (
        <Modal
          title="Data w przyszłości"
          onClose={() => setFutureDateConfirm(false)}
          size="sm"
          footer={
            <>
              <ActionButton
                text="Popraw datę"
                className="modal-button"
                onClick={() => setFutureDateConfirm(false)}
              />
              <ActionButton text="Zapisz" className="modal-button" onClick={save} />
            </>
          }
        >
          <p className="remove-modal-warning text-align-center m-0">
            Data kosztu {formatDate(expenseDTO.expenseDate)} jest w przyszłości. Zapisać?
          </p>
        </Modal>
      )}
    </>
  );
}

export default ExpenseModal;
