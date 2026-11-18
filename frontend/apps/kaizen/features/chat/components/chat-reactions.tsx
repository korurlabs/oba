"use client";

import { memo, useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { SmilePlus } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { getDisplayName } from "@/components/ui/user-avatar";
import { useToggleChatReaction } from "../hooks/use-chat-mutations";
import { useChatMembers } from "../hooks/use-chat-messages";
import {
  CHAT_MAX_REACTIONS_PER_MESSAGE,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
} from "../schemas";
import { ChatEmojiPicker } from "./chat-emoji-picker";

/**
 * Toggles the reader's reaction with `emoji` on a message: removes it when
 * the reader has it, adds it otherwise, refusing a new emoji beyond the
 * per-message limit with a toast.
 */
export function useChatReact(): (message: ChatMessageResponse, emoji: string) => void {
  const t = useTranslations("chat.reactions");
  const { mutate } = useToggleChatReaction();
  return useCallback(
    (message: ChatMessageResponse, emoji: string) => {
      const existing = message.reactions.find((reaction) => reaction.emoji === emoji);
      if (!existing && message.reactions.length >= CHAT_MAX_REACTIONS_PER_MESSAGE) {
        toast.error(t("limitReached", { max: CHAT_MAX_REACTIONS_PER_MESSAGE }));
        return;
      }
      mutate({ messageId: message.id, emoji, add: !existing?.reactedByMe });
    },
    [mutate, t],
  );
}

interface ChatReactionsProps {
  message: ChatMessageResponse;
  /** False for a reader who may only read (GUEST): the row shows, but nothing toggles. */
  canReact: boolean;
}

/**
 * The reactions row under a message: each emoji with its count, the reader's
 * own highlighted; a click toggles the reader's reaction, and hovering lists
 * who reacted (the first 10 names, then "+N"). The trailing button adds a new
 * emoji, up to 20 distinct per message.
 */
export const ChatReactions = memo(function ChatReactions({ message, canReact }: ChatReactionsProps) {
  const t = useTranslations("chat.reactions");
  const { data: members } = useChatMembers();
  const { reactions } = message;

  const membersById = useMemo(() => {
    const map = new Map<string, ChatMemberResponse>();
    for (const member of members ?? []) map.set(member.userId, member);
    return map;
  }, [members]);

  const nameOf = useCallback(
    (userId: string): string => {
      const user = message.users[userId];
      const member = membersById.get(userId);
      const source = member ?? (user?.member ? user : undefined);
      if (!source) return t("unknownMember");
      return (
        getDisplayName({
          firstName: source.firstName ?? undefined,
          lastName: source.lastName ?? undefined,
          email: source.email ?? undefined,
        }) || t("unknownMember")
      );
    },
    [message.users, membersById, t],
  );

  const reactTo = useChatReact();
  const react = useCallback((emoji: string) => reactTo(message, emoji), [reactTo, message]);

  if (reactions.length === 0) return null;

  return (
    // Under the reader's own message the row sits on the right with it.
    <div className="flex flex-wrap items-center gap-1 group-data-[own]/row:justify-end" aria-label={t("label")}>
      <TooltipProvider delayDuration={300}>
        {reactions.map((reaction) => (
          <ReactionPill
            key={reaction.emoji}
            reaction={reaction}
            names={reaction.userIds.map(nameOf)}
            disabled={!canReact}
            onToggle={() => react(reaction.emoji)}
          />
        ))}
      </TooltipProvider>
      {canReact && reactions.length < CHAT_MAX_REACTIONS_PER_MESSAGE && (
        <ChatEmojiPicker onSelect={react} align="start">
          <button
            type="button"
            aria-label={t("add")}
            title={t("add")}
            className="border-border text-muted-foreground hover:text-foreground hover:bg-muted focus-visible:ring-ring inline-flex h-6 items-center rounded-full border px-1.5 focus-visible:ring-2 focus-visible:outline-none"
          >
            <SmilePlus className="size-3.5" aria-hidden />
          </button>
        </ChatEmojiPicker>
      )}
    </div>
  );
});

interface ReactionPillProps {
  reaction: ChatReactionSummaryResponse;
  /** Names of the first listed users; the server lists at most 10. */
  names: string[];
  disabled: boolean;
  onToggle: () => void;
}

function ReactionPill({ reaction, names, disabled, onToggle }: ReactionPillProps) {
  const t = useTranslations("chat.reactions");
  const others = Math.max(0, reaction.count - names.length);
  const who = others > 0 ? t("whoMore", { names: names.join(", "), count: others }) : names.join(", ");
  const label = reaction.reactedByMe
    ? t("toggleMine", { emoji: reaction.emoji, count: reaction.count })
    : t("toggleOther", { emoji: reaction.emoji, count: reaction.count });

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          onClick={onToggle}
          disabled={disabled}
          aria-pressed={reaction.reactedByMe}
          aria-label={label}
          className={cn(
            "focus-visible:ring-ring inline-flex h-6 items-center gap-1 rounded-full border px-2 text-xs tabular-nums transition-colors focus-visible:ring-2 focus-visible:outline-none disabled:cursor-default",
            reaction.reactedByMe
              ? "border-primary/50 bg-primary/10 text-primary"
              : "border-border bg-muted/40 text-foreground enabled:hover:bg-muted",
          )}
        >
          <span className="text-sm leading-none">{reaction.emoji}</span>
          <span className="font-medium">{reaction.count}</span>
        </button>
      </TooltipTrigger>
      <TooltipContent className="max-w-64">
        <p>{t("reactedWith", { names: who, emoji: reaction.emoji })}</p>
      </TooltipContent>
    </Tooltip>
  );
}
