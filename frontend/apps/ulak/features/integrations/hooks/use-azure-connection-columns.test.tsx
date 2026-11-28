import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { ColumnDef } from "@tanstack/react-table";
import type { AzureConnection } from "../schemas";

// The row menu is a Radix portal that a static render leaves closed. The menu
// and the confirm dialog are replaced by inline elements, so the markup lists
// every entry the row offers; each entry's and the confirm's handlers are
// recorded. Dates are formatted by a marker.
const selected: Record<string, () => void> = {};
const confirmed: (() => void)[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualMenu = { ...(await import("@oba/ui/sky/dropdown-menu")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualButton = { ...(await import("@oba/ui/sky/button")) };
const actualDateFormat = { ...(await import("@oba/shared/i18n/use-date-format")) };

function textOf(children: ReactNode): string {
  const parts = Array.isArray(children) ? children : [children];
  return parts.filter((part) => typeof part === "string").join("");
}

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string, values?: Record<string, string>) =>
    values ? `${key}:${JSON.stringify(values)}` : key,
}));
mock.module("@oba/shared/i18n/use-date-format", () => ({
  ...actualDateFormat,
  useDateFormat: () => ({ formatDateTime: (value: string) => `at(${value})` }),
}));
mock.module("@oba/ui/sky/dropdown-menu", () => ({
  ...actualMenu,
  DropdownMenu: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuItem: ({ children, onSelect }: { children: ReactNode; onSelect?: () => void }) => {
    const label = textOf(children);
    if (onSelect) selected[label] = onSelect;
    return <div>{`item:${label}`}</div>;
  },
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
    return <span>{`confirm:${textOf(children)}`}</span>;
  },
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/i18n/use-date-format", () => actualDateFormat);
  mock.module("@oba/ui/sky/dropdown-menu", () => actualMenu);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("@oba/ui/sky/button", () => actualButton);
});

const { useAzureConnectionColumns } = await import("./use-azure-connection-columns");

const connection: AzureConnection = {
  id: "c-1",
  name: "Production",
  tenantId: "11111111-1111-4111-8111-111111111111",
  clientId: "22222222-2222-4222-8222-222222222222",
  status: "FAILING",
  lastVerifiedAt: "2026-11-24T10:15:30Z",
  lastError: "AZR_UPS_009:AADSTS700213",
  createdAt: "2026-11-20T09:00:00Z",
  subscriptions: [],
  setup: {
    issuer: "https://oba.example",
    subject: "oba:organization:o-1:connection:c-1",
    audience: "api://AzureADTokenExchange",
  },
};

const calls: string[] = [];
const handlers = {
  onOpen: (value: AzureConnection) => void calls.push(`open:${value.id}`),
  onEdit: (value: AzureConnection) => void calls.push(`edit:${value.id}`),
  onVerify: (connectionId: string) => void calls.push(`verify:${connectionId}`),
  onDelete: (connectionId: string) => void calls.push(`delete:${connectionId}`),
  isVerifying: false,
  isDeleting: false,
};

function columns(): ColumnDef<AzureConnection, unknown>[] {
  let result: ColumnDef<AzureConnection, unknown>[] | undefined;
  function Probe() {
    result = useAzureConnectionColumns(handlers);
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result!;
}

function renderCell(columnId: string, value: AzureConnection): string {
  const column = columns().find((candidate) => candidate.id === columnId)!;
  const Cell = column.cell as (context: { row: { original: AzureConnection } }) => ReactNode;
  return renderToStaticMarkup(<>{Cell({ row: { original: value } })}</>);
}

beforeEach(() => {
  calls.length = 0;
  confirmed.length = 0;
  for (const key of Object.keys(selected)) delete selected[key];
});

describe("useAzureConnectionColumns", () => {
  test("has the name, tenant id, status, last check, last error and actions columns", () => {
    expect(columns().map((column) => column.id)).toEqual([
      "name",
      "tenantId",
      "status",
      "lastVerifiedAt",
      "lastError",
      "actions",
    ]);
  });

  test("shows the name, the tenant id and the status as text", () => {
    expect(renderCell("name", connection)).toContain("Production");
    expect(renderCell("tenantId", connection)).toContain(connection.tenantId);
    expect(renderCell("status", connection)).toContain("statuses.FAILING");
    expect(renderCell("status", { ...connection, status: "CONNECTED" })).toContain(
      "statuses.CONNECTED"
    );
  });

  test("shows the last check as a formatted date, or says it never ran", () => {
    expect(renderCell("lastVerifiedAt", connection)).toContain("at(2026-11-24T10:15:30Z)");
    expect(renderCell("lastVerifiedAt", { ...connection, lastVerifiedAt: null })).toContain(
      "neverVerified"
    );
  });

  test("shows a known last error by its text with Entra's code, never a raw message", () => {
    const html = renderCell("lastError", connection);
    expect(html).toContain("lastErrors.AZR_UPS_009");
    expect(html).toContain("lastErrors.entraCode");
    expect(html).toContain("AADSTS700213");
  });

  test("names an unknown last error by its code and shows none when there is no error", () => {
    expect(renderCell("lastError", { ...connection, lastError: "AZR_XXX_001" })).toContain(
      "AZR_XXX_001"
    );
    expect(renderCell("lastError", { ...connection, lastError: null })).toContain("noError");
  });

  test("the menu offers open, edit, verify and delete", () => {
    const html = renderCell("actions", connection);
    for (const entry of ["actions.open", "actions.edit", "actions.verify", "actions.delete"]) {
      expect(html).toContain(`item:${entry}`);
    }
  });

  test("the menu entries pass the connection on", () => {
    renderCell("actions", connection);
    selected["actions.open"]!();
    selected["actions.edit"]!();
    selected["actions.verify"]!();

    expect(calls).toEqual(["open:c-1", "edit:c-1", "verify:c-1"]);
  });

  test("the delete confirmation names the connection and passes its id on", () => {
    const html = renderCell("actions", connection);
    expect(html).toContain("deleteConfirmDescription:{&quot;name&quot;:&quot;Production&quot;}");

    confirmed.at(-1)!();

    expect(calls).toEqual(["delete:c-1"]);
  });
});
