import { NextResponse, type NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";

import { kayaSessionCookieName } from "@/lib/auth/cookies";
import {
  endSessionUrl,
  kayaOrigin,
  kayaSessionCookieNames,
  SIGNED_OUT_LANDING,
} from "@/lib/auth/logout";

const SAME_ORIGIN = "same-origin";

/**
 * Starts the sign-out (a top-level form POST, see submitLogout): clears Kaya's
 * session and sends the browser to Keycloak's end-session endpoint.
 *
 * The browser has to go there itself. The SSO session lives in Keycloak's
 * cookie, and it may be newer than the session that issued Kaya's id token,
 * for example after the user signed out of Kazan and in again. A server-side
 * logout with that id token would end only the old session, and the next
 * sign-in would silently reuse the new one.
 */
export async function POST(request: NextRequest): Promise<Response> {
  // Without this check any site could end the user's Keycloak session with an
  // auto-submitting form. Browsers that send no Sec-Fetch-Site header at all
  // are let through, so that signing out keeps working in them.
  const fetchSite = request.headers.get("sec-fetch-site");
  if (fetchSite !== null && fetchSite !== SAME_ORIGIN) {
    return new NextResponse(null, { status: 403 });
  }

  const authUrl = process.env.AUTH_URL;
  const origin = kayaOrigin(authUrl, request.nextUrl.origin);
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER;
  const clientId = process.env.AUTH_KEYCLOAK_ID;
  if (!issuer || !clientId) {
    console.error(
      "[auth] Sign-out cannot end the Keycloak session: AUTH_KEYCLOAK_ISSUER or AUTH_KEYCLOAK_ID is missing"
    );
    return expireSession(NextResponse.redirect(new URL(SIGNED_OUT_LANDING, origin), 303), authUrl);
  }

  // Read before the cookies are cleared. The id token stays on the server and
  // only travels to Keycloak in the redirect.
  const cookieName = kayaSessionCookieName(authUrl);
  const token = await getToken({
    req: request,
    secret: process.env.AUTH_SECRET,
    cookieName,
    salt: cookieName,
  });
  const idToken = typeof token?.idToken === "string" ? token.idToken : undefined;

  // 303, so the browser follows with a GET whatever the method of this request.
  const response = NextResponse.redirect(endSessionUrl(issuer, clientId, origin, idToken), 303);
  return expireSession(response, authUrl);
}

/**
 * Finishes the sign-out: Keycloak returns the browser here after ending its
 * session. The cookies are cleared a second time, because a session read that
 * was in flight while POST ran can have written the cookie back. The landing
 * page then starts a sign-in, which now asks for the password.
 */
export async function GET(request: NextRequest): Promise<Response> {
  const authUrl = process.env.AUTH_URL;
  const landing = new URL(SIGNED_OUT_LANDING, kayaOrigin(authUrl, request.nextUrl.origin));
  return expireSession(NextResponse.redirect(landing, 303), authUrl);
}

/**
 * Expires Kaya's session cookie and all of its chunks. Host-only like the
 * cookies Auth.js writes for Kaya, and named with Kaya's prefix, so the Oba
 * apps' session survives. `secure` repeats what a `__Secure-` name requires,
 * or the browser would ignore the expiry.
 */
function expireSession(response: NextResponse, authUrl: string | undefined): NextResponse {
  const secure = kayaSessionCookieName(authUrl).startsWith("__Secure-");
  for (const name of kayaSessionCookieNames(authUrl)) {
    response.cookies.set(name, "", {
      httpOnly: true,
      sameSite: "lax",
      secure,
      path: "/",
      maxAge: 0,
    });
  }
  return response;
}
