import React, { useCallback, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { SquarePen, Trash2, UserX, Eye } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { Review, ReviewSource } from "../../models/review";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";
import booksyIcon from "../../assets/booksy.png";
import googleIcon from "../../assets/google.png";

const reviewSourceIcons: Record<ReviewSource, string> = {
  [ReviewSource.BOOKSY]: booksyIcon,
  [ReviewSource.GOOGLE]: googleIcon,
};

export interface ReviewDataListProps {
  items: Review[];
  setEditReviewId?: (reviewId: number | string) => void;
  setRemoveReviewId?: (reviewId: number | string) => void;
  isLoading?: boolean;
  className?: string;
}

export function ReviewDataList({
  items,
  setEditReviewId,
  setRemoveReviewId,
  isLoading = false,
  className = "",
}: ReviewDataListProps) {
  const [selectedReviewIdForVisit, setSelectedReviewIdForVisit] = useState<
    string | number | null
  >(null);
  const { user } = useUser();
  const { isMaster } = useRoles();
  const { isMobile } = useBreakpoint();

  const handleOnClickEdit = useCallback(
    (e: React.MouseEvent, item: Review) => {
      e.stopPropagation();
      setEditReviewId?.(item.id);
    },
    [setEditReviewId]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: Review) => {
      e.stopPropagation();
      setRemoveReviewId?.(item.id);
    },
    [setRemoveReviewId]
  );

  const canManage = useCallback(
    (item: Review): boolean =>
      item.isUsed === false &&
      (item.createdBy === user?.id ||
        isMaster),
    [user?.id, isMaster]
  );

  const columns = useMemo<Column<Review>[]>(
    () => [
      {
        key: "source",
        header: "Źródło",
        width: isMobile ? "2rem" : "5rem",
        align: "center",
        render: (item) => (
          <img
            src={reviewSourceIcons[item.source]}
            alt={item.source}
            title={item.source}
            className={`rv-source ${item.source.toLowerCase()}`}
          />
        ),
      },
      {
        key: "client",
        header: "Klient",
        grow: true,
        render: (item) => {
          const nameRow = (
            <span
              className="flex align-items-center g-05"
              title={item.client.isDeleted ? "Klient usunięty" : ""}
            >
              <Link
                to={`/client/${item.client.id}`}
                className={`dl-name pd-product-link ${item.client.isDeleted ? "rv-removed" : ""}`}
                title="Otwórz stronę klienta"
                onClick={(e) => e.stopPropagation()}
              >
                {item.client.firstName} {item.client.lastName}
              </Link>
              {item.client.isDeleted && <UserX className="dl-icon-1 c-red" />}
            </span>
          );

          if (isMobile) {
            return (
              <span className="rv-name-stack flex-column mw-0">
                {nameRow}
                <span className="rv-name-meta dl-dim">
                  Dodano {new Date(item.issueDate).toLocaleDateString("pl-PL")}
                </span>
              </span>
            );
          }

          return nameRow;
        },
      },
      {
        key: "status",
        header: "Status",
        width: isMobile ? "7rem" : "10rem",
        align: "center",
        render: (item) => {
          if (item.source !== ReviewSource.GOOGLE) return null;

          const isActive = item.isUsed === false;
          if (isActive) {
            return <span className="rv-status active">AKTYWNA</span>;
          }
          return (
            <span
              className="rv-status used pointer flex align-items-center g-05"
              title="Pokaż Wizytę"
              onClick={(e) => {
                e.stopPropagation();
                setSelectedReviewIdForVisit(item.id);
              }}
            >
              <Eye className="dl-icon-1" />
              <span className="rv-status-text">ZREALIZOWANA</span>
            </span>
          );
        },
      },
      ...(isMobile
        ? []
        : [
            {
              key: "issueDate",
              header: "Dodano",
              width: "8rem",
              align: "center" as const,
              render: (item: Review) => (
                <span className="dl-dim">
                  {new Date(item.issueDate).toLocaleDateString("pl-PL")}
                </span>
              ),
            },
          ]),
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "1.9rem" : "6rem",
        align: "center",
        render: (item) =>
          !canManage(item) ? null : isMobile ? (
            <div className="flex align-items-center justify-center">
              <RowActionMenu
                items={[
                  {
                    label: "Edytuj",
                    icon: <SquarePen className="dl-icon c-blue" />,
                    onClick: () => setEditReviewId?.(item.id),
                  },
                  {
                    label: "Usuń",
                    icon: <Trash2 className="dl-icon c-red" />,
                    onClick: () => setRemoveReviewId?.(item.id),
                    danger: true,
                  },
                ]}
              />
            </div>
          ) : (
            <div className="flex align-items-center justify-center g-05">
              <ActionButton
                iconTitle={"Edytuj Opinię"}
                onClick={(e) => handleOnClickEdit(e, item)}
                disableText={true}
                disableBorder={true}
              >
                <SquarePen className="dl-icon c-blue" />
              </ActionButton>
              <ActionButton
                iconTitle={"Usuń Opinię"}
                onClick={(e) => handleOnClickRemove(e, item)}
                disableText={true}
                disableBorder={true}
              >
                <Trash2 className="dl-icon c-red" />
              </ActionButton>
            </div>
          ),
      },
    ],
    [
      canManage,
      isMobile,
      handleOnClickEdit,
      handleOnClickRemove,
      setEditReviewId,
      setRemoveReviewId,
    ]
  );

  return (
    <>
      <DataList<Review>
        columns={columns}
        items={items}
        getRowKey={(item) => item.id}
        isLoading={isLoading}
        hideHeader={isMobile}
        emptyText="Brak opinii"
        className={className}
      />

      {selectedReviewIdForVisit !== null && (
        <VisitPreviewModal
          onClose={() => setSelectedReviewIdForVisit(null)}
          reviewId={selectedReviewIdForVisit}
        />
      )}
    </>
  );
}

export default ReviewDataList;
