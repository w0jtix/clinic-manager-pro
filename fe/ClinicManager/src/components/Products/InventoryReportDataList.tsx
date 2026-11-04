import React, { useCallback, useMemo, useState } from "react";
import { CircleAlert, CircleCheck, Eye, SquarePen, Trash2 } from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import RemoveModal from "../Popups/RemoveModal";
import StockAdjustmentReportModal from "../Popups/StockAdjustmentReportModal";
import InventoryReportPreviewModal from "../Popups/InventoryReportPreviewModal";
import { formatDate } from "../../utils/dateUtils";
import { InventoryReport } from "../../models/inventory_report";
import { useRoles } from "../../hooks/useRoles";
import InventoryReportService from "../../services/InventoryReportService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface InventoryReportDataListProps {
  inventoryReports: InventoryReport[];
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  onSuccess?: () => void;
  isLoading?: boolean;
  className?: string;
}

const getTotals = (report: InventoryReport) => {
  let positive = 0;
  let negative = 0;
  report.items.forEach((item) => {
    const diff = item.supplyAfter - item.supplyBefore;
    if (diff > 0) positive += diff;
    if (diff < 0) negative += diff;
  });
  return { positive, negative };
};

export function InventoryReportDataList({
  inventoryReports,
  onScroll,
  onSuccess,
  isLoading = false,
  className = "",
}: InventoryReportDataListProps) {
  const { isMaster } = useRoles();
  const [editInventoryReportId, setEditInventoryReportId] = useState<number | null>(
    null
  );
  const [removeInventoryReportId, setRemoveInventoryReportId] = useState<
    number | null
  >(null);
  const [previewInventoryReportId, setPreviewInventoryReportId] = useState<
    number | null
  >(null);
  const { showAlert } = useAlert();
  const { isMobile, isCompact } = useBreakpoint();

  const deleteInventoryReport = useCallback(async () => {
    if (!removeInventoryReportId) return;

    InventoryReportService.deleteInventoryReport(removeInventoryReportId)
      .then(() => {
        showAlert(
          "Pomyślnie usunięto Raport! Przywrócono ilość Produktów sprzed Raportu.",
          AlertType.SUCCESS
        );
        setRemoveInventoryReportId(null);
        onSuccess?.();
      })
      .catch((error: any) => {
        const message =
          error?.response?.status === 409 && error?.response?.data
            ? error.response.data
            : "Błąd podczas usuwania Raportu!";
        showAlert(message, AlertType.ERROR);
        console.error("Error while removing Inventory Report: ", error);
      });
  }, [removeInventoryReportId, showAlert, onSuccess]);

  const columns = useMemo<Column<InventoryReport>[]>(
    () => [
      {
        key: "status",
        header: "Status",
        width: isCompact ? "2rem" : "10rem",
        align: isCompact ? "center" : "start",
        render: (report) =>
          report.approved ? (
            <span
              className="ir-approved flex align-items-center g-5px"
              title="Zatwierdzony"
            >
              <CircleCheck className="dl-icon-1" />
              {!isCompact && "Zatwierdzony"}
            </span>
          ) : (
            <span
              className={`ir-awaits flex align-items-center g-5px ${
                isMaster ? "pending pointer" : ""
              }`}
              title={
                isMaster ? "Sprawdź i zatwierdź" : "Oczekuje na zatwierdzenie"
              }
              onClick={(e) => {
                if (!isMaster) return;
                e.stopPropagation();
                setPreviewInventoryReportId(report.id);
              }}
            >
              <CircleAlert className="dl-icon-1" />
              {!isCompact &&
                (isMaster ? "Sprawdź i zatwierdź" : "Oczekuje na zatwierdzenie")}
            </span>
          ),
      },
      {
        key: "name",
        header: "Nazwa",
        grow: true,
        render: (report) => {
          const nameNode = (
            <span
              className={`dl-name ir-name ${report.approved ? "" : "pending"}`}
            >
              Raport Inwentaryzacyjny #{report.id}
            </span>
          );

          if (isMobile) {
            return (
              <span className="ir-name-stack flex-column mw-0">
                {nameNode}
                <span className="ir-name-meta flex align-items-center g-05">
                  <span className="dl-dim">{formatDate(report.createdAt)}</span>
                  <span className="dl-dim">{report.createdBy.name}</span>
                </span>
              </span>
            );
          }

          return nameNode;
        },
      },
      ...(isMobile
        ? []
        : [
            {
              key: "createdAt",
              header: "Data",
              width: "8rem",
              align: "center" as const,
              render: (report: InventoryReport) => (
                <span className="dl-dim">{formatDate(report.createdAt)}</span>
              ),
            },
            {
              key: "createdBy",
              header: "Użytkownik",
              width: "9rem",
              align: "center" as const,
              render: (report: InventoryReport) => (
                <span className="dl-dim">{report.createdBy.name}</span>
              ),
            },
          ]),
      {
        key: "change",
        header: "Zmiana",
        width: isMobile ? "4rem" : "8rem",
        align: "center",
        render: (report) => {
          const { positive, negative } = getTotals(report);
          return (
            <span className="flex align-items-center g-5px">
              <span
                className={`ir-change ${positive === 0 ? "neutral" : "positive"}`}
              >
                +{positive}
              </span>
              <span className="ir-change neutral">/</span>
              <span
                className={`ir-change ${negative === 0 ? "neutral" : "negative"}`}
              >
                {negative === 0 ? "-0" : negative}
              </span>
            </span>
          );
        },
      },
      {
        key: "options",
        header: "Opcje",
        width: isMobile ? "1.9rem" : "8rem",
        align: "center",
        render: (report) =>
          isMobile ? (
            <div className="flex align-items-center justify-center">
              <RowActionMenu
                items={[
                  {
                    label: "Podgląd",
                    icon: <Eye className="dl-icon" />,
                    onClick: () => setPreviewInventoryReportId(report.id),
                  },
                  ...(report.approved
                    ? []
                    : [
                        {
                          label: "Edytuj",
                          icon: <SquarePen className="dl-icon c-blue" />,
                          onClick: () => setEditInventoryReportId(report.id),
                        },
                        {
                          label: "Usuń",
                          icon: <Trash2 className="dl-icon c-red" />,
                          onClick: () => setRemoveInventoryReportId(report.id),
                          danger: true,
                        },
                      ]),
                ]}
              />
            </div>
          ) : (
          <div className="flex align-items-center justify-center g-05">
            <ActionButton
              iconTitle={"Podgląd Raportu"}
              onClick={(e) => {
                e.stopPropagation();
                setPreviewInventoryReportId(report.id);
              }}
              disableText={true}
              disableBorder={true}
            >
              <Eye className="dl-icon" />
            </ActionButton>
            {!report.approved && (
              <>
                <ActionButton
                  iconTitle={"Edytuj Raport"}
                  onClick={(e) => {
                    e.stopPropagation();
                    setEditInventoryReportId(report.id);
                  }}
                  disableText={true}
                  disableBorder={true}
                >
                  <SquarePen className="dl-icon c-blue" />
                </ActionButton>
                <ActionButton
                  iconTitle={"Usuń Raport"}
                  onClick={(e) => {
                    e.stopPropagation();
                    setRemoveInventoryReportId(report.id);
                  }}
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
    [isMaster, isMobile, isCompact]
  );

  return (
    <div className={`min-height-0 width-93 f-1 flex-column align-self-center mb-2 ${className}`}>
      <DataList<InventoryReport>
        columns={columns}
        items={inventoryReports}
        getRowKey={(report) => report.id}
        onRowClick={(report) => setPreviewInventoryReportId(report.id)}
        onScroll={onScroll}
        isLoading={isLoading}
        hideHeader={isMobile}
        emptyText="Brak raportów"
        className={className}
      />

      {previewInventoryReportId != null && (
        <InventoryReportPreviewModal
          onClose={() => setPreviewInventoryReportId(null)}
          onApproved={onSuccess}
          inventoryReportId={previewInventoryReportId}
        />
      )}
      {editInventoryReportId != null && (
        <StockAdjustmentReportModal
          onClose={() => setEditInventoryReportId(null)}
          onSuccess={onSuccess}
          inventoryReportId={editInventoryReportId}
        />
      )}
      {removeInventoryReportId != null && (
        <RemoveModal
          onClose={() => setRemoveInventoryReportId(null)}
          handleRemove={() => deleteInventoryReport()}
          warningText={
            "Ilość Produktów zostanie przywrócona do stanu sprzed Raportu."
          }
        />
      )}
    </div>
  );
}

export default InventoryReportDataList;
