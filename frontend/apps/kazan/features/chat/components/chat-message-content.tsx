"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@oba/ui/lib/utils";
import type { ChatMessageLink } from "../lib/chat-message-link";
import { renderChatMarkdown, type ChatMarkdownContext } from "../lib/render-chat-markdown";
import { ChatMessageLinkChip } from "./chat-message-link-chip";

function renderMessageLink(link: ChatMessageLink, key: number) {
  return <ChatMessageLinkChip key={key} link={link} />;
}

interface ChatMessageContentProps {
  /** Markdown with tokens, as stored. */
  content: string;
  users: ChatMarkdownContext["users"];
  ticketRefs: ChatMarkdownContext["ticketRefs"];
  linkedTickets: ChatMarkdownContext["linkedTickets"];
  meId: string | undefined;
  mentionsMe: boolean;
  className?: string;
}

/** The rendered text of a message; nothing for an attachment-only message. */
export function ChatMessageContent({
  content,
  users,
  ticketRefs,
  linkedTickets,
  meId,
  mentionsMe,
  className,
}: ChatMessageContentProps) {
  const t = useTranslations("chat.message");
  const unknownMember = t("unknownMember");
  const unknownTicket = t("unknownTicket");

  const rendered = useMemo(
    () =>
      renderChatMarkdown(content, {
        users,
        ticketRefs,
        linkedTickets,
        meId,
        mentionsMe,
        labels: { unknownMember, unknownTicket },
        renderMessageLink,
      }),
    [content, users, ticketRefs, linkedTickets, meId, mentionsMe, unknownMember, unknownTicket],
  );

  if (content.trim().length === 0) return null;

  return (
    <div className={cn("text-foreground min-w-0 space-y-1 text-sm leading-relaxed", className)}>{rendered}</div>
  );
}
