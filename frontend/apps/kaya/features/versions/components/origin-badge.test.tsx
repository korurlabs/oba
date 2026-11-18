import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { FileOrigin } from "@/features/scope/components/scope-files";
import { OriginBadge } from "./file-viewer";

function render(origin: FileOrigin): string {
  return renderToStaticMarkup(<OriginBadge origin={origin} />);
}

describe("OriginBadge", () => {
  test("an override shows its badge and the path it replaces", () => {
    const html = render({
      kind: "OVERRIDES",
      label: "Overrides .agents/rules/11-backend.md",
      target: ".agents/rules/11-backend.md",
    });
    expect(html).toContain("Overrides");
    expect(html).toContain("<code");
    expect(html).toContain(".agents/rules/11-backend.md");
  });

  test("a narrowed rule shows its label", () => {
    expect(render({ kind: "NARROWED", label: "Narrowed for kagi" })).toContain("Narrowed for kagi");
  });
});
