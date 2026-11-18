import { describe, expect, test } from "bun:test";

import type { PathMappingResponse, ToolPathResponse } from "../schemas";
import { PATH_MAPPING_ROWS } from "../schemas/path-mapping-rows.fixture";
import { TOOL_MAPPINGS, withStates } from "../schemas/tool-mappings.fixture";
import {
  groupByLoading,
  loadingHelp,
  NOT_IN_MOUNT,
  notDeliveredLabel,
  readInfoLines,
  readNotDelivered,
  ROOT_AS_LINK_NOTE,
  rootDeliveredAsLink,
  rowInfoLines,
  rowNotDelivered,
  sentence,
  splitDelivered,
  stateHelpLines,
} from "./mapping-groups";

const CLAUDE = TOOL_MAPPINGS[0]!;

function counted(counts: Record<number, number>, others: number | null): PathMappingResponse[] {
  return PATH_MAPPING_ROWS.map((row: PathMappingResponse, index: number) => ({
    ...row,
    count: counts[index] ?? others,
  }));
}

describe("splitDelivered", () => {
  test("keeps the server's order on both sides", () => {
    const split = splitDelivered([1, 2, 3, 4, 5], (value: number) => value % 2 === 0);
    expect(split).toEqual({ delivered: [1, 3, 5], notDelivered: [2, 4] });
  });

  test("tool reads: only NOT_DELIVERED leaves the delivered side; no scope keeps every read", () => {
    const states = withStates(CLAUDE, [
      { state: "LINKED", count: 1 },
      { state: "LINK_MISSING", count: 12 },
      { state: "DIRECT", count: 1 },
      { state: "NOT_DELIVERED", count: 0 },
    ]);
    const split = splitDelivered(states.reads, readNotDelivered);
    expect(split.notDelivered.map((read: ToolPathResponse) => read.readPath)).toEqual([
      ".claude/skills/<name>/SKILL.md",
    ]);
    expect(split.delivered).toHaveLength(CLAUDE.reads.length - 1);
    expect(splitDelivered(CLAUDE.reads, readNotDelivered).notDelivered).toEqual([]);
  });
});

describe("rowNotDelivered", () => {
  test("a row with no count is never set aside, a count of 0 is", () => {
    const rows = counted({ 8: 3 }, 0);
    const rootAsLink = rootDeliveredAsLink(rows);
    const split = splitDelivered(rows, (row: PathMappingResponse) =>
      rowNotDelivered(row, rootAsLink)
    );
    expect(split.delivered.map((row: PathMappingResponse) => row.kind)).toEqual(["HOOK"]);
    expect(split.notDelivered).toHaveLength(PATH_MAPPING_ROWS.length - 1);
    expect(PATH_MAPPING_ROWS.some((row: PathMappingResponse) => rowNotDelivered(row, false))).toBe(
      false
    );
  });

  test("the root AGENTS.md stays delivered when the scope delivers its tool link", () => {
    const rows = counted({ 18: 1 }, 0);
    const rootAsLink = rootDeliveredAsLink(rows);
    expect(rootAsLink).toBe(true);
    expect(rowNotDelivered(rows[0]!, rootAsLink)).toBe(false);
    expect(rowNotDelivered(rows[1]!, rootAsLink)).toBe(true);
    expect(rowNotDelivered(rows[0]!, false)).toBe(true);
  });
});

describe("groupByLoading", () => {
  test("groups At start, When used and When referenced in that order, keeping each group's order", () => {
    const groups = groupByLoading(CLAUDE.reads);
    expect(groups.map((group) => group.loading)).toEqual(["ALWAYS", "ON_DEMAND", "ON_REFERENCE"]);
    expect(groups[0]!.reads.map((read: ToolPathResponse) => read.readPath)).toEqual([
      "AGENTS.md",
      ".claude/rules/<path>.md",
      ".claude/settings.json",
    ]);
    expect(groups.flatMap((group) => group.reads)).toHaveLength(CLAUDE.reads.length);
  });

  test("leaves out a loading kind with no read", () => {
    const groups = groupByLoading(
      CLAUDE.reads.filter((read: ToolPathResponse) => read.loading !== "ON_DEMAND")
    );
    expect(groups.map((group) => group.loading)).toEqual(["ALWAYS", "ON_REFERENCE"]);
    expect(groupByLoading([])).toEqual([]);
  });
});

describe("readInfoLines", () => {
  test("a linked read names the note and the link it passes", () => {
    expect(readInfoLines(CLAUDE.reads[1]!)).toEqual([
      "A rule with paths in its frontmatter loads when Claude Code reads a matching file.",
      "Read through the link .claude/rules, which points to ../.agents/rules.",
    ]);
  });

  test("a direct read without a note has nothing to say", () => {
    expect(readInfoLines(CLAUDE.reads[2]!)).toEqual([]);
  });

  test("a regular root AGENTS.md is read directly, so it names no link", () => {
    const direct = withStates(CLAUDE, [{ state: "DIRECT", count: 1 }]).reads[0]!;
    expect(readInfoLines(direct)).toEqual([]);
  });
});

describe("rowInfoLines", () => {
  test("names where a mounted pack delivers the kind, or that it may not, and the formats", () => {
    expect(rowInfoLines(PATH_MAPPING_ROWS[3]!, false)).toEqual([
      "In a mounted pack: .agents/rules/<pack>/<path>.md",
      "Format: Markdown",
    ]);
    expect(rowInfoLines(PATH_MAPPING_ROWS[1]!, false)).toContain(NOT_IN_MOUNT);
  });

  test("carries the note and the owner review of an executable row", () => {
    const executable = PATH_MAPPING_ROWS.filter((row: PathMappingResponse) => row.executable);
    expect(executable).toHaveLength(3);
    for (const row of executable) {
      expect(rowInfoLines(row, false)).toContain(
        "A change here is an executable change, which an owner approves."
      );
    }
    expect(rowInfoLines(PATH_MAPPING_ROWS[2]!, false)[0]).toBe(
      "For a pack mounted in a folder, at that folder."
    );
  });

  test("the root AGENTS.md says when the scope delivers it as the tool link", () => {
    expect(rowInfoLines(PATH_MAPPING_ROWS[0]!, true)).toContain(ROOT_AS_LINK_NOTE);
    expect(rowInfoLines(PATH_MAPPING_ROWS[0]!, false)).not.toContain(ROOT_AS_LINK_NOTE);
    expect(rowInfoLines(PATH_MAPPING_ROWS[1]!, true)).not.toContain(ROOT_AS_LINK_NOTE);
  });
});

describe("help texts", () => {
  test("each loading kind explains itself as a sentence", () => {
    expect(loadingHelp("ALWAYS")).toBe("Loaded at the start of every session.");
  });

  test("the In scope help names each badge and the dash, never None in scope", () => {
    const repository = stateHelpLines(false);
    expect(repository.map((line: string) => line.split(":")[0])).toEqual([
      "Linked",
      "Direct",
      "Link missing",
      "A dash",
    ]);
    expect(stateHelpLines(true)[2]).toStartWith("From the repository: ");
    expect([...repository, ...stateHelpLines(true)].join(" ")).not.toContain("None in scope");
  });

  test("the closed group counts its rows", () => {
    expect(notDeliveredLabel(4)).toBe("4 not delivered by this scope");
  });

  test("sentence capitalises and ends a text once", () => {
    expect(sentence("read when named")).toBe("Read when named.");
    expect(sentence("Done.")).toBe("Done.");
    expect(sentence("")).toBe("");
  });
});
