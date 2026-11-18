"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  UserMe,
  UserProfileUpdate,
} from "@oba/shared/features/users/schema";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import type { ProfileFormValues } from "../schemas";

/** An input left empty is stored as null, not as an empty string. */
function toNullable(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

/**
 * Saves the name and phone number of the signed-in user (`PUT /users/me`).
 *
 * The backend rebuilds the whole embedded profile from the request, so a
 * field that is missing from it comes back null. The form values are
 * therefore merged over the cached profile, which keeps `userPosition`,
 * `userPreferredLanguage` and `userGender` as they are.
 *
 * `usersApi.updateMe` never throws: it answers `{ success: false }` for a
 * rejected or failed request. The mutation function turns that into an error
 * so that `onError` runs and the failure is shown.
 *
 * `onSuccess` returns the invalidation of `USERS_KEYS.me`, so the mutation
 * stays pending until the saved profile is refetched and the form is reset.
 * (`useUpdateMe` starts the same invalidation but does not wait for it.)
 */
export function useUpdateProfile() {
  const t = useTranslations("profile");
  const queryClient = useQueryClient();
  const { mutateAsync: updateMe } = useUpdateMe();

  return useMutation({
    mutationFn: async (values: ProfileFormValues) => {
      const me = queryClient.getQueryData<ApiResponse<UserMe>>(USERS_KEYS.me);
      if (!me?.data) {
        // Without the current profile the merge would null the other fields.
        throw new Error("Current user is not loaded; profile was not saved");
      }

      const profile: UserProfileUpdate = {
        userPosition: me.data.profile?.userPosition ?? null,
        userPreferredLanguage: me.data.profile?.userPreferredLanguage ?? null,
        userGender: me.data.profile?.userGender ?? null,
        firstName: toNullable(values.firstName),
        lastName: toNullable(values.lastName),
        phoneNumber: toNullable(values.phoneNumber),
      };

      const response = await updateMe({ profile });
      if (!response.success) {
        throw new Error(response.message || "PUT /users/me was rejected");
      }
      return response;
    },
    onSuccess: () => {
      toast.success(t("updateSuccess"));
      return queryClient.invalidateQueries({ queryKey: USERS_KEYS.me });
    },
    onError: (error: Error) => {
      console.error("[use-update-profile] profile update failed:", error);
      toast.error(t("updateError"));
    },
  });
}
