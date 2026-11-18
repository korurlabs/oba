import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ComponentProps, ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { EditSource } from "../hooks/use-change-write";
import type {
  EffectiveItemResponse,
  EffectiveOverrideResponse,
  Owner,
} from "@/features/scope/schemas";

// The note and the dialog run their writes through hooks and the API; those
// are replaced by recorders. The Button records its props so a test can press
// it, and the Dialog shows its content at once; the rendering, the status
// texts and the action logic are the real code. No DOM library is installed,
// so a press calls the recorded onClick and the effect is read from the
// recorders.
type Pressable = { label: string; onClick?: () => unknown; disabled?: boolean };
let buttons: Pressable[] = [];
let saves: unknown[] = [];
let removals: unknown[] = [];
let bases: unknown[] = [];
let clears: unknown[] = [];
// What the change-write hooks are told and answer; the writes themselves are recorders.
let source: EditSource | null = SINGLE_SOURCE();
let started: string[] = [];
let startRefusal: string | null = null;
let ran: { reason: string | undefined }[] = [];
let submitted: { id: string; number: number } | null = null;
function SINGLE_SOURCE(): EditSource {
  return { kind: "single", packId: "p-village", current: null };
}
function CHANGE_SOURCE(): EditSource {
  return { kind: "change", draft: { id: "d-1", packId: "p-village" } as never };
}
let versions: Record<string, { files: { path: string; mode: string }[] }> = {};
let contents: Record<string, { encoding: string; content: string }> = {};

function textOf(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(textOf).join("");
  return "";
}

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualButton = { ...(await import("@oba/ui/sky/button")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualDiff = { ...(await import("@/features/changes/components/diff-view")) };
const actualApi = { ...(await import("../api")) };
const actualWrite = { ...(await import("../hooks/use-change-write")) };
const actualVersions = { ...(await import("../hooks/use-versions")) };
const actualNavigation = { ...(await import("next/navigation")) };

mock.module("@oba/ui/sky/button", () => ({
  ...actualButton,
  Button: ({ children, onClick, disabled }: ComponentProps<"button">) => {
    buttons.push({ label: textOf(children), onClick: onClick as () => unknown, disabled });
    return <button disabled={disabled}>{children}</button>;
  },
}));
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({ children }: { children: ReactNode }) => <div data-dialog>{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
}));
mock.module("@/features/changes/components/diff-view", () => ({
  ...actualDiff,
  FileDiff: ({ file }: { file: { path: string } }) => <pre data-diff={file.path} />,
}));
mock.module("../api", () => ({
  ...actualApi,
  getVersion: async (id: string) => ({ data: { id, files: versions[id]?.files ?? [] } }),
  getVersionFile: async (id: string, path: string) => ({ data: contents[`${id}:${path}`] }),
}));
mock.module("../hooks/use-change-write", () => ({
  ...actualWrite,
  useEditSource: () => source,
  useChangeWrite: () => ({
    run: async (write: (draftId: string) => Promise<unknown>, reason?: string) => {
      ran.push({ reason });
      await write("d-1");
      return submitted;
    },
    startChange: async (packId: string) => {
      started.push(packId);
      if (startRefusal !== null) throw new Error(startRefusal);
      return "d-1";
    },
    isPending: false,
  }),
}));
mock.module("../hooks/use-versions", () => ({
  ...actualVersions,
  useSaveDraftFile: () => ({
    isPending: false,
    mutateAsync: async (input: unknown) => void saves.push(input),
  }),
  useDeleteDraftFile: () => ({
    isPending: false,
    mutateAsync: async (input: unknown) => void removals.push(input),
  }),
  useSetOverrideBase: () => ({
    isPending: false,
    mutateAsync: async (input: unknown) => void bases.push(input),
  }),
  useClearOverride: () => ({
    isPending: false,
    mutateAsync: async (input: unknown) => void clears.push(input),
  }),
  useVersionDiff: () => ({
    data: { data: [{ path: ".agents/rules/core.md" }, { path: ".agents/rules/other.md" }] },
    error: null,
    refetch: async () => undefined,
  }),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  usePathname: () => "/rules",
}));
afterAll(() => {
  mock.module("@oba/ui/sky/button", () => actualButton);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("@/features/changes/components/diff-view", () => actualDiff);
  mock.module("../api", () => actualApi);
  mock.module("../hooks/use-change-write", () => actualWrite);
  mock.module("../hooks/use-versions", () => actualVersions);
  mock.module("next/navigation", () => actualNavigation);
});

const { InheritedNote, ItemStatusBadges, OverrideNote } = await import("./override-status");

const pack = (id: string, name: string): Owner => ({
  id,
  name,
  parentPackId: null,
  currentVersionId: null,
  required: false,
  mountPaths: [],
  repositoryIds: [],
});
const OWNERS: Owner[] = [pack("p-org", "org"), pack("p-village", "village")];

const CORE = ".agents/rules/core.md";
const SKILL = ".agents/skills/deploy/";

function item(overrides: Partial<EffectiveItemResponse> = {}): EffectiveItemResponse {
  return {
    path: CORE,
    kind: "RULE",
    sourcePackId: "p-org",
    sourceVersionId: "v-org",
    own: false,
    locked: null,
    override: null,
    ...overrides,
  } as EffectiveItemResponse;
}
const OVERRIDE: EffectiveOverrideResponse = {
  basePackId: "p-org",
  baseVersionId: "v-old",
  baseItemVersion: 4,
  parentVersionId: "v-org",
  parentItemVersion: 5,
  status: "PARENT_UPDATED",
};

function file(path: string, overrides: Partial<ScopeFile> = {}): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-org",
    ownerId: "p-org",
    ...overrides,
  };
}
function ready(items: EffectiveItemResponse[]): ReadyScope {
  return {
    files: [],
    edit: null,
    origins: new Map(),
    effective: new Map(items.map((entry: EffectiveItemResponse) => [entry.path, entry])),
    inherited: true,
    owners: OWNERS,
    release: null,
    scope: { kind: "PACK", packId: "p-village" },
  } as unknown as ReadyScope;
}
const press = async (label: string) => {
  const button = buttons.find((entry: Pressable) => entry.label === label);
  if (!button?.onClick) throw new Error(`No button "${label}" was rendered.`);
  await button.onClick();
  // The handlers start their writes with `void`, so let the whole chain settle.
  await new Promise((resolve) => setTimeout(resolve, 0));
};

beforeEach(() => {
  buttons = [];
  saves = [];
  removals = [];
  bases = [];
  clears = [];
  source = SINGLE_SOURCE();
  started = [];
  startRefusal = null;
  ran = [];
  submitted = null;
  versions = {};
  contents = {};
});

describe("ItemStatusBadges", () => {
  test("a missing item and a plain inherited item show nothing", () => {
    expect(renderToStaticMarkup(<ItemStatusBadges item={undefined} owners={OWNERS} />)).toBe("");
    expect(renderToStaticMarkup(<ItemStatusBadges item={item()} owners={OWNERS} />)).toBe("");
  });

  test("a locked inherited item shows Locked with the pack that locks it", () => {
    const required = renderToStaticMarkup(
      <ItemStatusBadges item={item({ locked: "REQUIRED" })} owners={OWNERS} />
    );
    expect(required).toContain("Locked");
    expect(required).toContain('title="Required by org"');
    const guardrail = renderToStaticMarkup(
      <ItemStatusBadges item={item({ locked: "GUARDRAIL" })} owners={OWNERS} />
    );
    expect(guardrail).toContain('title="Guardrail, set by org"');
  });

  test("an override names the pack it replaces and, once the parent moved, says so", () => {
    const current = renderToStaticMarkup(
      <ItemStatusBadges
        item={item({ own: true, override: { ...OVERRIDE, status: "CURRENT" } })}
        owners={OWNERS}
      />
    );
    expect(current).toContain("Overrides org");
    expect(current).not.toContain("Parent updated");
    expect(current).not.toContain("Parent removed");

    const updated = renderToStaticMarkup(
      <ItemStatusBadges item={item({ own: true, override: OVERRIDE })} owners={OWNERS} />
    );
    expect(updated).toContain("Parent updated");

    const removed = renderToStaticMarkup(
      <ItemStatusBadges
        item={item({ own: true, override: { ...OVERRIDE, status: "PARENT_REMOVED" } })}
        owners={OWNERS}
      />
    );
    expect(removed).toContain("Parent removed");
    expect(removed).not.toContain("Parent updated");
  });

  test("an own item that overrides nothing shows nothing, even when locked is set", () => {
    const html = renderToStaticMarkup(
      <ItemStatusBadges item={item({ own: true, locked: "REQUIRED" })} owners={OWNERS} />
    );
    expect(html).toBe("");
  });
});

describe("InheritedNote render", () => {
  test("names the source pack, links to it and offers Override", () => {
    const html = renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([item()])} />);
    expect(html).toContain("Inherited from pack org.");
    expect(html).toContain("Open org");
    expect(html).toContain('href="/rules?scope=pack%3Ap-org"');
    expect(buttons.map((entry: Pressable) => entry.label)).toEqual(["Override"]);
  });

  test("an owner the list does not hold is named generically, without a link", () => {
    const html = renderToStaticMarkup(
      <InheritedNote file={file(CORE, { ownerId: "p-gone" })} ready={ready([item()])} />
    );
    expect(html).toContain("Inherited from an ancestor pack.");
    expect(html).not.toContain("Open ");
  });

  test("a locked item names the lock and offers no Override", () => {
    const html = renderToStaticMarkup(
      <InheritedNote file={file(CORE)} ready={ready([item({ locked: "REQUIRED" })])} />
    );
    expect(html).toContain("Required by org, so this pack cannot override it.");
    expect(buttons).toEqual([]);
  });

  test("an inherited symlink, an unknown item and an own item offer no Override", () => {
    renderToStaticMarkup(
      <InheritedNote file={file(CORE, { mode: "SYMLINK" })} ready={ready([item()])} />
    );
    renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([])} />);
    renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([item({ own: true })])} />);
    expect(buttons).toEqual([]);
  });
});

describe("InheritedNote Override action", () => {
  test("copies the item's regular files from the ancestor version into the started change", async () => {
    versions["v-org"] = {
      files: [
        { path: `${SKILL}SKILL.md`, mode: "FILE" },
        { path: `${SKILL}run.sh`, mode: "EXECUTABLE" },
        { path: `${SKILL}link`, mode: "SYMLINK" },
        { path: CORE, mode: "FILE" },
      ],
    };
    contents[`v-org:${SKILL}SKILL.md`] = { encoding: "UTF_8", content: "skill" };
    contents[`v-org:${SKILL}run.sh`] = { encoding: "UTF_8", content: "echo" };
    renderToStaticMarkup(
      <InheritedNote
        file={file(`${SKILL}SKILL.md`)}
        ready={ready([item({ path: SKILL, kind: "SKILL" })])}
      />
    );
    await press("Override");
    expect(started).toEqual(["p-village"]);
    expect(ran).toEqual([]);
    expect(saves).toEqual([
      {
        draftId: "d-1",
        data: { path: `${SKILL}SKILL.md`, encoding: "UTF_8", content: "skill" },
      },
      {
        draftId: "d-1",
        data: { path: `${SKILL}run.sh`, encoding: "UTF_8", content: "echo", executable: true },
      },
    ]);
  });

  test("a refusal to start the change writes nothing", async () => {
    startRefusal = "Submit or close your change of kagi first.";
    renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([item()])} />);
    await press("Override");
    expect(started).toEqual(["p-village"]);
    expect(saves).toEqual([]);
  });

  test("with a change of the pack open, Override saves into it", async () => {
    source = CHANGE_SOURCE();
    versions["v-org"] = { files: [{ path: CORE, mode: "FILE" }] };
    contents[`v-org:${CORE}`] = { encoding: "UTF_8", content: "core" };
    renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([item()])} />);
    await press("Override");
    expect(saves).toEqual([
      { draftId: "d-1", data: { path: CORE, encoding: "UTF_8", content: "core" } },
    ]);
  });

  test("with a change of another pack open, Override is disabled with the lock tip and writes nothing", async () => {
    source = { kind: "locked", openPackName: "kagi" };
    const html = renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([item()])} />);
    expect(buttons.find((entry: Pressable) => entry.label === "Override")?.disabled).toBe(true);
    expect(html).toContain('aria-label="Why is this disabled?"');
    expect(started).toEqual([]);
  });

  test("while the edit source loads, Override is disabled and shows no lock tip", () => {
    source = null;
    const html = renderToStaticMarkup(<InheritedNote file={file(CORE)} ready={ready([item()])} />);
    expect(buttons.find((entry: Pressable) => entry.label === "Override")?.disabled).toBe(true);
    expect(html).not.toContain("Why is this disabled?");
  });

  test("an item the ancestor gives without a version writes nothing", async () => {
    renderToStaticMarkup(
      <InheritedNote file={file(CORE)} ready={ready([item({ sourceVersionId: null })])} />
    );
    await press("Override");
    expect(saves).toEqual([]);
  });
});

describe("OverrideNote", () => {
  const own = (override: Partial<EffectiveOverrideResponse> | null) =>
    item({ own: true, override: override === null ? null : { ...OVERRIDE, ...override } });

  test("shows nothing for a plain own item, a current override or an unknown item", () => {
    for (const scope of [ready([own(null)]), ready([own({ status: "CURRENT" })]), ready([])]) {
      expect(renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={scope} />)).toBe("");
    }
  });

  test("an inherited item has no decision to make", () => {
    expect(renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([item()])} />)).toBe(
      ""
    );
  });

  test("a parent update says what moved and offers Review, with the three actions and the diff", () => {
    const html = renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    expect(html).toContain("Parent updated");
    expect(html).toContain("org updated to v5, you override v4.");
    expect(buttons.map((entry: Pressable) => entry.label)).toEqual([
      "Review",
      "Drop override",
      "Keep override",
      "Update override",
    ]);
    expect(html).toContain(`data-diff="${CORE}"`);
    expect(html).not.toContain("other.md");
  });

  test("a removed parent offers Keep as own only and no diff", () => {
    const html = renderToStaticMarkup(
      <OverrideNote file={file(CORE)} ready={ready([own({ status: "PARENT_REMOVED" })])} />
    );
    expect(html).toContain("Parent removed");
    expect(html).toContain("org no longer gives this item.");
    expect(buttons.map((entry: Pressable) => entry.label)).toEqual(["Review", "Keep as own"]);
    expect(html).not.toContain("data-diff");
  });

  test("Update override and Keep override move the override's base to the parent's version", async () => {
    source = CHANGE_SOURCE();
    renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    await press("Update override");
    await press("Keep override");
    const expected = { draftId: "d-1", data: { path: CORE, baseVersionId: "v-org" } };
    expect(bases).toEqual([expected, expected]);
    expect(removals).toEqual([]);
    expect(clears).toEqual([]);
  });

  test("Keep override without a parent version changes nothing", async () => {
    source = CHANGE_SOURCE();
    renderToStaticMarkup(
      <OverrideNote file={file(CORE)} ready={ready([own({ parentVersionId: null })])} />
    );
    await press("Keep override");
    expect(bases).toEqual([]);
  });

  test("Drop override deletes every file of the item from the change and nothing else", async () => {
    source = CHANGE_SOURCE();
    versions["d-1"] = {
      files: [
        { path: `${SKILL}SKILL.md`, mode: "FILE" },
        { path: `${SKILL}link`, mode: "SYMLINK" },
        { path: `${SKILL}run.sh`, mode: "EXECUTABLE" },
        { path: CORE, mode: "FILE" },
      ],
    };
    renderToStaticMarkup(
      <OverrideNote
        file={file(`${SKILL}SKILL.md`)}
        ready={ready([{ ...own({}), path: SKILL, kind: "SKILL" } as EffectiveItemResponse])}
      />
    );
    await press("Drop override");
    expect(removals).toEqual([
      { draftId: "d-1", path: `${SKILL}SKILL.md` },
      { draftId: "d-1", path: `${SKILL}run.sh` },
    ]);
    expect(bases).toEqual([]);
  });

  test("Keep as own clears the override record of the path", async () => {
    source = CHANGE_SOURCE();
    renderToStaticMarkup(
      <OverrideNote file={file(CORE)} ready={ready([own({ status: "PARENT_REMOVED" })])} />
    );
    await press("Keep as own");
    expect(clears).toEqual([{ draftId: "d-1", path: CORE }]);
    expect(bases).toEqual([]);
  });

  test("Update override whose change cannot be started writes nothing", async () => {
    startRefusal = "nope";
    renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    await press("Update override");
    expect(started).toEqual(["p-village"]);
    expect(bases).toEqual([]);
  });

  test("Update override starts the change of the pack even with no change open", async () => {
    renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    await press("Update override");
    expect(started).toEqual(["p-village"]);
    expect(ran).toEqual([]);
    expect(bases).toEqual([{ draftId: "d-1", data: { path: CORE, baseVersionId: "v-org" } }]);
  });

  test("with no change open, Keep, Drop and Keep as own each ask for a reason before sending a change", async () => {
    const html = renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    expect(html).toContain("Reason");
    expect(html).toContain("Each goes to review as a change of its own");
    await press("Keep override");
    await press("Drop override");
    expect(ran).toEqual([]);
    expect(bases).toEqual([]);
    expect(removals).toEqual([]);

    buttons = [];
    renderToStaticMarkup(
      <OverrideNote file={file(CORE)} ready={ready([own({ status: "PARENT_REMOVED" })])} />
    );
    await press("Keep as own");
    expect(ran).toEqual([]);
    expect(clears).toEqual([]);
  });

  test("inside an open change there is no reason field and each action goes into the change", async () => {
    source = CHANGE_SOURCE();
    const html = renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    expect(html).not.toContain(">Reason<");
    expect(html).toContain("Each goes into your open change.");
    await press("Keep override");
    expect(ran).toEqual([{ reason: undefined }]);
    expect(bases).toHaveLength(1);
  });

  test("with a change of another pack open, every decision is disabled with the lock tip", () => {
    source = { kind: "locked", openPackName: "kagi" };
    const html = renderToStaticMarkup(<OverrideNote file={file(CORE)} ready={ready([own({})])} />);
    for (const label of ["Drop override", "Keep override", "Update override"]) {
      expect(buttons.find((entry: Pressable) => entry.label === label)?.disabled).toBe(true);
    }
    expect(html).toContain('aria-label="Why is this disabled?"');
  });
});
