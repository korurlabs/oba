import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { TOOL_MAPPINGS } from "../schemas/tool-mappings.fixture";
import { ToolNotDelivered } from "./tool-not-delivered";

describe("ToolNotDelivered", () => {
  test("lists each path the tool reads that Kaya does not deliver, with its note", () => {
    const cursor = TOOL_MAPPINGS[2]!;
    const html = renderToStaticMarkup(
      <ToolNotDelivered toolName={cursor.name} gaps={cursor.notDelivered} />
    );
    expect(html).toContain("Not delivered by Kaya");
    expect(html).toContain('aria-label="What is not delivered by Kaya?"');
    expect(html).not.toContain("Kaya does not deliver them");
    expect(html).toContain(">.cursor/rules/</code>");
    expect(html).toContain("Cursor&#x27;s own rule files");
    expect(html.match(/<li /g)).toHaveLength(2);
  });

  test("says so when Kaya delivers everything the tool reads", () => {
    const html = renderToStaticMarkup(<ToolNotDelivered toolName="Cursor" gaps={[]} />);
    expect(html).toContain("Kaya delivers every path Cursor reads.");
    expect(html).not.toContain("<ul");
  });
});
