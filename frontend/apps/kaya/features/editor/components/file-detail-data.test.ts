import { describe, expect, test } from "bun:test";

import {
  breadcrumbOf,
  frontmatterSummary,
  historyCount,
  linksCount,
  tabLabel,
} from "./file-detail-data";

describe("frontmatterSummary", () => {
  test("names the number of fields, one in the singular", () => {
    expect(frontmatterSummary(3)).toBe("Frontmatter, 3 fields");
    expect(frontmatterSummary(1)).toBe("Frontmatter, 1 field");
  });
});

describe("tabLabel", () => {
  test("puts a known count after the label", () => {
    expect(tabLabel("Links", 10)).toBe("Links 10");
    expect(tabLabel("History", 0)).toBe("History 0");
  });

  test("shows the label alone while the count is unknown", () => {
    expect(tabLabel("Links", null)).toBe("Links");
  });
});

describe("linksCount", () => {
  test("counts what the file links to and what uses it", () => {
    const entry = { path: "a.md", relation: "MARKDOWN_LINK", status: "FOUND" } as const;
    expect(linksCount({ path: "b.md", linksTo: [entry, entry], usedBy: [entry] })).toBe(3);
  });

  test("is unknown before the read arrives", () => {
    expect(linksCount(undefined)).toBeNull();
    expect(linksCount(null)).toBeNull();
  });
});

describe("historyCount", () => {
  test("is known only once the last page is loaded", () => {
    expect(historyCount(4, true)).toBe(4);
    expect(historyCount(20, false)).toBeNull();
  });
});

describe("breadcrumbOf", () => {
  const MAIN = ".agents/skills/kaizen/SKILL.md";

  test("gives the folders and name of a part of the document", () => {
    expect(breadcrumbOf(".agents/skills/kaizen/rules/forms.md", MAIN)).toEqual([
      ".agents",
      "skills",
      "kaizen",
      "rules",
      "forms.md",
    ]);
  });

  test("is not shown for the main file or a document with one file", () => {
    expect(breadcrumbOf(MAIN, MAIN)).toBeNull();
    expect(breadcrumbOf(".agents/rules/a.md", undefined)).toBeNull();
    expect(breadcrumbOf(".agents/rules/a.md", null)).toBeNull();
  });
});
