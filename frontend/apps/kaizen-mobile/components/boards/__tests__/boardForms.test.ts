import { describe, expect, it } from "bun:test";

import {
  assigneeOptions,
  boardIdBySlug,
  boardNameProblem,
  boardPrefixProblem,
  filterResultCount,
  newBoardRecheck,
  normalizePrefix,
  parentOptionLabel,
  VISIBLE_ASSIGNEES,
  visibleAssignees,
  type AssigneeOption,
} from "@/components/boards/boardForms";
import type { BoardRow } from "@/components/boards/boardModel";
import type { BoardMember, BoardTreeItem } from "@/lib/schemas/boards";

describe("boardNameProblem", () => {
  it("requires a name that is not blank", () => {
    expect(boardNameProblem("")).toBe("required");
    expect(boardNameProblem("   ")).toBe("required");
  });

  it("refuses a name over 255 characters", () => {
    expect(boardNameProblem("a".repeat(256))).toBe("too-long");
    expect(boardNameProblem("a".repeat(255))).toBeNull();
  });
});

describe("boardPrefixProblem", () => {
  it("requires a key", () => {
    expect(boardPrefixProblem("")).toBe("required");
  });

  it("allows at most five characters", () => {
    expect(boardPrefixProblem("ABCDEF")).toBe("too-long");
    expect(boardPrefixProblem("ABCDE")).toBeNull();
  });

  it("allows only upper-case letters, digits and underscores", () => {
    expect(boardPrefixProblem("kz")).toBe("pattern");
    expect(boardPrefixProblem("K-Z")).toBe("pattern");
    expect(boardPrefixProblem("K_Z2")).toBeNull();
  });
});

describe("normalizePrefix", () => {
  it("upper-cases and drops spaces", () => {
    expect(normalizePrefix(" kz n ")).toBe("KZN");
  });
});

describe("newBoardRecheck", () => {
  const before = { name: "Web", prefix: "WEB" };

  it("checks nothing before the first create while the name fills in the key", () => {
    expect(
      newBoardRecheck(
        before,
        { name: "Webs", prefix: "WEB" },
        { submitted: false, typedKey: false }
      )
    ).toEqual({ name: false, prefix: false });
    expect(
      newBoardRecheck(
        { name: "", prefix: "" },
        { name: "A", prefix: "A" },
        {
          submitted: false,
          typedKey: false,
        }
      )
    ).toEqual({ name: false, prefix: false });
  });

  it("checks a typed key before the first create, as the web form does", () => {
    expect(
      newBoardRecheck(before, { name: "Web", prefix: "" }, { submitted: false, typedKey: true })
    ).toEqual({ name: false, prefix: true });
  });

  it("checks the key again after a create when the name fills it in", () => {
    expect(
      newBoardRecheck(
        { name: "", prefix: "" },
        { name: "M", prefix: "M" },
        {
          submitted: true,
          typedKey: false,
        }
      )
    ).toEqual({ name: true, prefix: true });
  });

  it("checks a cleared name and the key it empties after a create", () => {
    expect(
      newBoardRecheck(
        { name: "M", prefix: "M" },
        { name: "", prefix: "" },
        {
          submitted: true,
          typedKey: false,
        }
      )
    ).toEqual({ name: true, prefix: true });
  });

  it("keeps the key's error when the name changes but the key does not", () => {
    expect(
      newBoardRecheck(before, { name: "Webs", prefix: "WEB" }, { submitted: true, typedKey: false })
    ).toEqual({ name: true, prefix: false });
  });

  it("keeps the name's error when only the key is typed", () => {
    expect(
      newBoardRecheck(before, { name: "Web", prefix: "WE" }, { submitted: true, typedKey: true })
    ).toEqual({ name: false, prefix: true });
  });
});

describe("parentOptionLabel", () => {
  const row: BoardRow = {
    id: "b",
    name: "Mobile",
    prefix: "MOB",
    slug: "mobile",
    color: null,
    icon: null,
    depth: 1,
    parentId: "a",
    parentName: "Kaizen",
    childCount: 0,
  };

  it("shows the key and indents child boards", () => {
    expect(parentOptionLabel(row)).toBe("  (MOB) Mobile");
    expect(parentOptionLabel({ ...row, depth: 0, prefix: null })).toBe("Mobile");
  });
});

describe("assigneeOptions", () => {
  const member = (userId: string, firstName: string): BoardMember => ({
    id: `m-${userId}`,
    userId,
    firstName,
    lastName: null,
    email: null,
    role: "MEMBER",
  });

  it("puts the signed-in user first, then sorts by name", () => {
    const options: AssigneeOption[] = assigneeOptions(
      [member("u3", "Zeynep"), member("u1", "Ayşe"), member("me", "Mert")],
      "me"
    );
    expect(options.map((option) => option.id)).toEqual(["me", "u1", "u3"]);
    expect(options[0].isMe).toBe(true);
  });
});

describe("visibleAssignees", () => {
  const options: AssigneeOption[] = Array.from({ length: VISIBLE_ASSIGNEES + 3 }, (_, index) => ({
    id: `u${index}`,
    name: `Person ${index}`,
    isMe: false,
  }));

  it("shows the first few and counts the rest", () => {
    const result = visibleAssignees(options, [], false);
    expect(result.shown).toHaveLength(VISIBLE_ASSIGNEES);
    expect(result.hidden).toBe(3);
  });

  it("keeps a selected person visible", () => {
    const last: string = options[options.length - 1].id;
    const result = visibleAssignees(options, [last], false);
    expect(result.shown.map((option) => option.id)).toContain(last);
    expect(result.hidden).toBe(2);
  });

  it("shows everyone when expanded", () => {
    expect(visibleAssignees(options, [], true).hidden).toBe(0);
  });
});

describe("filterResultCount", () => {
  it("sums the enabled lists", () => {
    expect(
      filterResultCount([
        { enabled: true, data: [1, 2] },
        { enabled: true, data: [3] },
        { enabled: false, data: undefined },
      ])
    ).toBe(3);
  });

  it("waits for every enabled list", () => {
    expect(
      filterResultCount([
        { enabled: true, data: [1] },
        { enabled: true, data: undefined },
      ])
    ).toBeNull();
  });
});

describe("boardIdBySlug", () => {
  const tree: BoardTreeItem[] = [
    {
      id: "a",
      name: "Kaizen",
      slug: "kaizen",
      childBoards: [{ id: "b", name: "Mobile", slug: "mobile", childBoards: null }],
    },
  ];

  it("finds top-level and child boards", () => {
    expect(boardIdBySlug(tree, "kaizen")).toBe("a");
    expect(boardIdBySlug(tree, "mobile")).toBe("b");
    expect(boardIdBySlug(tree, "missing")).toBeNull();
  });
});
