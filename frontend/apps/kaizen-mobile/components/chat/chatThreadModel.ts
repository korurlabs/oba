import {
  chatEntrySummary,
  chatMessageSnippet,
  chatUserDisplayName,
} from "@/components/chat/chatDisplay";
import { chatThreadUnread } from "@/components/chat/chatScreenModel";
import {
  CHAT_THREAD_DEFAULT_ARCHIVE_MINUTES,
  CHAT_THREAD_TITLE_MAX,
  ChatThreadArchiveMinutesSchema,
  ChatThreadStatus,
  type ChatConversationUnreadResponse,
  type ChatMessageResponse,
  type ChatThreadResponse,
  type ChatThreadArchiveMinutes,
  type ChatThreadSummaryResponse,
  type ChatThreadUpdateRequest,
  type ChatUnread,
  type ChatUserResponse,
} from "@/lib/schemas/chat";

/*
 * The decisions of the thread screens that need no React: the title a new
 * thread starts with, which settings, status changes and membership the
 * thread menu offers, what the thread settings send, and what the Threads
 * list marks as unread. They follow the web
 * app and the server's rules.
 */

/** A title taken from the root's text or file name is cut here, as the server does. */
export const CHAT_THREAD_DEFAULT_TITLE_LENGTH = 40;

/**
 * The title a new thread on `message` starts with: the first line of a
 * shared pull request's summary, else the text with names for mentions and
 * tickets (a file-only message gives its file names), cut at 40 characters.
 * A daily update starts empty: the server titles it with its author and
 * report day, which this device cannot compute in the team's zone.
 */
export function chatThreadDefaultTitle(
  message: ChatMessageResponse,
  unknownMember: string
): string {
  if (message.dailyUpdateId != null) return "";
  if (message.pullRequestShareId != null) {
    const firstLine: string = (chatEntrySummary(message) ?? "").split("\n", 1)[0]?.trim() ?? "";
    return firstLine.length > CHAT_THREAD_DEFAULT_TITLE_LENGTH
      ? `${firstLine.slice(0, CHAT_THREAD_DEFAULT_TITLE_LENGTH).trimEnd()}…`
      : firstLine;
  }
  return chatMessageSnippet(message, unknownMember, CHAT_THREAD_DEFAULT_TITLE_LENGTH);
}

export type ChatThreadMenuActionId =
  | "join"
  | "leave"
  | "edit"
  | "close"
  | "reopen"
  | "archive"
  | "unarchive";

/** The status each status action sets. */
export const CHAT_THREAD_ACTION_STATUS: Record<
  Exclude<ChatThreadMenuActionId, "join" | "leave" | "edit">,
  ChatThreadStatus
> = {
  close: ChatThreadStatus.CLOSED,
  reopen: ChatThreadStatus.ACTIVE,
  archive: ChatThreadStatus.ARCHIVED,
  unarchive: ChatThreadStatus.ACTIVE,
};

/**
 * The thread menu: join or leave for a reader who may write, then, for its
 * creator and the organization's owners and admins (`canManage`), its
 * settings in every status (the server allows a rename of a closed or
 * archived thread too) and the status changes. An active thread can be
 * closed or archived, a closed one only reopened (the server refuses to
 * archive it), an archived one unarchived or closed.
 */
export function chatThreadMenuActions(
  thread: Pick<ChatThreadResponse, "status" | "member" | "canManage">,
  canWrite: boolean
): ChatThreadMenuActionId[] {
  const actions: ChatThreadMenuActionId[] = [];
  if (canWrite) actions.push(thread.member ? "leave" : "join");
  if (!thread.canManage) return actions;
  actions.push("edit");
  switch (thread.status) {
    case ChatThreadStatus.ACTIVE:
      actions.push("close", "archive");
      break;
    case ChatThreadStatus.CLOSED:
      actions.push("reopen");
      break;
    case ChatThreadStatus.ARCHIVED:
      actions.push("unarchive", "close");
      break;
  }
  return actions;
}

/** What the thread settings hold while they are edited: the name as typed and the archive window. */
export interface ChatThreadSettingsDraft {
  title: string;
  autoArchiveMinutes: ChatThreadArchiveMinutes;
}

/**
 * The settings a thread opens with: its title and its archive window. A
 * window the settings do not offer (set before the options were fixed)
 * shows the default, as the web does, and is replaced on save.
 */
export function chatThreadSettingsDraft(
  thread: Pick<ChatThreadResponse, "title" | "autoArchiveMinutes">
): ChatThreadSettingsDraft {
  const parsed = ChatThreadArchiveMinutesSchema.safeParse(thread.autoArchiveMinutes);
  return {
    title: thread.title,
    autoArchiveMinutes: parsed.success ? parsed.data : CHAT_THREAD_DEFAULT_ARCHIVE_MINUTES,
  };
}

/** Why the name cannot be saved, or null: it is blank, or longer than the server allows once trimmed. */
export function chatThreadTitleProblem(title: string): "required" | "tooLong" | null {
  const trimmed: string = title.trim();
  if (trimmed.length === 0) return "required";
  if (trimmed.length > CHAT_THREAD_TITLE_MAX) return "tooLong";
  return null;
}

/** The update the settings send: the trimmed name and the archive window, always both. */
export function chatThreadUpdateRequest(draft: ChatThreadSettingsDraft): ChatThreadUpdateRequest {
  return { title: draft.title.trim(), autoArchiveMinutes: draft.autoArchiveMinutes };
}

/**
 * Whether saving would send something other than what the settings opened
 * with, so spaces typed around the name alone do not count as a change.
 */
export function chatThreadSettingsChanged(
  initial: ChatThreadSettingsDraft,
  draft: ChatThreadSettingsDraft
): boolean {
  const before: ChatThreadUpdateRequest = chatThreadUpdateRequest(initial);
  const after: ChatThreadUpdateRequest = chatThreadUpdateRequest(draft);
  return before.title !== after.title || before.autoArchiveMinutes !== after.autoArchiveMinutes;
}

/** The tabs of the Threads list, one per effective status, in the order shown. */
export const CHAT_THREAD_TABS: readonly ChatThreadStatus[] = [
  ChatThreadStatus.ACTIVE,
  ChatThreadStatus.CLOSED,
  ChatThreadStatus.ARCHIVED,
];

/** What a row of the Threads list marks: unread replies and the notifications among them. */
export interface ChatThreadListMarks {
  unread: number;
  mentions: number;
}

const NO_MARKS: ChatThreadListMarks = { unread: 0, mentions: 0 };

/**
 * The marks of one thread in the Threads list. Only an active thread counts:
 * the server keeps read state for the active threads the reader is a member
 * of, so a closed or archived thread never shows a mark, as on web.
 */
export function chatThreadListMarks(
  thread: Pick<ChatThreadResponse, "id" | "status">,
  unread: ChatUnread | undefined
): ChatThreadListMarks {
  if (thread.status !== ChatThreadStatus.ACTIVE) return NO_MARKS;
  const entry: ChatConversationUnreadResponse | undefined = chatThreadUnread(unread, thread.id);
  if (!entry) return NO_MARKS;
  return { unread: entry.unread, mentions: entry.mentionCount };
}

/** The red "@N" of the thread summary under a message: its thread's marks, or none without a thread. */
export function chatMessageThreadMentions(
  message: Pick<ChatMessageResponse, "thread">,
  unread: ChatUnread | undefined
): number {
  const thread = message.thread;
  if (!thread) return 0;
  return chatThreadListMarks({ id: thread.threadId, status: thread.status }, unread).mentions;
}

/**
 * The badge of the Threads button in a channel's navigation bar: how many of
 * the reader's active threads in that channel hold unread replies. A mention
 * is always an unread reply too, so a thread with a mention counts once, and
 * a thread without a mention still counts, so new replies are seen without
 * opening the list.
 */
export function chatChannelUnreadThreads(
  unread: ChatUnread | undefined,
  channelId: string | null | undefined
): number {
  if (!unread || !channelId) return 0;
  return unread.threads.filter((entry) => entry.channelId === channelId && entry.unread > 0).length;
}

/**
 * The thread mark of a channel row on Chat home: the unread replies in the
 * reader's active threads of that channel and the notifications among them.
 * The Chat tab badge adds these replies to the channels' own unread counts,
 * so a channel whose only news is in a thread still shows where the badge
 * comes from.
 */
export function chatChannelThreadMarks(
  unread: ChatUnread | undefined,
  channelId: string | null | undefined
): ChatThreadListMarks {
  if (!unread || !channelId) return NO_MARKS;
  return unread.threads.reduce<ChatThreadListMarks>(
    (marks, entry) =>
      entry.channelId === channelId
        ? { unread: marks.unread + entry.unread, mentions: marks.mentions + entry.mentionCount }
        : marks,
    NO_MARKS
  );
}

/** The people a thread summary shows: the preview members by name, and the thread's member count. */
export interface ChatThreadSummaryPeople {
  names: string[];
  total: number;
}

/**
 * The member avatars under a message that started a thread: the preview
 * members the message's page names, in the server's order (latest repliers
 * first), skipping an ID the page does not carry; "+N" counts the rest of
 * the thread's members.
 */
export function chatThreadSummaryPeople(
  summary: Pick<ChatThreadSummaryResponse, "memberPreviewIds" | "memberCount">,
  users: Record<string, ChatUserResponse>,
  unknownMember: string
): ChatThreadSummaryPeople {
  const byId: Map<string, ChatUserResponse> = new Map(
    Object.entries(users).map(([id, user]) => [id.toLowerCase(), user] as const)
  );
  const names: string[] = summary.memberPreviewIds.flatMap((id: string) => {
    const user: ChatUserResponse | undefined = byId.get(id.toLowerCase());
    return user ? [chatUserDisplayName(user, unknownMember)] : [];
  });
  return { names, total: Math.max(summary.memberCount, names.length) };
}
