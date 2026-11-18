import { describe, expect, it } from "bun:test";

import {
  applyPlacementMove,
  applyStatusMove,
  applyTicketChange,
  closesIncident,
  edgeDirection,
  inBacklog,
  isStatusNoop,
  landingOrder,
  moveChoiceName,
  nextSwipeStatus,
  placementChoices,
  placeBefore,
  slotBefore,
  statusChoices,
  statusLanding,
  swipeActionLabelKey,
} from "@/components/boards/ticketMoveModel";
import type { TicketMove } from "@/hooks/queries/ticketMoves";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";

const BOARD = "board-1";

function ticket(
  id: string,
  status: string,
  displayOrder: number | null,
  boardId: string = BOARD
): BoardTicket {
  return {
    id,
    createdAt: "2026-11-01T09:00:00Z",
    title: `Ticket ${id}`,
    key: `KZN-${id}`,
    displayOrder,
    type: "FEATURE",
    status,
    priority: "MEDIUM",
    missionRank: "M",
    dueDate: null,
    assignees: [],
    board: { id: boardId, name: "Board", prefix: boardId === BOARD ? "KZN" : "MOB" },
  } as BoardTicket;
}

function sprint(id: string, status: string, startDate: string): Sprint {
  return { id, name: `Sprint ${id}`, status, startDate } as Sprint;
}

const ids = (rows: readonly BoardTicket[]): string[] => rows.map((row) => row.id);

describe("nextSwipeStatus", () => {
  it("steps Todo, In Progress, In Review, Done and skips Waiting", () => {
    expect(nextSwipeStatus("TODO")).toBe("IN_PROGRESS");
    expect(nextSwipeStatus("IN_PROGRESS")).toBe("IN_REVIEW");
    expect(nextSwipeStatus("WAITING_FOR_THIRD_PARTY")).toBe("IN_REVIEW");
    expect(nextSwipeStatus("IN_REVIEW")).toBe("DONE");
    expect(nextSwipeStatus("DONE")).toBeNull();
  });

  it("treats an unknown status as Todo", () => {
    expect(nextSwipeStatus("ARCHIVED")).toBe("IN_PROGRESS");
  });

  it("names the step of each swipe", () => {
    expect(swipeActionLabelKey("IN_PROGRESS")).toBe("boardSwipeStart");
    expect(swipeActionLabelKey("IN_REVIEW")).toBe("boardSwipeReview");
    expect(swipeActionLabelKey("DONE")).toBe("boardSwipeDone");
  });
});

describe("placeBefore", () => {
  const rows = [ticket("a", "TODO", 1), ticket("b", "TODO", 2), ticket("c", "TODO", 3)];

  it("moves a ticket before another", () => {
    expect(ids(placeBefore(rows, rows[2]!, "a"))).toEqual(["c", "a", "b"]);
  });

  it("puts it last for null or an unknown id", () => {
    expect(ids(placeBefore(rows, rows[0]!, null))).toEqual(["b", "c", "a"]);
    expect(ids(placeBefore(rows, rows[0]!, "zz"))).toEqual(["b", "c", "a"]);
  });
});

describe("landingOrder", () => {
  it("takes the next row's order when the ticket moves up", () => {
    const landing = [ticket("a", "TODO", 1), ticket("x", "TODO", 9), ticket("b", "TODO", 2)];
    expect(landingOrder(landing, "x", BOARD)).toBe(2);
  });

  it("takes the previous row's order when the ticket moves down", () => {
    const landing = [ticket("a", "TODO", 4), ticket("b", "TODO", 6), ticket("x", "TODO", 1)];
    expect(landingOrder(landing, "x", BOARD)).toBe(6);
  });

  it("sends nothing when the order already keeps the ticket there", () => {
    const landing = [ticket("a", "TODO", 1), ticket("x", "TODO", 3), ticket("b", "TODO", 5)];
    expect(landingOrder(landing, "x", BOARD)).toBeUndefined();
  });

  it("ignores rows of another board", () => {
    const landing = [
      ticket("m", "TODO", 0, "child"),
      ticket("x", "TODO", 3),
      ticket("b", "TODO", 5),
    ];
    expect(landingOrder(landing, "x", BOARD)).toBeUndefined();
  });

  it("sends nothing for a ticket without an order", () => {
    expect(landingOrder([ticket("x", "TODO", null)], "x", BOARD)).toBeUndefined();
  });
});

describe("status moves", () => {
  const rows = [
    ticket("a", "TODO", 1),
    ticket("b", "TODO", 2),
    ticket("c", "IN_PROGRESS", 3),
    ticket("d", "IN_PROGRESS", 4),
  ];

  it("lands the ticket before the target card with the new status and order", () => {
    const next = applyStatusMove(rows, "a", { status: "IN_PROGRESS", beforeId: "d" }, 3);
    expect(ids(next)).toEqual(["b", "c", "a", "d"]);
    expect(next.find((row) => row.id === "a")).toMatchObject({
      status: "IN_PROGRESS",
      displayOrder: 3,
    });
  });

  it("appends at the end of the column for a drop on the strip", () => {
    const landing = statusLanding(rows, rows[0]!, { status: "IN_PROGRESS", beforeId: null });
    expect(ids(landing)).toEqual(["c", "d", "a"]);
    expect(landingOrder(landing, "a", BOARD)).toBe(4);
  });

  it("knows a drop back on the same slot", () => {
    expect(isStatusNoop(rows, rows[0]!, { status: "TODO", beforeId: "b" })).toBe(true);
    expect(isStatusNoop(rows, rows[0]!, { status: "TODO", beforeId: "a" })).toBe(true);
    expect(isStatusNoop(rows, rows[0]!, { status: "TODO", beforeId: null })).toBe(false);
    expect(isStatusNoop(rows, rows[1]!, { status: "TODO", beforeId: null })).toBe(true);
    expect(isStatusNoop(rows, rows[0]!, { status: "DONE", beforeId: null })).toBe(false);
  });

  it("returns a copy when the ticket is not in the list", () => {
    const next = applyStatusMove(rows, "zz", { status: "DONE", beforeId: null }, undefined);
    expect(next).not.toBe(rows);
    expect(ids(next)).toEqual(ids(rows));
  });
});

describe("applyPlacementMove", () => {
  const moved = ticket("x", "IN_PROGRESS", 7);

  it("adds the ticket to the target list at the slot", () => {
    const backlog = [ticket("a", "TODO", 1), ticket("b", "TODO", 2)];
    const next = applyPlacementMove(
      backlog,
      { backlogId: "bl" },
      moved,
      { backlogId: "bl" },
      "b",
      2
    );
    expect(ids(next)).toEqual(["a", "x", "b"]);
    expect(next[1]?.displayOrder).toBe(2);
  });

  it("removes the ticket from every other list", () => {
    const sprintList = [ticket("a", "TODO", 1), moved];
    const next = applyPlacementMove(
      sprintList,
      { sprintId: "s1" },
      moved,
      { backlogId: "bl" },
      null,
      undefined
    );
    expect(ids(next)).toEqual(["a"]);
  });
});

describe("drop geometry", () => {
  it("finds the card the finger is above, skipping the dragged one", () => {
    const slots = [
      { id: "a", middle: 100 },
      { id: "x", middle: 200 },
      { id: "b", middle: 300 },
    ];
    expect(slotBefore(slots, 50, "x")).toBe("a");
    expect(slotBefore(slots, 150, "x")).toBe("b");
    expect(slotBefore(slots, 350, "x")).toBeNull();
  });

  it("reports the edge zones", () => {
    expect(edgeDirection(10, 400)).toBe(-1);
    expect(edgeDirection(200, 400)).toBe(0);
    expect(edgeDirection(390, 400)).toBe(1);
  });
});

describe("move choices", () => {
  it("offers every other status", () => {
    const names = statusChoices("IN_REVIEW").map(moveChoiceName);
    expect(names).toEqual([
      "status:TODO",
      "status:IN_PROGRESS",
      "status:WAITING_FOR_THIRD_PARTY",
      "status:DONE",
    ]);
  });

  it("offers the backlog and the other open sprints, active first", () => {
    const sprints = [
      sprint("done", "COMPLETED", "2026-10-01"),
      sprint("p2", "PLANNED", "2026-12-01"),
      sprint("p1", "PLANNED", "2026-11-17"),
      sprint("act", "ACTIVE", "2026-11-03"),
    ];
    const choices = placementChoices(
      ticket("x", "TODO", 1),
      BOARD,
      "act",
      "bl",
      sprints,
      "Backlog"
    );
    expect(choices.map(moveChoiceName)).toEqual(["backlog", "sprint:p1", "sprint:p2"]);
  });

  it("offers no backlog to a ticket that is already there", () => {
    const sprints = [sprint("act", "ACTIVE", "2026-11-03")];
    const choices = placementChoices(ticket("x", "TODO", 1), BOARD, null, "bl", sprints, "Backlog");
    expect(choices.map(moveChoiceName)).toEqual(["sprint:act"]);
  });

  it("offers no placement to a ticket of a child board", () => {
    const sprints = [sprint("act", "ACTIVE", "2026-11-03")];
    const child = ticket("m", "TODO", 1, "child");
    expect(placementChoices(child, BOARD, "act", "bl", sprints, "Backlog")).toEqual([]);
  });
});

describe("applyTicketChange", () => {
  it("changes only the row of the ticket", () => {
    const rows: BoardTicket[] = [ticket("a", "TODO", 1), ticket("b", "TODO", 2)];
    const next: BoardTicket[] = applyTicketChange(rows, "b", (row) => ({
      ...row,
      priority: "HIGH",
    }));
    expect(next.map((row) => row.priority)).toEqual([rows[0].priority, "HIGH"]);
    expect(next[0]).toBe(rows[0]);
    expect(rows[1].priority).not.toBe("HIGH");
  });

  it("returns the same list when the ticket is not in it", () => {
    const rows: BoardTicket[] = [ticket("a", "TODO", 1)];
    expect(applyTicketChange(rows, "z", (row) => ({ ...row, priority: "HIGH" }))).toBe(rows);
  });
});

describe("closesIncident", () => {
  const incident = (status: string): BoardTicket => ({
    ...ticket("i", status, 1),
    type: "INCIDENT",
  });
  const statusMove = (row: BoardTicket, status: string): TicketMove => ({
    kind: "status",
    ticket: row,
    boardId: BOARD,
    target: { status, beforeId: null },
  });
  const placeMove = (row: BoardTicket, status?: string): TicketMove => ({
    kind: "place",
    ticket: row,
    boardId: BOARD,
    target: { sprintId: "s-1" },
    beforeId: null,
    status,
  });

  it("asks for a drag, swipe or pick that sets an incident to Done", () => {
    expect(closesIncident(statusMove(incident("IN_REVIEW"), "DONE"))).toBe(true);
    expect(closesIncident(statusMove(incident("TODO"), "DONE"))).toBe(true);
  });

  it("asks for the Undo of a move away from Done", () => {
    // The Undo of Done -> In Review is a status move of the moved card back to Done.
    expect(closesIncident(statusMove(incident("IN_REVIEW"), "DONE"))).toBe(true);
  });

  it("asks for the Undo of a Done incident's move into the backlog", () => {
    expect(closesIncident(placeMove(inBacklog(incident("DONE")), "DONE"))).toBe(true);
  });

  it("does not ask for an incident that stays Done, such as a reorder in the Done column", () => {
    expect(closesIncident(statusMove(incident("DONE"), "DONE"))).toBe(false);
    expect(closesIncident(placeMove(incident("DONE"), "DONE"))).toBe(false);
  });

  it("does not ask for other statuses, other types or a plain placement move", () => {
    expect(closesIncident(statusMove(incident("DONE"), "IN_REVIEW"))).toBe(false);
    expect(closesIncident(statusMove(ticket("b", "IN_REVIEW", 1), "DONE"))).toBe(false);
    expect(closesIncident(placeMove(incident("IN_PROGRESS")))).toBe(false);
  });
});

describe("inBacklog", () => {
  it("makes a top-level ticket Todo, as the API does when it enters the backlog", () => {
    expect(inBacklog(ticket("a", "DONE", 1)).status).toBe("TODO");
  });

  it("keeps a sub-ticket's status", () => {
    const sub: BoardTicket = { ...ticket("a", "DONE", 1), parentTicketId: "p" };
    expect(inBacklog(sub).status).toBe("DONE");
  });
});
