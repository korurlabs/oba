"use client";

import { memo, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Loader2, MessageSquareOff, RotateCcw, Trash2 } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import { getDisplayName, type UserAvatarUser } from "@/components/ui/user-avatar";
import { PullRequestShareContent } from "@/features/pull-requests/components/pull-request-message-card";
import { UpdateMessageContent } from "@/features/updates/components/update-message-card";
import type { UseDateFormat } from "@oba/shared/i18n/use-date-format";
import {
  CHAT_ERROR_CODES,
  type ChatMessageResponse,
  type ChatOutboxMessage,
  type ChatReplyPreviewResponse,
  type ChatUserResponse,
} from "../schemas";
import { ChatAttachments, ChatPendingFiles, type ChatMessageSide } from "./chat-attachments";
import { useChatMessageRow } from "./chat-message-actions";
import { ChatMessageContent } from "./chat-message-content";
import {
  ChatMessageBubble,
  ChatMessageLayout,
  ChatMessagePart,
  ChatRowFrame,
  HoverToolbar,
  MessageMeta,
} from "./chat-message-shell";
import { ChatReplyPreview } from "./chat-reply-preview";

export { CHAT_ROW_ATTRIBUTE } from "./chat-message-shell";

/** Renders per-message extras into a row; called for every live message. */
export type ChatMessageSlot = (message: ChatMessageResponse) => ReactNode;

function authorUser(
  author: { firstName?: string | null; lastName?: string | null; email?: string | null } | null,
): UserAvatarUser {
  return {
    firstName: author?.firstName ?? undefined,
    lastName: author?.lastName ?? undefined,
    email: author?.email ?? undefined,
  };
}

// ── Message ─────────────────────────────────────────────────────────

interface ChatMessageProps {
  message: ChatMessageResponse;
  meId: string | undefined;
  dates: UseDateFormat;
  grouped: boolean;
  highlighted: boolean;
  /** Goes to a quoted message, loading the messages around it when it is not loaded. */
  onJumpTo: (messageId: string) => void;
  renderFooter?: ChatMessageSlot;
}

/**
 * One message of a channel or thread: avatar, name and time on the first
 * message of an author's group, the reply quote above, then the text in a
 * bubble, the files and the footer (reactions, thread summary). The reader's
 * own messages are mirrored to the right. A daily update and a shared pull
 * request fill the same bubble with their card; the deleted root of a thread
 * leaves a placeholder that keeps the thread reachable.
 */
export const ChatMessage = memo(function ChatMessage({
  message,
  meId,
  dates,
  grouped,
  highlighted,
  onJumpTo,
  renderFooter,
}: ChatMessageProps) {
  const t = useTranslations("chat.message");
  const interaction = useChatMessageRow(message);
  const footer = renderFooter?.(message);

  if (message.deleted) {
    // The server sends the placeholder without its author, so it cannot take a side.
    return (
      <ChatRowFrame rowId={message.id} grouped={false} highlighted={highlighted}>
        <div className="flex gap-3">
          <div className="flex w-8 shrink-0 justify-center pt-0.5">
            <MessageSquareOff className="text-muted-foreground h-4 w-4" aria-hidden />
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-1.5">
            <div>
              <p className="text-muted-foreground text-sm italic">{t("deleted")}</p>
              {message.thread && (
                <p className="text-foreground text-xs font-medium">
                  {t("threadPlaceholder", { title: message.thread.title })}
                </p>
              )}
            </div>
            {footer}
          </div>
        </div>
      </ChatRowFrame>
    );
  }

  const own = !!meId && message.author?.userId === meId;
  const side: ChatMessageSide = own ? "end" : "start";

  const toolbar = interaction && <HoverToolbar row={interaction} side={side} />;

  if (message.dailyUpdateId != null && message.dailyUpdate) {
    const update = message.dailyUpdate;
    const updateAuthor = authorUser(update.author);
    return (
      <ChatRowFrame
        rowId={message.id}
        grouped={false}
        highlighted={highlighted}
        own={own}
        interaction={interaction}
      >
        <ChatMessageLayout
          side={side}
          grouped={false}
          author={updateAuthor}
          name={getDisplayName(updateAuthor) || t("unknownMember")}
          createdAt={update.createdAt}
          dates={dates}
          meta={
            <MessageMeta editedAt={update.editedAt} pinnedAt={message.pinnedAt} dates={dates} />
          }
          footer={footer}
          toolbar={toolbar}
        >
          <ChatMessageBubble side={side} tail fill>
            <UpdateMessageContent update={update} currentUserId={meId} />
          </ChatMessageBubble>
        </ChatMessageLayout>
      </ChatRowFrame>
    );
  }

  const user = authorUser(message.author);
  const name = getDisplayName(user) || t("unknownMember");

  if (message.pullRequestShareId != null && message.pullRequestShare) {
    const share = message.pullRequestShare;
    return (
      <ChatRowFrame
        rowId={message.id}
        grouped={false}
        mentioned={message.mentionsMe || message.repliesToMe}
        highlighted={highlighted}
        own={own}
        interaction={interaction}
      >
        <ChatMessageLayout
          side={side}
          grouped={false}
          author={user}
          name={name}
          createdAt={message.createdAt}
          dates={dates}
          meta={<MessageMeta editedAt={share.editedAt} pinnedAt={message.pinnedAt} dates={dates} />}
          footer={footer}
          toolbar={toolbar}
        >
          <ChatMessageBubble side={side} tail fill>
            <PullRequestShareContent
              share={share}
              messageId={message.id}
              authorName={name}
              currentUserId={meId}
            />
          </ChatMessageBubble>
        </ChatMessageLayout>
      </ChatRowFrame>
    );
  }

  return (
    <ChatRowFrame
      rowId={message.id}
      grouped={grouped}
      mentioned={message.mentionsMe || message.repliesToMe}
      highlighted={highlighted}
      own={own}
      interaction={interaction}
    >
      <ChatMessageLayout
        side={side}
        grouped={grouped}
        author={user}
        name={name}
        createdAt={message.createdAt}
        dates={dates}
        meta={<MessageMeta editedAt={message.editedAt} pinnedAt={message.pinnedAt} dates={dates} />}
        quote={
          message.replyTo && <ChatReplyPreview replyTo={message.replyTo} users={message.users} onJump={onJumpTo} />
        }
        footer={footer}
        toolbar={toolbar}
      >
        {message.content.trim().length > 0 && (
          <ChatMessageBubble side={side} tail={!grouped}>
            <ChatMessageContent
              content={message.content}
              users={message.users}
              ticketRefs={message.ticketRefs}
              linkedTickets={message.linkedTickets}
              meId={meId}
              mentionsMe={message.mentionsMe}
            />
          </ChatMessageBubble>
        )}
        {message.attachments.length > 0 && (
          <ChatMessagePart>
            <ChatAttachments attachments={message.attachments} side={side} className="mt-0" />
          </ChatMessagePart>
        )}
      </ChatMessageLayout>
    </ChatRowFrame>
  );
});

// ── Unsent message ──────────────────────────────────────────────────

const NO_TICKET_REFS: [] = [];

interface ChatOutboxRowProps {
  row: ChatOutboxMessage;
  /** The reader, who wrote it. */
  author: UserAvatarUser;
  meId: string | undefined;
  /** Known users, so mentions in the text show names before the server confirms it. */
  users: Record<string, ChatUserResponse>;
  dates: UseDateFormat;
  grouped: boolean;
  /** The quote of a reply, built from the loaded original; null when it is not loaded. */
  replyTo: ChatReplyPreviewResponse | null;
  replyUsers: Record<string, ChatUserResponse>;
  onJumpTo: (messageId: string) => void;
  onRetry: (clientNonce: string) => void;
  onDiscard: (clientNonce: string) => void;
}

/**
 * A message of this tab the server has not confirmed: dimmed with
 * "Sending…" while it is sent, "Not sent" with the reason, Retry and Delete
 * after a failure. Its text and files stay here until it is sent or deleted. The reader wrote it,
 * so it sits on the right as their sent messages do.
 */
export function ChatOutboxRow({
  row,
  author,
  meId,
  users,
  dates,
  grouped,
  replyTo,
  replyUsers,
  onJumpTo,
  onRetry,
  onDiscard,
}: ChatOutboxRowProps) {
  const t = useTranslations("chat.message");
  const failed = row.status === "failed";
  const reason =
    row.errorCode === CHAT_ERROR_CODES.rateLimited ? t("tooFast") : (row.errorMessage ?? undefined);

  const inGroup = grouped && !replyTo;
  return (
    <ChatRowFrame rowId={`outbox-${row.clientNonce}`} grouped={inGroup} own>
      <ChatMessageLayout
        side="end"
        grouped={inGroup}
        author={author}
        name={getDisplayName(author)}
        createdAt={row.createdAt}
        dates={dates}
        quote={replyTo && <ChatReplyPreview replyTo={replyTo} users={replyUsers} onJump={onJumpTo} />}
        footer={
          failed ? (
            <div role="alert" className="flex flex-wrap items-center justify-end gap-x-2 gap-y-1 text-xs">
              <span className="text-destructive inline-flex items-center gap-1 font-medium">
                <AlertCircle className="h-3.5 w-3.5" aria-hidden />
                {t("notSent")}
              </span>
              {reason && <span className="text-muted-foreground">{reason}</span>}
              <Button
                variant="ghost"
                size="sm"
                className="h-6 gap-1 px-2 text-xs"
                onClick={() => onRetry(row.clientNonce)}
              >
                <RotateCcw className="h-3 w-3" />
                {t("retry")}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="text-destructive hover:text-destructive h-6 gap-1 px-2 text-xs"
                onClick={() => onDiscard(row.clientNonce)}
              >
                <Trash2 className="h-3 w-3" />
                {t("discard")}
              </Button>
            </div>
          ) : (
            <span className="text-muted-foreground inline-flex items-center justify-end gap-1 text-xs">
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden />
              {t("pending")}
            </span>
          )
        }
      >
        <div
          className={cn("flex w-full min-w-0 flex-col items-end gap-1.5", !failed && "opacity-60")}
          aria-busy={!failed}
        >
          {row.content.trim().length > 0 && (
            <ChatMessageBubble side="end" tail={!inGroup}>
              <ChatMessageContent
                content={row.content}
                users={users}
                ticketRefs={NO_TICKET_REFS}
                linkedTickets={NO_TICKET_REFS}
                meId={meId}
                mentionsMe={false}
              />
            </ChatMessageBubble>
          )}
          <ChatPendingFiles files={row.files} side="end" />
        </div>
      </ChatMessageLayout>
    </ChatRowFrame>
  );
}
