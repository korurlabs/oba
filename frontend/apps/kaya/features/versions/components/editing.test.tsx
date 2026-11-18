import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { EditSource, SubmittedChange } from "../hooks/use-change-write";
import type { Owner } from "@/features/scope/schemas";
import type { VersionDetailResponse } from "../schemas";

// The bar and the editor run mutations and read files through the query
// cache; those are replaced, the rendering and the rule checks are real.
let fileContent: string | null = null;

// Hoisted above the mocks, which read it when a test renders.
const MOUNTED: Owner = {
  id: "p-kagi",
  name: "kagi",
  parentPackId: null,
  currentVersionId: null,
  required: false,
  mountPaths: ["projects/kagi"],
  repositoryIds: ["r-1"],
};

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualVersions = { ...(await import("../hooks/use-versions")) };
const actualOwners = { ...(await import("@/features/scope/hooks/use-owners")) };
const actualEffective = { ...(await import("@/features/scope/hooks/use-effective")) };
const actualWrite = { ...(await import("../hooks/use-change-write")) };
const actualBody = { ...(await import("@/features/editor/components/document-body")) };
const actualReason = { ...(await import("@/components/reason-dialog")) };
let effectiveItems: unknown[] = [];

// What the editor asked its collaborators to do. The change-write hook is a
// recorder that runs the write it is given against the recorded mutations.
type Run = { source: EditSource | null; reason: string | undefined };
let runs: Run[] = [];
let mutations: unknown[] = [];
let submitted: SubmittedChange | null = null;
let refusal: Error | null = null;
let body: { onSave: () => void; onChange: (next: string) => void } | null = null;
let reasonDialogs: Record<string, unknown>[] = [];

mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useMutation: () => ({
    isPending: false,
    mutate: () => undefined,
    mutateAsync: async (input: unknown) => {
      mutations.push(input);
      return {};
    },
  }),
  useQueryClient: () => ({}),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: "user-me" },
    organization: { organizationId: "org-1", name: "Korur", role: "MEMBER" },
    switchOrganization: () => undefined,
    can: () => false,
  }),
}));
mock.module("@/features/scope/hooks/use-owners", () => ({
  ...actualOwners,
  useOwners: () => ({ owners: [MOUNTED], repositories: [], error: null, retry: () => undefined }),
}));
mock.module("@/features/scope/hooks/use-effective", () => ({
  ...actualEffective,
  usePackEffective: () => ({ data: { success: true, data: effectiveItems }, error: null }),
}));
mock.module("../hooks/use-versions", () => ({
  ...actualVersions,
  useVersion: () => ({ data: undefined, error: null, refetch: async () => undefined }),
  useVersionFile: (versionId: string) => ({
    data:
      versionId === "" || fileContent === null
        ? undefined
        : {
            success: true,
            data: { versionId, path: "p", mode: "FILE", encoding: "UTF_8", content: fileContent },
          },
    error: null,
    refetch: async () => undefined,
  }),
}));
mock.module("../hooks/use-change-write", () => ({
  ...actualWrite,
  useChangeWrite: (source: EditSource | null) => ({
    run: async (write: (draftId: string) => Promise<unknown>, reason?: string) => {
      runs.push({ source, reason });
      if (refusal !== null) throw refusal;
      if (source?.kind === "locked" || source === null) throw new Error("locked");
      await write("w-1");
      return source.kind === "single" ? submitted : null;
    },
    startChange: async () => "w-1",
    isPending: false,
  }),
}));
mock.module("@/features/editor/components/document-body", () => ({
  ...actualBody,
  DocumentBody: (props: { onSave: () => void; onChange: (next: string) => void }) => {
    body = props;
    return <div aria-busy="true" />;
  },
}));
mock.module("@/components/reason-dialog", () => ({
  ...actualReason,
  ReasonDialog: (props: Record<string, unknown>) => {
    reasonDialogs.push(props);
    return <div data-reason-dialog={String(props.title)} />;
  },
}));
afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("../hooks/use-change-write", () => actualWrite);
  mock.module("@/features/editor/components/document-body", () => actualBody);
  mock.module("@/components/reason-dialog", () => actualReason);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("../hooks/use-versions", () => actualVersions);
  mock.module("@/features/scope/hooks/use-owners", () => actualOwners);
  mock.module("@/features/scope/hooks/use-effective", () => actualEffective);
});

const { submitBlocker } = await import("./change-control");
const { FileEditor, liveEditOf, mayBeExecutable, saveBody, saveConfirmed, textAfterSave } =
  await import("./file-editor");
const {
  DISCARD_EDIT_QUESTION,
  UNSAVED_EDIT_BLOCKER,
  guardUnload,
  hasUnsavedEdit,
  holdUnsavedEdit,
  mayLeaveEdit,
  unsavedEditOrigin,
  mayLeavePage,
} = await import("./draft-context");
const { guardNavigation } = await import("@/components/layout/kaya-sidebar");

const DRAFT = {
  id: "d-6",
  packId: "k-1",
  number: 6,
  status: "DRAFT",
  baseVersionId: "v-5",
  proposedBy: "user-me",
  source: "MANUAL",
  executableChange: false,
  createdAt: "2026-10-24T08:00:00Z",
  files: [{ path: ".agents/rules/a.md", mode: "FILE", size: 5, sha256: "s" }],
} as VersionDetailResponse;

const disabledButton = (label: string) =>
  new RegExp(`<button[^>]*disabled=""[^>]*>${label}</button>`);
const enabledButton = (label: string) =>
  new RegExp(`<button(?![^>]*disabled="")[^>]*>${label}</button>`);

describe("FileEditor", () => {
  test("a new file outside the delivered paths cannot be created", () => {
    fileContent = null;
    const html = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: DRAFT }}
        file={null}
        initialPath=".agents/docs/x.md"
        initialContent="x"
      />
    );
    expect(html).toContain("Kaya delivers only AGENTS.md");
    expect(html).toMatch(disabledButton("Save to change"));
  });

  test("a draft of a pack mounted in a folder refuses a path outside its namespace", () => {
    fileContent = null;
    const mountedDraft = {
      ...DRAFT,
      packId: "p-kagi",
      files: [],
    } as VersionDetailResponse;
    const outside = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: mountedDraft }}
        file={null}
        initialPath=".agents/rules/b.md"
        initialContent="# b"
      />
    );
    expect(outside).toContain("outside the namespace of pack kagi");
    expect(outside).toMatch(disabledButton("Save to change"));
    const inside = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: mountedDraft }}
        file={null}
        initialPath="AGENTS.md"
        initialContent="# Kagi"
      />
    );
    expect(inside).not.toContain("outside the namespace");
    expect(inside).toMatch(enabledButton("Save to change"));
  });

  test("a rule of a mounted pack is refused until its paths lie under the mount folder", () => {
    fileContent = null;
    const mountedDraft = { ...DRAFT, packId: "p-kagi", files: [] } as VersionDetailResponse;
    const unscoped = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: mountedDraft }}
        file={null}
        initialPath=".agents/rules/kagi/api.md"
        initialContent="# Api"
      />
    );
    expect(unscoped).toContain(
      "The rule .agents/rules/kagi/api.md of pack kagi needs paths frontmatter whose every pattern lies under projects/kagi/"
    );
    expect(unscoped).toMatch(disabledButton("Save to change"));
    const scoped = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: mountedDraft }}
        file={null}
        initialPath=".agents/rules/kagi/api.md"
        initialContent={"---\npaths: projects/kagi/**\n---\n# Api"}
      />
    );
    expect(scoped).not.toContain("needs paths frontmatter");
    expect(scoped).toMatch(enabledButton("Save to change"));
  });

  test("a path whose item an ancestor gives locked is refused before the save", () => {
    fileContent = null;
    effectiveItems = [
      {
        path: ".agents/rules/guardrails/no-push.md",
        kind: "GUARDRAIL",
        sourcePackId: "p-kagi",
        sourceVersionId: "v-1",
        own: false,
        locked: "GUARDRAIL",
        override: null,
      },
    ];
    const html = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: DRAFT }}
        file={null}
        initialPath=".agents/rules/guardrails/no-push.md"
        initialContent="# No push"
      />
    );
    effectiveItems = [];
    expect(html).toContain(
      "The item .agents/rules/guardrails/no-push.md is locked: it is a guardrail of pack kagi, so no descendant can override it"
    );
    expect(html).toMatch(disabledButton("Save to change"));
  });

  test("a new rule can be created", () => {
    const html = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: DRAFT }}
        file={null}
        initialPath=".agents/rules/b.md"
        initialContent="# b"
      />
    );
    expect(html).not.toContain('aria-label="Rule checks"');
    expect(html).toMatch(enabledButton("Save to change"));
  });

  test("a path the draft already holds is refused", () => {
    const html = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "change", draft: DRAFT }}
        file={null}
        initialPath=".agents/rules/a.md"
        initialContent="# a"
      />
    );
    expect(html).toContain("open that file instead");
  });

  test("an existing file loads its editor with nothing to save yet", () => {
    fileContent = "# saved rule";
    const file = {
      path: ".agents/rules/a.md",
      mode: "FILE" as const,
      sha256: "s",
      size: 12,
      target: null,
      alwaysOn: true,
      versionId: "d-6",
      ownerId: "k-1",
    };
    const html = renderToStaticMarkup(
      <FileEditor source={{ kind: "change", draft: DRAFT }} file={file} />
    );
    // The code editor needs the browser, so the server renders its placeholder.
    expect(html).toContain('aria-busy="true"');
    expect(html).toMatch(disabledButton("Save to change"));
    expect(html).toContain("Delete file");
  });
});

describe("unsaved edits", () => {
  const LIVE = {
    path: ".agents/rules/a.md",
    mode: "FILE" as const,
    text: "# typed",
    replaces: null,
  };

  test("a change is not submitted while an editor holds unsaved text", () => {
    expect(submitBlocker(false, true)).toBe(UNSAVED_EDIT_BLOCKER);
    expect(submitBlocker(false, false)).toBeNull();
  });

  test("a stale base is named before the unsaved edit", () => {
    expect(submitBlocker(true, true)).toBe("Rebase the change onto the current version first.");
  });

  test("leaving a dirty editor asks first and follows the answer", () => {
    const asked: string[] = [];
    const answer = (value: boolean) => (question: string) => {
      asked.push(question);
      return value;
    };
    expect(mayLeaveEdit({ ...LIVE, dirty: true }, answer(false))).toBe(false);
    expect(mayLeaveEdit({ ...LIVE, dirty: true }, answer(true))).toBe(true);
    expect(asked).toEqual([DISCARD_EDIT_QUESTION, DISCARD_EDIT_QUESTION]);
  });

  test("leaving a clean editor or none asks nothing", () => {
    const never = () => {
      throw new Error("asked without an unsaved edit");
    };
    expect(mayLeaveEdit({ ...LIVE, dirty: false }, never)).toBe(true);
    expect(mayLeaveEdit(null, never)).toBe(true);
  });

  test("a reload with an unsaved edit is held for the browser's prompt", () => {
    let prevented = false;
    const event = {
      returnValue: undefined as unknown,
      preventDefault: () => {
        prevented = true;
      },
    };
    guardUnload(event as unknown as BeforeUnloadEvent);
    expect(prevented).toBe(true);
    expect(event.returnValue).toBe("");
  });

  test("a save clears the editor's text only when it still holds what was sent", () => {
    expect(textAfterSave("# sent", "# sent")).toBeNull();
    expect(textAfterSave(null, null)).toBeNull();
    expect(textAfterSave("# sent and more", "# sent")).toBe("# sent and more");
    expect(textAfterSave("# typed during the save", null)).toBe("# typed during the save");
  });
});

describe("edits without text", () => {
  test("an executable hook reports its unsaved edit as executable", () => {
    expect(
      liveEditOf({
        path: ".agents/hooks/guard.sh",
        executable: true,
        text: "#!/bin/sh",
        textless: false,
        savedPath: ".agents/hooks/guard.sh",
        dirty: true,
      })
    ).toEqual({
      path: ".agents/hooks/guard.sh",
      mode: "EXECUTABLE",
      text: "#!/bin/sh",
      replaces: null,
      dirty: true,
      origin: { path: ".agents/hooks/guard.sh", sha256: null, isNew: false },
    });
  });

  test("a renamed binary file reports its unsaved rename", () => {
    const live = liveEditOf({
      path: "assets/new.png",
      executable: false,
      text: null,
      textless: true,
      savedPath: "assets/old.png",
      dirty: true,
    });
    expect(live).toMatchObject({ text: null, replaces: "assets/old.png", dirty: true });
    expect(submitBlocker(false, live?.dirty === true)).toBe(UNSAVED_EDIT_BLOCKER);
  });

  test("a text file publishes its text, and nothing while the text loads", () => {
    const state = {
      path: ".agents/rules/a.md",
      executable: false,
      textless: false,
      savedPath: ".agents/rules/a.md",
      dirty: false,
    };
    expect(liveEditOf({ ...state, text: null })).toBeNull();
    expect(liveEditOf({ ...state, text: "# saved" })).toMatchObject({
      text: "# saved",
      dirty: false,
    });
  });
});

describe("the save request", () => {
  test("only a hook or a skill file other than SKILL.md may be executable", () => {
    expect(mayBeExecutable(".agents/hooks/guard.sh")).toBe(true);
    expect(mayBeExecutable(".agents/skills/deploy/run.sh")).toBe(true);
    expect(mayBeExecutable(".agents/skills/deploy/SKILL.md")).toBe(false);
    expect(mayBeExecutable(".agents/rules/a.md")).toBe(false);
  });

  test("sends the bit only where the server takes it, and no mode", () => {
    const base = { text: "x", binary: false, executable: true, overridesPath: null };
    expect(saveBody({ ...base, path: ".agents/rules/a.md" })).toEqual({
      path: ".agents/rules/a.md",
      encoding: "UTF_8",
      content: "x",
    });
    expect(saveBody({ ...base, path: ".agents/hooks/guard.sh" })).toEqual({
      path: ".agents/hooks/guard.sh",
      encoding: "UTF_8",
      content: "x",
      executable: true,
    });
    expect(
      saveBody({ ...base, path: "a.png", binary: true, overridesPath: ".agents/rules/b.md" })
    ).toEqual({
      path: "a.png",
      encoding: "BASE64",
      content: "x",
      overridesPath: ".agents/rules/b.md",
    });
  });
});

describe("the question before a save", () => {
  test("a save without a question goes ahead", async () => {
    expect(await saveConfirmed(undefined)).toBe(true);
  });

  test("a save goes ahead only on a yes, asked once per save", async () => {
    let asked = 0;
    const answer = (yes: boolean) => async () => {
      asked += 1;
      return yes;
    };
    expect(await saveConfirmed(answer(false))).toBe(false);
    expect(await saveConfirmed(answer(true))).toBe(true);
    expect(await saveConfirmed(() => true)).toBe(true);
    expect(asked).toBe(2);
  });
});

describe("leaving from outside the page", () => {
  const answer = (value: boolean, asked: string[]) => (question: string) => {
    asked.push(question);
    return value;
  };

  test("a menu link asks while an edit is unsaved and stays on a no", () => {
    const release = holdUnsavedEdit();
    const asked: string[] = [];
    let prevented = false;
    const event = {
      preventDefault: () => {
        prevented = true;
      },
    };
    try {
      guardNavigation(event, answer(false, asked));
      expect(prevented).toBe(true);
      expect(asked).toEqual([DISCARD_EDIT_QUESTION]);
      prevented = false;
      guardNavigation(event, answer(true, asked));
      expect(prevented).toBe(false);
    } finally {
      release();
    }
  });

  test("with every edit released, leaving asks nothing", () => {
    const first = holdUnsavedEdit();
    const second = holdUnsavedEdit();
    first();
    expect(mayLeavePage(() => false)).toBe(false);
    second();
    const never = () => {
      throw new Error("asked without an unsaved edit");
    };
    expect(mayLeavePage(never)).toBe(true);
    let prevented = false;
    guardNavigation({ preventDefault: () => (prevented = true) }, never);
    expect(prevented).toBe(false);
  });
});

const SINGLE: EditSource = { kind: "single", packId: "k-1", current: DRAFT };
const CHANGE: EditSource = { kind: "change", draft: DRAFT };
const LOCKED: EditSource = { kind: "locked", openPackName: "kagi" };
const LOCK_TIP = 'aria-label="Why is this disabled?"';
const NEW_RULE = { file: null, initialPath: ".agents/rules/b.md", initialContent: "# b" };

/** Lets the handlers that start their writes with `void` run to the end. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

function reset() {
  runs = [];
  mutations = [];
  submitted = null;
  refusal = null;
  body = null;
  reasonDialogs = [];
  fileContent = null;
}

describe("FileEditor per source", () => {
  test("inside the open change the button saves to the change", () => {
    reset();
    const html = renderToStaticMarkup(<FileEditor source={CHANGE} {...NEW_RULE} />);
    expect(html).toMatch(enabledButton("Save to change"));
    expect(html).toContain("Ctrl+S saves.");
    expect(html).not.toContain("Submit</button>");
    expect(reasonDialogs).toEqual([]);
    expect(html).not.toContain(LOCK_TIP);
  });

  test("without an open change the button submits and a reason dialog is mounted for it", () => {
    reset();
    const html = renderToStaticMarkup(<FileEditor source={SINGLE} {...NEW_RULE} />);
    expect(html).toMatch(enabledButton("Submit"));
    expect(html).toContain("Ctrl+S submits.");
    expect(html).not.toContain("Save to change");
    const dialog = reasonDialogs.find((props) => props.open !== undefined);
    expect(dialog).toMatchObject({
      open: false,
      title: "Submit this change for review",
      confirmLabel: "Submit",
      destructive: false,
    });
  });

  test("with a change of another pack open nothing can be submitted and the lock tip shows", () => {
    reset();
    fileContent = "# saved";
    const existing = {
      path: ".agents/rules/a.md",
      mode: "FILE" as const,
      sha256: "s",
      size: 7,
      target: null,
      alwaysOn: null,
      versionId: "v-5",
      ownerId: "k-1",
    };
    const html = renderToStaticMarkup(<FileEditor source={LOCKED} file={existing} />);
    expect(html).toMatch(disabledButton("Submit"));
    expect(html).toContain(LOCK_TIP);
    expect(html).toMatch(disabledButton("Delete file"));
    expect(html).not.toContain("Ctrl+S");
  });

  test("an existing file offers a delete that goes into the change or to review, by source", () => {
    reset();
    fileContent = "# saved";
    const existing = {
      path: ".agents/rules/a.md",
      mode: "FILE" as const,
      sha256: "s",
      size: 7,
      target: null,
      alwaysOn: null,
      versionId: "d-6",
      ownerId: "k-1",
    };
    renderToStaticMarkup(<FileEditor source={SINGLE} file={existing} />);
    expect(reasonDialogs.map((props) => props.title)).toContain("Delete this file");
    reasonDialogs = [];
    renderToStaticMarkup(<FileEditor source={CHANGE} file={existing} />);
    expect(reasonDialogs.map((props) => props.title)).not.toContain("Delete this file");
  });

  test("a path the pack already has is refused on a one-edit source, in the pack's words", () => {
    reset();
    const current = {
      ...DRAFT,
      files: [{ path: ".agents/rules/a.md", mode: "FILE", size: 5, sha256: "s" }],
    } as VersionDetailResponse;
    const html = renderToStaticMarkup(
      <FileEditor
        source={{ kind: "single", packId: "k-1", current }}
        file={null}
        initialPath=".agents/rules/a.md"
        initialContent="# a"
      />
    );
    expect(html).toContain("The pack has a file at this path; open that file instead.");
    expect(html).toMatch(disabledButton("Submit"));
  });
});

describe("FileEditor saving", () => {
  test("Ctrl+S inside the open change writes the file into it and reports the path", async () => {
    reset();
    const saved: string[] = [];
    renderToStaticMarkup(
      <FileEditor source={CHANGE} {...NEW_RULE} onSaved={(path: string) => saved.push(path)} />
    );
    body?.onSave();
    await settle();
    expect(runs).toEqual([{ source: CHANGE, reason: undefined }]);
    expect(mutations).toEqual([
      {
        draftId: "w-1",
        data: { path: ".agents/rules/b.md", encoding: "UTF_8", content: "# b" },
      },
    ]);
    expect(saved).toEqual([".agents/rules/b.md"]);
  });

  test("Ctrl+S without an open change sends nothing yet: it asks for the reason first", async () => {
    reset();
    renderToStaticMarkup(<FileEditor source={SINGLE} {...NEW_RULE} />);
    body?.onSave();
    await settle();
    expect(runs).toEqual([]);
    expect(mutations).toEqual([]);
  });

  test("the reason sends the edit as a one-edit change and reports the submitted version", async () => {
    reset();
    submitted = { id: "c-9", number: 9 };
    const reported: SubmittedChange[] = [];
    renderToStaticMarkup(
      <FileEditor
        source={SINGLE}
        {...NEW_RULE}
        onSubmitted={(change: SubmittedChange) => reported.push(change)}
      />
    );
    const dialog = reasonDialogs.find((props) => props.open !== undefined);
    await (dialog?.onConfirm as (reason: string) => Promise<void>)("Allow the helper rule");
    expect(runs).toEqual([{ source: SINGLE, reason: "Allow the helper rule" }]);
    expect(mutations).toHaveLength(1);
    expect(reported).toEqual([{ id: "c-9", number: 9 }]);
  });

  test("a refused one-edit change reaches the reason dialog, which keeps its reason, and reports nothing", async () => {
    reset();
    refusal = new Error("Another change of this pack is in review.");
    const reported: SubmittedChange[] = [];
    renderToStaticMarkup(
      <FileEditor
        source={SINGLE}
        {...NEW_RULE}
        onSubmitted={(change: SubmittedChange) => reported.push(change)}
      />
    );
    const dialog = reasonDialogs.find((props) => props.open !== undefined);
    const confirm = dialog?.onConfirm as (reason: string) => Promise<void>;
    await expect(confirm("A reason that is long enough")).rejects.toThrow("in review");
    expect(reported).toEqual([]);
  });

  test("with a change of another pack open Ctrl+S writes nothing", async () => {
    reset();
    renderToStaticMarkup(<FileEditor source={LOCKED} {...NEW_RULE} />);
    body?.onSave();
    await settle();
    expect(runs).toEqual([]);
    expect(mutations).toEqual([]);
  });

  test("a save the page does not confirm writes nothing, whatever the source", async () => {
    reset();
    for (const source of [CHANGE, SINGLE]) {
      renderToStaticMarkup(<FileEditor source={source} {...NEW_RULE} onBeforeSave={() => false} />);
      body?.onSave();
      await settle();
    }
    expect(runs).toEqual([]);
    expect(mutations).toEqual([]);
  });
});

describe("the origin of an unsaved edit", () => {
  const state = {
    path: ".agents/rules/a.md",
    executable: false,
    text: "# typed",
    textless: false,
    dirty: true,
  };

  test("an existing file publishes its saved path and the hash it loaded", () => {
    expect(
      liveEditOf({ ...state, savedPath: ".agents/rules/a.md", savedSha256: "h-1" })?.origin
    ).toEqual({
      path: ".agents/rules/a.md",
      sha256: "h-1",
      isNew: false,
    });
  });

  test("a renamed file keeps the path it was saved at", () => {
    const live = liveEditOf({
      ...state,
      path: ".agents/rules/renamed.md",
      savedPath: ".agents/rules/a.md",
      savedSha256: "h-1",
    });
    expect(live?.origin).toEqual({ path: ".agents/rules/a.md", sha256: "h-1", isNew: false });
    expect(live?.replaces).toBe(".agents/rules/a.md");
  });

  test("a new file publishes its path with no hash", () => {
    expect(liveEditOf({ ...state, savedPath: null })?.origin).toEqual({
      path: ".agents/rules/a.md",
      sha256: null,
      isNew: true,
    });
  });

  test("an open unsaved edit reports its origin until it is released", () => {
    expect(hasUnsavedEdit()).toBe(false);
    expect(unsavedEditOrigin()).toBeNull();
    const origin = { path: ".agents/rules/a.md", sha256: "h-1", isNew: false };
    const release = holdUnsavedEdit(origin);
    expect(hasUnsavedEdit()).toBe(true);
    expect(unsavedEditOrigin()).toEqual(origin);
    release();
    expect(hasUnsavedEdit()).toBe(false);
    expect(unsavedEditOrigin()).toBeNull();
  });

  test("an edit that is not one file's text, such as allowlist rule rows, holds no origin", () => {
    const release = holdUnsavedEdit();
    expect(hasUnsavedEdit()).toBe(true);
    expect(unsavedEditOrigin()).toBeNull();
    release();
  });
});
