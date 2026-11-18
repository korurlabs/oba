import NetInfo, { type NetInfoState } from "@react-native-community/netinfo";
import { useEffect, useRef, useSyncExternalStore } from "react";
import { AppState, type AppStateStatus } from "react-native";
import { z } from "zod";
import {
  assertRealtimeSubscribable,
  createRealtimeConnection,
  describeError,
  realtimeBrokerUrl,
  RTM_SEC_001,
  RTM_SEC_002,
  type RealtimeClientConfig,
  type RealtimeConnection,
  type RealtimeConnectionOptions,
  type RealtimeFrame,
  type RealtimeStatus,
  type RealtimeTicketResult,
} from "@oba/shared/realtime";

import { api, isApiError, ORGANIZATION_HEADER, readData, refreshAccessToken } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { ENV } from "@/lib/env";

/*
 * The app's one realtime connection to the Oba STOMP broker, kept in a module
 * store so every screen shares it. The shared client owns the reconnect
 * policy; this module owns what a phone adds to it: the session pauses 30 s
 * after the app leaves the foreground, and `retry()` runs when the app comes
 * back or NetInfo reports the network again, because React Native fires no
 * `online` event.
 */

const LOG_PREFIX = "[realtime]";

/**
 * React Native's WebSocket corrupts text frames that hold a NULL byte, which
 * ends every STOMP frame: frames go out as binary, and a frame that arrives
 * without its NULL gets one appended.
 */
export const NATIVE_CLIENT_CONFIG: RealtimeClientConfig = {
  forceBinaryWSFrames: true,
  appendMissingNULLonIncoming: true,
};

/** How long the app may stay in the background before the session closes. */
export const BACKGROUND_DISCONNECT_MS = 30_000;

/** `RealtimeTicketResponse`; `expiresAt` is not read. */
const realtimeTicketSchema = z.object({ ticket: z.string().min(1) });

/** 403 and 404 refuse the identity or the organization, as `RTM_SEC_002` does. */
const REFUSED_TICKET_STATUSES: ReadonlySet<number> = new Set<number>([403, 404]);

/**
 * How a failed ticket request ends the attempt: 403 and 404 take the
 * connection offline until a retry, 401 asks for a fresh token, and anything
 * else (no response, 429, a server error) is retried with backoff.
 */
export function ticketFailure(error: unknown): RealtimeTicketResult {
  if (isApiError(error) && error.status !== undefined) {
    if (REFUSED_TICKET_STATUSES.has(error.status)) {
      console.warn(
        `${LOG_PREFIX} the ticket request was refused (${error.status} ${error.code ?? "no code"})`
      );
      return { ticket: null, errorCode: RTM_SEC_002 };
    }
    if (error.status === 401) {
      console.warn(`${LOG_PREFIX} the ticket request was refused (401 ${error.code ?? "no code"})`);
      return { ticket: null, errorCode: RTM_SEC_001 };
    }
  }
  console.error(`${LOG_PREFIX} requesting a realtime ticket failed`, describeError(error));
  return { ticket: null, errorCode: null };
}

/** What the screens read about the connection. */
export interface RealtimeSnapshot {
  status: RealtimeStatus;
  /** The code of the last end: `RTM_SEC_002` while refused, otherwise usually null. */
  errorCode: string | null;
  /** False while NetInfo reports no network. */
  networkOnline: boolean;
  /**
   * Grows on every CONNECTED and on every unreadable frame: each is a point
   * after which signals may have been missed.
   */
  resyncCount: number;
}

export type RealtimeFrameHandler = (body: unknown) => void;

/** What the store needs from the outside; the app passes the real ones, tests pass fakes. */
export interface RealtimeHubDependencies {
  brokerUrl: string | null;
  createConnection: (options: RealtimeConnectionOptions) => RealtimeConnection;
  /** A ticket for the organization; the call is aborted when `signal` fires. */
  requestTicket: (organizationId: string, signal: AbortSignal) => Promise<RealtimeTicketResult>;
  /** A fresh access token; resolves false when none could be had. */
  refreshToken: () => Promise<boolean>;
  setTimer: (callback: () => void, ms: number) => ReturnType<typeof setTimeout>;
  clearTimer: (timer: ReturnType<typeof setTimeout>) => void;
}

interface Session {
  organizationId: string;
  connection: RealtimeConnection;
  owners: number;
  /** False while paused in the background. */
  running: boolean;
}

export interface RealtimeHub {
  getSnapshot: () => RealtimeSnapshot;
  subscribeSnapshot: (listener: () => void) => () => void;
  /** Run `handler` for each frame of `destination` until the returned function runs. */
  subscribe: (destination: string, handler: RealtimeFrameHandler) => () => void;
  /** Send on the open session; false, and nothing sent, while none is connected. */
  publish: (destination: string, body: unknown) => boolean;
  /** Keep a session open for the organization until the returned function runs. */
  acquire: (organizationId: string) => () => void;
  appStateChanged: (state: AppStateStatus) => void;
  networkChanged: (online: boolean) => void;
  /** Try again now; after a refusal only once a token refresh succeeded. */
  retry: () => Promise<void>;
}

export function createRealtimeHub(deps: RealtimeHubDependencies): RealtimeHub {
  const INITIAL: RealtimeSnapshot = {
    status: "connecting",
    errorCode: null,
    networkOnline: true,
    resyncCount: 0,
  };
  let snapshot: RealtimeSnapshot = INITIAL;
  const snapshotListeners: Set<() => void> = new Set<() => void>();
  const frameListeners: Map<string, Set<RealtimeFrameHandler>> = new Map();
  let session: Session | null = null;
  let backgroundTimer: ReturnType<typeof setTimeout> | null = null;
  /** The last end code reported by the connection, read before the next ticket. */
  let lastErrorCode: string | null = null;

  const setSnapshot = (patch: Partial<RealtimeSnapshot>): void => {
    snapshot = { ...snapshot, ...patch };
    for (const listener of snapshotListeners) listener();
  };

  const notifyFrame = (frame: RealtimeFrame): void => {
    const handlers: Set<RealtimeFrameHandler> | undefined = frameListeners.get(frame.destination);
    if (!handlers) return;
    for (const handler of handlers) {
      try {
        handler(frame.body);
      } catch (error) {
        // One feature's defect must not cost the others their frames; the
        // error still reaches the global handler.
        console.error(`${LOG_PREFIX} a frame handler failed`, describeError(error));
        setTimeout((): never => {
          throw error;
        }, 0);
      }
    }
  };

  const getTicket = async (
    organizationId: string,
    signal: AbortSignal
  ): Promise<RealtimeTicketResult> => {
    // The session's token expired: read a fresh one before asking for the next ticket.
    if (lastErrorCode === RTM_SEC_001) {
      const refreshed: boolean = await deps.refreshToken();
      if (signal.aborted) return { ticket: null, errorCode: null };
      if (!refreshed) {
        console.warn(`${LOG_PREFIX} no fresh token for the next realtime session yet`);
        return { ticket: null, errorCode: null };
      }
      lastErrorCode = null;
    }
    return deps.requestTicket(organizationId, signal);
  };

  const openSession = (organizationId: string): Session | null => {
    if (!deps.brokerUrl) {
      console.error(`${LOG_PREFIX} no broker URL could be built from the API base URL`);
      setSnapshot({ status: "offline", errorCode: null });
      return null;
    }
    const connection: RealtimeConnection = deps.createConnection({
      brokerUrl: deps.brokerUrl,
      organizationId,
      clientConfig: NATIVE_CLIENT_CONFIG,
      getTicket: (signal: AbortSignal) => getTicket(organizationId, signal),
      onStatus: (status: RealtimeStatus, errorCode: string | null): void => {
        if (session?.connection !== connection) return;
        lastErrorCode = errorCode ?? (status === "connected" ? null : lastErrorCode);
        setSnapshot({ status, errorCode });
      },
      onConnected: (): void => {
        if (session?.connection !== connection) return;
        setSnapshot({ resyncCount: snapshot.resyncCount + 1 });
      },
      onFrame: notifyFrame,
      onMalformedFrame: (error: unknown): void => {
        // The signal it carried is lost; the re-sync recovers what it changed.
        console.error(`${LOG_PREFIX} unreadable frame; re-syncing`, describeError(error));
        setSnapshot({ resyncCount: snapshot.resyncCount + 1 });
      },
    });
    // Features mounted before this session keep their registrations.
    for (const destination of frameListeners.keys()) {
      connection.addDestination(destination);
    }
    return { organizationId, connection, owners: 0, running: false };
  };

  const startSession = (current: Session): void => {
    if (current.running) return;
    current.running = true;
    lastErrorCode = null;
    current.connection.start();
  };

  const pauseSession = (current: Session): void => {
    if (!current.running) return;
    current.running = false;
    current.connection.stop();
    setSnapshot({ status: "connecting", errorCode: null });
  };

  const clearBackgroundTimer = (): void => {
    if (backgroundTimer !== null) {
      deps.clearTimer(backgroundTimer);
      backgroundTimer = null;
    }
  };

  const retry = async (): Promise<void> => {
    const current: Session | null = session;
    if (!current) return;
    if (!current.running) {
      startSession(current);
      return;
    }
    if (snapshot.status === "offline" && snapshot.errorCode === RTM_SEC_002) {
      // A refusal is retried only with a token the server has not seen yet,
      // so a lost membership does not loop on the same refusal.
      const refreshed: boolean = await deps.refreshToken();
      if (!refreshed || session !== current) {
        console.warn(`${LOG_PREFIX} the refused session is not retried without a fresh token`);
        return;
      }
    }
    current.connection.retry();
  };

  const runRetry = (): void => {
    retry().catch((error: unknown) => {
      console.error(`${LOG_PREFIX} retrying the session failed`, describeError(error));
      setTimeout((): never => {
        throw error;
      }, 0);
    });
  };

  return {
    getSnapshot: () => snapshot,
    subscribeSnapshot: (listener: () => void): (() => void) => {
      snapshotListeners.add(listener);
      return () => {
        snapshotListeners.delete(listener);
      };
    },
    subscribe: (destination: string, handler: RealtimeFrameHandler): (() => void) => {
      assertRealtimeSubscribable(destination);
      let handlers: Set<RealtimeFrameHandler> | undefined = frameListeners.get(destination);
      if (!handlers) {
        handlers = new Set<RealtimeFrameHandler>();
        frameListeners.set(destination, handlers);
        session?.connection.addDestination(destination);
      }
      const registered: Set<RealtimeFrameHandler> = handlers;
      registered.add(handler);
      return () => {
        registered.delete(handler);
        if (registered.size === 0 && frameListeners.get(destination) === registered) {
          frameListeners.delete(destination);
          session?.connection.removeDestination(destination);
        }
      };
    },
    publish: (destination: string, body: unknown): boolean =>
      session?.connection.publish(destination, body) ?? false,
    acquire: (organizationId: string): (() => void) => {
      if (session && session.organizationId !== organizationId) {
        // Another organization: its frames must stop before the new session opens.
        clearBackgroundTimer();
        session.connection.stop();
        session = null;
        setSnapshot({ status: "connecting", errorCode: null });
      }
      if (!session) {
        session = openSession(organizationId);
      }
      const current: Session | null = session;
      if (!current) return () => undefined;
      current.owners += 1;
      if (AppState.currentState !== "background") startSession(current);
      let released: boolean = false;
      return () => {
        if (released) return;
        released = true;
        current.owners -= 1;
        if (current.owners > 0 || session !== current) return;
        clearBackgroundTimer();
        current.connection.stop();
        session = null;
        setSnapshot({ status: "connecting", errorCode: null });
      };
    },
    appStateChanged: (state: AppStateStatus): void => {
      const current: Session | null = session;
      if (!current) return;
      if (state === "background") {
        if (backgroundTimer !== null || !current.running) return;
        backgroundTimer = deps.setTimer(() => {
          backgroundTimer = null;
          if (session === current) pauseSession(current);
        }, BACKGROUND_DISCONNECT_MS);
        return;
      }
      if (state === "active") {
        clearBackgroundTimer();
        runRetry();
      }
    },
    networkChanged: (online: boolean): void => {
      const wasOnline: boolean = snapshot.networkOnline;
      if (wasOnline !== online) setSnapshot({ networkOnline: online });
      if (online && !wasOnline && session?.running) runRetry();
    },
    retry,
  };
}

async function requestTicket(
  organizationId: string,
  signal: AbortSignal
): Promise<RealtimeTicketResult> {
  try {
    // The organization is named explicitly so the ticket is issued for the
    // organization this session serves, even if the active one changes while
    // the request is in flight. Silent: the connection status is the surface.
    const response = await api.post(API_ROUTES.realtime.tickets, undefined, {
      headers: { [ORGANIZATION_HEADER]: organizationId },
      signal,
      _silentErrors: true,
    });
    return { ticket: readData(realtimeTicketSchema, response.data).ticket };
  } catch (error) {
    // Cancelled because the session stopped; the connection ignores the result.
    if (signal.aborted) return { ticket: null, errorCode: null };
    return ticketFailure(error);
  }
}

async function refreshToken(): Promise<boolean> {
  const outcome = await refreshAccessToken();
  if (!outcome.ok) console.warn(`${LOG_PREFIX} token refresh failed: ${outcome.reason}`);
  return outcome.ok;
}

/** The app's realtime store. */
export const realtime: RealtimeHub = createRealtimeHub({
  brokerUrl: realtimeBrokerUrl(ENV.apiBaseUrl),
  createConnection: createRealtimeConnection,
  requestTicket,
  refreshToken,
  setTimer: (callback: () => void, ms: number) => setTimeout(callback, ms),
  clearTimer: (timer: ReturnType<typeof setTimeout>) => clearTimeout(timer),
});

/**
 * Keeps the realtime session of the organization open while mounted, and
 * follows the app's foreground state and the network. Mount it once per
 * signed-in tree; a second owner shares the same session.
 */
export function useRealtimeSession(organizationId: string | null): void {
  useEffect(() => {
    if (!organizationId) return;
    return realtime.acquire(organizationId);
  }, [organizationId]);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state: AppStateStatus) => {
      realtime.appStateChanged(state);
    });
    const unsubscribeNetwork = NetInfo.addEventListener((state: NetInfoState) => {
      // `isConnected` is null until the first probe: treat unknown as online.
      realtime.networkChanged(state.isConnected !== false);
    });
    return () => {
      subscription.remove();
      unsubscribeNetwork();
    };
  }, []);
}

/** The connection state; re-renders on every change. */
export function useRealtimeStatus(): RealtimeSnapshot {
  return useSyncExternalStore(
    realtime.subscribeSnapshot,
    realtime.getSnapshot,
    realtime.getSnapshot
  );
}

/**
 * Run `handler` for every frame of `destination` while mounted, across
 * reconnects. `null` pauses it. The handler may change between renders
 * without resubscribing; it validates the body itself.
 */
export function useRealtimeSubscription(
  destination: string | null,
  handler: RealtimeFrameHandler
): void {
  const handlerRef = useRef<RealtimeFrameHandler>(handler);
  useEffect(() => {
    handlerRef.current = handler;
  });
  useEffect(() => {
    if (!destination) return;
    return realtime.subscribe(destination, (body: unknown) => {
      handlerRef.current(body);
    });
  }, [destination]);
}

/**
 * Run `onResync` after every point where signals may have been missed: each
 * reconnect and each unreadable frame. The point already passed at mount is
 * skipped, because the data was fetched for this mount anyway.
 */
export function useRealtimeResync(onResync: () => void): void {
  const { resyncCount } = useRealtimeStatus();
  const onResyncRef = useRef<() => void>(onResync);
  useEffect(() => {
    onResyncRef.current = onResync;
  });
  const seenRef = useRef<number>(resyncCount);
  useEffect(() => {
    if (resyncCount === seenRef.current) return;
    seenRef.current = resyncCount;
    onResyncRef.current();
  }, [resyncCount]);
}
