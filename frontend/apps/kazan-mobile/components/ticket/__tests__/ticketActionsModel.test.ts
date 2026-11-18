import { describe, expect, it } from "bun:test";

import {
  assigneeCandidates,
  assigneeIdsOf,
  assigneesFor,
  canChangeStatus,
  isAssignee,
  moveBoardChanges,
  moveTargets,
  moveToBoardBody,
  priorityChoices,
  RANK_CHOICES,
  resolvePlacementOnBoard,
  sameIds,
  STATUS_CHOICES,
  typeChange,
  unfinishedSubTicketCount,
} from "@/components/ticket/ticketActionsModel";
import { BACKLOG_PLACEMENT } from "@/components/ticket/newTicketModel";
import { ticketSheetHref } from "@/components/ticket/ticketRoutes";
import type { BoardMember, BoardTreeItem, Sprint } from "@/lib/schemas/boards";
import type { SubTicket, TicketDetail, TicketPerson } from "@/lib/schemas/tickets";

const ME = "me-id";

const member = (userId: string, firstName: string, role: string = "MEMBER"): BoardMember => ({
  id: `link-${userId}`,
  userId,
  firstName,
  lastName: null,
  email: `${firstName.toLowerCase()}@example.com`,
  role,
});

const person = (userId: string, firstName: string): TicketPerson => ({
  id: `assignee-${userId}`,
  userId,
  firstName,
  lastName: null,
  email: null,
});

const sub = (id: string, status: string): SubTicket => ({
  id,
  key: `KZN-${id}`,
  title: id,
  type: "FEATURE",
  status,
});

describe("pickers", () => {
  it("offers every status in board order", () => {
    expect(STATUS_CHOICES).toEqual([
      "TODO",
      "IN_PROGRESS",
      "WAITING_FOR_THIRD_PARTY",
      "IN_REVIEW",
      "DONE",
    ]);
  });

  it("offers P1 to P3 for an incident and HIGHEST to LOWEST otherwise", () => {
    expect(priorityChoices("INCIDENT")).toEqual(["P1", "P2", "P3"]);
    expect(priorityChoices("BUG")).toEqual(["HIGHEST", "HIGH", "MEDIUM", "LOW", "LOWEST"]);
  });

  it("sends a type change with the priority the new type allows", () => {
    expect(typeChange("BUG", "HIGH")).toEqual({ type: "BUG", priority: "HIGH" });
    expect(typeChange("INCIDENT", "HIGH")).toEqual({ type: "INCIDENT", priority: "P2" });
    expect(typeChange("INCIDENT", "P1")).toEqual({ type: "INCIDENT", priority: "P1" });
    expect(typeChange("FEATURE", "P3")).toEqual({ type: "FEATURE", priority: "MEDIUM" });
    expect(typeChange("REFACTOR", null)).toEqual({ type: "REFACTOR", priority: "MEDIUM" });
  });

  it("keeps a top-level backlog ticket at Todo", () => {
    expect(canChangeStatus({ sprint: null, parentTicketId: null })).toBe(false);
    expect(canChangeStatus({ sprint: { id: "s", name: "Sprint 14" }, parentTicketId: null })).toBe(
      true
    );
    expect(canChangeStatus({ sprint: null, parentTicketId: "parent" })).toBe(true);
  });

  it("lists the ranks smallest first with their leaderboard points", () => {
    expect(RANK_CHOICES.map((choice) => `${choice.rank}:${choice.points}`)).toEqual([
      "XS:1",
      "S:2",
      "M:3",
      "L:5",
      "XL:8",
      "XXL:13",
    ]);
  });
});

describe("assignees", () => {
  const ticket = { assignees: [person("b", "Burak"), person(ME, "Me")] };

  it("reads the assignee user ids and whether the user is one", () => {
    expect(assigneeIdsOf(ticket)).toEqual(["b", ME]);
    expect(isAssignee(ticket, ME)).toBe(true);
    expect(isAssignee(ticket, "c")).toBe(false);
    expect(isAssignee(ticket, undefined)).toBe(false);
    expect(assigneeIdsOf({ assignees: null })).toEqual([]);
  });

  it("lists me first, then by name, and keeps an assignee who left the board", () => {
    const rows = assigneeCandidates(
      [member("c", "Cem"), member(ME, "Zeynep"), member("a", "Ayşe", "VIEWER")],
      [person("gone", "Deniz")],
      ME,
      "",
      "Unknown"
    );
    expect(rows.map((row) => row.userId)).toEqual([ME, "a", "c", "gone"]);
    expect(rows.find((row) => row.userId === "gone")?.role).toBeNull();
    expect(rows.find((row) => row.userId === "a")?.role).toBe("VIEWER");
  });

  it("carries Kazan access: a member without it is false, one who left the board true", () => {
    const rows = assigneeCandidates(
      [
        { ...member("a", "Ayşe"), kazanAccess: true },
        { ...member("c", "Cem"), kazanAccess: false },
        member("d", "Derya"),
      ],
      [person("gone", "Gül")],
      ME,
      "",
      "Unknown"
    );
    expect(rows.map((row) => [row.userId, row.kazanAccess])).toEqual([
      ["a", true],
      ["c", false],
      ["d", false],
      ["gone", true],
    ]);
  });

  it("filters by name or email, ignoring case and spaces", () => {
    const members = [member("c", "Cem"), member("a", "Ayşe")];
    expect(assigneeCandidates(members, [], ME, "  CE ", "Unknown").map((r) => r.userId)).toEqual([
      "c",
    ]);
    expect(
      assigneeCandidates(members, [], ME, "ayşe@example", "Unknown").map((r) => r.userId)
    ).toEqual(["a"]);
    expect(assigneeCandidates(members, [], ME, "nobody", "Unknown")).toEqual([]);
  });

  it("builds the optimistic assignees, keeping known link rows", () => {
    const candidates = assigneeCandidates([member("c", "Cem")], [], ME, "", "Unknown");
    const current = [person("b", "Burak")];
    expect(assigneesFor(["b", "c"], candidates, current)).toEqual([
      current[0],
      { id: "c", userId: "c", firstName: "Cem", lastName: null, email: "cem@example.com" },
    ]);
  });

  it("compares id lists without order", () => {
    expect(sameIds(["a", "b"], ["b", "a"])).toBe(true);
    expect(sameIds(["a"], ["a", "b"])).toBe(false);
    expect(sameIds(["a", "c"], ["a", "b"])).toBe(false);
  });
});

describe("moves", () => {
  const tree: BoardTreeItem[] = [
    {
      id: "kzn",
      name: "Kazan",
      prefix: "KZN",
      slug: "kazan",
      childBoards: [{ id: "mob", name: "Mobile", prefix: "MOB", slug: "mobile" }],
    },
    { id: "ops", name: "Ops", prefix: null, slug: "ops", childBoards: null },
  ];

  it("lists every other board in tree order with its depth", () => {
    expect(moveTargets(tree, "ops")).toEqual([
      { id: "kzn", name: "Kazan", prefix: "KZN", depth: 0 },
      { id: "mob", name: "Mobile", prefix: "MOB", depth: 1 },
    ]);
  });

  it("keeps the child boards of the ticket's own board", () => {
    expect(moveTargets(tree, "kzn").map((target) => target.id)).toEqual(["mob", "ops"]);
  });

  it("counts the sub-tickets that are not done", () => {
    expect(unfinishedSubTicketCount({ subTickets: [sub("1", "DONE"), sub("2", "TODO")] })).toBe(1);
    expect(unfinishedSubTicketCount({ subTickets: null })).toBe(0);
  });

  it("addresses a sheet of the ticket by its key", () => {
    expect(ticketSheetHref("KZN-7", "labels")).toEqual({
      pathname: "/ticket/[ticketKey]/labels",
      params: { ticketKey: "KZN-7" },
    });
  });
});

describe("move to another board", () => {
  const sprint = (id: string, name: string, status: string = "ACTIVE"): Sprint => ({
    id,
    name,
    status,
    startDate: null,
    endDate: null,
  });

  type MovedTicket = Pick<
    TicketDetail,
    "key" | "status" | "subTickets" | "project" | "milestone" | "board"
  >;

  const ticket = (overrides: Partial<MovedTicket> = {}): MovedTicket => ({
    key: "KZN-7",
    status: "TODO",
    subTickets: null,
    project: null,
    milestone: null,
    board: { id: "kzn", name: "Kazan", prefix: "KZN", slug: "kazan" },
    ...overrides,
  });

  describe("resolvePlacementOnBoard", () => {
    it("keeps a sprint the target board shares", () => {
      const current = { id: "s1", name: "Sprint 1" };
      expect(
        resolvePlacementOnBoard(current, [sprint("s2", "Sprint 2"), sprint("s1", "Sprint 1")])
      ).toBe("s1");
    });

    it("takes an open sprint of the same name, ignoring case and outer spaces", () => {
      const current = { id: "s1", name: " Sprint 12 " };
      expect(resolvePlacementOnBoard(current, [sprint("t9", "sprint 12", "PLANNED")])).toBe("t9");
    });

    it("ignores a completed sprint, shared or of the same name", () => {
      const current = { id: "s1", name: "Sprint 1" };
      expect(
        resolvePlacementOnBoard(current, [
          sprint("s1", "Sprint 1", "COMPLETED"),
          sprint("t1", "sprint 1", "COMPLETED"),
        ])
      ).toBe(BACKLOG_PLACEMENT);
    });

    it("lands in the backlog when no sprint matches", () => {
      expect(resolvePlacementOnBoard({ id: "s1", name: "Sprint 1" }, [sprint("t2", "Other")])).toBe(
        BACKLOG_PLACEMENT
      );
    });

    it("lands in the backlog for a ticket without a sprint", () => {
      expect(resolvePlacementOnBoard(null, [sprint("t1", "Sprint 1")])).toBe(BACKLOG_PLACEMENT);
      expect(resolvePlacementOnBoard(undefined, [])).toBe(BACKLOG_PLACEMENT);
    });
  });

  describe("moveBoardChanges", () => {
    it("names only the new key for a plain ticket going to a sprint", () => {
      expect(moveBoardChanges(ticket(), { prefix: "MOB" }, "s1", false)).toEqual([
        { key: "ticketMoveBoardChangeNewKey", values: { key: "KZN-7", prefix: "MOB" } },
      ]);
    });

    it("uses the text without a prefix when the target board has none", () => {
      expect(moveBoardChanges(ticket(), { prefix: null }, "s1", false)).toEqual([
        { key: "ticketMoveBoardChangeNewKeyNoPrefix", values: { key: "KZN-7" } },
      ]);
    });

    it("lists every change in order: key, sub-tickets, project, milestone, status", () => {
      const moved = ticket({
        status: "IN_PROGRESS",
        subTickets: [sub("1", "DONE"), sub("2", "TODO")],
        project: { id: "p", name: "Apollo" },
        milestone: { id: "m", name: "Beta" },
      });
      expect(moveBoardChanges(moved, { prefix: "MOB" }, BACKLOG_PLACEMENT, false)).toEqual([
        { key: "ticketMoveBoardChangeNewKey", values: { key: "KZN-7", prefix: "MOB" } },
        { key: "ticketMoveBoardChangeSubTickets", values: { count: 2 } },
        { key: "ticketMoveBoardChangeProject", values: { project: "Apollo" } },
        { key: "ticketMoveBoardChangeMilestone", values: { milestone: "Beta" } },
        { key: "ticketMoveBoardChangeBacklogStatus", values: {} },
      ]);
    });

    it("says the sub-tickets stay on the old board when they are detached", () => {
      const moved = ticket({ subTickets: [sub("1", "TODO")] });
      expect(moveBoardChanges(moved, { prefix: "MOB" }, "s1", true)[1]).toEqual({
        key: "ticketMoveBoardChangeSubTicketsDetached",
        values: { count: 1, board: "Kazan" },
      });
    });

    it("leaves the status line out for a To Do ticket or a sprint target", () => {
      const keys = (status: string, placement: string): string[] =>
        moveBoardChanges(ticket({ status }), { prefix: "MOB" }, placement, false).map(
          (change) => change.key
        );
      expect(keys("TODO", BACKLOG_PLACEMENT)).not.toContain("ticketMoveBoardChangeBacklogStatus");
      expect(keys("IN_PROGRESS", "s1")).not.toContain("ticketMoveBoardChangeBacklogStatus");
    });

    it("lists the lines that do not depend on the placement before one is known", () => {
      const moved = ticket({ status: "IN_PROGRESS", subTickets: [sub("1", "TODO")] });
      expect(moveBoardChanges(moved, { prefix: "MOB" }, null, false)).toEqual([
        { key: "ticketMoveBoardChangeNewKey", values: { key: "KZN-7", prefix: "MOB" } },
        { key: "ticketMoveBoardChangeSubTickets", values: { count: 1 } },
      ]);
    });
  });

  describe("moveToBoardBody", () => {
    it("moves to the target board's backlog without a sprint", () => {
      expect(moveToBoardBody("t1", "mob", BACKLOG_PLACEMENT, false)).toEqual({
        ticketIds: ["t1"],
        action: "MOVE_TO_BACKLOG",
        targetBoardId: "mob",
      });
    });

    it("moves to a sprint of the target board", () => {
      expect(moveToBoardBody("t1", "mob", "s1", false)).toEqual({
        ticketIds: ["t1"],
        action: "MOVE_TO_SPRINT",
        targetSprintId: "s1",
        targetBoardId: "mob",
      });
    });

    it("sends detachSubTickets only when the sub-tickets stay behind", () => {
      expect(moveToBoardBody("t1", "mob", "s1", true).detachSubTickets).toBe(true);
      expect("detachSubTickets" in moveToBoardBody("t1", "mob", "s1", false)).toBe(false);
    });
  });
});
