import { describe, expect, test } from "bun:test";

import { isParsableRule, settingsQueryRequestSchema, settingsResponseSchema } from ".";

describe("isParsableRule", () => {
  test("reads a tool name with or without an argument, as the server does", () => {
    for (const rule of [
      "Bash(curl:*)",
      "WebFetch",
      "WebFetch(domain:example.com)",
      "mcp__github",
    ]) {
      expect(isParsableRule(rule)).toBe(true);
    }
  });

  test("refuses lower-case tools, empty arguments, surrounding spaces and control characters", () => {
    for (const rule of [
      "bash(curl)",
      "Bash()",
      " Bash(curl:*)",
      "Bash(curl:*) ",
      "Bash(a\tb)",
      "",
    ]) {
      expect(isParsableRule(rule)).toBe(false);
    }
  });
});

describe("settings", () => {
  const SETTINGS = {
    id: "st-1",
    versionId: "v-1",
    sizeBytes: 120,
    allow: ["Bash(git status)"],
    ask: [],
    deny: [],
    hooks: [{ event: "PreToolUse", matcher: null, type: "command", command: "x.sh" }],
  };

  test("reads the permission lists and hooks of a version's settings file", () => {
    expect(settingsResponseSchema.safeParse(SETTINGS).success).toBe(true);
    expect(settingsResponseSchema.safeParse({ ...SETTINGS, deny: undefined }).success).toBe(false);
    expect(settingsResponseSchema.safeParse({ ...SETTINGS, hooks: [{}] }).success).toBe(false);
  });

  test("a query names one to 20 versions", () => {
    expect(settingsQueryRequestSchema.safeParse({ versionId: ["v-1"] }).success).toBe(true);
    expect(settingsQueryRequestSchema.safeParse({ versionId: [] }).success).toBe(false);
  });
});
