import { useEffect, useState, useCallback } from "react";
import { CirclePlus, RotateCw } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import ActionButton from "../ActionButton";
import NavigationBar from "../NavigationBar";
import SearchBar from "../SearchBar";
import RemoveModal from "../Popups/RemoveModal";
import { AlertType } from "../../models/alert";
import { Client } from "../../models/client";
import { Review, ReviewFilterDTO, ReviewSource } from "../../models/review";
import ReviewService from "../../services/ReviewService";
import ClientService from "../../services/ClientService";
import ReviewDataList from "./ReviewDataList";
import ReviewModal from "../Popups/ReviewModal";
import booksyIcon from "../../assets/booksy.png";
import googleIcon from "../../assets/google.png";
import FilterDropdown from "../FilterDropdown";
import CycleFilter from "../CycleFilter";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";

const reviewSourceIcons: Record<ReviewSource, string> = {
  [ReviewSource.BOOKSY]: booksyIcon,
  [ReviewSource.GOOGLE]: googleIcon,
};

const REVIEW_SOURCE_LABELS: Record<ReviewSource, string> = {
  [ReviewSource.BOOKSY]: "Booksy",
  [ReviewSource.GOOGLE]: "Google",
};

const EMPTY_FILTER: ReviewFilterDTO = {
  source: null,
  keyword: "",
  isUsed: null,
};

export function ReviewsDashboard() {
  const [isAddNewReviewPopupOpen, setIsAddNewReviewPopupOpen] =
    useState<boolean>(false);
  const [editReviewId, setEditReviewId] = useState<number | string | null>(null);
  const [removeReviewId, setRemoveReviewId] = useState<number | string | null>(
    null
  );
  const [reviews, setReviews] = useState<Review[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [filter, setFilter] = useRestorableState<ReviewFilterDTO>("reviews.filter", EMPTY_FILTER);
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchReviews = async (): Promise<void> => {
    ReviewService.getReviews(filter)
      .then((data) => {
        const sortedData = data.sort(
          (a, b) =>
            new Date(b.issueDate).getTime() - new Date(a.issueDate).getTime()
        );
        setReviews(sortedData);
      })
      .catch((error) => {
        setReviews([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching reviews: ", error);
      });
  };

  const fetchClients = async (): Promise<void> => {
    ClientService.getClients({ keyword: "" })
      .then((data) => setClients(data))
      .catch((error) => {
        setClients([]);
        console.error("Error fetching Clients: ", error);
      });
  };

  const handleKeywordChange = useCallback((newKeyword: string) => {
    setFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const handleReviewSourceChange = useCallback((source: ReviewSource) => {
    setFilter((prev) => ({
      ...prev,
      source: prev.source === source ? null : source,
    }));
  }, []);

  const toggleStatus = useCallback(() => {
    setFilter((prev) => {
      let nextStatus: boolean | null = null;
      if (prev.isUsed === null) nextStatus = false;
      else if (prev.isUsed === false) nextStatus = true;
      else nextStatus = null;

      return { ...prev, isUsed: nextStatus };
    });
  }, []);

  const handleResetFilters = useCallback(() => {
    setFilter(EMPTY_FILTER);
    setResetTriggered((prev) => !prev);
  }, []);

  const handleReviewRemove = useCallback(async (): Promise<void> => {
    try {
      if (removeReviewId) {
        await ReviewService.deleteReview(removeReviewId);
        showAlert("Pomyślnie usunięto opinię!", AlertType.SUCCESS);
        setRemoveReviewId(null);
        fetchReviews();
      }
    } catch (error) {
      showAlert("Błąd usuwania opinii!", AlertType.ERROR);
    }
  }, [removeReviewId, showAlert]);

  useEffect(() => {
    fetchClients();
  }, []);

  useEffect(() => {
    fetchReviews();
  }, [filter]);

  const searchField = (
    <SearchBar
      onKeywordChange={handleKeywordChange}
      resetTriggered={resetTriggered}
      initialValue={filter.keyword ?? ""}
    />
  );

  const statusField = (
    <CycleFilter
      label="Status:"
      value={
        filter.isUsed === null
          ? "wszystkie"
          : filter.isUsed === false
          ? "aktywne"
          : "zrealizowane"
      }
      tone={filter.isUsed === null ? null : filter.isUsed === false ? "green" : "grey"}
      iconTitle="Status Opinii"
      onClick={toggleStatus}
    />
  );

  const sourceFields = (
    <div className="cat-seg-group rv-source-seg align-items-center g-025">
      {Object.values(ReviewSource).map((source) => (
        <button
          type="button"
          key={source}
          title={REVIEW_SOURCE_LABELS[source]}
          className={`cat-seg align-items-center pointer ${
            filter.source === source ? "active" : ""
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
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <section className="cl-toolbar width-93 flex align-items-center space-between g-1 mt-1 mb-1">
          <div className="flex align-items-center g-075">
            <FilterDropdown
              label="Filtry"
              className="rv-filters"
              count={
                [!!filter.keyword, filter.isUsed !== null, filter.source !== null].filter(Boolean)
                  .length
              }
            >
              {searchField}
              <div className="fd-row flex align-items-center g-05">
                {statusField}
                {sourceFields}
              </div>
            </FilterDropdown>
            <ActionButton
              disableText={true}
              iconTitle={"Resetuj filtry"}
              onClick={handleResetFilters}
              className={"reset"}
            >
              <RotateCw className="dl-icon icon-control" />
            </ActionButton>
          </div>

          <ActionButton
            text={isMobile ? "Opinia" : "Nowa Opinia"}
            iconTitle={"Nowa Opinia"}
            onClick={() => setIsAddNewReviewPopupOpen(true)}
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        </section>

        <div className="width-93 flex-column f-1 min-height-0 mb-2">
          <ReviewDataList
            className="rv-list"
            items={reviews}
            setEditReviewId={setEditReviewId}
            setRemoveReviewId={setRemoveReviewId}
          />
        </div>

        {isAddNewReviewPopupOpen && (
          <ReviewModal
            onClose={() => {
              setIsAddNewReviewPopupOpen(false);
              fetchReviews();
            }}
            clients={clients}
            className=""
          />
        )}
        {editReviewId != null && (
          <ReviewModal
            onClose={() => {
              setEditReviewId(null);
              fetchReviews();
            }}
            clients={clients}
            className=""
            reviewId={editReviewId}
          />
        )}
        {removeReviewId != null && (
          <RemoveModal
            onClose={() => setRemoveReviewId(null)}
            handleRemove={handleReviewRemove}
            warningText={"Usunięcie spowoduje utratę informacji o Opinii."}
          />
        )}
      </div>
    </div>
  );
}

export default ReviewsDashboard;
