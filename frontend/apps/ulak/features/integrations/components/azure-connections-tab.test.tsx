import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import type { AzureConnection } from "../schemas";

// The tab reads the list through a query hook. The hooks, the table, the
// dialog and the sheet are replaced: the table prints its rows and the column
// options are recorded, so the test can run a verify and a delete.
type ListResult = {
  data?: { data: AzureConnection[] };
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
};
type ColumnOptions = {
  onVerify: (connectionId: string) => void;
  onDelete: (connectionId: string) => void;
};

let list: ListResult;
let columnOptions: ColumnOptions;
const verified: string[] = [];
const deleted: string[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualHooks = { ...(await import("../hooks/use-azure-connections")) };
const actualColumns = { ...(await import("../hooks/use-azure-connection-columns")) };
const actualTable = { ...(await import("@/components/data-table")) };
const actualDialog = { ...(await import("./azure-connection-form-dialog")) };
const actualSheet = { ...(await import("./azure-connection-sheet")) };
const actualLoadError = {
  ...(await import("@/features/organizations/components/organization-load-error")),
};

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("../hooks/use-azure-connections", () => ({
  ...actualHooks,
  useAzureConnections: () => list,
  useVerifyAzureConnection: () => ({
    isPending: false,
    mutate: (connectionId: string) => void verified.push(connectionId),
  }),
  useDeleteAzureConnection: () => ({
    isPending: false,
    mutate: (connectionId: string) => void deleted.push(connectionId),
  }),
}));
mock.module("../hooks/use-azure-connection-columns", () => ({
  ...actualColumns,
  useAzureConnectionColumns: (options: ColumnOptions) => {
    columnOptions = options;
    return [];
  },
}));
mock.module("@/components/data-table", () => ({
  ...actualTable,
  DataTable: ({
    data,
    emptyMessage,
  }: {
    data: AzureConnection[];
    emptyMessage: React.ReactNode;
  }) =>
    data.length === 0 ? (
      <>{emptyMessage}</>
    ) : (
      <p>{`rows:${data.map((row) => row.name).join(",")}`}</p>
    ),
}));
mock.module("./azure-connection-form-dialog", () => ({
  ...actualDialog,
  AzureConnectionFormDialog: () => <p>form-dialog</p>,
}));
mock.module("./azure-connection-sheet", () => ({
  ...actualSheet,
  AzureConnectionSheet: () => <p>sheet</p>,
}));
mock.module("@/features/organizations/components/organization-load-error", () => ({
  ...actualLoadError,
  OrganizationLoadError: () => <p>load-error</p>,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("../hooks/use-azure-connections", () => actualHooks);
  mock.module("../hooks/use-azure-connection-columns", () => actualColumns);
  mock.module("@/components/data-table", () => actualTable);
  mock.module("./azure-connection-form-dialog", () => actualDialog);
  mock.module("./azure-connection-sheet", () => actualSheet);
  mock.module("@/features/organizations/components/organization-load-error", () => actualLoadError);
});

const { AzureConnectionsTab } = await import("./azure-connections-tab");

function connection(overrides: Partial<AzureConnection> = {}): AzureConnection {
  return {
    id: "c0a80101-0000-4000-8000-000000000001",
    name: "Production tenant",
    tenantId: "8f0e2c1a-4b5d-4e6f-9a7b-1c2d3e4f5a6b",
    clientId: null,
    status: "PENDING",
    lastVerifiedAt: null,
    lastError: null,
    createdAt: "2026-11-20T09:00:00Z",
    subscriptions: [],
    setup: {
      issuer: "https://api.example.com",
      subject: "oba:organization:org-1:connection:c0a80101-0000-4000-8000-000000000001",
      audience: "api://AzureADTokenExchange",
    },
    ...overrides,
  };
}

function render(): string {
  return renderToStaticMarkup(<AzureConnectionsTab organizationId="org-1" />);
}

beforeEach(() => {
  list = {
    data: { data: [connection()] },
    isLoading: false,
    isError: false,
    refetch: () => undefined,
  };
  verified.length = 0;
  deleted.length = 0;
});

describe("AzureConnectionsTab", () => {
  test("lists the connections with the New button, the dialog and the sheet", () => {
    const html = render();

    expect(html).toContain("rows:Production tenant");
    expect(html).toContain("newConnection");
    expect(html).toContain("form-dialog");
    expect(html).toContain("sheet");
  });

  test("an empty list shows the empty state", () => {
    list = { ...list, data: { data: [] } };

    const html = render();

    expect(html).toContain("emptyTitle");
    expect(html).toContain("emptyDescription");
  });

  test("a failed first load shows the retry error in place of the table", () => {
    list = { isLoading: false, isError: true, refetch: () => undefined };

    const html = render();

    expect(html).toContain("load-error");
    expect(html).not.toContain("rows:");
  });

  test("a failed refetch keeps the cached rows", () => {
    list = { ...list, isError: true };

    expect(render()).toContain("rows:Production tenant");
  });

  test("a connection without an issuer says connections are not available here", () => {
    list = {
      ...list,
      data: { data: [connection({ setup: { ...connection().setup, issuer: null } })] },
    };

    expect(render()).toContain("unavailableTitle");
  });

  test("connections with an issuer show no unavailable notice", () => {
    expect(render()).not.toContain("unavailableTitle");
  });

  test("the row actions run verify and delete with the connection id", () => {
    render();

    columnOptions.onVerify("c-1");
    columnOptions.onDelete("c-2");

    expect(verified).toEqual(["c-1"]);
    expect(deleted).toEqual(["c-2"]);
  });
});
