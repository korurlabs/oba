import { usersApi } from "../../features/users/api";
import NextAuth, { type NextAuthResult } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import Keycloak from "next-auth/providers/keycloak";
import { isTokenValid } from "./token-expiry";
import { refreshAccessToken } from "./token-refresh";
import "./types";
import { AuthError } from "./types";
import { decodeToken } from "./token-decode";
import { devLog } from "@oba/shared/helpers/dev-log";
import { handleUserStatus } from "./handle-user-status";
import { isDevelopmentLocalhostUrl } from "./callback-url";
import { authCookieOverrides } from "./session-cookie";
import { clientDefaultOrganizationId } from "./session-update";

const ERROR_PAGE_URL = "/";
// Each app handles its own sign-in entry point: a local GET route that
// initiates the Keycloak OAuth flow server-side (see ./login-route). There is
// no separate login app. Relative path so it resolves against the current app.
const LOGIN_PAGE_URL = "/api/auth/login";
const LOGOUT_PAGE_URL = "/";

// Keycloak issuer, read once for building the explicit provider endpoints
// below. The rest of the auth stack (token refresh, dev credentials login)
// already assumes this env var is set; when it is missing we fall back to
// OIDC discovery, which only defers the failure to the first sign-in.
const keycloakIssuer = process.env.AUTH_KEYCLOAK_ISSUER;

const result = NextAuth({
  cookies: authCookieOverrides(process.env.AUTH_URL),
  providers: [
    Keycloak({
      // Explicit Keycloak endpoints — derived deterministically from the
      // issuer ({issuer}/protocol/openid-connect/*). Supplying these makes
      // Auth.js SKIP OIDC discovery entirely (the .well-known fetch on the
      // first sign-in/callback after every pod start, which sits directly on
      // the /api/auth/login redirect path):
      //  - signin only discovers when `authorization.url` is undefined,
      //  - callback only discovers when `token.url` AND `userinfo.url` are
      //    both undefined.
      // Same pattern as Kagi (projects/kagi/frontend/.../lib/auth/auth.ts).
      // `jwks_endpoint` is not read on the code flow path; set for intent.
      authorization: keycloakIssuer
        ? {
            url: `${keycloakIssuer}/protocol/openid-connect/auth`,
            params: { scope: "openid profile email" },
          }
        : { params: { scope: "openid profile email" } },
      ...(keycloakIssuer && {
        token: `${keycloakIssuer}/protocol/openid-connect/token`,
        userinfo: `${keycloakIssuer}/protocol/openid-connect/userinfo`,
        jwks_endpoint: `${keycloakIssuer}/protocol/openid-connect/certs`,
      }),
      clientId: process.env.AUTH_KEYCLOAK_ID,
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET,
      issuer: process.env.AUTH_KEYCLOAK_ISSUER,
    }),
    Credentials({
      credentials: {
        email: { label: "Email", type: "text" },
        password: { label: "Password", type: "password" },
      },
      async authorize(credentials) {
        if (process.env.NODE_ENV !== "development") return null;

        const issuer = process.env.AUTH_KEYCLOAK_ISSUER;
        const clientId = process.env.AUTH_KEYCLOAK_ID;
        const clientSecret = process.env.AUTH_KEYCLOAK_SECRET;

        if (!issuer || !clientId || !clientSecret) return null;

        try {
          const tokenUrl = `${issuer}/protocol/openid-connect/token`;
          const response = await fetch(tokenUrl, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
              grant_type: "password",
              client_id: clientId,
              client_secret: clientSecret,
              username: credentials.email as string,
              password: credentials.password as string,
              scope: "openid profile email",
            }),
          });

          if (!response.ok) return null;

          const data = await response.json();

          return {
            id: data.sub ?? "credentials-user",
            email: credentials.email as string,
            access_token: data.access_token,
            refresh_token: data.refresh_token,
            expires_in: data.expires_in,
            id_token: data.id_token,
          };
        } catch {
          return null;
        }
      },
    }),
  ],
  callbacks: {
    redirect({ url, baseUrl }) {
      // Allow cross-origin redirects for trusted domains
      // In production, all apps are on *.sensey.io or *.korur.com
      // In development, they're on localhost with different ports
      if (url.startsWith("http://localhost:")) {
        return url;
      }
      // Relative URLs
      if (url.startsWith("/")) {
        return `${baseUrl}${url}`;
      }
      let parsed: URL;
      try {
        parsed = new URL(url);
      } catch {
        // An unparseable callback URL is untrusted input, not an error:
        // the user lands on the app's base URL instead.
        return baseUrl;
      }
      // The app's own origin, whatever host it is served on
      if (parsed.origin === new URL(baseUrl).origin) {
        return url;
      }
      if (parsed.protocol === "https:") {
        const hostname = parsed.hostname;
        if (
          hostname.endsWith(".sensey.io") ||
          hostname === "sensey.io" ||
          hostname.endsWith(".korur.com") ||
          hostname === "korur.com"
        ) {
          return url;
        }
      }
      // Local development stack instances on <instance>.localhost hosts
      if (isDevelopmentLocalhostUrl(parsed)) {
        return url;
      }
      return baseUrl;
    },
    async jwt({ token, account, user, trigger, profile, session: updateData }) {
 
      if (trigger === "update") {
        // The API refused the access token although it has not expired (the
        // client calls update({ forceRefresh: true }) after a 401). Only a new
        // token can help, so nothing else in this branch runs: /users/me would
        // be one more request with a token the API may reject.
        if (updateData?.forceRefresh === true) {
          return await refreshAccessToken(token);
        }

        // Apply client-passed data first (from update() call)
        token.defaultOrganizationId = clientDefaultOrganizationId(
          updateData,
          token.defaultOrganizationId,
        );

        // Remove a resolved invitation
        if (updateData?.removedInvitationId) {
          token.pendingInvitationIds = (token.pendingInvitationIds ?? []).filter(
            (id) => id !== updateData.removedInvitationId,
          );
        }

        // Set remind-later for an invitation
        if (updateData?.remindLaterInvitationId && updateData?.remindAt) {
          token.remindLaterInvitations = {
            ...(token.remindLaterInvitations ?? {}),
            [updateData.remindLaterInvitationId]: updateData.remindAt,
          };
        }

        // Clear expired remind-later entries (housekeeping)
        if (token.remindLaterInvitations) {
          const now = Date.now();
          token.remindLaterInvitations = Object.fromEntries(
            Object.entries(token.remindLaterInvitations).filter(
              ([, remindAt]) => remindAt > now,
            ),
          );
        }

        // Refresh access token if expired before calling the API
        if (!isTokenValid(token)) {
          token = await refreshAccessToken(token);
          if (token.error === AuthError.REFRESH_TOKEN_ERROR) {
            return token;
          }
        }

        try {
          // Try to get fresh data from API
          const getMeResult = await usersApi.getMe(token.accessToken);

          if (getMeResult.success && getMeResult.data) {
            token.defaultOrganizationId = getMeResult.data.defaultOrganizationId ?? null;
            token.userStatus = getMeResult.data.userStatus;
            token.email = getMeResult.data.email;
            token.pendingInvitationIds =
              getMeResult.data.pendingInvitations
                ?.filter((inv) => inv.status === "PENDING")
                .map((inv) => inv.id) ?? [];
            token.error = undefined;
          } else {
            // GET failed — if it was 404, try POST to sync/create
            if (getMeResult.error?.message?.includes("User has not been synced yet")) {
              const syncResult = await usersApi.syncMe(token.accessToken);
              if (syncResult.success && syncResult.data) {
                token.defaultOrganizationId = syncResult.data.defaultOrganizationId ?? null;
                token.userStatus = syncResult.data.userStatus;
                token.email = syncResult.data.email;
                token.error = undefined;
              } else {
                token.error = AuthError.UNEXPECTED_ERROR;
              }
            } else {
              token.error = AuthError.UNEXPECTED_ERROR;
            }
          }
        } catch (error) {
          devLog("[JWT Callback - update trigger error]", error);
          token.error = AuthError.UNEXPECTED_ERROR;
        }

        // Client-passed error always takes priority over API-determined error
        if (updateData?.error) {
          token.error = updateData.error;
        }

        return token;
      }

      if (account) {
        // For credentials login, tokens come from the user object (returned by authorize())
        // For OAuth (Keycloak), tokens come from the account object
        const isCredentials = account.type === "credentials";
        const userObj = user as Record<string, unknown>;

        const accountAccessToken = isCredentials
          ? (userObj.access_token as string)
          : account.access_token;
        token.verified = profile?.email_verified ?? false;
        token.id_token = isCredentials
          ? (userObj.id_token as string)
          : account.id_token;
        token.accessToken = accountAccessToken;
        token.expiresAt = isCredentials
          ? Math.floor(Date.now() / 1000) + (userObj.expires_in as number)
          : account.expires_at;
        token.refreshToken = isCredentials
          ? (userObj.refresh_token as string)
          : account.refresh_token;

        // Seed email from the user object up front so a /users/me failure
        // does not leave the session without an email. A successful
        // /users/me response below can still overwrite it.
        token.email = (user as { email?: string }).email ?? token.email;

        try {
          // Step 1: Try GET /users/me (read-only)
          const getMeResult = await usersApi.getMe(accountAccessToken);

          devLog("[JWT Callback - getMeResult]", getMeResult);

          if (getMeResult.success && getMeResult.data) {
            // User exists — use response for routing
            token.defaultOrganizationId = getMeResult.data.defaultOrganizationId ?? null;
            token.userStatus = getMeResult.data.userStatus;
            token.email = getMeResult.data.email || token.email;
            token.pendingInvitationIds =
              getMeResult.data.pendingInvitations
                ?.filter((inv) => inv.status === "PENDING")
                .map((inv) => inv.id) ?? [];
            token.error = handleUserStatus(getMeResult.data.userStatus);
          } else if (getMeResult.error?.message?.includes("User has not been synced yet")) {
            // Step 2: User doesn't exist yet — create via POST /users/me
            const syncResult = await usersApi.syncMe(accountAccessToken);

            if (syncResult.success && syncResult.data) {
              token.defaultOrganizationId = syncResult.data.defaultOrganizationId ?? null;
              token.userStatus = syncResult.data.userStatus;
              token.email = syncResult.data.email || token.email;
              token.pendingInvitationIds =
                syncResult.data.pendingInvitations
                  ?.filter((inv) => inv.status === "PENDING")
                  .map((inv) => inv.id) ?? [];
              token.error = handleUserStatus(syncResult.data.userStatus);
            } else if (!syncResult.success) {
              token.error = AuthError.ONBOARDING_REQUIRED;
            } else {
              token.error = AuthError.UNEXPECTED_ERROR;
            }
          } else {
            token.error = AuthError.UNEXPECTED_ERROR;
          }
        } catch (error) {
          devLog("[JWT Callback - usersApi error]", error);
          token.error = AuthError.UNEXPECTED_ERROR;
        }

        return token;
      }

      if (isTokenValid(token)) {
        return token;
      }

      return await refreshAccessToken(token);
    },
    async session({ session, token, trigger }) {
      if (trigger === "update") {
        session.user = {
          ...session.user,
          verified: Boolean(token.verified ?? false) as boolean,
          defaultOrganizationId: token.defaultOrganizationId ?? null,
        };
      }
      session.accessToken = token.accessToken;
      session.expiresAt = token.expiresAt;
      session.error = token.error;
      session.userStatus = token.userStatus;
      session.pendingInvitationIds = token.pendingInvitationIds;
      session.remindLaterInvitations = token.remindLaterInvitations;

      if (!token.accessToken) {
        session.error = AuthError.REFRESH_TOKEN_ERROR;
      }

      if (token.id_token) {
        try {
          session.sub = decodeToken(token.id_token).sub;
        } catch (error) {
          console.error("[Auth] Failed to decode id_token, marking session as errored:", error);
          session.error = AuthError.REFRESH_TOKEN_ERROR;
        }
      }

      session.user = {
        ...session.user,
        email: token.email ?? session.user?.email ?? "",
        verified: Boolean(token.verified ?? false) as boolean,
        defaultOrganizationId: token.defaultOrganizationId ?? null,
      };

      return session;
    },
  },
  session: {
    strategy: "jwt",
  },
  pages: {
    signIn: LOGIN_PAGE_URL,
    signOut: LOGOUT_PAGE_URL,
    error: ERROR_PAGE_URL,
  },
  events: {
    // Serves the server-side signOut() calls (Log out itself goes through the
    // /api/auth/logout route). Ending the Keycloak session too keeps the next
    // sign-in from silently reusing it. Only id_token_hint is sent: Keycloak 18
    // and later reject redirect_uri on this endpoint.
    async signOut(message) {
      const token = "token" in message ? message.token : null;
      if (!keycloakIssuer || !token?.id_token) {
        return;
      }
      try {
        const url = new URL(`${keycloakIssuer}/protocol/openid-connect/logout`);
        url.searchParams.set("id_token_hint", token.id_token);
        const response = await fetch(url);
        if (!response.ok) {
          console.error(`[Auth] Keycloak logout answered ${response.status}`);
        }
      } catch (error) {
        // NextAuth still clears the session cookie, so the user is signed out
        // locally, but the Keycloak SSO session may live on. Logged so failed
        // back-channel logouts are visible in pod logs.
        console.error("[Auth] Keycloak logout request failed:", error);
      }
    },
  },
  trustHost: true,
});

export { isSafeCallbackUrl, sanitizeCallbackUrl } from "./callback-url";

export const handlers: NextAuthResult["handlers"] = result.handlers;
export const auth: NextAuthResult["auth"] = result.auth;
export const signIn: NextAuthResult["signIn"] = result.signIn;
export const signOut: NextAuthResult["signOut"] = result.signOut;
export const unstable_update: NextAuthResult["unstable_update"] = result.unstable_update;
