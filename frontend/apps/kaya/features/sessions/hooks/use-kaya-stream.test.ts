import {
  afterAll,
  afterEach,
  beforeEach,
  describe,
  expect,
  jest,
  mock,
  spyOn,
  test,
} from "bun:test";

import { KayaApiError } from "@/lib/api/errors";

// The hook is driven without a DOM. React's `useState` and `useEffect` are
// replaced by a small runner that keeps the state slots, runs an effect when
// its dependencies change and runs its cleanup before that and on unmount.
// The collaborators that need a browser or a provider (the session, the
// identity, the query client, the access-loss store) are replaced by values
// the tests set; fetch serves streams the tests write to.
const realSetImmediate = globalThis.setImmediate;

type Effect = { deps: readonly unknown[]; cleanup: (() => void) | void };
const slots: unknown[] = [];
const effects: Effect[] = [];
let slotIndex = 0;
let effectIndex = 0;
let pendingEffects: Array<() => void> = [];

function useStateStub<T>(initial: T | (() => T)): [T, (next: T) => void] {
  const index = slotIndex++;
  if (!(index in slots)) {
    slots[index] = typeof initial === "function" ? (initial as () => T)() : initial;
  }
  return [slots[index] as T, (next: T) => void (slots[index] = next)];
}

function useEffectStub(run: () => (() => void) | void, deps: readonly unknown[]): void {
  const index = effectIndex++;
  const previous = effects[index];
  const changed =
    previous === undefined ||
    previous.deps.length !== deps.length ||
    previous.deps.some((dep: unknown, at: number) => !Object.is(dep, deps[at]));
  if (changed) {
    pendingEffects.push(() => {
      if (typeof previous?.cleanup === "function") previous.cleanup();
      effects[index] = { deps, cleanup: run() };
    });
  }
}

const invalidations: unknown[][] = [];
const queryClient = {
  invalidateQueries: (filters: { queryKey: unknown[] }) => {
    invalidations.push(filters.queryKey);
    return Promise.resolve();
  },
};
let organizationId = "org-1";
const reported: KayaApiError[] = [];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualReact = { ...(await import("react")) };
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualAuth = { ...(await import("next-auth/react")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualAccess = { ...(await import("@/lib/api/access")) };

mock.module("react", () => ({ ...actualReact, useState: useStateStub, useEffect: useEffectStub }));
mock.module("@tanstack/react-query", () => ({ ...actualQuery, useQueryClient: () => queryClient }));
mock.module("next-auth/react", () => ({
  ...actualAuth,
  getSession: async () => ({ accessToken: "token-1" }),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({ organization: { organizationId } }),
}));
mock.module("@/lib/api/access", () => ({
  ...actualAccess,
  reportAccessLoss: (error: KayaApiError) => void reported.push(error),
}));

afterAll(() => {
  mock.module("react", () => actualReact);
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("next-auth/react", () => actualAuth);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("@/lib/api/access", () => actualAccess);
});

const { useKayaStream, STREAM_FALLBACK_POLL_MS } = await import("./use-kaya-stream");
type LiveState = ReturnType<typeof useKayaStream>;
// Called outside a component on purpose: the stubs above stand in for React.
const runHook: () => LiveState = useKayaStream;

/** Renders the hook once: reads its state, then runs the effects that changed. */
function render(): LiveState {
  slotIndex = 0;
  effectIndex = 0;
  pendingEffects = [];
  const state = runHook();
  pendingEffects.forEach((run: () => void) => run());
  return state;
}

function unmount(): void {
  effects.forEach((effect: Effect) => {
    if (typeof effect.cleanup === "function") effect.cleanup();
  });
}

/** Lets the promise chains of the hook, and the streams it reads, run. */
async function settle(): Promise<void> {
  for (let round = 0; round < 8; round++) {
    await new Promise<void>((resolve) => realSetImmediate(resolve));
  }
}

type Served = { signal: AbortSignal | undefined; headers: Record<string, string>; url: string };
type FakeStream = { push: (text: string) => void; close: () => void };

const served: Served[] = [];
const streams: FakeStream[] = [];
let answers: Array<(signal: AbortSignal | undefined) => Response> = [];

function liveAnswer(): (signal: AbortSignal | undefined) => Response {
  return (signal) => {
    const encoder = new TextEncoder();
    let controller!: ReadableStreamDefaultController<Uint8Array>;
    const body = new ReadableStream<Uint8Array>({
      start(started) {
        controller = started;
      },
    });
    signal?.addEventListener("abort", () => controller.error(new Error("aborted")));
    streams.push({
      push: (text: string) => controller.enqueue(encoder.encode(text)),
      close: () => controller.close(),
    });
    return new Response(body, { status: 200 });
  };
}

function refusedAnswer(
  status: number,
  body: unknown
): (signal: AbortSignal | undefined) => Response {
  return () => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });
}

const HEARTBEAT = 'event: heartbeat\ndata: {"at":"2026-10-23T10:00:00Z"}\n\n';
function sessionUpdated(sessionId: string): string {
  return `event: session-updated\ndata: {"sessionId":"${sessionId}","lastEventAt":"2026-10-23T10:00:01Z"}\n\n`;
}

const realFetch = globalThis.fetch;
const realBase = process.env.API_BASE_URL;

beforeEach(() => {
  slots.length = 0;
  effects.length = 0;
  invalidations.length = 0;
  reported.length = 0;
  served.length = 0;
  streams.length = 0;
  answers = [];
  organizationId = "org-1";
  process.env.API_BASE_URL = "https://api.test";
  jest.useFakeTimers();
  globalThis.fetch = (async (url: string, init: RequestInit) => {
    served.push({
      url,
      headers: init.headers as Record<string, string>,
      signal: init.signal ?? undefined,
    });
    const answer = answers.shift() ?? liveAnswer();
    return answer(init.signal ?? undefined);
  }) as unknown as typeof fetch;
});

afterEach(() => {
  unmount();
  jest.useRealTimers();
  globalThis.fetch = realFetch;
  if (realBase === undefined) delete process.env.API_BASE_URL;
  else process.env.API_BASE_URL = realBase;
});

/** Starts the hook and gets its first stream live. */
async function startLive(): Promise<void> {
  render();
  await settle();
  streams[0]?.push(HEARTBEAT);
  await settle();
}

describe("useKayaStream: opening", () => {
  test("reads /stream with the bearer token and the organization, and is live after the first event", async () => {
    expect(render()).toBe("connecting");
    await settle();
    expect(served).toHaveLength(1);
    expect(served[0]?.url).toBe("https://api.test/kaya/stream");
    expect(served[0]?.headers).toEqual({
      Accept: "text/event-stream",
      "X-Organization-ID": "org-1",
      Authorization: "Bearer token-1",
    });
    expect(render()).toBe("connecting");

    streams[0]?.push(HEARTBEAT);
    await settle();
    expect(render()).toBe("live");
    // The first connection missed nothing, so it reloads nothing.
    expect(invalidations).toEqual([]);
  });
});

describe("useKayaStream: invalidation", () => {
  test("reloads the wall, its filters and each named session once per window", async () => {
    await startLive();
    streams[0]?.push(sessionUpdated("s1") + sessionUpdated("s2") + sessionUpdated("s1"));
    await settle();

    jest.advanceTimersByTime(999);
    expect(invalidations).toEqual([]);
    jest.advanceTimersByTime(1);
    expect(invalidations).toEqual([
      ["sessions", "org-1", "list"],
      ["sessions", "org-1", "filters"],
      ["sessions", "org-1", "detail", "s1"],
      ["sessions", "org-1", "detail", "s2"],
    ]);

    // A later change opens a new window.
    streams[0]?.push(sessionUpdated("s3"));
    await settle();
    jest.advanceTimersByTime(1000);
    expect(invalidations.slice(4)).toEqual([
      ["sessions", "org-1", "list"],
      ["sessions", "org-1", "filters"],
      ["sessions", "org-1", "detail", "s3"],
    ]);
  });

  test("ignores heartbeats, other events and a session-updated without a session", async () => {
    await startLive();
    streams[0]?.push(
      HEARTBEAT +
        "event: other\ndata: {}\n\n" +
        'event: session-updated\ndata: {"lastEventAt":"x"}\n\n'
    );
    await settle();
    jest.advanceTimersByTime(5000);
    expect(invalidations).toEqual([]);
  });

  test("reads an event that arrives in pieces", async () => {
    await startLive();
    streams[0]?.push("event: session-upd");
    streams[0]?.push('ated\ndata: {"sessionId":"s9"}\n');
    streams[0]?.push("\n");
    await settle();
    jest.advanceTimersByTime(1000);
    expect(invalidations).toContainEqual(["sessions", "org-1", "detail", "s9"]);
  });
});

describe("useKayaStream: reconnecting", () => {
  test("reopens at once when the server ends the stream, and reloads what it missed", async () => {
    await startLive();
    streams[0]?.close();
    await settle();
    expect(served).toHaveLength(2);
    expect(render()).toBe("live");

    streams[1]?.push(HEARTBEAT);
    await settle();
    expect(render()).toBe("live");
    expect(invalidations).toEqual([
      ["sessions", "org-1"],
    ]);
  });

  test("a stream that closes before its first event is broken, not finished", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    render();
    await settle();
    streams[0]?.close();
    await settle();
    expect(render()).toBe("paused");
    expect(served).toHaveLength(1);
    warn.mockRestore();
  });
});

describe("useKayaStream: the pause banner", () => {
  test("a refused stream pauses, reloads the lists every ten seconds and retries with a growing wait", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    answers = [
      refusedAnswer(500, { success: false, error: { code: "GEN_ERR_001", message: "down" } }),
      refusedAnswer(500, { success: false, error: { code: "GEN_ERR_001", message: "down" } }),
      refusedAnswer(500, { success: false, error: { code: "GEN_ERR_001", message: "down" } }),
    ];
    expect(render()).toBe("connecting");
    await settle();
    expect(render()).toBe("paused");
    expect(served).toHaveLength(1);
    expect(reported[0]?.message).toBe("down");
    expect(reported[0]?.status).toBe(500);

    // The first retry comes after 5 s and fails again; the next wait is 10 s.
    jest.advanceTimersByTime(5_000);
    await settle();
    expect(served).toHaveLength(2);
    expect(invalidations).toEqual([]);

    // The lists reload every ten seconds while the stream is down.
    jest.advanceTimersByTime(5_000);
    expect(invalidations).toEqual([
      ["sessions", "org-1"],
    ]);
    jest.advanceTimersByTime(4_999);
    await settle();
    expect(served).toHaveLength(2);
    jest.advanceTimersByTime(1);
    await settle();
    expect(served).toHaveLength(3);
    expect(render()).toBe("paused");
    warn.mockRestore();
  });

  test("the wait doubles to a one-minute maximum", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    answers = Array.from({ length: 8 }, () => refusedAnswer(503, "unavailable"));
    render();
    await settle();
    const waits = [5_000, 10_000, 20_000, 40_000, 60_000, 60_000];
    let expected = 1;
    for (const wait of waits) {
      expect(served).toHaveLength(expected);
      jest.advanceTimersByTime(wait - 1);
      await settle();
      expect(served).toHaveLength(expected);
      jest.advanceTimersByTime(1);
      await settle();
      expected += 1;
    }
    expect(served).toHaveLength(7);
    warn.mockRestore();
  });

  test("a recovered stream is live again, stops the polling and starts the wait over", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    answers = [refusedAnswer(500, { success: false, error: { code: "GEN_ERR_001" } })];
    render();
    await settle();
    expect(render()).toBe("paused");

    jest.advanceTimersByTime(5_000);
    await settle();
    expect(served).toHaveLength(2);
    streams[0]?.push(HEARTBEAT);
    await settle();
    expect(render()).toBe("live");

    // No polling, no further retry.
    const before = invalidations.length;
    jest.advanceTimersByTime(STREAM_FALLBACK_POLL_MS * 3);
    await settle();
    expect(invalidations).toHaveLength(before);
    expect(served).toHaveLength(2);

    // A later outage starts again at the first wait of 5 s.
    answers = [refusedAnswer(500, "x")];
    streams[0]?.close();
    await settle();
    expect(served).toHaveLength(3);
    expect(render()).toBe("paused");
    jest.advanceTimersByTime(5_000);
    await settle();
    expect(served).toHaveLength(4);
    warn.mockRestore();
  });
});

describe("useKayaStream: refusals reach the identity gate", () => {
  test("hands a membership refusal to the access-loss store with its code and values", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    answers = [
      refusedAnswer(403, {
        success: false,
        error: {
          code: "KAY_SEC_002",
          message: "Not a member",
          errorValues: { organizationId: "o" },
        },
      }),
    ];
    render();
    await settle();
    expect(reported).toHaveLength(1);
    expect(reported[0]?.isNotMember).toBe(true);
    expect(reported[0]?.errorValues).toEqual({ organizationId: "o" });
    expect(render()).toBe("paused");
    warn.mockRestore();
  });

  test("hands a 401 over and reads a refusal without a JSON body", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    answers = [refusedAnswer(401, "<html>unauthorized</html>")];
    render();
    await settle();
    expect(reported[0]?.status).toBe(401);
    expect(reported[0]?.code).toBeNull();
    expect(reported[0]?.message).toBe("Kaya answered 401 to the live stream");
    warn.mockRestore();
  });
});

describe("useKayaStream: organization and unmount", () => {
  test("a switch to another organization starts over on that organization's own stream", async () => {
    await startLive();
    expect(render()).toBe("live");

    organizationId = "org-2";
    expect(render()).toBe("connecting");
    expect(served[0]?.signal?.aborted).toBe(true);
    await settle();
    expect(served).toHaveLength(2);
    expect(served[1]?.headers["X-Organization-ID"]).toBe("org-2");

    streams[1]?.push(HEARTBEAT + sessionUpdated("s1"));
    await settle();
    expect(render()).toBe("live");
    jest.advanceTimersByTime(1000);
    expect(invalidations[0]).toEqual(["sessions", "org-2", "list"]);
  });

  test("unmounting closes the stream and cancels the pending reload and the retry", async () => {
    await startLive();
    streams[0]?.push(sessionUpdated("s1"));
    await settle();
    unmount();
    expect(served[0]?.signal?.aborted).toBe(true);
    jest.advanceTimersByTime(120_000);
    await settle();
    expect(invalidations).toEqual([]);
    expect(served).toHaveLength(1);
  });

  test("unmounting while paused stops the polling and the retry", async () => {
    const warn = spyOn(console, "warn").mockImplementation(() => undefined);
    answers = [refusedAnswer(500, "x")];
    render();
    await settle();
    unmount();
    jest.advanceTimersByTime(120_000);
    await settle();
    expect(invalidations).toEqual([]);
    expect(served).toHaveLength(1);
    warn.mockRestore();
  });
});
