import { useCallback, useEffect, useState } from "react";
import { TriangleAlert } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { StockChangePreview } from "../../models/stock_change";
import ExpenseService from "../../services/ExpenseService";

export interface ExpenseRemoveModalProps {
  expenseId: number;
  onClose: () => void;
  onRemoved: () => void;
  className?: string;
}

export function ExpenseRemoveModal({
  expenseId,
  onClose,
  onRemoved,
  className = "",
}: ExpenseRemoveModalProps) {
  const { showAlert } = useAlert();
  const [preview, setPreview] = useState<StockChangePreview | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [removing, setRemoving] = useState<boolean>(false);

  useEffect(() => {
    setLoading(true);
    ExpenseService.getDeletionPreview(expenseId)
      .then((data) => setPreview(data))
      .catch((error) => {
        console.error("Error fetching expense deletion preview: ", error);
        showAlert("Błąd pobierania danych kosztu!", AlertType.ERROR);
      })
      .finally(() => setLoading(false));
  }, [expenseId]);

  const handleRemove = useCallback(async () => {
    setRemoving(true);
    try {
      await ExpenseService.deleteExpense(expenseId);
      showAlert("Pomyślnie usunięto Koszt!", AlertType.SUCCESS);
      onRemoved();
    } catch (error: any) {
      if (error?.response?.status === 409 && Array.isArray(error.response.data?.products)) {
        setPreview(error.response.data as StockChangePreview);
        showAlert("Stan magazynowy zmienił się - nie można usunąć kosztu.", AlertType.ERROR);
      } else {
        console.error("Error removing Expense", error);
        showAlert("Błąd usuwania Kosztu!", AlertType.ERROR);
      }
    } finally {
      setRemoving(false);
    }
  }, [expenseId, onRemoved, showAlert]);

  const products = preview?.products ?? [];
  const blocked = preview?.canApply === false;

  return (
    <Modal
      title="Na pewno?"
      onClose={onClose}
      size={products.length > 0 ? "md" : "sm"}
      className={className}
      footer={
        <ActionButton
          text="Usuń"
          className="modal-button danger"
          disabled={loading || removing || blocked}
          onClick={handleRemove}
        />
      }
    >
      <div className="remove-modal-body flex-column align-items-center text-align-center g-075">
        <TriangleAlert className="dl-icon-1 c-red remove-modal-icon" />
        <p className="remove-modal-warning m-0">
          Zatwierdzenie spowoduje usunięcie Kosztu z bazy danych!
        </p>
        {loading && <span className="dl-dim">Ładowanie...</span>}
        {products.length > 0 && (
          <>
            <span className="erm-intro">Zmiany:</span>
            <div className="erm-list flex-column width-max">
              {products.map((product) => (
                <div
                  key={product.productId}
                  className={`erm-row ${product.skipped ? "skipped" : ""} ${
                    product.sufficient ? "" : "insufficient"
                  }`}
                >
                  <span className="erm-name flex-column mw-0">
                    <span className="erm-product">{product.productName}</span>
                    {product.skipped && (
                      <span className="erm-hint">produkt usunięty - pominięty</span>
                    )}
                    {!product.sufficient && (
                      <span className="erm-error">Niewystarczająca ilość w magazynie</span>
                    )}
                  </span>
                  <span className={`erm-qty tnum ${product.quantity > 0 ? "plus" : ""}`}>
                    {product.quantity > 0 ? `+${product.quantity}` : product.quantity}
                  </span>
                  <span className="erm-stock tnum">
                    {product.skipped ? (
                      "-"
                    ) : (
                      <>
                        <span className="erm-from">{product.currentSupply}</span>
                        {" → "}
                        <span className="erm-to">{product.supplyAfter}</span>
                      </>
                    )}
                  </span>
                </div>
              ))}
            </div>
            {blocked && (
              <span className="erm-error">
                Nie można usunąć kosztu - niewystarczająca ilość w magazynie.
              </span>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}

export default ExpenseRemoveModal;
