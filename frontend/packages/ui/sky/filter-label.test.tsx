import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { FilterLabel } from "./filter-label";

describe("FilterLabel", () => {
  test("puts the label above its control and ties it to the control's id", () => {
    const html = renderToStaticMarkup(
      <FilterLabel label="Owner" htmlFor="owner" className="w-48">
        <select id="owner" />
      </FilterLabel>
    );
    expect(html).toContain('<label for="owner" class="text-muted-foreground">Owner</label>');
    expect(html.indexOf("<label")).toBeLessThan(html.indexOf("<select"));
    expect(html).toContain("w-48");
  });
});
