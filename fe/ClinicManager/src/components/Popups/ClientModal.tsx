import { useState, useEffect, useCallback, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { CirclePlus, Flag, RotateCcw, Rocket, TriangleAlert, X } from "lucide-react";
import { Action } from "../../models/action";
import {
  Client,
  ClientNote,
  NewClient,
  NewClientNote,
} from "../../models/client";
import Modal from "../Modal";
import TextInput from "../TextInput";
import ActionButton from "../ActionButton";
import { AVAILABLE_AVATARS } from "../../constants/avatars";
import { useAlert } from "../Alert/AlertProvider";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";
import { AlertType } from "../../models/alert";
import { formatDate } from "../../utils/dateUtils";
import {
  validateClientForm,
  validateClientNoteForm,
} from "../../utils/validators";
import { extractClientErrorMessage } from "../../utils/errorHandler";
import ClientService from "../../services/ClientService";
import ClientNoteService from "../../services/ClientNoteService";
import UserService from "../../services/UserService";
// import DateInput from "../DateInput";
import BirthDateField from "../ClientCards/BirthDateField";
import DuplicateClientNotice from "../ClientCards/DuplicateClientNotice";
import {
  buildDuplicateClientMessage,
  getDuplicateClientError,
} from "../../utils/clientDuplicateUtils";

export interface ClientModalProps {
  onClose: () => void;
  onReset?: () => void;
  onNotesSaved?: () => void;
  selectedClientId?: number | null;
  onSelectClient?: (client: Client) => void;
  autoAddNote?: boolean;
  className?: string;
}

export function ClientModal({
  onClose,
  onReset,
  onNotesSaved,
  selectedClientId,
  onSelectClient,
  autoAddNote = false,
  className = "",
}: ClientModalProps) {
  const [clientDTO, setClientDTO] = useState<NewClient>({
    firstName: "",
    lastName: "",
    signedRegulations: true,
    boostClient: false,
    redFlag: false,
    phoneNumber: null,
    birthDate: null,
    email: null,
    address: null,
    postalCode: null,
    city: null,
  });
  const [fetchedClient, setFetchedClient] = useState<Client | null>(null);
  const [newClientNotesDTO, setNewClientNotesDTO] = useState<NewClientNote[]>([]);
  const [existingClientNotes, setExistingClientNotes] = useState<ClientNote[]>([]);
  const [existingClientNotesIdsToRemove, setExistingClientNotesIdsToRemove] =
    useState<number[]>([]);
  const [employeeUsersMap, setEmployeeUsersMap] = useState<Map<number, any>>(
    new Map()
  );
  const [restoring, setRestoring] = useState<boolean>(false);
  const { user } = useUser();
  const { isMaster } = useRoles();
  const { showAlert } = useAlert();
  const navigate = useNavigate();

  const action = selectedClientId ? Action.EDIT : Action.CREATE;
  const isDeletedClient = action === Action.EDIT && !!fetchedClient?.isDeleted;

  const fetchClientById = async (clientId: number) => {
    ClientService.getClientById(clientId)
      .then((data) => {
        setClientDTO({
          id: data.id,
          firstName: data.firstName,
          lastName: data.lastName,
          signedRegulations: data.signedRegulations,
          boostClient: data.boostClient,
          redFlag: data.redFlag,
          phoneNumber: data.phoneNumber,
          birthDate: data.birthDate ?? null,
          email: data.email,
          address: data.address,
          postalCode: data.postalCode,
          city: data.city,
          discount: data.discount,
        });
        setFetchedClient(data);
      })
      .catch((error) => {
        console.error("Error fetching Client!", error);
        showAlert("Błąd", AlertType.ERROR);
      });
  };

  const fetchClientNotes = async (clientId: number) => {
    ClientNoteService.getClientNotesByClientId(clientId)
      .then((data) => {
        const sortedNotes = [...data].sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        setExistingClientNotes(sortedNotes);
      })
      .catch((error) => {
        console.error("Error fetching ClientNotes!", error);
        showAlert("Błąd", AlertType.ERROR);
      });
  };

  const fetchUserByEmployeeId = useCallback(
    async (employeeId: number) => {
      if (employeeUsersMap.has(employeeId)) return;

      try {
        const userData = await UserService.getUserByEmployeeId(employeeId);
        setEmployeeUsersMap((prev) => new Map(prev).set(employeeId, userData));
      } catch (error) {
        console.error(`Failed to fetch user for employee ${employeeId}:`, error);
      }
    },
    [employeeUsersMap]
  );

  useEffect(() => {
    if (selectedClientId) {
      fetchClientById(selectedClientId);
      fetchClientNotes(selectedClientId);
    }
  }, [selectedClientId]);

  useEffect(() => {
    existingClientNotes.forEach((note) => {
      if (note.createdBy?.id) {
        fetchUserByEmployeeId(note.createdBy.id);
      }
    });
  }, [existingClientNotes, fetchUserByEmployeeId]);

  const handleFirstNameChange = useCallback((firstName: string) => {
    setClientDTO((prev) => ({ ...prev, firstName }));
  }, []);

  const handleLastNameChange = useCallback((lastName: string) => {
    setClientDTO((prev) => ({ ...prev, lastName }));
  }, []);

  const handlePhoneNumberChange = useCallback((phoneNumber: string) => {
    setClientDTO((prev) => ({
      ...prev,
      phoneNumber: phoneNumber === "" ? null : phoneNumber,
    }));
  }, []);

  const handleBirthDateChange = useCallback((birthDate: string | null) => {
    setClientDTO((prev) => ({ ...prev, birthDate }));
  }, []);

  const handleUseExistingClient = useCallback(
    async (clientId: number) => {
      try {
        const existing = await ClientService.getClientById(clientId);
        onReset?.();
        onClose();
        if (onSelectClient) {
          onSelectClient(existing);
        } else {
          navigate(`/client/${clientId}`);
        }
      } catch (error) {
        console.error("Error fetching existing Client!", error);
        showAlert("Błąd", AlertType.ERROR);
      }
    },
    [onSelectClient, onReset, onClose, navigate, showAlert]
  );

  const handleRestoreClient = useCallback(async () => {
    if (!fetchedClient || restoring) return;
    setRestoring(true);
    try {
      await ClientService.restoreClient(fetchedClient.id);
      showAlert(
        `Klient ${fetchedClient.firstName} ${fetchedClient.lastName} przywrócony!`,
        AlertType.SUCCESS
      );
      fetchClientById(fetchedClient.id);
      onReset?.();
    } catch (error) {
      const duplicate = getDuplicateClientError(error);
      showAlert(
        duplicate
          ? buildDuplicateClientMessage(duplicate, true)
          : "Błąd przywracania klienta.",
        AlertType.ERROR
      );
    } finally {
      setRestoring(false);
    }
  }, [fetchedClient, restoring, showAlert, onReset]);

  const handleAddressFieldChange = useCallback(
    (key: "address" | "postalCode" | "city", value: string) => {
      setClientDTO((prev) => ({
        ...prev,
        [key]: value === "" ? null : value,
      }));
    },
    []
  );

  const handleEmailChange = useCallback((email: string) => {
    setClientDTO((prev) => ({
      ...prev,
      email: email === "" ? null : email,
    }));
  }, []);

  const handleSignedRegulations = useCallback(() => {
    setClientDTO((prev) => ({
      ...prev,
      signedRegulations: !prev.signedRegulations,
    }));
  }, []);

  const handleBoostClient = useCallback(() => {
    setClientDTO((prev) => ({ ...prev, boostClient: !prev.boostClient }));
  }, []);

  const handleRedFlag = useCallback(() => {
    setClientDTO((prev) => ({ ...prev, redFlag: !prev.redFlag }));
  }, []);

  const addNote = useCallback(() => {
    const noteClientId = selectedClientId ?? (clientDTO as Client).id;
    if (!noteClientId) return;

    const newNote: NewClientNote = {
      client: { ...(clientDTO as Client), id: noteClientId },
      content: "",
      createdAt: new Date().toISOString().split("T")[0],
      createdBy: user?.employee ?? null,
    };

    setNewClientNotesDTO((prev) => [...prev, newNote]);
  }, [user, clientDTO, selectedClientId]);

  const autoAddNoteExecuted = useRef(false);

  useEffect(() => {
  if (autoAddNote && !autoAddNoteExecuted.current && !isDeletedClient) {
    autoAddNoteExecuted.current = true;
    addNote();
  }
}, [autoAddNote, addNote]);

  const handleNewNoteContentUpdate = useCallback(
    (content: string, index: number) => {
      setNewClientNotesDTO((prev) =>
        prev.map((note, i) => (i === index ? { ...note, content } : note))
      );
    },
    []
  );

  const handleExistingNoteRemove = useCallback((noteId: number) => {
    setExistingClientNotesIdsToRemove((prev) => [...prev, noteId]);
    setExistingClientNotes((prev) => prev.filter((note) => note.id !== noteId));
  }, []);

  const handleClientAction = useCallback(async () => {
    try {
      if (action === Action.CREATE) {
        const error = validateClientForm(clientDTO, action, null);
        if (error) {
          showAlert(error, AlertType.ERROR);
          return;
        }

        const createdClient = await ClientService.createClient(
          clientDTO as NewClient
        );
        onSelectClient?.(createdClient);
        showAlert(
          `Klient ${clientDTO.firstName + " " + clientDTO.lastName} utworzony!`,
          AlertType.SUCCESS
        );
        onReset?.();
        onClose();
      } else {
        const clientError = isDeletedClient
          ? "Brak zmian!"
          : validateClientForm(clientDTO, action, fetchedClient);
        const hasClientChanges = clientError !== "Brak zmian!";

        const notesError = validateClientNoteForm(newClientNotesDTO);
        const hasNewNotes = newClientNotesDTO.length > 0 && !notesError;
        const hasNotesToRemove = existingClientNotesIdsToRemove.length > 0;

        if (!hasClientChanges && !hasNewNotes && !hasNotesToRemove) {
          showAlert("Nie wykryto żadnych zmian", AlertType.ERROR);
          return;
        }

        if (clientError && clientError !== "Brak zmian!") {
          showAlert(clientError, AlertType.ERROR);
          return;
        }

        if (notesError && newClientNotesDTO.length > 0) {
          showAlert(notesError, AlertType.ERROR);
          return;
        }

        if (hasNotesToRemove) {
          await Promise.all(
            existingClientNotesIdsToRemove.map((id) =>
              ClientNoteService.removeClientNote(id)
            )
          );
        }

        if (hasClientChanges && fetchedClient) {
          await ClientService.updateClient(
            fetchedClient.id,
            clientDTO as NewClient
          );
          onClose();
        }

        if (hasNewNotes) {
          await Promise.all(
            newClientNotesDTO.map((note) =>
              ClientNoteService.createClientNote(note)
            )
          );
        }

        showAlert(
          `Klient ${
            clientDTO.firstName + " " + clientDTO.lastName
          } zaktualizowany!`,
          AlertType.SUCCESS
        );
        if (hasClientChanges) {
          onReset?.();
        } else {
          onNotesSaved?.();
          setExistingClientNotesIdsToRemove([]);
          setNewClientNotesDTO([]);
          fetchClientNotes(selectedClientId!);
        }
      }
    } catch (error) {
      console.error(
        `Error ${action === Action.CREATE ? "creating" : "updating"} client:`,
        error
      );
      const duplicate = getDuplicateClientError(error);
      const errorMessage = duplicate
        ? buildDuplicateClientMessage(duplicate, isMaster)
        : extractClientErrorMessage(error, action);
      showAlert(errorMessage, AlertType.ERROR);
    }
  }, [
    clientDTO,
    action,
    fetchedClient,
    selectedClientId,
    newClientNotesDTO,
    existingClientNotesIdsToRemove,
    onReset,
    onNotesSaved,
    onClose,
    showAlert,
    onSelectClient,
    isDeletedClient,
    isMaster,
  ]);

  const isEdit = action === Action.EDIT;

  return (
    <Modal
      dirtyValue={[clientDTO, newClientNotesDTO, existingClientNotesIdsToRemove]}
      title={
        action === Action.CREATE ? "Dodaj Nowego Klienta" : "Edytuj Klienta"
      }
      onClose={onClose}
      size="md"
      height={isEdit ? "tall" : "auto"}
      onConfirm={handleClientAction}
      className={`cl-modal ${className}`}
    >
      {isDeletedClient && (
        <section className="cl-deleted-banner flex align-items-center space-between g-075 mb-1">
          <span className="flex align-items-center g-05">
            <TriangleAlert className="dl-icon-1 c-red" />
            <span className="flex-column">
              <span className="cl-deleted-title">Klient usunięty</span>
              <span className="cl-deleted-sub">
                {isMaster
                  ? "Przywróć klienta, aby edytować jego dane."
                  : "Edycja danych usuniętego klienta jest niedostępna."}
              </span>
            </span>
          </span>
          {isMaster && (
            <ActionButton
              text={"Przywróć klienta"}
              onClick={handleRestoreClient}
              disabled={restoring}
              className="cl-restore"
            >
              <RotateCcw className="dl-icon-1 c-green" />
            </ActionButton>
          )}
        </section>
      )}

      <section className="cl-toggles flex align-items-center g-075">
        <div className="cat-seg-group cl-flags flex align-items-center width-fit-content g-025">
          <button
            type="button"
            title="Klient kontrowersyjny"
            disabled={isDeletedClient}
            className={`cat-seg cl-seg-red align-items-center pointer ${
              clientDTO.redFlag ? "active" : ""
            }`}
            onClick={handleRedFlag}
          >
            <Flag className="dl-icon-1 c-red" />
            Red Flag
          </button>
          {action === Action.CREATE && (
            <button
              type="button"
              title="Klient pozyskany przez Boost"
              className={`cat-seg cl-seg-boost align-items-center pointer ${
                clientDTO.boostClient ? "active" : ""
              }`}
              onClick={handleBoostClient}
            >
              <Rocket className="dl-icon-1 icon-rocket" />
              Klient z Boosta
            </button>
          )}
        </div>

        {action === Action.CREATE && <span className="cl-toggles-break" />}

        <div className="cat-seg-group flex align-items-center width-fit-content g-025">
          <span className="cat-seg-label">Regulamin:</span>
          <button
            type="button"
            disabled={isDeletedClient}
            className={`cat-seg cl-yes align-items-center pointer ${
              clientDTO.signedRegulations ? "active" : ""
            }`}
            onClick={() => {
              if (!clientDTO.signedRegulations) handleSignedRegulations();
            }}
          >
            TAK
          </button>
          <button
            type="button"
            disabled={isDeletedClient}
            className={`cat-seg cl-no align-items-center pointer ${
              !clientDTO.signedRegulations ? "active" : ""
            }`}
            onClick={() => {
              if (clientDTO.signedRegulations) handleSignedRegulations();
            }}
          >
            NIE
          </button>
        </div>
      </section>

      <div className="flex-column g-075 mt-1">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Imię:</span>
          <TextInput
            dropdown={false}
            disabled={isDeletedClient}
            className="in-full"
            value={clientDTO.firstName}
            onSelect={(value) => {
              if (typeof value === "string") handleFirstNameChange(value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwisko:</span>
          <TextInput
            dropdown={false}
            disabled={isDeletedClient}
            className="in-full"
            value={clientDTO.lastName}
            onSelect={(value) => {
              if (typeof value === "string") handleLastNameChange(value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nr kontaktowy:</span>
          <TextInput
            dropdown={false}
            disabled={isDeletedClient}
            className="in-full"
            numbersOnly={true}
            value={clientDTO.phoneNumber ?? ""}
            onSelect={(value) => {
              if (typeof value === "string") handlePhoneNumberChange(value);
            }}
          />
        </div>
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Data urodzenia:</span>
          {/* <DateInput
            onChange={(date) => handleBirthDateChange(date)}
            selectedDate={clientDTO.birthDate ?? null}
            showPlaceholder={true}
            placeholder="Opcjonalnie"
            maxDate={new Date()}
            yearDropdownItemNumber={110}
            disabled={isDeletedClient}
          /> */}
          <BirthDateField
            value={clientDTO.birthDate ?? null}
            onChange={handleBirthDateChange}
            disabled={isDeletedClient}
          />
        </div>
        {!isDeletedClient && (
          <DuplicateClientNotice
            firstName={clientDTO.firstName}
            lastName={clientDTO.lastName}
            phoneNumber={clientDTO.phoneNumber}
            excludeId={isEdit ? selectedClientId : null}
            onUseClient={isEdit ? undefined : handleUseExistingClient}
          />
        )}
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">E-mail:</span>
          <TextInput
            dropdown={false}
            disabled={isDeletedClient}
            className="in-full"
            noCapitalize={true}
            value={clientDTO.email ?? ""}
            onSelect={(value) => {
              if (typeof value === "string") handleEmailChange(value);
            }}
          />
        </div>
        {isMaster && (
          <>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Adres:</span>
              <TextInput
                dropdown={false}
                disabled={isDeletedClient}
                className="in-full"
                value={clientDTO.address ?? ""}
                onSelect={(value) => {
                  if (typeof value === "string")
                    handleAddressFieldChange("address", value);
                }}
              />
            </div>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Kod pocztowy:</span>
              <TextInput
                dropdown={false}
                disabled={isDeletedClient}
                className="in-full"
                noCapitalize={true}
                value={clientDTO.postalCode ?? ""}
                onSelect={(value) => {
                  if (typeof value === "string")
                    handleAddressFieldChange("postalCode", value);
                }}
              />
            </div>
            <div className="pf-row flex align-items-center g-1">
              <span className="pf-label">Miasto:</span>
              <TextInput
                dropdown={false}
                disabled={isDeletedClient}
                className="in-full"
                value={clientDTO.city ?? ""}
                onSelect={(value) => {
                  if (typeof value === "string")
                    handleAddressFieldChange("city", value);
                }}
              />
            </div>
          </>
        )}
      </div>

      {isEdit && (
        <section className="cl-notes flex-column g-075 mt-1">
          <div className="flex align-items-center space-between g-1">
            <span className="sv-variants-title">Notatki</span>
            {!isDeletedClient && (
              <ActionButton text={"Dodaj notatkę"} onClick={addNote}>
                <CirclePlus className="dl-icon-1 c-green" />
              </ActionButton>
            )}
          </div>

          <div className="cl-notes-list flex-column g-05">
            {newClientNotesDTO.map((note, index) => (
              <div key={`new-${index}`} className="cl-note new flex-column g-05">
                <div className="flex align-items-center space-between g-1">
                  <span className="flex align-items-center g-05">
                    <img
                      src={AVAILABLE_AVATARS[user?.avatar ?? "avatar1.png"]}
                      alt="Avatar"
                      className="pf-avatar-sm"
                    />
                    <span className="dl-name">{user?.employee?.name}</span>
                    <span className="dl-dim">{formatDate(note.createdAt)}</span>
                  </span>
                  <ActionButton
                    iconTitle={"Usuń notatkę"}
                    onClick={() =>
                      setNewClientNotesDTO((prev) =>
                        prev.filter((_, i) => i !== index)
                      )
                    }
                    disableText={true}
                    disableBorder={true}
                    className="ur-remove"
                  >
                    <X className="dl-icon-1" />
                  </ActionButton>
                </div>
                <TextInput
                  value={note.content ?? ""}
                  rows={2}
                  multiline={true}
                  placeholder={"Wpisz Notatkę o Kliencie..."}
                  onSelect={(value) => {
                    if (typeof value === "string")
                      handleNewNoteContentUpdate(value, index);
                  }}
                />
              </div>
            ))}

            {existingClientNotes.map((note) => {
              const noteUser = employeeUsersMap.get(note.createdBy.id);
              const canRemove =
                note.createdByUserId === user?.id ||
                isMaster;
              return (
                <div key={note.id} className="cl-note flex-column g-05">
                  <div className="flex align-items-center space-between g-1">
                    <span className="flex align-items-center g-05">
                      <img
                        src={AVAILABLE_AVATARS[noteUser?.avatar ?? "avatar1.png"]}
                        alt="Avatar"
                        className="pf-avatar-sm"
                      />
                      <span className="dl-name">{note.createdBy.name}</span>
                      <span className="dl-dim">{formatDate(note.createdAt)}</span>
                    </span>
                    {canRemove && (
                      <ActionButton
                        iconTitle={"Usuń notatkę"}
                        onClick={() => handleExistingNoteRemove(note.id)}
                        disableText={true}
                        disableBorder={true}
                        className="ur-remove"
                      >
                        <X className="dl-icon-1" />
                      </ActionButton>
                    )}
                  </div>
                  <span className="cl-note-content italic">{note.content}</span>
                </div>
              );
            })}
          </div>
        </section>
      )}
    </Modal>
  );
}

export default ClientModal;
