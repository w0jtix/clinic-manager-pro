import { useEffect, useMemo, useState } from "react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import ExpenseService from "../../services/ExpenseService";
import {
  CompanyExpense,
  SalaryComponent,
  getSalaryComponentDisplay,
} from "../../models/expense";
import { formatDate } from "../../utils/dateUtils";
import SalaryHistoryChart from "../Business/SalaryHistoryChart";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import LabeledRow from "../LabeledRow";
import { formatCurrency } from "../../utils/formatUtils";

export interface SalaryHistoryModalProps {
  employeeId: number;
  employeeName?: string;
  onClose: () => void;
  className?: string;
}

const componentSum = (
  expense: CompanyExpense,
  component: SalaryComponent
): number =>
  expense.expenseItems
    .filter((item) => item.salaryComponent === component)
    .reduce((sum, item) => sum + item.price * item.quantity, 0);

export function SalaryHistoryModal({
  employeeId,
  employeeName,
  onClose,
  className = "",
}: SalaryHistoryModalProps) {
  const [payouts, setPayouts] = useState<CompanyExpense[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile, isNarrow } = useBreakpoint();

  useEffect(() => {
    setLoading(true);
    ExpenseService.getSalaryHistory(employeeId)
      .then((data) => setPayouts(data ?? []))
      .catch((error) => {
        setPayouts([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Salary history: ", error);
      })
      .finally(() => setLoading(false));
  }, [employeeId]);

  const hasSaleBonus = useMemo(
    () =>
      payouts.some((expense) =>
        expense.expenseItems.some(
          (item) => item.salaryComponent === SalaryComponent.SALE_BONUS
        )
      ),
    [payouts]
  );

  const columns = useMemo<Column<CompanyExpense>[]>(() => {
    const result: Column<CompanyExpense>[] = [
      {
        key: "source",
        header: "Wypłata",
        grow: true,
        render: (expense) => (
          <span className="flex-column">
            <span className="dl-name">{expense.source}</span>
            <span className="dl-dim">{formatDate(expense.expenseDate)}</span>
          </span>
        ),
      },
      {
        key: "base",
        header: getSalaryComponentDisplay(SalaryComponent.BASE),
        width: "7rem",
        align: "end",
        render: (expense) => (
          <span className="tnum">
            {formatCurrency(componentSum(expense, SalaryComponent.BASE))}
          </span>
        ),
      },
      {
        key: "bonus",
        header: getSalaryComponentDisplay(SalaryComponent.BONUS),
        width: "7rem",
        align: "end",
        render: (expense) => (
          <span className="tnum">
            {formatCurrency(componentSum(expense, SalaryComponent.BONUS))}
          </span>
        ),
      },
    ];
    if (hasSaleBonus) {
      result.push({
        key: "saleBonus",
        header: getSalaryComponentDisplay(SalaryComponent.SALE_BONUS),
        width: "9rem",
        align: "end",
        render: (expense) => {
          const value = componentSum(expense, SalaryComponent.SALE_BONUS);
          return (
            <span className={`tnum ${value > 0 ? "" : "bch-none"}`}>
              {value > 0 ? formatCurrency(value) : "-"}
            </span>
          );
        },
      });
    }
    result.push({
      key: "total",
      header: "Razem",
      width: "8rem",
      align: "end",
      render: (expense) => (
        <span className="tnum pc-y">{formatCurrency(expense.totalValue)}</span>
      ),
    });
    return result;
  }, [hasSaleBonus]);

  const mobileColumns = useMemo<Column<CompanyExpense>[]>(() => {
    const componentRow = (
      expense: CompanyExpense,
      component: SalaryComponent
    ) => {
      const value = componentSum(expense, component);
      return (
        <LabeledRow
          label={
            isNarrow && component === SalaryComponent.SALE_BONUS
              ? "Sprzedaż"
              : getSalaryComponentDisplay(component)
          }
          valueClassName={value > 0 ? "" : "bch-none"}
        >
          {value > 0 ? formatCurrency(value) : "-"}
        </LabeledRow>
      );
    };
    const result: Column<CompanyExpense>[] = [
      {
        key: "source",
        grow: true,
        render: (expense) => (
          <span className="exp-m-stack flex-column mw-0">
            <span className="dl-name">{expense.source}</span>
            <span className="dl-dim">{formatDate(expense.expenseDate)}</span>
          </span>
        ),
      },
      {
        key: "components",
        width: isNarrow ? "8.25rem" : "10rem",
        render: (expense) => (
          <span className="sh-m-comp flex-column width-max">
            {componentRow(expense, SalaryComponent.BASE)}
            {componentRow(expense, SalaryComponent.BONUS)}
            {hasSaleBonus && componentRow(expense, SalaryComponent.SALE_BONUS)}
            {isNarrow && (
              <LabeledRow label="Razem" labelClassName="pc-y" valueClassName="pc-y">
                {formatCurrency(expense.totalValue)}
              </LabeledRow>
            )}
          </span>
        ),
      },
      {
        key: "total",
        width: "4.75rem",
        align: "end",
        render: (expense) => (
          <span className="tnum pc-y">{formatCurrency(expense.totalValue)}</span>
        ),
      },
    ];
    return isNarrow ? result.filter((col) => col.key !== "total") : result;
  }, [hasSaleBonus, isNarrow]);

  return (
    <Modal
      title={
        employeeName
          ? `Historia Wynagrodzeń - ${employeeName}`
          : "Historia Wynagrodzeń"
      }
      onClose={onClose}
      size="xl"
      height="tall"
      bodyClassName="nested"
      className={className}
    >
      <SalaryHistoryChart payouts={payouts} loading={loading} />
      <DataList<CompanyExpense>
        columns={isMobile ? mobileColumns : columns}
        hideHeader={isMobile}
        items={payouts}
        getRowKey={(expense) => expense.id}
        isLoading={loading}
        emptyText="Brak zaksięgowanych wypłat"
        className="sh-list"
      />
    </Modal>
  );
}

export default SalaryHistoryModal;
