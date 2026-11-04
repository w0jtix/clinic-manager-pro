import { useAlert } from "../Alert/AlertProvider";
import { useState, useEffect, useCallback } from "react";
import {
  CirclePlus,
  RotateCw,
  Rocket,
  TriangleAlert,
  Coins,
  Percent,
} from "lucide-react";
import { AlertType } from "../../models/alert";
import ActionButton from "../ActionButton";
import NavigationBar from "../NavigationBar";
import SearchBar from "../SearchBar";
import { Client, ClientFilterDTO } from "../../models/client";
import ClientService from "../../services/ClientService";
import ClientModal from "../Popups/ClientModal";
import RemoveModal from "../Popups/RemoveModal";
import ClientDataList from "./ClientDataList";
import DiscountsModal from "../Popups/DiscountsModal";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";
import { useRoles } from "../../hooks/useRoles";

const EMPTY_FILTER: ClientFilterDTO = {
  keyword: "",
  boostClient: null,
  signedRegulations: null,
  hasDebts: null,
};

export function ClientsDashboard() {
  const { isMobile, isCompact } = useBreakpoint();
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const [removeClientId, setRemoveClientId] = useState<string | number | null>(
    null
  );
  const [clients, setClients] = useState<Client[]>([]);
  const [selectedClientId, setSelectedClientId] = useState<number | null>(null);
  const [filter, setFilter] = useRestorableState<ClientFilterDTO>("clients.filter", EMPTY_FILTER);
  const [isAddNewClientPopupOpen, setIsAddNewClientPopupOpen] =
    useState<boolean>(false);
  const [isManageDiscountsPopupOpen, setIsManageDiscountsPopupOpen] =
    useRestorableState<boolean>("clients.discountsOpen", false, {
      skipOnReload: true,
    });
  const { showAlert } = useAlert();
  const { isMaster } = useRoles();

  const fetchClients = async (): Promise<void> => {
    ClientService.getClients(filter)
      .then((data) => {
        const sortedClients = [...data].sort((a, b) =>
          a.firstName.localeCompare(b.firstName, "pl", { sensitivity: "base" })
        );
        setClients(sortedClients);
      })
      .catch((error) => {
        setClients([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Clients: ", error);
      });
  };

  const handleKeywordChange = useCallback((newKeyword: string) => {
    setFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const handleResetFiltersAndData = useCallback(() => {
    setResetTriggered((prev) => !prev);
    setSelectedClientId(null);
    setRemoveClientId(null);
    setFilter(EMPTY_FILTER);
  }, []);

  const handlePopupSuccess = useCallback(
    (message: string) => {
      showAlert(message, AlertType.SUCCESS);
      handleResetFiltersAndData();
    },
    [showAlert, handleResetFiltersAndData]
  );

  const handleBoost = useCallback(() => {
    setFilter((prev) => ({
      ...prev,
      boostClient: prev.boostClient === true ? null : true,
    }));
  }, []);

  const handleTerms = useCallback(() => {
    setFilter((prev) => ({
      ...prev,
      signedRegulations: prev.signedRegulations === false ? null : false,
    }));
  }, []);

  const handleDebt = useCallback(() => {
    setFilter((prev) => ({
      ...prev,
      hasDebts: prev.hasDebts === true ? null : true,
    }));
  }, []);

  const handleClientRemove = useCallback(async () => {
    if (!removeClientId) return;

    const selectedClient = clients.find(
      (client) => client.id === removeClientId
    );
    if (!selectedClient) {
      showAlert("Nie znaleziono klienta.", AlertType.ERROR);
      return;
    }

    ClientService.deleteClient(removeClientId)
      .then(() => {
        handlePopupSuccess(
          `Klient ${
            selectedClient.firstName + " " + selectedClient.lastName
          } usunięty!`
        );
        setRemoveClientId(null);
      })
      .catch((error) => {
        console.error("Error removing Client", error);
        showAlert("Błąd usuwania klienta.", AlertType.ERROR);
      });
  }, [showAlert, removeClientId, clients, handlePopupSuccess]);

  const handleRefresh = useCallback(() => {
    fetchClients();
  }, [fetchClients]);

  useEffect(() => {
    fetchClients();
  }, [resetTriggered, filter]);

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <section className="cl-toolbar cl-toolbar-stack width-93 flex align-items-center space-between g-1 mt-1 mb-1">
          <div className="cl-filters flex align-items-center g-075">
            <SearchBar
              onKeywordChange={handleKeywordChange}
              resetTriggered={resetTriggered}
              initialValue={filter.keyword ?? ""}
            />
            <div className="cat-seg-group cl-quick-filters align-items-center align-self-end g-025">
              <button
                type="button"
                title="Boost"
                className={`cat-seg align-items-center pointer ${
                  filter.boostClient ? "active" : ""
                }`}
                onClick={handleBoost}
              >
                <Rocket className="dl-icon-1 icon-rocket" />
                Boost
              </button>
              <button
                type="button"
                title="Brak Regulaminu"
                className={`cat-seg align-items-center pointer ${
                  filter.signedRegulations === false ? "active" : ""
                }`}
                onClick={handleTerms}
              >
                <TriangleAlert className="dl-icon-1 pc-y" />
                {isCompact ? "Regulamin" : "Brak Regulaminu"}
              </button>
              <button
                type="button"
                title="Posiadający Dług"
                className={`cat-seg align-items-center pointer ${
                  filter.hasDebts ? "active" : ""
                }`}
                onClick={handleDebt}
              >
                <Coins className="dl-icon-1 icon-debt" />
                {isCompact ? "Dług" : "Posiadający Dług"}
              </button>
            </div>
            <ActionButton
              disableText={true}
              iconTitle={"Resetuj filtry"}
              onClick={handleResetFiltersAndData}
              className={"reset align-self-end"}
            >
              <RotateCw className="dl-icon icon-control" />
            </ActionButton>
          </div>

          <div className="cl-action-buttons flex align-items-center g-075">
            {isMaster && (
              <ActionButton
                text={"Rabaty"}
                onClick={() => setIsManageDiscountsPopupOpen(true)}
              >
                <Percent className="dl-icon-1 icon-discount" />
              </ActionButton>
            )}
            <ActionButton
              text={isMobile ? "Klient" : "Nowy Klient"}
              iconTitle={"Nowy Klient"}
              onClick={() => setIsAddNewClientPopupOpen(true)}
            >
              <CirclePlus className="dl-icon-1 c-green" />
            </ActionButton>
          </div>
        </section>

        <div className="width-93 flex-column f-1 min-height-0 mb-2">
          <ClientDataList
            className="cl-list"
            items={clients}
            setRemoveClientId={setRemoveClientId}
            setSelectedClientId={setSelectedClientId}
          />
        </div>

        {isAddNewClientPopupOpen && (
          <ClientModal
            onClose={() => {
              setIsAddNewClientPopupOpen(false);
              setSelectedClientId(null);
            }}
            onReset={handleResetFiltersAndData}
            className={"client-popup"}
          />
        )}
        {selectedClientId != null && (
          <ClientModal
            onClose={() => setSelectedClientId(null)}
            onReset={handleResetFiltersAndData}
            onNotesSaved={handleRefresh}
            selectedClientId={selectedClientId}
            className={"client-popup"}
          />
        )}
        {removeClientId != null && (
          <RemoveModal
            onClose={() => setRemoveClientId(null)}
            warningText={"Usunięcie spowoduje utratę informacji o Kliencie."}
            handleRemove={handleClientRemove}
          />
        )}
        {isMaster && isManageDiscountsPopupOpen && (
          <DiscountsModal
            onClose={() => setIsManageDiscountsPopupOpen(false)}
            onReset={handleResetFiltersAndData}
          />
        )}
      </div>
    </div>
  );
}

export default ClientsDashboard;
