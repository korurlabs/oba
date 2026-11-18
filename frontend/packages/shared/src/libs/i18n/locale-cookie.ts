import { localeCookieConfig } from "./routing";

/**
 * The `document.cookie` value that stores a language choice in the browser.
 *
 * It has the name, lifetime and domain of the cookie the proxy writes, so the
 * two writers always replace one cookie and never leave a second one of the
 * same name on another domain. `SameSite=Lax` is the attribute the proxy adds.
 */
export function localeCookieString(locale: string, baseUrl?: string): string {
  const { name, maxAge, domain } = localeCookieConfig(baseUrl);
  const attributes = [`${name}=${locale}`, "path=/", `max-age=${maxAge}`, "samesite=lax"];
  if (domain) {
    attributes.push(`domain=${domain}`);
  }
  return attributes.join("; ");
}
