import { describe, expect, it, mock } from "bun:test";
import type {
  RealtimeConnection,
  RealtimeConnectionOptions,
  RealtimeTicketResult,
} from "@oba/shared/realtime";

// The same stubs as lib/__tests__/api.test.ts: lib/realtime.ts loads lib/api.ts,
// which reads the env, SecureStore and Keycloak discovery at import.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { currentState: "active", addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("@react-native-community/netinfo", () => ({
  default: { addEventListener: () => () => undefined },
}));

const secureStore = new Map<string, string>();
mock.module("expo-secure-store", () => ({
  getItemAsync: async (key: string) => secureStore.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => {
    secureStore.set(key, value);
  },
  deleteItemAsync: async (key: string) => {
    secureStore.delete(key);
  },
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kaizen-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kaizenWebUrl: "https://kaizen.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

const { ApiError } = await import("@/lib/api");
const { BACKGROUND_DISCONNECT_MS, NATIVE_CLIENT_CONFIG, createRealtimeHub, ticketFailure } =
  await import("@/lib/realtime");

const TOPIC = "/topic/organizations/org-1/chat";

interface FakeConnection extends RealtimeConnection {
  options: RealtimeConnectionOptions;
  calls: string[];
  destinations: Set<string>;
}

function setup(overrides: { refreshToken?: () => Promise<boolean> } = {}) {
  const connections: FakeConnection[] = [];
  const timers: { callback: () => void; ms: number; cleared: boolean }[] = [];
  const tickets: string[] = [];
  const refreshToken = mock(overrides.refreshToken ?? (async () => true));
  const hub = createRealtimeHub({
    brokerUrl: "wss://api.test/ws",
    createConnection: (options: RealtimeConnectionOptions): RealtimeConnection => {
      const calls: string[] = [];
      const destinations = new Set<string>();
      const connection: FakeConnection = {
        options,
        calls,
        destinations,
        start: () => calls.push("start"),
        stop: () => calls.push("stop"),
        retry: () => calls.push("retry"),
        addDestination: (destination: string) => destinations.add(destination),
        removeDestination: (destination: string) => destinations.delete(destination),
        publish: () => true,
      };
      connections.push(connection);
      return connection;
    },
    requestTicket: async (organizationId: string): Promise<RealtimeTicketResult> => {
      tickets.push(organizationId);
      return { ticket: "ticket" };
    },
    refreshToken,
    setTimer: (callback: () => void, ms: number) => {
      timers.push({ callback, ms, cleared: false });
      return timers.length as unknown as ReturnType<typeof setTimeout>;
    },
    clearTimer: (timer: ReturnType<typeof setTimeout>) => {
      const entry = timers[(timer as unknown as number) - 1];
      if (entry) entry.cleared = true;
    },
  });
  return { hub, connections, timers, tickets, refreshToken };
}

describe("ticketFailure", () => {
  it("takes the session offline on 403 and 404 and asks for a token on 401", () => {
    expect(ticketFailure(new ApiError("refused", { status: 403, kind: "forbidden" }))).toEqual({
      ticket: null,
      errorCode: "RTM_SEC_002",
    });
    expect(ticketFailure(new ApiError("gone", { status: 404, kind: "not-found" })).errorCode).toBe(
      "RTM_SEC_002"
    );
    expect(
      ticketFailure(new ApiError("expired", { status: 401, kind: "unauthorized" })).errorCode
    ).toBe("RTM_SEC_001");
  });

  it("leaves other failures to the backoff", () => {
    expect(
      ticketFailure(new ApiError("down", { status: 503, kind: "server" })).errorCode
    ).toBeNull();
    expect(ticketFailure(new Error("network")).errorCode).toBeNull();
  });
});

describe("createRealtimeHub", () => {
  it("opens one session with the native frame settings and shares it between owners", () => {
    const { hub, connections } = setup();
    const releaseA = hub.acquire("org-1");
    const releaseB = hub.acquire("org-1");
    expect(connections).toHaveLength(1);
    const connection = connections[0]!;
    expect(connection.options.clientConfig).toEqual(NATIVE_CLIENT_CONFIG);
    expect(NATIVE_CLIENT_CONFIG).toEqual({
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
    });
    expect(connection.calls).toEqual(["start"]);
    releaseA();
    expect(connection.calls).toEqual(["start"]);
    releaseB();
    expect(connection.calls).toEqual(["start", "stop"]);
  });

  it("closes the old organization's session before opening the new one", () => {
    const { hub, connections } = setup();
    hub.acquire("org-1");
    hub.acquire("org-2");
    expect(connections).toHaveLength(2);
    expect(connections[0]!.calls).toEqual(["start", "stop"]);
    expect(connections[1]!.options.organizationId).toBe("org-2");
  });

  it("delivers frames to the destination's handlers and keeps them across sessions", () => {
    const { hub, connections } = setup();
    const received: unknown[] = [];
    const unsubscribe = hub.subscribe(TOPIC, (body) => received.push(body));
    hub.acquire("org-1");
    const connection = connections[0]!;
    expect(connection.destinations.has(TOPIC)).toBe(true);
    connection.options.onFrame({ destination: TOPIC, body: { type: "X" } } as never);
    expect(received).toEqual([{ type: "X" }]);
    unsubscribe();
    expect(connection.destinations.has(TOPIC)).toBe(false);
  });

  it("counts a re-sync on every CONNECTED", () => {
    const { hub, connections } = setup();
    hub.acquire("org-1");
    const connection = connections[0]!;
    connection.options.onStatus("connected", null);
    connection.options.onConnected();
    expect(hub.getSnapshot()).toMatchObject({ status: "connected", resyncCount: 1 });
  });

  it("retries when the network comes back", () => {
    const { hub, connections } = setup();
    hub.acquire("org-1");
    hub.networkChanged(false);
    expect(hub.getSnapshot().networkOnline).toBe(false);
    hub.networkChanged(true);
    expect(hub.getSnapshot().networkOnline).toBe(true);
    expect(connections[0]!.calls).toEqual(["start", "retry"]);
  });

  it("pauses after the background delay and starts again in the foreground", () => {
    const { hub, connections, timers } = setup();
    hub.acquire("org-1");
    hub.appStateChanged("background");
    expect(timers[0]?.ms).toBe(BACKGROUND_DISCONNECT_MS);
    timers[0]!.callback();
    expect(connections[0]!.calls).toEqual(["start", "stop"]);
    hub.appStateChanged("active");
    expect(connections[0]!.calls).toEqual(["start", "stop", "start"]);
  });

  it("keeps the session when the app returns before the delay", async () => {
    const { hub, connections, timers } = setup();
    hub.acquire("org-1");
    hub.appStateChanged("background");
    hub.appStateChanged("active");
    await Promise.resolve();
    expect(timers[0]?.cleared).toBe(true);
    expect(connections[0]!.calls).toEqual(["start", "retry"]);
  });

  it("retries a refused session only with a fresh token", async () => {
    const { hub, connections, refreshToken } = setup({ refreshToken: async () => false });
    hub.acquire("org-1");
    const connection = connections[0]!;
    connection.options.onStatus("offline", "RTM_SEC_002");
    await hub.retry();
    expect(refreshToken).toHaveBeenCalledTimes(1);
    expect(connection.calls).toEqual(["start"]);
  });

  it("refreshes the token before the next ticket after an expired session", async () => {
    const { hub, connections, refreshToken, tickets } = setup();
    hub.acquire("org-1");
    const connection = connections[0]!;
    connection.options.onStatus("reconnecting", "RTM_SEC_001");
    const result = await connection.options.getTicket(new AbortController().signal);
    expect(refreshToken).toHaveBeenCalledTimes(1);
    expect(tickets).toEqual(["org-1"]);
    expect(result.ticket).toBe("ticket");
  });
});

describe("reconnecting on foreground", () => {
  it("waits 30 seconds in the background before it disconnects", () => {
    expect(BACKGROUND_DISCONNECT_MS).toBe(30_000);
  });

  it("asks for a fresh ticket for every session start, never reusing one", async () => {
    const { hub, connections, timers, tickets } = setup();
    hub.acquire("org-1");
    const connection = connections[0]!;
    await connection.options.getTicket(new AbortController().signal);
    hub.appStateChanged("background");
    timers[0]!.callback();
    hub.appStateChanged("active");
    await connection.options.getTicket(new AbortController().signal);
    expect(connection.calls).toEqual(["start", "stop", "start"]);
    expect(tickets).toEqual(["org-1", "org-1"]);
  });

  it("asks for no ticket while the expired session's token cannot be refreshed", async () => {
    const { hub, connections, refreshToken, tickets } = setup({ refreshToken: async () => false });
    hub.acquire("org-1");
    const connection = connections[0]!;
    connection.options.onStatus("reconnecting", "RTM_SEC_001");
    const result = await connection.options.getTicket(new AbortController().signal);
    expect(refreshToken).toHaveBeenCalledTimes(1);
    expect(result.ticket).toBeNull();
    expect(tickets).toEqual([]);
  });

  it("refreshes the token only once for the next tickets after RTM_SEC_001", async () => {
    const { hub, connections, refreshToken, tickets } = setup();
    hub.acquire("org-1");
    const connection = connections[0]!;
    connection.options.onStatus("reconnecting", "RTM_SEC_001");
    await connection.options.getTicket(new AbortController().signal);
    await connection.options.getTicket(new AbortController().signal);
    expect(refreshToken).toHaveBeenCalledTimes(1);
    expect(tickets).toEqual(["org-1", "org-1"]);
  });
});

describe("refetch after a reconnect", () => {
  it("counts a re-sync on every reconnect and on an unreadable frame", () => {
    const { hub, connections } = setup();
    hub.acquire("org-1");
    const connection = connections[0]!;
    connection.options.onConnected();
    connection.options.onConnected();
    expect(hub.getSnapshot().resyncCount).toBe(2);
    const original = console.error;
    console.error = () => undefined;
    try {
      connection.options.onMalformedFrame?.(new Error("bad frame"));
    } finally {
      console.error = original;
    }
    expect(hub.getSnapshot().resyncCount).toBe(3);
  });

  it("ignores a re-sync of a session that was replaced", () => {
    const { hub, connections } = setup();
    hub.acquire("org-1");
    hub.acquire("org-2");
    connections[0]!.options.onConnected();
    expect(hub.getSnapshot().resyncCount).toBe(0);
  });
});
