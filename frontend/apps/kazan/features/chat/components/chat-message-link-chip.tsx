"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { Hash, MessageSquareText } from "lucide-react";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { UserAvatar, getDisplayName } from "@/components/ui/user-avatar";
import { useChatChannels, useChatOpenChannelId } from "../hooks/use-chat-channels";
import { useChatParams } from "../hooks/use-chat-params";
import { useChatMessage, useChatThread } from "../hooks/use-chat-messages";
import type { ChatMessageLink, ChatMessageLinkChannel } from "../lib/chat-message-link";
import { chatMessageSnippet } from "../lib/chat-message-text";
import { chatChannelPath } from "../lib/chat-route";
import type { ChatChannelResponse } from "../schemas";

/** About one line of the linked message. */
const SNIPPET_LENGTH = 80;

const CHIP_CLASS =
  "inline-flex max-w-full min-w-0 items-center gap-1 rounded-none border px-1 py-px align-middle text-[13px] leading-5";

/**
 * A loaded chip is about one line of text wide (`max-w-md` on the chip) and
 * never wider than the message bubble it sits in (this frame), so it does not
 * run over the bubble's edge or the time beside it. The two caps sit on two
 * elements because the bubble sizes itself to its content: a fixed cap is
 * seen in that measurement, a percentage cap is not, and one percentage cap
 * on the chip would let a long snippet widen the bubble far past the chip.
 */
const LOADED_CHIP_FRAME_CLASS = "inline-flex max-w-full min-w-0 align-middle";

interface ChatMessageLinkPreviewProps {
  link: ChatMessageLink;
  /**
   * Goes to the message, in the channel the preview names (null before the
   * preview loads or when it failed); without it the chip is not
   * interactive (the composer).
   */
  onJump?: (messageId: string, threadId: string | null, channelId: string | null) => void;
}

/**
 * A link to a chat message shown as a chip: the author's avatar and name,
 * the start of the text and the thread when the message is in one. A
 * message that is deleted or not readable here shows as unavailable and
 * does not jump; a preview that failed for another reason still jumps, as
 * the link itself may be fine.
 */
export function ChatMessageLinkPreview({ link, onJump }: ChatMessageLinkPreviewProps) {
  const t = useTranslations("chat");
  const { data: message, isPending, isError, error } = useChatMessage(link.messageId);
  const unavailable = message === null || !!message?.deleted;
  const failed = isError;
  const threadId = message?.threadId ?? link.threadId;
  const channelId = message?.channelId ?? null;
  const { data: thread } = useChatThread(message && threadId ? threadId : null);

  useEffect(() => {
    if (failed) console.error("[chat] could not load the preview of a message link", link.messageId, error);
  }, [failed, link.messageId, error]);

  if (unavailable) {
    return (
      <span className={cn(CHIP_CLASS, "bg-muted/60 text-muted-foreground italic")} title={link.href}>
        <MessageSquareText className="size-3.5 shrink-0" aria-hidden />
        {t("messageLink.unavailable")}
      </span>
    );
  }

  const jump = onJump ? () => onJump(link.messageId, threadId, channelId) : undefined;
  const interactiveClass =
    "border-primary/30 bg-primary/5 text-primary hover:bg-primary/10 focus-visible:ring-ring cursor-pointer text-left focus-visible:ring-2 focus-visible:outline-none";

  if (isPending || failed || !message) {
    const label = failed ? t("messageLink.error") : t("messageLink.loading");
    const body = (
      <>
        <MessageSquareText className="size-3.5 shrink-0" aria-hidden />
        {failed ? (
          <span className="truncate">{label}</span>
        ) : (
          <span className="bg-primary/15 inline-block h-3 w-40 animate-pulse" aria-hidden />
        )}
      </>
    );
    return jump ? (
      <button type="button" onClick={jump} aria-label={label} title={link.href} className={cn(CHIP_CLASS, interactiveClass)}>
        {body}
      </button>
    ) : (
      <span role="status" aria-label={label} title={link.href} className={cn(CHIP_CLASS, "border-primary/30 bg-primary/5 text-primary")}>
        {body}
      </span>
    );
  }

  const author = message.author;
  const authorName = author
    ? getDisplayName({
        firstName: author.firstName ?? undefined,
        lastName: author.lastName ?? undefined,
        email: author.email ?? undefined,
      }) || t("message.unknownMember")
    : t("message.unknownMember");
  const snippet = chatMessageSnippet(message, t("message.unknownMember"), SNIPPET_LENGTH) || t("reply.attachmentOnly");

  const body = (
    <>
      <MessageSquareText className="size-3.5 shrink-0" aria-hidden />
      <UserAvatar
        user={{
          firstName: author?.firstName ?? undefined,
          lastName: author?.lastName ?? undefined,
          email: author?.email ?? undefined,
        }}
        size="xs"
        name={authorName}
        className="max-w-[calc(100%-1.125rem)] shrink-0 gap-1"
        nameClassName="text-inherit text-[length:inherit] font-semibold"
      />
      {/* The name keeps its width and the snippet and thread share the rest. The name is
          capped at the chip less the icon and its gap, so a name wider than the chip
          truncates inside the chip's border. Such a name leaves the snippet and thread no
          room; they and their gaps are clipped (here and on the chip), so the thread's
          icon is never drawn past the border. */}
      <span className="text-foreground/80 min-w-0 truncate">{snippet}</span>
      {thread && (
        <span className="text-muted-foreground inline-flex min-w-0 shrink items-center gap-0.5 overflow-hidden">
          <Hash className="size-3 shrink-0" aria-hidden />
          <span className="truncate">{t("messageLink.inThread", { title: thread.title })}</span>
        </span>
      )}
    </>
  );

  return (
    <span className={LOADED_CHIP_FRAME_CLASS}>
      {jump ? (
        <button
          type="button"
          onClick={jump}
          aria-label={t("messageLink.jump", { name: authorName })}
          title={link.href}
          className={cn(CHIP_CLASS, "max-w-md overflow-hidden", interactiveClass)}
        >
          {body}
        </button>
      ) : (
        <span title={link.href} className={cn(CHIP_CLASS, "max-w-md overflow-hidden border-primary/30 bg-primary/5 text-primary")}>
          {body}
        </span>
      )}
    </span>
  );
}

/** The listed channel a link names, by ID, slug or purpose. */
function findLinkChannel(
  channels: readonly ChatChannelResponse[],
  target: ChatMessageLinkChannel,
): ChatChannelResponse | undefined {
  if ("id" in target) return channels.find((channel) => channel.id === target.id);
  if ("slug" in target) return channels.find((channel) => channel.slug === target.slug);
  return channels.find((channel) => channel.purpose === target.purpose);
}

/**
 * The chip of a message link in a sent message. A click shows the message
 * as the link would: in the open channel it shows a channel message in the
 * channel and a thread message in its thread's panel, without loading the
 * page again; a message of another channel opens that channel's URL with
 * the same message parameters. The channel is the one the preview names,
 * else the one the link names; a link whose channel is neither known nor
 * listed stays in the open channel.
 */
export function ChatMessageLinkChip({ link }: { link: ChatMessageLink }) {
  const router = useRouter();
  const [, setParams] = useChatParams();
  const openChannelId = useChatOpenChannelId();
  const { data: channels } = useChatChannels();
  const jump = (messageId: string, threadId: string | null, channelId: string | null) => {
    const listed = channels ?? [];
    const target =
      (channelId ? listed.find((channel) => channel.id === channelId) : undefined) ??
      (link.channel ? findLinkChannel(listed, link.channel) : undefined);
    if (target && target.id !== openChannelId) {
      const query = threadId
        ? new URLSearchParams({ thread: threadId, threadMessage: messageId })
        : new URLSearchParams({ jump: messageId });
      router.push(`${chatChannelPath(target)}?${query.toString()}`);
    } else if (threadId) {
      void setParams({ thread: threadId, threadMessage: messageId, jump: null });
    } else {
      void setParams({ jump: messageId });
    }
  };
  return <ChatMessageLinkPreview link={link} onJump={jump} />;
}
