import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { ColumnDef } from "@tanstack/react-table";
import type { OrganizationGroup } from "@oba/shared/features/organizations/schemas";

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

const { useGroupColumns } = await import("./use-group-columns");

const everyone: OrganizationGroup = {
  id: "g-everyone",
  name: "Everyone",
  description: null,
  type: "EVERYONE",
  memberCount: 7,
  apps: ["KAZAN", "KAYA"],
} as OrganizationGroup;

const custom: OrganizationGroup = {
  id: "g-dev",
  name: "Developers",
  description: "Builds things",
  type: "CUSTOM",
  memberCount: 3,
  apps: ["KAZAN"],
} as OrganizationGroup;

const handlers = {
  onShowMembers: () => undefined,
  onRename: () => undefined,
  onDelete: (groupId: string) => void groupId,
  isDeleting: false,
};

function columns(): ColumnDef<OrganizationGroup, unknown>[] {
  let result: ColumnDef<OrganizationGroup, unknown>[] | undefined;
  function Probe() {
    result = useGroupColumns(handlers);
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result!;
}

function renderCell(columnId: string, group: OrganizationGroup): string {
  const column = columns().find((candidate) => candidate.id === columnId)!;
  const Cell = column.cell as (context: { row: { original: OrganizationGroup } }) => ReactNode;
  return renderToStaticMarkup(<>{Cell({ row: { original: group } })}</>);
}

beforeEach(() => {
  confirmed.length = 0;
});

describe("useGroupColumns", () => {
  test("has the name, description, member count, apps and actions columns", () => {
    expect(columns().map((column) => column.id)).toEqual([
      "name",
      "description",
      "memberCount",
      "apps",
      "actions",
    ]);
  });

  test("the Everyone row carries the System badge, a custom row does not", () => {
    expect(renderCell("name", everyone)).toContain("groups.system");
    expect(renderCell("name", custom)).not.toContain("groups.system");
  });

  test("the Everyone row offers View members only: no Rename and no Delete", () => {
    const html = renderCell("actions", everyone);

    expect(html).toContain("groups.viewMembers");
    expect(html).not.toContain("groups.manageMembers");
    expect(html).not.toContain("groups.rename");
    expect(html).not.toContain("groups.delete");
  });

  test("a custom row offers Manage members, Rename and Delete", () => {
    const html = renderCell("actions", custom);

    expect(html).toContain("item:");
    expect(html).toContain("groups.manageMembers");
    expect(html).toContain("groups.rename");
    expect(html).toContain("groups.delete");
  });

  test("the delete confirmation names the group and the apps whose grants go with it", () => {
    const html = renderCell("actions", custom);

    expect(html).toContain(
      "groups.deleteConfirmDescription:{&quot;name&quot;:&quot;Developers&quot;}"
    );
    expect(html).toContain("groups.deleteConfirmGrants");
    expect(html).toContain("kazan.name");
  });

  test("a group without grants says so in the delete confirmation", () => {
    const html = renderCell("actions", { ...custom, apps: [] });

    expect(html).toContain("groups.deleteConfirmNoGrants");
    expect(html).not.toContain("groups.deleteConfirmGrants");
  });

  test("confirming the delete passes the group id on", () => {
    const deleted: string[] = [];
    handlers.onDelete = (groupId) => void deleted.push(groupId);
    renderCell("actions", custom);

    confirmed.at(-1)!();

    expect(deleted).toEqual(["g-dev"]);
  });

  test("shows the member count and an empty-apps label", () => {
    expect(renderCell("memberCount", everyone)).toContain(">7<");
    expect(renderCell("apps", { ...custom, apps: [] })).toContain("groups.noApps");
  });
});
