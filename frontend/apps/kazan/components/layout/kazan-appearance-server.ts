/**
 * Kazan's appearance on the Next server, so the layout and the background
 * are part of the first paint rather than a switch after hydration. Imported
 * by server components only.
 */

import { cache } from "react";
import { normalizeKazanAppearance, type KazanAppearance } from "@/lib/kazan-appearance";
import { loadServerMe } from "./kazan-access-server";

/**
 * The signed-in user's layout and background, or the defaults when there is
 * no user (a public page) or it could not be loaded. Shares the request's one
 * `/users/me` call with the dashboard layout.
 */
export const loadServerAppearance = cache(async (): Promise<KazanAppearance> => {
  const me = await loadServerMe();
  return normalizeKazanAppearance(me?.data?.userPreferences);
});
