"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Loader2, Pin } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useChatOpenChannelId } from "../hooks/use-chat-channels";
import { useChatPins } from "../hooks/use-chat-messages";
import type { ChatMessageResponse } from "../schemas";
import { ChatFoundMessage, ChatLoadMore } from "./chat-search-panel";

interface ChatPinsPopoverProps {
  /** Shows the message: in the channel around it, or in its thread's panel. */
  onJump: (message: ChatMessageResponse) => void;
}

/**
 * The "Pinned" control of the channel header: the pinned messages of the
 * channel and its threads, most recently pinned first, each with Jump. The
 * list is fetched only while the popover is open; a pin or unpin anywhere
 * refreshes it through the PIN_CHANGED signal.
 */
export function ChatPinsPopover({ onJump }: ChatPinsPopoverProps) {
  const t = useTranslations("chat.pins");
  const [open, setOpen] = useState(false);

  const jump = (message: ChatMessageResponse) => {
    setOpen(false);
    onJump(message);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="border-border h-8 gap-1.5 text-xs"
          aria-label={t("button")}
        >
          <Pin className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">{t("button")}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="flex w-[min(26rem,calc(100vw-2rem))] flex-col gap-2 p-3"
      >
        <h2 className="text-foreground text-sm font-semibold">{t("title")}</h2>
        <PinList enabled={open} onJump={jump} />
      </PopoverContent>
    </Popover>
  );
}

function PinList({
  enabled,
  onJump,
}: {
  enabled: boolean;
  onJump: (message: ChatMessageResponse) => void;
}) {
  const t = useTranslations("chat.pins");
  const channelId = useChatOpenChannelId();
  const {
    data,
    error,
    isPending,
    isError,
    isFetching,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
  } = useChatPins(channelId, { enabled });

  if (isPending) {
    return (
      <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-xs">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t("loading")}
      </div>
    );
  }

  if (isError && !data) {
    return (
      <div role="alert" className="flex flex-col items-center gap-2 py-6 text-center">
        <AlertCircle className="text-destructive h-5 w-5" />
        <p className="text-foreground text-xs font-medium">{t("loadError")}</p>
        {error?.message && <p className="text-muted-foreground text-xs">{error.message}</p>}
        <Button
          variant="outline"
          size="sm"
          className="h-7"
          disabled={isFetching}
          onClick={() => void refetch()}
        >
          {t("retry")}
        </Button>
      </div>
    );
  }

  const messages = data.pages.flatMap((page) => page.items);
  if (messages.length === 0) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-8 text-center text-xs">
        <Pin className="h-5 w-5" />
        <p className="text-foreground font-medium">{t("emptyTitle")}</p>
        <p>{t("emptyDescription")}</p>
      </div>
    );
  }

  return (
    <div className="flex max-h-[60vh] flex-col gap-1 overflow-y-auto">
      <ul className="flex flex-col" aria-label={t("title")}>
        {messages.map((message) => (
          <li key={message.id}>
            <ChatFoundMessage message={message} onJump={onJump} />
          </li>
        ))}
      </ul>
      <ChatLoadMore
        hasNextPage={hasNextPage}
        isFetchingNextPage={isFetchingNextPage}
        isFetchNextPageError={isFetchNextPageError}
        fetchNextPage={fetchNextPage}
        labels={{ loadMore: t("loadMore"), loadMoreError: t("loadMoreError"), retry: t("retry") }}
      />
    </div>
  );
}
