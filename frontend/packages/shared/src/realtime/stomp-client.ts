import {
  Client,
  type IFrame,
  type IMessage,
  type StompConfig,
  type StompSubscription,
} from "@stomp/stompjs";

/**
 * STOMP transport of the realtime signals: one plain WebSocket to `/ws`
 * speaking STOMP 1.2 (no SockJS), subscribed to every destination a feature
 * currently wants.
 *
 * The socket is authenticated by a one-time ticket in its URL
 * (`/ws?ticket=...`), which the REST API issues for the session's token and
 * organization just before each attempt. The server refuses the handshake
 * without a valid unused ticket, so no unauthenticated socket is ever open, and
 * the CONNECT frame carries no credentials. A ticket is spent by the handshake
 * that presents it, so every attempt, reconnects included, asks for a new one.
 *
 * The server refuses by default: a destination no feature of the server
 * claims, a malformed one (blank, too long, a wildcard or `..`), a SUBSCRIBE
 * outside `/topic/` and `/user/queue/`, a SEND outside `/app/`, an
 * organization topic of another organization, and a reused subscription id
 * all end the session with `RTM_SEC_002`. The client checks what it can
 * before a frame leaves, so a defect or a stale organization does not cost
 * the whole tab its connection.
 *
 * The server ends the session with an ERROR frame `RTM_SEC_001` when the token
 * the ticket was issued for expires. That close is answered with an immediate
 * reconnect on a new ticket, issued for a freshly read session token. `RTM_SEC_002`
 * means the user, the membership or the organization is refused; retrying
 * would be refused again, so the connection goes offline until the caller asks
 * for another attempt. Every other loss waits with a jittered exponential
 * backoff, so a backend rollout does not see every open tab reconnect in the
 * same second.
 *
 * At most one attempt is open at a time: a ticket request, a handshake or a
 * session. A trigger that arrives meanwhile (the `online` event, a Retry
 * click) does nothing; during a backoff wait it replaces the wait with the
 * attempt instead of adding one. Stopping aborts a ticket request still in
 * flight, so a restart for another identity never overlaps the old request.
 */

/** Handshake path, joined to the API base URL the Axios client uses. */
export const REALTIME_ENDPOINT_PATH = "/ws";

/** Query parameter of the handshake URL that carries the one-time ticket. */
const REALTIME_TICKET_PARAMETER = "ticket";

/** Prefixes a SUBSCRIBE may use: topics, and the caller's own queues. */
const SUBSCRIBE_DESTINATION_PREFIXES: readonly string[] = ["/topic/", "/user/queue/"];

/** The prefix a SEND must use: application handlers, never the broker itself. */
const SEND_DESTINATION_PREFIX = "/app/";

/** Topics of one organization; the segment after the prefix is its ID. */
const ORGANIZATION_TOPIC_PREFIX = "/topic/organizations/";

/** Broker pattern characters and path climbing, which the server refuses outright. */
const FORBIDDEN_DESTINATION_SEQUENCES: readonly string[] = ["*", "?", "{", "}", ".."];

/** The longest destination the server accepts. */
const MAX_DESTINATION_LENGTH = 256;

/**
 * The topic of one feature in one organization, such as
 * `/topic/organizations/{organizationId}/chat` for `suffix` `/chat`. The server
 * accepts it only from a session of that organization.
 */
export function realtimeOrganizationTopic(organizationId: string, suffix: string): string {
  return `${ORGANIZATION_TOPIC_PREFIX}${organizationId}${suffix}`;
}

/** The organization an organization topic belongs to, or null for any other destination. */
export function realtimeTopicOrganization(destination: string): string | null {
  if (!destination.startsWith(ORGANIZATION_TOPIC_PREFIX)) return null;
  const rest: string = destination.slice(ORGANIZATION_TOPIC_PREFIX.length);
  const end: number = rest.indexOf("/");
  return end === -1 ? rest : rest.slice(0, end);
}

function destinationShapeProblem(destination: string): string | null {
  if (destination.trim() === "") return "it is blank";
  if (destination.length > MAX_DESTINATION_LENGTH) return "it is too long";
  const forbidden: boolean = FORBIDDEN_DESTINATION_SEQUENCES.some((sequence: string): boolean =>
    destination.includes(sequence),
  );
  if (forbidden) return "it holds a wildcard or ..";
  return null;
}

/** Why the server would refuse a SUBSCRIBE to `destination`, or null when its form is valid. */
export function realtimeSubscribeProblem(destination: string): string | null {
  const shape: string | null = destinationShapeProblem(destination);
  if (shape) return shape;
  const allowed: boolean = SUBSCRIBE_DESTINATION_PREFIXES.some((prefix: string): boolean =>
    destination.startsWith(prefix),
  );
  if (!allowed) return `it is not under ${SUBSCRIBE_DESTINATION_PREFIXES.join(" or ")}`;
  return null;
}

/**
 * Throw when the server would refuse a SUBSCRIBE to `destination`. Such a
 * destination is a defect in the code that named it, and sending it would end
 * the session for every feature of the tab.
 */
export function assertRealtimeSubscribable(destination: string): void {
  const problem: string | null = realtimeSubscribeProblem(destination);
  if (problem) {
    throw new Error(`${LOG_PREFIX} cannot subscribe to ${destination}: ${problem}`);
  }
}

/** Why the server would refuse a SEND to `destination`, or null when its form is valid. */
export function realtimeSendProblem(destination: string): string | null {
  const shape: string | null = destinationShapeProblem(destination);
  if (shape) return shape;
  if (!destination.startsWith(SEND_DESTINATION_PREFIX)) {
    return `it is not under ${SEND_DESTINATION_PREFIX}`;
  }
  return null;
}

/**
 * The session outlived its token, or a CONNECT came without the ticket's
 * identity; answered with a new ticket. A ticket request refused with 401 is
 * reported with this code too, because a fresh session token may succeed.
 */
export const RTM_SEC_001 = "RTM_SEC_001";

/**
 * User not active, no access to the organization, or access revoked. A ticket
 * request refused with 403 or 404 is reported with this code too: the same
 * identity and organization would be refused again.
 */
export const RTM_SEC_002 = "RTM_SEC_002";

/** Heart-beat both ways, as the server's broker; keeps the socket under proxy idle timeouts. */
export const REALTIME_HEARTBEAT_MS = 25_000;

/** Missed server heart-beat intervals after which the socket counts as dead. */
const REALTIME_HEARTBEAT_TOLERANCE = 3;

export const REALTIME_BACKOFF_MIN_MS = 1_000;
export const REALTIME_BACKOFF_MAX_MS = 30_000;

/** A socket that has not reached CONNECTED by then is closed and retried. */
const CONNECT_TIMEOUT_MS = 10_000;

/**
 * Shortest session whose token-expiry close earns an immediate reconnect. A
 * session that ends with `RTM_SEC_001` sooner than this was opened with a token
 * about to expire; reconnecting at once could repeat that in a tight loop, so
 * it backs off instead.
 */
const MIN_ROUTINE_SESSION_MS = 10_000;

const LOG_PREFIX = "[realtime]";

/**
 * Connection state as a consumer sees it.
 *
 * - `connecting`: no session has reached CONNECTED yet; the first attempt is open.
 * - `connected`: subscribed; signals arrive live.
 * - `reconnecting`: a session was lost or an attempt failed; the next attempt
 *   is open or scheduled.
 * - `offline`: no attempt is made: there is no signed-in organization, the
 *   browser is offline, the server refused the identity or organization
 *   (`RTM_SEC_002`, from the session or from the ticket request), or no
 *   WebSocket URL could be built.
 */
export type RealtimeStatus = "connecting" | "connected" | "reconnecting" | "offline";

/** A MESSAGE frame, its body parsed as JSON but not yet validated. */
export interface RealtimeFrame {
  /** The destination of the subscription it arrived on, as subscribed. */
  destination: string;
  body: unknown;
}

/**
 * The result of a ticket request made before an attempt: a ticket, or the
 * code to end the attempt with. `errorCode` `RTM_SEC_002` takes the
 * connection offline; any other value, null included, retries with backoff.
 */
export type RealtimeTicketResult =
  | { ticket: string }
  | { ticket: null; errorCode: string | null };

/** What follows the end of a session. */
export type RealtimeSessionEnd = "routine" | "backoff" | "refused";

/**
 * Decide how to follow a session that ended.
 *
 * `errorCode` is the `message` header of the ERROR frame the server sent before
 * closing, or null when the socket closed without one (network loss, rollout).
 */
export function classifySessionEnd(input: {
  connected: boolean;
  errorCode: string | null;
  sessionMs: number;
}): RealtimeSessionEnd {
  if (input.errorCode === RTM_SEC_002) {
    return "refused";
  }
  if (
    input.errorCode === RTM_SEC_001 &&
    input.connected &&
    input.sessionMs >= MIN_ROUTINE_SESSION_MS
  ) {
    return "routine";
  }
  return "backoff";
}

/**
 * Delay before reconnect attempt `attempt` (0-based): exponential from 1 s,
 * capped at 30 s, with "equal jitter" (a random point in the upper half of the
 * window), never below 1 s.
 */
export function backoffDelayMs(attempt: number, random: () => number = Math.random): number {
  const ceiling: number = Math.min(
    REALTIME_BACKOFF_MAX_MS,
    REALTIME_BACKOFF_MIN_MS * 2 ** Math.max(0, attempt),
  );
  const jittered: number = ceiling / 2 + random() * (ceiling / 2);
  return Math.max(REALTIME_BACKOFF_MIN_MS, Math.round(jittered));
}

/** WebSocket scheme for each API scheme: a TLS API gets a TLS socket. */
const SOCKET_SCHEMES: Readonly<Record<string, string>> = { https: "wss", http: "ws" };

/** A URL scheme: a letter, then letters, digits, `+`, `.` or `-`. */
const URL_SCHEME_PATTERN: RegExp = /^[a-z][a-z0-9+.-]*$/i;

/** An absolute URL split into scheme, authority (non-empty) and the rest. */
interface AbsoluteUrlParts {
  scheme: string;
  authority: string;
  path: string;
}

/**
 * `value` without its trailing slashes. A backwards scan rather than a
 * `/\/+$/` replace: the base URL comes from configuration the apps pass in,
 * and that pattern retries from every slash, so a long run of slashes not at
 * the end costs quadratic time.
 */
function stripTrailingSlashes(value: string): string {
  let end: number = value.length;
  while (end > 0 && value.charCodeAt(end - 1) === 0x2f) {
    end -= 1;
  }
  return value.slice(0, end);
}

/**
 * Splits `scheme://authority/rest`, or null when `url` has no valid scheme
 * before its first `://` or an empty authority. The authority runs to the first
 * `/`, `?` or `#`. Parsed with index lookups instead of one pattern with
 * adjacent overlapping quantifiers, so the time stays linear in the length of
 * the input.
 */
function splitAbsoluteUrl(url: string): AbsoluteUrlParts | null {
  const separator: number = url.indexOf("://");
  if (separator < 0) {
    return null;
  }
  const scheme: string = url.slice(0, separator);
  if (!URL_SCHEME_PATTERN.test(scheme)) {
    return null;
  }
  const rest: string = url.slice(separator + 3);
  let authorityEnd: number = 0;
  while (authorityEnd < rest.length && !"/?#".includes(rest.charAt(authorityEnd))) {
    authorityEnd += 1;
  }
  if (authorityEnd === 0) {
    return null;
  }
  return { scheme, authority: rest.slice(0, authorityEnd), path: rest.slice(authorityEnd) };
}

/**
 * The WebSocket URL for an API base URL: the same join Axios makes for
 * `baseURL` + path, with `http` turned into `ws` and `https` into `wss`. Null
 * when the base URL is missing, not an absolute http(s) URL, or carries a query
 * or fragment; a relative socket URL would point at the app's own origin, which
 * serves no `/ws`.
 *
 * Built as a string rather than with `URL` setters: React Native's `URL` does
 * not implement them, and each app passes its own configured base URL.
 */
export function realtimeBrokerUrl(apiBaseUrl: string | undefined): string | null {
  if (!apiBaseUrl) {
    console.error(`${LOG_PREFIX} the API base URL is not set`);
    return null;
  }
  const base: string = stripTrailingSlashes(apiBaseUrl.trim());
  const parts: AbsoluteUrlParts | null = /\s/.test(base) ? null : splitAbsoluteUrl(base);
  if (!parts) {
    console.error(`${LOG_PREFIX} the API base URL is not an absolute URL: ${apiBaseUrl}`);
    return null;
  }
  const { scheme, authority, path }: AbsoluteUrlParts = parts;
  const socketScheme: string | undefined = SOCKET_SCHEMES[scheme.toLowerCase()];
  if (!socketScheme) {
    console.error(`${LOG_PREFIX} the API base URL has an unsupported protocol: ${scheme}:`);
    return null;
  }
  if (path.includes("?") || path.includes("#")) {
    console.error(`${LOG_PREFIX} the API base URL must not carry a query or fragment: ${apiBaseUrl}`);
    return null;
  }
  return `${socketScheme}://${authority}${path}${REALTIME_ENDPOINT_PATH}`;
}

/**
 * The handshake URL of one attempt: the broker URL with the attempt's ticket.
 * The broker URL has no query of its own (`realtimeBrokerUrl` refuses one), and
 * is joined as a string because React Native's `URLSearchParams` is incomplete.
 */
export function realtimeTicketUrl(brokerUrl: string, ticket: string): string {
  const separator: string = brokerUrl.includes("?") ? "&" : "?";
  return `${brokerUrl}${separator}${REALTIME_TICKET_PARAMETER}=${encodeURIComponent(ticket)}`;
}

/**
 * Transport settings of the stompjs `Client` that depend on the platform.
 * The web app passes none. React Native passes both as true: its WebSocket
 * delivers text frames without the trailing NULL that STOMP requires and
 * corrupts outgoing text frames, so frames go out binary and the NULL is
 * restored on arrival.
 */
export type RealtimeClientConfig = Pick<
  StompConfig,
  "forceBinaryWSFrames" | "appendMissingNULLonIncoming"
>;

export interface RealtimeConnectionOptions {
  /** The handshake URL without a ticket, as `realtimeBrokerUrl` builds it. */
  brokerUrl: string;
  /** The organization this connection serves; only its organization topics are subscribed. */
  organizationId: string;
  /**
   * Requests a new ticket before every attempt; a ticket is never reused. A
   * throw is logged and retried with backoff, as a result without a ticket
   * and with an error code other than `RTM_SEC_002` is. `signal` is aborted
   * when the connection stops while the request is in flight; the request
   * should be cancelled then, and its result is ignored.
   */
  getTicket: (signal: AbortSignal) => Promise<RealtimeTicketResult>;
  onStatus: (status: RealtimeStatus, errorCode: string | null) => void;
  /** Called on every CONNECTED, the first included, once the wanted destinations are subscribed. */
  onConnected: () => void;
  onFrame: (frame: RealtimeFrame) => void;
  /** A MESSAGE frame whose body is not JSON; the signal it carried is lost. */
  onMalformedFrame: (error: unknown) => void;
  /** Platform transport settings applied to every attempt's client; see `RealtimeClientConfig`. */
  clientConfig?: RealtimeClientConfig;
  /** Test seam; production uses the stompjs `Client`. */
  createClient?: () => Client;
  now?: () => number;
  random?: () => number;
}

export interface RealtimeConnection {
  start: () => void;
  stop: () => void;
  /**
   * Try at once when no session is open: after a refusal, while offline, or
   * while a backoff timer is pending. No-op while an attempt is open or
   * connected, and after `stop`.
   */
  retry: () => void;
  /**
   * Want the frames of `destination`: subscribed at once when a session is
   * connected, and again on every later CONNECTED. Throws for a destination
   * whose form the server refuses. An organization topic of another
   * organization is not subscribed: during an organization switch a feature
   * can name the old organization for a render, and sending it would end the
   * session.
   */
  addDestination: (destination: string) => void;
  /** Stop wanting `destination`; unsubscribed at once when a session is connected. */
  removeDestination: (destination: string) => void;
  /**
   * Send a frame on the open session. Returns false, and sends nothing, when
   * no session is connected: a signal sent by a client (such as typing) is
   * worthless once late. Throws for a destination whose form the server refuses.
   */
  publish: (destination: string, body: unknown) => boolean;
}

/** A session that reached CONNECTED, with what it is subscribed to. */
interface LiveSession {
  client: Client;
  subscriptions: Map<string, StompSubscription>;
  nextSubscriptionId: number;
}

/**
 * One logical realtime connection: a fresh stompjs `Client` per attempt, so no
 * state of a failed attempt leaks into the next, with the reconnect policy kept
 * here rather than in stompjs (its built-in reconnect has no jitter and cannot
 * tell a token-expiry close from a refusal).
 */
export function createRealtimeConnection(options: RealtimeConnectionOptions): RealtimeConnection {
  const now: () => number = options.now ?? Date.now;
  const random: () => number = options.random ?? Math.random;
  const makeClient: () => Client = options.createClient ?? ((): Client => new Client());

  let stopped: boolean = true;
  let hasConnected: boolean = false;
  let attempt: number = 0;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let current: Client | null = null;
  /** Cancels the ticket request of the open attempt; null once it has settled. */
  let ticketAbort: AbortController | null = null;
  /** The session once CONNECTED; null while connecting, between sessions and after `stop`. */
  let live: LiveSession | null = null;
  /** Destinations the features want, kept across sessions. */
  const wanted: Set<string> = new Set<string>();

  const clearRetryTimer = (): void => {
    if (retryTimer !== null) {
      clearTimeout(retryTimer);
      retryTimer = null;
    }
  };

  const isBrowserOffline = (): boolean =>
    typeof navigator !== "undefined" && navigator.onLine === false;

  /**
   * `force` drops the socket without the DISCONNECT handshake, whose graceful
   * form waits for a RECEIPT that a dying server may never send; a socket left
   * open that way would keep receiving another organization's frames.
   * A failed close is a defect, not a state the reader can act on, so it goes
   * to the browser's global error reporting.
   */
  const deactivate = (client: Client, force: boolean = false): void => {
    client.deactivate({ force }).catch((error: unknown): void => {
      console.error(`${LOG_PREFIX} closing the STOMP client failed`, describeError(error));
      if (typeof globalThis.reportError === "function") {
        globalThis.reportError(error);
      } else {
        setTimeout((): never => {
          throw error;
        }, 0);
      }
    });
  };

  const handleMessage = (destination: string, client: Client, message: IMessage): void => {
    // A frame still in flight on a session that was replaced or stopped
    // belongs to an organization this connection no longer serves.
    if (stopped || current !== client) return;
    let body: unknown;
    try {
      body = JSON.parse(message.body);
    } catch (error) {
      options.onMalformedFrame(error);
      return;
    }
    options.onFrame({ destination, body });
  };

  const subscribeOn = (session: LiveSession, destination: string): void => {
    // A socket that is closing cannot take the frame; the destination stays
    // wanted and is subscribed on the next CONNECTED.
    if (session.subscriptions.has(destination) || !session.client.connected) return;
    // The server ends the session when a SUBSCRIBE reuses an id the session
    // holds, so each SUBSCRIBE gets a new one, also for a destination
    // subscribed again after an unsubscribe.
    const id: string = `sub-${session.nextSubscriptionId}`;
    session.nextSubscriptionId += 1;
    const subscription: StompSubscription = session.client.subscribe(
      destination,
      (message: IMessage): void => {
        handleMessage(destination, session.client, message);
      },
      { id },
    );
    session.subscriptions.set(destination, subscription);
  };

  const isForeignOrganizationTopic = (destination: string): boolean => {
    const organizationId: string | null = realtimeTopicOrganization(destination);
    return organizationId !== null && organizationId !== options.organizationId;
  };

  const openSession = (): void => {
    // One attempt at a time: a second one would request a second ticket.
    if (stopped || current !== null) return;
    if (isBrowserOffline()) {
      options.onStatus("offline", null);
      return;
    }

    const client: Client = makeClient();
    current = client;
    let connectedAt: number | null = null;
    let errorCode: string | null = null;
    let ended: boolean = false;

    const endSession = (): void => {
      if (ended) return;
      ended = true;
      if (live?.client === client) live = null;
      deactivate(client);
      if (stopped || current !== client) return;
      current = null;

      const outcome: RealtimeSessionEnd = classifySessionEnd({
        connected: connectedAt !== null,
        errorCode,
        sessionMs: connectedAt === null ? 0 : now() - connectedAt,
      });

      if (outcome === "refused") {
        console.warn(`${LOG_PREFIX} the server refused the session (${errorCode})`);
        options.onStatus("offline", errorCode);
        return;
      }

      if (outcome === "routine") {
        openSession();
        return;
      }

      if (isBrowserOffline()) {
        // The `online` listener reconnects; a timer would only spin.
        options.onStatus("offline", errorCode);
        return;
      }

      const delay: number = backoffDelayMs(attempt, random);
      attempt += 1;
      options.onStatus("reconnecting", errorCode);
      retryTimer = setTimeout((): void => {
        retryTimer = null;
        openSession();
      }, delay);
    };

    client.configure({
      ...options.clientConfig,
      brokerURL: options.brokerUrl,
      // The reconnect policy lives in this module; stompjs must not race it.
      reconnectDelay: 0,
      connectionTimeout: CONNECT_TIMEOUT_MS,
      heartbeatIncoming: REALTIME_HEARTBEAT_MS,
      heartbeatOutgoing: REALTIME_HEARTBEAT_MS,
      // The server's broker checks every heart-beat interval and writes only
      // when the last write is older than one interval, so its heart-beats can
      // be almost two intervals apart: stompjs' default tolerance of exactly
      // two would drop a healthy session now and then. Three matches the
      // tolerance the server applies to this client's heart-beats.
      heartbeatToleranceMultiplier: REALTIME_HEARTBEAT_TOLERANCE,
      // A missed heart-beat means the peer is gone; drop the socket at once
      // instead of waiting for the browser's own close handshake to time out.
      discardWebsocketOnCommFailure: true,
      beforeConnect: async (stompClient: Client): Promise<void> => {
        let result: RealtimeTicketResult = { ticket: null, errorCode: null };
        const abort: AbortController = new AbortController();
        ticketAbort = abort;
        try {
          result = await options.getTicket(abort.signal);
        } catch (error) {
          // An aborted request belongs to a stopped connection and is no failure.
          if (!abort.signal.aborted) {
            console.error(`${LOG_PREFIX} requesting a realtime ticket failed`, describeError(error));
          }
        } finally {
          if (ticketAbort === abort) ticketAbort = null;
        }
        // A ticket issued for an attempt that was replaced or stopped is left
        // unused; the server lets it expire.
        if (stopped || current !== client) {
          await stompClient.deactivate();
          return;
        }
        if (result.ticket === null) {
          errorCode = result.errorCode;
          await stompClient.deactivate();
          endSession();
          return;
        }
        // stompjs opens the socket after this callback, on the URL set here.
        stompClient.brokerURL = realtimeTicketUrl(options.brokerUrl, result.ticket);
      },
      onConnect: (): void => {
        if (stopped || current !== client) return;
        connectedAt = now();
        attempt = 0;
        hasConnected = true;
        const session: LiveSession = { client, subscriptions: new Map(), nextSubscriptionId: 0 };
        live = session;
        for (const destination of wanted) {
          subscribeOn(session, destination);
        }
        options.onStatus("connected", null);
        options.onConnected();
      },
      onStompError: (frame: IFrame): void => {
        errorCode = frame.headers.message ?? null;
        // The two known codes are handled when the socket closes. Anything
        // else is an unexpected server-side failure of the session, which then
        // shows as `reconnecting`.
        if (errorCode !== RTM_SEC_001 && errorCode !== RTM_SEC_002) {
          console.error(`${LOG_PREFIX} STOMP ERROR frame: ${errorCode ?? "(no message header)"}`);
        }
      },
      onWebSocketClose: (): void => {
        endSession();
      },
    });

    options.onStatus(hasConnected ? "reconnecting" : "connecting", null);
    client.activate();
  };

  const retryNow = (): void => {
    if (stopped || current !== null) return;
    clearRetryTimer();
    attempt = 0;
    openSession();
  };

  const handleOnline = (): void => {
    retryNow();
  };

  /**
   * The browser window that fires `online`, or null where there is none: on
   * the server, and in React Native, whose `window` is the global object
   * without event listeners. There the app calls `retry` when it sees the
   * network return.
   */
  const onlineEventTarget = (): Window | null =>
    typeof window !== "undefined" && typeof window.addEventListener === "function"
      ? window
      : null;

  return {
    start: (): void => {
      if (!stopped) return;
      stopped = false;
      hasConnected = false;
      attempt = 0;
      onlineEventTarget()?.addEventListener("online", handleOnline);
      openSession();
    },
    stop: (): void => {
      stopped = true;
      clearRetryTimer();
      ticketAbort?.abort();
      ticketAbort = null;
      onlineEventTarget()?.removeEventListener("online", handleOnline);
      const client: Client | null = current;
      current = null;
      live = null;
      if (client) deactivate(client, true);
    },
    retry: retryNow,
    addDestination: (destination: string): void => {
      assertRealtimeSubscribable(destination);
      if (isForeignOrganizationTopic(destination)) {
        console.warn(`${LOG_PREFIX} not subscribing to another organization: ${destination}`);
        return;
      }
      wanted.add(destination);
      if (!stopped && live) subscribeOn(live, destination);
    },
    removeDestination: (destination: string): void => {
      wanted.delete(destination);
      const session: LiveSession | null = live;
      const subscription: StompSubscription | undefined = session?.subscriptions.get(destination);
      if (!session || !subscription) return;
      session.subscriptions.delete(destination);
      // A closed socket has already dropped the subscription with the session.
      if (session.client.connected) subscription.unsubscribe();
    },
    publish: (destination: string, body: unknown): boolean => {
      const problem: string | null = realtimeSendProblem(destination);
      if (problem) {
        throw new Error(`${LOG_PREFIX} cannot send to ${destination}: ${problem}`);
      }
      const session: LiveSession | null = live;
      if (stopped || !session || !session.client.connected) return false;
      session.client.publish({ destination, body: JSON.stringify(body) });
      return true;
    },
  };
}

export function describeError(error: unknown): string {
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
}
