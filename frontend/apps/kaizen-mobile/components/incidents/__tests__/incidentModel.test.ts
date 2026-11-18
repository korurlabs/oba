import { describe, expect, it } from "bun:test";
import { createInstance, type i18n as I18n } from "i18next";

import {
  assignedTo,
  bannerIncident,
  groupIncidentsByPriority,
  incidentListParams,
  incidentTiming,
  NO_PRIORITY,
  openP1Incidents,
  scopeOfSegment,
} from "@/components/incidents/incidentModel";
import en from "@/i18n/locales/en.json";
import { createDates, type DateTranslate } from "@/lib/dates";
import type { TicketListItem } from "@/lib/schemas/tickets";

function incident(overrides: Partial<TicketListItem> & { id: string }): TicketListItem {
  return {
    createdAt: "2026-11-09T08:00:00Z",
    title: `Incident ${overrides.id}`,
    key: `KZN-${overrides.id}`,
    type: "INCIDENT",
    status: "TODO",
    priority: "P1",
    missionRank: "M",
    dueDate: null,
    assignees: [],
    board: null,
    ...overrides,
  };
}

function person(userId: string) {
  return { id: `link-${userId}`, userId };
}

describe("scopeOfSegment", () => {
  it("reads Open and Mine from the open incidents and Resolved from the done ones", () => {
    expect(scopeOfSegment("open")).toBe("open");
    expect(scopeOfSegment("mine")).toBe("open");
    expect(scopeOfSegment("resolved")).toBe("resolved");
  });
});

describe("incidentListParams", () => {
  it("asks for open incidents newest first", () => {
    expect(incidentListParams("open")).toEqual({
      types: "INCIDENT",
      statuses: "TODO,IN_PROGRESS,WAITING_FOR_THIRD_PARTY,IN_REVIEW",
      sortBy: "createdAt",
      sortDirection: "desc",
    });
  });

  it("asks for resolved incidents by their last change and scopes to one board", () => {
    expect(incidentListParams("resolved", "b1")).toEqual({
      types: "INCIDENT",
      statuses: "DONE",
      sortBy: "updatedAt",
      sortDirection: "desc",
      boardIds: "b1",
    });
  });
});

describe("assignedTo", () => {
  it("keeps the incidents the user is assigned to", () => {
    const list: TicketListItem[] = [
      incident({ id: "1", assignees: [person("me"), person("other")] }),
      incident({ id: "2", assignees: [person("other")] }),
      incident({ id: "3", assignees: null }),
    ];
    expect(assignedTo(list, "me").map((item) => item.id)).toEqual(["1"]);
  });

  it("is empty while the user is unknown", () => {
    expect(assignedTo([incident({ id: "1", assignees: [person("me")] })], undefined)).toEqual([]);
  });
});

describe("groupIncidentsByPriority", () => {
  it("orders P1 to P3, then other priorities, then none, keeping the list order", () => {
    const list: TicketListItem[] = [
      incident({ id: "1", priority: "P3" }),
      incident({ id: "2", priority: null }),
      incident({ id: "3", priority: "P1" }),
      incident({ id: "4", priority: "HIGH" }),
      incident({ id: "5", priority: "P1" }),
      incident({ id: "6", priority: "P2" }),
    ];
    const groups = groupIncidentsByPriority(list);
    expect(groups.map((group) => group.priority)).toEqual(["P1", "P2", "P3", "HIGH", NO_PRIORITY]);
    expect(groups[0]?.incidents.map((item) => item.id)).toEqual(["3", "5"]);
  });

  it("returns no groups for no incidents", () => {
    expect(groupIncidentsByPriority([])).toEqual([]);
  });
});

describe("bannerIncident", () => {
  it("names the P1 that has been open longest", () => {
    const list: TicketListItem[] = [
      incident({ id: "1", priority: "P1", createdAt: "2026-11-09T09:00:00Z" }),
      incident({ id: "2", priority: "P2", createdAt: "2026-11-01T09:00:00Z" }),
      incident({ id: "3", priority: "P1", createdAt: "2026-11-09T07:30:00+01:00" }),
    ];
    expect(bannerIncident(list)?.id).toBe("3");
    expect(openP1Incidents(list).map((item) => item.id)).toEqual(["1", "3"]);
  });

  it("is null when no P1 is open", () => {
    expect(bannerIncident([incident({ id: "1", priority: "P2" })])).toBeNull();
  });
});

describe("incidentTiming", () => {
  const instance: I18n = createInstance();
  void instance.init({
    resources: { en: { translation: en } },
    lng: "en",
    interpolation: { escapeValue: false },
    compatibilityJSON: "v4",
    initAsync: false,
  });
  const fixed = instance.getFixedT("en");
  const t: DateTranslate = (key, options) => fixed(key, options as never) as unknown as string;
  // 42 minutes after the incident below was opened, in the local zone.
  const opened = new Date(2026, 10, 9, 9, 0);
  const dates = createDates({
    language: "en",
    t,
    now: () => new Date(opened.getTime() + 42 * 60_000),
  });
  const createdAt: string = opened.toISOString();

  it("shows an open P1's open time in the late colour", () => {
    expect(incidentTiming({ createdAt, priority: "P1" }, false, dates, t)).toEqual({
      icon: "clock",
      label: "open 42 min",
      late: true,
    });
  });

  it("keeps P2 and P3 open times muted", () => {
    expect(incidentTiming({ createdAt, priority: "P2" }, false, dates, t)?.late).toBe(false);
    expect(incidentTiming({ createdAt, priority: "P3" }, false, dates, t)?.late).toBe(false);
    expect(incidentTiming({ createdAt, priority: null }, false, dates, t)?.late).toBe(false);
  });

  it("shows the day a resolved incident was opened, muted", () => {
    expect(incidentTiming({ createdAt, priority: "P1" }, true, dates, t)).toEqual({
      icon: "calendar",
      label: "Opened Nov 9",
      late: false,
    });
  });

  it("shows no slot for an unreadable time", () => {
    const originalWarn = console.warn;
    console.warn = () => undefined;
    try {
      expect(incidentTiming({ createdAt: "soon", priority: "P1" }, false, dates, t)).toBeNull();
    } finally {
      console.warn = originalWarn;
    }
  });
});
