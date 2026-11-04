import { useEffect, useState, useCallback } from "react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import CashLedgerModal from "./CashLedgerModal";
import { CashLedger, CashLedgerFilterDTO } from "../../models/cash_ledger";
import CashLedgerService from "../../services/CashLedgerService";
import CashLedgerDataList from "../CashLedger/CashLedgerDataList";
import { Action } from "../../models/action";
import { validateCashLedgerForm } from "../../utils/validators";
import PeriodPill from "../PeriodPill";

export interface CashLedgerManageProps {
  onClose: () => void;
  className?: string;
}

export function CashLedgerManage({
  onClose,
  className = "",
}: CashLedgerManageProps) {
  const [cashLedgers, setCashLedgers] = useState<CashLedger[]>([]);
  const [selectedCashLedger, setSelectedCashLedger] =
    useState<CashLedger | null>(null);
  const currentDate = new Date();
  const [filter, setFilter] = useState<CashLedgerFilterDTO>({
    isClosed: true,
    year: currentDate.getFullYear(),
    month: currentDate.getMonth() + 1,
  });
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();

  const fetchCashLedgers = useCallback(
    async (pageNum: number = 0, append: boolean = false): Promise<void> => {
      CashLedgerService.getCashLedgers(filter, pageNum, 30)
        .then((data) => {
          const content = data?.content || [];
          if (append) {
            setCashLedgers((prev) => [...prev, ...content]);
          } else {
            setCashLedgers(content);
          }

          setHasMore(!data.last);
          setPage(pageNum);
        })
        .catch((error) => {
          if (!append) setCashLedgers([]);
          setHasMore(false);
          showAlert("Błąd", AlertType.ERROR);
          console.error("Error fetching Cash Ledgers:", error);
        })
        .finally(() => {
          setLoading(false);
        });
    },
    [filter]
  );

  useEffect(() => {
    fetchCashLedgers(0, false);
    setPage(0);
    setHasMore(true);
  }, [filter, fetchCashLedgers]);


  const handleMonthChange = useCallback(
    (month: number | null) => {
      setFilter((prevFilter) => ({
        ...prevFilter,
        month: month ?? null,
      }));
    },
    []
  );
  const handleYearChange = useCallback(
    (year: number | null) => {
      setFilter((prevFilter) => ({
        ...prevFilter,
        year: year ?? null,
      }));
    },
    []
  );

  const handleEditCashLedger = useCallback(async () => {
    if (!selectedCashLedger || !selectedCashLedger.id) return;
    const cashLedgerBeforeUpdate = cashLedgers.find(
      (cl) => cl.id === selectedCashLedger.id
    );
    const error = validateCashLedgerForm(
      selectedCashLedger,
      cashLedgerBeforeUpdate as CashLedger,
      Action.EDIT
    );
    if (error) {
      showAlert(error, AlertType.ERROR);
      return null;
    }
    CashLedgerService.updateCashLedger(
      selectedCashLedger.id,
      selectedCashLedger
    )
      .then(() => {
        showAlert("Pomyślnie zaktualizowano Kasetkę!", AlertType.SUCCESS);
        setSelectedCashLedger(null);
        fetchCashLedgers(0, false);
      })
      .catch((error) => {
        showAlert("Błąd aktualizacji Kasetki!", AlertType.ERROR);
        console.error("Error updating Cash Ledger", error);
      });
  }, [selectedCashLedger, fetchCashLedgers]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;

      if (scrolledToBottom && hasMore && !loading) {
        fetchCashLedgers(page + 1, true);
      }
    },
    [hasMore, loading, page, filter]
  );

  return (
    <Modal
      title="Historia Kasetki"
      onClose={onClose}
      size="2xl"
      height="tall"
      bodyClassName="nested"
      className={className}
    >
      <div className="clh-filters flex align-items-center g-2 mb-1">
        <PeriodPill
          month={filter.month}
          year={filter.year}
          onMonthChange={handleMonthChange}
          onYearChange={handleYearChange}
        />
      </div>

      <CashLedgerDataList
        items={cashLedgers}
        setSelectedCashLedger={setSelectedCashLedger}
        onScroll={handleScroll}
        isLoading={loading}
      />

      {selectedCashLedger != null && (
        <CashLedgerModal
          onClose={() => setSelectedCashLedger(null)}
          handleCloseCashLedger={handleEditCashLedger}
          cashLedger={selectedCashLedger as CashLedger}
          setCashLedger={setSelectedCashLedger}
          action={Action.EDIT}
        />
      )}
    </Modal>
  );
}

export default CashLedgerManage;
