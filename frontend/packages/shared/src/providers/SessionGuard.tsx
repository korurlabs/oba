"use client";

import type { Session } from "next-auth";
import { getSession, SessionContext, useSession } from "next-auth/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { registerSessionRefresher } from "../libs/auth/session-recovery";
import { AuthError } from "../libs/auth/types";

/** Retries before a null session is treated as a real logout. */
const VERIFY_RETRIES = 2;
/** Delay between verification retries. */
const VERIFY_DELAY_MS = 2000;
/** Delay before re-checking an auth endpoint that could not be reached while the browser reports online. */
const UNREACHABLE_RETRY_MS = 15000;
/** Stable toast id so repeated confirmations never stack prompts. */
const SESSION_EXPIRED_TOAST_ID = "session-expired";
/** The next-auth session endpoint; the guard calls it itself to tell "no session" from "no answer". */
const SESSION_ENDPOINT = "/api/auth/session";

/**
 * What the auth endpoint said about the session: it has one, it answered that
 * there is none, or it gave no usable answer (network failure or server error).
 */
type SessionProbe = "session" | "none" | "unreachable";

type SessionContextValue = ReturnType<typeof useSession>;

function loginUrl(): string {
  return `/api/auth/login?callbackUrl=${encodeURIComponent(window.location.href)}`;
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Asks the session endpoint directly. next-auth's getSession() returns null on
 * any failure, so it cannot tell an offline browser from a signed-out user;
 * only a 200 answer with an empty body means the session is really gone.
 */
async function probeSession(): Promise<SessionProbe> {
  try {
    const response = await fetch(SESSION_ENDPOINT, {
      headers: { "Content-Type": "application/json" },
      cache: "no-store",
    });
    if (!response.ok) {
      console.warn(`[SessionGuard] session endpoint answered ${response.status}; treating it as unreachable`);
      return "unreachable";
    }
    const data: unknown = await response.json();
    return data !== null && typeof data === "object" ? "session" : "none";
  } catch (error) {
    console.warn("[SessionGuard] session endpoint unreachable; keeping the last session", error);
    return "unreachable";
  }
}

/**
 * Watches session status and handles the session becoming unauthenticated
 * (e.g. after logout in another tab/app) WITHOUT ever hard-navigating a
 * foreground tab — a reload destroys in-progress user state (open dialogs,
 * half-filled forms).
 *
 * Why verification is needed: next-auth's session fetch returns null on ANY
 * fetch failure (offline browser, 5xx during a deploy rollout, network blip,
 * timeout), which is indistinguishable from a real logout. Reacting
 * immediately turned every transient blip into a sign-in prompt or a full-page
 * round trip through Keycloak SSO. So on an authenticated -> unauthenticated
 * transition we:
 *
 * 1. Keep giving the last authenticated session to everything below this
 *    guard (it re-provides next-auth's SessionContext, so every useSession()
 *    call sees it) until the loss is confirmed. Pages that read the
 *    organization from the session therefore stay mounted while offline.
 * 2. Re-verify by asking the session endpoint directly. If it returns a
 *    session it was a transient failure: one broadcasting getSession() heals
 *    the provider (after a null session it stops refetching on visibility
 *    until a broadcast arrives) and we do nothing else.
 * 3. If the browser is offline or the endpoint cannot be reached, the loss is
 *    not a sign-out: we wait for the "online" event (or a fixed delay when the
 *    browser reports online but the server did not answer) and verify again.
 *    A recovery by the provider itself (another tab's broadcast, any
 *    successful getSession()) ends the wait as well.
 * 4. Skip the retries when the "next-auth" BroadcastChannel announced a real
 *    signOut from another tab ({ data: { trigger: "signout" } }).
 * 5. On a CONFIRMED logout (the endpoint answered that there is no session):
 *    consumers see "unauthenticated"; hidden tab -> background redirect to the
 *    local /api/auth/login route (live Keycloak SSO restores it silently);
 *    visible tab -> NO navigation, a persistent toast with a "sign in" action
 *    lets the user leave on their own terms. The toast re-surfaces on
 *    visibilitychange while the logout persists, and is dismissed if the
 *    session recovers.
 *
 * A confirmed-logged-out tab is deliberately never auto-redirected once it has
 * shown the prompt, even if it later goes hidden — the prompt exists because
 * the page may hold unsaved state, and navigating later would still destroy it.
 *
 * The guard also lends the provider's update() to the API client: after a 401
 * the client forces a token refresh through it (see session-recovery). When
 * that refresh fails, the session comes back "authenticated" but marked
 * REFRESH_TOKEN_ERROR, without an access token. No probe can change that, so
 * the logout is confirmed at once and the same prompt is shown.
 */
export function SessionGuard({ children }: { children: React.ReactNode }) {
  const providerValue = useSession();
  const { status } = providerValue;
  const t = useTranslations("session");
  // The session last seen as authenticated; held for consumers while a loss
  // is unconfirmed. Kept in state because the render below reads it.
  const [lastSession, setLastSession] = useState<Session | null>(null);
  const [confirmedLogout, setConfirmedLogout] = useState(false);
  const verifyingRef = useRef(false);
  const confirmedLogoutRef = useRef(false);
  const signOutBroadcastRef = useRef(false);
  // Removes the pending "verify again" trigger (online listener or timer).
  const cancelReverifyRef = useRef<(() => void) | null>(null);
  // Latest status, readable from the in-flight verification loop so it can
  // abort when the provider recovers on its own mid-verification.
  const statusRef = useRef(status);
  statusRef.current = status;
  // Latest update(): the provider recreates it whenever the session changes,
  // and while it is loading the stale one returns without refreshing.
  const updateRef = useRef(providerValue.update);
  updateRef.current = providerValue.update;
  // The session is there, but its tokens could not be refreshed: every API
  // call with it fails until the user signs in again.
  const refreshFailed =
    status === "authenticated" && providerValue.data?.error === AuthError.REFRESH_TOKEN_ERROR;

  if (status === "authenticated" && providerValue.data !== lastSession) {
    setLastSession(providerValue.data);
  }

  const showExpiredToast = () => {
    toast.warning(t("expiredTitle"), {
      id: SESSION_EXPIRED_TOAST_ID,
      description: t("expiredDescription"),
      duration: Infinity,
      action: {
        label: t("signInAction"),
        onClick: () => {
          window.location.href = loginUrl();
        },
      },
    });
  };
  const showExpiredToastRef = useRef(showExpiredToast);
  showExpiredToastRef.current = showExpiredToast;

  const cancelReverify = () => {
    cancelReverifyRef.current?.();
    cancelReverifyRef.current = null;
  };

  // Verifies again once the auth endpoint can plausibly answer: on the
  // "online" event when the browser is offline, otherwise after a delay.
  const scheduleReverify = () => {
    if (cancelReverifyRef.current) return;
    const reverify = () => {
      cancelReverifyRef.current = null;
      if (statusRef.current !== "authenticated") {
        void verifySessionLossRef.current();
      }
    };
    if (!navigator.onLine) {
      window.addEventListener("online", reverify, { once: true });
      cancelReverifyRef.current = () => window.removeEventListener("online", reverify);
    } else {
      const timer = setTimeout(reverify, UNREACHABLE_RETRY_MS);
      cancelReverifyRef.current = () => clearTimeout(timer);
    }
  };

  // Treats the session as gone: consumers see "unauthenticated" (or the
  // errored session), a hidden tab goes to sign-in, a visible one gets the prompt.
  const confirmLogout = () => {
    confirmedLogoutRef.current = true;
    setConfirmedLogout(true);
    if (document.visibilityState === "hidden") {
      // Background tab: redirecting is allowed — no user state at risk of
      // surprise loss. Live Keycloak SSO restores the session silently.
      window.location.href = loginUrl();
      return;
    }
    showExpiredToastRef.current();
  };

  const verifySessionLoss = async () => {
    if (verifyingRef.current) return;
    verifyingRef.current = true;
    try {
      if (!signOutBroadcastRef.current) {
        for (let attempt = 1; attempt <= VERIFY_RETRIES; attempt++) {
          // An offline browser cannot learn anything about the session; wait
          // for the network instead of spending the retries.
          if (!navigator.onLine) {
            scheduleReverify();
            return;
          }
          await wait(VERIFY_DELAY_MS);
          // The provider may have recovered on its own (e.g. a broadcast
          // from another tab) while we slept — nothing to verify anymore.
          if (statusRef.current === "authenticated") {
            return;
          }
          const probe = await probeSession();
          if (probe === "unreachable") {
            scheduleReverify();
            return;
          }
          if (probe === "session") {
            console.warn(
              `[SessionGuard] transient session fetch failure recovered (attempt ${attempt}/${VERIFY_RETRIES})`,
            );
            // One broadcasting getSession() heals this tab's provider (it
            // stops refetching after a null session until a broadcast
            // arrives) and flips status back to "authenticated".
            await getSession();
            return;
          }
        }
      }

      // Final guard against a recovery that landed during the last probe.
      if (statusRef.current === "authenticated") {
        return;
      }

      confirmLogout();
    } finally {
      verifyingRef.current = false;
    }
  };
  const verifySessionLossRef = useRef(verifySessionLoss);
  verifySessionLossRef.current = verifySessionLoss;

  // Mark cross-tab user-initiated signOuts so verification can be skipped:
  // the session is genuinely gone, retrying would only delay the handling.
  useEffect(() => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel("next-auth");
    const onMessage = (message: MessageEvent) => {
      if (message.data?.data?.trigger === "signout") {
        signOutBroadcastRef.current = true;
      }
    };
    channel.addEventListener("message", onMessage);
    return () => {
      channel.removeEventListener("message", onMessage);
      channel.close();
    };
  }, []);

  // Re-surface the prompt when a confirmed-logged-out tab becomes visible
  // again (the user may have dismissed the toast before hiding the tab).
  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible" && confirmedLogoutRef.current) {
        showExpiredToastRef.current();
      }
    };
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => document.removeEventListener("visibilitychange", onVisibilityChange);
  }, []);

  useEffect(() => () => cancelReverifyRef.current?.(), []);

  useEffect(() => {
    registerSessionRefresher(() => updateRef.current({ forceRefresh: true }));
    return () => registerSessionRefresher(null);
  }, []);

  useEffect(() => {
    if (refreshFailed) {
      cancelReverify();
      if (!confirmedLogoutRef.current) {
        confirmLogout();
      }
      return;
    }

    if (status === "authenticated") {
      signOutBroadcastRef.current = false;
      cancelReverify();
      if (confirmedLogoutRef.current) {
        confirmedLogoutRef.current = false;
        setConfirmedLogout(false);
        toast.dismiss(SESSION_EXPIRED_TOAST_ID);
      }
      return;
    }

    if (status !== "unauthenticated" || lastSession === null || confirmedLogoutRef.current) {
      return;
    }

    void verifySessionLossRef.current();
  }, [status, lastSession, refreshFailed]);

  // While a loss is unconfirmed, consumers keep the last authenticated
  // session instead of a null one that may only mean "offline".
  const value = useMemo<SessionContextValue>(() => {
    if (status === "unauthenticated" && lastSession !== null && !confirmedLogout) {
      return { data: lastSession, status: "authenticated", update: providerValue.update };
    }
    return providerValue;
  }, [status, lastSession, confirmedLogout, providerValue]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
