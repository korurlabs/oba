import { afterAll, describe, expect, mock, test } from "bun:test";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { OrganizationRole } from "@/lib/identity/schemas";
import { hasRole } from "@/lib/identity/schemas";
import type { Owner } from "@/features/scope/schemas";

// The reads and the role are replaced; the page, its toolbar and the dialog trigger are real.
let role: OrganizationRole = "OWNER";
const owner = (id: string, name: string): Owner => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: null,
  required: false,
  mountPaths: [],
  repositoryIds: [],
});
let owners: Owner[] | undefined = [owner("p-1", "village")];

const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualOwners = { ...(await import("@/features/scope/hooks/use-owners")) };
const actualPacks = { ...(await import("../hooks/use-packs")) };
const actualTree = { ...(await import("@/features/scope/components/pack-tree")) };
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
mock.module("@/features/scope/hooks/use-owners", () => ({
  ...actualOwners,
  useOwners: () => ({ owners, repositories: [], error: null, retry: () => undefined }),
  useCreatePack: () => ({ isPending: false, mutate: () => undefined }),
}));
mock.module("../hooks/use-packs", () => ({
  ...actualPacks,
  usePackDetail: () => ({ data: undefined, error: null, refetch: () => undefined }),
}));
mock.module("@/features/scope/components/pack-tree", () => ({
  ...actualTree,
  PackTree: () => <nav>tree</nav>,
}));
afterAll(() => {
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("@/features/scope/hooks/use-owners", () => actualOwners);
  mock.module("../hooks/use-packs", () => actualPacks);
  mock.module("@/features/scope/components/pack-tree", () => actualTree);
});

const { PacksPage } = await import("./packs-page");

const render = (): string =>
  renderToStaticMarkup(
    <NuqsTestingAdapter>
      <PacksPage />
    </NuqsTestingAdapter>
  );

describe("PacksPage", () => {
  test("a subtitle sits under the title, and the What is a pack tip stays", () => {
    role = "OWNER";
    const html = render();
    expect(html).toContain(">Packs</h1>");
    expect(html).toContain(
      "The packs your repositories compose, how they nest, and their versions."
    );
    expect(html.indexOf(">Packs</h1>")).toBeLessThan(html.indexOf("The packs your repositories"));
    expect(html).toContain('aria-label="What is a pack?"');
  });

  test("an owner gets a New pack button in the toolbar and no child pack button", () => {
    role = "OWNER";
    const html = render();
    expect(html).toContain("New pack</button>");
    expect(html).not.toContain("Child pack");
  });

  test("a member and an admin get no New pack button", () => {
    for (const viewer of ["MEMBER", "ADMIN"] as const) {
      role = viewer;
      expect(render()).not.toContain("New pack");
    }
    role = "OWNER";
  });

  test("an empty organization gives an owner the New pack button in the empty state", () => {
    role = "OWNER";
    owners = [];
    const html = render();
    expect(html).toContain("No packs yet");
    // One New pack in the toolbar and one in the empty state.
    expect(html.split("New pack</button>").length - 1).toBe(2);
    role = "MEMBER";
    const member = render();
    expect(member).toContain("An owner creates packs.");
    expect(member).not.toContain("New pack</button>");
    owners = [owner("p-1", "village")];
    role = "OWNER";
  });

  test("no count or source line renders", () => {
    role = "OWNER";
    const html = render();
    expect(html).not.toMatch(/\d+ packs?\./);
    expect(html).not.toContain("version 1 (approved)");
  });
});
