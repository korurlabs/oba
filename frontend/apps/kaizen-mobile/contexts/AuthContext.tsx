import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useMutation, useQueryClient, type UseMutationResult } from "@tanstack/react-query";
import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";

import i18n from "@/i18n";
import {
  api,
  clearPushDeviceId,
  clearTokens,
  getPushDeviceId,
  getRefreshToken,
  keycloakAxios,
  onSessionEnded,
  settleWithin,
  storeTokens,
} from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import {
  buildRedirectUri,
  buildSignInExtraParams,
  clearDiscoveryCache,
  getDiscovery,
  getKeycloakClientId,
  OAUTH_SCOPES,
} from "@/lib/oauthConfig";
import {
  clearActiveOrganizationId,
  hydrateActiveOrganizationId,
  setActiveOrganizationId,
  useActiveOrganizationId,
} from "@/lib/organizationStore";
import { clearDeliveredNotifications } from "@/lib/push";
import { clearSessionStores } from "@/contexts/sessionStores";
import {
  DISCOVERY_FAILED_CODE,
  INCOMPLETE_TOKEN_RESPONSE_CODE,
  oauthErrorCode,
  SignInCancelledError,
  SignInError,
  toSignInError,
} from "@/contexts/signInErrors";

// Completes a pending web auth popup; a no-op on iOS and Android.
WebBrowser.maybeCompleteAuthSession();

/**
 * - `loading`: the stored session is being read at start; show the splash.
 * - `signed-out`: no session; the Sign in screen.
 * - `signed-in`: a refresh token is stored. The access token may be expired;
 *   the first request refreshes it.
 */
export type AuthStatus = "loading" | "signed-out" | "signed-in";

/**
 * Why the last session ended: `user` after Sign out, `session-ended` when
 * Keycloak rejected the refresh token (Sign in then shows "Your session ended.
 * Sign in again."). Null after a sign-in and at a fresh start.
 */
export type SignOutReason = "user" | "session-ended" | null;

/**
 * What the server side of a sign-out achieved. Local state is always cleared;
 * these two steps are best effort, so a screen can say when one did not happen.
 * `null` means there was nothing to do (no push device, no refresh token).
 */
export interface SignOutOutcome {
  pushDeviceUnregistered: boolean | null;
  grantRevoked: boolean | null;
}

interface AuthContextValue {
  status: AuthStatus;
  signOutReason: SignOutReason;
  /** The organization whose data the app shows, or null before one is chosen. */
  activeOrganizationId: string | null;
  /**
   * Runs the Keycloak login and stores the tokens. Rejects with
   * SignInCancelledError when the user closed the page, else SignInError.
   */
  signIn: () => Promise<void>;
  /** Unregisters push, revokes the grant, clears SecureStore and the query cache. */
  signOut: () => Promise<SignOutOutcome>;
  /** Makes an organization active and reloads every query for it. */
  switchOrganization: (organizationId: string) => Promise<void>;
  /** For button state (`isPending`). */
  signInMutation: UseMutationResult<void, SignInError | SignInCancelledError, void>;
  signOutMutation: UseMutationResult<SignOutOutcome, Error, void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Bound of the discovery wait and the end-session POST at sign-out. */
const REVOKE_TIMEOUT_MS = 10_000;

/**
 * Ends the Keycloak session of `refreshToken` with a direct POST to the
 * end-session endpoint, without a browser round trip. Returns false when it
 * failed; the failure is logged and the local sign-out goes on.
 */
async function revokeGrant(refreshToken: string): Promise<boolean> {
  try {
    const discovery = await settleWithin(getDiscovery(), REVOKE_TIMEOUT_MS, "Keycloak discovery");
    if (!discovery.endSessionEndpoint) {
      console.warn("[auth] discovery has no end-session endpoint; the grant is not revoked");
      return false;
    }
    const body = new URLSearchParams({
      client_id: getKeycloakClientId(),
      refresh_token: refreshToken,
    });
    await keycloakAxios.post(discovery.endSessionEndpoint, body.toString(), {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      timeout: REVOKE_TIMEOUT_MS,
    });
    return true;
  } catch (error: unknown) {
    console.warn(
      "[auth] Keycloak grant revocation failed; signing out locally",
      error instanceof Error ? error.message : error
    );
    return false;
  }
}

/**
 * Removes this phone's push registration while the tokens still work, so the
 * signed-out user gets no more pushes here. Returns false when it failed; the
 * failure is logged and sign-out goes on.
 */
async function unregisterPushDevice(): Promise<boolean | null> {
  let deviceId: string | null;
  try {
    deviceId = await getPushDeviceId();
  } catch (error: unknown) {
    console.warn(
      "[auth] reading the push device id failed; the device stays registered",
      error instanceof Error ? error.message : error
    );
    return false;
  }
  if (deviceId === null) return null;
  try {
    // The sign-out outcome reports this failure, so no global toast.
    await api.delete(API_ROUTES.push.device(deviceId), { _silentErrors: true });
    return true;
  } catch (error: unknown) {
    console.warn(
      "[auth] unregistering the push device failed",
      error instanceof Error ? error.message : error
    );
    return false;
  }
}

/** Runs the authorize round trip and the code exchange, and stores the tokens. */
async function runSignIn(forceLoginForm: boolean): Promise<void> {
  let discovery: AuthSession.DiscoveryDocument;
  try {
    discovery = await getDiscovery();
  } catch (error: unknown) {
    throw new SignInError(
      DISCOVERY_FAILED_CODE,
      error instanceof Error ? error.message : "Keycloak discovery failed",
      { cause: error }
    );
  }
  const redirectUri: string = buildRedirectUri();
  const extraParams: Record<string, string> = buildSignInExtraParams({
    uiLocale: i18n.language,
    forceLoginForm,
  });

  const request = new AuthSession.AuthRequest({
    clientId: getKeycloakClientId(),
    redirectUri,
    scopes: [...OAUTH_SCOPES],
    usePKCE: true,
    extraParams,
  });
  // An ephemeral session leaves no Keycloak cookie in the iOS auth browser, so
  // the next sign-in after Sign out cannot silently return the previous user.
  const result: AuthSession.AuthSessionResult = await request.promptAsync(discovery, {
    preferEphemeralSession: true,
  });
  if (result.type === "error") {
    const code: string = result.error ? oauthErrorCode(result.error) : (result.params.error ?? "");
    throw new SignInError(
      code || oauthErrorCode(undefined),
      result.error?.message ?? result.params.error_description ?? "Authorization failed",
      { cause: result.error }
    );
  }
  if (result.type !== "success") {
    throw new SignInCancelledError();
  }
  if (!request.codeVerifier) {
    throw new SignInError(INCOMPLETE_TOKEN_RESPONSE_CODE, "PKCE code verifier missing");
  }

  const tokens: AuthSession.TokenResponse = await AuthSession.exchangeCodeAsync(
    {
      clientId: getKeycloakClientId(),
      code: result.params.code,
      redirectUri,
      extraParams: { code_verifier: request.codeVerifier },
    },
    discovery
  );
  if (!tokens.accessToken || !tokens.refreshToken) {
    throw new SignInError(
      INCOMPLETE_TOKEN_RESPONSE_CODE,
      "The token response has no access or refresh token"
    );
  }
  await storeTokens({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    idToken: tokens.idToken ?? null,
  });
}

/**
 * The session of the app: Keycloak sign-in with PKCE, sign-out, the session
 * end reported by the 401 refresh in lib/api.ts, and the active organization.
 * Sits inside QueryClientProvider, because sign-in and sign-out are mutations
 * and both clear the query cache. Route guards read `status`; this provider
 * does not navigate.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [signOutReason, setSignOutReason] = useState<SignOutReason>(null);
  const activeOrganizationId: string | null = useActiveOrganizationId();

  // Start: signed in when a refresh token is stored. A failed read shows Sign
  // in and keeps the stored tokens for the next start.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      let nextStatus: AuthStatus = "signed-out";
      try {
        nextStatus = (await getRefreshToken()) !== null ? "signed-in" : "signed-out";
      } catch (error: unknown) {
        console.warn(
          "[auth] reading the stored session failed; showing Sign in",
          error instanceof Error ? error.message : error
        );
      }
      if (nextStatus === "signed-in") {
        try {
          await hydrateActiveOrganizationId();
        } catch (error: unknown) {
          // The organization is picked again from the membership list.
          console.warn(
            "[auth] reading the active organization failed; it is chosen again",
            error instanceof Error ? error.message : error
          );
        }
      }
      if (!cancelled) setStatus(nextStatus);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Keycloak rejected the refresh token: lib/api.ts has cleared the tokens.
  useEffect(
    () =>
      onSessionEnded(() => {
        console.warn("[auth] the session ended; returning to Sign in");
        queryClient.clear();
        clearSessionStores();
        setSignOutReason("session-ended");
        setStatus("signed-out");
      }),
    [queryClient]
  );

  const signInMutation = useMutation<void, SignInError | SignInCancelledError, void>({
    mutationFn: async () => {
      try {
        await runSignIn(signOutReason === "user");
      } catch (error: unknown) {
        const signInError = toSignInError(error);
        if (signInError instanceof SignInError) {
          console.warn("[auth] sign-in failed", signInError.code, signInError.message);
        }
        throw signInError;
      }
    },
    onSuccess: () => {
      queryClient.clear();
      setSignOutReason(null);
      setStatus("signed-in");
    },
  });

  const signOutMutation = useMutation<SignOutOutcome, Error, void>({
    mutationFn: async () => {
      const pushDeviceUnregistered: boolean | null = await unregisterPushDevice();

      let refreshToken: string | null = null;
      try {
        refreshToken = await getRefreshToken();
      } catch (error: unknown) {
        console.warn(
          "[auth] reading the refresh token for revocation failed",
          error instanceof Error ? error.message : error
        );
      }
      const grantRevoked: boolean | null =
        refreshToken !== null ? await revokeGrant(refreshToken) : null;

      // The local clear always runs in full; a failed step is reported after it.
      // The push device id is dropped even when unregistering failed: the
      // next sign-in registers this phone afresh, and the outcome tells the
      // screen to warn that pushes may still arrive.
      const failures: string[] = [];
      const steps: [string, () => Promise<void> | void][] = [
        ["tokens", clearTokens],
        ["push device id", clearPushDeviceId],
        ["active organization", clearActiveOrganizationId],
        ["discovery cache", clearDiscoveryCache],
        ["query cache", () => queryClient.clear()],
        ["filters, nudges and unsent messages", clearSessionStores],
        ["delivered notifications", clearDeliveredNotifications],
      ];
      for (const [name, step] of steps) {
        try {
          await step();
        } catch (error: unknown) {
          console.warn(
            `[auth] sign-out step "${name}" failed`,
            error instanceof Error ? error.message : error
          );
          failures.push(name);
        }
      }
      setSignOutReason("user");
      setStatus("signed-out");
      if (failures.length > 0) {
        throw new Error(`Local sign-out incomplete: ${failures.join(", ")}`);
      }
      return { pushDeviceUnregistered, grantRevoked };
    },
  });

  const signIn = useCallback(async (): Promise<void> => {
    await signInMutation.mutateAsync();
  }, [signInMutation]);

  const signOut = useCallback(
    (): Promise<SignOutOutcome> => signOutMutation.mutateAsync(),
    [signOutMutation]
  );

  const switchOrganization = useCallback(
    async (organizationId: string): Promise<void> => {
      await setActiveOrganizationId(organizationId);
      // Active queries drop the previous organization's data and refetch
      // with the new header, so lists show their skeletons meanwhile.
      await queryClient.resetQueries();
    },
    [queryClient]
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      signOutReason,
      activeOrganizationId,
      signIn,
      signOut,
      switchOrganization,
      signInMutation,
      signOutMutation,
    }),
    [
      status,
      signOutReason,
      activeOrganizationId,
      signIn,
      signOut,
      switchOrganization,
      signInMutation,
      signOutMutation,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** The session. Throws outside AuthProvider. */
export function useAuth(): AuthContextValue {
  const context: AuthContextValue | null = useContext(AuthContext);
  if (context === null) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
