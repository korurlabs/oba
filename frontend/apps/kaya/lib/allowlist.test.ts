import { describe, expect, test } from "bun:test";

import {
  EVERY_COMMAND,
  filterRules,
  groupRules,
  programOf,
  ruleText,
  sourceLabel,
} from "@/lib/allowlist";
import type { AllowRule, PermissionEffect } from "@/types/run";

/** A rule as the recorder writes it, from its settings text such as `Bash(git push *)`. */
function rule(text: string, effect: PermissionEffect = "allow", source = "project"): AllowRule {
  const found = /^([^()\s]+)\((.*)\)$/.exec(text);
  const tool = found?.[1] ?? text;
  const pattern = found?.[2] ?? null;
  return {
    tool,
    pattern,
    program: tool === "Bash" && pattern ? (pattern.split(/[\s:*]/)[0] ?? null) : null,
    effect,
    source,
    file: source === "user" ? "/home/me/.claude/settings.json" : "/repo/.claude/settings.json",
    line: 4,
  };
}

describe("groupRules", () => {
  test("orders tools, gathers MCP servers and splits Bash per program past assignments", () => {
    const rules = [
      rule("WebFetch(domain:github.com)"),
      rule("mcp__github__get_issue"),
      rule("Bash(JAVA_HOME=/usr/lib/jvm/java-25 mvn test *)", "allow", "user"),
      rule("Bash(mvn clean package *)"),
      rule("Bash(git push *)", "deny"),
      rule("Bash(git status)"),
      rule("Bash"),
      rule("Read(//usr/**)"),
      rule("Glob"),
      rule("Agent(Explore)"),
    ];
    const sections = groupRules(rules);
    expect(sections.map((section) => section.tool)).toEqual([
      "Bash",
      "Read",
      "WebFetch",
      "MCP",
      "Agent",
      "Glob",
    ]);
    const bash = sections[0];
    expect(bash?.count).toBe(5);
    expect(bash?.cards.map((card) => card.title)).toEqual([EVERY_COMMAND, "git", "mvn"]);
    expect(bash?.cards[1]?.rules.map(ruleText)).toEqual(["Bash(git push *)", "Bash(git status)"]);
    expect(bash?.cards[2]?.rules).toHaveLength(2);
    expect(sections[3]?.cards.map((card) => card.title)).toEqual(["github"]);
  });

  test("programOf skips wrappers and directories", () => {
    expect(programOf(rule("Bash(timeout 30 /usr/bin/curl -s *)"))).toBe("curl");
    expect(programOf(rule("Bash(oba:*)"))).toBe("oba");
    expect(programOf(rule("Bash(*)"))).toBeNull();
    expect(programOf(rule("Bash(./scripts/*)"))).toBe("./scripts/*");
    expect(programOf(rule("Read(//usr/**)"))).toBeNull();
  });
});

describe("filterRules and labels", () => {
  const rules = [
    rule("Bash(git push *)", "allow", "user"),
    rule("Bash(git fetch *)", "allow", "project"),
    rule("Bash(bun run lint)", "allow", "local"),
  ];

  test("filters by source kind and by every word of the text", () => {
    expect(filterRules(rules, { source: "project", text: "" }).map(ruleText)).toEqual([
      "Bash(git fetch *)",
    ]);
    expect(filterRules(rules, { source: null, text: "GIT push" }).map(ruleText)).toEqual([
      "Bash(git push *)",
    ]);
    expect(filterRules(rules, { source: null, text: "project settings" })).toHaveLength(1);
  });

  test("labels sources", () => {
    expect(sourceLabel("user")).toBe("User settings");
    expect(sourceLabel("local")).toBe("Local settings");
    expect(sourceLabel("unknown")).toBe("unknown");
  });
});
