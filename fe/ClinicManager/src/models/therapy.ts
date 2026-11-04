import { PaymentStatus } from "./payment";

export type TimelineItemType = "ENTRY" | "VISIT" | "ABSENCE";

export interface TherapyEntry {
  id: number;
  content: string;
  authorUserId: number;
  authorName: string;
  createdAt: string;
  updatedAt: string | null;
  edited: boolean;
  invalid: boolean;
  invalidatedAt: string | null;
  version: number;
  canEdit: boolean;
}

export interface VisitSummaryService {
  name: string;
  color: string | null;
}

export interface VisitSummaryProduct {
  name: string;
  quantity: number;
  color: string | null;
}

export interface VisitSummary {
  id: number;
  employeeName: string | null;
  services: VisitSummaryService[];
  products: VisitSummaryProduct[];
  absence: boolean;
  paymentStatus: PaymentStatus;
  totalValue: number;
}

export interface TimelineItem {
  type: TimelineItemType;
  date: string;
  entry: TherapyEntry | null;
  visits: VisitSummary[];
}

export interface TimelinePage {
  items: TimelineItem[];
  nextBeforeDate: string | null;
}

export interface TherapyEntryInput {
  entryDate: string;
  content: string;
}

export interface TherapyEntryUpdate extends TherapyEntryInput {
  expectedVersion: number;
}

export interface TherapyEntryRevision {
  previousEntryDate: string;
  previousContent: string;
  changedAt: string;
  changedByName: string;
}

export interface TherapyDayTakenError {
  code: "THERAPY_ENTRY_DAY_TAKEN";
  entryId: number;
}

export interface TherapyConflictError {
  code: "THERAPY_ENTRY_CONFLICT";
  currentVersion: number;
}
