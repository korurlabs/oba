import { describe, expect, it } from "bun:test";

import {
  activeFilterCount,
  boardRouteParamPatch,
  boardRouteParams,
  boardTicketParams,
  canCreateBoard,
  canDeleteBoard,
  canEditBoard,
  canEditBoardTickets,
  canManageSprints,
  canWriteBoard,
  countByBoard,
  EMPTY_FILTERS,
  flattenBoardTree,
  groupByStatus,
  groupUnderParents,
  sprintProgressSegments,
  milestoneBar,
  milestoneProgress,
  nextPlannedSprint,
  parentCandidates,
  parseBoardRoute,
  pushRecent,
  quarterOf,
  quarterTodayPercent,
  recentBoardRows,
  searchBoardRows,
  sortBacklog,
  sortSprintsForPicker,
  sprintForView,
  statusCounts,
  subTicketProgress,
  suggestPrefix,
  toggleStatus,
  toggleValue,
  type BoardRouteState,
} from "@/components/boards/boardModel";
import type { BoardTicket, BoardTreeItem, Sprint } from "@/lib/schemas/boards";

// A zone west of UTC, so a day read in the wrong zone shows up as a shift.
process.env.TZ = "America/New_York";

function board(id: string, name: string, children: BoardTreeItem[] = []): BoardTreeItem {
  return { id, name, slug: id, prefix: id.toUpperCase(), childBoards: children };
}

function sprint(id: string, status: string, startDate: string | null): Sprint {
  return { id, name: id, status, startDate, endDate: null };
}

function ticket(overrides: Partial<BoardTicket>): BoardTicket {
  return {
    id: "t",
    createdAt: "2026-11-01T10:00:00Z",
    title: "Title",
    key: "KZN-1",
    type: "FEATURE",
    status: "TODO",
    priority: null,
    missionRank: null,
    dueDate: null,
    assignees: null,
    board: null,
    ...overrides,
  };
}

const TREE: BoardTreeItem[] = [
  board("web", "Websites"),
  board("kzn", "Kazan Platform", [board("mob", "Mobile App"), board("api", "API")]),
];

describe("board tree", () => {
  it("follows the saved sidebar order, with unlisted boards after it by name", () => {
    const tree: BoardTreeItem[] = [...TREE, board("ops", "Operations"), board("ai", "AI Lab")];
    const rows = flattenBoardTree(tree, {
      boardOrder: ["kzn", "gone", "web", "kzn"],
      childBoardOrder: { kzn: ["mob", "api"] },
    });
    expect(rows.map((row) => row.id)).toEqual(["kzn", "mob", "api", "web", "ai", "ops"]);
  });

  it("sorts by name when the saved order is empty", () => {
    const rows = flattenBoardTree(TREE, { boardOrder: [], childBoardOrder: {} });
    expect(rows.map((row) => row.id)).toEqual(["kzn", "api", "mob", "web"]);
  });

  it("flattens depth first, by name, with parent and child count", () => {
    const rows = flattenBoardTree(TREE);
    expect(rows.map((row) => row.id)).toEqual(["kzn", "api", "mob", "web"]);
    expect(rows[0]).toMatchObject({ depth: 0, childCount: 2, parentId: null });
    expect(rows[1]).toMatchObject({ depth: 1, parentId: "kzn", parentName: "Kazan Platform" });
  });

  it("searches name and key, ignoring case", () => {
    const rows = flattenBoardTree(TREE);
    expect(searchBoardRows(rows, "mob").map((row) => row.id)).toEqual(["mob"]);
    expect(searchBoardRows(rows, "WEB").map((row) => row.id)).toEqual(["web"]);
    expect(searchBoardRows(rows, "  ")).toHaveLength(4);
  });

  it("never offers a board or its descendants as its own parent", () => {
    const rows = flattenBoardTree(TREE);
    expect(parentCandidates(rows, "kzn").map((row) => row.id)).toEqual(["web"]);
    expect(parentCandidates(rows, null)).toHaveLength(4);
  });

  it("keeps recent boards newest first, unique and bounded", () => {
    expect(pushRecent(["a", "b", "c"], "b")).toEqual(["b", "a", "c"]);
    expect(pushRecent(["a", "b", "c", "d", "e"], "f")).toEqual(["f", "a", "b", "c", "d"]);
  });

  it("maps recent ids to rows and skips boards no longer listed", () => {
    const rows = flattenBoardTree(TREE);
    expect(recentBoardRows(rows, ["mob", "gone", "web"]).map((row) => row.id)).toEqual([
      "mob",
      "web",
    ]);
  });

  it("suggests a ticket key from the board name", () => {
    expect(suggestPrefix("Kazan Platform")).toBe("KP");
    expect(suggestPrefix("websites")).toBe("WEB");
    expect(suggestPrefix("A b c d e f g")).toBe("ABCDE");
  });

  it("counts open tickets per board", () => {
    expect(countByBoard([{ board: { id: "a" } }, { board: { id: "a" } }, { board: null }])).toEqual(
      { a: 2 }
    );
  });
});

describe("roles", () => {
  it("lets every organization role but a guest create a board", () => {
    expect(canCreateBoard("MEMBER")).toBe(true);
    expect(canCreateBoard("GUEST")).toBe(false);
    expect(canCreateBoard(null)).toBe(false);
  });

  it("needs a writing organization role and board role for ticket writes", () => {
    expect(canWriteBoard("MEMBER", "MEMBER")).toBe(true);
    expect(canWriteBoard("GUEST", "OWNER")).toBe(false);
    expect(canWriteBoard("OWNER", "VIEWER")).toBe(false);
  });

  it("keeps sprint management to board admins with a writing organization role", () => {
    expect(canManageSprints("MEMBER", "ADMIN")).toBe(true);
    expect(canManageSprints("MEMBER", "OWNER")).toBe(true);
    expect(canManageSprints("MEMBER", "MEMBER")).toBe(false);
    expect(canManageSprints("GUEST", "OWNER")).toBe(false);
    expect(canManageSprints(null, "ADMIN")).toBe(false);
  });

  it("keeps board edits to admins and deletes to owners", () => {
    expect(canEditBoard("ADMIN")).toBe(true);
    expect(canEditBoard("MEMBER")).toBe(false);
    expect(canDeleteBoard("ADMIN")).toBe(false);
    expect(canDeleteBoard("OWNER")).toBe(true);
  });

  it("lets every board role but viewer move tickets together, reading no organization role", () => {
    expect(canEditBoardTickets("OWNER")).toBe(true);
    expect(canEditBoardTickets("ADMIN")).toBe(true);
    expect(canEditBoardTickets("MEMBER")).toBe(true);
    expect(canEditBoardTickets("VIEWER")).toBe(false);
    expect(canEditBoardTickets(null)).toBe(false);
  });
});

describe("board route params", () => {
  const state: BoardRouteState = {
    view: "sprint",
    layout: "list",
    sprintId: "s1",
    search: " notif ",
    filters: {
      ...EMPTY_FILTERS,
      statuses: ["TODO", "IN_REVIEW"],
      types: ["BUG", "INCIDENT"],
      due: "week",
    },
  };

  it("round-trips a state through the params", () => {
    const params = boardRouteParams(state);
    expect(params).toEqual({
      view: "sprint",
      layout: "list",
      sprintId: "s1",
      q: "notif",
      statuses: "TODO,IN_REVIEW",
      types: "BUG,INCIDENT",
      due: "week",
    });
    expect(parseBoardRoute(params)).toEqual({ ...state, search: "notif" });
  });

  it("falls back to defaults for unknown values", () => {
    expect(parseBoardRoute({ view: "gantt", layout: "grid", due: "month" })).toEqual({
      view: "all",
      layout: "kanban",
      sprintId: null,
      search: "",
      filters: EMPTY_FILTERS,
    });
  });

  it("drops a status the API does not know", () => {
    expect(parseBoardRoute({ statuses: "DONE,ARCHIVED,todo" }).filters.statuses).toEqual(["DONE"]);
  });

  it("clears every param the new state leaves at its default", () => {
    const patch = boardRouteParamPatch({ ...state, view: "all", filters: EMPTY_FILTERS });
    expect(patch.view).toBeUndefined();
    expect(patch.types).toBeUndefined();
    expect(Object.keys(patch)).toContain("statuses");
    expect(patch.statuses).toBeUndefined();
    expect(patch.layout).toBe("list");
    expect(Object.keys(patch)).toContain("due");
  });

  it("counts filter groups and toggles values", () => {
    expect(activeFilterCount(state.filters)).toBe(3);
    expect(toggleValue(["a"], "b")).toEqual(["a", "b"]);
    expect(toggleValue(["a", "b"], "a")).toEqual(["b"]);
    expect(toggleStatus(["DONE"], "TODO")).toEqual(["TODO", "DONE"]);
    expect(toggleStatus(["TODO", "DONE"], "DONE")).toEqual(["TODO"]);
  });
});

describe("boardTicketParams", () => {
  it("scopes by sprint or backlog and maps the filters to the API names", () => {
    const params = boardTicketParams(
      { sprintId: "s1" },
      {
        ...EMPTY_FILTERS,
        statuses: ["IN_PROGRESS", "WAITING_FOR_THIRD_PARTY"],
        assignees: ["u1", "u2"],
        labels: ["l1"],
        project: "p1",
        milestone: "m1",
      },
      " pay "
    );
    expect(params).toEqual({
      sortBy: "displayOrder",
      sortDirection: "asc",
      sprintId: "s1",
      search: "pay",
      statuses: "IN_PROGRESS,WAITING_FOR_THIRD_PARTY",
      assigneeIds: "u1,u2",
      labelIds: "l1",
      projectId: "p1",
      milestoneId: "m1",
    });
    expect(boardTicketParams({ backlogId: "b1" }, EMPTY_FILTERS, "").backlogId).toBe("b1");
  });

  it("turns This week into Monday to Sunday of the viewer's week", () => {
    // Thursday 5 Nov 2026, 22:00 in New York.
    const params = boardTicketParams(
      { sprintId: "s1" },
      { ...EMPTY_FILTERS, due: "week" },
      "",
      new Date("2026-11-06T03:00:00Z")
    );
    expect(params.dueDateStart).toBe("2026-11-02");
    expect(params.dueDateEnd).toBe("2026-11-08");
  });
});

describe("sprints", () => {
  const sprints: Sprint[] = [
    sprint("done", "COMPLETED", "2026-10-01T00:00:00Z"),
    sprint("later", "PLANNED", "2026-11-20T00:00:00Z"),
    sprint("now", "ACTIVE", "2026-11-01T00:00:00Z"),
    sprint("next", "PLANNED", "2026-11-10T00:00:00Z"),
  ];

  it("picks the sprint each view shows", () => {
    expect(sprintForView("all", sprints, null)?.id).toBe("now");
    expect(sprintForView("sprint", sprints, "later")?.id).toBe("later");
    expect(sprintForView("all", sprints, "missing")?.id).toBe("now");
    expect(sprintForView("backlog", sprints, "now")?.id).toBe("next");
    expect(sprintForView("roadmap", sprints, "now")).toBeNull();
  });

  it("falls back to the first planned sprint, then none", () => {
    expect(sprintForView("all", [sprints[1]], null)?.id).toBe("later");
    expect(sprintForView("all", [], null)).toBeNull();
    expect(nextPlannedSprint([sprints[0]])).toBeNull();
  });

  it("orders the picker active, planned, completed", () => {
    expect(sortSprintsForPicker(sprints).map((item) => item.id)).toEqual([
      "now",
      "later",
      "next",
      "done",
    ]);
  });
});

describe("tickets", () => {
  it("groups by status in board order with every column present", () => {
    const groups = groupByStatus([
      ticket({ id: "a", status: "DONE" }),
      ticket({ id: "b", status: "UNKNOWN" }),
    ]);
    expect(groups[0].tickets.map((item) => item.id)).toEqual(["b"]);
    expect(groups[groups.length - 1].tickets.map((item) => item.id)).toEqual(["a"]);
    expect(statusCounts([ticket({ status: "DONE" })]).DONE).toBe(1);
  });

  it("sorts the backlog by rank, priority or newest without mutating", () => {
    const input = [
      ticket({ id: "low", displayOrder: 2, priority: "LOW", createdAt: "2026-11-03T00:00:00Z" }),
      ticket({ id: "p1", displayOrder: 3, priority: "P1", createdAt: "2026-11-01T00:00:00Z" }),
      ticket({ id: "top", displayOrder: 1, priority: "HIGH", createdAt: "2026-11-02T00:00:00Z" }),
    ];
    expect(sortBacklog(input, "rank").map((item) => item.id)).toEqual(["top", "low", "p1"]);
    expect(sortBacklog(input, "priority").map((item) => item.id)).toEqual(["p1", "top", "low"]);
    expect(sortBacklog(input, "created").map((item) => item.id)).toEqual(["low", "top", "p1"]);
    expect(input[0].id).toBe("low");
  });

  it("reports sub-ticket progress only for parents", () => {
    expect(subTicketProgress(ticket({}))).toBeNull();
    expect(
      subTicketProgress(
        ticket({
          subTickets: [
            { id: "1", status: "DONE" },
            { id: "2", status: "TODO" },
          ],
        })
      )
    ).toEqual({ done: 1, total: 2 });
  });
});

describe("roadmap", () => {
  it("finds the quarter and its months, across a year end", () => {
    const quarter = quarterOf(new Date(2026, 10, 6));
    expect(quarter).toEqual({
      quarter: 4,
      year: 2026,
      from: "2026-10-01",
      to: "2026-12-31",
      months: ["2026-10-01", "2026-11-01", "2026-12-01"],
    });
    expect(quarterOf(new Date(2026, 10, 6), 1).from).toBe("2027-01-01");
    expect(quarterOf(new Date(2026, 1, 6), -1).to).toBe("2025-12-31");
  });

  it("marks today only inside the quarter", () => {
    const quarter = quarterOf(new Date(2026, 10, 6));
    expect(quarterTodayPercent(quarter, "2026-10-01")).toBe(0);
    expect(quarterTodayPercent(quarter, "2027-01-01")).toBeNull();
    expect(quarterTodayPercent(quarter, "2026-11-15")).toBeGreaterThan(40);
  });

  it("places a milestone bar and clamps it to the quarter", () => {
    const bar = milestoneBar(
      { startDate: "2026-09-01", targetDate: "2026-10-31" },
      "2026-10-01",
      "2026-12-31"
    );
    expect(bar?.start).toBe(0);
    expect(bar?.width).toBeCloseTo((31 / 92) * 100, 5);
    expect(
      milestoneBar({ startDate: null, targetDate: null }, "2026-10-01", "2026-12-31")
    ).toBeNull();
  });

  it("computes done share as a whole percent", () => {
    expect(milestoneProgress({ doneCount: 1, totalCount: 3 })).toBe(33);
    expect(milestoneProgress({ doneCount: null, totalCount: null })).toBe(0);
  });
});

describe("groupUnderParents", () => {
  const parent = { id: "p", key: "KZN-9", title: "Parent" };

  it("gathers the sub-tickets of a parent from elsewhere at the first of them", () => {
    const rows: BoardTicket[] = [
      ticket({ id: "a", parentTicketId: "p", parentTicket: parent }),
      ticket({ id: "b" }),
      ticket({ id: "c", parentTicketId: "p", parentTicket: parent }),
    ];
    const known: BoardTicket[] = [
      ticket({
        id: "p",
        subTickets: [
          { id: "a", status: "DONE" },
          { id: "c", status: "TODO" },
          { id: "x", status: "DONE" },
        ],
      }),
    ];
    const result = groupUnderParents(rows, known);
    expect(result.rows.map((row) => row.id)).toEqual(["a", "c", "b"]);
    expect(result.headers.get("a")).toEqual({
      parent,
      progress: { done: 2, total: 3 },
      parentDone: false,
    });
    expect([...result.children]).toEqual(["a", "c"]);
  });

  it("leaves a sub-ticket whose parent is in the same list in place", () => {
    const rows: BoardTicket[] = [
      ticket({ id: "p" }),
      ticket({ id: "a", parentTicketId: "p", parentTicket: { ...parent } }),
    ];
    const result = groupUnderParents(rows);
    expect(result.rows.map((row) => row.id)).toEqual(["p", "a"]);
    expect(result.headers.size).toBe(0);
  });

  it("has no progress when the parent is not loaded", () => {
    const rows: BoardTicket[] = [ticket({ id: "a", parentTicketId: "p", parentTicket: parent })];
    expect(groupUnderParents(rows).headers.get("a")?.progress).toBeNull();
  });
});

describe("sprintProgressSegments", () => {
  it("orders the statuses furthest along first and rounds the done share", () => {
    const result = sprintProgressSegments([
      ticket({ id: "a", status: "DONE" }),
      ticket({ id: "b", status: "TODO" }),
      ticket({ id: "c", status: "IN_REVIEW" }),
    ]);
    expect(result.segments.map((segment) => segment.status)).toEqual([
      "DONE",
      "IN_REVIEW",
      "WAITING_FOR_THIRD_PARTY",
      "IN_PROGRESS",
      "TODO",
    ]);
    expect(result.segments.map((segment) => segment.count)).toEqual([1, 1, 0, 0, 1]);
    expect(result.donePercent).toBe(33);
  });

  it("is zero percent without tickets", () => {
    expect(sprintProgressSegments([]).donePercent).toBe(0);
  });
});
