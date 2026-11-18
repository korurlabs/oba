import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { DocumentBody } from "./document-body";
import { MarkdownPreview } from "./markdown-preview";
import { ViewToggle } from "./view-toggle";

const NOTE =
  "---\nname: core\nalwaysApply: true\n---\n# Core\n\nSee <script>alert(1)</script> and <b>bold</b>.\n";

function body(path: string, view: "edit" | "preview" | "split"): string {
  return renderToStaticMarkup(
    <DocumentBody path={path} text={NOTE} view={view} onChange={() => undefined} label="Text" />
  );
}

describe("ViewToggle", () => {
  test("offers Edit, Preview and Split with the chosen one pressed", () => {
    const html = renderToStaticMarkup(<ViewToggle view="split" onChange={() => undefined} />);
    expect(html).toContain('aria-label="View"');
    for (const label of ["Edit", "Preview", "Split"]) {
      expect(html).toContain(`aria-label="${label}"`);
    }
    const pressed = html.match(/<button[^>]*aria-checked="true"[^>]*>/g) ?? [];
    expect(pressed).toHaveLength(1);
    expect(pressed[0]).toContain('aria-label="Split"');
  });
});

describe("DocumentBody", () => {
  test("edit shows the editor and no preview", () => {
    const html = body("AGENTS.md", "edit");
    expect(html).toContain('aria-busy="true"');
    expect(html).not.toContain('aria-label="Preview"');
  });

  test("preview shows the rendered file and no editor", () => {
    const html = body("AGENTS.md", "preview");
    expect(html).toContain('aria-label="Preview"');
    expect(html).not.toContain('aria-busy="true"');
  });

  test("split shows the editor beside the preview", () => {
    const html = body("AGENTS.md", "split");
    expect(html).toContain('aria-label="Preview"');
    expect(html).toContain('aria-busy="true"');
  });

  test("a file that is not Markdown always shows as text", () => {
    for (const view of ["preview", "split"] as const) {
      const html = body(".claude/settings.json", view);
      expect(html).not.toContain('aria-label="Preview"');
      expect(html).toContain('aria-busy="true"');
    }
  });
});

describe("MarkdownPreview", () => {
  test("shows the frontmatter as a table of its fields", () => {
    const html = renderToStaticMarkup(<MarkdownPreview text={NOTE} />);
    expect(html).toContain('aria-label="Frontmatter"');
    expect(html).toContain("alwaysApply");
    expect(html).toContain("true");
  });

  test("HTML in the file stays text", () => {
    const html = renderToStaticMarkup(<MarkdownPreview text={NOTE} />);
    expect(html).not.toContain("<script");
    expect(html).not.toContain("<b>");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
  });

  test("a file with no body says so", () => {
    const html = renderToStaticMarkup(<MarkdownPreview text={"---\nname: x\n---\n"} />);
    expect(html).toContain("The file has no body.");
  });
});
