import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { Users, Settings2 } from "lucide-react";
import ActionButton from "../ActionButton";
import { Client } from "../../models/client";
import ChooseClientsModal from "../Popups/ChooseClientsModal";

export interface DisplayClientsListProps {
  className?: string;
  clients: Client[];
  onClientsChange: (clients: Client[]) => void;
}

export function DisplayClientsList({
  className = "",
  clients,
  onClientsChange,
}: DisplayClientsListProps) {
  const [isManageClientsPopupOpen, setIsManageClientsPopupOpen] =
    useState<boolean>(false);
  const [tempClients, setTempClients] = useState<Client[]>(clients);

  useEffect(() => {
    if (isManageClientsPopupOpen) {
      setTempClients(clients);
    }
  }, [isManageClientsPopupOpen, clients]);

  const handleToggleClient = useCallback((selected: Client) => {
    setTempClients((prev) =>
      prev.some((c) => c.id === selected.id)
        ? prev.filter((c) => c.id !== selected.id)
        : [...prev, selected]
    );
  }, []);

  const handleSaveClients = useCallback(() => {
    onClientsChange(tempClients);
    setIsManageClientsPopupOpen(false);
  }, [tempClients, onClientsChange]);

  return (
    <div className={`dc-clients flex-column width-max g-075 ${className}`}>
      <div className="flex align-items-center space-between g-1">
        <span className="flex align-items-center g-05">
          <Users className="dl-icon-1 pc-y" />
          <span className="sv-variants-title">Klienci</span>
          <span className="dl-dim">{clients.length}</span>
        </span>
        <ActionButton
          text={"Zarządzaj"}
          onClick={() => setIsManageClientsPopupOpen(true)}
        >
          <Settings2 className="dl-icon-1" />
        </ActionButton>
      </div>

      <div className="dc-clients-list ur-list scrollbar flex-column">
        {clients.map((client) => (
          <div key={client.id} className="ur-item flex align-items-center g-05">
            <Link
              to={`/client/${client.id}`}
              className="ur-name pd-product-link"
              title="Otwórz stronę klienta"
            >
              {client.firstName} {client.lastName}
            </Link>
          </div>
        ))}
      </div>

      {isManageClientsPopupOpen && (
        <ChooseClientsModal
          onClose={() => setIsManageClientsPopupOpen(false)}
          handleSelectClients={handleToggleClient}
          selectedClients={tempClients}
          onSave={handleSaveClients}
        />
      )}
    </div>
  );
}

export default DisplayClientsList;
