import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { ReadPreview } from "./file-content";

const NOTE = "---\nname: forms\ndescription: How forms validate\npaths: src/**\n---\n# Forms\n";

describe("ReadPreview", () => {
  test("closes the frontmatter to one line and renders the body under it", () => {
    const html = renderToStaticMarkup(<ReadPreview text={NOTE} />);
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain("Frontmatter, 3 fields");
    expect(html).not.toContain('aria-label="Frontmatter"');
    expect(html).not.toContain("How forms validate");
    expect(html).toContain(">Forms</p>");
  });

  test("a file without frontmatter shows only its body", () => {
    const html = renderToStaticMarkup(<ReadPreview text={"# Title\n"} />);
    expect(html).not.toContain("Frontmatter");
  });
});
