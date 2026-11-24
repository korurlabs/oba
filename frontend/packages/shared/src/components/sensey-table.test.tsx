import { describe, expect, test } from "bun:test";
import type { ColumnDef } from "@tanstack/react-table";
import { renderToStaticMarkup } from "react-dom/server";

import { SenseyTable } from "./sensey-table";

type Row = { id: string; title: string; subTickets?: Row[] };

const COLUMNS: ColumnDef<Row, unknown>[] = [
  { id: "title", accessorKey: "title", header: "Title", cell: ({ row }) => row.original.title },
];

const DATA: Row[] = [
  { id: "parent", title: "Parent ticket", subTickets: [{ id: "child", title: "Nested child" }] },
];

const TWO_CHILDREN: Row[] = [
  {
    id: "parent",
    title: "Parent ticket",
    subTickets: [
      { id: "kept", title: "Kept child" },
      { id: "hidden", title: "Hidden child" },
    ],
  },
];

const keepKept = (row: Row) => row.id === "kept";
const keepNone = () => false;

function markup(props: {
  expandAll?: boolean;
  selectableSubRows?: boolean;
  subRowFilter?: (row: Row) => boolean;
  data?: Row[];
}): string {
  return renderToStaticMarkup(
    <SenseyTable<Row>
      columns={COLUMNS}
      hierarchyKey="subTickets"
      getRowId={(row) => row.id}
      enableSelection
      virtualized={false}
      {...props}
      data={props.data ?? DATA}
    />
  );
}

function count(html: string, text: string): number {
  return html.split(text).length - 1;
}

describe("SenseyTable hierarchy", () => {
  test("hides child rows until a row is expanded", () => {
    const html = markup({});

    expect(html).toContain("Parent ticket");
    expect(html).not.toContain("Nested child");
  });

  test("expandAll shows every child row", () => {
    const html = markup({ expandAll: true });

    expect(html).toContain("Parent ticket");
    expect(html).toContain("Nested child");
  });

  test("subRowFilter shows only the child rows it keeps", () => {
    const html = markup({ expandAll: true, subRowFilter: keepKept, data: TWO_CHILDREN });

    expect(html).toContain("Kept child");
    expect(html).not.toContain("Hidden child");
  });

  test("a row whose children are all filtered out lists none of them", () => {
    const html = markup({ expandAll: true, subRowFilter: keepNone, data: TWO_CHILDREN });

    expect(html).toContain("Parent ticket");
    expect(html).not.toContain("Kept child");
    expect(html).not.toContain("Hidden child");
  });

  test("child rows get no checkbox unless selectableSubRows is set", () => {
    expect(count(markup({ expandAll: true }), 'aria-label="Select row"')).toBe(1);
    expect(
      count(markup({ expandAll: true, selectableSubRows: true }), 'aria-label="Select row"')
    ).toBe(2);
  });
});
