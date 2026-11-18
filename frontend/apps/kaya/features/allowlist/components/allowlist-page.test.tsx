import { afterAll, describe, expect, mock, test } from "bun:test";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";

// The allowlist reads the server's parse of the settings file, the file's
// history and links, the members and the draft session through the query
// cache; those reads are replaced, the rest is real.
let settingsQuery: { data?: unknown; error: Error | null } = { error: null };
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualSettings = { ...(await import("../hooks/use-settings")) };
const actualChanges = { ...(await import("@/features/changes/hooks/use-changes")) };
const actualDependencies = { ...(await import("@/features/links/hooks/use-dependencies")) };
const actualMembers = { ...(await import("@/features/members/hooks/use-members")) };
const actualSession = { ...(await import("@/features/versions/hooks/use-draft-session")) };
mock.module("../hooks/use-settings", () => ({
  ...actualSettings,
  useSettings: () => ({ ...settingsQuery, refetch: async () => undefined }),
}));
mock.module("@/features/changes/hooks/use-changes", () => ({
  ...actualChanges,
  useFileHistory: () => ({ data: undefined, error: null }),
}));
mock.module("@/features/links/hooks/use-dependencies", () => ({
  ...actualDependencies,
  useDependencies: () => ({ data: { data: { linksTo: [{}], usedBy: [{}, {}] } }, error: null }),
}));
mock.module("@/features/members/hooks/use-members", () => ({
  ...actualMembers,
  useMembers: () => ({ data: undefined }),
}));
mock.module("@/features/versions/hooks/use-draft-session", () => ({
  ...actualSession,
  useDraftSession: () => ({ target: null, draftId: null, setDraftId: () => undefined }),
}));
afterAll(() => {
  mock.module("../hooks/use-settings", () => actualSettings);
  mock.module("@/features/changes/hooks/use-changes", () => actualChanges);
  mock.module("@/features/links/hooks/use-dependencies", () => actualDependencies);
  mock.module("@/features/members/hooks/use-members", () => actualMembers);
  mock.module("@/features/versions/hooks/use-draft-session", () => actualSession);
});

const { SettingsAllowlist } = await import("./allowlist-tabs");
const { allowlistTabAfter } = await import("./allowlist-tabs-data");

const settings = { versionId: "v1", path: ".claude/settings.json" } as ScopeFile;
const ready = {
  files: [],
  owners: [],
  edit: null,
  scope: { kind: "PACK", packId: "k-1" },
} as unknown as ReadyScope;

/** The allowlist as the page renders it; its open tab, `at` and `compare` live in the URL. */
function renderAllowlist(searchParams = ""): string {
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={searchParams}>
      <SettingsAllowlist ready={ready} settings={settings} />
    </NuqsTestingAdapter>
  );
}

function settingsOf(allow: string[]) {
  return {
    data: {
      success: true,
      data: [{ id: "st-1", versionId: "v1", sizeBytes: 40, allow, ask: [], deny: [], hooks: [] }],
    },
    error: null,
  };
}

describe("SettingsAllowlist", () => {
  test("shows the rules the server read from the version's settings file", () => {
    settingsQuery = settingsOf(["Bash(oba:*)"]);
    const html = renderAllowlist();
    expect(html).toContain('<code class="font-mono text-sm break-all">oba:*</code>');
  });

  test("counts the rules, the hooks and the file's links on their tabs", () => {
    settingsQuery = settingsOf(["Bash(oba:*)", "Read"]);
    const html = renderAllowlist();
    expect(html).toContain("Rules 2");
    expect(html).toContain("Hooks 0");
    expect(html).toContain("Links 3");
    expect(html).toContain(">History<");
    expect(html).toContain(">Content<");
    expect(html).toContain(">Checks<");
  });

  test("opens the Rules tab without a tab in the URL", () => {
    settingsQuery = settingsOf(["Bash(oba:*)"]);
    const html = renderAllowlist();
    expect(html).toContain("oba:*");
    expect(html).not.toContain("No hook is registered.");
  });

  test("a link with ?tab=hooks opens the Hooks tab, which links to the hook scripts", () => {
    settingsQuery = settingsOf(["Bash(oba:*)"]);
    const html = renderAllowlist("?tab=hooks");
    expect(html).toContain("No hook is registered.");
    expect(html).toContain('href="/rules?scope=pack%3Ak-1&amp;tab=hooks"');
    expect(html).not.toContain("oba:*");
  });

  test("an unknown tab in the URL opens Rules", () => {
    settingsQuery = settingsOf(["Bash(oba:*)"]);
    expect(renderAllowlist("?tab=other")).toContain("oba:*");
  });

  test("a failed read says so", () => {
    settingsQuery = { error: new Error("network down") };
    const html = renderAllowlist();
    expect(html).toContain("Could not load the allowlist");
    expect(html).toContain("network down");
  });
});

describe("allowlistTabAfter", () => {
  test("unsaved text in the open tab stays when the member declines to leave", () => {
    let asked = 0;
    const decline = () => {
      asked += 1;
      return false;
    };
    expect(allowlistTabAfter("hooks", decline)).toBeNull();
    expect(asked).toBe(1);
  });

  test("opens the picked tab when nothing is unsaved or the member agrees", () => {
    expect(allowlistTabAfter("hooks", () => true)).toBe("hooks");
    expect(allowlistTabAfter("content", () => true)).toBe("content");
  });

  test("an unknown tab is ignored without asking", () => {
    const never = () => {
      throw new Error("asked");
    };
    expect(allowlistTabAfter("other", never)).toBeNull();
    expect(allowlistTabAfter("settings", never)).toBeNull();
  });
});
