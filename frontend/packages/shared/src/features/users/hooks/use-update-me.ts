import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getSession, useSession } from "next-auth/react";
import { usersApi, type UserMeResponse } from "../api";
import type { UserUpdate } from "../schema";

const KEYS = {
  all: ["users"] as const,
  me: ["users", "me"] as const,
};

/**
 * Saves changes to the signed-in user (`PUT /users/me`).
 *
 * `usersApi.updateMe` never rejects: an error status or a network failure
 * resolves with `success: false`, because the same fetch serves the JWT
 * callback, which branches on that value. Every caller of this mutation saves
 * something the user expects to be kept, so the mutation function turns that
 * value into a rejection: `mutateAsync` rejects and the caller's error path
 * runs instead of its "saved" path. `onSuccess`, and with it the refetch of
 * the current user, runs only after a save that went through.
 *
 * The token is read from the session at request time, as `useGetMe` does: the
 * `useSession` state is refetched only on a tab focus change, so after the
 * access token's lifetime it holds an expired token, and an organization
 * switch would be refused with 401. Reading the session refreshes an expired
 * token on the server first.
 *
 * There is no `onError` toast here: each caller shows its own message for
 * what it was saving.
 */
export function useUpdateMe() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: UserUpdate): Promise<UserMeResponse> => {
      const current = await getSession({ broadcast: false });
      const response = await usersApi.updateMe(
        current?.accessToken ?? session?.accessToken,
        data,
      );
      if (response.success !== true) {
        throw new Error(
          response.error?.message || response.message || "PUT /users/me was rejected",
          { cause: response.error },
        );
      }
      return response;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.me });
    },
  });
}
