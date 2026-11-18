import type { NextRequest } from "next/server";
import { handlers } from "./index";
import { sessionCookieName } from "./session-cookie";
import {
  callbackUrlCookieName,
  expiredCookieHeader,
  isSecureAuthUrl,
  signInRetryCookieName,
  writesSessionCookie,
} from "./sign-in-retry";

/**
 * The Auth.js route handlers, which also end the bookkeeping of a sign-in
 * once its callback succeeds.
 *
 * Apps mount them at app/api/auth/[...nextauth]/route.ts via:
 *
 *   export { GET, POST } from "@oba/shared/libs/auth/auth-route";
 *
 * A successful callback removes two cookies that would otherwise outlive the
 * sign-in:
 *  - the retry mark (see ./sign-in-failure): left in place, a real failure
 *    within its window would show "Signing in failed" without the one
 *    automatic retry it is owed;
 *  - the Auth.js callback URL cookie: the root page offers its value as the
 *    destination of "Sign in again", which must not be a page the user left
 *    long ago. The next sign-in writes it afresh, and Auth.js writes the
 *    app's origin into it on any later request without one.
 *
 * Both are removed in the callback because only there does a sign-in
 * succeed, and the session cookie it writes proves it; no other request pays
 * for the check. The root page cannot set cookies.
 */

const CALLBACK_PATH_SEGMENT = "/api/auth/callback/";

export async function GET(request: NextRequest): Promise<Response> {
  return endSignInBookkeeping(request, await handlers.GET(request));
}

export async function POST(request: NextRequest): Promise<Response> {
  return endSignInBookkeeping(request, await handlers.POST(request));
}

/**
 * Adds the removal of the retry mark and the callback URL cookie to the
 * response of a callback that signed the user in. Both are removed even when
 * the request did not carry them, because Auth.js may write the callback URL
 * cookie in this same response; the removal comes after it and wins.
 */
function endSignInBookkeeping(request: NextRequest, response: Response): Response {
  if (!request.nextUrl.pathname.includes(CALLBACK_PATH_SEGMENT)) return response;

  const authUrl = process.env.AUTH_URL;
  if (!writesSessionCookie(response.headers.getSetCookie(), sessionCookieName(authUrl))) {
    return response;
  }

  const headers = new Headers(response.headers);
  const secure = isSecureAuthUrl(authUrl);
  for (const name of [signInRetryCookieName(authUrl), callbackUrlCookieName(authUrl)]) {
    headers.append("Set-Cookie", expiredCookieHeader(name, secure));
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
