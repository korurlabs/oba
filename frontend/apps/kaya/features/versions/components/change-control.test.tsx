import { afterAll, afterEach, beforeEach, describe, expect, mock, spyOn, test } from "bun:test";
import {
  Children,
  cloneElement,
  createContext,
  isValidElement,
  useContext,
  useEffect,
  useState,
  type ReactElement,
  type ReactNode,
} from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";

import type { ReadyScope } from "@/features/scope/components/scope-page";
import type { VersionDetailResponse } from "@/features/versions/schemas";
import { cleanup, renderLive, type Screen } from "@oba/shared/testing/live-render";

import {
  holdUnsavedEdit,
  hasUnsavedEdit,
  UNSAVED_EDIT_BLOCKER,
  type EditOrigin,
} from "./draft-context";

// The top bar, the open change and an editor that holds an unsaved edit are real;
// the server answers, the identity and the dialog shell are fixed.
const ME = "u-1";
const OWNERS = [
  ["p-1", "Village"],
  ["p-2", "Zebra"],
].map(([id, name]) => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: "v-0",
  required: false,
  mountPaths: [],
  repositoryIds: [],
}));

type Fake = {
  drafts: { id: string; packId: string }[];
  files: Record<string, { path: string; sha256: string | null }[]>;
  created: string[];
  createFails: boolean;
};
let fake: Fake;
let userSeed = 0;
let user = "o-0";

function detail(id: string, packId: string): VersionDetailResponse {
  return {
    id,
    number: 4,
    packId,
    status: "DRAFT",
    proposedBy: ME,
    baseVersionId: "v-0",
    files: fake.files[id] ?? [],
  } as unknown as VersionDetailResponse;
}

const DialogState = createContext<{ open: boolean; set: (open: boolean) => void }>({
  open: false,
  set: () => undefined,
});

const actualApi = { ...(await import("@/features/versions/api")) };
const actualChangesApi = { ...(await import("@/features/changes/api")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualOwners = { ...(await import("@/features/scope/hooks/use-owners")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
mock.module("@/features/versions/api", () => ({
  ...actualApi,
  getVersion: async (id: string) => {
    const known = [
      ...fake.drafts,
      ...fake.created.map((created) => ({ id: created, packId: "p-1" })),
    ];
    const found = known.find((entry) => entry.id === id);
    return { data: found === undefined ? null : detail(found.id, found.packId) };
  },
  createDraft: async ({ packId }: { packId: string }) => {
    if (fake.createFails) throw new Error("The server refused the change.");
    const id = `c-new-${fake.created.length + 1}`;
    fake.created.push(id);
    fake.drafts.push({ id, packId });
    return { data: { id, number: 1 } };
  },
  getVersionDiff: async () => ({ data: [] }),
}));
mock.module("@/features/changes/api", () => ({
  ...actualChangesApi,
  listChanges: async ({ packId }: { packId: string }) => ({
    data: fake.drafts
      .filter((entry) => entry.packId === packId && !fake.created.includes(entry.id))
      .map((entry) => ({
        version: {
          id: entry.id,
          packId: entry.packId,
          proposedBy: ME,
          createdAt: "2026-01-01T00:00:00Z",
        },
      })),
  }),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({ me: { id: ME, memberships: [] }, organization: { organizationId: user } }),
}));
mock.module("@/features/scope/hooks/use-owners", () => ({
  ...actualOwners,
  useOwners: () => ({ owners: OWNERS, repositories: [], error: null, retry: () => undefined }),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined, replace: () => undefined }),
}));
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({
    open,
    onOpenChange,
    children,
  }: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    children: ReactNode;
  }) => <DialogState.Provider value={{ open, set: onOpenChange }}>{children}</DialogState.Provider>,
  DialogTrigger: ({ children }: { children: ReactNode }) => {
    const { set } = useContext(DialogState);
    const only = Children.only(children);
    return isValidElement(only)
      ? cloneElement(only as ReactElement<{ onClick: () => void }>, { onClick: () => set(true) })
      : only;
  },
  DialogContent: ({ children }: { children: ReactNode }) =>
    useContext(DialogState).open ? <div>{children}</div> : null,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
afterAll(() => {
  mock.module("@/features/versions/api", () => actualApi);
  mock.module("@/features/changes/api", () => actualChangesApi);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("@/features/scope/hooks/use-owners", () => actualOwners);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
});

const { OpenChangeProvider } = await import("./open-change");
const { ChangeControl } = await import("./change-control");
const { useEditSource } = await import("../hooks/use-change-write");
const { missingEditText, changedEditText } = await import("../hooks/use-change-write");

/** What the page under the top bar was given, in order; null is a page with no editor. */
let kinds: string[] = [];

/** An editor that, once the member typed, holds an unsaved edit of `origin`, as the real one does. */
function Editor({ kind, origin }: { kind: string; origin: EditOrigin | null }) {
  const [text, setText] = useState("");
  useEffect(() => {
    if (text === "") return;
    return holdUnsavedEdit(origin);
  }, [text, origin]);
  return (
    <div>
      <p>{`editor:${kind} text:${text}`}</p>
      <button onClick={() => setText("typed")}>Type</button>
      <button disabled>{kind === "change" ? "Save to change" : "Save"}</button>
    </div>
  );
}

function Page({ ready, origin }: { ready: ReadyScope; origin: EditOrigin | null }) {
  const source = useEditSource(ready);
  const kind = source?.kind ?? "null";
  useEffect(() => {
    kinds.push(kind);
  }, [kind]);
  return source === null ? <p>no editor</p> : <Editor kind={kind} origin={origin} />;
}

const READY = {
  scope: { kind: "PACK", packId: "p-1" },
  owners: OWNERS,
  version: null,
} as unknown as ReadyScope;
const EXISTING: EditOrigin = { path: ".agents/rules/a.md", sha256: "h-1", isNew: false };

function tree(origin: EditOrigin | null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={client}>
      <NuqsTestingAdapter searchParams="">
        <OpenChangeProvider>
          <ChangeControl />
          <Page ready={READY} origin={origin} />
        </OpenChangeProvider>
      </NuqsTestingAdapter>
    </QueryClientProvider>
  );
}

async function open(origin: EditOrigin | null, typed = true): Promise<Screen> {
  const screen = await renderLive(tree(origin));
  await screen.settle();
  if (typed) await screen.byText("Type")?.click();
  await screen.byText("Start change")?.click();
  return screen;
}

async function start(screen: Screen): Promise<void> {
  await screen.byText("Start")?.click();
  await screen.settle();
}

const alertOf = (screen: Screen): string | undefined =>
  screen.byAttribute("role", "alert")[0]?.text;

beforeEach(() => {
  userSeed += 1;
  user = `org-change-control-${userSeed}`;
  kinds = [];
  fake = { drafts: [], files: {}, created: [], createFails: false };
});

afterEach(cleanup);

describe("Start change with an unsaved edit", () => {
  test("of the page's own pack, it keeps the editor and its text and saves into the new change", async () => {
    const screen = await open(EXISTING);
    expect(screen.text()).toContain("editor:single text:typed");
    expect(hasUnsavedEdit()).toBe(true);

    await start(screen);

    expect(fake.created).toEqual(["c-new-1"]);
    expect(screen.text()).toContain("Change of Village");
    expect(screen.text()).toContain("editor:change text:typed");
    expect(screen.byText("Save to change")).not.toBeNull();
    expect(hasUnsavedEdit()).toBe(true);
    expect(kinds).toEqual(["single", "change"]);
  });

  test("of the page's own pack, it continues the member's newest change that holds the edited file as it was", async () => {
    fake.drafts = [{ id: "c-9", packId: "p-1" }];
    fake.files["c-9"] = [{ path: EXISTING.path, sha256: "h-1" }];
    const screen = await open(EXISTING);

    await start(screen);

    expect(fake.created).toEqual([]);
    expect(screen.text()).toContain("editor:change text:typed");
    expect(kinds).toEqual(["single", "change"]);
  });

  test("of text that is not one file's, such as allowlist rules, it keeps the editor across a continued change", async () => {
    fake.drafts = [{ id: "c-9", packId: "p-1" }];
    fake.files["c-9"] = [];
    const screen = await open(null);

    await start(screen);

    expect(alertOf(screen)).toBeUndefined();
    expect(screen.text()).toContain("editor:change text:typed");
    expect(kinds).toEqual(["single", "change"]);
  });

  test("of another pack, it stays blocked and starts nothing", async () => {
    const screen = await open(EXISTING);
    const select = screen.byAttribute("data-slot", "select")[0];
    await select?.change("p-2");

    expect(alertOf(screen)).toBe(UNSAVED_EDIT_BLOCKER);
    expect(screen.byText("Start")?.disabled).toBe(true);
    await start(screen);
    expect(fake.created).toEqual([]);
    expect(screen.text()).toContain("editor:single text:typed");
  });

  test("of another pack, it is allowed once nothing is unsaved", async () => {
    const screen = await open(EXISTING, false);
    await screen.byAttribute("data-slot", "select")[0]?.change("p-2");

    expect(alertOf(screen)).toBeUndefined();
    expect(screen.byText("Start")?.disabled).toBe(false);
  });

  test("the dialog preselects the page's pack when the URL names no scope", async () => {
    const screen = await open(EXISTING, false);

    expect(screen.byAttribute("data-slot", "select")[0]?.value).toBe("p-1");
    expect(screen.byText("Start")?.disabled).toBe(false);
  });
});

describe("a refused start never discards the unsaved edit", () => {
  async function expectKept(screen: Screen): Promise<void> {
    expect(screen.text()).toContain("editor:single text:typed");
    expect(screen.text()).not.toContain("Change of Village");
    expect(hasUnsavedEdit()).toBe(true);
    expect(kinds).toEqual(["single"]);
  }

  test("a continued change that no longer has the edited file", async () => {
    fake.drafts = [{ id: "c-9", packId: "p-1" }];
    fake.files["c-9"] = [];
    const screen = await open(EXISTING);

    await start(screen);

    expect(alertOf(screen)).toBe(missingEditText(EXISTING.path));
    await expectKept(screen);
  });

  test("a continued change that already changed the edited file", async () => {
    fake.drafts = [{ id: "c-9", packId: "p-1" }];
    fake.files["c-9"] = [{ path: EXISTING.path, sha256: "h-other" }];
    const screen = await open(EXISTING);

    await start(screen);

    expect(alertOf(screen)).toBe(changedEditText(EXISTING.path));
    await expectKept(screen);
  });

  test("a continued change that already holds the path of a new file", async () => {
    const created: EditOrigin = { path: ".agents/rules/new.md", sha256: null, isNew: true };
    fake.drafts = [{ id: "c-9", packId: "p-1" }];
    fake.files["c-9"] = [{ path: created.path, sha256: "h-2" }];
    const screen = await open(created);

    await start(screen);

    expect(alertOf(screen)).toBe(changedEditText(created.path));
    await expectKept(screen);
  });

  test("a continued change that does not hold the path of a new file takes the edit over", async () => {
    const created: EditOrigin = { path: ".agents/rules/new.md", sha256: null, isNew: true };
    fake.drafts = [{ id: "c-9", packId: "p-1" }];
    const screen = await open(created);

    await start(screen);

    expect(alertOf(screen)).toBeUndefined();
    expect(screen.text()).toContain("editor:change text:typed");
  });

  test("a change the server refuses to create", async () => {
    fake.createFails = true;
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    const screen = await open(EXISTING);

    await start(screen);

    expect(alertOf(screen)).toContain("The server refused the change.");
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
    await expectKept(screen);
  });
});
