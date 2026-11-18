import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The list shows the organizations the current user manages. The query hook,
// the columns and the table are replaced; the table prints the names
// of the rows it received, so the test reads what the filter handed down.
type Membership = { id: string; name: string; role: string; organizationStatus: string };
type MeResult = {
  data?: { success: boolean; data?: { organizations?: Membership[] } };
  isPending: boolean;
  isError: boolean;
  isFetching: boolean;
};

let me: MeResult;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualUseGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualColumns = { ...(await import("../hooks/use-organization-columns")) };
const actualDataTable = { ...(await import("@/components/data-table")) };
const actualLoadError = { ...(await import("./organization-load-error")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualUseGetMe,
  useGetMe: () => ({ ...me, refetch: async () => undefined }),
}));
mock.module("../hooks/use-organization-columns", () => ({
  ...actualColumns,
  useOrganizationColumns: () => [],
}));
mock.module("@/components/data-table", () => ({
  ...actualDataTable,
  DataTable: ({ data, emptyMessage }: { data: Membership[]; emptyMessage: React.ReactNode }) =>
    data.length === 0 ? (
      <p>{emptyMessage}</p>
    ) : (
      <ul>
        {data.map((row: Membership) => (
          <li key={row.id}>{`row:${row.name}`}</li>
        ))}
      </ul>
    ),
}));
mock.module("./organization-load-error", () => ({
  ...actualLoadError,
  OrganizationLoadError: () => <p>load-error</p>,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualUseGetMe);
  mock.module("../hooks/use-organization-columns", () => actualColumns);
  mock.module("@/components/data-table", () => actualDataTable);
  mock.module("./organization-load-error", () => actualLoadError);
});

const { OrganizationTable } = await import("./organization-table");

function membership(name: string, role: string, organizationStatus = "ACTIVE"): Membership {
  return { id: `id-${name}`, name, role, organizationStatus };
}

function loaded(organizations: Membership[]): MeResult {
  return {
    data: { success: true, data: { organizations } },
    isPending: false,
    isError: false,
    isFetching: false,
  };
}

beforeEach(() => {
  me = loaded([]);
});

describe("OrganizationTable list filter", () => {
  test("lists only ACTIVE organizations where the user is OWNER or ADMIN", () => {
    me = loaded([
      membership("Owned", "OWNER"),
      membership("Joined", "MEMBER"),
      membership("Administered", "ADMIN"),
      membership("Visited", "GUEST"),
      membership("Switched off", "OWNER", "DISABLED"),
      membership("Closed admin", "ADMIN", "DISABLED"),
    ]);

    const html = renderToStaticMarkup(<OrganizationTable />);

    expect(html).toContain("row:Owned");
    expect(html).toContain("row:Administered");
    expect(html).not.toContain("row:Joined");
    expect(html).not.toContain("row:Visited");
    expect(html).not.toContain("row:Switched off");
    expect(html).not.toContain("row:Closed admin");
  });

  test("shows the empty message when the user manages nothing", () => {
    me = loaded([membership("Joined", "MEMBER")]);

    expect(renderToStaticMarkup(<OrganizationTable />)).toContain("noOrganizations");
  });

  test("shows the retry error when the user never loaded", () => {
    me = { isPending: false, isError: true, isFetching: false };

    const html = renderToStaticMarkup(<OrganizationTable />);

    expect(html).toContain("load-error");
    expect(html).not.toContain("noOrganizations");
  });

  test("keeps the managed organizations of the cached user when a refetch failed", () => {
    me = { ...loaded([membership("Owned", "OWNER")]), isError: true };

    const html = renderToStaticMarkup(<OrganizationTable />);

    expect(html).toContain("row:Owned");
    expect(html).not.toContain("load-error");
  });
});
