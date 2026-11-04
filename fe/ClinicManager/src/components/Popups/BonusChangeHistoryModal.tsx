import { useEffect, useMemo, useState } from "react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import EmployeeService from "../../services/EmployeeService";
import { BonusChangePoint } from "../../models/employee";
import { MONTHS } from "../../utils/dateUtils";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import LabeledRow from "../LabeledRow";

export interface BonusChangeHistoryModalProps {
  employeeId: number;
  employeeName?: string;
  onClose: () => void;
  className?: string;
}

const formatPercent = (value: number | null): string =>
  value == null
    ? ""
    : `${value.toLocaleString("pl-PL", { maximumFractionDigits: 2 })}%`;

const formatDelta = (change: number): string =>
  `${change > 0 ? "+" : ""}${change.toLocaleString("pl-PL", {
    maximumFractionDigits: 2,
  })}%`;

const renderValue = (value: number | null, change: number | null) => (
  <>
    <span className={`tnum ${value != null ? "bch-value pc-y" : "bch-none"}`}>
      {formatPercent(value)}
    </span>
    {value != null && change != null && (
      <span className={`tnum bch-delta ${change > 0 ? "up" : "down"}`}>
        {formatDelta(change)}
      </span>
    )}
  </>
);

export function BonusChangeHistoryModal({
  employeeId,
  employeeName,
  onClose,
  className = "",
}: BonusChangeHistoryModalProps) {
  const [points, setPoints] = useState<BonusChangePoint[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  useEffect(() => {
    setLoading(true);
    EmployeeService.getBonusChangeHistory(employeeId)
      .then((data) => setPoints(data ?? []))
      .catch((error) => {
        setPoints([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching bonus change history: ", error);
      })
      .finally(() => setLoading(false));
  }, [employeeId]);

  const hasBonus = useMemo(
    () => points.some((p) => p.bonusPercent != null),
    [points]
  );
  const hasSaleBonus = useMemo(
    () => points.some((p) => p.saleBonusPercent != null),
    [points]
  );

  const columns = useMemo<Column<BonusChangePoint>[]>(() => {
    const result: Column<BonusChangePoint>[] = [
      {
        key: "period",
        header: "Miesiąc",
        grow: true,
        render: (point) => (
          <span className="dl-name bch-period">
            {MONTHS[point.month - 1]?.name} {point.year}
          </span>
        ),
      },
    ];
    if (hasBonus) {
      result.push({
        key: "bonusPercent",
        header: "Premia utarg",
        width: "10rem",
        align: "end",
        render: (point) =>
          renderValue(point.bonusPercent, point.bonusPercentChange),
      });
    }
    if (hasSaleBonus) {
      result.push({
        key: "saleBonusPercent",
        header: "Premia produktowa",
        width: "12rem",
        align: "end",
        render: (point) =>
          renderValue(point.saleBonusPercent, point.saleBonusPercentChange),
      });
    }
    return result;
  }, [hasBonus, hasSaleBonus]);

  const mobileColumns = useMemo<Column<BonusChangePoint>[]>(
    () => [
      {
        key: "period",
        grow: true,
        render: (point) => (
          <span className="dl-name bch-period">
            {MONTHS[point.month - 1]?.name} {point.year}
          </span>
        ),
      },
      {
        key: "values",
        width: "12rem",
        render: (point) => (
          <span className="sh-m-comp flex-column width-max">
            {hasBonus && (
              <LabeledRow label="Premia utarg" valueClassName="flex align-items-center g-05">
                {renderValue(point.bonusPercent, point.bonusPercentChange)}
              </LabeledRow>
            )}
            {hasSaleBonus && (
              <LabeledRow label="Premia produktowa" valueClassName="flex align-items-center g-05">
                {renderValue(point.saleBonusPercent, point.saleBonusPercentChange)}
              </LabeledRow>
            )}
          </span>
        ),
      },
    ],
    [hasBonus, hasSaleBonus]
  );

  return (
    <Modal
      title={
        employeeName ? `Historia awansów - ${employeeName}` : "Historia awansów"
      }
      onClose={onClose}
      size="md"
      className={className}
    >
      <DataList<BonusChangePoint>
        columns={isMobile ? mobileColumns : columns}
        hideHeader={isMobile}
        items={points}
        getRowKey={(point) => `${point.year}-${point.month}`}
        isLoading={loading}
        emptyText="Brak historii zmian premii"
        className="bch-list"
      />
    </Modal>
  );
}

export default BonusChangeHistoryModal;
