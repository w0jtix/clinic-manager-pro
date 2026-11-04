import { PaymentStatus } from "../models/payment";
import {
  TherapyConflictError,
  TherapyDayTakenError,
  TimelineItem,
  VisitSummary,
  VisitSummaryProduct,
  VisitSummaryService,
} from "../models/therapy";

export const THERAPY_CONTENT_MAX_LENGTH = 5000;

export const todayIso = (now: Date = new Date()): string => {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
};

export const validateTherapyEntry = (entryDate: string | null, content: string, today: string = todayIso()): string | null => {
  if (!entryDate) return "Wybierz datę wpisu!";
  if (entryDate > today) return "Data wpisu nie może być z przyszłości!";
  if (content.trim() === "") return "Treść wpisu nie może być pusta!";
  if (content.trim().length > THERAPY_CONTENT_MAX_LENGTH) {
    return `Treść wpisu może mieć maksymalnie ${THERAPY_CONTENT_MAX_LENGTH} znaków!`;
  }
  return null;
};

const conflictData = (error: unknown): { status?: number; data?: Record<string, unknown> } => {
  const response = (error as { response?: { status?: number; data?: unknown } } | null)?.response;
  const data = response?.data;
  return { status: response?.status, data: data && typeof data === "object" ? (data as Record<string, unknown>) : undefined };
};

export const getTherapyDayTaken = (error: unknown): TherapyDayTakenError | null => {
  const { status, data } = conflictData(error);
  return status === 409 && data?.code === "THERAPY_ENTRY_DAY_TAKEN" ? (data as unknown as TherapyDayTakenError) : null;
};

export const getTherapyConflict = (error: unknown): TherapyConflictError | null => {
  const { status, data } = conflictData(error);
  return status === 409 && data?.code === "THERAPY_ENTRY_CONFLICT" ? (data as unknown as TherapyConflictError) : null;
};

export const timelineItemKey = (item: TimelineItem): string => {
  if (item.type === "ENTRY" && item.entry) return `entry-${item.entry.id}`;
  if (item.type === "ABSENCE" && item.visits[0]) return `absence-${item.visits[0].id}`;
  return `visit-${item.date}`;
};

export const mergeTimelinePages = (current: TimelineItem[], incoming: TimelineItem[]): TimelineItem[] => {
  const seen = new Set(current.map(timelineItemKey));
  return [...current, ...incoming.filter((item) => !seen.has(timelineItemKey(item)))];
};

export const replaceTimelineEntry = (items: TimelineItem[], updated: TimelineItem): TimelineItem[] => {
  const key = timelineItemKey(updated);
  return items.map((item) => (timelineItemKey(item) === key ? updated : item));
};

export const ABSENCE_STATUS_LABELS: Record<PaymentStatus, string> = {
  [PaymentStatus.PAID]: "Opłacona",
  [PaymentStatus.PARTIAL]: "Częściowo opłacona",
  [PaymentStatus.UNPAID]: "Nieopłacona",
};

export const summarizeVisits = (
  visits: VisitSummary[]
): { services: VisitSummaryService[]; products: VisitSummaryProduct[] } => {
  const services = new Map<string, VisitSummaryService>();
  visits.forEach((visit) =>
    visit.services.forEach((service) => {
      if (!services.has(service.name)) services.set(service.name, service);
    })
  );
  const products = new Map<string, VisitSummaryProduct>();
  visits.forEach((visit) =>
    visit.products.forEach((product) => {
      const existing = products.get(product.name);
      products.set(
        product.name,
        existing ? { ...existing, quantity: existing.quantity + product.quantity } : { ...product }
      );
    })
  );
  return { services: [...services.values()], products: [...products.values()] };
};
