import { describe, expect, it } from "vitest";
import {
  getTherapyConflict,
  getTherapyDayTaken,
  mergeTimelinePages,
  replaceTimelineEntry,
  summarizeVisits,
  timelineItemKey,
  todayIso,
  validateTherapyEntry,
} from "../../utils/therapyUtils";
import { TherapyEntry, TimelineItem } from "../../models/therapy";
import { PaymentStatus } from "../../models/payment";

const entry = (overrides: Partial<TherapyEntry>): TherapyEntry => ({
  id: 1,
  content: "Opracowanie paznokcia",
  authorUserId: 1,
  authorName: "Tomek",
  createdAt: "2026-10-06T10:00:00",
  updatedAt: null,
  edited: false,
  invalid: false,
  invalidatedAt: null,
  version: 0,
  canEdit: true,
  ...overrides,
});

const visit = (id: number, absence = false) => ({
  id,
  employeeName: "Tomek",
  services: [{ name: "Podologia", color: "143,118,230" }],
  products: [],
  absence,
  paymentStatus: PaymentStatus.PAID,
  totalValue: 100,
});

describe("therapy entry validation", () => {
  it("formats today as an ISO date", () => {
    expect(todayIso(new Date(2026, 0, 5))).toBe("2026-01-05");
  });

  it("rejects missing date, future date, empty and too long content", () => {
    expect(validateTherapyEntry(null, "abc", "2026-10-06")).not.toBeNull();
    expect(validateTherapyEntry("2026-10-07", "abc", "2026-10-06")).toContain("przyszłości");
    expect(validateTherapyEntry("2026-10-06", "   ", "2026-10-06")).toContain("pusta");
    expect(validateTherapyEntry("2026-10-06", "a".repeat(5001), "2026-10-06")).toContain("5000");
    expect(validateTherapyEntry("2026-10-01", "Wpis", "2026-10-06")).toBeNull();
  });
});

describe("therapy conflicts", () => {
  it("recognizes the day taken and version conflict responses", () => {
    expect(getTherapyDayTaken({ response: { status: 409, data: { code: "THERAPY_ENTRY_DAY_TAKEN", entryId: 7 } } })).toEqual({
      code: "THERAPY_ENTRY_DAY_TAKEN",
      entryId: 7,
    });
    expect(getTherapyConflict({ response: { status: 409, data: { code: "THERAPY_ENTRY_CONFLICT", currentVersion: 2 } } })).toEqual({
      code: "THERAPY_ENTRY_CONFLICT",
      currentVersion: 2,
    });
    expect(getTherapyDayTaken({ response: { status: 409, data: "Klient jest usunięty." } })).toBeNull();
    expect(getTherapyConflict(new Error("network"))).toBeNull();
  });
});

describe("timeline helpers", () => {
  const entryItem: TimelineItem = { type: "ENTRY", date: "2026-10-06", entry: entry({}), visits: [visit(5)] };
  const absenceItem: TimelineItem = { type: "ABSENCE", date: "2026-10-01", entry: null, visits: [visit(6, true)] };
  const visitItem: TimelineItem = { type: "VISIT", date: "2026-09-20", entry: null, visits: [visit(7)] };

  it("builds stable keys per item type", () => {
    expect(timelineItemKey(entryItem)).toBe("entry-1");
    expect(timelineItemKey(absenceItem)).toBe("absence-6");
    expect(timelineItemKey(visitItem)).toBe("visit-2026-09-20");
  });

  it("appends a new page without duplicates and replaces an updated entry", () => {
    expect(mergeTimelinePages([entryItem], [entryItem, absenceItem])).toEqual([entryItem, absenceItem]);
    const updated = { ...entryItem, entry: entry({ content: "Nowa treść", version: 1 }) };
    expect(replaceTimelineEntry([entryItem, absenceItem], updated)[0].entry?.content).toBe("Nowa treść");
  });

  it("summarizes services and products across visits of one day", () => {
    const podologia = { name: "Podologia", color: "143,118,230" };
    const wkladki = { name: "Wkładki", color: null };
    const first = { ...visit(1), services: [podologia, wkladki], products: [{ name: "Krem", quantity: 1, color: "1,2,3" }] };
    const second = {
      ...visit(2),
      services: [podologia],
      products: [
        { name: "Krem", quantity: 2, color: "1,2,3" },
        { name: "Pasta", quantity: 1, color: null },
      ],
    };
    expect(summarizeVisits([first, second])).toEqual({
      services: [podologia, wkladki],
      products: [
        { name: "Krem", quantity: 3, color: "1,2,3" },
        { name: "Pasta", quantity: 1, color: null },
      ],
    });
  });
});
