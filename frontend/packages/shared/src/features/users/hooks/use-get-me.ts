import { useQuery } from "@tanstack/react-query";
import { usersApi } from "../api";
import { getSession, useSession } from "next-auth/react";

/**
 * Re-exported so server components can call the EXACT same api function the
 * hook's queryFn uses when prefetching (the users api module has no package
 * export of its own). Keeps the cached value shape identical by construction.
 */
export { usersApi };

/**
 * Query-key factory for the current-user ("me") query. Exported so server
 * components can prefetch under the EXACT same key the client hook uses,
 * per the server-prefetch-convention. Never hand-mirror this key.
 */
export const USERS_KEYS = {
  me: ["users", "me"] as const,
};

/**
 * The current user.
 *
 * The token is read from the session at fetch time, as the API client does,
 * and not from the `useSession` state: that state is only refetched on a tab
 * focus change, so after the access token's lifetime it can hold an expired
 * token. Reading the session refreshes an expired token on the server first.
 *
 * `usersApi.getMe` never rejects (the JWT callback shares it and branches on
 * `success`), so the query function turns a failed response into a rejection.
 * A failed refetch then leaves the last good user in the cache and sets the
 * query's `error`, instead of replacing the user with the failure: every "is it
 * me" check (own chat messages, the organization switcher, the account menu)
 * keeps working until the next successful fetch. Views that cannot render
 * without the user show their error state from `isError` or a missing `data`.
 */
export function useGetMe() {
  const { data: session } = useSession();
  return useQuery({
    queryKey: USERS_KEYS.me,
    queryFn: async () => {
      const current = await getSession({ broadcast: false });
      const response = await usersApi.getMe(current?.accessToken ?? session?.accessToken);
      if (response.success !== true || !response.data) {
        const error = new Error(
          response.error?.message || response.message || "GET /users/me was rejected",
          { cause: response.error },
        );
        console.error("[useGetMe] current user fetch failed:", error);
        throw error;
      }
      return response;
    },
    enabled: !!session?.accessToken,
  });
}
