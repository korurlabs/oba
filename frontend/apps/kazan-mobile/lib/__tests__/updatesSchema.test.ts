import { describe, expect, it } from "bun:test";

import {
  updateMissingResponseSchema,
  updateNudgeResponseSchema,
  updateResponseSchema,
  updateTicketOptionSchema,
  updateWriteRequestSchema,
} from "@/lib/schemas/updates";

describe("daily update schemas", () => {
  it("reads an update with a removed ticket line", () => {
    const parsed = updateResponseSchema.parse({
      id: "u-1",
      createdAt: "2026-11-09T06:45:00Z",
      editedAt: null,
      author: { userId: "user-1" },
      items: [
        {
          ticketId: null,
          boardId: "b-1",
          boardName: "Kazan",
          ticketKey: "KZN-2",
          ticketTitle: "Removed",
          percentage: 10,
          progressSynced: false,
          canOpen: false,
        },
      ],
      finishedTickets: [],
      summaryText: "text",
    });
    expect(parsed.items[0].ticketId).toBeNull();
  });

  it("limits a write to 1 to 30 lines with whole percentages", () => {
    const line = { ticketId: "t", percentage: 50, note: null };
    expect(
      updateWriteRequestSchema.safeParse({ tickets: [], focus: null, questions: null }).success
    ).toBe(false);
    expect(
      updateWriteRequestSchema.safeParse({
        tickets: Array(31).fill(line),
        focus: null,
        questions: null,
      }).success
    ).toBe(false);
    expect(
      updateWriteRequestSchema.safeParse({
        tickets: [{ ...line, percentage: 101 }],
        focus: null,
        questions: null,
      }).success
    ).toBe(false);
    expect(
      updateWriteRequestSchema.safeParse({ tickets: [line], focus: null, questions: null }).success
    ).toBe(true);
  });

  it("reads a ticket option, the missing report and a nudge answer", () => {
    expect(
      updateTicketOptionSchema.parse({
        id: "t",
        key: "K-1",
        title: "T",
        percentage: 0,
        board: null,
      }).id
    ).toBe("t");
    const report = updateMissingResponseSchema.parse({
      organizationId: "o",
      date: "2026-11-09",
      deadline: "2026-11-09T07:30:00Z",
      workday: true,
      deadlinePassed: false,
      expectedCount: 3,
      members: [{ userId: "u", email: "a@x.io", firstName: null, lastName: null, postedAt: null }],
    });
    expect(report.members).toHaveLength(1);
    expect(
      updateNudgeResponseSchema.parse({
        recipientId: "u",
        day: "2026-11-09",
        createdAt: "2026-11-09T07:00:00Z",
      }).day
    ).toBe("2026-11-09");
  });
});
