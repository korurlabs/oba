/**
 * Failures of the sign-in flow. The "Signing in failed" screen shows `code`
 * ("Error code: invalid_grant"); a cancelled login returns to Sign in without
 * an error, so it has its own class the screen can ignore.
 */

/** Code of a failure that carries no OAuth error code (no connection, a storage failure). */
export const SIGN_IN_FAILED_CODE = "sign_in_failed";

/** Code when Keycloak's discovery document could not be fetched. */
export const DISCOVERY_FAILED_CODE = "discovery_failed";

/** Code when the token response holds no access or refresh token. */
export const INCOMPLETE_TOKEN_RESPONSE_CODE = "incomplete_token_response";

/** A sign-in that failed; `code` is the OAuth error code when Keycloak sent one. */
export class SignInError extends Error {
  readonly code: string;

  constructor(code: string, message: string, options?: { cause?: unknown }) {
    super(message, { cause: options?.cause });
    this.name = "SignInError";
    this.code = code;
  }
}

/** The user closed the login page; not an error to show. */
export class SignInCancelledError extends Error {
  constructor() {
    super("Sign-in cancelled");
    this.name = "SignInCancelledError";
  }
}

export function isSignInCancelled(error: unknown): error is SignInCancelledError {
  return error instanceof SignInCancelledError;
}

/**
 * The OAuth error code of an expo-auth-session error (its `code` is the
 * `error` parameter of the response), or SIGN_IN_FAILED_CODE.
 */
export function oauthErrorCode(error: unknown): string {
  const code: unknown =
    error !== null && typeof error === "object" ? (error as { code?: unknown }).code : undefined;
  return typeof code === "string" && /^[a-z_]+$/.test(code) ? code : SIGN_IN_FAILED_CODE;
}

/** The SignInError for any failure of the flow; a SignInError or cancellation passes through. */
export function toSignInError(error: unknown): SignInError | SignInCancelledError {
  if (error instanceof SignInError || error instanceof SignInCancelledError) return error;
  const message: string = error instanceof Error ? error.message : String(error);
  return new SignInError(oauthErrorCode(error), message, { cause: error });
}
