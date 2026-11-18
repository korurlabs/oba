import { chatPersonName } from "@/components/chat/chatDisplay";
import { hasKaizenAccess } from "@/lib/kaizenAccess";
import type { ChatMemberResponse, ChatUserResponse } from "@/lib/schemas/chat";

/*
 * Who is in a channel or a thread, split by presence, without React, so the
 * rules can be tested alone.
 */

/** One row of the members sheet. */
export interface ChatMemberRow {
  userId: string;
  name: string;
  online: boolean;
}

/** The members sheet's two sections, each sorted by name. */
export interface ChatMemberSections {
  online: ChatMemberRow[];
  offline: ChatMemberRow[];
}

function byName(left: ChatMemberRow, right: ChatMemberRow): number {
  return left.name.localeCompare(right.name, undefined, { sensitivity: "base" });
}

/** Splits rows into online and offline, each sorted by name. */
export function chatMemberSections(rows: readonly ChatMemberRow[]): ChatMemberSections {
  const sorted: ChatMemberRow[] = [...rows].sort(byName);
  return {
    online: sorted.filter((row) => row.online),
    offline: sorted.filter((row) => !row.online),
  };
}

/** The members of a channel as rows; a member without a name or email shows `unknownLabel`. */
export function chatChannelMemberRows(
  members: readonly ChatMemberResponse[],
  unknownLabel: string
): ChatMemberRow[] {
  return members.map((member) => ({
    userId: member.userId,
    name: chatPersonName(member) || unknownLabel,
    online: member.online,
  }));
}

/**
 * The members of a thread as rows. A thread lists people, not presence, so
 * presence comes from the organization's members; someone who has left the
 * organization is left out, as the server no longer counts them.
 */
export function chatThreadMemberRows(
  users: readonly ChatUserResponse[],
  organizationMembers: readonly ChatMemberResponse[] | undefined,
  unknownLabel: string
): ChatMemberRow[] {
  const onlineById = new Map<string, boolean>(
    (organizationMembers ?? []).map((member) => [member.userId, member.online])
  );
  return users
    .filter((user) => user.member)
    .map((user) => ({
      userId: user.userId,
      name: chatPersonName(user) || unknownLabel,
      online: onlineById.get(user.userId) ?? false,
    }));
}

/**
 * How many of a channel's members are online, for the channel's subtitle;
 * null until the members have loaded, so the subtitle falls back to the
 * member count instead of claiming nobody is there.
 */
export function chatOnlineCount(members: readonly ChatMemberResponse[] | undefined): number | null {
  if (!members) return null;
  return members.filter((member) => member.online).length;
}

/**
 * The organization's members who are not on a private channel's list, in the
 * organization's order: the people the members sheet offers to add. While the
 * list is unknown (still loading, or hidden from an admin who is not on it)
 * everyone is offered, the reader included, as the web does. Members without
 * Kaizen stay in the list with their flag, so the sheet shows why they
 * cannot be added.
 */
export function chatChannelAddCandidates(
  organizationMembers: readonly ChatMemberResponse[] | undefined,
  channelMembers: readonly ChatMemberResponse[] | undefined
): ChatMemberResponse[] {
  const onList = new Set<string>((channelMembers ?? []).map((member) => member.userId));
  return (organizationMembers ?? []).filter((member) => !onList.has(member.userId));
}

/**
 * True when a member picker shows this member as a row that cannot be
 * picked: the server refuses to attach a member without Kaizen to a channel,
 * a mention or a pull request. A member the field already holds stays
 * pickable, so they can be removed.
 */
export function chatMemberPickBlocked(
  member: Pick<ChatMemberResponse, "kaizenAccess">,
  selected: boolean
): boolean {
  return !hasKaizenAccess(member) && !selected;
}

/**
 * The subtitle of a member picker row: the email, and on a row that cannot be
 * picked the reason on its own line, so VoiceOver and TalkBack read why the
 * row is disabled.
 */
export function chatMemberPickSubtitle(
  email: string | null | undefined,
  blocked: boolean,
  noAccessLabel: string
): string | undefined {
  const lines: string[] = [];
  if (email) lines.push(email);
  if (blocked) lines.push(noAccessLabel);
  return lines.length > 0 ? lines.join("\n") : undefined;
}
