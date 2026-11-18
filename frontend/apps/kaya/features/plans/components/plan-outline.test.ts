import { readFileSync } from "node:fs";

import { describe, expect, test } from "bun:test";

import {
  parsePlanOutline,
  parseSessionOutline,
  parseSpecOutline,
  pathsIn,
  tableCells,
} from "./plan-outline";

// A copy of the Kaya plans spec, so the test reads a real spec with stories, units and
// assumptions. Prettier wraps its prose, so the parser is also held to wrapped lines.
const SPEC = readFileSync(`${import.meta.dir}/fixtures/spec-04-kaya-plans.md`, "utf8");

const CORE_PLAN = `I read the handoffs first.

1. **Goal**: The plan page shows the outline of a plan.
2. **Steps**:
   1. Add \`features/plans/components/plan-outline.ts\` with the parser.
      It reads bold labels and headings.
   2. Render it in \`plan-detail.tsx\`; run \`bun test\` after.
3. **Verification**: \`bun test\` passes.
4. **Risks**: A Stop message in this format becomes a session plan.
`;

describe("parseSessionOutline", () => {
  test("reads the core plan format with numbered labels", () => {
    const outline = parseSessionOutline(CORE_PLAN);
    expect(outline.goal).toBe("The plan page shows the outline of a plan.");
    expect(outline.steps).toEqual([
      {
        number: 1,
        text: "Add `features/plans/components/plan-outline.ts` with the parser.\nIt reads bold labels and headings.",
        paths: ["features/plans/components/plan-outline.ts"],
      },
      {
        number: 2,
        text: "Render it in `plan-detail.tsx`; run `bun test` after.",
        paths: ["plan-detail.tsx"],
      },
    ]);
    expect(outline.verification).toBe("`bun test` passes.");
    expect(outline.risks).toBe("A Stop message in this format becomes a session plan.");
  });

  test("reads bold labels with the colon inside and heading labels", () => {
    const outline = parseSessionOutline(
      [
        "**Goal:** Ship it.",
        "",
        "## Steps",
        "1. Edit `a/b.ts`.",
        "2. Edit `c/d.ts`.",
        "",
        "### Verification",
        "- `bun run build` succeeds.",
      ].join("\n")
    );
    expect(outline.goal).toBe("Ship it.");
    expect(outline.steps?.map((step) => step.paths)).toEqual([["a/b.ts"], ["c/d.ts"]]);
    expect(outline.verification).toBe("- `bun run build` succeeds.");
  });

  test("a missing section is null, so the page says it is not in the plan", () => {
    const outline = parseSessionOutline("**Goal**: Only a goal.\n**Steps**:\n1. One step.");
    expect(outline.verification).toBeNull();
    expect(outline.risks).toBeNull();
    expect(outline.steps).toHaveLength(1);
  });

  test("labels inside a code fence are text", () => {
    const outline = parseSessionOutline("**Goal**: G\n```\n**Risks**: none\n```\n");
    expect(outline.risks).toBeNull();
    expect(outline.goal).toContain("**Risks**: none");
  });

  test("steps without a list are one step", () => {
    const outline = parseSessionOutline("**Goal**: G\n**Steps**: edit `x/y.go` and test it.");
    expect(outline.steps).toEqual([
      { number: 1, text: "edit `x/y.go` and test it.", paths: ["x/y.go"] },
    ]);
  });
});

describe("pathsIn", () => {
  test("keeps code spans that name files and leaves commands and identifiers out", () => {
    expect(
      pathsIn(
        "`lib/a.ts`, `README.md`, `bun test`, `ReviewButtons`, `API_ROUTES.plans`, `lib/a.ts`"
      )
    ).toEqual(["lib/a.ts", "README.md"]);
  });
});

describe("parseSpecOutline on the plans spec", () => {
  const outline = parseSpecOutline(SPEC);

  test("takes the summary from section 1", () => {
    expect(outline.summary?.startsWith('"Plans" makes the plans Claude Code writes')).toBe(true);
  });

  test("reads the nine stories of section 2 with their acceptance checkboxes", () => {
    const stories = outline.stories ?? [];
    expect(stories.map((story) => story.id)).toEqual([
      "PL1",
      "PL2",
      "PL3",
      "PL4",
      "PL5",
      "PL6",
      "PL7",
      "PL8",
      "PL9",
    ]);
    expect(stories[0]?.text).toBe(
      "As a MEMBER, I want spec files a session writes to appear as plans, so that I can review them without opening the worktree."
    );
    expect(stories[0]?.criteria).toHaveLength(3);
    expect(stories[0]?.criteria[1]).toEqual({
      text: "The plan's identity is (repository, path): a second session that edits the same spec adds a version to the same plan; unchanged content (same SHA-256 as the latest version) adds none.",
      checked: false,
    });
    expect(stories[5]?.criteria).toHaveLength(6);
    expect(stories[8]?.criteria).toHaveLength(1);
  });

  test("reads the units table of section 7, not the code references table of section 3", () => {
    const units = outline.units;
    expect(units?.headers).toEqual([
      "#",
      "Role",
      "Scope",
      "Unit",
      "Files or modules",
      "Needs unit",
    ]);
    expect(units?.rows.map((row) => row.number)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    expect(units?.rows[8]?.cells.slice(0, 4)).toEqual([
      "9",
      "coding-engineer",
      "frontend",
      "Plan page (outline, document, changes, feedback, handoff panel), session panel",
    ]);
    expect(units?.notes).toBe(
      "Units 6, 7 and 8 can run in parallel once their backend unit is done."
    );
  });

  test("reads the six assumptions to confirm of section 10 and stops before the risks", () => {
    const assumptions = outline.assumptions ?? [];
    expect(assumptions).toHaveLength(6);
    expect(
      assumptions[0]?.startsWith("How feedback reaches Claude: as hook `additionalContext`")
    ).toBe(true);
    expect(assumptions[5]).toBe(
      "SessionStart calls Kaya for feedback even under `kaya launch`, which today keeps that hook offline (bounded at 2 s)."
    );
  });

  test("unwrapped stories, one line each, read the same way", () => {
    const stories = parseSpecOutline(
      [
        "## 2. User stories and acceptance criteria",
        "",
        "PL1. As a MEMBER, I want spec files as plans.",
        "- [ ] A write is captured.",
        "- [x] Unchanged content adds no version.",
        "",
        "PL2. As a MEMBER, I want in-session plans.",
        "- [ ] ExitPlanMode is captured.",
      ].join("\n")
    ).stories;
    expect(stories).toEqual([
      {
        id: "PL1",
        text: "As a MEMBER, I want spec files as plans.",
        criteria: [
          { text: "A write is captured.", checked: false },
          { text: "Unchanged content adds no version.", checked: true },
        ],
      },
      {
        id: "PL2",
        text: "As a MEMBER, I want in-session plans.",
        criteria: [{ text: "ExitPlanMode is captured.", checked: false }],
      },
    ]);
  });

  test("a spec without these sections gives null for each", () => {
    expect(parseSpecOutline("# Title\n\nSome text.")).toEqual({
      kind: "SPEC",
      summary: null,
      stories: null,
      units: null,
      assumptions: null,
    });
  });
});

describe("tableCells", () => {
  test("keeps an escaped pipe inside its cell", () => {
    expect(tableCells("| `Agent\\|Skill` | existing |")).toEqual(["`Agent|Skill`", "existing"]);
  });
});

describe("parsePlanOutline", () => {
  test("picks the reader by the plan's kind", () => {
    expect(parsePlanOutline("SESSION", CORE_PLAN).kind).toBe("SESSION");
    expect(parsePlanOutline("SPEC", SPEC).kind).toBe("SPEC");
  });
});
