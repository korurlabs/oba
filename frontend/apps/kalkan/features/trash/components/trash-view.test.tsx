import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The trash lists deleted pages for members. The role, the user, the trash
// query and the restore are replaced; the translator prints its key and values.
type QueryState = {
  data?: { success: boolean; data: unknown[] };
  isError: boolean;
  error?: unknown;
};

let isMember: boolean;
let me: { data?: unknown; isError: boolean };
let trash: QueryState;
let trashEnabled: boolean[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualDates = { ...(await import("@oba/shared/i18n/use-date-format")) };
const actualRole = { ...(await import("@/hooks/use-is-organization-admin")) };
const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useLocale: () => "en",
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    values ? `${key}(${Object.values(values).join(",")})` : key,
}));
mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  useTrash: ({ enabled }: { enabled: boolean }) => {
    trashEnabled.push(enabled);
    return { ...trash, refetch: async () => undefined };
  },
  useRestorePage: () => ({ mutate: () => undefined, isPending: false, variables: undefined }),
}));
mock.module("@oba/shared/i18n/use-date-format", () => ({
  ...actualDates,
  useDateFormat: () => ({
    formatDate: (value: string) => `date:${value}`,
    formatDateTime: (value: string) => `dateTime:${value}`,
  }),
}));
mock.module("@/hooks/use-is-organization-admin", () => ({
  ...actualRole,
  useIsOrganizationMember: () => isMember,
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualMe,
  useGetMe: () => me,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@oba/shared/i18n/use-date-format", () => actualDates);
  mock.module("@/hooks/use-is-organization-admin", () => actualRole);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualMe);
});

const { TrashView } = await import("./trash-view");
const { useTrashColumns } = await import("../hooks/use-trash-columns");
const { DataTable } = await import("@/components/data-table");

const deleted = {
  id: "page-1",
  title: "Old policy",
  path: "policies/old",
  deletedAt: "2026-11-22T09:00:00Z",
  deletedByName: "Zeynep",
  descendantCount: 3,
};

beforeEach(() => {
  isMember = true;
  me = { data: { success: true }, isError: false };
  trash = { isError: false };
  trashEnabled = [];
});

describe("TrashView", () => {
  test("tells a guest the trash is for members and does not ask the server", () => {
    isMember = false;

    const html = renderToStaticMarkup(<TrashView />);

    expect(html).toContain("membersOnly");
    expect(trashEnabled).toEqual([false]);
  });

  test("waits for the user before deciding the role", () => {
    isMember = false;
    me = { isError: false };

    const html = renderToStaticMarkup(<TrashView />);

    expect(html).toContain('aria-busy="true"');
    expect(html).not.toContain("membersOnly");
  });

  test("shows the loading outline, then the empty state", () => {
    expect(renderToStaticMarkup(<TrashView />)).toContain('aria-label="loading"');

    trash = { data: { success: true, data: [] }, isError: false };
    expect(renderToStaticMarkup(<TrashView />)).toContain("emptyHint");
  });

  test("lists title, path, deleted when and by, descendants and a restore action", () => {
    trash = { data: { success: true, data: [deleted] }, isError: false };

    const html = renderToStaticMarkup(<TrashView />);

    expect(html).toContain("Old policy");
    expect(html).toContain("policies/old");
    expect(html).toContain("date:2026-11-22T09:00:00Z");
    expect(html).toContain("Zeynep");
    expect(html).toContain(">3<");
    expect(html).toContain("restoreLabel(Old policy)");
    expect(html).not.toContain("parentFirst");
  });

  test("shows a retry when the trash failed to load", () => {
    trash = { isError: true, error: new Error("server down") };

    const html = renderToStaticMarkup(<TrashView />);

    expect(html).toContain('role="alert"');
    expect(html).toContain("server down");
  });
});

describe("useTrashColumns", () => {
  function Table({ parentFirstIds }: { parentFirstIds: ReadonlySet<string> }) {
    const columns = useTrashColumns({
      onRestore: () => undefined,
      restoringId: "page-1",
      parentFirstIds,
    });
    return (
      <DataTable label="trash" columns={columns} data={[deleted]} getRowId={(row) => row.id} />
    );
  }

  test("marks a row whose parent must be restored first", () => {
    const html = renderToStaticMarkup(<Table parentFirstIds={new Set(["page-1"])} />);

    expect(html).toContain("parentFirst");
  });

  test("disables the restore button while that page is being restored", () => {
    const html = renderToStaticMarkup(<Table parentFirstIds={new Set()} />);

    expect(html).toMatch(/<button[^>]*disabled=""[^>]*aria-label="restoreLabel\(Old policy\)"/);
  });
});
