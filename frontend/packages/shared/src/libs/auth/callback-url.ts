/**
 * Callback URL validation. Kept free of the NextAuth setup so client
 * components can import it without pulling the server auth module in.
 */

// Never resolvable (RFC 2606 reserved TLD). Only used as a base to find out
// whether a relative-looking value stays on the origin it is resolved against.
const RELATIVE_BASE_ORIGIN = "http://callback.invalid";

/**
 * True for a plain-http URL on a `*.localhost` host while running in
 * development. Local development stacks serve each instance on its own
 * `<instance>.localhost` host; those names resolve to the loopback interface
 * only, but a production build must never trust them, so they are accepted
 * only when `NODE_ENV` is "development".
 */
export function isDevelopmentLocalhostUrl(parsed: URL): boolean {
  return (
    process.env.NODE_ENV === "development" &&
    parsed.protocol === "http:" &&
    parsed.hostname.endsWith(".localhost")
  );
}

/**
 * True for an absolute http(s) URL on localhost, on sensey.io or korur.com
 * and their subdomains, and, in development only, for an http URL on a
 * `*.localhost` host (see `isDevelopmentLocalhostUrl`).
 */
export function isSafeCallbackUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    // A non-web scheme can carry a trusted hostname too
    // (`javascript://localhost/...`), and a callback URL ends up in
    // `window.location.href`.
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return false;
    if (parsed.hostname === "localhost") return true;
    if (parsed.hostname.endsWith(".sensey.io") || parsed.hostname === "sensey.io") return true;
    if (parsed.hostname.endsWith(".korur.com") || parsed.hostname === "korur.com") return true;
    if (isDevelopmentLocalhostUrl(parsed)) return true;
    return false;
  } catch {
    return false;
  }
}

/**
 * Returns `raw` when it is safe to redirect to, otherwise undefined.
 *
 * Safe means either a path on the current origin or an absolute URL that
 * passes `isSafeCallbackUrl`. A leading "/" alone does not make a value a
 * same-origin path: `//evil.example` is protocol-relative, and browsers read
 * `\` as `/` and drop tabs and newlines, so `/\evil.example` and
 * `/<tab>/evil.example` leave the origin as well. The value is therefore
 * resolved the way a browser resolves it, and accepted only when the result
 * is still on the base origin.
 */
export function sanitizeCallbackUrl(raw: string | null | undefined): string | undefined {
  if (!raw) return undefined;

  if (raw.startsWith("/")) {
    try {
      return new URL(raw, RELATIVE_BASE_ORIGIN).origin === RELATIVE_BASE_ORIGIN ? raw : undefined;
    } catch {
      return undefined;
    }
  }

  return isSafeCallbackUrl(raw) ? raw : undefined;
}
