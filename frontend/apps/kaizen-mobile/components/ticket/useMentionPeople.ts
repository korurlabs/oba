import { useEffect, useMemo } from "react";

import { personName, type MentionPerson } from "@/components/ticket/ticketModel";
import { useBoardMembers } from "@/hooks/queries/boards";
import { useTranslation } from "@/i18n";
import type { BoardMember } from "@/lib/schemas/boards";

/** Who a comment can mention, and every board member's name for reading mentions. */
export interface MentionPeople {
  /** Board members other than the signed-in user, by name. */
  candidates: MentionPerson[];
  /** Every board member's name by lower-case user id. */
  names: ReadonlyMap<string, string>;
}

/**
 * The people of the ticket's board, for the mention list and for naming the
 * mentions in comments. A failed member read leaves the list empty, so the
 * mention list stays closed and mentions read "@unknown member"; the comments
 * themselves still load, and the failure is logged.
 */
export function useMentionPeople(boardId: string | undefined, meId: string | undefined) {
  const { t } = useTranslation();
  const members = useBoardMembers(boardId);
  const memberError: Error | null = members.error;
  useEffect(() => {
    if (memberError) console.warn("[ticket] board members failed", memberError.message);
  }, [memberError]);
  return useMemo<MentionPeople>(() => {
    const list: BoardMember[] = members.data ?? [];
    const names: Map<string, string> = new Map();
    const candidates: MentionPerson[] = [];
    for (const member of list) {
      const userId: string = member.userId.toLowerCase();
      const name: string = personName(member, t("ticketUnknownPerson"));
      names.set(userId, name);
      if (userId !== meId?.toLowerCase()) candidates.push({ userId, name });
    }
    candidates.sort((a, b) => a.name.localeCompare(b.name));
    return { candidates, names };
  }, [members.data, meId, t]);
}
