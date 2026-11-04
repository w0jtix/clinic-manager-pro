import { useState, useCallback, useEffect } from "react";
import Modal from "../Modal";
import DropdownSelect from "../DropdownSelect";
// import DateInput from "../DateInput";
import WheelDateField from "../WheelDateField";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import { Client } from "../../models/client";
import { Review, NewReview, ReviewSource } from "../../models/review";
import { validateReviewForm } from "../../utils/validators";
import ReviewService from "../../services/ReviewService";
import booksyIcon from "../../assets/booksy.png";
import googleIcon from "../../assets/google.png";

const reviewSourceIcons: Record<ReviewSource, string> = {
  [ReviewSource.BOOKSY]: booksyIcon,
  [ReviewSource.GOOGLE]: googleIcon,
};

const REVIEW_SOURCE_LABELS: Record<ReviewSource, string> = {
  [ReviewSource.BOOKSY]: "Booksy",
  [ReviewSource.GOOGLE]: "Google",
};

export interface ReviewModalProps {
  onClose: () => void;
  clients: Client[];
  reviewId?: number | string | null;
  className?: string;
}

export function ReviewModal({
  onClose,
  clients,
  reviewId,
  className = "",
}: ReviewModalProps) {
  const [fetchedReview, setFetchedReview] = useState<Review | null>(null);
  const [reviewDTO, setReviewDTO] = useState<NewReview>({
    client: null,
    issueDate: new Date().toISOString().split("T")[0],
    source: null,
    isUsed: null,
  });
  const { showAlert } = useAlert();

  const action = reviewId ? Action.EDIT : Action.CREATE;

  const fetchReviewById = async (reviewId: number | string) => {
    ReviewService.getReviewById(reviewId)
      .then((data) => {
        setFetchedReview(data);
        setReviewDTO(data);
      })
      .catch((error) => {
        console.error("Error fetching review: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  useEffect(() => {
    if (reviewId) {
      fetchReviewById(reviewId);
    }
  }, [reviewId]);

  const handleClientChange = useCallback((client: Client | Client[] | null) => {
    const selectedClient = Array.isArray(client) ? client[0] : client;
    setReviewDTO((prev) => ({ ...prev, client: selectedClient ?? null }));
  }, []);

  const handleReviewSourceChange = useCallback((source: ReviewSource) => {
    setReviewDTO((prev) => ({ ...prev, source }));
  }, []);

  const handleIssueDateChange = useCallback((newDate: string | null) => {
    setReviewDTO((prev) => ({
      ...prev,
      issueDate: newDate || new Date().toISOString(),
    }));
  }, []);

  const handleReviewAction = useCallback(async () => {
    const error = validateReviewForm(reviewDTO, action, fetchedReview);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      if (action === Action.CREATE) {
        await ReviewService.createReview(reviewDTO as NewReview);
        showAlert(
          `Opinia klienta ${
            reviewDTO.client?.firstName + " " + reviewDTO.client?.lastName
          } utworzona!`,
          AlertType.SUCCESS
        );
      } else if (action === Action.EDIT && reviewId) {
        await ReviewService.updateReview(reviewId, reviewDTO as NewReview);
        showAlert(`Opinia zaktualizowana!`, AlertType.SUCCESS);
      }
      onClose();
    } catch (error) {
      showAlert(
        `Błąd ${
          action === Action.CREATE ? "tworzenia" : "aktualizacji"
        } opinii!`,
        AlertType.ERROR
      );
    }
  }, [reviewDTO, showAlert, reviewId, action, fetchedReview, onClose]);

  return (
    <Modal
      dirtyValue={reviewDTO}
      title={action === Action.CREATE ? "Nowa Opinia" : "Edytuj Opinię"}
      onClose={onClose}
      size="md"
      onConfirm={handleReviewAction}
      className={`modal-overflow ${className}`}
    >
      <div className="flex-column g-075">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Źródło:</span>
          <div className="cat-seg-group rv-source-seg align-items-center g-025">
            {Object.values(ReviewSource).map((source) => (
              <button
                type="button"
                key={source}
                title={REVIEW_SOURCE_LABELS[source]}
                className={`cat-seg align-items-center pointer ${
                  reviewDTO.source === source ? "active" : ""
                }`}
                onClick={() => handleReviewSourceChange(source)}
              >
                <img
                  src={reviewSourceIcons[source]}
                  alt={REVIEW_SOURCE_LABELS[source]}
                  className={`rv-seg-icon ${source.toLowerCase()}`}
                />
                {REVIEW_SOURCE_LABELS[source]}
              </button>
            ))}
          </div>
        </div>

        <div className="pf-row pf-client-row flex align-items-center g-1">
          <span className="pf-label">Klient:</span>
          <DropdownSelect<Client>
            items={clients}
            placeholder="Wybierz Klienta"
            value={reviewDTO.client}
            getItemLabel={(c) => `${c.firstName} ${c.lastName}`}
            allowNew={false}
            onChange={handleClientChange}
            className="clients"
          />
        </div>

        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Data dodania opinii:</span>
          {/* <DateInput
            onChange={handleIssueDateChange}
            selectedDate={reviewDTO.issueDate ?? new Date()}
          /> */}
          <WheelDateField
            title="Data dodania opinii"
            value={reviewDTO.issueDate ?? new Date()}
            onChange={handleIssueDateChange}
          />
        </div>
      </div>
    </Modal>
  );
}

export default ReviewModal;
