import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { PackDetailResponse } from "@/features/scope/schemas";
import type { OrganizationRole } from "@/lib/identity/schemas";
import { hasRole } from "@/lib/identity/schemas";

// The role is replaced; the section, its buttons and the closed packs dialog are real.
let role: OrganizationRole = "MEMBER";

// A copy, not the namespace: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mock back for the files that run later.
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
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
afterAll(() => {
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
});

const { PackRepositories } = await import("./pack-repositories");

const pack = (required = false) =>
  ({
    id: "p-1",
    name: "village",
    required,
    repositories: [
      {
        id: "r-1",
        name: "app",
        remoteUrl: "git@github.com:acme/app.git",
        status: "ACTIVE",
        rootPath: null,
        latestReleaseNumber: 3,
      },
    ],
  }) as unknown as PackDetailResponse;

const repositories = [
  { id: "r-1", name: "app", remoteUrl: "git@github.com:acme/app.git", status: "ACTIVE" },
  { id: "r-2", name: "docs", remoteUrl: "git@github.com:acme/docs.git", status: "ACTIVE" },
];

const render = (viewer: OrganizationRole, required = false) => {
  role = viewer;
  return renderToStaticMarkup(
    <PackRepositories pack={pack(required)} repositories={repositories} />
  );
};

describe("PackRepositories", () => {
  test("the list is read only: no folder field and no remove button for anyone", () => {
    for (const viewer of ["MEMBER", "OWNER"] as const) {
      const html = render(viewer);
      expect(html).toContain("app");
      expect(html).not.toContain("Remove village from app");
      expect(html).not.toContain("Attach");
      expect(html).not.toContain("Mount folder");
    }
  });

  test("a member reads the rows and gets no action", () => {
    const html = render("MEMBER");
    expect(html).not.toContain('aria-label="Packs of app"');
    expect(html).not.toContain("Add to repository");
  });

  test("an admin reads the rows as a member does", () => {
    const html = render("ADMIN");
    expect(html).not.toContain('aria-label="Packs of app"');
    expect(html).not.toContain("Add to repository");
  });

  test("an owner opens a row's packs dialog and adds the pack to a repository", () => {
    const html = render("OWNER");
    expect(html).toContain('aria-label="Packs of app"');
    expect(html).toContain("Add to repository");
  });

  test("a required pack is never added, but its rows still open the packs dialog", () => {
    const html = render("OWNER", true);
    expect(html).not.toContain("Add to repository");
    expect(html).toContain('aria-label="Packs of app"');
  });
});
