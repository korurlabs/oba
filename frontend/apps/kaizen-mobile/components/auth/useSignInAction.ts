import { useCallback } from "react";
import { usePathname, useRouter } from "expo-router";

import { classifySignInFailure } from "@/components/auth/signInOutcome";
import { useAuth } from "@/contexts/AuthContext";

/** The "Signing in failed" route; its `code` parameter is the OAuth error code. */
export const SIGN_IN_FAILED_ROUTE = "/sign-in-failed";

/** The Sign in route, where a cancelled login returns. */
export const SIGN_IN_ROUTE = "/sign-in";

/**
 * Starts the Keycloak login from a sign-in screen. A failed login opens
 * "Signing in failed" with the error code; a cancelled one shows no error and
 * lands on Sign in. Success needs no navigation: the `(auth)` layout leaves for
 * the app as soon as the session exists.
 *
 * `pending` is true while the flow runs, for the button spinner.
 */
export function useSignInAction() {
  const router = useRouter();
  const pathname: string = usePathname();
  const { signIn, signInMutation } = useAuth();

  const start = useCallback(async (): Promise<void> => {
    try {
      await signIn();
    } catch (error: unknown) {
      const failure = classifySignInFailure(error);
      // AuthProvider has logged the failure with its message; the screen shows the code.
      const onFailedScreen: boolean = pathname === SIGN_IN_FAILED_ROUTE;
      if (failure.kind === "failed") {
        // Back from "Signing in failed" returns to Sign in.
        if (onFailedScreen) router.setParams({ code: failure.code });
        else router.push({ pathname: SIGN_IN_FAILED_ROUTE, params: { code: failure.code } });
      } else if (onFailedScreen) {
        if (router.canGoBack()) router.back();
        else router.replace(SIGN_IN_ROUTE);
      }
    }
  }, [pathname, router, signIn]);

  const pending: boolean = signInMutation.isPending;

  return { start, pending };
}
