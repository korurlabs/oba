import { describe, expect, it } from "bun:test";

import en from "@/i18n/locales/en.json";
import {
  humanizeEnum,
  isIncidentPriority,
  isMissionRank,
  labelColour,
  MILESTONE_HEALTHS,
  resolveHealth,
  resolvePriority,
  resolveStatus,
  resolveTicketType,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  TICKET_TYPES,
} from "@/components/kaizen/ticketMetaModel";

describe("resolveStatus", () => {
  it("resolves every known status to its own label", () => {
    for (const status of TICKET_STATUSES) {
      expect(resolveStatus(status).status).toBe(status);
    }
    expect(resolveStatus("WAITING_FOR_THIRD_PARTY").dotClassName).toBe("bg-status-waiting");
  });

  it("shows an unknown or missing status as Todo", () => {
    expect(resolveStatus("ARCHIVED").status).toBe("TODO");
    expect(resolveStatus(undefined).labelKey).toBe("ticketStatusTodo");
  });
});

describe("resolveTicketType", () => {
  it("resolves every known type to its own colours", () => {
    for (const type of TICKET_TYPES) {
      expect(resolveTicketType(type).type).toBe(type);
    }
    expect(resolveTicketType("TECH_DEBT").bgClassName).toBe("bg-type-tech-debt-bg");
  });

  it("renders TASK and a missing type as Feature without a raw label", () => {
    for (const type of ["TASK", null, undefined, ""]) {
      const meta = resolveTicketType(type);
      expect(meta.type).toBe("FEATURE");
      expect(meta.rawLabel).toBeUndefined();
    }
  });

  it("keeps the name of an unknown type on Feature's colours", () => {
    const meta = resolveTicketType("SECURITY_REVIEW");
    expect(meta.type).toBe("FEATURE");
    expect(meta.rawLabel).toBe("Security review");
  });
});

describe("resolvePriority", () => {
  it("resolves every known priority", () => {
    for (const priority of TICKET_PRIORITIES) {
      expect(resolvePriority(priority)?.priority).toBe(priority);
    }
  });

  it("returns null for a missing or unknown priority", () => {
    expect(resolvePriority(null)).toBeNull();
    expect(resolvePriority("URGENT")).toBeNull();
  });

  it("treats P1 to P3 as incident priorities", () => {
    expect(isIncidentPriority("P1")).toBe(true);
    expect(isIncidentPriority("P3")).toBe(true);
    expect(isIncidentPriority("HIGHEST")).toBe(false);
  });
});

describe("resolveHealth", () => {
  it("resolves every health and rejects others", () => {
    for (const health of MILESTONE_HEALTHS) {
      expect(resolveHealth(health)?.health).toBe(health);
    }
    expect(resolveHealth("UNKNOWN")).toBeNull();
  });
});

describe("label keys", () => {
  it("exist in the English resource", () => {
    const keys = [
      ...TICKET_STATUSES.map((status) => resolveStatus(status).labelKey),
      ...TICKET_TYPES.map((type) => resolveTicketType(type).labelKey),
      ...TICKET_PRIORITIES.map((priority) => resolvePriority(priority)?.labelKey),
      ...MILESTONE_HEALTHS.map((health) => resolveHealth(health)?.labelKey),
    ];
    for (const key of keys) {
      expect(Object.keys(en)).toContain(key as string);
    }
  });
});

describe("helpers", () => {
  it("humanizes an enum name", () => {
    expect(humanizeEnum("TECH_DEBT")).toBe("Tech debt");
    expect(humanizeEnum("SPIKE")).toBe("Spike");
  });

  it("knows the mission ranks XS to XXL", () => {
    expect(isMissionRank("XXL")).toBe(true);
    expect(isMissionRank("M2")).toBe(false);
    expect(isMissionRank(null)).toBe(false);
  });

  it("accepts only hex label colours", () => {
    expect(labelColour("#e5484d")).toBe("#e5484d");
    expect(labelColour(" #FFF ")).toBe("#FFF");
    expect(labelColour("red")).toBeNull();
    expect(labelColour("#12345")).toBeNull();
    expect(labelColour(undefined)).toBeNull();
  });
});
