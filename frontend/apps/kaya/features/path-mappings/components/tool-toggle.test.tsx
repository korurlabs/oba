import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { BY_KIND, serializeToolView, toolFromToggle, ToolToggle } from "./tool-toggle";

const noop = () => undefined;

describe("tool toggle", () => {
  test("picking a tool writes it to `tool`, and By kind removes it", () => {
    expect(serializeToolView("?scope=pack-1", { tool: "CURSOR" })).toBe(
      "?scope=pack-1&tool=CURSOR"
    );
    expect(serializeToolView("?scope=pack-1&tool=CURSOR", { tool: null })).toBe("?scope=pack-1");
  });

  test("a toggle value is a tool, By kind, or no change", () => {
    expect(toolFromToggle("GEMINI_CLI")).toBe("GEMINI_CLI");
    expect(toolFromToggle(BY_KIND)).toBeNull();
    // A single toggle group sends "" when its pressed item is pressed again.
    expect(toolFromToggle("")).toBeUndefined();
    expect(toolFromToggle("COPILOT")).toBeUndefined();
  });

  test("offers By kind and the four tools under a visible label, with the current one pressed", () => {
    const html = renderToStaticMarkup(<ToolToggle tool="CODEX" onChange={noop} />);
    const labelId = /<span id="([^"]+)"[^>]*>View<\/span>/.exec(html)?.[1];
    expect(labelId).toBeDefined();
    expect(html).toContain(`aria-labelledby="${labelId}"`);
    for (const label of ["By kind", "Claude Code", "OpenAI Codex", "Cursor", "Gemini CLI"]) {
      expect(html).toContain(`>${label}</button>`);
    }
    expect(html).toMatch(/aria-checked="true"[^>]*>OpenAI Codex</);
  });

  test("without a tool By kind is pressed", () => {
    const html = renderToStaticMarkup(<ToolToggle tool={null} onChange={noop} />);
    expect(html).toMatch(/aria-checked="true"[^>]*>By kind</);
  });
});
