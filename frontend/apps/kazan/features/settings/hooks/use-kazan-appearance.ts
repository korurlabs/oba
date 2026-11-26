"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import type { ApiResponse } from "@oba/shared/schema";
import type { UserMe } from "@oba/shared/features/users/schema";
import { USERS_KEYS, useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import {
  KAZAN_APPEARANCE_KEY,
  normalizeKazanAppearance,
  storedKazanAppearance,
  type KazanAppearance,
} from "@/lib/kazan-appearance";

/** The signed-in user's layout and background, the defaults until the user is loaded. */
export function useKazanAppearance(): KazanAppearance {
  const { data: me } = useGetMe();
  return normalizeKazanAppearance(me?.data?.userPreferences);
}

/**
 * Writes the preferences of the cached user at once, so the shell follows a
 * saved choice without waiting for the refetch that `useUpdateMe` starts.
 */
function setCachedPreferences(
  queryClient: ReturnType<typeof useQueryClient>,
  userPreferences: Record<string, unknown>
) {
  queryClient.setQueryData<ApiResponse<UserMe>>(USERS_KEYS.me, (current) =>
    current?.data ? { ...current, data: { ...current.data, userPreferences } } : current
  );
}

/**
 * Saves the user's preferences built from the cached copy (`PUT /users/me`
 * replaces the whole map, so the other keys are carried over). The mutation
 * rejects for a refused save, and `onError` logs it and shows the toast.
 */
function useSavePreferences<TVariables>(
  logTag: string,
  build: (current: Record<string, unknown>, variables: TVariables) => Record<string, unknown>
) {
  const t = useTranslations("appearance");
  const queryClient = useQueryClient();
  const { mutateAsync: updateMe } = useUpdateMe();

  return useMutation({
    mutationFn: async (variables: TVariables) => {
      const me = queryClient.getQueryData<ApiResponse<UserMe>>(USERS_KEYS.me);
      const userPreferences = build(me?.data?.userPreferences ?? {}, variables);
      const response = await updateMe({ userPreferences });
      if (!response.success) {
        throw new Error(response.message || "PUT /users/me was rejected");
      }
      return userPreferences;
    },
    onSuccess: (userPreferences) => setCachedPreferences(queryClient, userPreferences),
    onError: (error: Error) => {
      console.error(`[${logTag}] save failed:`, error);
      toast.error(t("saveError"));
    },
  });
}

/**
 * Saves a change of the layout or the background. Only the changed value is
 * merged into the stored record, so a background the user never picked keeps
 * following the layout's default.
 */
export function useUpdateKazanAppearance() {
  return useSavePreferences<Partial<KazanAppearance>>(
    "use-update-kazan-appearance",
    (current, patch) => ({
      ...current,
      [KAZAN_APPEARANCE_KEY]: { ...storedKazanAppearance(current), ...patch },
    })
  );
}

/**
 * Saves the theme in the Oba-wide `theme` preference, which every app
 * applies. There is no success toast: the caller applies the theme with
 * `setTheme` in its own `onSuccess`, and the changed theme is the
 * confirmation.
 */
export function useUpdateTheme() {
  return useSavePreferences<string>("use-update-theme", (current, theme) => ({
    ...current,
    theme,
  }));
}
