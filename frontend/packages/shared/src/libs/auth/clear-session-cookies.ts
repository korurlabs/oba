import { cookies } from "next/headers";
import { sessionCookieDomain, sessionCookieName } from "./session-cookie";

/**
 * THE single source of truth for purging the NextAuth session cookie.
 *
 * Why this exists (the ERR_TOO_MANY_REDIRECTS root cause):
 * The session cookie is CHUNKED — its value (access + refresh + id tokens) is
 * split across `${name}.0`, `${name}.1`, … when it exceeds the per-cookie size
 * limit. On read, @auth/core's SessionStore concatenates EVERY cookie whose
 * name starts with the base name, sorted by numeric suffix. So a single stale
 * chunk poisons the whole session: the joined value no longer decrypts, `auth()`
 * returns null, the proxy treats the user as unauthenticated and bounces them to
 * /api/auth/login, Keycloak's still-live SSO immediately returns a fresh code,
 * NextAuth writes a NEW (possibly shorter) chunk set on top of the orphan, the
 * orphan survives, the next read is corrupt again -> infinite loop. Prod only,
 * because the orphan arises from the shared ".sensey.io" cookie domain: one app
 * writes 3 chunks, a sibling writes 2, and a chunk written host-only by an older
 * deploy cannot be overwritten by a Domain-scoped write.
 *
 * NextAuth's own signOut() only deletes the chunks its SessionStore saw, with
 * the single configured Domain — it cannot evict a HOST-ONLY orphan left under a
 * bare subdomain. This helper closes that gap: it deletes a generous range of
 * chunk indices under BOTH the apex Domain (".sensey.io") AND host-only (no
 * Domain), guaranteeing every variant the browser might still hold is expired.
 *
 * Idempotent and safe to call when no cookie is present (expiring an absent
 * cookie is a no-op for the browser).
 */
export async function clearSessionCookies(): Promise<void> {
  const cookieStore = await cookies();
  const baseName = sessionCookieName(process.env.AUTH_URL);
  const domain = sessionCookieDomain(process.env.AUTH_URL);
  const secure = process.env.AUTH_URL?.startsWith("https://") ?? false;

  // The unchunked cookie plus a safe range of chunk suffixes. Session JWTs with
  // three Keycloak tokens realistically fit in <8 chunks; 12 is generous
  // headroom and costs nothing for absent indices.
  const names: string[] = [baseName];
  for (let i = 0; i < 12; i++) {
    names.push(`${baseName}.${i}`);
  }

  for (const name of names) {
    // Host-only deletion (no Domain) — evicts orphans written without a Domain
    // attribute by older deploys or by the host itself.
    cookieStore.set(name, "", {
      httpOnly: true,
      sameSite: "lax",
      path: "/",
      secure,
      maxAge: 0,
    });

    // Apex-domain deletion — evicts the shared-subdomain chunks NextAuth writes
    // in prod (domain ".sensey.io"). Only emitted when we actually run under an
    // apex domain; on localhost `domain` is undefined and this is skipped.
    if (domain) {
      cookieStore.set(name, "", {
        httpOnly: true,
        sameSite: "lax",
        path: "/",
        secure,
        domain,
        maxAge: 0,
      });
    }
  }
}
