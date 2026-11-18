import { describe, expect, test } from "bun:test";

import { checksText, DEFAULT_PANEL, panelOf, PANELS } from "./document-panels-data";

describe("document panels", () => {
  test("the tabs are Links, History and Checks, Links by default", () => {
    expect(PANELS.map((panel) => panel.label)).toEqual(["Links", "History", "Checks"]);
    expect(DEFAULT_PANEL).toBe("links");
  });

  test("a tab value maps to its panel; an unknown one to none", () => {
    expect(panelOf("history")).toBe("history");
    expect(panelOf("checks")).toBe("checks");
    expect(panelOf("comments")).toBeNull();
  });
});

describe("checksText", () => {
  test("an empty list means the draft may be submitted", () => {
    expect(checksText([])).toBe("Your change breaks no rule, so it may be submitted.");
  });

  test("violations are counted", () => {
    const violation = { rule: "PATH_NOT_ALLOWED", path: "x.md", message: "Not allowed." };
    expect(checksText([violation])).toContain("breaks a rule");
    expect(checksText([violation, violation])).toContain("breaks 2 rules");
  });

  test("no list means the server did not check the draft", () => {
    expect(checksText(null)).toBe("Kaya checks only your own open change.");
  });
});
