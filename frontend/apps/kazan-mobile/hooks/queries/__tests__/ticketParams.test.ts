import { describe, expect, it } from "bun:test";

import { myTicketsParams, URGENT_INCIDENT_PARAMS } from "@/hooks/queries/ticketParams";

const NO_FILTERS = { boardId: null, dueThisWeek: false, incidentsOnly: false };

describe("myTicketsParams", () => {
  it("lists the user's open assigned tickets", () => {
    expect(myTicketsParams("u1", "assigned", NO_FILTERS)).toEqual({
      statuses: "TODO,IN_PROGRESS,WAITING_FOR_THIRD_PARTY,IN_REVIEW",
      sortBy: "createdAt",
      sortDirection: "desc",
      assigneeIds: "u1",
    });
  });

  it("filters reported tickets by board, type and the Monday-to-Sunday week", () => {
    const params = myTicketsParams(
      "u1",
      "reported",
      { boardId: "b1", dueThisWeek: true, incidentsOnly: true },
      new Date(2026, 10, 8, 23, 0)
    );
    expect(params.createdByIds).toBe("u1");
    expect(params.assigneeIds).toBeUndefined();
    expect(params.boardIds).toBe("b1");
    expect(params.types).toBe("INCIDENT");
    expect(params.dueDateStart).toBe("2026-11-02");
    expect(params.dueDateEnd).toBe("2026-11-08");
  });
});

describe("URGENT_INCIDENT_PARAMS", () => {
  it("asks for open P1 and P2 incidents", () => {
    expect(URGENT_INCIDENT_PARAMS).toEqual({
      types: "INCIDENT",
      priorities: "P1,P2",
      statuses: "TODO,IN_PROGRESS,WAITING_FOR_THIRD_PARTY,IN_REVIEW",
    });
  });
});
