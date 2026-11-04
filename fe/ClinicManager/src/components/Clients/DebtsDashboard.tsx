import { useEffect, useState, useCallback } from "react";
import { CirclePlus, RotateCw } from "lucide-react";
import { ClientDebt, DebtFilterDTO } from "../../models/debt";
import { useAlert } from "../Alert/AlertProvider";
import ActionButton from "../ActionButton";
import NavigationBar from "../NavigationBar";
import SearchBar from "../SearchBar";
import { Client } from "../../models/client";
import DebtModal from "../Popups/DebtModal";
import ClientDebtService from "../../services/ClientDebtService";
import ClientService from "../../services/ClientService";
import DebtDataList from "./DebtDataList";
import RemoveModal from "../Popups/RemoveModal";
import { AlertType } from "../../models/alert";
import { PaymentStatus } from "../../models/payment";
import FilterDropdown from "../FilterDropdown";
import CycleFilter from "../CycleFilter";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";

const EMPTY_FILTER: DebtFilterDTO = {
  paymentStatus: null,
  keyword: "",
};

export function DebtsDashboard() {
  const [isAddNewDebtPopupOpen, setIsAddNewDebtPopupOpen] =
    useState<boolean>(false);
  const [editDebtId, setEditDebtId] = useState<number | string | null>(null);
  const [removeDebtId, setRemoveDebtId] = useState<number | string | null>(null);
  const [debts, setDebts] = useState<ClientDebt[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [filter, setFilter] = useRestorableState<DebtFilterDTO>("debts.filter", EMPTY_FILTER);
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchDebts = async (): Promise<void> => {
    ClientDebtService.getDebts(filter)
      .then((data) => {
        const sortedData = data.sort((a, b) => b.id - a.id);
        setDebts(sortedData);
      })
      .catch((error) => {
        setDebts([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Debts: ", error);
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

  const toggleStatus = useCallback(() => {
    setFilter((prev) => {
      let nextStatus: PaymentStatus | null = null;

      if (prev.paymentStatus === null) nextStatus = PaymentStatus.UNPAID;
      else if (prev.paymentStatus === PaymentStatus.UNPAID)
        nextStatus = PaymentStatus.PAID;
      else nextStatus = null;

      return { ...prev, paymentStatus: nextStatus };
    });
  }, []);

  const handleResetFilters = useCallback(() => {
    setFilter(EMPTY_FILTER);
    setResetTriggered((prev) => !prev);
  }, []);

  const handleDebtRemove = useCallback(async (): Promise<void> => {
    try {
      if (removeDebtId != null) {
        await ClientDebtService.removeDebt(removeDebtId);
        showAlert("Pomyślnie usunięto dług!", AlertType.SUCCESS);
        setRemoveDebtId(null);
        fetchDebts();
      }
    } catch (error) {
      showAlert("Błąd usuwania długu!", AlertType.ERROR);
    }
  }, [removeDebtId, showAlert]);

  useEffect(() => {
    fetchClients();
  }, []);

  useEffect(() => {
    fetchDebts();
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
        filter.paymentStatus === null
          ? "wszystkie"
          : filter.paymentStatus === PaymentStatus.PAID
          ? "opłacone"
          : "nieopłacone"
      }
      tone={
        filter.paymentStatus === null
          ? null
          : filter.paymentStatus === PaymentStatus.PAID
          ? "green"
          : "red"
      }
      iconTitle="Status płatności"
      onClick={toggleStatus}
    />
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <section className="cl-toolbar width-93 flex align-items-center space-between g-1 mt-1 mb-1">
          <div className="flex align-items-center g-075">
            {isMobile ? (
              <FilterDropdown
                label="Filtry"
                className="rv-filters"
                count={
                  [!!filter.keyword, filter.paymentStatus !== null].filter(Boolean).length
                }
              >
                {searchField}
                <div className="fd-row flex align-items-center g-05">
                  {statusField}
                </div>
              </FilterDropdown>
            ) : (
              <>
                {searchField}
                {statusField}
              </>
            )}
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
            text={isMobile ? "Dług" : "Nowy Dług"}
            iconTitle={"Nowy Dług"}
            onClick={() => setIsAddNewDebtPopupOpen(true)}
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        </section>

        <div className="width-93 flex-column f-1 min-height-0 mb-2">
          <DebtDataList
            className="dt-list"
            items={debts}
            setEditDebtId={setEditDebtId}
            setRemoveDebtId={setRemoveDebtId}
          />
        </div>

        {isAddNewDebtPopupOpen && (
          <DebtModal
            onClose={() => {
              setIsAddNewDebtPopupOpen(false);
              fetchDebts();
            }}
            clients={clients}
            className=""
          />
        )}
        {editDebtId != null && (
          <DebtModal
            onClose={() => {
              setEditDebtId(null);
              fetchDebts();
            }}
            clients={clients}
            className=""
            debtId={editDebtId}
          />
        )}
        {removeDebtId != null && (
          <RemoveModal
            onClose={() => setRemoveDebtId(null)}
            handleRemove={handleDebtRemove}
            warningText={
              "Usunięcie spowoduje utratę informacji o Długu.\n\nDług powstały podczas Wizyty może być usunięty tylko wraz z Wizytą."
            }
          />
        )}
      </div>
    </div>
  );
}

export default DebtsDashboard;
