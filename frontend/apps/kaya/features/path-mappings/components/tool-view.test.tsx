import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { TOOL_MAPPINGS } from "../schemas/tool-mappings.fixture";
import { ToolViewBody } from "./tool-view";

const noop = () => undefined;

describe("ToolViewBody", () => {
  test("a failed load shows the error with a retry", () => {
    const html = renderToStaticMarkup(
      <ToolViewBody
        tool="CURSOR"
        mappings={undefined}
        loading={false}
        error={new Error("Network Error")}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    expect(html).toContain('role="alert"');
    expect(html).toContain("Could not load what Cursor reads");
    expect(html).toContain("Try again");
  });

  test("shows the loading outline while the tools load", () => {
    const html = renderToStaticMarkup(
      <ToolViewBody
        tool="CURSOR"
        mappings={undefined}
        loading
        error={null}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    expect(html).toContain('aria-busy="true"');
  });

  test("shows the picked tool's reads and gaps; the summary shows under the tab and no legend shows", () => {
    const html = renderToStaticMarkup(
      <ToolViewBody
        tool="CODEX"
        mappings={TOOL_MAPPINGS}
        loading={false}
        error={null}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    // The tab names the tool, so the view repeats no heading.
    expect(html).toContain('aria-label="What OpenAI Codex reads"');
    expect(html).not.toContain("<h2");
    expect(html).toContain("Reads AGENTS.md and the skills under .agents/skills");
    expect(html).toContain("Choose a repository or pack to see which of these paths");
    expect(html).not.toContain("<dl");
    expect(html).not.toContain("loaded at the start of every session");
    expect(html).toContain("~/.codex/config.toml");
    expect(html).not.toContain(".cursor/rules/");
  });

  test.each([
    ["a repository", "repository:r-1"],
    ["a pack", "pack:p-1"],
  ])("in %s scope the states are explained in the In scope info tip, not a legend", (_, scope) => {
    const html = renderToStaticMarkup(
      <ToolViewBody
        tool="CLAUDE_CODE"
        mappings={TOOL_MAPPINGS}
        loading={false}
        error={null}
        onRetry={noop}
        scope={scope}
        versionCount={1}
      />
    );
    expect(html).toContain('aria-label="What does In scope show?"');
    expect(html).not.toContain("<dl");
    expect(html).not.toContain("None in scope");
  });

  test("above 20 versions it says why the states are missing", () => {
    const html = renderToStaticMarkup(
      <ToolViewBody
        tool="CLAUDE_CODE"
        mappings={TOOL_MAPPINGS}
        loading={false}
        error={null}
        onRetry={noop}
        scope="repository:r-1"
        versionCount={21}
      />
    );
    expect(html).toContain("more than 20 versions");
    expect(html).not.toContain("Choose a repository");
  });

  test("a tool the answer lacks gets an empty state", () => {
    const html = renderToStaticMarkup(
      <ToolViewBody
        tool="GEMINI_CLI"
        mappings={TOOL_MAPPINGS.slice(0, 3)}
        loading={false}
        error={null}
        onRetry={noop}
        scope={null}
        versionCount={0}
      />
    );
    expect(html).toContain("Kaya has no path table for Gemini CLI");
  });
});
