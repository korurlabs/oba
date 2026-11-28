import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { BY_KIND, serializeToolView, toolFromTab, ToolTabs } from "./tool-tabs";

const noop = () => undefined;

describe("tool tabs", () => {
  test("picking a tool writes it to `tool`, and By kind removes it", () => {
    expect(serializeToolView("?scope=pack-1", { tool: "CURSOR" })).toBe(
      "?scope=pack-1&tool=CURSOR"
    );
    expect(serializeToolView("?scope=pack-1&tool=CURSOR", { tool: null })).toBe("?scope=pack-1");
  });

  test("a tab value is a tool, By kind, or no change", () => {
    expect(toolFromTab("GEMINI_CLI")).toBe("GEMINI_CLI");
    expect(toolFromTab(BY_KIND)).toBeNull();
    expect(toolFromTab("")).toBeUndefined();
    expect(toolFromTab("COPILOT")).toBeUndefined();
  });

  test("offers By kind and the four tools as tabs, with the current one selected", () => {
    const html = renderToStaticMarkup(<ToolTabs tool="CODEX" onChange={noop} />);
    expect(html).toContain('aria-label="View"');
    for (const label of ["By kind", "Claude Code", "OpenAI Codex", "Cursor", "Gemini CLI"]) {
      expect(html).toContain(`>${label}</button>`);
    }
    expect(html).toMatch(/aria-selected="true"[^>]*>OpenAI Codex</);
  });

  test("without a tool By kind is selected", () => {
    const html = renderToStaticMarkup(<ToolTabs tool={null} onChange={noop} />);
    expect(html).toMatch(/aria-selected="true"[^>]*>By kind</);
  });
});
