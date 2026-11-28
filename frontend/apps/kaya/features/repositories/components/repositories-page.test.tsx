import { afterAll, describe, expect, mock, test } from "bun:test";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { OrganizationRole } from "@/lib/identity/schemas";
import { hasRole } from "@/lib/identity/schemas";

// The table reads the role, the repository list and the query cache; those are
// replaced and the row actions, which depend on the role, are real.
let role: OrganizationRole = "MEMBER";

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualRepositories = { ...(await import("../hooks/use-repositories")) };
mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useQuery: () => ({ data: undefined, error: null, refetch: async () => undefined }),
  useQueries: () => [],
  useMutation: () => ({ isPending: false, mutate: () => undefined, mutateAsync: async () => ({}) }),
  useQueryClient: () => ({}),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: "user-me" },
    organization: { organizationId: "org-1", name: "Korur", role },
    authorId: null,
    switchOrganization: () => undefined,
    can: (required: OrganizationRole) => hasRole(role, required),
  }),
}));
mock.module("../hooks/use-repositories", () => ({
  ...actualRepositories,
  useAllRepositories: () => ({
    data: {
      success: true,
      data: [
        {
          id: "r-1",
          name: "app",
          remoteUrl: "git@github.com:acme/app.git",
          ticketUrlTemplate: null,
          status: "ACTIVE",
        },
      ],
    },
    error: null,
    refetch: async () => undefined,
  }),
  useUpdateRepository: () => ({ isPending: false, mutate: () => undefined }),
}));
afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("../hooks/use-repositories", () => actualRepositories);
});

const { RepositoriesPage, searchRepositories } = await import("./repositories-page");

const render = (viewer: OrganizationRole, searchParams = "") => {
  role = viewer;
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={searchParams}>
      <RepositoriesPage />
    </NuqsTestingAdapter>
  );
};

describe("RepositoriesPage row actions", () => {
  test("a member has no row action", () => {
    const html = render("MEMBER");
    expect(html).not.toContain("Edit app");
    expect(html).not.toContain("Packs of app");
  });

  test("an admin edits the repository but does not pick its packs", () => {
    const html = render("ADMIN");
    expect(html).not.toContain("Packs of app");
  });

  test("only an owner opens the packs of a repository", () => {
    const html = render("OWNER");
    expect(html).toContain('aria-label="Packs of app"');
  });
});

describe("RepositoriesPage layout", () => {
  test("the page heading and the toolbar's search and filter lead the list", () => {
    const html = render("MEMBER");
    expect(html).toContain(">Repositories</h1>");
    expect(html).toContain('data-slot="list-toolbar"');
    expect(html).toContain('aria-label="Search repositories"');
    expect(html).toContain("Filter");
  });

  test("a status the list does not hold shows the filtered empty state", () => {
    const html = render("MEMBER", "?status=ARCHIVED");
    expect(html).toContain("No repository matches");
    expect(html).toContain("Clear filters");
  });
});

describe("searchRepositories", () => {
  const repository = (
    id: string,
    name: string,
    remoteUrl: string,
    status: "ACTIVE" | "ARCHIVED"
  ) => ({
    id,
    name,
    remoteUrl,
    ticketUrlTemplate: null,
    status,
    createdAt: "2026-11-28T10:00:00Z",
  });
  const repositories = [
    repository("r-1", "app", "git@github.com:acme/app.git", "ACTIVE"),
    repository("r-2", "Docs", "git@github.com:acme/handbook.git", "ARCHIVED"),
  ];
  const ids = (rows: readonly { id: string }[]) => rows.map((row: { id: string }) => row.id);

  test("an empty search and no status keep every repository", () => {
    expect(ids(searchRepositories(repositories, "  ", null))).toEqual(["r-1", "r-2"]);
  });

  test("the search matches the name or the remote, ignoring case", () => {
    expect(ids(searchRepositories(repositories, "docs", null))).toEqual(["r-2"]);
    expect(ids(searchRepositories(repositories, "HANDBOOK", null))).toEqual(["r-2"]);
  });

  test("the status keeps only its repositories", () => {
    expect(ids(searchRepositories(repositories, "", "ACTIVE"))).toEqual(["r-1"]);
    expect(ids(searchRepositories(repositories, "app", "ARCHIVED"))).toEqual([]);
  });
});
