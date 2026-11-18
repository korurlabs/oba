import { describe, expect, test } from "bun:test";

import { submitBlocker } from "@/features/versions/components/change-control";
import { UNSAVED_EDIT_BLOCKER } from "@/features/versions/components/draft-context";

import { SETTINGS_FILE } from "./allowlist-data";
import { ruleRowKeys, rulesLiveEdit } from "./allowlist-editor";

describe("unsaved allowlist rules", () => {
  test("a typed rule or an open row edit is an unsaved edit of the settings file", () => {
    expect(rulesLiveEdit("Bash(git *)", 0)).toEqual({
      path: SETTINGS_FILE,
      mode: "FILE",
      text: null,
      replaces: null,
      dirty: true,
    });
    expect(rulesLiveEdit("", 1)?.dirty).toBe(true);
    expect(submitBlocker(false, rulesLiveEdit("", 2)?.dirty === true)).toBe(UNSAVED_EDIT_BLOCKER);
  });

  test("with nothing typed and no row edited, nothing is unsaved", () => {
    expect(rulesLiveEdit("", 0)).toBeNull();
  });

  test("row keys follow the rule, not its place, so removing one keeps the others", () => {
    const before = ruleRowKeys(["Read", "Bash(git *)", "WebFetch"]);
    const after = ruleRowKeys(["Bash(git *)", "WebFetch"]);
    expect(after).toEqual(before.slice(1));
  });

  test("a rule listed twice gets a key per occurrence", () => {
    expect(ruleRowKeys(["Read", "Read"])).toEqual(["Read#0", "Read#1"]);
  });
});
