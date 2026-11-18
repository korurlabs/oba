import { describe, expect, test } from "bun:test";

import { describeError } from "./error-text";
import { KayaApiError } from "./errors";

const text = (code: string, status: number, values?: Record<string, unknown>) =>
  describeError(new KayaApiError("server text", status, code, values));

describe("change-control refusals", () => {
  test("each of KAY_CFG_014 to KAY_CFG_020 gives its whole sentence, not the server's", () => {
    expect(text("KAY_CFG_014", 409)).toBe(
      "This change is no longer open; only an open change is edited, rebased, submitted or deleted."
    );
    expect(text("KAY_CFG_015", 409)).toBe(
      "Another change of this pack is already in review; it must be approved or rejected first."
    );
    expect(text("KAY_CFG_016", 409)).toBe(
      "Nothing would change: the files are the same as the version this is based on."
    );
    expect(text("KAY_CFG_017", 409)).toBe(
      "This change is no longer in review; it was approved or sent back to its proposer."
    );
    expect(text("KAY_CFG_018", 409)).toBe(
      "Only a released version, approved or superseded, can be restored."
    );
    expect(text("KAY_CFG_020", 409)).toBe(
      "This change was reviewed, so it is kept with its reviews and cannot be deleted."
    );
  });

  test("error values do not change the fixed texts", () => {
    expect(text("KAY_CFG_014", 409, { versionId: "v", status: "IN_REVIEW" })).toStartWith(
      "This change is no longer open"
    );
    expect(text("KAY_CFG_015", 409, { versionId: "v-2" })).toStartWith(
      "Another change of this pack is already in review"
    );
  });

  test("KAY_CFG_019 keeps the general text when the owners are missing, malformed or a single group", () => {
    const general = "A proposal changes the files of one pack only; these files come from several.";
    expect(text("KAY_CFG_019", 422)).toBe(general);
    expect(text("KAY_CFG_019", 422, { owners: "x" })).toBe(general);
    expect(text("KAY_CFG_019", 422, { owners: { a: ["AGENTS.md"] } })).toBe(general);
    expect(text("KAY_CFG_019", 422, { owners: { a: [], b: [] } })).toBe(general);
  });

  test("KAY_CFG_019 skips an owner whose paths are not a list and entries that are not paths", () => {
    expect(text("KAY_CFG_019", 422, { owners: { a: ["A.md", 7], b: "x", c: ["B.md"] } })).toEndWith(
      "Propose each group on its own: A.md; B.md."
    );
  });

  test("KAY_CFG_002 without error values asks for the rebase", () => {
    const base =
      "The pack has a newer version than this change is based on, so the change must be rebased first.";
    expect(text("KAY_CFG_002", 409)).toBe(base);
    expect(text("KAY_CFG_002", 409, { conflicts: "x" })).toBe(base);
    expect(text("KAY_CFG_002", 409, { conflicts: [{ draftChange: "ADDED" }, 3] })).toBe(base);
  });

  test("a rebase conflict names an unknown side as unchanged wording", () => {
    const line = text("KAY_CFG_002", 409, {
      conflicts: [{ path: "a.md", draftChange: "RENAMED", currentChange: null }],
    });
    expect(line).toContain(
      "a.md: your edits leave it as delivered, the current version changed it."
    );
  });

  test("KAY_CFG_004 without a reason keeps the general text", () => {
    expect(text("KAY_CFG_004", 403)).toBe("You may not review this change.");
    expect(text("KAY_CFG_004", 403, { reason: "OTHER" })).toBe("You may not review this change.");
  });

  test("a code the table does not know falls back to the server's message", () => {
    expect(text("KAY_CFG_099", 409)).toBe("server text");
  });
});

describe("refused files", () => {
  test("KAY_CFG_027 and KAY_CFG_028 name the refused path and the reason", () => {
    expect(text("KAY_CFG_027", 422, { path: ".claude/settings.json", reason: "Not JSON" })).toBe(
      "Kaya cannot read this file: its frontmatter, JSON or YAML is invalid, or a field is missing or of the wrong type. .claude/settings.json: Not JSON."
    );
    expect(text("KAY_CFG_028", 422)).toBe(
      "No file kind is delivered at this path, so Kaya does not take it."
    );
  });

  test("KAY_CFG_027 with violations names each broken delivery rule, not the parse headline", () => {
    expect(
      text("KAY_CFG_027", 422, {
        path: ".agents/hooks/run.sh",
        reason: "the file breaks a delivery rule",
        violations: [
          { path: ".agents/hooks/run.sh", rule: "HOOK_SYMLINK", message: "hook is a symlink" },
          { path: ".agents/skills/a/SKILL.md", rule: "FILE_TOO_LARGE", message: "File too large" },
        ],
      })
    ).toBe(
      "Kaya refused the configuration. .agents/hooks/run.sh: A hook must be a regular file, so that every change to the code it runs needs an owner's approval. .agents/skills/a/SKILL.md: File too large."
    );
  });

  test("KAY_CFG_027 with an empty violations list keeps the path and reason", () => {
    expect(text("KAY_CFG_027", 422, { path: "a.md", reason: "Bad YAML", violations: [] })).toBe(
      "Kaya cannot read this file: its frontmatter, JSON or YAML is invalid, or a field is missing or of the wrong type. a.md: Bad YAML."
    );
  });
});
