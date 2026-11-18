import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";
import type { UserMe, UserPreferences } from "@oba/shared/features/users/schema";

import type { BoardRow } from "@/components/boards/boardModel";
import {
  isBoardPinned,
  readSidebarPreferences,
  toggleBoardPin,
  withSidebarPreferences,
  type PinTarget,
  type SidebarPreferences,
} from "@/components/boards/boardPins";
import { toast } from "@/components/ui/toast";
import { saveUserPreferences, userKeys, useMe } from "@/hooks/queries/users";
import { translate } from "@/i18n";

type PinVariables = { preferences: UserPreferences };
type PinContext = { previous: UserMe | undefined };

/**
 * The signed-in user's pinned boards, shared with Kaizen web through the
 * `sidebar` record of `userPreferences` (`GET` and `PUT /users/me`), and the
 * toggle that pins or unpins a board.
 *
 * A toggle shows at once: the cached user gets the new preferences before
 * the save. The save sends every preference, built from that cache, so a
 * second toggle before the first answer builds on the first. A failed save
 * puts the cached preferences back and toasts, since the pin would otherwise
 * look saved until the next start. The user is read again afterwards, which
 * also picks up a pin changed on the web in the meantime.
 */
export function useBoardPins() {
  const queryClient = useQueryClient();
  const me = useMe(true);
  const sidebar: SidebarPreferences = useMemo(
    () => readSidebarPreferences(me.data?.userPreferences),
    [me.data?.userPreferences]
  );

  const save = useMutation<UserMe, Error, PinVariables, PinContext>({
    mutationFn: ({ preferences }) => saveUserPreferences(preferences),
    onMutate: async ({ preferences }) => {
      await queryClient.cancelQueries({ queryKey: userKeys.me });
      const previous: UserMe | undefined = queryClient.getQueryData<UserMe>(userKeys.me);
      if (previous) {
        queryClient.setQueryData<UserMe>(userKeys.me, {
          ...previous,
          userPreferences: preferences,
        });
      }
      return { previous };
    },
    onError: (error: Error, _variables: PinVariables, context: PinContext | undefined) => {
      console.warn("[boards] saving a board pin failed", error.message);
      if (context?.previous) queryClient.setQueryData(userKeys.me, context.previous);
      toast.error(translate("boardsPinSaveError"));
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: userKeys.me }),
  });
  const { mutate } = save;

  const isPinned = useCallback(
    (target: PinTarget): boolean => isBoardPinned(sidebar, target),
    [sidebar]
  );

  /** Pins an unpinned board, unpins a pinned one. Does nothing until the user is loaded. */
  const togglePin = useCallback(
    (target: Pick<BoardRow, "id" | "parentId">) => {
      const current: UserMe | undefined = queryClient.getQueryData<UserMe>(userKeys.me);
      if (!current) return;
      const next: SidebarPreferences = toggleBoardPin(
        readSidebarPreferences(current.userPreferences),
        target
      );
      mutate({ preferences: withSidebarPreferences(current.userPreferences, next) });
    },
    [queryClient, mutate]
  );

  return { sidebar, ready: me.isSuccess, isPinned, togglePin };
}
