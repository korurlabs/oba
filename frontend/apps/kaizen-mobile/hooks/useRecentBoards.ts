import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as SecureStore from "expo-secure-store";
import { useCallback } from "react";
import { z } from "zod";

import { pushRecent } from "@/components/boards/boardModel";
import { useActiveOrganizationId } from "@/lib/organizationStore";

/*
 * The boards this phone opened last, per organization, for the Recent section
 * of the Boards list. The server keeps no such list, so it lives on the device
 * and is lost on reinstall, which costs only the shortcut.
 */

const recentIdsSchema = z.array(z.string());

function storageKey(orgId: string): string {
  return `kaizen_recent_boards_${orgId}`;
}

const recentBoardKeys = {
  list: (orgId: string) => ["recent-boards", orgId] as const,
};

async function readRecent(orgId: string): Promise<string[]> {
  const raw: string | null = await SecureStore.getItemAsync(storageKey(orgId));
  if (!raw) return [];
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch (error) {
    // A damaged shortcut list starts over empty; the next opened board rewrites it.
    console.warn(
      "[boards] recent boards unreadable, starting empty",
      error instanceof Error ? error.message : error
    );
    return [];
  }
  const parsed = recentIdsSchema.safeParse(value);
  if (!parsed.success) {
    console.warn("[boards] recent boards unreadable, starting empty", parsed.error.message);
    return [];
  }
  return parsed.data;
}

/** The recent board ids, newest first, and `remember` to put a board on top. */
export function useRecentBoards() {
  const orgId: string | null = useActiveOrganizationId();
  const queryClient = useQueryClient();
  const query = useQuery<string[]>({
    queryKey: recentBoardKeys.list(orgId ?? ""),
    queryFn: () => readRecent(orgId as string),
    enabled: !!orgId,
    staleTime: Infinity,
  });

  const remember = useCallback(
    (boardId: string) => {
      if (!orgId) return;
      const key = recentBoardKeys.list(orgId);
      const next: string[] = pushRecent(queryClient.getQueryData<string[]>(key) ?? [], boardId);
      queryClient.setQueryData<string[]>(key, next);
      SecureStore.setItemAsync(storageKey(orgId), JSON.stringify(next)).catch((error: unknown) => {
        // The list in memory is already updated; only the copy for the next launch is missing.
        console.warn(
          "[boards] recent boards not saved",
          error instanceof Error ? error.message : error
        );
      });
    },
    [orgId, queryClient]
  );

  return { recent: query.data ?? [], remember };
}
