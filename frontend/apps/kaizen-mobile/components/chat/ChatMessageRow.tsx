import {
  Archive,
  ChevronRight,
  GitPullRequest,
  Lock,
  MessagesSquare,
  Newspaper,
  Pin,
  Reply,
  RotateCw,
  Trash2,
} from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { ChatMessageAttachments, ChatQuoteThumbnail } from "@/components/chat/ChatAttachmentViews";
import { isChatPreviewableImage } from "@/components/chat/chatAttachments";
import {
  chatAuthorName,
  chatEntrySummary,
  chatReactionPeople,
  chatUserDisplayName,
  isOwnChatMessage,
} from "@/components/chat/chatDisplay";
import { ChatMarkdownView, type ChatMarkdownHandlers } from "@/components/chat/ChatMarkdownView";
import { ChatPendingImages } from "@/components/chat/ChatPendingImages";
import { ChatSwipeToReply } from "@/components/chat/ChatSwipeToReply";
import { chatThreadSummaryPeople } from "@/components/chat/chatThreadModel";
import { Avatar, AvatarStack } from "@/components/ui/avatar";
import { formatBadgeCount } from "@/components/ui/badge-count";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import {
  CHAT_ERROR_CODES,
  ChatThreadStatus,
  type ChatAttachmentResponse,
  type ChatMessageResponse,
  type ChatOutboxMessage,
  type ChatReactionSummaryResponse,
} from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

/** Leading column of a row: the avatar, or the same width under a grouped header. */
const AVATAR_COLUMN = "w-8";

/**
 * The empty side of a row, so a bubble never spans the screen and whose
 * message it is reads from its side: the reader's own on the right, the
 * others' on the left.
 */
const OWN_ROW_GUTTER = "pl-12";
const OTHERS_ROW_GUTTER = "pr-8";

/**
 * Reaction chips are 32 tall; the slop brings the touch target to 44 pt on
 * iOS and 48 dp on Android without spacing the rows further apart.
 */
const REACTION_HIT_SLOP = Platform.select({
  ios: { top: 6, bottom: 6 },
  default: { top: 8, bottom: 8 },
});

export type ChatMessageRowProps = ChatMarkdownHandlers & {
  message: ChatMessageResponse;
  /** Continues the previous message of the same author: no avatar and no header. */
  grouped: boolean;
  meId: string | undefined;
  /** Opens the actions of a message; without it a long press does nothing. */
  onLongPress?: (message: ChatMessageResponse) => void;
  /**
   * Starts a reply to a message, from a swipe to the right or the screen
   * reader's Reply action; without it the message does not swipe.
   */
  onSwipeReply?: (message: ChatMessageResponse) => void;
  /** Toggles the reader's reaction; without it the reactions only show. */
  onReactionPress?: (message: ChatMessageResponse, reaction: ChatReactionSummaryResponse) => void;
  /** Opens the thread of a root message; without it the summary only shows. */
  onOpenThread?: (threadId: string) => void;
  /** Unread replies in this message's thread that notify the reader, shown as a red "@N". */
  threadMentionCount?: number;
  /** Leaves out the thread summary: the root shown above its own thread. */
  hideThreadSummary?: boolean;
  /** Marks the message a search result, a pin, a link or a quote just jumped to. */
  jumped?: boolean;
  /** Brings the message a reply quotes into view; without it the quote only shows. */
  onOpenQuote?: (messageId: string) => void;
  /** Opens a file of a message in the viewer; without it the files only show. */
  onOpenAttachment?: (message: ChatMessageResponse, attachment: ChatAttachmentResponse) => void;
};

/**
 * The message a reply answers: a reply arrow, then the quoted author with
 * their avatar and two lines of the text (or the file kind) on a `muted`
 * block with a `primary` bar. A tap brings the quoted message into view.
 */
export function ReplyQuote({
  message,
  onOpenQuote,
  className,
}: {
  message: ChatMessageResponse;
  onOpenQuote?: (messageId: string) => void;
  className?: string;
}) {
  const { t } = useTranslation();
  const reply = message.replyTo;
  if (!reply) return null;
  const author: string = reply.authorId
    ? chatUserDisplayName(message.users[reply.authorId], t("chatUnknownMember"))
    : t("chatUnknownMember");
  const text: string | null =
    reply.snippet.trim().length > 0
      ? reply.snippet
      : reply.attachments.length > 0
        ? t(
            reply.attachments.every((file) => isChatPreviewableImage(file.contentType))
              ? "chatQuotePhoto"
              : "chatQuoteAttachment"
          )
        : null;
  const pressable: boolean = !!onOpenQuote && !reply.deleted;
  return (
    <Pressable
      disabled={!pressable}
      onPress={() => onOpenQuote?.(reply.messageId)}
      accessibilityRole={pressable ? "button" : "text"}
      accessibilityLabel={
        reply.deleted
          ? t("chatReplyDeleted")
          : [t("chatLinkJump", { name: author }), text].filter(Boolean).join(", ")
      }
      className={cn("mb-1 max-w-full flex-row items-start gap-1.5 active:opacity-60", className)}
    >
      <Icon as={Reply} size={14} className="mt-2 text-muted-foreground" />
      {reply.deleted ? (
        <View className="shrink rounded-md border-l-2 border-border bg-muted py-1.5 pl-2.5 pr-2">
          <Text variant="footnote" className="italic text-muted-foreground">
            {t("chatReplyDeleted")}
          </Text>
        </View>
      ) : (
        <View className="shrink flex-row items-center gap-2 rounded-md border-l-2 border-primary bg-muted py-1.5 pl-2.5 pr-2">
          <View className="shrink gap-0.5">
            <View className="flex-row items-center gap-1.5">
              <Avatar name={author} size="sm" />
              <Text
                variant="footnote"
                numberOfLines={1}
                className="shrink font-sans-semibold text-primary"
              >
                {author}
              </Text>
            </View>
            {text ? (
              <Text variant="footnote" numberOfLines={2} className="text-muted-foreground">
                {text}
              </Text>
            ) : null}
          </View>
          <ChatQuoteThumbnail attachments={reply.attachments} />
        </View>
      )}
    </Pressable>
  );
}

/**
 * The reactions under a message: each emoji with its count, the reader's
 * own on `primary-light` ringed in `primary`; a tap toggles the reader's.
 * Also used under the cards of the pull requests and daily updates channels.
 */
export function ChatReactions({
  message,
  onReactionPress,
  className,
}: {
  message: ChatMessageResponse;
  onReactionPress?: ChatMessageRowProps["onReactionPress"];
  className?: string;
}) {
  const { t } = useTranslation();
  if (message.reactions.length === 0) return null;
  // A long press tells who reacted: the first ten names, then how many more.
  const showPeople = (reaction: ChatReactionSummaryResponse): void => {
    const people = chatReactionPeople(reaction, message.users, t("chatUnknownMember"));
    const listed: string = people.names.join(", ");
    const names: string =
      people.others > 0 ? t("chatReactionMore", { names: listed, count: people.others }) : listed;
    if (names) toast(t("chatReactedWith", { names, emoji: reaction.emoji }));
  };
  return (
    <View className={cn("mt-1.5 flex-row flex-wrap gap-2", className)}>
      {message.reactions.map((reaction) => (
        <Pressable
          key={reaction.emoji}
          onPress={onReactionPress ? () => onReactionPress(message, reaction) : undefined}
          onLongPress={() => showPeople(reaction)}
          delayLongPress={350}
          accessibilityRole="button"
          accessibilityState={{ selected: reaction.reactedByMe }}
          accessibilityLabel={t("chatReactionA11y", {
            emoji: reaction.emoji,
            count: reaction.count,
          })}
          hitSlop={REACTION_HIT_SLOP}
          className={cn(
            "min-h-8 min-w-11 flex-row items-center justify-center gap-1 rounded-full border px-2.5 py-1 active:opacity-60",
            reaction.reactedByMe ? "border-primary bg-primary-light" : "border-border bg-card"
          )}
        >
          <Text variant="subhead">{reaction.emoji}</Text>
          <Text variant="caption" className="text-foreground">
            {reaction.count}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

/**
 * The line under a message that started a thread: the title as a link, the
 * reply count and last reply, a red "@N" while replies notify the reader, the
 * chip of a closed or archived thread, and up to three member avatars with
 * "+N". A tap opens the thread. Also used under the cards of the pull
 * requests and daily updates channels.
 */
export function ChatThreadSummary({
  message,
  mentionCount = 0,
  onOpenThread,
  className,
}: {
  message: ChatMessageResponse;
  mentionCount?: number;
  onOpenThread?: (threadId: string) => void;
  className?: string;
}) {
  const { t } = useTranslation();
  const dates = useDates();
  const thread = message.thread;
  const unknownMember: string = t("chatUnknownMember");
  const people = React.useMemo(
    () => (thread ? chatThreadSummaryPeople(thread, message.users, unknownMember) : null),
    [thread, message.users, unknownMember]
  );
  if (!thread || !people) return null;
  const mentionText: string | null = formatBadgeCount(mentionCount);
  const statusLabel: string | null =
    thread.status === ChatThreadStatus.CLOSED
      ? t("chatThreadsClosed")
      : thread.status === ChatThreadStatus.ARCHIVED
        ? t("chatThreadsArchived")
        : null;
  const activity: string = [
    t("chatThreadReplies", { count: thread.messageCount }),
    thread.messageCount > 0 ? dates.relativePast(thread.lastMessageAt) : null,
  ]
    .filter(Boolean)
    .join(" · ");
  const label: string = [
    thread.title,
    statusLabel,
    activity,
    mentionCount > 0 ? t("chatThreadsMentions", { count: mentionCount }) : null,
  ]
    .filter(Boolean)
    .join(", ");
  return (
    <Pressable
      disabled={!onOpenThread}
      onPress={() => onOpenThread?.(thread.threadId)}
      accessibilityRole={onOpenThread ? "button" : "text"}
      accessibilityLabel={label}
      className={cn(
        "mt-1.5 min-h-11 flex-row items-center gap-2 self-stretch rounded-md border border-border bg-card px-3 py-2 active:opacity-60",
        className
      )}
    >
      <Icon as={MessagesSquare} size={16} className="text-link" />
      <View className="flex-1 gap-0.5">
        <View className="flex-row items-center gap-1.5">
          <Text
            variant="footnote"
            numberOfLines={1}
            className="shrink font-sans-semibold text-link"
          >
            {thread.title}
          </Text>
        </View>
        <View className="flex-row items-center gap-1.5">
          {statusLabel ? (
            <View className="flex-row items-center gap-1 rounded-full border border-border bg-muted px-1.5">
              <Icon
                as={thread.status === ChatThreadStatus.CLOSED ? Lock : Archive}
                size={11}
                className="text-muted-foreground"
              />
              <Text variant="caption" className="text-muted-foreground">
                {statusLabel}
              </Text>
            </View>
          ) : null}
          <Text variant="caption" numberOfLines={1} className="shrink text-muted-foreground">
            {activity}
          </Text>
        </View>
      </View>
      {people.names.length > 0 ? (
        <AvatarStack
          people={people.names.map((name: string) => ({ name }))}
          total={people.total}
          size="sm"
        />
      ) : null}
      {mentionText ? (
        <View className="min-h-[18px] min-w-[18px] items-center justify-center rounded-full bg-destructive-fill px-1">
          <Text className="font-sans-semibold text-caption-2 text-destructive-foreground">
            {`@${mentionText}`}
          </Text>
        </View>
      ) : null}
      {onOpenThread ? <Icon as={ChevronRight} size={16} className="text-muted-foreground" /> : null}
    </Pressable>
  );
}

function EntryCard({ message }: { message: ChatMessageResponse }) {
  const { t } = useTranslation();
  const summary: string | null = chatEntrySummary(message);
  if (summary === null) return null;
  const dailyUpdate: boolean = message.dailyUpdateId != null;
  return (
    <View className="mt-0.5 gap-1 self-stretch rounded-md border border-border bg-card px-3 py-2">
      <View className="flex-row items-center gap-1.5">
        <Icon
          as={dailyUpdate ? Newspaper : GitPullRequest}
          size={14}
          className="text-muted-foreground"
        />
        <Text variant="caption" className="text-muted-foreground">
          {t(dailyUpdate ? "chatDailyUpdateEntry" : "chatPullRequestEntry")}
        </Text>
      </View>
      {summary ? <Text variant="subhead">{summary}</Text> : null}
    </View>
  );
}

/**
 * The box around a message's text: `muted` with a `border` edge for others,
 * `primary-light` with a `primary-medium` edge for the reader's own, and
 * `chat-highlight` with a bar on its leading edge when it mentions or answers
 * the reader. A message just jumped to is ringed. The first message of an
 * author's group points at its side with a squared top corner.
 *
 * The bubble is as wide as its content, up to the row. Content inside it
 * therefore sizes from its own text: a child that fills the remaining space
 * (`flex-1`) has no width of its own to give and collapses, or wraps to a
 * different width than the bubble measured, so rows inside use `shrink`.
 */
function MessageBubble({
  own,
  tail,
  highlighted = false,
  jumped = false,
  children,
}: {
  own: boolean;
  tail: boolean;
  highlighted?: boolean;
  jumped?: boolean;
  children: React.ReactNode;
}) {
  return (
    <View
      className={cn(
        "max-w-full rounded-2xl border px-3 py-2",
        highlighted
          ? "border-l-[3px] border-chat-highlight-border bg-chat-highlight"
          : own
            ? "border-primary-medium bg-primary-light"
            : "border-border bg-muted",
        jumped && "border-2 border-ring",
        tail && (own ? "rounded-tr-sm" : "rounded-tl-sm")
      )}
    >
      {children}
    </View>
  );
}

/**
 * One message of a channel. Others' messages sit on the left with the
 * author's avatar, the reader's own on the right without one, each in its
 * bubble; then the quoted reply, a daily update or pull request card,
 * files, reactions and the thread summary on the same side. A message that
 * mentions or answers the reader is highlighted.
 */
function ChatMessageRowImpl({
  message,
  grouped,
  meId,
  onOpenTicket,
  onOpenLink,
  onLongPress,
  onSwipeReply,
  onReactionPress,
  onOpenThread,
  threadMentionCount = 0,
  hideThreadSummary = false,
  onOpenAttachment,
  jumped = false,
  onOpenQuote,
}: ChatMessageRowProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const author: string = chatAuthorName(message, t("chatUnknownMember"));
  const time: string | null = dates.time(message.createdAt);
  const own: boolean = isOwnChatMessage(message, meId);
  const highlighted: boolean = !message.deleted && (message.mentionsMe || message.repliesToMe);
  const replyable: boolean = !!onSwipeReply && !message.deleted;
  const reply = React.useCallback(() => onSwipeReply?.(message), [onSwipeReply, message]);
  const hasText: boolean = message.content.trim().length > 0;
  const bubble: boolean = message.deleted || hasText;
  const edited: boolean = !!message.editedAt && !message.deleted;

  const row = (
    <Pressable
      onLongPress={onLongPress ? () => onLongPress(message) : undefined}
      delayLongPress={350}
      accessibilityActions={
        replyable ? [{ name: "reply", label: t("chatActionReply") }] : undefined
      }
      onAccessibilityAction={
        replyable
          ? (event) => {
              if (event.nativeEvent.actionName === "reply") reply();
            }
          : undefined
      }
      className={cn(
        "flex-row gap-3 px-4",
        own ? OWN_ROW_GUTTER : OTHERS_ROW_GUTTER,
        grouped ? "pt-0.5" : "pt-3",
        // A message without a text bubble shows the jump mark on its row.
        jumped && !bubble && "bg-primary-light"
      )}
    >
      {own ? null : (
        <View className={AVATAR_COLUMN}>{grouped ? null : <Avatar name={author} size="md" />}</View>
      )}
      <View className={cn("flex-1 pb-0.5", own ? "items-end" : "items-start")}>
        {grouped ? null : (
          // The reader's own header is the time alone: the side and colour say whose it is.
          <View className="mb-0.5 flex-row items-baseline gap-1.5 px-1">
            {own ? null : (
              <Text variant="subhead" numberOfLines={1} className="shrink font-sans-semibold">
                {author}
              </Text>
            )}
            {time ? (
              <Text variant="caption" className="text-muted-foreground">
                {time}
              </Text>
            ) : null}
            {edited ? (
              <Text variant="caption" className="italic text-muted-foreground">
                {`(${t("chatEdited")})`}
              </Text>
            ) : null}
            {message.pinnedAt ? (
              <Icon as={Pin} size={12} className="text-muted-foreground" />
            ) : null}
          </View>
        )}
        <ReplyQuote message={message} onOpenQuote={onOpenQuote} />
        {message.deleted ? (
          <MessageBubble own={own} tail={!grouped} jumped={jumped}>
            <Text className="italic text-muted-foreground">{t("chatDeletedMessage")}</Text>
          </MessageBubble>
        ) : hasText ? (
          <MessageBubble own={own} tail={!grouped} highlighted={highlighted} jumped={jumped}>
            <ChatMarkdownView
              message={message}
              meId={meId}
              onOpenTicket={onOpenTicket}
              onOpenLink={onOpenLink}
            />
          </MessageBubble>
        ) : null}
        {edited && grouped ? (
          <Text variant="caption" className="mt-0.5 px-1 italic text-muted-foreground">
            {`(${t("chatEdited")})`}
          </Text>
        ) : null}
        <EntryCard message={message} />
        {message.deleted ? null : (
          <ChatMessageAttachments
            attachments={message.attachments}
            own={own}
            onOpen={
              onOpenAttachment
                ? (attachment: ChatAttachmentResponse) => onOpenAttachment(message, attachment)
                : undefined
            }
          />
        )}
        <ChatReactions message={message} onReactionPress={onReactionPress} />
        {hideThreadSummary ? null : (
          <ChatThreadSummary
            message={message}
            mentionCount={threadMentionCount}
            onOpenThread={onOpenThread}
          />
        )}
      </View>
    </Pressable>
  );

  return replyable ? <ChatSwipeToReply onReply={reply}>{row}</ChatSwipeToReply> : row;
}

export const ChatMessageRow = React.memo(ChatMessageRowImpl);

type ChatOutboxRowProps = {
  row: ChatOutboxMessage;
  /** The row's text as the reader reads it, mentions by name. */
  text: string;
  onRetry: (clientNonce: string) => void;
  onDiscard: (clientNonce: string) => void;
};

/**
 * A message of this device the server has not confirmed, on the reader's
 * side: "Sending…" while the request runs, then "Not sent" with Retry and
 * Delete. The text stays, so nothing typed is lost.
 */
function ChatOutboxRowImpl({ row, text, onRetry, onDiscard }: ChatOutboxRowProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const failed: boolean = row.status === "failed";
  const time: string | null = dates.time(row.createdAt);
  return (
    <View className={cn("px-4 pt-3", OWN_ROW_GUTTER)}>
      <View className={cn("items-end pb-0.5", !failed && "opacity-60")}>
        {time ? (
          <Text variant="caption" className="mb-0.5 px-1 text-muted-foreground">
            {time}
          </Text>
        ) : null}
        {text.trim().length > 0 ? (
          <MessageBubble own tail>
            <Text>{text}</Text>
          </MessageBubble>
        ) : null}
        <ChatPendingImages files={row.files} />
        {failed && row.files.length > 0 ? (
          <View className="mt-1 items-end">
            {row.files.map((file) => (
              <Text key={file.key} variant="caption" className="text-destructive">
                {t("chatFileNotSent", { name: file.name })}
              </Text>
            ))}
          </View>
        ) : null}
        {failed ? (
          <View className="mt-1 items-end gap-0.5" accessibilityRole="alert">
            <View className="flex-row flex-wrap items-center justify-end gap-x-1">
              <Text variant="footnote" className="mr-1 text-destructive">
                {t("chatNotSent")}
              </Text>
              <Button
                variant="ghost"
                size="sm"
                icon={RotateCw}
                onPress={() => onRetry(row.clientNonce)}
              >
                {t("chatRetry")}
              </Button>
              <Button variant="ghost" size="sm" onPress={() => onDiscard(row.clientNonce)}>
                <Icon as={Trash2} size={16} className="text-destructive" />
                <Text className="text-destructive">{t("chatDelete")}</Text>
              </Button>
            </View>
            {row.errorCode === CHAT_ERROR_CODES.rateLimited ? (
              <Text variant="caption" className="text-muted-foreground">
                {t("chatRateLimited")}
              </Text>
            ) : null}
          </View>
        ) : (
          <Text variant="caption" className="mt-0.5 text-muted-foreground">
            {row.progress !== null
              ? t("chatUploading", { percent: Math.round(row.progress * 100) })
              : t("chatSending")}
          </Text>
        )}
      </View>
    </View>
  );
}

export const ChatOutboxRow = React.memo(ChatOutboxRowImpl);
