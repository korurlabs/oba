import { describe, expect, it } from "bun:test";

import {
  attachmentExistsSchema,
  subTicketSchema,
  ticketAttachmentListSchema,
  ticketDetailSchema,
} from "@/lib/schemas/tickets";

const SUB_TICKET = {
  id: "t-2",
  key: "KZN-2",
  title: "Write the migration",
  type: "TASK",
  status: "TODO",
  priority: "P2",
  assignees: [],
};

describe("subTicketSchema", () => {
  it("reads the sub-ticket's own sprint", () => {
    const parsed = subTicketSchema.parse({
      ...SUB_TICKET,
      sprint: { id: "s-1", name: "Sprint 14", status: "ACTIVE" },
    });
    expect(parsed.sprint).toEqual({ id: "s-1", name: "Sprint 14", status: "ACTIVE" });
  });

  it("accepts a sub-ticket in the backlog, with a null or missing sprint", () => {
    expect(subTicketSchema.parse({ ...SUB_TICKET, sprint: null }).sprint).toBeNull();
    expect(subTicketSchema.parse(SUB_TICKET).sprint).toBeUndefined();
  });

  it("keeps each sub-ticket's sprint in the detail", () => {
    const detail = ticketDetailSchema.parse({
      id: "t-1",
      createdAt: "2026-11-09T08:00:00Z",
      updatedAt: "2026-11-09T08:00:00Z",
      title: "Parent",
      key: "KZN-1",
      type: "STORY",
      status: "IN_PROGRESS",
      priority: null,
      missionRank: null,
      dueDate: null,
      board: { id: "b-1", name: "Kaizen", slug: "kaizen" },
      subTickets: [
        { ...SUB_TICKET, sprint: { id: "s-2", name: "Sprint 15" } },
        { ...SUB_TICKET, id: "t-3", key: "KZN-3", sprint: null },
      ],
      sprint: { id: "s-1", name: "Sprint 14" },
    });
    expect(detail.subTickets?.map((sub) => sub.sprint?.name ?? null)).toEqual(["Sprint 15", null]);
  });
});

/**
 * A `TicketDetailResponse` of an incident, field by field from the Java
 * record, with every field the backend sends as null.
 */
const INCIDENT_DETAIL = {
  id: "b0000000-0000-0000-0000-000000000041",
  createdAt: "2026-11-10T07:12:00Z",
  updatedAt: "2026-11-10T09:30:00Z",
  title: "API returns 502 on login",
  description: null,
  key: "KZN-41",
  displayOrder: 3,
  type: "INCIDENT",
  status: "IN_REVIEW",
  priority: "P1",
  missionRank: "M",
  percentage: 0,
  weight: null,
  dueDate: null,
  rca: null,
  lessonsLearned: null,
  createdBy: null,
  createdBySystemActor: { id: "a-1", name: "Alertmanager", type: "ALERT_ROUTE" },
  assignees: [],
  reviewers: [],
  project: null,
  milestone: null,
  board: { id: "b-1", name: "Kaizen", prefix: "KZN", slug: "kaizen", icon: null, color: null },
  parentTicketId: null,
  subTickets: [],
  labels: [],
  sprint: { id: "s-1", name: "Sprint 14", status: "ACTIVE" },
  backlog: null,
};

describe("ticketDetailSchema incident texts", () => {
  it("reads an incident without its root cause analysis and lessons learned as null", () => {
    const detail = ticketDetailSchema.parse(INCIDENT_DETAIL);
    expect(detail.rca).toBeNull();
    expect(detail.lessonsLearned).toBeNull();
  });

  it("reads the texts of a closed incident", () => {
    const detail = ticketDetailSchema.parse({
      ...INCIDENT_DETAIL,
      status: "DONE",
      rca: "The token cache expired under load.",
      lessonsLearned: "Alert on cache misses.",
    });
    expect(detail.rca).toBe("The token cache expired under load.");
    expect(detail.lessonsLearned).toBe("Alert on cache misses.");
  });
});

describe("ticketAttachmentListSchema", () => {
  const ATTACHMENT = {
    id: "b0000000-0000-0000-0000-000000000001",
    url: "https://blob.test/village-container/shot.png?sig=x",
    fileName: "shot.png",
    contentType: "image/png",
    fileSize: 2048,
    entityType: "TICKET",
    entityId: "7d3f7f58-2f0b-4a8e-9c3e-1d2b3c4d5e6f",
    createdAt: "2026-11-18T09:00:00Z",
    width: 640,
    height: 480,
  };

  it("reads an image with its dimensions and a file without them", () => {
    const parsed = ticketAttachmentListSchema.parse([
      ATTACHMENT,
      {
        ...ATTACHMENT,
        fileName: "spec.pdf",
        contentType: "application/pdf",
        width: null,
        height: null,
      },
    ]);
    expect(parsed[0]?.width).toBe(640);
    expect(parsed[1]?.width).toBeNull();
  });

  it("refuses an attachment of an unknown entity type", () => {
    expect(() =>
      ticketAttachmentListSchema.parse([{ ...ATTACHMENT, entityType: "BOARD" }])
    ).toThrow();
  });
});

describe("attachmentExistsSchema", () => {
  it("reads the answer of the duplicate check", () => {
    expect(attachmentExistsSchema.parse({ exists: true }).exists).toBe(true);
    expect(() => attachmentExistsSchema.parse({})).toThrow();
  });
});
