import { describe, expect, it } from "bun:test";

import { boardTicketSchema } from "@/lib/schemas/boards";
import { ticketListItemSchema } from "@/lib/schemas/tickets";

/*
 * One incident of `GET /kaizen/tickets`, field by field as
 * `AllTicketListResponse` serialises it, with the nulls the backend sends.
 * `TicketListResponse` of `GET /kaizen/boards/{boardId}/tickets` carries the
 * same fields, with `parentTicket` in place of `backlog`.
 */
const ALL_TICKET_ROW = {
  id: "b0000000-0000-0000-0000-000000000311",
  createdAt: "2026-11-09T06:40:12.418Z",
  title: "Checkout returns 502 for every card payment",
  key: "ALP-16",
  displayOrder: 0,
  type: "INCIDENT",
  status: "IN_PROGRESS",
  priority: "P1",
  missionRank: "M",
  percentage: 0,
  weight: null,
  dueDate: null,
  createdBy: {
    userId: "b0000000-0000-0000-0000-000000000001",
    email: "ayse.kaya@example.com",
    firstName: "Ayşe",
    lastName: "Kaya",
  },
  createdBySystemActor: null,
  assignees: [
    {
      id: "b0000000-0000-0000-0000-000000000901",
      userId: "b0000000-0000-0000-0000-000000000002",
      email: "mert.yilmaz@example.com",
      firstName: "Mert",
      lastName: "Yilmaz",
    },
  ],
  reviewers: [],
  project: null,
  milestone: null,
  board: {
    id: "b0000000-0000-0000-0000-000000000101",
    name: "Alpha",
    prefix: "ALP",
    slug: "alpha",
    icon: null,
    color: "#4AA3FF",
  },
  parentTicketId: null,
  subTickets: [],
  labels: [
    { id: "b0000000-0000-0000-0000-000000000501", name: "payments", color: "#E5484D" },
    { id: "b0000000-0000-0000-0000-000000000502", name: "customer-facing", color: null },
  ],
  sprint: { id: "b0000000-0000-0000-0000-000000000201", name: "Sprint 14", status: "ACTIVE" },
  backlog: null,
};

describe("ticketListItemSchema", () => {
  it("reads the member who created a ticket and when", () => {
    const parsed = ticketListItemSchema.parse(ALL_TICKET_ROW);
    expect(parsed.createdAt).toBe("2026-11-09T06:40:12.418Z");
    expect(parsed.createdBy?.firstName).toBe("Ayşe");
    expect(parsed.createdBySystemActor).toBeNull();
  });

  it("reads an incident an alert route created", () => {
    const parsed = ticketListItemSchema.parse({
      ...ALL_TICKET_ROW,
      createdBy: null,
      createdBySystemActor: {
        id: "b0000000-0000-0000-0000-000000000701",
        name: "Alertmanager",
        type: "ALERT_ROUTE",
      },
    });
    expect(parsed.createdBy).toBeNull();
    expect(parsed.createdBySystemActor?.name).toBe("Alertmanager");
  });

  it("reads the labels of a ticket, with a label that has no colour", () => {
    const parsed = ticketListItemSchema.parse(ALL_TICKET_ROW);
    expect(parsed.labels).toEqual([
      { id: "b0000000-0000-0000-0000-000000000501", name: "payments", color: "#E5484D" },
      { id: "b0000000-0000-0000-0000-000000000502", name: "customer-facing", color: null },
    ]);
  });

  it("reads a ticket without labels", () => {
    expect(ticketListItemSchema.parse({ ...ALL_TICKET_ROW, labels: [] }).labels).toEqual([]);
    expect(ticketListItemSchema.parse({ ...ALL_TICKET_ROW, labels: null }).labels).toBeNull();
  });
});

describe("boardTicketSchema", () => {
  it("reads the creator of a board ticket", () => {
    const { backlog: _backlog, ...row } = ALL_TICKET_ROW;
    const parsed = boardTicketSchema.parse({ ...row, parentTicket: null });
    expect(parsed.createdBy?.lastName).toBe("Kaya");
    expect(parsed.labels?.map((label) => label.name)).toEqual(["payments", "customer-facing"]);
  });
});
