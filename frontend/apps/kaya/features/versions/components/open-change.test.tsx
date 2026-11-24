import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import { focusManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";

import type { VersionDetailResponse } from "@/features/versions/schemas";
import { KayaApiError } from "@/lib/api/errors";
import { cleanup, renderLive, type Screen } from "@oba/shared/testing/live-render";

// The change is read through the real query cache; only the server answer and
// the identity are fixed, so a refetch on focus shows what another tab did.
const ME = "u-1";
let answer: () => Promise<{ data: VersionDetailResponse }>;
let reads = 0;
let userSeed = 0;
let user = "u-0";

function change(patch: Partial<VersionDetailResponse> = {}): VersionDetailResponse {
  return {
    id: "c-1",
    number: 3,
    packId: "p-1",
    status: "DRAFT",
    proposedBy: ME,
    baseVersionId: "v-0",
    files: [],
    ...patch,
  } as unknown as VersionDetailResponse;
}

const actualApi = { ...(await import("@/features/versions/api")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
mock.module("@/features/versions/api", () => ({
  ...actualApi,
  getVersion: () => {
    reads += 1;
    return answer();
  },
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: ME, memberships: [] },
    organization: { organizationId: user },
  }),
}));
afterAll(() => {
  mock.module("@/features/versions/api", () => actualApi);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
});

const { OpenChangeProvider, useOpenChange } = await import("./open-change");

function Probe() {
  const { change: open, changeId, setChangeId } = useOpenChange();
  return (
    <div>
      <p>{`open:${open?.id ?? "none"} stored:${changeId ?? "none"}`}</p>
      <button onClick={() => setChangeId("c-1")}>Remember</button>
    </div>
  );
}

function tree(client: QueryClient) {
  return (
    <QueryClientProvider client={client}>
      <OpenChangeProvider>
        <Probe />
      </OpenChangeProvider>
    </QueryClientProvider>
  );
}

function newClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
}

/** Another tab was in use and this one is focused again. */
async function refocus(screen: Screen): Promise<void> {
  await screen.act(() => focusManager.setFocused(false));
  await screen.act(() => focusManager.setFocused(true));
  await screen.settle();
}

beforeEach(() => {
  userSeed += 1;
  user = `org-open-change-${userSeed}`;
  reads = 0;
  answer = async () => ({ data: change() });
});

afterEach(async () => {
  focusManager.setFocused(undefined);
  await cleanup();
});

describe("OpenChangeProvider", () => {
  test("a remembered change of the member's own is the open change", async () => {
    const screen = await renderLive(tree(newClient()));
    expect(screen.text()).toContain("open:none stored:none");
    await screen.byText("Remember")?.click();
    await screen.settle();
    expect(screen.text()).toContain("open:c-1 stored:c-1");
  });

  test("a change submitted in another tab is no longer open after the window regains focus", async () => {
    const screen = await renderLive(tree(newClient()));
    await screen.byText("Remember")?.click();
    await screen.settle();
    expect(screen.text()).toContain("open:c-1");

    answer = async () => ({ data: change({ status: "IN_REVIEW" }) });
    await refocus(screen);

    expect(screen.text()).toContain("open:none stored:none");
    expect(reads).toBeGreaterThanOrEqual(2);
  });

  test("a change deleted in another tab is no longer open after the window regains focus", async () => {
    const screen = await renderLive(tree(newClient()));
    await screen.byText("Remember")?.click();
    await screen.settle();
    expect(screen.text()).toContain("open:c-1");

    answer = async () => {
      throw new KayaApiError("Not found", 404, "KAY_NF_001");
    };
    await refocus(screen);

    expect(screen.text()).toContain("open:none stored:none");
  });

  test("a change of the member that is still a draft stays open across a focus", async () => {
    const screen = await renderLive(tree(newClient()));
    await screen.byText("Remember")?.click();
    await screen.settle();
    await refocus(screen);
    expect(screen.text()).toContain("open:c-1 stored:c-1");
    expect(reads).toBeGreaterThanOrEqual(2);
  });

  test("another member's draft is forgotten", async () => {
    answer = async () => ({ data: change({ proposedBy: "someone-else" }) });
    const screen = await renderLive(tree(newClient()));
    await screen.byText("Remember")?.click();
    await screen.settle();
    expect(screen.text()).toContain("open:none stored:none");
  });
});
