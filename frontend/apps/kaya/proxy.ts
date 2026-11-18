import { NextResponse } from "next/server";
import type { NextAuthRequest } from "next-auth";

import { auth, SIGN_IN_PATH } from "@/lib/auth";

/**
 * Sends a signed-out visitor, or one whose session can no longer be
 * refreshed, to the Keycloak sign-in before any page renders, and back to the
 * same page afterwards. The identity gate still handles a session lost while
 * a page is open, and the membership refusals.
 *
 * A page opened with `?error=` is where a failed sign-in lands; it renders
 * "Signing in failed" instead of starting another sign-in, which would loop.
 *
 * The handler runs inside Auth.js's `auth()` wrapper rather than calling
 * `auth()` itself, because the wrapper writes a refreshed session cookie into
 * whatever response the handler returns; a bare call would drop the rotated
 * refresh token.
 */
export default auth((request: NextAuthRequest): NextResponse => {
  const signedIn = request.auth !== null && request.auth.error === undefined;
  if (signedIn || request.nextUrl.searchParams.has("error")) {
    return NextResponse.next();
  }
  const signIn = new URL(SIGN_IN_PATH, request.nextUrl);
  signIn.searchParams.set("callbackUrl", `${request.nextUrl.pathname}${request.nextUrl.search}`);
  return NextResponse.redirect(signIn);
});

export const config = {
  // Auth.js routes, Next.js assets and public files (anything with a dot) pass untouched.
  matcher: "/((?!api|_next|.*\\..*).*)",
};
