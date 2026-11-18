import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import {
  allowlistTabsOf,
  hookScriptsHref,
  mayEditSettings,
  shownAllowlistTab,
} from "./allowlist-tabs-data";

const file = (overrides: Partial<ScopeFile> = {}): ScopeFile =>
  ({ path: ".claude/settings.json", mode: "FILE", ownerId: "p-1", ...overrides }) as ScopeFile;

describe("allowlist tabs", () => {
  test("a scope with a settings file shows every tab", () => {
    expect(allowlistTabsOf(true)).toEqual([
      "rules",
      "hooks",
      "content",
      "links",
      "history",
      "checks",
    ]);
  });

  test("without a settings file the tabs that read it are left out", () => {
    expect(allowlistTabsOf(false)).toEqual(["rules", "hooks", "checks"]);
  });

  test("a tab that is not shown opens Rules", () => {
    expect(shownAllowlistTab("history", allowlistTabsOf(false))).toBe("rules");
    expect(shownAllowlistTab("hooks", allowlistTabsOf(false))).toBe("hooks");
  });

  test("a link to the older Settings file tab opens Content", () => {
    expect(shownAllowlistTab("settings", allowlistTabsOf(true))).toBe("content");
  });
});

describe("mayEditSettings", () => {
  test("the pack an edit changes may edit its own file or create one", () => {
    expect(mayEditSettings(file(), "p-1", null)).toBe(true);
    expect(mayEditSettings(null, "p-1", null)).toBe(true);
  });

  test("a repository, another pack's file, a link or an older version is not edited", () => {
    expect(mayEditSettings(file(), null, null)).toBe(false);
    expect(mayEditSettings(file({ ownerId: "p-parent" }), "p-1", null)).toBe(false);
    expect(mayEditSettings(file({ mode: "SYMLINK" }), "p-1", null)).toBe(false);
    expect(mayEditSettings(file(), "p-1", "v-old")).toBe(false);
  });
});

describe("hookScriptsHref", () => {
  test("opens the Rules page's Hooks tab in the same scope", () => {
    expect(hookScriptsHref({ kind: "PACK", packId: "p-1" })).toBe(
      "/rules?scope=pack%3Ap-1&tab=hooks"
    );
  });
});
