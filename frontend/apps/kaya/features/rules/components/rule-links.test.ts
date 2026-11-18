import { describe, expect, test } from "bun:test";

import {
  kindOfSegment,
  legacyFileRedirect,
  newFileStart,
  ruleDetailHref,
  ruleNewHref,
  rulesListHref,
} from "./rule-links";

const PATH = ".agents/rules/11-backend-rules.md";
const CURRENT = new URLSearchParams(
  "scope=pack%3Ap-1&release=4&draft=d-1&inherited=true&file=x&at=v-2&view=split&panel=links&compare=v-3&tab=hooks"
);

describe("kindOfSegment", () => {
  test("a Rules tab id is its kind, any other segment is none", () => {
    expect(kindOfSegment("rules")).toBe("rules");
    expect(kindOfSegment("agents-md")).toBe("agents-md");
    expect(kindOfSegment("hooks")).toBe("hooks");
    expect(kindOfSegment("bogus")).toBeNull();
    expect(kindOfSegment("")).toBeNull();
    expect(kindOfSegment("Rules")).toBeNull();
  });
});

describe("rule addresses", () => {
  test("a detail address encodes the path as one segment and keeps scope, release and inherited, and drops a draft", () => {
    expect(ruleDetailHref("rules", PATH, CURRENT)).toBe(
      `/rules/rules/${encodeURIComponent(PATH)}?scope=pack%3Ap-1&release=4&inherited=true`
    );
  });

  test("a detail address drops file, at, view, panel, compare and tab", () => {
    const href = ruleDetailHref("hooks", ".agents/hooks/a.sh", CURRENT);
    for (const dropped of ["file=", "at=", "view=", "panel=", "compare=", "tab="]) {
      expect(href).not.toContain(dropped);
    }
  });

  test("without parameters to keep the address has no query", () => {
    expect(ruleDetailHref("agents-md", "AGENTS.md", new URLSearchParams())).toBe(
      "/rules/agents-md/AGENTS.md"
    );
    expect(ruleNewHref("roles", new URLSearchParams())).toBe("/rules/roles/new");
  });

  test("the new-file address keeps the same parameters", () => {
    expect(ruleNewHref("subagents", CURRENT)).toBe(
      "/rules/subagents/new?scope=pack%3Ap-1&release=4&inherited=true"
    );
  });

  test("the list address carries the tab unless it is the default one", () => {
    expect(rulesListHref("rules", CURRENT)).toBe(
      "/rules?scope=pack%3Ap-1&release=4&inherited=true"
    );
    expect(rulesListHref("hooks", CURRENT)).toBe(
      "/rules?scope=pack%3Ap-1&release=4&inherited=true&tab=hooks"
    );
    expect(rulesListHref("rules", new URLSearchParams())).toBe("/rules");
    expect(rulesListHref("roles", new URLSearchParams())).toBe("/rules?tab=roles");
  });
});

describe("legacyFileRedirect", () => {
  test("a link without file does not redirect", () => {
    expect(legacyFileRedirect(new URLSearchParams("tab=rules&scope=pack%3Ap-1"))).toBeNull();
    expect(legacyFileRedirect(new URLSearchParams())).toBeNull();
  });

  test("a rule path without tab leads to the rules detail page, keeping every other parameter", () => {
    const query = new URLSearchParams(
      `file=${encodeURIComponent(PATH)}&scope=pack%3Ap-1&release=4&inherited=true&at=v-2&panel=history&compare=v-3`
    );
    expect(legacyFileRedirect(query)).toBe(
      `/rules/rules/${encodeURIComponent(PATH)}?scope=pack%3Ap-1&release=4&inherited=true&at=v-2&panel=history&compare=v-3`
    );
  });

  test("the path decides the kind over the tab parameter, and the tab parameter is dropped", () => {
    const query = new URLSearchParams(`tab=roles&file=${encodeURIComponent(PATH)}`);
    expect(legacyFileRedirect(query)).toBe(`/rules/rules/${encodeURIComponent(PATH)}`);
  });

  test("a path that fits no tab takes the valid tab parameter", () => {
    const query = new URLSearchParams("tab=hooks&file=scripts%2Fa.sh&scope=pack%3Ap-1");
    expect(legacyFileRedirect(query)).toBe("/rules/hooks/scripts%2Fa.sh?scope=pack%3Ap-1");
  });

  test("a path that fits no tab and no valid tab leads to the list without file", () => {
    expect(legacyFileRedirect(new URLSearchParams("file=scripts%2Fa.sh&scope=pack%3Ap-1"))).toBe(
      "/rules?scope=pack%3Ap-1"
    );
    expect(legacyFileRedirect(new URLSearchParams("tab=bogus&file=scripts%2Fa.sh"))).toBe(
      "/rules?tab=bogus"
    );
    expect(legacyFileRedirect(new URLSearchParams("file=scripts%2Fa.sh"))).toBe("/rules");
  });

  test("an AGENTS.md path leads to the agents-md kind", () => {
    expect(legacyFileRedirect(new URLSearchParams("file=AGENTS.md&release=4"))).toBe(
      "/rules/agents-md/AGENTS.md?release=4"
    );
  });
});

describe("newFileStart", () => {
  test("each tab starts in its folder, hooks executable", () => {
    expect(newFileStart("rules", null)).toEqual({ path: ".agents/rules/", executable: false });
    expect(newFileStart("agents-md", null)).toEqual({ path: "AGENTS.md", executable: false });
    expect(newFileStart("hooks", null)).toEqual({ path: ".agents/hooks/", executable: true });
  });

  test("a mounted pack starts in its namespace and may not add hooks", () => {
    const mount = { name: "kagi", mountPaths: ["projects/kagi"] };
    expect(newFileStart("rules", mount)).toEqual({
      path: ".agents/rules/kagi/",
      executable: false,
    });
    expect(newFileStart("hooks", mount)).toBeNull();
  });
});
