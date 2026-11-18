import { chatEntrySummary, isChatEntryCard } from "@/components/chat/chatDisplay";
import {
  ChatChannelPurpose,
  type ChatChannelResponse,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";

/*
 * What the reader may do with a message, and the link that opens it on the
 * web. Pure, so the rules can be tested without React; they are the web
 * app's rules, and the server enforces the same ones.
 */

/** Every action of the long-press sheet, in the order it lists them. */
export type ChatMessageActionId =
  | "reply"
  | "react"
  | "thread"
  | "pin"
  | "copyText"
  | "copyLink"
  | "edit"
  | "delete";

const ACTION_ORDER: readonly ChatMessageActionId[] = [
  "reply",
  "react",
  "thread",
  "pin",
  "copyText",
  "copyLink",
  "edit",
  "delete",
];

/** The quick row of the reaction picker; "More emoji" opens the full picker. */
export const CHAT_QUICK_REACTIONS: readonly string[] = ["👍", "❤️", "😂", "🎉", "👀", "✅"];

export interface ChatActionRights {
  meId: string | undefined;
  /** The organization role: `OWNER`, `ADMIN`, `MEMBER`, `GUEST`, or null while loading. */
  role: string | null;
  /** The message's conversation is a closed thread: read-only, so no reply and no edit. */
  closed?: boolean;
}

/** Not a guest: may send, react and pin. Unknown while the profile loads, so nothing is offered. */
export function chatCanWrite(role: string | null): boolean {
  return role !== null && role !== "GUEST";
}

/** OWNER and ADMIN may pin, unpin and delete anyone's message. */
export function chatCanModerate(role: string | null): boolean {
  return role === "OWNER" || role === "ADMIN";
}

/**
 * The actions the reader may take on a message, in sheet order: writing
 * needs a role above guest; a thread opens from a channel message that has
 * one and starts on any other channel message, never on a reply in a
 * thread; pins are for the author and the organization's owners and admins;
 * edit only on the reader's own message, delete on it and, for an owner or
 * admin, on anyone's; neither on a daily update or a shared pull request,
 * which change through their own screens. A closed thread takes no reply
 * and no edit; reactions, pins and deleting stay. A deleted message offers
 * nothing.
 */
export function chatMessageActions(
  message: ChatMessageResponse,
  rights: ChatActionRights
): ChatMessageActionId[] {
  if (message.deleted) return [];
  const canWrite: boolean = chatCanWrite(rights.role);
  const canModerate: boolean = chatCanModerate(rights.role);
  const own: boolean = !!rights.meId && message.author?.userId === rights.meId;
  const summary: string | null = chatEntrySummary(message);
  const hasText: boolean =
    summary !== null ? summary.trim().length > 0 : message.content.trim().length > 0;
  const editable: boolean = !isChatEntryCard(message);
  const closed: boolean = !!rights.closed;
  const allowed: Record<ChatMessageActionId, boolean> = {
    reply: canWrite && !closed,
    react: canWrite,
    thread: !message.threadId && (!!message.thread || canWrite),
    pin: canWrite && (own || canModerate),
    copyText: hasText,
    copyLink: true,
    edit: canWrite && !closed && own && editable,
    delete: canWrite && (own || canModerate) && editable,
  };
  return ACTION_ORDER.filter((id) => allowed[id]);
}

/** The web app's path of a channel: fixed for daily updates and pull requests, else its slug. */
export function chatChannelWebPath(channel: Pick<ChatChannelResponse, "slug" | "purpose">): string {
  switch (channel.purpose) {
    case ChatChannelPurpose.DAILY_UPDATES:
      return "/daily-updates";
    case ChatChannelPurpose.PULL_REQUESTS:
      return "/pull-requests";
    default:
      return `/chats/${encodeURIComponent(channel.slug)}`;
  }
}

/**
 * The web link that opens the chat at a message, the same link the web app
 * copies: the channel's page with `jump`, or with `thread` and
 * `threadMessage` for a message of a thread. A web link opens for anyone,
 * on any device, which an app-only link would not.
 */
export function chatMessageWebLink(
  webUrl: string,
  message: Pick<ChatMessageResponse, "id" | "threadId">,
  channel: Pick<ChatChannelResponse, "slug" | "purpose">
): string {
  const base = `${webUrl.replace(/\/+$/, "")}${chatChannelWebPath(channel)}`;
  const query: string = message.threadId
    ? `thread=${encodeURIComponent(message.threadId)}&threadMessage=${encodeURIComponent(message.id)}`
    : `jump=${encodeURIComponent(message.id)}`;
  return `${base}?${query}`;
}
