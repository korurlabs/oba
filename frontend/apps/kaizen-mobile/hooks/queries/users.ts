import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  userMeSchema,
  type UserMe,
  type UserProfileUpdate,
} from "@oba/shared/features/users/schema";

import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { toProfileUpdate, type ProfileFormValues } from "@/lib/schemas/users";

/** Query keys of the signed-in user. Not organization-scoped: the profile spans every organization. */
export const userKeys = {
  all: ["users"] as const,
  me: ["users", "me"] as const,
};

/** `GET /users/me`: the profile with `defaultOrganizationId`, `organizations` and `pendingInvitations`. */
export async function fetchMe(): Promise<UserMe> {
  const response = await api.get(API_ROUTES.users.me);
  return readData(userMeSchema, response.data);
}

/** The signed-in user. `enabled` is false while no session exists. */
export function useMe(enabled: boolean) {
  return useQuery({ queryKey: userKeys.me, queryFn: fetchMe, enabled });
}

/**
 * `PUT /users/me` with only the preferences. The backend replaces every
 * preference with the ones sent, so the caller sends the whole record with
 * its own key changed. Silent: each caller (the chat channel order, pinned
 * boards) toasts its own failure, naming what was not saved.
 */
export async function saveUserPreferences(
  userPreferences: Record<string, unknown>
): Promise<UserMe> {
  const response = await api.put(
    API_ROUTES.users.me,
    { userPreferences },
    {
      _silentErrors: true,
    }
  );
  return readData(userMeSchema, response.data);
}

/**
 * `PUT /users/me` with the first and last name.
 *
 * The backend rebuilds the whole embedded profile from the request, so a
 * field missing from it would come back null. The form values are merged over
 * the loaded profile, which keeps the phone number, position, preferred
 * language and gender as they are.
 *
 * The PUT answers with `pendingInvitations: null` whatever invitations are
 * pending, so writing the answer into the cache would hide them; the profile
 * is read again instead. `onSuccess` returns that refetch, so the save stays pending until the
 * screen shows the saved names.
 *
 * A failure rejects: the app-wide toast reports a failed write, and the
 * screen shows a validation error under its field.
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, ProfileFormValues>({
    mutationFn: async (values: ProfileFormValues) => {
      const me: UserMe | undefined = queryClient.getQueryData<UserMe>(userKeys.me);
      if (!me) {
        // Without the current profile the merge would null the other fields.
        throw new Error("The current user is not loaded; the profile was not saved");
      }
      const profile: UserProfileUpdate = toProfileUpdate(me.profile, values);
      await api.put(API_ROUTES.users.me, { profile });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.me }),
    onError: (error: Error) => {
      console.warn("[account] saving the profile failed", error.message);
    },
  });
}

/**
 * `DELETE /users/me`: disables the account in every Korur app. The request
 * is sent with `_silentErrors`, because the caller explains the refusal
 * itself (the sole owner of an organization cannot leave it ownerless) and a
 * second, generic toast would contradict it. Signing out afterwards is the
 * caller's step, so it can tell the user which of the two failed.
 */
export function useDeleteAccount() {
  return useMutation<void, Error, void>({
    mutationFn: async () => {
      await api.delete(API_ROUTES.users.me, { _silentErrors: true });
    },
    onError: (error: Error) => {
      console.warn("[account] deleting the account failed", error.message);
    },
  });
}
