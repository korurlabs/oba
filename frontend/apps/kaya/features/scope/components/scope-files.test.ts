import { describe, expect, test } from "bun:test";

import type { SettingsHookResponse } from "@/features/allowlist/schemas";

import { hookEventsOf, ruleTabOf, type ScopeFile } from "./scope-files";

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "o-1",
  };
}

describe("ruleTabOf", () => {
  test("each folder gives its tab", () => {
    expect(ruleTabOf(".agents/rules/a.md")).toBe("rules");
    expect(ruleTabOf(".agents/roles/a.md")).toBe("roles");
    expect(ruleTabOf(".claude/agents/a.md")).toBe("subagents");
    expect(ruleTabOf(".agents/workflows/a.md")).toBe("workflows");
    expect(ruleTabOf(".agents/templates/a.md")).toBe("templates");
    expect(ruleTabOf(".agents/hooks/a.sh")).toBe("hooks");
  });

  test("a root, directory and mounted AGENTS.md give agents-md", () => {
    expect(ruleTabOf("AGENTS.md")).toBe("agents-md");
    expect(ruleTabOf(".agents/AGENTS.md")).toBe("agents-md");
    expect(ruleTabOf("projects/kagi/AGENTS.md")).toBe("agents-md");
  });

  test("a path no tab lists gives none", () => {
    expect(ruleTabOf("README.md")).toBeNull();
    expect(ruleTabOf(".agents/skills/a/SKILL.md")).toBeNull();
    expect(ruleTabOf(".claude/settings.json")).toBeNull();
  });
});

describe("hookEventsOf", () => {
  const SCRIPT = ".agents/hooks/notify.sh";
  const GUARD = ".agents/hooks/pre-bash-destructive-git.sh";
  const hook = (event: string, command: string | null): SettingsHookResponse => ({
    event,
    command,
  });

  test("a script a command names directly gives that command's event, once per event", () => {
    const hooks = [
      hook("Stop", `bash ${SCRIPT}`),
      hook("Stop", `bash ${SCRIPT} --again`),
      hook("Notification", `$CLAUDE_PROJECT_DIR/${SCRIPT}`),
      hook("Stop", "bash .agents/hooks/other.sh"),
    ];
    expect(hookEventsOf(file(SCRIPT), hooks)).toEqual(["Stop", "Notification"]);
  });

  test("a guard script runs through the dispatcher for PreToolUse", () => {
    const hooks = [hook("PreToolUse", "bash .agents/hooks/dispatch.sh")];
    expect(hookEventsOf(file(GUARD), hooks)).toEqual(["PreToolUse"]);
  });

  test("the dispatcher on another event, or a non-guard script, does not register", () => {
    const hooks = [
      hook("Stop", "bash .agents/hooks/dispatch.sh"),
      hook("PreToolUse", "bash .agents/hooks/dispatch.sh"),
    ];
    expect(hookEventsOf(file(GUARD), [hooks[0] as SettingsHookResponse])).toEqual([]);
    expect(hookEventsOf(file(SCRIPT), hooks)).toEqual([]);
  });

  test("an unregistered script, no hooks or a hook without a command give none", () => {
    expect(hookEventsOf(file(SCRIPT), [])).toEqual([]);
    expect(hookEventsOf(file(SCRIPT), [hook("Stop", null), hook("Stop", "")])).toEqual([]);
    expect(hookEventsOf(file(SCRIPT), [hook("Stop", "echo hi")])).toEqual([]);
  });
});
