"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import type { ApiResponse } from "@oba/shared/schema";
import type { UserMe } from "@oba/shared/features/users/schema";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";

/**
 * Saves the theme in the signed-in user's preferences (`PUT /users/me`),
 * merged over the cached preferences so the other preferences are kept.
 *
 * `usersApi.updateMe` never throws: it answers `{ success: false }` for a
 * rejected or failed request. The mutation function turns that into an error
 * so that `onError` runs and the failure is shown.
 *
 * There is no success toast: the caller applies the theme in its own
 * `onSuccess`, and the changed theme is the confirmation. Cache invalidation
 * of `USERS_KEYS.me` is done by `useUpdateMe`.
 */
export function useUpdateTheme() {
  const t = useTranslations("settings");
  const queryClient = useQueryClient();
  const { mutateAsync: updateMe } = useUpdateMe();

  return useMutation({
    mutationFn: async (theme: string) => {
      const me = queryClient.getQueryData<ApiResponse<UserMe>>(USERS_KEYS.me);

      const response = await updateMe({
        userPreferences: {
          ...(me?.data?.userPreferences ?? {}),
          theme,
        },
      });
      if (!response.success) {
        throw new Error(response.message || "PUT /users/me was rejected");
      }
      return response;
    },
    onError: (error: Error) => {
      console.error("[use-update-theme] theme save failed:", error);
      toast.error(t("themeUpdateError"));
    },
  });
}
