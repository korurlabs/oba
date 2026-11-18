import { NextRequest, NextResponse } from "next/server";
import { getToken } from "next-auth/jwt";
import { clearSessionCookies } from "./clear-session-cookies";
import { sessionCookieName } from "./session-cookie";

/**
 * Server logout route.
 *
 * Apps mount it at app/api/auth/logout/route.ts via:
 *
 *   export { GET, POST } from "@oba/shared/libs/auth/logout-route";
 *
 * POST starts Log out (a top-level form submit from the app, see
 * ./logout-client): it clears the session cookies and sends the browser to the
 * Keycloak end-session endpoint, so the Keycloak SSO session ends too and the
 * next sign-in asks for the password again.
 *
 * GET finishes it: Keycloak returns the browser here after ending its session.
 * It clears the cookies a second time, because a session read that was in
 * flight while POST ran can have written the cookie back, and lands the user
 * signed out on the public home.
 *
 * Like the login route this lives under /api, which the proxy matcher
 * excludes, so no proxy session read rewrites the cookie during either step.
 */

const LOGOUT_ROUTE = "/api/auth/logout";
const SIGNED_OUT_LANDING = "/";
const SAME_ORIGIN = "same-origin";

export async function POST(request: NextRequest): Promise<Response> {
  // Without this check any site could end the user's Keycloak session with an
  // auto-submitting form. Browsers that send no Sec-Fetch-Site header at all
  // are let through, so that Log out keeps working in them.
  const fetchSite: string | null = request.headers.get("sec-fetch-site");
  if (fetchSite !== null && fetchSite !== SAME_ORIGIN) {
    return new NextResponse(null, { status: 403 });
  }

  // Read the id token before the cookies are cleared: Keycloak needs it as the
  // hint to end this session without asking the user to confirm. It stays on
  // the server; it never reaches the client session.
  const cookieName: string = sessionCookieName(process.env.AUTH_URL);
  const token = await getToken({
    req: request,
    secret: process.env.AUTH_SECRET,
    cookieName,
    salt: cookieName,
  });

  await clearSessionCookies();

  const origin: string = appOrigin(request);
  const issuer: string | undefined = process.env.AUTH_KEYCLOAK_ISSUER;
  const clientId: string | undefined = process.env.AUTH_KEYCLOAK_ID;
  if (!issuer || !clientId) {
    console.error(
      "[Auth] Logout cannot end the Keycloak session: AUTH_KEYCLOAK_ISSUER or AUTH_KEYCLOAK_ID is missing"
    );
    return NextResponse.redirect(new URL(SIGNED_OUT_LANDING, origin), 303);
  }

  // post_logout_redirect_uri, never redirect_uri: Keycloak 18 and later reject
  // redirect_uri on this endpoint.
  const endSessionUrl: URL = new URL(`${issuer}/protocol/openid-connect/logout`);
  endSessionUrl.searchParams.set("client_id", clientId);
  endSessionUrl.searchParams.set("post_logout_redirect_uri", new URL(LOGOUT_ROUTE, origin).toString());
  if (typeof token?.id_token === "string") {
    endSessionUrl.searchParams.set("id_token_hint", token.id_token);
  }

  // 303 so the browser follows with a GET, whatever the method of this request.
  return NextResponse.redirect(endSessionUrl, 303);
}

export async function GET(request: NextRequest): Promise<Response> {
  await clearSessionCookies();
  return NextResponse.redirect(new URL(SIGNED_OUT_LANDING, appOrigin(request)), 303);
}

/**
 * The public origin of this app. AUTH_URL comes first because Keycloak only
 * accepts the post-logout redirect URIs registered for the client, and those
 * are the public URLs; the request origin is the fallback for a missing or
 * invalid AUTH_URL.
 */
function appOrigin(request: NextRequest): string {
  const authUrl: string | undefined = process.env.AUTH_URL;
  if (authUrl && URL.canParse(authUrl)) {
    return new URL(authUrl).origin;
  }
  return request.nextUrl.origin;
}
