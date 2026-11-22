import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The tab state is URL state. nuqs is replaced by a stub that returns the
// requested value and records the setter calls, so the test reads the fallback
// rule of the hook and not the router.
let requested: string;
const setterCalls: string[] = [];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualNuqs = { ...(await import("nuqs")) };

mock.module("nuqs", () => ({
  ...actualNuqs,
  useQueryState: () => [requested, (value: string) => setterCalls.push(value)],
}));

afterAll(() => {
  mock.module("nuqs", () => actualNuqs);
});

const { useOrgDetailTab } = await import("./use-org-detail-tab");

type TabState = ReturnType<typeof useOrgDetailTab>;

function readTab(canManage: boolean): TabState {
  let state: TabState | undefined;
  function Probe() {
    state = useOrgDetailTab({ canManage });
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return state!;
}

beforeEach(() => {
  requested = "detail";
  setterCalls.length = 0;
});

describe("useOrgDetailTab", () => {
  test.each(["detail", "members", "invitations", "legalEntities", "groups", "access"])(
    "a manager keeps the requested tab %s",
    (tab) => {
      requested = tab;

      expect(readTab(true).activeTab).toBe(tab);
    }
  );

  test.each(["groups", "access"])("a non-manager requesting %s falls back to detail", (tab) => {
    requested = tab;

    expect(readTab(false).activeTab).toBe("detail");
  });

  test.each(["detail", "members", "invitations", "legalEntities"])(
    "a non-manager keeps the open tab %s",
    (tab) => {
      requested = tab;

      expect(readTab(false).activeTab).toBe(tab);
    }
  );

  test("setTab passes the tab on to the URL state", () => {
    readTab(true).setTab("access");

    expect(setterCalls).toEqual(["access"]);
  });
});
