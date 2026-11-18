import NextAuth, { type NextAuthResult } from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

import { kayaAuthCookies } from "./cookies";
import { isTokenValid, refreshAccessToken } from "./token-refresh";
import { AuthError } from "./types";

/** Signing in starts at this route, which sends the browser straight to Keycloak. */
export const SIGN_IN_PATH = "/api/auth/login";

const issuer = process.env.AUTH_KEYCLOAK_ISSUER;

const result = NextAuth({
  providers: [
    Keycloak({
      // Explicit endpoints, derived from the issuer, let Auth.js skip the OIDC
      // discovery request it would otherwise make on the first sign-in.
      authorization: issuer
        ? {
            url: `${issuer}/protocol/openid-connect/auth`,
            params: { scope: "openid profile email" },
          }
        : { params: { scope: "openid profile email" } },
      ...(issuer && {
        token: `${issuer}/protocol/openid-connect/token`,
        userinfo: `${issuer}/protocol/openid-connect/userinfo`,
        jwks_endpoint: `${issuer}/protocol/openid-connect/certs`,
      }),
      clientId: process.env.AUTH_KEYCLOAK_ID,
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET,
      issuer,
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          expiresAt: account.expires_at,
          idToken: account.id_token,
          error: undefined,
        };
      }
      if (isTokenValid(token)) {
        return token;
      }
      return refreshAccessToken(token);
    },
    session({ session, token }) {
      session.accessToken = token.accessToken;
      session.expiresAt = token.expiresAt;
      session.error = token.accessToken === undefined ? AuthError.REFRESH_TOKEN_ERROR : token.error;
      return session;
    },
  },
  session: { strategy: "jwt" },
  cookies: kayaAuthCookies(process.env.AUTH_URL),
  pages: { signIn: SIGN_IN_PATH },
  events: {
    async signOut(message) {
      // Kaya's buttons sign out through the logout route, but the Auth.js route
      // still serves its own sign-out page at /api/auth/signout, whose form
      // lands here. That sign-out clears only Kaya's cookie, so this ends the
      // Keycloak session that issued Kaya's id token, on a best-effort basis;
      // ending the browser's current SSO session takes a browser visit to
      // Keycloak, which only the logout route makes.
      const token = "token" in message ? message.token : null;
      if (!issuer || !token?.idToken) {
        return;
      }
      try {
        const url = new URL(`${issuer}/protocol/openid-connect/logout`);
        url.searchParams.set("id_token_hint", token.idToken);
        const response = await fetch(url);
        if (!response.ok) {
          console.error(`[auth] Keycloak logout answered ${response.status}`);
        }
      } catch (error) {
        // The session cookie is cleared regardless; the Keycloak session may live on.
        console.error("[auth] Keycloak logout failed", error);
      }
    },
  },
  trustHost: true,
});

export const handlers: NextAuthResult["handlers"] = result.handlers;
export const auth: NextAuthResult["auth"] = result.auth;
export const signIn: NextAuthResult["signIn"] = result.signIn;
export const signOut: NextAuthResult["signOut"] = result.signOut;
export { AuthError };
