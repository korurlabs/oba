import { afterAll, describe, expect, mock, test } from "bun:test";
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
const actualSettings = { ...(await import("../hooks/use-settings")) };
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
mock.module("../hooks/use-settings", () => ({
  ...actualSettings,
  useRepositorySettings: () => ({
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
  mock.module("../hooks/use-settings", () => actualSettings);
});

const { RepositoriesSettings } = await import("./repositories-settings");

const render = (viewer: OrganizationRole) => {
  role = viewer;
  return renderToStaticMarkup(<RepositoriesSettings />);
};

describe("RepositoriesSettings row actions", () => {
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
