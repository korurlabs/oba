import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { ColumnDef } from "@tanstack/react-table";
import type { LegalEntityListItem } from "../schemas";

// The row menu is a Radix portal that a static render leaves closed. The menu
// and the confirm dialog are replaced by inline elements, so the markup lists
// every entry the row offers; the confirm button's handler is recorded.
const confirmed: (() => void)[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualMenu = { ...(await import("@oba/ui/sky/dropdown-menu")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualButton = { ...(await import("@oba/ui/sky/button")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string, values?: Record<string, string>) =>
    values ? `${key}:${JSON.stringify(values)}` : key,
}));
mock.module("@oba/ui/sky/dropdown-menu", () => ({
  ...actualMenu,
  DropdownMenu: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuItem: ({ children }: { children: ReactNode }) => (
    <div>{`item:${children as string}`}</div>
  ),
}));
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
  DialogClose: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
// The destructive button is the confirm of the delete dialog.
mock.module("@oba/ui/sky/button", () => ({
  ...actualButton,
  Button: ({
    children,
    onClick,
    variant,
  }: {
    children: ReactNode;
    onClick?: () => void;
    variant?: string;
  }) => {
    if (variant !== "destructive") return <span>{children}</span>;
    if (onClick) confirmed.push(onClick);
    return <span>{`confirm:${children as string}`}</span>;
  },
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/ui/sky/dropdown-menu", () => actualMenu);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("@oba/ui/sky/button", () => actualButton);
});

const { useLegalEntityColumns } = await import("./use-legal-entity-columns");

const entity: LegalEntityListItem = {
  id: "le-1",
  shortName: "Korur NL",
  legalName: "Korur B.V.",
  country: "NLD",
  registrationNumber: null,
  updatedAt: "2026-11-21T09:00:00Z",
};

const handlers = {
  canEdit: true,
  onEdit: (row: LegalEntityListItem) => void row,
  onDelete: (legalEntityId: string) => void legalEntityId,
  isDeleting: false,
};

function columns(): ColumnDef<LegalEntityListItem, unknown>[] {
  let result: ColumnDef<LegalEntityListItem, unknown>[] | undefined;
  function Probe() {
    result = useLegalEntityColumns(handlers);
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result!;
}

function renderCell(columnId: string, row: LegalEntityListItem): string {
  const column = columns().find((candidate) => candidate.id === columnId)!;
  const Cell = column.cell as (context: { row: { original: LegalEntityListItem } }) => ReactNode;
  return renderToStaticMarkup(<>{Cell({ row: { original: row } })}</>);
}

beforeEach(() => {
  confirmed.length = 0;
  handlers.canEdit = true;
  handlers.onDelete = (legalEntityId: string) => void legalEntityId;
});

describe("useLegalEntityColumns", () => {
  test("an editor gets short name, legal name, country, registration number and actions", () => {
    expect(columns().map((column) => column.id)).toEqual([
      "shortName",
      "legalName",
      "country",
      "registrationNumber",
      "actions",
    ]);
  });

  test("a reader gets no actions column", () => {
    handlers.canEdit = false;

    expect(columns().map((column) => column.id)).not.toContain("actions");
  });

  test("a known country shows its name, another code shows as stored", () => {
    expect(renderCell("country", entity)).toContain("countries.NLD");
    expect(renderCell("country", { ...entity, country: "ESP" })).toContain("ESP");
  });

  test("a missing registration number shows a dash", () => {
    expect(renderCell("registrationNumber", entity)).toContain("-");
    expect(renderCell("registrationNumber", { ...entity, registrationNumber: "123" })).toContain(
      "123"
    );
  });

  test("the row menu offers Edit and Delete, and the confirmation names the short name", () => {
    const html = renderCell("actions", entity);

    expect(html).toContain("item:");
    expect(html).toContain("legalEntities.edit");
    expect(html).toContain("legalEntities.delete");
    expect(html).toContain(
      "legalEntities.deleteConfirmDescription:{&quot;name&quot;:&quot;Korur NL&quot;}"
    );
  });

  test("confirming the delete passes the id on", () => {
    const deleted: string[] = [];
    handlers.onDelete = (legalEntityId) => void deleted.push(legalEntityId);
    renderCell("actions", entity);

    confirmed.at(-1)!();

    expect(deleted).toEqual(["le-1"]);
  });
});
