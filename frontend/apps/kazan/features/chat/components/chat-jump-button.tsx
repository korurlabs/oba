"use client";

import { useTranslations } from "next-intl";
import { ArrowDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CHAT_UNREAD_CAP } from "../schemas";

interface ChatJumpButtonProps {
  /** Messages below the view the reader has not seen: unread ones and new arrivals. 0 offers "Jump to present". */
  newCount: number;
  onClick: () => void;
}

/**
 * The pill at the bottom of a message list that takes the reader to the
 * newest message, as in Discord and Teams. It names the unseen messages below
 * when there are any, so the reader knows why it is there.
 */
export function ChatJumpButton({ newCount, onClick }: ChatJumpButtonProps) {
  const t = useTranslations("chat.list");
  const text =
    newCount <= 0
      ? t("jumpToPresent")
      : newCount >= CHAT_UNREAD_CAP
        ? t("unreadBarCapped", { count: CHAT_UNREAD_CAP })
        : t("newMessages", { count: newCount });
  const label = newCount > 0 ? t("newMessagesJumpLabel", { messages: text }) : text;

  return (
    <Button
      type="button"
      size="sm"
      onClick={onClick}
      aria-label={label}
      className="absolute bottom-3 left-1/2 z-10 h-8 -translate-x-1/2 gap-1.5 shadow-(--kazan-grid-shadow)"
    >
      <ArrowDown className="h-3.5 w-3.5" aria-hidden />
      {text}
    </Button>
  );
}
