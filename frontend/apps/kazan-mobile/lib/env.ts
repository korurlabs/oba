import Constants from "expo-constants";

/**
 * Typed access to the values `app.config.ts` puts in `extra`, filled per EAS
 * build profile (eas.json) or, for `expo start`, from `.env.local` through the
 * `EXPO_PUBLIC_*` variables. A device has no Next.js `NEXT_PUBLIC_*`
 * variables; `expo-constants` is the runtime source.
 *
 * A missing or malformed value throws when this module loads, so a build with
 * a wrong profile fails at start instead of sending requests to nowhere.
 */
type Extra = {
  apiBaseUrl?: string;
  keycloakIssuer?: string;
  keycloakClientId?: string;
  accountUrl?: string;
  ulakUrl?: string;
  kazanWebUrl?: string;
};

const extra: Extra = (Constants.expoConfig?.extra ?? {}) as Extra;

// A release build has `extra` in its embedded config. The Expo dev client does
// not receive the `extra` of a dynamic config, so the values fall back to the
// EXPO_PUBLIC_* variables that Metro inlines into the bundle. Metro inlines
// only full static member expressions, so each one is written out.
const inlined: Extra = {
  apiBaseUrl: process.env.EXPO_PUBLIC_OBA_API_URL,
  keycloakIssuer: process.env.EXPO_PUBLIC_KEYCLOAK_ISSUER,
  keycloakClientId: process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID,
  accountUrl: process.env.EXPO_PUBLIC_ACCOUNT_URL,
  ulakUrl: process.env.EXPO_PUBLIC_ULAK_URL,
  kazanWebUrl: process.env.EXPO_PUBLIC_KAZAN_WEB_URL,
};

function required(name: keyof Extra): string {
  const value: string | undefined = extra[name] || inlined[name];
  if (!value) {
    throw new Error(
      `[env] Missing config value "${name}". Set its EXPO_PUBLIC_* variable in the ` +
        "EAS build profile (eas.json) or in .env.local (see .env.example)."
    );
  }
  return value;
}

/** An absolute http(s) URL without a trailing slash, so paths can be appended. */
function requiredUrl(name: keyof Extra): string {
  const value: string = required(name);
  if (!/^https?:\/\/[^/?#]+/.test(value)) {
    throw new Error(`[env] Config value "${name}" is not an absolute http(s) URL: ${value}`);
  }
  return value.replace(/\/+$/, "");
}

export const ENV = {
  /** Oba API origin, for example https://api.korur.com. The STOMP broker is `${apiBaseUrl}/ws`. */
  apiBaseUrl: requiredUrl("apiBaseUrl"),
  /** Keycloak issuer of realm `sensey`, the issuer the Oba API accepts. */
  keycloakIssuer: requiredUrl("keycloakIssuer"),
  /** Public PKCE client of realm `sensey` for this app. */
  keycloakClientId: required("keycloakClientId"),
  /** Account web app: onboarding, invitations and new organizations open there. */
  accountUrl: requiredUrl("accountUrl"),
  /** Ulak web app: the organizations a user owns or administers are managed there. */
  ulakUrl: requiredUrl("ulakUrl"),
  /** Kazan web app: board settings pages the app does not draw open there. */
  kazanWebUrl: requiredUrl("kazanWebUrl"),
} as const;
