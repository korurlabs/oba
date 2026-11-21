import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { OrganizationRole } from "@/lib/identity/schemas";
import { hasRole } from "@/lib/identity/schemas";

import type { ChangeDetailResponse } from "../schemas";

// The detail reads the change, its diff, the members and the owners through
// the query cache and the role from the identity; those reads are replaced,
// the rendering and the access rules are real.
let role: OrganizationRole = "MEMBER";
let me = "user-me";
let change: ChangeDetailResponse;

const IN_REVIEW: ChangeDetailResponse = {
  version: {
    id: "v-7",
    packId: "p-1",
    number: 7,
    status: "IN_REVIEW",
    baseVersionId: "v-6",
    proposedBy: "user-ada",
    source: "CLI",
    reason: "Allow the oba CLI in every repository",
    executableChange: false,
    manifestSha256: null,
    submittedAt: "2026-10-24T09:00:00Z",
    decidedAt: null,
    createdAt: "2026-10-24T08:00:00Z",
    updatedAt: "2026-10-24T09:00:00Z",
  },
  requiredRole: "ADMIN",
  reviews: [
    {
      id: "r-1",
      versionId: "v-7",
      reviewerId: "user-bob",
      decision: "COMMENT",
      comment: "Limit it to kazan tickets",
      createdAt: "2026-10-24T10:00:00Z",
    },
  ],
  violations: [],
  canApprove: true,
  canReview: true,
};

const answer = (data: unknown) => ({
  data: { success: true, data },
  error: null,
  refetch: async () => undefined,
});

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualChanges = { ...(await import("../hooks/use-changes")) };
const actualSettings = { ...(await import("@/features/members/hooks/use-members")) };
const actualVersions = { ...(await import("@/features/versions/hooks/use-versions")) };
const actualOwners = { ...(await import("@/features/scope/hooks/use-owners")) };
const actualControl = { ...(await import("@/features/versions/components/change-control")) };
const actualOpen = { ...(await import("@/features/versions/components/open-change")) };
let openChange: { id: string; packId: string } | null = null;
const setChangeId = (id: string | null): void => {
  setIds.push(id);
};
const setIds: (string | null)[] = [];
const actualEffective = { ...(await import("@/features/scope/hooks/use-effective")) };

/** The effective items per version id; a version left out reads as empty. */
let effective: Record<string, unknown[]> = {};
let effectiveError: Error | null = null;

const OWNER = {
  id: "p-1",
  name: "village",
  parentPackId: null,
  currentVersionId: "v-8",
  required: false,
  mountPaths: [],
  repositoryIds: ["repo-1"],
};
let owners: unknown[] = [OWNER];

mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useMutation: () => ({ isPending: false, mutate: () => undefined, mutateAsync: async () => ({}) }),
  useQueryClient: () => ({}),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined }),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: me },
    organization: { organizationId: "org-1", name: "Korur", role },
    switchOrganization: () => undefined,
    can: (required: OrganizationRole) => hasRole(role, required),
  }),
}));
mock.module("../hooks/use-changes", () => ({
  ...actualChanges,
  useChange: () => answer(change),
}));
mock.module("@/features/versions/hooks/use-versions", () => ({
  ...actualVersions,
  useVersionDiff: () =>
    answer([
      {
        path: ".claude/settings.json",
        change: "MODIFIED",
        oldMode: "FILE",
        newMode: "FILE",
        oldSha256: "a",
        newSha256: "b",
        oldSymlinkTarget: null,
        newSymlinkTarget: null,
        binary: false,
        unifiedDiff: '--- a/x\n+++ b/x\n@@ -1 +1 @@\n-"old"\n+"Bash(oba:*)"\n',
      },
    ]),
}));
mock.module("@/features/members/hooks/use-members", () => ({
  ...actualSettings,
  useMembers: () =>
    answer([
      { userId: "user-ada", displayName: "Ada" },
      { userId: "user-bob", displayName: "Bob" },
    ]),
}));
mock.module("@/features/scope/hooks/use-owners", () => ({
  ...actualOwners,
  useOwners: () => ({ owners, repositories: [], error: null, retry: () => undefined }),
}));
mock.module("@/features/scope/hooks/use-effective", () => ({
  ...actualEffective,
  usePackEffective: (_packId: string, versionId: string | null) =>
    effectiveError
      ? { data: undefined, error: effectiveError, refetch: async () => undefined }
      : answer(effective[versionId ?? ""] ?? []),
}));
// The rebase with conflict choices is the change control's dialog; the detail only has to mount it.
mock.module("@/features/versions/components/change-control", () => ({
  ...actualControl,
  RebaseDialog: ({ draftId }: { draftId: string }) => <span data-rebase-dialog={draftId} />,
}));
mock.module("@/features/versions/components/open-change", () => ({
  ...actualOpen,
  useOpenChange: () => ({
    changeId: openChange?.id ?? null,
    change: openChange,
    loading: false,
    setChangeId,
  }),
}));

afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("../hooks/use-changes", () => actualChanges);
  mock.module("@/features/members/hooks/use-members", () => actualSettings);
  mock.module("@/features/versions/hooks/use-versions", () => actualVersions);
  mock.module("@/features/scope/hooks/use-owners", () => actualOwners);
  mock.module("@/features/versions/components/change-control", () => actualControl);
  mock.module("@/features/versions/components/open-change", () => actualOpen);
  mock.module("@/features/scope/hooks/use-effective", () => actualEffective);
});

const { ChangeDetail, overrideBaseMoves } = await import("./change-detail");

function render(
  as: OrganizationRole,
  value: ChangeDetailResponse,
  viewer = "user-me",
  known: unknown[] = [OWNER]
) {
  owners = known;
  role = as;
  me = viewer;
  change = value;
  return renderToStaticMarkup(<ChangeDetail versionId={value.version.id} />);
}

// The class list holds Tailwind's `disabled:` variants, so only the attribute counts.
const enabledButton = (label: string) =>
  new RegExp(`<button(?![^>]*disabled="")[^>]*>${label}</button>`);
const disabledButton = (label: string) =>
  new RegExp(`<button[^>]*disabled=""[^>]*>${label}</button>`);

const DRAFT_OF_ADA: ChangeDetailResponse = {
  ...IN_REVIEW,
  version: { ...IN_REVIEW.version, status: "DRAFT", submittedAt: null },
  reviews: [],
  violations: null,
  canApprove: false,
  canReview: false,
};

describe("ChangeDetail", () => {
  test("shows the change, its diff and its reviews by name", () => {
    const html = render("MEMBER", IN_REVIEW);
    expect(html).toContain("Version 7 of Pack village");
    expect(html).toContain("oba kaya propose");
    expect(html).toContain("Ada");
    expect(html).toContain("Allow the oba CLI in every repository");
    expect(html).toContain(".claude/settings.json");
    expect(html).toContain("Bash(oba:*)");
    expect(html).toContain("Bob");
    expect(html).toContain("Limit it to kazan tickets");
  });

  test("a child pack's change names the pack, not a repository", () => {
    const html = render("MEMBER", IN_REVIEW, "user-me", [
      { ...OWNER, name: "kagi", parentPackId: "p-root", mountPaths: ["kagi"] },
    ]);
    expect(html).toContain("Pack kagi");
    expect(html).not.toContain("Repository kagi");
  });

  test("a member reviews nothing", () => {
    const html = render("MEMBER", IN_REVIEW);
    expect(html).not.toContain(">Approve<");
    expect(html).not.toContain(">Reject<");
    expect(html).not.toContain(">Comment<");
  });

  test("an eligible admin approves, rejects and comments", () => {
    const html = render("ADMIN", IN_REVIEW);
    expect(html).toMatch(enabledButton("Approve"));
    expect(html).toMatch(enabledButton("Reject"));
    expect(html).toMatch(enabledButton("Comment"));
  });

  test("an admin may not approve an executable change", () => {
    const html = render("ADMIN", {
      ...IN_REVIEW,
      version: { ...IN_REVIEW.version, executableChange: true },
      requiredRole: "OWNER",
      canApprove: false,
    });
    expect(html).toMatch(disabledButton("Approve"));
    expect(html).toContain("only an owner approves it");
    expect(html).toMatch(enabledButton("Reject"));
  });

  test("the proposer's controls are greyed out", () => {
    const html = render("OWNER", { ...IN_REVIEW, canApprove: false, canReview: false }, "user-ada");
    expect(html).toMatch(disabledButton("Approve"));
    expect(html).toMatch(disabledButton("Reject"));
    expect(html).toContain("You proposed this change");
  });

  test("the rules a change breaks are listed", () => {
    const html = render("OWNER", {
      ...IN_REVIEW,
      canApprove: false,
      violations: [
        { path: "AGENTS.md", rule: "FILE_TOO_LARGE", message: "AGENTS.md is over 5 MiB" },
      ],
    });
    expect(html).toContain("AGENTS.md is over 5 MiB");
    expect(html).toMatch(disabledButton("Approve"));
  });

  test("a superseded version can be restored by any member", () => {
    const html = render("MEMBER", {
      ...IN_REVIEW,
      version: { ...IN_REVIEW.version, status: "SUPERSEDED" },
      violations: null,
      canApprove: false,
      canReview: false,
    });
    expect(html).toContain(">Restore<");
    expect(html).not.toContain(">Approve<");
  });

  test("the current version is not offered for restore", () => {
    const html = render("OWNER", {
      ...IN_REVIEW,
      version: { ...IN_REVIEW.version, id: "v-8", status: "APPROVED" },
      violations: null,
      canApprove: false,
      canReview: false,
    });
    expect(html).toContain(">Current<");
    expect(html).not.toContain(">Restore<");
  });

  test("the proposer of an open change submits, rebases, continues and deletes it; others do not", () => {
    const draft: ChangeDetailResponse = {
      ...IN_REVIEW,
      version: { ...IN_REVIEW.version, status: "DRAFT", submittedAt: null },
      reviews: [],
      violations: null,
      canApprove: false,
      canReview: false,
    };
    const own = render("MEMBER", draft, "user-ada");
    expect(own).toContain(">Submit for review<");
    expect(own).toContain('data-rebase-dialog="v-7"');
    expect(own).toContain(">Delete change<");
    expect(own).toContain(">Continue editing<");
    const other = render("OWNER", draft);
    expect(other).not.toContain(">Submit for review<");
    expect(other).not.toContain(">Delete change<");
    expect(other).not.toContain(">Continue editing<");
    expect(other).not.toContain("data-rebase-dialog");
  });

  test("the proposer sees the rules their change breaks; nobody sees another's checks", () => {
    const draft: ChangeDetailResponse = {
      ...IN_REVIEW,
      version: { ...IN_REVIEW.version, status: "DRAFT", submittedAt: null },
      reviews: [],
      violations: [
        { path: "AGENTS.md", rule: "FILE_TOO_LARGE", message: "AGENTS.md is over 5 MiB" },
      ],
      canApprove: false,
      canReview: false,
    };
    const own = render("MEMBER", draft, "user-ada");
    expect(own).toContain("Your change breaks a rule; a submit refuses it until it is fixed.");
    expect(own).toContain("AGENTS.md is over 5 MiB");
    const other = render("OWNER", draft);
    expect(other).not.toContain("AGENTS.md is over 5 MiB");
  });

  test("a reviewed change keeps its delete hidden", () => {
    const html = render(
      "MEMBER",
      {
        ...IN_REVIEW,
        version: { ...IN_REVIEW.version, status: "DRAFT", decidedAt: "2026-10-24T11:00:00Z" },
        violations: null,
        canApprove: false,
        canReview: false,
      },
      "user-ada"
    );
    expect(html).toContain(">Submit for review<");
    expect(html).not.toContain(">Delete change<");
  });

  test("Continue editing is enabled with no change open, and waits while another change is open", () => {
    openChange = null;
    const own = render("MEMBER", DRAFT_OF_ADA, "user-ada");
    expect(own).toMatch(enabledButton("Continue editing"));
    expect(own).not.toContain("Why is this disabled?");

    openChange = { id: "v-other", packId: "p-2" };
    const locked = render("MEMBER", DRAFT_OF_ADA, "user-ada", [
      OWNER,
      { ...OWNER, id: "p-2", name: "kagi" },
    ]);
    openChange = null;
    expect(locked).toMatch(disabledButton("Continue editing"));
    expect(locked).toContain('aria-label="Why is this disabled?"');
  });

  test("Continue editing is disabled for a second change of the same pack, which would swap the open one", () => {
    openChange = { id: "v-other", packId: "p-1" };
    const html = render("MEMBER", DRAFT_OF_ADA, "user-ada");
    openChange = null;
    expect(html).toMatch(disabledButton("Continue editing"));
    expect(html).toContain('aria-label="Why is this disabled?"');
  });

  test("Continue editing is enabled on the change that is already open", () => {
    openChange = { id: "v-7", packId: "p-1" };
    const html = render("MEMBER", DRAFT_OF_ADA, "user-ada");
    openChange = null;
    expect(html).toMatch(enabledButton("Continue editing"));
  });

  test("Restore is disabled only by an open change of another pack", () => {
    const superseded: ChangeDetailResponse = {
      ...IN_REVIEW,
      version: { ...IN_REVIEW.version, status: "SUPERSEDED" },
      violations: null,
      canApprove: false,
      canReview: false,
    };
    openChange = { id: "v-other", packId: "p-2" };
    const locked = render("MEMBER", superseded, "user-me", [
      OWNER,
      { ...OWNER, id: "p-2", name: "kagi" },
    ]);
    expect(locked).toMatch(disabledButton("Restore"));

    openChange = { id: "v-other", packId: "p-1" };
    const same = render("MEMBER", superseded);
    openChange = null;
    expect(same).toMatch(enabledButton("Restore"));
    expect(same).not.toContain("Why is this disabled?");
  });

  test("a change that only moves an override base lists it above the diffs", () => {
    effective = {
      "v-6": [overrideItem("v-root-3", 4)],
      "v-7": [overrideItem("v-root-5", 5)],
    };
    const html = render("ADMIN", IN_REVIEW, "user-me", [OWNER, ROOT]);
    effective = {};
    expect(html).toContain("Override base v4 to v5");
    expect(html).toContain(".agents/rules/style.md");
    expect(html).toContain("from org");
    expect(html.indexOf("Override base v4 to v5")).toBeLessThan(
      html.indexOf(".claude/settings.json")
    );
  });

  test("a refused read of the overrides shows its error", () => {
    effectiveError = new Error("Network down");
    const html = render("ADMIN", IN_REVIEW);
    effectiveError = null;
    expect(html).toContain("Could not load the overrides of this change");
  });
});

const ROOT = { ...OWNER, id: "p-root", name: "org", currentVersionId: "v-root-5" };

function overrideItem(baseVersionId: string | null, baseItemVersion: number | null, own = true) {
  return {
    path: ".agents/rules/style.md",
    kind: "RULE",
    sourcePackId: "p-1",
    sourceVersionId: "v-7",
    own,
    locked: null,
    override: {
      basePackId: "p-root",
      baseVersionId,
      baseItemVersion,
      parentVersionId: "v-root-5",
      parentItemVersion: 5,
      status: "CURRENT",
    },
  } as const;
}

describe("overrideBaseMoves", () => {
  test("lists an own override whose base moved, with the parent pack's name", () => {
    expect(
      overrideBaseMoves([overrideItem("v-root-5", 5)], [overrideItem("v-root-3", 4)], [ROOT])
    ).toEqual([{ path: ".agents/rules/style.md", packName: "org", from: 4, to: 5 }]);
  });

  test("skips an unchanged base, a new override, an unknown base and an inherited item", () => {
    expect(
      overrideBaseMoves([overrideItem("v-root-3", 4)], [overrideItem("v-root-3", 4)], [ROOT])
    ).toEqual([]);
    expect(overrideBaseMoves([overrideItem("v-root-5", 5)], [], [ROOT])).toEqual([]);
    expect(
      overrideBaseMoves([overrideItem("v-root-5", 5)], [overrideItem(null, null)], [ROOT])
    ).toEqual([]);
    expect(
      overrideBaseMoves([overrideItem("v-root-5", 5, false)], [overrideItem("v-root-3", 4)], [ROOT])
    ).toEqual([]);
  });

  test("names an unknown pack plainly", () => {
    expect(
      overrideBaseMoves([overrideItem("v-root-5", 5)], [overrideItem("v-root-3", 4)], [])[0]
        ?.packName
    ).toBe("a parent pack");
  });
});
