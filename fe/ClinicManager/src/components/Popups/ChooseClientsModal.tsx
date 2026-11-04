import { useEffect, useState, useMemo } from "react";
import { Check } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import Modal from "../Modal";
import SearchBar from "../SearchBar";
import DataList, { Column } from "../DataList";
import { Client } from "../../models/client";
import ClientService from "../../services/ClientService";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface ChooseClientsModalProps {
  onClose: () => void;
  handleSelectClients: (client: Client) => void;
  selectedClients: Client[];
  onSave: () => void;
  className?: string;
}

export function ChooseClientsModal({
  onClose,
  handleSelectClients,
  selectedClients,
  onSave,
  className = "",
}: ChooseClientsModalProps) {
  const [keyword, setKeyword] = useState<string>("");
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchClients = async (): Promise<void> => {
    setLoading(true);
    ClientService.getClients({ keyword: keyword })
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
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchClients();
  }, [keyword]);

  const isSelected = (client: Client): boolean =>
    selectedClients.some((c) => c.id === client.id);

  const columns = useMemo<Column<Client>[]>(
    () => [
      {
        key: "client",
        header: "Klient",
        grow: true,
        render: (client) => (
          <span className={`dl-name ${isSelected(client) ? "pc-y" : ""}`}>
            {client.firstName} {client.lastName}
          </span>
        ),
      },
      {
        key: "discount",
        header: "Obecny Rabat",
        width: isMobile ? "4rem" : "9rem",
        align: isMobile ? "end" : "center",
        render: (client) => (
          <span className="dl-dim">{client.discount?.name ?? "-"}</span>
        ),
      },
      ...(isMobile
        ? []
        : [
            {
              key: "visitsCount",
              header: "Wizyty",
              width: "6rem",
              align: "center" as const,
              render: (client: Client) => (
                <span className="dl-dim">{client.visitsCount}</span>
              ),
            },
          ]),
      {
        key: "selected",
        header: "",
        width: isMobile ? "1.75rem" : "2.5rem",
        align: "center",
        render: (client) =>
          isSelected(client) ? <Check className="dl-icon-1 c-green" /> : null,
      },
    ],
    [selectedClients, isMobile]
  );

  return (
    <Modal
      title={`Wybierz Klientów (${selectedClients.length})`}
      onClose={onClose}
      size="lg"
      height="tall"
      bodyClassName="nested"
      onConfirm={onSave}
      className={className}
    >
      <section className="cc-filters modal-filters flex width-max align-items-center">
        <SearchBar onKeywordChange={setKeyword} />
      </section>

      <DataList<Client>
        columns={columns}
        items={clients}
        getRowKey={(client) => client.id}
        onRowClick={handleSelectClients}
        isLoading={loading}
        hideHeader={isMobile}
        emptyText="Brak klientów"
        className="cc-list"
      />
    </Modal>
  );
}

export default ChooseClientsModal;
