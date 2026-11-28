import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { ListToolbar } from "./list-toolbar";

describe("ListToolbar", () => {
  test("lays out tabs and search on the left, then actions, filter and create", () => {
    const html = renderToStaticMarkup(
      <ListToolbar
        tabs={<span>TABS</span>}
        search={<span>SEARCH</span>}
        actions={<span>ACTIONS</span>}
        filters={<span>FILTER</span>}
        create={<span>CREATE</span>}
      />
    );
    const order = ["TABS", "SEARCH", 'class="flex-1"', "ACTIONS", "FILTER", "CREATE"].map((part) =>
      html.indexOf(part)
    );
    for (const index of order) expect(index).toBeGreaterThan(-1);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
  });

  test("keeps the right group on the right without a search", () => {
    const html = renderToStaticMarkup(<ListToolbar create={<span>CREATE</span>} />);
    expect(html).toContain('class="flex-1"');
    expect(html.indexOf('class="flex-1"')).toBeLessThan(html.indexOf("CREATE"));
    expect(html).not.toContain("min-w-0");
  });

  test("uses Kazan's row spacing and accepts extra classes", () => {
    const html = renderToStaticMarkup(<ListToolbar className="mb-2" />);
    expect(html).toContain('data-slot="list-toolbar"');
    expect(html).toContain("flex-wrap items-center gap-2 lg:gap-3");
    expect(html).toContain("mb-2");
  });
});
