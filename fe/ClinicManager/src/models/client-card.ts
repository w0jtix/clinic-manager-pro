export type ClientMatchReason = "EXACT" | "SAME_NAME" | "SAME_PHONE";

export interface ClientCardFilterDTO {
  keyword: string;
}

export interface ClientCardListItem {
  id: number;
  firstName: string;
  lastName: string;
  birthDate: string | null;
  phoneNumber: string | null;
  isDeleted: boolean;
  lastVisitDate: string | null;
  lastVisitAbsence: boolean | null;
}

export interface ClientCard {
  clientId: number;
  firstName: string;
  lastName: string;
  birthDate: string | null;
  phoneNumber: string | null;
  redFlag: boolean;
  signedRegulations: boolean;
  isDeleted: boolean;
  lastVisitDate: string | null;
  lastAbsenceUnpaidDate: string | null;
  cardCreatedAt: string | null;
  remarks: string | null;
}

export interface ClientCardData {
  firstName: string;
  lastName: string;
  birthDate: string | null;
  phoneNumber: string | null;
  redFlag: boolean;
  signedRegulations: boolean;
  remarks: string | null;
}

export type NewClientCard = Omit<ClientCardData, "redFlag" | "remarks">;

export interface ClientIdentity {
  firstName: string;
  lastName: string;
  phoneNumber: string | null;
}

export interface ClientMatch {
  id: number;
  firstName: string;
  lastName: string;
  birthDate: string | null;
  phoneNumber: string | null;
  lastVisitDate: string | null;
  isDeleted: boolean;
  matchReason: ClientMatchReason;
}

export interface ClientDuplicateCheck {
  exactMatch: ClientMatch | null;
  similar: ClientMatch[];
}

export interface DuplicateClientError {
  code: "DUPLICATE_CLIENT";
  clientId: number;
  deleted: boolean;
  withoutPhone: boolean;
  firstName: string;
  lastName: string;
}

export type CardHistoryAction =
  | "VIEW_CARD"
  | "CREATE_CARD"
  | "UPDATE_CLIENT_DATA"
  | "VIEW_QUESTIONNAIRE"
  | "UPDATE_QUESTIONNAIRE"
  | "UPDATE_DRAWING"
  | "VIEW_THERAPY_ENTRIES"
  | "CREATE_THERAPY_ENTRY"
  | "UPDATE_THERAPY_ENTRY"
  | "INVALIDATE_THERAPY_ENTRY"
  | "VIEW_THERAPY_ENTRY_HISTORY";

export interface CardHistoryChange {
  label: string;
  type: string;
  before: string | null;
  after: string | null;
}

export interface CardHistoryEntrySnapshot {
  entryDate: string;
  content: string;
}

export interface CardHistoryEntryEdit {
  beforeEntryDate: string;
  beforeContent: string;
  afterEntryDate: string;
  afterContent: string;
}

export interface CardHistoryItem {
  id: number;
  at: string;
  action: CardHistoryAction;
  userId: number | null;
  userName: string | null;
  ipAddress: string | null;
  deviceName?: string | null;
  entryDate: string | null;
  changes: CardHistoryChange[] | null;
  createdEntry: CardHistoryEntrySnapshot | null;
  editedEntry: CardHistoryEntryEdit | null;
  invalidatedEntry: CardHistoryEntrySnapshot | null;
}

export const CARD_HISTORY_ACTION_LABELS: Record<CardHistoryAction, string> = {
  VIEW_CARD: "Otwarcie karty",
  CREATE_CARD: "Utworzenie karty",
  UPDATE_CLIENT_DATA: "Edycja danych klienta",
  VIEW_QUESTIONNAIRE: "Podgląd ankiety",
  UPDATE_QUESTIONNAIRE: "Edycja ankiety",
  UPDATE_DRAWING: "Aktualizacja rysunku",
  VIEW_THERAPY_ENTRIES: "Podgląd wpisów",
  CREATE_THERAPY_ENTRY: "Nowy wpis",
  UPDATE_THERAPY_ENTRY: "Edycja wpisu",
  INVALIDATE_THERAPY_ENTRY: "Błędny wpis",
  VIEW_THERAPY_ENTRY_HISTORY: "Podgląd historii wpisu",
};
