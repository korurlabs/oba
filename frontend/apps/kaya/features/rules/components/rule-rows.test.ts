import { describe, expect, test } from "bun:test";

import type { ScopeFile } from "@/features/scope/components/scope-files";

import type { RuleTabRow } from "./rule-rows";
import {
  filterRuleRows,
  placementOf,
  ROW_FIELD_LABELS,
  rowField,
  ruleTabRows,
  tabVersionIds,
} from "./rule-rows";

function file(path: string, versionId = "v-1"): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId,
    ownerId: "o-1",
  };
}

const RULE = {
  id: "r-1",
  versionId: "v-1",
  path: "00-core.md",
  description: "Core rules",
  paths: [],
  alwaysOn: true,
  conventions: [],
  sizeBytes: 1,
};

describe("Rules tab rows", () => {
  test("a delivered rule takes the description and scope of its version's row", () => {
    const files = [file(".agents/rules/00-core.md"), file(".agents/rules/10-java.md")];
    const rows = ruleTabRows(files, "rules", {
      tab: "rules",
      rows: [
        RULE,
        {
          ...RULE,
          id: "r-2",
          path: "10-java.md",
          description: null,
          paths: ["**/*.java"],
          alwaysOn: false,
          conventions: [{ conventionPath: "docs/private/conventions/backend/a.md" }],
        },
      ],
    });
    expect(rows.map((row) => row.summary)).toEqual([
      {
        title: "Core rules",
        details: [],
        alwaysOn: true,
        sizeBytes: 1,
        paths: [],
        description: null,
      },
      {
        title: null,
        details: ["Applies to **/*.java", "Links 1 convention doc"],
        alwaysOn: false,
        sizeBytes: 1,
        paths: ["**/*.java"],
        description: null,
      },
    ]);
  });

  test("a row of another version, such as an overridden inherited rule, describes nothing", () => {
    const rows = ruleTabRows([file(".agents/rules/00-core.md", "v-2")], "rules", {
      tab: "rules",
      rows: [RULE],
    });
    expect(rows).toEqual([{ file: file(".agents/rules/00-core.md", "v-2"), summary: null }]);
  });

  test("a file the list does not name, or a list not loaded, keeps its path only", () => {
    const files = [file(".claude/agents/coder.md")];
    expect(ruleTabRows(files, "subagents", null)[0]?.summary).toBeNull();
    expect(ruleTabRows(files, "subagents", { tab: "subagents", rows: [] })[0]?.summary).toBeNull();
  });

  test("a list of another tab is not applied", () => {
    const rows = ruleTabRows([file(".agents/rules/00-core.md")], "rules", {
      tab: "roles",
      rows: [{ id: "o-1", versionId: "v-1", path: "00-core.md", title: "X", sizeBytes: 1 }],
    });
    expect(rows[0]?.summary).toBeNull();
  });

  test("AGENTS.md files match their row by placement", () => {
    expect(placementOf("AGENTS.md")).toBe("ROOT");
    expect(placementOf(".agents/AGENTS.md")).toBe("AGENTS_DIR");
    expect(placementOf("projects/kagi/AGENTS.md")).toBe("ROOT");
    const rows = ruleTabRows([file("AGENTS.md"), file(".agents/AGENTS.md")], "agents-md", {
      tab: "agents-md",
      rows: [{ id: "a-1", versionId: "v-1", placement: "AGENTS_DIR", sizeBytes: 1 }],
    });
    expect(rows.map((row) => [row.file.path, row.summary?.alwaysOn ?? null])).toEqual([
      ["AGENTS.md", null],
      [".agents/AGENTS.md", true],
    ]);
  });

  test("subagents and hooks show their typed facts", () => {
    const subagent = ruleTabRows([file(".claude/agents/coder.md")], "subagents", {
      tab: "subagents",
      rows: [
        {
          id: "s-1",
          versionId: "v-1",
          path: "coder.md",
          name: "coder",
          description: "Writes code",
          model: "opus",
          maxTurns: 50,
          tools: ["Bash", "Edit"],
          sizeBytes: 1,
        },
      ],
    });
    expect(subagent[0]?.summary).toEqual({
      title: "coder",
      details: ["Model opus", "At most 50 turns", "Tools: Bash, Edit"],
      alwaysOn: null,
      sizeBytes: 1,
      paths: [],
      description: "Writes code",
    });
    const hook = ruleTabRows([file(".agents/hooks/a.sh")], "hooks", {
      tab: "hooks",
      rows: [{ id: "h-1", versionId: "v-1", path: "a.sh", executable: false, sizeBytes: 1 }],
    });
    expect(hook[0]?.summary?.details).toEqual(["Not executable, so it never runs"]);
  });

  test("a workflow only a skill holds is skipped", () => {
    const rows = ruleTabRows([file(".agents/workflows/plan.md")], "workflows", {
      tab: "workflows",
      rows: [
        { id: "w-1", versionId: "v-1", path: null, title: "Skill workflow", sizeBytes: 1 },
        { id: "w-2", versionId: "v-1", path: "plan.md", title: "Plan", sizeBytes: 1 },
      ],
    });
    expect(rows[0]?.summary?.title).toBe("Plan");
  });

  test("a tab needs the versions of its own files, once each", () => {
    const files = [
      file(".agents/rules/a.md", "v-1"),
      file(".agents/rules/b.md", "v-1"),
      file(".agents/rules/c.md", "v-2"),
      file(".agents/roles/x.md", "v-3"),
    ];
    expect(tabVersionIds(files, "rules")).toEqual(["v-1", "v-2"]);
  });
});

function summary(overrides: Partial<NonNullable<RuleTabRow["summary"]>> = {}) {
  return {
    title: null,
    details: [],
    alwaysOn: null,
    sizeBytes: 2150,
    paths: [],
    description: null,
    ...overrides,
  };
}

describe("rowField", () => {
  const NONE: readonly string[] = [];

  test("an always-on rule reads Always on with its size", () => {
    const row: RuleTabRow = {
      file: file(".agents/rules/a.md"),
      summary: summary({ alwaysOn: true }),
    };
    expect(rowField("rules", row, NONE)).toBe("Always on, 2,150 bytes");
  });

  test("a scoped rule reads the paths it applies to", () => {
    const row: RuleTabRow = {
      file: file(".agents/rules/a.md"),
      summary: summary({ alwaysOn: false, paths: ["a/**", "b/**"] }),
    };
    expect(rowField("rules", row, NONE)).toBe("Applies to a/**, b/**");
  });

  test("a rule without a summary falls back to the file's flag and size", () => {
    const base = file(".agents/rules/a.md");
    expect(rowField("rules", { file: base, summary: null }, NONE)).toBeNull();
    expect(rowField("rules", { file: { ...base, size: 10 }, summary: null }, NONE)).toBe(
      "10 bytes"
    );
    expect(rowField("rules", { file: { ...base, alwaysOn: true }, summary: null }, NONE)).toBe(
      "Always on"
    );
    expect(
      rowField("rules", { file: { ...base, alwaysOn: true, size: 4 }, summary: null }, NONE)
    ).toBe("Always on, 4 bytes");
  });

  test("an AGENTS.md file is always on, with its size when known", () => {
    expect(rowField("agents-md", { file: file("AGENTS.md"), summary: summary() }, NONE)).toBe(
      "Always on, 2,150 bytes"
    );
    expect(rowField("agents-md", { file: file("AGENTS.md"), summary: null }, NONE)).toBe(
      "Always on"
    );
  });

  test("a subagent shows its description, or nothing before its list loads", () => {
    const base = file(".claude/agents/a.md");
    expect(
      rowField("subagents", { file: base, summary: summary({ description: "Writes code" }) }, NONE)
    ).toBe("Writes code");
    expect(rowField("subagents", { file: base, summary: null }, NONE)).toBeNull();
  });

  test("a hook shows its events, or Not registered", () => {
    const row: RuleTabRow = { file: file(".agents/hooks/a.sh"), summary: summary() };
    expect(rowField("hooks", row, ["PreToolUse", "Stop"])).toBe("PreToolUse, Stop");
    expect(rowField("hooks", row, NONE)).toBe("Not registered");
  });

  test("roles, workflows and templates show their size, nothing when unknown", () => {
    for (const tab of ["roles", "workflows", "templates"] as const) {
      const base = file(`.agents/${tab}/a.md`);
      expect(rowField(tab, { file: base, summary: summary() }, NONE)).toBe("2,150 bytes");
      expect(rowField(tab, { file: base, summary: null }, NONE)).toBeNull();
      expect(rowField(tab, { file: { ...base, size: 1 }, summary: null }, NONE)).toBe("1 bytes");
    }
  });

  test("every tab has a column header", () => {
    expect(ROW_FIELD_LABELS.hooks).toBe("Events");
    expect(Object.keys(ROW_FIELD_LABELS)).toHaveLength(7);
  });
});

describe("filterRuleRows", () => {
  const rows = (): RuleTabRow[] => [
    {
      file: file(".agents/rules/00-core.md"),
      summary: {
        title: "Core",
        description: "Shared by every repository",
      } as RuleTabRow["summary"],
    },
    { file: file(".agents/rules/10-java.md"), summary: null },
  ];

  test("an empty or blank search keeps every row", () => {
    expect(filterRuleRows(rows(), "")).toHaveLength(2);
    expect(filterRuleRows(rows(), "   ")).toHaveLength(2);
  });

  test("a term is found in the path, title or description, ignoring case", () => {
    expect(filterRuleRows(rows(), "JAVA").map((row) => row.file.path)).toEqual([
      ".agents/rules/10-java.md",
    ]);
    expect(filterRuleRows(rows(), "core")).toHaveLength(1);
    expect(filterRuleRows(rows(), "every repository")).toHaveLength(1);
  });

  test("every term must match, and no match leaves no row", () => {
    expect(filterRuleRows(rows(), "core java")).toHaveLength(0);
    expect(filterRuleRows(rows(), "nothing")).toHaveLength(0);
  });
});
