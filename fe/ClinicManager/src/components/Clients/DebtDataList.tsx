import React, { useCallback, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { SquarePen, Trash2, UserX, Eye, HandCoins } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { ClientDebt, DebtType } from "../../models/debt";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { PaymentStatus } from "../../models/payment";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";

export interface DebtDataListProps {
  items: ClientDebt[];
  setEditDebtId?: (debtId: number | string | null) => void;
  setRemoveDebtId?: (debtId: number | string | null) => void;
  isLoading?: boolean;
  className?: string;
}

export function DebtDataList({
  items,
  setEditDebtId,
  setRemoveDebtId,
  isLoading = false,
  className = "",
}: DebtDataListProps) {
  const { user } = useUser();
  const { isMaster } = useRoles();
  const { isMobile, isCompact, isNarrow } = useBreakpoint();
  const [redemptionDebtId, setRedemptionDebtId] = useState<
    number | string | null
  >(null);
  const [sourceVisitId, setSourceVisitId] = useState<number | string | null>(
    null
  );

  const handleOnClickEdit = useCallback(
    (e: React.MouseEvent, item: ClientDebt) => {
      e.stopPropagation();
      setEditDebtId?.(item.id);
    },
    [setEditDebtId]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: ClientDebt) => {
      e.stopPropagation();
      setRemoveDebtId?.(item.id);
    },
    [setRemoveDebtId]
  );

  const canManage = useCallback(
    (item: ClientDebt): boolean =>
      item.sourceVisit === null &&
      (item.createdBy === user?.id ||
        isMaster),
    [user?.id, isMaster]
  );

  const columns = useMemo<Column<ClientDebt>[]>(
    () => [
      {
        key: "client",
        header: "Klient",
        grow: true,
        render: (item) => (
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
        ),
      },
      {
        key: "type",
        header: "Przyczyna",
        width: isCompact ? "2rem" : isMobile ? "6rem" : "11rem",
        align: "center",
        render: (item) => {
          const isAbsence = item.type === DebtType.ABSENCE_FEE;
          const label = isAbsence ? "NIEOBECNOŚĆ" : "ZALEGŁOŚĆ";

          if (isCompact) {
            return (
              <span
                className="flex align-items-center justify-center"
                title={label}
              >
                {isAbsence ? (
                  <UserX className="dl-icon-1 c-red" />
                ) : (
                  <HandCoins className="dl-icon-1 c-yel" />
                )}
              </span>
            );
          }

          return (
            <span className={`rv-status ${isAbsence ? "absence" : "onhold"}`}>
              {label}
            </span>
          );
        },
      },
      {
        key: "status",
        header: "Status",
        width: isMobile ? "7rem" : "10rem",
        align: "center",
        render: (item) => {
          const isPaid = item.paymentStatus === PaymentStatus.PAID;
          if (!isPaid) {
            return <span className="rv-status expired">NIEOPŁACONE</span>;
          }
          return (
            <span
              className="rv-status active dt-paid pointer flex align-items-center g-05"
              title="Pokaż Wizytę spłaty"
              onClick={(e) => {
                e.stopPropagation();
                setRedemptionDebtId(item.id);
              }}
            >
              {!isNarrow && <Eye className="dl-icon-1" />}
              <span className="rv-status-text">OPŁACONE</span>
            </span>
          );
        },
      },
      {
        key: "value",
        header: "Wartość",
        width: isMobile ? "4.75rem" : "7rem",
        align: "center",
        render: (item) => (
          <span className="sv-price nowrap">{item.value} zł</span>
        ),
      },
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "1.9rem" : "7rem",
        align: "center",
        render: (item) =>
          isMobile ? (
            <div className="flex align-items-center justify-center">
              <RowActionMenu
                items={[
                  ...(item.sourceVisit != null
                    ? [
                        {
                          label: "Pokaż wizytę źródłową",
                          icon: <Eye className="dl-icon" />,
                          onClick: () => setSourceVisitId(item.sourceVisit!.id),
                        },
                      ]
                    : []),
                  ...(canManage(item)
                    ? [
                        {
                          label: "Edytuj",
                          icon: <SquarePen className="dl-icon c-blue" />,
                          onClick: () => setEditDebtId?.(item.id),
                        },
                        {
                          label: "Usuń",
                          icon: <Trash2 className="dl-icon c-red" />,
                          onClick: () => setRemoveDebtId?.(item.id),
                          danger: true,
                        },
                      ]
                    : []),
                ]}
              />
            </div>
          ) : (
          <div className="flex align-items-center justify-center g-05">
            {item.sourceVisit != null && (
              <ActionButton
                iconTitle={"Pokaż Wizytę - źródło długu"}
                onClick={(e) => {
                  e.stopPropagation();
                  setSourceVisitId(item.sourceVisit!.id);
                }}
                disableText={true}
                disableBorder={true}
              >
                <Eye className="dl-icon" />
              </ActionButton>
            )}
            {canManage(item) && (
              <>
                <ActionButton
                  iconTitle={"Edytuj Dług"}
                  onClick={(e) => handleOnClickEdit(e, item)}
                  disableText={true}
                  disableBorder={true}
                >
                  <SquarePen className="dl-icon c-blue" />
                </ActionButton>
                <ActionButton
                  iconTitle={"Usuń Dług"}
                  onClick={(e) => handleOnClickRemove(e, item)}
                  disableText={true}
                  disableBorder={true}
                >
                  <Trash2 className="dl-icon c-red" />
                </ActionButton>
              </>
            )}
          </div>
          ),
      },
    ],
    [
      canManage,
      isMobile,
      isCompact,
      isNarrow,
      handleOnClickEdit,
      handleOnClickRemove,
      setEditDebtId,
      setRemoveDebtId,
    ]
  );

  return (
    <>
      <DataList<ClientDebt>
        columns={columns}
        items={items}
        getRowKey={(item) => item.id}
        onRowClick={(item) => item.sourceVisit && setSourceVisitId(item.sourceVisit.id)}
        isRowClickable={(item) => item.sourceVisit != null}
        isLoading={isLoading}
        hideHeader={isMobile}
        emptyText="Brak długów"
        className={className}
      />

      {redemptionDebtId !== null && (
        <VisitPreviewModal
          onClose={() => setRedemptionDebtId(null)}
          debtRedemptionSourceId={redemptionDebtId}
        />
      )}
      {sourceVisitId !== null && (
        <VisitPreviewModal
          onClose={() => setSourceVisitId(null)}
          selectedSourceVisitIdForVisit={sourceVisitId}
        />
      )}
    </>
  );
}

export default DebtDataList;
