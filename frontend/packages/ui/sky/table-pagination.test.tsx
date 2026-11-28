import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { TablePagination } from "./table-pagination";

function markup(page: number, totalPages: number): string {
  return renderToStaticMarkup(
    <TablePagination
      page={page}
      totalPages={totalPages}
      onPageChange={() => undefined}
      label="Campaign pages"
      previousLabel="Previous"
      nextLabel="Next"
      summary={`Page ${page} of ${totalPages}`}
    />
  );
}

function buttons(html: string): string[] {
  return html.match(/<button[^>]*>/g) ?? [];
}

describe("TablePagination", () => {
  test("is a named navigation with back, the summary and forward in that order", () => {
    const html = markup(2, 5);
    expect(html).toContain('<nav data-slot="table-pagination" aria-label="Campaign pages"');
    const order = ["Previous", "Page 2 of 5", "Next"].map((part) => html.indexOf(part));
    expect([...order].sort((a, b) => a - b)).toEqual(order);
    for (const button of buttons(html)) expect(button).not.toContain('disabled=""');
  });

  test("disables back on the first page", () => {
    const [previous, next] = buttons(markup(1, 5));
    expect(previous).toContain('disabled=""');
    expect(next).not.toContain('disabled=""');
  });

  test("disables forward on the last page", () => {
    const [previous, next] = buttons(markup(5, 5));
    expect(previous).not.toContain('disabled=""');
    expect(next).toContain('disabled=""');
  });
});
