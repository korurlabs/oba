/**
 * Cyberscore access on the Next server, for the cyberscore pages. Imported by
 * server components only.
 */

import { loadServerMe } from "@/components/layout/kazan-access-server";
import { isCyberscoreClosed } from "./cyberscore-access";

/**
 * True when the server knows the reader is a guest of the selected
 * organization, so a cyberscore page skips its prefetch: the API would refuse
 * it, and the access gate shows the no-access state instead. Shares the
 * request's cached `/users/me` call with the dashboard layout.
 */
export async function isCyberscoreClosedOnServer(): Promise<boolean> {
  const me = (await loadServerMe())?.data;
  return isCyberscoreClosed(me, me?.defaultOrganizationId);
}
