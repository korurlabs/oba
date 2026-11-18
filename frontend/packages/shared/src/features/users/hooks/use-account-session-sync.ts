"use client";

import { useTheme } from "next-themes";
import { useEffect } from "react";
import {
  USER_THEME_VALUES,
  type UserThemePreferenceValue,
} from "../constants/preference-keys";
import { useGetMe } from "./use-get-me";
import { useInvitationSessionSync } from "./use-invitation-session-sync";

function normalizeUserThemePreference(value: unknown): UserThemePreferenceValue | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!USER_THEME_VALUES.includes(trimmed as UserThemePreferenceValue)) return null;
  return trimmed as UserThemePreferenceValue;
}

/**
 * Keeps the signed-in user's session and theme in step with `GET /users/me`.
 *
 * Two side effects, which must not depend on the user menu being mounted (in
 * a sidebar that is an off-canvas sheet on narrow viewports, the menu is
 * unmounted while the sheet is closed):
 * - applies the theme stored in the user's preferences,
 * - keeps the session's pending invitation ids in sync as invitations arrive
 *   or are resolved (`useInvitationSessionSync`, the only writer of those ids
 *   from the client, so one mismatch causes one session request).
 *
 * Call it exactly once per page, from whatever hosts the user menu, inside the
 * session, query-client and theme providers.
 */
export function useAccountSessionSync() {
  const { data: user } = useGetMe();

  const userPreferences = user?.data?.userPreferences;
  const userTheme = userPreferences?.theme;
  const { setTheme } = useTheme();

  useEffect(() => {
    const pref = normalizeUserThemePreference(userTheme);
    if (pref === null) return;
    setTheme(pref);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- next-themes exposes setTheme with identity tied to current theme; including it re-runs this effect on every theme change and can cause maximum update depth (e.g. with Toaster/useTheme).
  }, [userTheme]);

  // Sync invitation state between API and session token
  useInvitationSessionSync(user?.data);
}
