import { describe, expect, test } from "bun:test";

import { PATH_MAPPING_ROWS } from "../schemas/path-mapping-rows.fixture";
import { filterMappingRows, kindPageHref, kindRowOf } from "./kind-pages";

describe("kindRowOf", () => {
  test("every row of the server's answer has a label, and a page unless it is a tool link", () => {
    for (const row of PATH_MAPPING_ROWS) {
      const kind = kindRowOf(row);
      expect(kind.label.length).toBeGreaterThan(0);
      if (row.kind === "TOOL_LINK") expect(kind.page).toBeNull();
      else expect(kind.page).not.toBeNull();
    }
  });

  test("a tool link reads as one and links to no page, since only a seed or oba kaya propose writes it", () => {
    expect(kindRowOf({ kind: "TOOL_LINK", part: null, placement: null })).toEqual({
      label: "Tool link",
      page: null,
    });
  });

  test("a skill part reads as the skill and its part, and opens Skills", () => {
    const script = kindRowOf({ kind: "SKILL", part: "SCRIPT", placement: null });
    expect(script.label).toBe("Skill: script");
    expect(script.page?.path).toBe("/skills");
  });

  test("each agents file placement has its own label on the AGENTS.md tab", () => {
    const labels = (["ROOT", "AGENTS_DIR", "MOUNT_ROOT"] as const).map(
      (placement) => kindRowOf({ kind: "AGENTS_FILE", part: null, placement }).label
    );
    expect(new Set(labels).size).toBe(3);
    expect(kindRowOf({ kind: "AGENTS_FILE", part: null, placement: "ROOT" }).page?.tab).toBe(
      "agents-md"
    );
  });

  test("settings opens the Allowlist's Content tab", () => {
    expect(kindRowOf({ kind: "SETTINGS" }).page).toMatchObject({
      path: "/allowlist",
      tab: "content",
    });
  });

  test("a kind the client does not know keeps its name and links nowhere", () => {
    const unknown = kindRowOf({ kind: "FUTURE_KIND" as never });
    expect(unknown).toEqual({ label: "FUTURE_KIND", page: null });
  });
});

describe("kindPageHref", () => {
  test("carries the scope and the tab", () => {
    expect(
      kindPageHref({ label: "Rules: Hooks", path: "/rules", tab: "hooks" }, "repository:r-1")
    ).toBe("/rules?scope=repository%3Ar-1&tab=hooks");
  });

  test("leaves out what is not there", () => {
    expect(kindPageHref({ label: "Skills", path: "/skills", tab: null }, null)).toBe("/skills");
    expect(kindPageHref({ label: "Skills", path: "/skills", tab: null }, "pack:p-1")).toBe(
      "/skills?scope=pack%3Ap-1"
    );
  });
});

describe("filterMappingRows", () => {
  test("an empty search keeps every row", () => {
    expect(filterMappingRows(PATH_MAPPING_ROWS, "")).toHaveLength(PATH_MAPPING_ROWS.length);
    expect(filterMappingRows(PATH_MAPPING_ROWS, "  ")).toHaveLength(PATH_MAPPING_ROWS.length);
  });

  test("a term is found in the pattern, the kind label or the page, ignoring case", () => {
    const first = PATH_MAPPING_ROWS[0];
    if (first === undefined) throw new Error("fixture has no rows");
    const byPattern = filterMappingRows(PATH_MAPPING_ROWS, first.pathPattern.toUpperCase());
    expect(byPattern).toContain(first);
    const skills = filterMappingRows(PATH_MAPPING_ROWS, "skill");
    expect(skills.length).toBeGreaterThan(0);
    for (const row of skills) {
      const kind = kindRowOf(row);
      const text = `${row.pathPattern} ${kind.label} ${kind.page?.label ?? ""}`.toLowerCase();
      expect(text).toContain("skill");
    }
  });

  test("a term nothing contains leaves no row", () => {
    expect(filterMappingRows(PATH_MAPPING_ROWS, "zzz-no-such-row")).toEqual([]);
  });
});
