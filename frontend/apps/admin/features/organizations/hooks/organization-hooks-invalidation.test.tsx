import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { APP_ACCESS_KEYS, GROUP_KEYS, MEMBER_KEYS } from "./query-keys";

// Each mutation hook is rendered once with React Query replaced by a recorder:
// `useMutation` hands its options back, `useQueryClient` records every
// invalidation and removal. The test then runs the options the way React Query
// does after a request, so it reads which caches a write refreshes.
type Key = readonly unknown[];
type MutationOptions = {
  mutationFn: (variables: never) => Promise<unknown>;
  onSuccess: (data: unknown, variables: never) => unknown;
  onError: (error: Error) => void;
};

const invalidated: Key[] = [];
const removed: Key[] = [];
const toasts: { kind: string; message: string }[] = [];

const queryClient = {
  invalidateQueries: ({ queryKey }: { queryKey: Key }) => {
    invalidated.push(queryKey);
    return Promise.resolve();
  },
  removeQueries: ({ queryKey }: { queryKey: Key }) => {
    removed.push(queryKey);
  },
};

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualIntl = { ...(await import("next-intl")) };
const actualSonner = { ...(await import("sonner")) };

mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useQueryClient: () => queryClient,
  useQuery: (options: unknown) => options,
  useMutation: (options: MutationOptions) => options,
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("sonner", () => ({
  ...actualSonner,
  toast: {
    success: (message: string) => toasts.push({ kind: "success", message }),
    error: (message: string) => toasts.push({ kind: "error", message }),
  },
}));

afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("next-intl", () => actualIntl);
  mock.module("sonner", () => actualSonner);
});

const groups = await import("./use-organization-groups");
const access = await import("./use-app-access");
const members = await import("./use-organization-members");

const ORG = "org-1";
const GROUP = "group-1";

function build(hook: () => unknown): unknown {
  let result: unknown;
  function Probe() {
    result = hook();
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result;
}

async function succeed(options: MutationOptions, variables: unknown = undefined) {
  await options.onSuccess(undefined, variables as never);
}

beforeEach(() => {
  invalidated.length = 0;
  removed.length = 0;
  toasts.length = 0;
});

describe("group hooks: invalidation", () => {
  test("create refreshes the group list and the grant list", async () => {
    await succeed(
      build(() => groups.useCreateOrganizationGroup(ORG)) as unknown as MutationOptions
    );

    expect(invalidated).toEqual([GROUP_KEYS.list(ORG), APP_ACCESS_KEYS.grants(ORG)]);
  });

  test("rename refreshes the group list and the grant list, which shows the group name", async () => {
    await succeed(
      build(() => groups.useUpdateOrganizationGroup(ORG)) as unknown as MutationOptions
    );

    expect(invalidated).toEqual([GROUP_KEYS.list(ORG), APP_ACCESS_KEYS.grants(ORG)]);
  });

  test("delete drops the member list of the group and refreshes lists, access and the current user", async () => {
    await succeed(
      build(() => groups.useDeleteOrganizationGroup(ORG)) as unknown as MutationOptions,
      GROUP
    );

    expect(removed).toEqual([GROUP_KEYS.members(ORG, GROUP)]);
    expect(invalidated).toEqual([
      GROUP_KEYS.list(ORG),
      APP_ACCESS_KEYS.organization(ORG),
      USERS_KEYS.me,
    ]);
  });

  test("delete removes the member list of the group and never invalidates it, not even by prefix", async () => {
    await succeed(
      build(() => groups.useDeleteOrganizationGroup(ORG)) as unknown as MutationOptions,
      GROUP
    );

    const membersKey = GROUP_KEYS.members(ORG, GROUP);
    const matchesMembers = (key: Key) =>
      key.length <= membersKey.length && key.every((part, index) => part === membersKey[index]);

    expect(removed).toContainEqual(membersKey);
    expect(invalidated.filter(matchesMembers)).toEqual([]);
  });

  test("delete hands the group to the caller after the removal and before the refetch", async () => {
    const events: string[] = [];
    const options = build(() =>
      groups.useDeleteOrganizationGroup(ORG, {
        onBeforeInvalidate: (groupId) =>
          events.push(
            `callback ${groupId}, removed ${removed.length}, invalidated ${invalidated.length}`
          ),
      })
    ) as unknown as MutationOptions;

    await succeed(options, GROUP);

    expect(events).toEqual([`callback ${GROUP}, removed 1, invalidated 0`]);
  });

  test.each([
    ["add members", () => groups.useAddOrganizationGroupMembers(ORG)],
    ["remove member", () => groups.useRemoveOrganizationGroupMember(ORG)],
  ])(
    "%s refreshes the member list, the group list, the effective access and /users/me",
    async (_, hook) => {
      await succeed(build(hook) as unknown as MutationOptions, { groupId: GROUP });

      expect(invalidated).toEqual([
        GROUP_KEYS.members(ORG, GROUP),
        GROUP_KEYS.list(ORG),
        APP_ACCESS_KEYS.members(ORG),
        USERS_KEYS.me,
      ]);
    }
  );

  test("a membership change leaves the member lists of other groups alone", async () => {
    await succeed(
      build(() => groups.useAddOrganizationGroupMembers(ORG)) as unknown as MutationOptions,
      {
        groupId: GROUP,
      }
    );

    expect(invalidated).not.toContainEqual(GROUP_KEYS.members(ORG, "other-group"));
    expect(invalidated).not.toContainEqual(GROUP_KEYS.organization(ORG));
  });

  test("success shows a toast", async () => {
    await succeed(
      build(() => groups.useCreateOrganizationGroup(ORG)) as unknown as MutationOptions
    );

    expect(toasts).toEqual([{ kind: "success", message: "groups.createSuccess" }]);
  });
});

describe("group hooks: form error codes", () => {
  const nameTaken = new ApiError("Name taken", { status: 409, code: "APP_STA_002" });

  test.each([
    [
      "create",
      (formErrorCodes: string[]) => groups.useCreateOrganizationGroup(ORG, { formErrorCodes }),
    ],
    [
      "rename",
      (formErrorCodes: string[]) => groups.useUpdateOrganizationGroup(ORG, { formErrorCodes }),
    ],
  ])("%s shows no toast for a code the form handles", (_, hook) => {
    const options = build(() => hook(["APP_STA_002"])) as unknown as MutationOptions;

    options.onError(nameTaken);

    expect(toasts).toEqual([]);
  });

  test.each([
    ["create", () => groups.useCreateOrganizationGroup(ORG)],
    ["rename", () => groups.useUpdateOrganizationGroup(ORG)],
  ])("%s without form codes toasts the API message", (_, hook) => {
    const options = build(hook) as unknown as MutationOptions;

    options.onError(nameTaken);

    expect(toasts).toEqual([{ kind: "error", message: "Name taken" }]);
  });

  test("another error code still toasts when the form handles APP_STA_002", () => {
    const options = build(() =>
      groups.useUpdateOrganizationGroup(ORG, { formErrorCodes: ["APP_STA_002"] })
    ) as unknown as MutationOptions;

    options.onError(new ApiError("Everyone is read-only", { status: 409, code: "APP_STA_001" }));

    expect(toasts).toEqual([{ kind: "error", message: "Everyone is read-only" }]);
  });

  test("an error without a message falls back to the translation key", () => {
    const options = build(() =>
      groups.useDeleteOrganizationGroup(ORG)
    ) as unknown as MutationOptions;

    options.onError(new Error(""));

    expect(toasts).toEqual([{ kind: "error", message: "groups.deleteError" }]);
  });
});

describe("grant hooks: invalidation", () => {
  test.each([
    ["grant", () => access.useCreateAppGrant(ORG)],
    ["revoke", () => access.useRevokeAppGrant(ORG)],
  ])(
    "%s refreshes the access of the organization, the group list and /users/me",
    async (_, hook) => {
      await succeed(build(hook) as unknown as MutationOptions);

      expect(invalidated).toEqual([
        APP_ACCESS_KEYS.organization(ORG),
        GROUP_KEYS.list(ORG),
        USERS_KEYS.me,
      ]);
    }
  );

  test("the access prefix matches both the grant list and the effective access list", () => {
    const prefix = APP_ACCESS_KEYS.organization(ORG);

    expect(APP_ACCESS_KEYS.grants(ORG).slice(0, prefix.length)).toEqual([...prefix]);
    expect(APP_ACCESS_KEYS.members(ORG).slice(0, prefix.length)).toEqual([...prefix]);
  });

  test("the success toasts are the grant and revoke keys", async () => {
    await succeed(build(() => access.useCreateAppGrant(ORG)) as unknown as MutationOptions);
    await succeed(build(() => access.useRevokeAppGrant(ORG)) as unknown as MutationOptions);

    expect(toasts.map((toast) => toast.message)).toEqual([
      "access.grantSuccess",
      "access.revokeSuccess",
    ]);
  });
});

describe("member hooks: invalidation", () => {
  const expected = [
    MEMBER_KEYS.list(ORG),
    GROUP_KEYS.organization(ORG),
    APP_ACCESS_KEYS.organization(ORG),
  ];

  test.each([
    ["role change", () => members.useUpdateMemberRole(ORG)],
    ["status change", () => members.useUpdateMemberStatus(ORG)],
    ["removal", () => members.useRemoveMember(ORG)],
  ])(
    "%s refreshes members, every group list of the organization and the access",
    async (_, hook) => {
      await succeed(build(hook) as unknown as MutationOptions);

      expect(invalidated).toEqual(expected);
    }
  );

  test("the group prefix matches the group list and every member list of the organization", () => {
    const prefix = GROUP_KEYS.organization(ORG);

    expect(GROUP_KEYS.list(ORG).slice(0, prefix.length)).toEqual([...prefix]);
    expect(GROUP_KEYS.members(ORG, GROUP).slice(0, prefix.length)).toEqual([...prefix]);
    expect(GROUP_KEYS.list("org-2").slice(0, prefix.length)).not.toEqual([...prefix]);
  });
});

describe("query hooks: enabled guards", () => {
  type QueryOptions = { enabled: boolean; queryKey: Key };

  test("the group lists wait for the ids", () => {
    expect((build(() => groups.useOrganizationGroups("")) as unknown as QueryOptions).enabled).toBe(
      false
    );
    expect(
      (build(() => groups.useOrganizationGroups(ORG)) as unknown as QueryOptions).enabled
    ).toBe(true);
    expect(
      (build(() => groups.useOrganizationGroupMembers(ORG, undefined)) as unknown as QueryOptions)
        .enabled
    ).toBe(false);
    expect(
      (build(() => groups.useOrganizationGroupMembers(ORG, GROUP)) as unknown as QueryOptions)
        .queryKey
    ).toEqual(GROUP_KEYS.members(ORG, GROUP) as unknown as Key);
  });

  test("the access queries wait for the organization id", () => {
    expect((build(() => access.useAppGrants(undefined)) as unknown as QueryOptions).enabled).toBe(
      false
    );
    expect(
      (build(() => access.useMemberAppAccess(ORG)) as unknown as QueryOptions).queryKey
    ).toEqual(APP_ACCESS_KEYS.members(ORG) as unknown as Key);
  });
});
