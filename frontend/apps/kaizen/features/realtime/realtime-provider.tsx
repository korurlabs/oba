"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { getSession, useSession } from "next-auth/react";
import type { Session } from "next-auth";

import { isApiError } from "@oba/shared/api/axios";
import { AuthError } from "@oba/shared/libs/auth/types";
import {
  assertRealtimeSubscribable,
  createRealtimeConnection,
  describeError,
  realtimeBrokerUrl,
  RTM_SEC_001,
  RTM_SEC_002,
  type RealtimeConnection,
  type RealtimeFrame,
  type RealtimeStatus,
  type RealtimeTicketResult,
} from "@oba/shared/realtime";
import type { ApiResponse } from "@oba/shared/schema";

import { useKaizenAccess, type KaizenAccess } from "@/components/layout/use-kaizen-access";
import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import { useRuntimeConfig } from "@oba/shared/providers";

const LOG_PREFIX = "[realtime]";

/** The body of `POST /realtime/tickets`: a one-time handshake ticket and when it lapses. */
interface RealtimeTicketResponse {
  ticket: string;
  expiresAt: string;
}

/**
 * Statuses of a refused ticket request whose cause a retry cannot fix: the
 * user, the membership or the organization is disabled or unknown (403, 404).
 */
const REFUSED_TICKET_STATUSES: ReadonlySet<number> = new Set<number>([403, 404]);

/**
 * How a failed ticket request ends the attempt. 403 and 404 refuse the
 * identity or organization, as `RTM_SEC_002` does on an open session; 401
 * refuses the token, which the next attempt reads fresh, as after
 * `RTM_SEC_001`. Network errors, 429 and server errors carry no code and are
 * retried with backoff.
 */
function ticketFailure(error: unknown): RealtimeTicketResult {
  if (isApiError(error) && error.status !== undefined) {
    if (REFUSED_TICKET_STATUSES.has(error.status)) {
      console.warn(`${LOG_PREFIX} the ticket request was refused (${error.status} ${error.code ?? "no code"})`);
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

/** Receives the parsed body of each frame of one destination; validating it is the caller's job. */
export type RealtimeFrameHandler = (body: unknown) => void;

export interface RealtimeContextValue {
  status: RealtimeStatus;
  /** The ERROR code behind `reconnecting` or `offline`, when the server sent one. */
  errorCode: string | null;
  /**
   * Grows by one on every CONNECTED (the first included) and after a frame
   * that could not be read: each is a point after which signals may have been
   * missed. Read it through `useRealtimeResync`, which runs a data hook's
   * re-sync (usually invalidating its queries) when it changes.
   */
  resyncCount: number;
  /**
   * The organization the session serves, which is the only one whose
   * organization topics are subscribed; null while signed out or loading,
   * and while Kaizen is closed in the selected organization.
   */
  organizationId: string | null;
  /**
   * Register for every frame of `destination`, across reconnects, until the
   * returned unsubscribe runs. The handlers of one destination share one
   * STOMP subscription, made when the first registers and dropped when the
   * last leaves. Throws for a destination whose form the server refuses.
   */
  subscribe: (destination: string, handler: RealtimeFrameHandler) => () => void;
  /** Send on the open session; false when none is connected (nothing is queued). */
  publish: (destination: string, body: unknown) => boolean;
  /** Try to connect at once, skipping a backoff wait or leaving `offline` after a refusal. */
  retry: () => void;
}

export const RealtimeContext = createContext<RealtimeContextValue | null>(null);

/** The realtime context; throws outside a `RealtimeProvider`. */
export function useRealtimeContext(): RealtimeContextValue {
  const context: RealtimeContextValue | null = useContext(RealtimeContext);
  if (!context) {
    throw new Error("Realtime hooks must be used within a RealtimeProvider");
  }
  return context;
}

interface ConnectionState {
  status: RealtimeStatus;
  errorCode: string | null;
}

/** Shown while the session or the Kaizen access loads, so the first paint does not flash an offline state. */
const INITIAL: ConnectionState = { status: "connecting", errorCode: null };
const OFFLINE: ConnectionState = { status: "offline", errorCode: null };

/** Who the session belongs to: the Keycloak subject, or the email when no id token was decoded. */
function sessionUserKey(session: Session | null | undefined): string | null {
  return session?.sub ?? session?.user?.email ?? null;
}

function sessionOrganizationId(session: Session | null | undefined): string | null {
  const organizationId: string | null | undefined = session?.user?.defaultOrganizationId;
  return organizationId ? organizationId : null;
}

/**
 * A listener that throws is a defect in that listener. It must not starve the
 * other listeners of the frame, so the error is handed to the browser's global
 * error reporting (console and `window.onerror`) instead of being thrown here.
 */
function reportListenerError(error: unknown): void {
  console.error(`${LOG_PREFIX} a frame listener threw`, describeError(error));
  if (typeof globalThis.reportError === "function") {
    globalThis.reportError(error);
  } else {
    setTimeout((): never => {
      throw error;
    }, 0);
  }
}

/**
 * Holds the one realtime session of the tab, for the signed-in user and the
 * session's default organization, which is the organization the Axios client
 * sends as `X-Organization-ID`.
 *
 * The session follows the identity: a change of user or organization closes it
 * and opens one for the new pair; sign-out closes it. Before every attempt the
 * session is read again through `getSession`, whose JWT callback refreshes an
 * access token that is about to expire, and a new one-time ticket is requested
 * for it, so a reconnect never presents a stale token or a spent ticket.
 *
 * No session opens while Kaizen is closed in the organization: every
 * organization topic belongs to Kaizen, and the API refuses its calls there.
 * Until that is known the status stays `connecting`, as while the session
 * loads.
 *
 * Frames are handed to the subscribers of their destination unvalidated: each
 * feature owns its signal schema and its cache updates. The provider itself
 * only subscribes the destinations that have handlers, reports status and
 * counts the points where a re-sync is due.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { data: session, status: sessionStatus } = useSession();
  const kaizenAccess: KaizenAccess["status"] = useKaizenAccess().status;
  const identityPending: boolean = sessionStatus === "loading" || kaizenAccess === "pending";
  const userKey: string | null =
    sessionStatus === "authenticated" && session?.error !== AuthError.REFRESH_TOKEN_ERROR
      ? sessionUserKey(session)
      : null;
  const organizationId: string | null =
    userKey && kaizenAccess === "open" ? sessionOrganizationId(session) : null;
  const apiBaseUrl: string | undefined = useRuntimeConfig().apiBaseUrl;

  const [connection, setConnection] = useState<ConnectionState>(INITIAL);
  const [resyncCount, setResyncCount] = useState<number>(0);
  const listenersRef = useRef<Map<string, Set<RealtimeFrameHandler>>>(
    new Map<string, Set<RealtimeFrameHandler>>(),
  );
  const connectionRef = useRef<RealtimeConnection | null>(null);

  useEffect(() => {
    if (identityPending) {
      setConnection(INITIAL);
      return;
    }
    if (!userKey || !organizationId) {
      setConnection(OFFLINE);
      return;
    }

    const brokerUrl: string | null = realtimeBrokerUrl(apiBaseUrl);
    if (!brokerUrl) {
      setConnection(OFFLINE);
      return;
    }

    const getTicket = async (signal: AbortSignal): Promise<RealtimeTicketResult> => {
      // broadcast: false — reading the session for a reconnect must not make
      // every other open tab refetch its session.
      const fresh: Session | null = await getSession({ broadcast: false });
      // The connection stopped while the session was read: no request is due.
      if (signal.aborted) return { ticket: null, errorCode: null };
      if (!fresh?.accessToken || fresh.error === AuthError.REFRESH_TOKEN_ERROR) {
        console.warn(`${LOG_PREFIX} no token for this organization's realtime session yet`);
        return { ticket: null, errorCode: null };
      }
      // The provider's session can trail the cookie by a render during a
      // switch; a session must never open for a pair other than the one it
      // serves. The next render restarts the connection for the new pair.
      if (sessionUserKey(fresh) !== userKey || sessionOrganizationId(fresh) !== organizationId) {
        console.warn(`${LOG_PREFIX} the session moved to another user or organization`);
        return { ticket: null, errorCode: null };
      }
      let data: RealtimeTicketResponse | null | undefined;
      try {
        // The organization is named explicitly so the ticket is issued for
        // the organization this connection serves, even if the session's
        // default changes while the request is in flight.
        const response = await api.post<ApiResponse<RealtimeTicketResponse>>(
          API_ROUTES.realtime.tickets,
          undefined,
          { headers: { "X-Organization-ID": organizationId }, signal },
        );
        data = response.data.data;
      } catch (error) {
        // Cancelled because the connection stopped; the connection ignores the result.
        if (signal.aborted) return { ticket: null, errorCode: null };
        return ticketFailure(error);
      }
      if (!data?.ticket) {
        console.error(`${LOG_PREFIX} the ticket response carried no ticket`);
        return { ticket: null, errorCode: null };
      }
      return { ticket: data.ticket };
    };

    const notifyListeners = (frame: RealtimeFrame): void => {
      const handlers: Set<RealtimeFrameHandler> | undefined = listenersRef.current.get(
        frame.destination,
      );
      if (!handlers) return;
      for (const handler of handlers) {
        try {
          handler(frame.body);
        } catch (error) {
          reportListenerError(error);
        }
      }
    };

    const realtime: RealtimeConnection = createRealtimeConnection({
      brokerUrl,
      organizationId,
      getTicket,
      onStatus: (status: RealtimeStatus, errorCode: string | null): void => {
        setConnection({ status, errorCode });
      },
      onConnected: (): void => {
        setResyncCount((count: number): number => count + 1);
      },
      onFrame: notifyListeners,
      onMalformedFrame: (error: unknown): void => {
        // The signal it carried is lost; the re-sync recovers what it changed.
        console.error(`${LOG_PREFIX} unreadable frame; re-syncing`, describeError(error));
        setResyncCount((count: number): number => count + 1);
      },
    });

    // Features mounted before this connection (or under the previous one)
    // keep their registrations; the new session subscribes them.
    for (const destination of listenersRef.current.keys()) {
      realtime.addDestination(destination);
    }
    connectionRef.current = realtime;
    realtime.start();
    return (): void => {
      realtime.stop();
      connectionRef.current = null;
      setConnection(INITIAL);
    };
  }, [identityPending, userKey, organizationId, apiBaseUrl]);

  const subscribe = useCallback(
    (destination: string, handler: RealtimeFrameHandler): (() => void) => {
      // Checked here as well as in the connection: with no connection open, a
      // bad destination would otherwise throw only when the next one starts,
      // far from the feature that named it.
      assertRealtimeSubscribable(destination);
      const listeners: Map<string, Set<RealtimeFrameHandler>> = listenersRef.current;
      let handlers: Set<RealtimeFrameHandler> | undefined = listeners.get(destination);
      if (!handlers) {
        handlers = new Set<RealtimeFrameHandler>();
        listeners.set(destination, handlers);
        connectionRef.current?.addDestination(destination);
      }
      const registered: Set<RealtimeFrameHandler> = handlers;
      registered.add(handler);
      return (): void => {
        registered.delete(handler);
        if (registered.size === 0 && listeners.get(destination) === registered) {
          listeners.delete(destination);
          connectionRef.current?.removeDestination(destination);
        }
      };
    },
    [],
  );

  const publish = useCallback((destination: string, body: unknown): boolean => {
    return connectionRef.current?.publish(destination, body) ?? false;
  }, []);

  const retry = useCallback((): void => {
    connectionRef.current?.retry();
  }, []);

  const value: RealtimeContextValue = useMemo(
    () => ({
      status: connection.status,
      errorCode: connection.errorCode,
      resyncCount,
      organizationId,
      subscribe,
      publish,
      retry,
    }),
    [
      connection.errorCode,
      connection.status,
      organizationId,
      publish,
      resyncCount,
      retry,
      subscribe,
    ],
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}
