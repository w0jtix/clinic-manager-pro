import { useCallback, useEffect, useState } from "react";
import { TriangleAlert } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { KsefBookCorrectionItem, KsefInvoice } from "../../models/ksef_invoice";
import { StockChangePreview } from "../../models/stock_change";
import KsefInvoiceService from "../../services/KsefInvoiceService";

export interface KsefCorrectionBookModalProps {
  invoiceId: number;
  items: KsefBookCorrectionItem[];
  onClose: () => void;
  onBooked: (updated: KsefInvoice) => void;
}

export function KsefCorrectionBookModal({
  invoiceId,
  items,
  onClose,
  onBooked,
}: KsefCorrectionBookModalProps) {
  const { showAlert } = useAlert();
  const [preview, setPreview] = useState<StockChangePreview | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [booking, setBooking] = useState<boolean>(false);

  useEffect(() => {
    setLoading(true);
    KsefInvoiceService.bookCorrectionPreview(invoiceId, { items })
      .then((data) => setPreview(data))
      .catch((error) => {
        console.error("Error fetching correction stock preview: ", error);
        showAlert("Błąd pobierania podglądu stanu magazynowego!", AlertType.ERROR);
      })
      .finally(() => setLoading(false));
  }, [invoiceId, items]);

  const handleBook = useCallback(async () => {
    setBooking(true);
    try {
      const updated = await KsefInvoiceService.bookCorrection(invoiceId, { items });
      showAlert("Korekta została zaksięgowana!", AlertType.SUCCESS);
      onBooked(updated);
    } catch (error: any) {
      if (error?.response?.status === 409 && Array.isArray(error.response.data?.products)) {
        setPreview(error.response.data as StockChangePreview);
        showAlert("Stan magazynowy zmienił się - nie można zaksięgować korekty.", AlertType.ERROR);
      } else {
        console.error("Error booking correction: ", error);
        showAlert("Błąd księgowania korekty.", AlertType.ERROR);
      }
    } finally {
      setBooking(false);
    }
  }, [invoiceId, items, onBooked, showAlert]);

  const products = preview?.products ?? [];
  const blocked = preview?.canApply === false;

  return (
    <Modal
      title="Księgowanie korekty"
      onClose={onClose}
      size="md"
      footer={
        <>
          <ActionButton text="Anuluj" className="modal-button" onClick={onClose} />
          <ActionButton
            text="Księguj"
            className="modal-button"
            disabled={loading || booking || blocked || preview == null}
            onClick={handleBook}
          />
        </>
      }
    >
      <div className="remove-modal-body flex-column align-items-center text-align-center g-075">
        <TriangleAlert className="dl-icon-1 c-red remove-modal-icon" />
        <p className="remove-modal-warning m-0">Zaksięgowania korekty nie będzie można cofnąć.</p>
        {loading && <span className="dl-dim">Ładowanie...</span>}
        {!loading && products.length === 0 && preview && (
          <span className="erm-note">Korekta nie zmienia stanu magazynowego.</span>
        )}
        {products.length > 0 && (
          <>
            <span className="erm-intro">Zmiana stanu magazynowego:</span>
            <div className="erm-list flex-column width-max">
              {products.map((product) => (
                <div
                  key={product.productId}
                  className={`erm-row ${product.sufficient ? "" : "insufficient"}`}
                >
                  <span className="erm-name flex-column mw-0">
                    <span className="erm-product">{product.productName}</span>
                    {!product.sufficient && (
                      <span className="erm-error">Niewystarczająca ilość w magazynie</span>
                    )}
                  </span>
                  <span className={`erm-qty tnum ${product.quantity > 0 ? "plus" : ""}`}>
                    {product.quantity > 0 ? `+${product.quantity}` : product.quantity}
                  </span>
                  <span className="erm-stock tnum">
                    <span className="erm-from">{product.currentSupply}</span>
                    {" → "}
                    <span className="erm-to">{product.supplyAfter}</span>
                  </span>
                </div>
              ))}
            </div>
            {blocked && (
              <span className="erm-error">
                Nie można zaksięgować korekty - niewystarczająca ilość w magazynie. Zaksięguj
                najpierw nową fakturę od dostawcy albo popraw stan inwentaryzacją.
              </span>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}

export default KsefCorrectionBookModal;
