import { describe, expect, test } from "bun:test";

import { ruleIssue, settingsWithPermissions, toAllowRule, toAllowRules } from "./allowlist-data";

describe("delivered rules", () => {
  test("a rule text splits into tool, pattern and program", () => {
    expect(toAllowRule("Bash(git push:*)", "ask", 1)).toMatchObject({
      tool: "Bash",
      pattern: "git push:*",
      program: "git",
      effect: "ask",
    });
    expect(toAllowRule("WebFetch", "allow", 2)).toMatchObject({
      tool: "WebFetch",
      pattern: null,
      program: null,
    });
    expect(toAllowRule("mcp__github__get_issue", "allow", 3).tool).toBe("mcp__github__get_issue");
  });

  test("the three lists come out deny first", () => {
    const rules = toAllowRules({
      allow: ["Bash(git *)"],
      ask: ["Bash(git push *)"],
      deny: ["Bash(git push --force *)"],
    });
    expect(rules.map((rule) => rule.effect)).toEqual(["deny", "ask", "allow"]);
  });
});

describe("editing the rules", () => {
  test("a rule must parse as Claude Code reads it and be new to its list", () => {
    expect(ruleIssue("Bash(git status *)", [])).toBeNull();
    expect(ruleIssue("WebFetch", [])).toBeNull();
    expect(ruleIssue("mcp__github__get_issue", [])).toBeNull();
    expect(ruleIssue("", [])).not.toBeNull();
    expect(ruleIssue(" Bash(ls)", [])).not.toBeNull();
    expect(ruleIssue("bash(ls)", [])).not.toBeNull();
    expect(ruleIssue("Bash()", [])).not.toBeNull();
    expect(ruleIssue("Bash(ls)", ["Bash(ls)"])).not.toBeNull();
  });

  test("the settings keep every other key and add only lists in use", () => {
    const content = JSON.stringify({
      hooks: { PreToolUse: [] },
      permissions: { allow: ["Bash(ls)"], defaultMode: "default" },
    });
    const next = JSON.parse(
      settingsWithPermissions(content, { allow: ["Bash(ls)", "WebFetch"], ask: [], deny: [] })
    );
    expect(next).toEqual({
      hooks: { PreToolUse: [] },
      permissions: { allow: ["Bash(ls)", "WebFetch"], defaultMode: "default" },
    });
  });

  test("a draft without settings gets a file with the lists", () => {
    const text = settingsWithPermissions(null, { allow: [], ask: [], deny: ["Bash(rm -rf *)"] });
    expect(text.endsWith("\n")).toBe(true);
    expect(JSON.parse(text)).toEqual({ permissions: { deny: ["Bash(rm -rf *)"] } });
  });

  test("settings that are not JSON are refused, not overwritten", () => {
    expect(() => settingsWithPermissions("{", { allow: [], ask: [], deny: [] })).toThrow(
      SyntaxError
    );
  });
});
