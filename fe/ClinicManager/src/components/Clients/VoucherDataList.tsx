import React, { useCallback, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Ticket, Eye, SquarePen, Trash2, UserX } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { Voucher, VoucherStatus } from "../../models/voucher";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";

const STATUS_LABELS: Record<VoucherStatus, string> = {
  [VoucherStatus.ACTIVE]: "AKTYWNY",
  [VoucherStatus.EXPIRED]: "NIEAKTYWNY",
  [VoucherStatus.USED]: "ZREALIZOWANY",
};

const STATUS_CLASSES: Record<VoucherStatus, string> = {
  [VoucherStatus.ACTIVE]: "active",
  [VoucherStatus.EXPIRED]: "expired",
  [VoucherStatus.USED]: "used",
};

export interface VoucherDataListProps {
  items: Voucher[];
  setEditVoucherId?: (voucherId: string | number | null) => void;
  setRemoveVoucherId?: (voucherId: string | number | null) => void;
  isLoading?: boolean;
  className?: string;
}

export function VoucherDataList({
  items,
  setEditVoucherId,
  setRemoveVoucherId,
  isLoading = false,
  className = "",
}: VoucherDataListProps) {
  const { user } = useUser();
  const { isMaster } = useRoles();
  const { isMobile, isNarrow } = useBreakpoint();
  const [selectedVoucherIdForVisit, setSelectedVoucherIdForVisit] = useState<
    string | number | null
  >(null);
  const [previewVisitId, setPreviewVisitId] = useState<string | number | null>(
    null
  );

  const handleOnClickEdit = useCallback(
    (e: React.MouseEvent, item: Voucher) => {
      e.stopPropagation();
      setEditVoucherId?.(item.id);
    },
    [setEditVoucherId]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: Voucher) => {
      e.stopPropagation();
      setRemoveVoucherId?.(item.id);
    },
    [setRemoveVoucherId]
  );

  const canManage = useCallback(
    (item: Voucher): boolean =>
      !item.purchaseVisitId &&
      item.status !== VoucherStatus.USED &&
      (item.createdBy === user?.id ||
        isMaster),
    [user?.id, isMaster]
  );

  const columns = useMemo<Column<Voucher>[]>(
    () => [
      ...(isNarrow
        ? []
        : [
            {
              key: "icon",
              header: "",
              width: isMobile ? "2rem" : "2.5rem",
              align: "center" as const,
              render: () => <Ticket className="dl-icon-1 icon-voucher" />,
            },
          ]),
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
              <span className="vc-name-stack flex-column mw-0">
                {nameRow}
                <span className="vc-name-meta dl-dim">
                  {new Date(item.issueDate).toLocaleDateString("pl-PL")} -{" "}
                  {new Date(item.expiryDate).toLocaleDateString("pl-PL")}
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
        width: isNarrow ? "7.5rem" : isMobile ? "7rem" : "10rem",
        align: "center",
        render: (item) => {
          const isUsed = item.status === VoucherStatus.USED;
          if (!isUsed) {
            return (
              <span className={`rv-status ${STATUS_CLASSES[item.status]}`}>
                {STATUS_LABELS[item.status]}
              </span>
            );
          }
          return (
            <span
              className={`rv-status ${STATUS_CLASSES[item.status]} pointer flex align-items-center g-05`}
              title="Pokaż Wizytę"
              onClick={(e) => {
                e.stopPropagation();
                setSelectedVoucherIdForVisit(item.id);
              }}
            >
              {!isNarrow && <Eye className="dl-icon-1" />}
              <span className="rv-status-text">{STATUS_LABELS[item.status]}</span>
            </span>
          );
        },
      },
      ...(isMobile
        ? []
        : [
            {
              key: "issueDate",
              header: "Ważny od",
              width: "8rem",
              align: "center" as const,
              render: (item: Voucher) => (
                <span className="dl-dim">
                  {new Date(item.issueDate).toLocaleDateString("pl-PL")}
                </span>
              ),
            },
            {
              key: "expiryDate",
              header: "Ważny do",
              width: "8rem",
              align: "center" as const,
              render: (item: Voucher) => (
                <span className="dl-dim">
                  {new Date(item.expiryDate).toLocaleDateString("pl-PL")}
                </span>
              ),
            },
          ]),
      {
        key: "value",
        header: "Wartość",
        width: isMobile ? "2.75rem" : "7rem",
        align: "center",
        render: (item) => <span className="sv-price">{item.value} zł</span>,
      },
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "1.9rem" : "6rem",
        align: "center",
        render: (item) =>
          isMobile ? (
            <div className="flex align-items-center justify-center">
              <RowActionMenu
                items={[
                  ...(canManage(item)
                    ? [
                        {
                          label: "Edytuj",
                          icon: <SquarePen className="dl-icon c-blue" />,
                          onClick: () => setEditVoucherId?.(item.id),
                        },
                        {
                          label: "Usuń",
                          icon: <Trash2 className="dl-icon c-red" />,
                          onClick: () => setRemoveVoucherId?.(item.id),
                          danger: true,
                        },
                      ]
                    : []),
                  ...(item.purchaseVisitId
                    ? [
                        {
                          label: "Podgląd wizyty",
                          icon: <Eye className="dl-icon" />,
                          onClick: () =>
                            setPreviewVisitId(item.purchaseVisitId!),
                        },
                      ]
                    : []),
                ]}
              />
            </div>
          ) : (
          <div className="flex align-items-center justify-center g-05">
            {canManage(item) && (
              <>
                <ActionButton
                  iconTitle={"Edytuj Voucher"}
                  onClick={(e) => handleOnClickEdit(e, item)}
                  disableText={true}
                  disableBorder={true}
                >
                  <SquarePen className="dl-icon c-blue" />
                </ActionButton>
                <ActionButton
                  iconTitle={"Usuń Voucher"}
                  onClick={(e) => handleOnClickRemove(e, item)}
                  disableText={true}
                  disableBorder={true}
                >
                  <Trash2 className="dl-icon c-red" />
                </ActionButton>
              </>
            )}
            {item.purchaseVisitId && (
              <ActionButton
                iconTitle={"Podgląd Wizyty zakupu Vouchera"}
                onClick={(e) => {
                  e.stopPropagation();
                  setPreviewVisitId(item.purchaseVisitId!);
                }}
                disableText={true}
                disableBorder={true}
              >
                <Eye className="dl-icon" />
              </ActionButton>
            )}
          </div>
          ),
      },
    ],
    [
      canManage,
      isMobile,
      isNarrow,
      handleOnClickEdit,
      handleOnClickRemove,
      setEditVoucherId,
      setRemoveVoucherId,
    ]
  );

  return (
    <>
      <DataList<Voucher>
        columns={columns}
        items={items}
        getRowKey={(item) => item.id}
        onRowClick={(item) => item.purchaseVisitId && setPreviewVisitId(item.purchaseVisitId)}
        isRowClickable={(item) => !!item.purchaseVisitId}
        isLoading={isLoading}
        hideHeader={isMobile}
        emptyText="Brak voucherów"
        className={className}
      />

      {selectedVoucherIdForVisit !== null && (
        <VisitPreviewModal
          onClose={() => setSelectedVoucherIdForVisit(null)}
          voucherId={selectedVoucherIdForVisit}
        />
      )}
      {previewVisitId && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
    </>
  );
}

export default VoucherDataList;
