import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getSession, useSession } from "next-auth/react";
import { usersApi, type UserMeResponse } from "../api";

const KEYS = {
  all: ["users"] as const,
  me: ["users", "me"] as const,
};

/**
 * Clears the signed-in user's default organization
 * (`DELETE /users/me/default-organization`), which selects the personal
 * workspace.
 *
 * Like `useUpdateMe`, the mutation function turns the `success: false` that
 * `usersApi` resolves with into a rejection, so the caller's error path runs
 * instead of its "switched" path, and reads the token from the session at
 * request time so that an expired token is refreshed first.
 *
 * There is no `onError` toast here: the caller shows its own message.
 */
export function useClearDefaultOrganization() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (): Promise<UserMeResponse> => {
      const current = await getSession({ broadcast: false });
      const response = await usersApi.clearDefaultOrganization(
        current?.accessToken ?? session?.accessToken,
      );
      if (response.success !== true) {
        throw new Error(
          response.error?.message ||
            response.message ||
            "DELETE /users/me/default-organization was rejected",
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
