import { useEffect, useState, useCallback } from "react";
import { CirclePlus } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import ActionButton from "../ActionButton";
import Modal from "../Modal";
import RemoveModal from "./RemoveModal";
import DiscountModal from "./DiscountModal";
import DiscountDataList from "../Clients/DiscountDataList";
import { AlertType } from "../../models/alert";
import { Discount } from "../../models/visit";
import DiscountService from "../../services/DiscountService";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";

export interface DiscountsModalProps {
  onClose: () => void;
  onReset?: () => void;
  className?: string;
}

export function DiscountsModal({
  onClose,
  onReset,
  className = "",
}: DiscountsModalProps) {
  const [isAddNewDiscountPopupOpen, setIsAddNewDiscountPopupOpen] =
    useState<boolean>(false);
  const [editDiscountId, setEditDiscountId] = useRestorableState<number | string | null>(
    "discounts.editId",
    null,
    { skipOnReload: true }
  );
  const [removeDiscountId, setRemoveDiscountId] = useState<
    number | string | null
  >(null);
  const [discounts, setDiscounts] = useState<Discount[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchDiscounts = async (): Promise<void> => {
    setLoading(true);
    DiscountService.getDiscounts()
      .then((data) => setDiscounts(data))
      .catch((error) => {
        setDiscounts([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Discounts: ", error);
      })
      .finally(() => setLoading(false));
  };

  const handleDiscountRemove = useCallback(async (): Promise<void> => {
    try {
      if (removeDiscountId) {
        await DiscountService.deleteDiscount(removeDiscountId);
        showAlert("Pomyślnie usunięto rabat!", AlertType.SUCCESS);
        setRemoveDiscountId(null);
        fetchDiscounts();
        onReset?.();
      }
    } catch (error) {
      showAlert("Błąd usuwania rabatu!", AlertType.ERROR);
    }
  }, [removeDiscountId, showAlert, onReset]);

  useEffect(() => {
    fetchDiscounts();
  }, []);

  return (
    <>
      <Modal
        title="Rabaty Klientów"
        onClose={onClose}
        size="lg"
        height="tall"
        bodyClassName="nested"
        className={className}
        headerExtra={
          <ActionButton
            text={isMobile ? "Rabat" : "Nowy Rabat"}
            iconTitle={"Nowy Rabat"}
            className="dc-new"
            onClick={() => setIsAddNewDiscountPopupOpen(true)}
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        }
      >
        <DiscountDataList
          className="dc-list"
          items={discounts}
          isLoading={loading}
          setEditDiscountId={setEditDiscountId}
          setRemoveDiscountId={setRemoveDiscountId}
        />
      </Modal>

      {isAddNewDiscountPopupOpen && (
        <DiscountModal
          onClose={() => {
            setIsAddNewDiscountPopupOpen(false);
            fetchDiscounts();
            onReset?.();
          }}
        />
      )}
      {editDiscountId != null && (
        <DiscountModal
          onClose={() => {
            setEditDiscountId(null);
            fetchDiscounts();
            onReset?.();
          }}
          discountId={editDiscountId}
        />
      )}
      {removeDiscountId != null && (
        <RemoveModal
          onClose={() => setRemoveDiscountId(null)}
          handleRemove={handleDiscountRemove}
          warningText={
            "Usunięcie odepnie Rabat od przypisanych Klientów i skasuje go z bazy."
          }
        />
      )}
    </>
  );
}

export default DiscountsModal;
