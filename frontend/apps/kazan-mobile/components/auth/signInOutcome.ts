import { isSignInCancelled, SIGN_IN_FAILED_CODE, SignInError } from "@/contexts/signInErrors";

/**
 * What the sign-in screens do after `signIn()` rejected: nothing when the user
 * closed the login page, the "Signing in failed" screen with `code` otherwise.
 */
export type SignInFailure = { kind: "cancelled" } | { kind: "failed"; code: string };

/** Sorts a rejection of `signIn()` into a cancel or a failure with its OAuth code. */
export function classifySignInFailure(error: unknown): SignInFailure {
  if (isSignInCancelled(error)) return { kind: "cancelled" };
  if (error instanceof SignInError && error.code.trim() !== "") {
    return { kind: "failed", code: error.code };
  }
  return { kind: "failed", code: SIGN_IN_FAILED_CODE };
}
