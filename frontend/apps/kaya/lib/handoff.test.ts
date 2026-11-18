import { describe, expect, test } from "bun:test";

import { readFixture } from "@/fixtures/load";
import { HANDOFF_SECTIONS, handoffSection, parseHandoff } from "@/lib/handoff";

describe("parseHandoff", () => {
  test("reads the template's title, status and every section in order", () => {
    const handoff = parseHandoff(readFixture("handoff-template.md"));
    expect(handoff.title).toBe("{feature} / {layer} / {subtask}");
    expect(handoff.status).toBe("PARTIAL");
    expect(handoff.sections.map((section) => section.heading)).toEqual([...HANDOFF_SECTIONS]);
    expect(handoffSection(handoff, "Next step")).toStartWith("What remains");
    expect(handoffSection(handoff, "Not a section")).toBeNull();
  });

  test("reads a written handoff's status", () => {
    const handoff = parseHandoff(
      readFixture("runs/validate-blocked/handoffs/board-export/validation.md")
    );
    expect(handoff.status).toBe("BLOCKED");
    expect(handoffSection(handoff, "Open questions and items")).toContain("Docker is not running");
  });

  test("a heading or status line inside a code fence is text", () => {
    const handoff = parseHandoff(
      [
        "# Handoff: a / b",
        "",
        "**Status:** DONE",
        "",
        "## Done so far",
        "",
        "```md",
        "## Not a heading",
        "**Status:** BLOCKED",
        "```",
        "",
        "## Decisions",
        "",
        "None.",
      ].join("\n")
    );
    expect(handoff.status).toBe("DONE");
    expect(handoff.sections.map((section) => section.heading)).toEqual([
      "Done so far",
      "Decisions",
    ]);
    expect(handoffSection(handoff, "Done so far")).toContain("## Not a heading");
  });

  test("a file without a status line, or with an unknown value, has no status", () => {
    expect(parseHandoff("# Handoff: x\n\n## Scope given\n\nNone.").status).toBeNull();
    expect(parseHandoff("# Handoff: x\n\n**Status:** MAYBE\n").status).toBeNull();
  });
});
