import { describe, expect, it } from "bun:test";

import { flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import {
  addExistingBoardRows,
  addExistingCandidates,
  addExistingPreview,
  boardOptionBody,
  boardOptionDefaults,
  boardOptionNameProblem,
  boardOptionRows,
  candidateName,
  candidateSubtitle,
  canRemoveChild,
  childBoardRows,
  hiddenDescendantIds,
  isBoardManageRole,
  isMilestoneFormValid,
  memberActions,
  memberCandidates,
  milestoneCreateBody,
  milestoneDotColor,
  milestoneFormDefaults,
  milestoneFormProblems,
  milestoneSections,
  milestoneUpdateBody,
  normalizePicks,
  togglePick,
  type MilestoneFormValues,
} from "@/components/boards/settings/boardSettingsModel";
import type {
  BoardMember,
  BoardMemberCandidate,
  BoardOwnedOption,
  BoardTreeItem,
  Milestone,
  MilestoneDetail,
} from "@/lib/schemas/boards";

const BOARD = "b0000000-0000-0000-0000-000000000001";

function treeBoard(id: string, name: string, childBoards: BoardTreeItem[] = []): BoardTreeItem {
  return {
    id,
    name,
    slug: name.toLowerCase(),
    prefix: name.slice(0, 3).toUpperCase(),
    childBoards,
  };
}

/*
 * Alpha
 *   Beta (the board under test)
 *     Gamma
 *       Delta
 *     Epsilon
 *   Zeta
 * Omega
 *   Psi
 */
const TREE: BoardTreeItem[] = [
  treeBoard("alpha", "Alpha", [
    treeBoard("beta", "Beta", [
      treeBoard("gamma", "Gamma", [treeBoard("delta", "Delta")]),
      treeBoard("epsilon", "Epsilon"),
    ]),
    treeBoard("zeta", "Zeta"),
  ]),
  treeBoard("omega", "Omega", [treeBoard("psi", "Psi")]),
];
const ROWS: BoardRow[] = flattenBoardTree(TREE);

const ids = (rows: readonly { board: BoardRow }[]): string[] => rows.map((row) => row.board.id);

describe("isBoardManageRole", () => {
  it("is true for OWNER and ADMIN only", () => {
    expect(isBoardManageRole("OWNER")).toBe(true);
    expect(isBoardManageRole("ADMIN")).toBe(true);
    expect(isBoardManageRole("MEMBER")).toBe(false);
    expect(isBoardManageRole("VIEWER")).toBe(false);
    expect(isBoardManageRole(undefined)).toBe(false);
  });
});

describe("labels and projects", () => {
  const own: BoardOwnedOption = {
    id: "l1",
    name: "Bug",
    color: "#EF4444",
    boardId: BOARD,
    boardName: "Kazan",
  };
  const related: BoardOwnedOption = {
    id: "l2",
    name: "Infra",
    color: null,
    boardId: "parent",
    boardName: "Platform",
  };

  it("keeps one list in the server's order and names the owner of a related board's option", () => {
    const rows = boardOptionRows([related, own], BOARD);
    expect(rows.map((row) => row.option.id)).toEqual(["l2", "l1"]);
    expect(rows[0]).toMatchObject({ related: true, ownerBoardName: "Platform" });
    expect(rows[1]).toMatchObject({ related: false, ownerBoardName: null });
  });

  it("opens a new option empty in blue and an existing one with its values", () => {
    expect(boardOptionDefaults(null)).toEqual({ name: "", color: "#3B82F6" });
    expect(boardOptionDefaults(own)).toEqual({ name: "Bug", color: "#EF4444" });
    expect(boardOptionDefaults(related)).toEqual({ name: "Infra", color: "#3B82F6" });
  });

  it("refuses only a blank name; length and duplicates are the server's", () => {
    expect(boardOptionNameProblem("")).toBe("required");
    expect(boardOptionNameProblem("   ")).toBe("required");
    expect(boardOptionNameProblem("a".repeat(300))).toBeNull();
    expect(boardOptionNameProblem("Bug")).toBeNull();
  });

  it("sends the trimmed name with the colour", () => {
    expect(boardOptionBody("  Bug  ", "#22C55E")).toEqual({ name: "Bug", color: "#22C55E" });
  });
});

describe("milestones", () => {
  const milestone = (overrides: Partial<Milestone>): Milestone => ({
    id: "m",
    name: "M",
    status: "OPEN",
    targetDate: "2026-12-01",
    ...overrides,
  });

  it("splits open and closed, each by target date then name", () => {
    const sections = milestoneSections([
      milestone({ id: "c", name: "C", targetDate: "2026-12-01" }),
      milestone({ id: "x", status: "CLOSED", targetDate: "2026-01-01" }),
      milestone({ id: "a", name: "A", targetDate: "2026-12-01" }),
      milestone({ id: "e", name: "E", targetDate: "2026-11-01" }),
    ]);
    expect(sections.open.map((item) => item.id)).toEqual(["e", "a", "c"]);
    expect(sections.closed.map((item) => item.id)).toEqual(["x"]);
  });

  it("colours a milestone's dot with its own colour, else its board's", () => {
    expect(milestoneDotColor({ color: "#FBBF24" }, "#4AA3FF")).toBe("#FBBF24");
    expect(milestoneDotColor({ color: null }, "#4AA3FF")).toBe("#4AA3FF");
    expect(milestoneDotColor({ color: null }, null)).toBeNull();
  });

  const detail: MilestoneDetail = {
    id: "m1",
    name: "Beta",
    status: "OPEN",
    startDate: "2026-11-01",
    targetDate: "2026-12-15",
    boardId: BOARD,
    color: "#4AA3FF",
    description: "Store listings",
  };

  it("starts a new milestone in the board colour only when it is #RRGGBB", () => {
    expect(milestoneFormDefaults(null, "#4ADE80")).toEqual({
      name: "",
      description: "",
      startDate: null,
      targetDate: null,
      color: "#4ADE80",
    });
    expect(milestoneFormDefaults(null, "blue").color).toBeNull();
    expect(milestoneFormDefaults(null, null).color).toBeNull();
  });

  it("opens an edit with the detail and empties a target that is not a day", () => {
    expect(milestoneFormDefaults(detail, "#000000")).toEqual({
      name: "Beta",
      description: "Store listings",
      startDate: "2026-11-01",
      targetDate: "2026-12-15",
      color: "#4AA3FF",
    });
    expect(milestoneFormDefaults({ ...detail, targetDate: "soon" }, null).targetDate).toBeNull();
  });

  const valid: MilestoneFormValues = {
    name: "Beta",
    description: "",
    startDate: null,
    targetDate: "2026-12-15",
    color: null,
  };

  it("checks the web form's rules", () => {
    expect(milestoneFormProblems(valid)).toEqual({});
    expect(isMilestoneFormValid(valid)).toBe(true);
    expect(milestoneFormProblems({ ...valid, name: "  " }).name).toBe("nameRequired");
    expect(milestoneFormProblems({ ...valid, name: `${"a".repeat(255)}  ` }).name).toBeUndefined();
    expect(milestoneFormProblems({ ...valid, name: "a".repeat(256) }).name).toBe("nameTooLong");
    expect(milestoneFormProblems({ ...valid, description: "d".repeat(2001) }).description).toBe(
      "descriptionTooLong"
    );
    expect(milestoneFormProblems({ ...valid, targetDate: null }).targetDate).toBe(
      "targetDateRequired"
    );
    expect(milestoneFormProblems({ ...valid, startDate: "2026-12-16" }).startDate).toBe(
      "startAfterTarget"
    );
    expect(milestoneFormProblems({ ...valid, startDate: "2026-12-15" })).toEqual({});
    expect(isMilestoneFormValid({ ...valid, targetDate: null })).toBe(false);
  });

  it("creates with only the fields that hold a value", () => {
    expect(milestoneCreateBody({ ...valid, name: " Beta ", description: "  " })).toEqual({
      name: "Beta",
      targetDate: "2026-12-15",
    });
    expect(
      milestoneCreateBody({
        ...valid,
        description: " Store ",
        startDate: "2026-11-01",
        color: "#C084FC",
      })
    ).toEqual({
      name: "Beta",
      description: "Store",
      startDate: "2026-11-01",
      targetDate: "2026-12-15",
      color: "#C084FC",
    });
  });

  it("updates with clear flags for emptied fields and the colour only when changed", () => {
    expect(
      milestoneUpdateBody({ ...valid, description: "", startDate: null, color: "#4AA3FF" }, detail)
    ).toEqual({
      name: "Beta",
      targetDate: "2026-12-15",
      clearDescription: true,
      clearStartDate: true,
    });
    expect(
      milestoneUpdateBody(
        { ...valid, description: "New", startDate: "2026-11-02", color: "#FBBF24" },
        detail
      )
    ).toEqual({
      name: "Beta",
      description: "New",
      startDate: "2026-11-02",
      targetDate: "2026-12-15",
      color: "#FBBF24",
    });
  });

  it("sends no clear flag for a field the milestone never had", () => {
    const bare: MilestoneDetail = { ...detail, description: null, startDate: null, color: null };
    expect(milestoneUpdateBody(valid, bare)).toEqual({ name: "Beta", targetDate: "2026-12-15" });
  });
});

describe("members", () => {
  const candidate = (
    userId: string,
    email: string,
    firstName: string | null = null,
    lastName: string | null = null,
    kazanAccess: boolean = true
  ): BoardMemberCandidate => ({ userId, email, firstName, lastName, role: "MEMBER", kazanAccess });
  const users: BoardMemberCandidate[] = [
    candidate("u1", "dev@example.com", "Dev", "User"),
    candidate("u2", "ayse@example.com", "Ayşe", "Kaya", false),
    candidate("u3", "nobody@example.com"),
  ];
  const member = (userId: string, role: string): BoardMember => ({
    id: `m-${userId}`,
    userId,
    role,
  });

  it("names a candidate by name, else by email", () => {
    expect(candidateName(users[0]!)).toBe("Dev User");
    expect(candidateName(users[2]!)).toBe("nobody@example.com");
  });

  it("offers the candidates who are not members, in the server's order, those without Kazan included", () => {
    expect(memberCandidates(users, [member("u1", "OWNER")], "").map((item) => item.userId)).toEqual(
      ["u2", "u3"]
    );
  });

  it("matches the search in the full name or the email, ignoring case", () => {
    expect(memberCandidates(users, [], "KAYA").map((item) => item.userId)).toEqual(["u2"]);
    expect(memberCandidates(users, [], "NOBODY@").map((item) => item.userId)).toEqual(["u3"]);
    expect(memberCandidates(users, [], "dev user").map((item) => item.userId)).toEqual(["u1"]);
    expect(memberCandidates(users, [], "nope")).toEqual([]);
  });

  it("gives a candidate without Kazan the reason under the email", () => {
    expect(candidateSubtitle(users[0]!, "hint")).toBe("dev@example.com");
    expect(candidateSubtitle(users[1]!, "hint")).toBe("ayse@example.com\nhint");
    expect(candidateSubtitle(users[2]!, "hint")).toBeUndefined();
    expect(candidateSubtitle(candidate("u4", "x@example.com", null, null, false), "hint")).toBe(
      "hint"
    );
    const fromOldApi: BoardMemberCandidate = { ...candidate("u5", "y@example.com", "Y") };
    delete fromOldApi.kazanAccess;
    expect(candidateSubtitle(fromOldApi, "hint")).toBe("y@example.com\nhint");
  });

  it("gives a manager the three roles and Remove on every row but the owner's", () => {
    expect(memberActions(member("u2", "MEMBER"), true)).toEqual({
      roles: [
        { role: "ADMIN", selected: false },
        { role: "MEMBER", selected: true },
        { role: "VIEWER", selected: false },
      ],
    });
    expect(memberActions(member("u1", "OWNER"), true)).toBeNull();
    expect(memberActions(member("u2", "MEMBER"), false)).toBeNull();
  });
});

describe("child boards", () => {
  it("lists every descendant in tree order, indented below the direct children", () => {
    const rows = childBoardRows(ROWS, "beta");
    expect(ids(rows)).toEqual(["epsilon", "gamma", "delta"]);
    expect(rows.map((row) => [row.indent, row.direct])).toEqual([
      [0, true],
      [0, true],
      [1, false],
    ]);
    expect(childBoardRows(ROWS, "zeta")).toEqual([]);
    expect(childBoardRows(ROWS, "missing")).toEqual([]);
  });

  it("offers Remove on a direct child only to a manager who manages that child", () => {
    const [epsilon, gamma, delta] = childBoardRows(ROWS, "beta");
    const roles: Map<string, string> = new Map([
      ["epsilon", "ADMIN"],
      ["gamma", "MEMBER"],
      ["delta", "OWNER"],
    ]);
    expect(canRemoveChild(epsilon!, true, roles)).toBe(true);
    expect(canRemoveChild(epsilon!, false, roles)).toBe(false);
    expect(canRemoveChild(gamma!, true, roles)).toBe(false);
    expect(canRemoveChild(delta!, true, roles)).toBe(false);
    expect(canRemoveChild(epsilon!, true, new Map())).toBe(false);
  });
});

describe("add existing boards", () => {
  const rows: BoardRow[] = addExistingBoardRows(ROWS, "beta");

  it("lists every board but the board and its descendants, ancestors included", () => {
    expect(rows.map((row) => row.id)).toEqual(["alpha", "zeta", "omega", "psi"]);
  });

  it("disables only boards where the reader is MEMBER or VIEWER, with that role", () => {
    const roles: Map<string, string> = new Map([
      ["alpha", "OWNER"],
      ["zeta", "VIEWER"],
      ["omega", "MEMBER"],
    ]);
    expect(
      addExistingCandidates(rows, [], roles).map((item) => [item.board.id, item.blockingRole])
    ).toEqual([
      ["alpha", null],
      ["zeta", "VIEWER"],
      ["omega", "MEMBER"],
      ["psi", null],
    ]);
  });

  it("hides the descendants of a pick and previews them as included", () => {
    expect([...hiddenDescendantIds(rows, ["omega"])]).toEqual(["psi"]);
    expect(addExistingCandidates(rows, ["omega"], new Map()).map((item) => item.board.id)).toEqual([
      "alpha",
      "zeta",
      "omega",
    ]);
    expect(
      addExistingPreview(rows, ["omega", "zeta"]).map((item) => [
        item.board.id,
        item.picked,
        item.indent,
      ])
    ).toEqual([
      ["zeta", true, 0],
      ["omega", true, 0],
      ["psi", false, 1],
    ]);
  });

  it("keeps pick order and drops a pick that a picked ancestor brings along", () => {
    expect(togglePick(rows, [], "psi")).toEqual(["psi"]);
    expect(togglePick(rows, ["psi", "zeta"], "omega")).toEqual(["zeta", "omega"]);
    expect(togglePick(rows, ["zeta", "omega"], "zeta")).toEqual(["omega"]);
    expect(normalizePicks(rows, ["psi", "alpha", "omega"])).toEqual(["alpha", "omega"]);
  });
});
