import { DuplicateClientError } from "../models/client-card";

export const getDuplicateClientError = (error: any): DuplicateClientError | null => {
  const data = error?.response?.data;
  if (error?.response?.status === 409 && data && typeof data === "object" && data.code === "DUPLICATE_CLIENT") {
    return data as DuplicateClientError;
  }
  return null;
};

export const buildDuplicateClientMessage = (
  duplicate: { firstName: string; lastName: string; withoutPhone: boolean; deleted: boolean },
  isMaster: boolean
): string => {
  const name = `${duplicate.firstName} ${duplicate.lastName}`;
  if (duplicate.deleted && isMaster) {
    return duplicate.withoutPhone
      ? `Usunięty klient ${name} bez numeru telefonu ma identyczne dane. Przywróć go albo wyróżnij nowego klienta numerem telefonu lub drugim imieniem.`
      : `Usunięty klient ${name} ma identyczne dane i ten sam numer telefonu. Przywróć go albo zmień imię lub nazwisko.`;
  }
  return duplicate.withoutPhone
    ? `Klient ${name} bez numeru telefonu już istnieje. Wyróżnij klienta numerem telefonu albo dodaj drugie imię lub dopisek.`
    : `Klient ${name} z tym numerem telefonu już istnieje. Zmień imię lub nazwisko, np. dodaj drugie imię albo dopisek „(syn)”.`;
};
