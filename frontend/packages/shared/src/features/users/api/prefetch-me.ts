import type { QueryClient } from "@tanstack/react-query";
import { USERS_KEYS } from "../hooks/use-get-me";
import { usersApi } from "./index";

/**
 * Server-side prefetch of the current-user ("me") query, under the key and
 * with the api function `useGetMe` uses.
 *
 * The cache is written only for a successful response. `usersApi.getMe` never
 * rejects: a failed request resolves with `success: false`, and seeding that
 * would hydrate as fresh data, so the client would show the failure for the
 * whole stale time instead of fetching again. On failure the cache is left
 * empty and `useGetMe` fetches on mount.
 *
 * Mirrors the hook's `enabled: !!accessToken` guard: without a token nothing
 * is fetched.
 *
 * @param logPrefix names the calling page in the server log, e.g. "profile-page".
 */
export async function prefetchMe(
  queryClient: QueryClient,
  accessToken: string | undefined,
  logPrefix: string
): Promise<void> {
  if (!accessToken) return;

  const data = await usersApi.getMe(accessToken);
  if (data.success && data.data) {
    queryClient.setQueryData(USERS_KEYS.me, data);
    return;
  }

  console.error(
    `[${logPrefix}] me prefetch failed — client will refetch:`,
    data.error ?? data.message
  );
}
