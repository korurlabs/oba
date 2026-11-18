import { chatCanModerate } from "@/components/chat/chatMessageActions";
import type { TranslationKey } from "@/i18n";
import { MEMBER_WITHOUT_KAIZEN_ACCESS_CODE, hasKaizenAccess } from "@/lib/kaizenAccess";
import {
  CHAT_CHANNEL_MAX_MEMBER_IDS,
  CHAT_CHANNEL_NAME_MAX,
  CHAT_ERROR_CODES,
  ChatChannelPurpose,
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatChannelUpdateRequest,
  type ChatMemberResponse,
} from "@/lib/schemas/chat";

/*
 * The channel menu and the channel change rules that need no React. They are
 * the web app's rules: owners and admins of the organization create, change
 * and delete channels and manage a private channel's members; any member of
 * a private channel may leave it. The server enforces the same.
 */

/** Every action of the channel menu, in the order it lists them. */
export type ChatChannelMenuActionId =
  | "members"
  | "pins"
  | "create"
  | "change"
  | "manageMembers"
  | "leave"
  | "delete";

const MENU_ORDER: readonly ChatChannelMenuActionId[] = [
  "members",
  "pins",
  "create",
  "change",
  "manageMembers",
  "leave",
  "delete",
];

/**
 * The channel menu for a reader with the organization `role` (null while the
 * profile loads, which offers nothing role-gated). Members and pins read the
 * channel, so they need the reader on it; New channel, Change channel and
 * Delete channel are for owners and admins; Manage members for owners and
 * admins on a private channel; Leave for a member of a private channel.
 */
export function chatChannelMenuActions(
  channel: Pick<ChatChannelResponse, "visibility" | "member">,
  role: string | null
): ChatChannelMenuActionId[] {
  const canManage: boolean = chatCanModerate(role);
  const isPrivate: boolean = channel.visibility === ChatChannelVisibility.PRIVATE;
  const allowed: Record<ChatChannelMenuActionId, boolean> = {
    members: channel.member,
    pins: channel.member,
    create: canManage,
    change: canManage,
    manageMembers: canManage && isPrivate,
    leave: isPrivate && channel.member,
    delete: canManage,
  };
  return MENU_ORDER.filter((id) => allowed[id]);
}

/**
 * The channel menu's rows that manage the channel, in its order: the menu
 * without Members and Pinned, which the daily updates and pull requests
 * screens list among their own rows.
 */
export function chatChannelManageActions(
  channel: Pick<ChatChannelResponse, "visibility" | "member">,
  role: string | null
): ChatChannelMenuActionId[] {
  return chatChannelMenuActions(channel, role).filter((id) => id !== "members" && id !== "pins");
}

/** The reader's own delete or leave of a channel, as the screen that sent it tracks it. */
export interface ChatChannelRemovalState {
  /** The channel whose delete or leave was sent and has not failed; null when none is. */
  removingId: string | null;
  /** The delete or leave of the shown channel succeeded, and the screen is closing. */
  leaving: boolean;
}

/**
 * True when a "not found" for the shown channel is the reader's own doing:
 * its delete or leave is on the way or done. A realtime refresh can answer
 * before the request does, and that answer names no loss.
 */
export function chatChannelGoneExpected(
  state: ChatChannelRemovalState,
  shownChannelId: string
): boolean {
  return state.leaving || (state.removingId !== null && state.removingId === shownChannelId);
}

/**
 * True when the screen shows only its header: it is closing, or the channel
 * list already shows the reader's delete or leave (the channel gone, or the
 * reader no longer on it) while the request is still on the way. The
 * not-found and not-member states are for a loss the reader did not cause.
 */
export function chatChannelContentHidden(
  state: ChatChannelRemovalState,
  shownChannelId: string,
  channel: Pick<ChatChannelResponse, "member"> | undefined
): boolean {
  if (state.leaving) return true;
  return chatChannelGoneExpected(state, shownChannelId) && (!channel || !channel.member);
}

/**
 * The text of the delete confirmation: a channel that receives the daily
 * updates or the pull requests says those stop reaching the chat.
 */
export function chatChannelDeleteMessageKey(purpose: ChatChannelPurpose): TranslationKey {
  switch (purpose) {
    case ChatChannelPurpose.DAILY_UPDATES:
      return "chatChannelDeleteBodyDailyUpdates";
    case ChatChannelPurpose.PULL_REQUESTS:
      return "chatChannelDeleteBodyPullRequests";
    default:
      return "chatChannelDeleteBody";
  }
}

/** The values of the channel form. `memberIds` counts only when the channel becomes private. */
export interface ChatChannelDraft {
  name: string;
  visibility: ChatChannelVisibility;
  purpose: ChatChannelPurpose;
  memberIds: readonly string[];
}

/** The change form of a channel starts from its current values, with no members picked. */
export function chatChannelUpdateDraft(
  channel: Pick<ChatChannelResponse, "name" | "visibility" | "purpose">
): ChatChannelDraft {
  return {
    name: channel.name,
    visibility: channel.visibility,
    purpose: channel.purpose,
    memberIds: [],
  };
}

/** True when the draft turns a public channel private, the only change that takes members. */
export function chatChannelMakesPrivate(
  channel: Pick<ChatChannelResponse, "visibility">,
  draft: Pick<ChatChannelDraft, "visibility">
): boolean {
  return (
    channel.visibility === ChatChannelVisibility.PUBLIC &&
    draft.visibility === ChatChannelVisibility.PRIVATE
  );
}

/** True when the draft turns a private channel public, which shows its past messages to everyone. */
export function chatChannelMakesPublic(
  channel: Pick<ChatChannelResponse, "visibility">,
  draft: Pick<ChatChannelDraft, "visibility">
): boolean {
  return (
    channel.visibility === ChatChannelVisibility.PRIVATE &&
    draft.visibility === ChatChannelVisibility.PUBLIC
  );
}

/**
 * The members preselected when a public channel is made private: everyone
 * who wrote in it, except the reader, whom the server adds anyway. Nobody
 * then loses a conversation they took part in without the admin seeing it.
 * An author without Kaizen is left out: the server refuses them as a member,
 * and the member list shows why they cannot be picked.
 */
export function chatChannelAuthorIds(
  authors: readonly Pick<ChatMemberResponse, "userId" | "kaizenAccess">[],
  meId: string | undefined
): string[] {
  return authors
    .filter((author) => author.userId !== meId && hasKaizenAccess(author))
    .map((author) => author.userId);
}

/**
 * True when the private channel would hold a purpose other than General,
 * which the server refuses: such a purpose serves the whole organization.
 */
export function chatChannelPrivateWithPurpose(
  draft: Pick<ChatChannelDraft, "visibility" | "purpose">
): boolean {
  return (
    draft.visibility === ChatChannelVisibility.PRIVATE &&
    draft.purpose !== ChatChannelPurpose.GENERAL
  );
}

/** True when more members are picked than one request takes. */
export function chatChannelMembersOverLimit(memberIds: readonly string[]): boolean {
  return memberIds.length > CHAT_CHANNEL_MAX_MEMBER_IDS;
}

/**
 * True when the change can be saved: a name of 1 to 80 characters after
 * trimming, no purpose but General on a private channel, and, when the
 * channel becomes private, at most the members one request takes. Members
 * picked before the draft went back to public are not sent, so they do not
 * count.
 */
export function chatChannelUpdateValid(
  channel: Pick<ChatChannelResponse, "visibility">,
  draft: ChatChannelDraft
): boolean {
  const name: string = draft.name.trim();
  return (
    name.length > 0 &&
    name.length <= CHAT_CHANNEL_NAME_MAX &&
    !chatChannelPrivateWithPurpose(draft) &&
    !(chatChannelMakesPrivate(channel, draft) && chatChannelMembersOverLimit(draft.memberIds))
  );
}

/**
 * True when saving the draft would change the channel: the update differs
 * from the values the sheet opened with, or it carries members. A draft that
 * went back to public after the authors were preselected sends no members,
 * so it counts as unchanged.
 */
export function chatChannelUpdateChanged(
  channel: Pick<ChatChannelResponse, "visibility">,
  initial: ChatChannelDraft,
  draft: ChatChannelDraft
): boolean {
  const update: ChatChannelUpdateRequest = chatChannelUpdateRequest(channel, draft);
  return (
    update.name !== initial.name.trim() ||
    update.visibility !== initial.visibility ||
    update.purpose !== initial.purpose ||
    (update.memberIds?.length ?? 0) > 0
  );
}

/**
 * The update a change sends: the trimmed name, the visibility and the
 * purpose, and the picked members only when the channel becomes private.
 * Any other update with members is refused by the server, even an empty list
 * is left out then.
 */
export function chatChannelUpdateRequest(
  channel: Pick<ChatChannelResponse, "visibility">,
  draft: ChatChannelDraft
): ChatChannelUpdateRequest {
  return {
    name: draft.name.trim(),
    visibility: draft.visibility,
    purpose: draft.purpose,
    memberIds: chatChannelMakesPrivate(channel, draft) ? [...draft.memberIds] : undefined,
  };
}

/**
 * The text of each channel write refusal that the app explains in the
 * reader's language, the same set the web app explains next to its forms.
 * Any other code is shown with the server's message.
 */
const CHANNEL_ERROR_KEYS: Readonly<Record<string, TranslationKey>> = {
  [CHAT_ERROR_CODES.channelMembersNotExpected]: "chatChannelErrorMembersNotExpected",
  [CHAT_ERROR_CODES.channelNotPrivate]: "chatChannelErrorNotPrivate",
  [CHAT_ERROR_CODES.channelPurposePrivate]: "chatChannelErrorPurposePrivate",
  [CHAT_ERROR_CODES.channelPurposeTaken]: "chatChannelErrorPurposeTaken",
  [CHAT_ERROR_CODES.mentionNotMember]: "chatChannelErrorMemberNotActive",
  [CHAT_ERROR_CODES.channelNotFound]: "chatChannelErrorNotFound",
  [CHAT_ERROR_CODES.channelMemberRemoveRefused]: "chatChannelErrorNotAllowed",
  [CHAT_ERROR_CODES.organizationRoleRefused]: "chatChannelErrorNotAllowed",
  [MEMBER_WITHOUT_KAIZEN_ACCESS_CODE]: "chatChannelErrorNoKaizenAccess",
};

/** The text key of a refused channel write, or null for a code shown with the server's message. */
export function chatChannelErrorKey(code: string | undefined): TranslationKey | null {
  return code !== undefined ? (CHANNEL_ERROR_KEYS[code] ?? null) : null;
}
