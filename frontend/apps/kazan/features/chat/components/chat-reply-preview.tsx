"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { CornerUpLeft } from "lucide-react";
import type { AttachmentResponse } from "@oba/shared/features/attachments/schemas";
import { cn } from "@oba/ui/lib/utils";
import { UserAvatar, type UserAvatarUser } from "@/components/ui/user-avatar";
import { chatMessagePlainText, isChatEntryCard } from "../lib/chat-message-text";
import { chatUserDisplayName } from "../lib/render-chat-markdown";
import type { ChatMessageResponse, ChatReplyPreviewResponse, ChatUserResponse } from "../schemas";
import { isChatImageType } from "./chat-attachments";

/** The server's snippet length for a quoted message. */
const SNIPPET_LENGTH = 200;

/**
 * The quote of a loaded message, for a reply this tab has not sent yet: the
 * server builds the snippet of a sent reply, with its tokens as names. Like
 * the server, it carries no files for a deleted message or a card entry.
 */
export function chatReplyPreviewOf(
  message: ChatMessageResponse,
  unknownMember: string,
): ChatReplyPreviewResponse {
  // An attachment-only message has an empty snippet, as the server builds it.
  const text = chatMessagePlainText(message, unknownMember, { withFileNames: false });
  return {
    messageId: message.id,
    deleted: message.deleted,
    authorId: message.author?.userId ?? null,
    snippet: text.slice(0, SNIPPET_LENGTH),
    attachments: message.deleted || isChatEntryCard(message) ? [] : message.attachments,
  };
}

/**
 * The line a quote shows: the quoted text, or for a message without text
 * "Photo" when its first file is an image and "Attachment" otherwise.
 */
export function chatQuoteText(
  text: string,
  attachments: readonly AttachmentResponse[],
  labels: { photo: string; attachment: string },
): string {
  if (text) return text;
  return isChatImageType(attachments[0]?.contentType) ? labels.photo : labels.attachment;
}

/** The first image of a message, which a quote shows as its thumbnail. */
export function chatQuoteImage(attachments: readonly AttachmentResponse[]): AttachmentResponse | null {
  return attachments.find((attachment) => isChatImageType(attachment.contentType)) ?? null;
}

/** The frame every quote shares: an accent bar on the left over a muted background. */
const QUOTE_FRAME =
  "border-primary bg-muted/50 flex min-w-0 items-center gap-2 rounded-none border-l-2 px-2 py-1 text-xs";

interface ChatQuoteProps {
  /**
   * The quoted author, shown as avatar and name on the first row; left out
   * where a line above the quote already names the author.
   */
  author?: { user: UserAvatarUser; name: string };
  /** Shown on at most two lines, cut with an ellipsis. */
  text: string;
  /** The quoted message's first image; null shows no thumbnail. */
  image: AttachmentResponse | null;
  className?: string;
}

/**
 * A quoted message as the reply bar and the line above a reply show it: the
 * author's avatar and name as one unit when given, the text on at most two
 * lines, and a small thumbnail of its first image. Built from spans, so it may
 * sit inside a button.
 */
export function ChatQuote({ author, text, image, className }: ChatQuoteProps) {
  // A signed URL that failed to load hides the thumbnail; a fresh URL tries again.
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = image !== null && image.url !== failedUrl;

  return (
    <span className={cn(QUOTE_FRAME, className)}>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        {author && (
          <UserAvatar
            user={author.user}
            size="xs"
            name={author.name}
            nameClassName="text-primary font-semibold"
          />
        )}
        <span className="text-muted-foreground line-clamp-2 break-words">{text}</span>
      </span>
      {showImage && (
        // eslint-disable-next-line @next/next/no-img-element -- reason: short-lived signed storage URLs, not routed through the Next image optimizer, as in the chat attachments
        <img
          src={image.url}
          alt={image.fileName}
          loading="lazy"
          onError={() => setFailedUrl(image.url)}
          className="size-10 shrink-0 rounded object-cover"
        />
      )}
    </span>
  );
}

interface ChatReplyPreviewProps {
  replyTo: ChatReplyPreviewResponse;
  /** Users of the replying message; the quoted author is among them. */
  users: Record<string, ChatUserResponse>;
  /** Scrolls to the quoted message, loading the messages around it when needed. */
  onJump?: (messageId: string) => void;
  className?: string;
}

/** The arrow before the quote above a reply, which marks the block as a reply. */
const REPLY_ARROW = <CornerUpLeft className="text-muted-foreground h-3.5 w-3.5 shrink-0" aria-hidden />;

/**
 * The quote above a reply: an arrow before the quote block, so it reads as a
 * reply and not as a message of its own, and the quoted author's avatar inside
 * the block beside the name. Clicking it goes to the original. A deleted
 * original shows only the arrow and that it was deleted.
 */
export function ChatReplyPreview({ replyTo, users, onJump, className }: ChatReplyPreviewProps) {
  const t = useTranslations("chat");

  if (replyTo.deleted) {
    return (
      <div className={cn("flex max-w-md min-w-0 items-center gap-1.5", className)}>
        {REPLY_ARROW}
        <span className={cn(QUOTE_FRAME, "text-muted-foreground italic")}>{t("reply.deleted")}</span>
      </div>
    );
  }

  const author = replyTo.authorId ? users[replyTo.authorId] : undefined;
  const authorName = chatUserDisplayName(author, t("message.unknownMember"));
  // REST message pages are not parsed with Zod, so the schema's empty default
  // does not reach them; an API that predates quoted files omits the field.
  const attachments = replyTo.attachments ?? [];
  const text = chatQuoteText(replyTo.snippet, attachments, {
    photo: t("reply.photo"),
    attachment: t("reply.attachmentOnly"),
  });

  // A button sizes to its content, and a truncated name's narrowest width is the whole
  // name, so the quote fills its box instead: on a narrow screen it ends at the column
  // and the name truncates. The cap is a fixed width, not a percentage, because only a
  // fixed cap limits the box's own content-based width, which keeps a short quote narrow.
  return (
    <button
      type="button"
      onClick={onJump ? () => onJump(replyTo.messageId) : undefined}
      disabled={!onJump}
      aria-label={t("reply.jump", { name: authorName })}
      className={cn(
        "focus-visible:ring-ring flex w-full max-w-md min-w-0 items-center gap-1.5 rounded-md text-left hover:opacity-80 focus-visible:ring-2 focus-visible:outline-none disabled:cursor-default disabled:hover:opacity-100",
        className,
      )}
    >
      {REPLY_ARROW}
      <ChatQuote
        author={{
          user: {
            firstName: author?.firstName ?? undefined,
            lastName: author?.lastName ?? undefined,
            email: author?.email ?? undefined,
          },
          name: authorName,
        }}
        text={text}
        image={chatQuoteImage(attachments)}
      />
    </button>
  );
}
