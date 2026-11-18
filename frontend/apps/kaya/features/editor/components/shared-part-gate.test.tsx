import { afterAll, describe, expect, mock, test } from "bun:test";
import { isValidElement, type ReactElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { DependencyResponse } from "@/features/links/schemas";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { BeforeSave } from "@/features/versions/components/file-editor";

// The gate reads which skills share the part through the query cache; that
// read is replaced, the gate's decision and its rendering are real.
let dependencies: DependencyResponse | undefined;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualDependencies = { ...(await import("@/features/links/hooks/use-dependencies")) };
mock.module("@/features/links/hooks/use-dependencies", () => ({
  ...actualDependencies,
  useDependencies: () => ({
    data: dependencies === undefined ? undefined : { success: true, data: dependencies },
    error: null,
  }),
}));
afterAll(() => {
  mock.module("@/features/links/hooks/use-dependencies", () => actualDependencies);
});

const { SharedPartGate } = await import("./shared-part-gate");

const PART = ".agents/skills/kaizen/workflows/sprint.md";

function file(path: string): ScopeFile {
  return {
    path,
    mode: "FILE",
    sha256: null,
    size: null,
    target: null,
    alwaysOn: null,
    versionId: "v-1",
    ownerId: "k-1",
  };
}

function skill(name: string) {
  return {
    path: `.agents/skills/${name}/SKILL.md`,
    kind: "SKILL" as const,
    relation: "SKILL_PART" as const,
    status: "FOUND" as const,
    versionId: "v-1",
  };
}

/** The gate around a stand-in editor, with the question it handed the editor. */
function render(path: string): { html: string; asks: BeforeSave | undefined } {
  let asks: BeforeSave | undefined;
  const html = renderToStaticMarkup(
    <SharedPartGate file={file(path)} files={[file(path)]}>
      {(onBeforeSave: BeforeSave | undefined) => {
        asks = onBeforeSave;
        return <textarea aria-label="editor" />;
      }}
    </SharedPartGate>
  );
  return { html, asks };
}

/**
 * The element the gate renders, as React reconciles it: the root's type and
 * each child slot's type, with an empty slot as null. The gate runs as the
 * body of a probe component, so its hooks have a component to belong to.
 */
function shape(path: string): { root: unknown; slots: unknown[] } {
  let tree: ReactNode = null;
  function Probe() {
    tree = SharedPartGate({
      file: file(path),
      files: [file(path)],
      children: () => <textarea aria-label="editor" />,
    });
    return null;
  }
  renderToStaticMarkup(<Probe />);
  const root = tree as unknown as ReactElement<{ children: ReactNode }>;
  return {
    root: root.type,
    slots: ([] as ReactNode[])
      .concat(root.props.children)
      .map((slot: ReactNode) => (isValidElement(slot) ? slot.type : null)),
  };
}

describe("SharedPartGate", () => {
  test("a shared part opens in the editor at once, and its save asks first", () => {
    dependencies = {
      path: PART,
      kind: "WORKFLOW",
      linksTo: [],
      usedBy: [skill("kaizen"), skill("kagi")],
    };
    const { html, asks } = render(PART);
    expect(html).toContain('<textarea aria-label="editor">');
    expect(html).toContain("The skills kagi and kaizen share this part.");
    expect(html).not.toContain("Edit the shared part");
    expect(asks).toBeDefined();
  });

  test("a file that is no shared part saves without a question", () => {
    dependencies = { path: PART, kind: "WORKFLOW", linksTo: [], usedBy: [skill("kaizen")] };
    const own = render(PART);
    expect(own.asks).toBeUndefined();
    expect(own.html).toContain('<textarea aria-label="editor"></textarea>');
    expect(own.html).not.toContain('role="note"');
    dependencies = undefined;
    expect(render(".agents/rules/a.md").asks).toBeUndefined();
  });

  test("the editor keeps its place when a loading part turns out to be one skill's own", () => {
    dependencies = undefined;
    const loading = shape(PART);
    dependencies = { path: PART, kind: "WORKFLOW", linksTo: [], usedBy: [skill("kaizen")] };
    const own = shape(PART);
    expect(own.root).toBe(loading.root);
    expect(own.slots.length).toBe(loading.slots.length);
    expect(own.slots[1]).toBe("textarea");
    expect(loading.slots[1]).toBe("textarea");
  });
});
