import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";

import { auth, signIn } from "@/lib/auth";
import { safeCallbackPath } from "@/lib/auth/callback-url";
import { legacyAuthCookieNames } from "@/lib/auth/cookies";
import {
  REAUTH_COOKIE,
  REAUTH_PARAM,
  REAUTH_WINDOW_SECONDS,
  SESSION_REJECTED,
} from "@/lib/auth/reauth";

/** The home page with `?error=`, which renders the reason instead of starting another sign-in. */
function homeWithError(request: NextRequest, error: string): NextResponse {
  const home = new URL("/", request.nextUrl);
  home.searchParams.set("error", error);
  return NextResponse.redirect(home);
}

/**
 * Expires the cookies Kaya wrote under the Auth.js default names before it had
 * its own; see legacyAuthCookieNames for why this cannot touch Oba's session.
 * Every user passes here once, because the new names start them signed out.
 * The expiring cookie repeats the attributes a `__Secure-` or `__Host-` name
 * requires, or the browser would ignore it.
 */
async function expireLegacyAuthCookies(request: NextRequest): Promise<void> {
  const names = legacyAuthCookieNames(
    request.cookies.getAll().map((cookie) => cookie.name),
    process.env.AUTH_URL
  );
  if (names.length === 0) {
    return;
  }
  const store = await cookies();
  for (const name of names) {
    store.set(name, "", { httpOnly: true, sameSite: "lax", secure: true, path: "/", maxAge: 0 });
  }
}

/**
 * Sign-in entry point: sends the browser straight to the Keycloak login of the
 * `kaya` realm and back to `callbackUrl` afterwards.
 *
 * Auth.js sends a failed sign-in back here with `?error=`; starting another
 * sign-in then would bounce between Kaya and Keycloak forever, so the user
 * lands on the home page with the error instead.
 *
 * A visitor who is already signed in goes straight back to `callbackUrl`,
 * unless the page sent `?reauth=1` because Kaya's API refused the session:
 * returning to the page would only be refused again, so a fresh sign-in
 * replaces the session. A second forced sign-in within REAUTH_WINDOW_SECONDS
 * means the fresh tokens were refused as well; the user then sees
 * SESSION_REJECTED, since another sign-in would loop through Keycloak.
 *
 * `signIn` redirects by throwing NEXT_REDIRECT, which must reach Next.js, so
 * it is not wrapped in a try.
 */
export async function GET(request: NextRequest): Promise<Response> {
  const params = request.nextUrl.searchParams;
  const callbackUrl = safeCallbackPath(params.get("callbackUrl")) ?? "/";
  const error = params.get("error");
  if (error !== null) {
    return homeWithError(request, error);
  }
  if (params.has(REAUTH_PARAM)) {
    if (request.cookies.has(REAUTH_COOKIE)) {
      const rejected = homeWithError(request, SESSION_REJECTED);
      rejected.cookies.delete(REAUTH_COOKIE);
      return rejected;
    }
    (await cookies()).set(REAUTH_COOKIE, "1", {
      httpOnly: true,
      sameSite: "lax",
      secure: request.nextUrl.protocol === "https:",
      path: "/",
      maxAge: REAUTH_WINDOW_SECONDS,
    });
    await expireLegacyAuthCookies(request);
    return signIn("keycloak", { redirectTo: callbackUrl });
  }
  const session = await auth();
  if (session !== null && session.error === undefined) {
    return NextResponse.redirect(new URL(callbackUrl, request.nextUrl));
  }
  await expireLegacyAuthCookies(request);
  return signIn("keycloak", { redirectTo: callbackUrl });
}
