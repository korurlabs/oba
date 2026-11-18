"use client";

import { useEffect, useImperativeHandle, useMemo, useState, type Ref } from "react";
import { useTranslations } from "next-intl";
import { Megaphone, Radio } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { getDisplayName, UserAvatar } from "@/components/ui/user-avatar";
import { hasKazanAccess } from "@/lib/kazan-member-access";
import { useChatChannels, useChatReadableChannelId } from "../hooks/use-chat-channels";
import { useChatMembers } from "../hooks/use-chat-messages";
import {
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  ChatChannelVisibility,
  type ChatMemberResponse,
} from "../schemas";

/** At most this many members are offered at once; typing narrows the list. */
const MAX_MEMBER_SUGGESTIONS = 8;

/** What the composer's suggestion handler forwards to an open suggestion list. */
export interface ChatSuggestionListHandle {
  /** Handles Up, Down, Enter and Tab; returns true when the key was used. */
  onKeyDown: (event: KeyboardEvent) => boolean;
}

/** The attributes of the chip the editor inserts for a choice. */
export interface ChatSuggestionChoice {
  id: string;
  label: string;
}

type MentionItem =
  | { kind: "member"; key: string; member: ChatMemberResponse; name: string; canMention: boolean }
  | { kind: typeof CHAT_MENTION_EVERYONE | typeof CHAT_MENTION_HERE; key: string };

function memberName(member: ChatMemberResponse): string {
  return getDisplayName({
    firstName: member.firstName ?? undefined,
    lastName: member.lastName ?? undefined,
    email: member.email ?? undefined,
  });
}

/**
 * Moves the active option with Up and Down (wrapping) and picks it with Enter
 * or Tab; shared by the member and the ticket list.
 */
export function useSuggestionKeys(
  ref: Ref<ChatSuggestionListHandle> | undefined,
  count: number,
  pick: (index: number) => void,
) {
  const [activeIndex, setActiveIndex] = useState(0);

  // A new result list starts at its first option.
  useEffect(() => {
    setActiveIndex(0);
  }, [count]);

  useImperativeHandle(
    ref,
    () => ({
      onKeyDown: (event: KeyboardEvent) => {
        if (count === 0) return false;
        if (event.key === "ArrowDown") {
          setActiveIndex((index) => (index + 1) % count);
          return true;
        }
        if (event.key === "ArrowUp") {
          setActiveIndex((index) => (index - 1 + count) % count);
          return true;
        }
        if (event.key === "Enter" || event.key === "Tab") {
          pick(Math.min(activeIndex, count - 1));
          return true;
        }
        return false;
      },
    }),
    [count, pick, activeIndex],
  );

  return { activeIndex, setActiveIndex };
}

interface ChatMentionListProps {
  /** The text typed after `@`. */
  query: string;
  onSelect: (choice: ChatSuggestionChoice) => void;
  ref?: Ref<ChatSuggestionListHandle>;
}

/**
 * The `@` suggestion: the members who can be mentioned in the open channel
 * (avatar, name, email, online dot) filtered by name or email, then
 * `@everyone` and `@here`. In a public channel those are the organization's
 * active members; in a private channel only the channel's members, because
 * the server refuses a mention of anyone else with 422 `KAZ_VAL_044`. A
 * member without Kazan access is listed but cannot be picked, because the
 * server refuses that mention too.
 */
export function ChatMentionList({ query, onSelect, ref }: ChatMentionListProps) {
  const t = useTranslations("chat.mentions");
  const tAccess = useTranslations("kazan.memberAccess");
  const openChannelId = useChatReadableChannelId();
  const { data: channels, isError: channelsError, refetch: refetchChannels } = useChatChannels();
  const channel = channels?.find((candidate) => candidate.id === openChannelId);
  // Until the open channel is known, offering the organization's members could offer someone the channel refuses.
  const channelKnown = !!channel;
  const {
    data: members,
    isLoading: membersLoading,
    isError: membersError,
    refetch: refetchMembers,
  } = useChatMembers({
    enabled: channelKnown,
    channelId: channel?.visibility === ChatChannelVisibility.PRIVATE ? channel.id : null,
  });
  const isError = membersError || channelsError;
  const isLoading = !isError && (membersLoading || !channelKnown);
  const refetch = () => (channelsError ? refetchChannels() : refetchMembers());

  const items = useMemo((): MentionItem[] => {
    const text = query.trim().toLowerCase();
    const matching = (members ?? [])
      .map((member) => ({ member, name: memberName(member) }))
      .filter(
        ({ member, name }) =>
          !text || name.toLowerCase().includes(text) || (member.email ?? "").toLowerCase().includes(text),
      )
      .slice(0, MAX_MEMBER_SUGGESTIONS)
      .map(
        ({ member, name }): MentionItem => ({
          kind: "member",
          key: member.userId,
          member,
          name,
          canMention: hasKazanAccess(member),
        }),
      );
    const groups = [CHAT_MENTION_EVERYONE, CHAT_MENTION_HERE] as const;
    for (const group of groups) {
      if (group.startsWith(text)) matching.push({ kind: group, key: group });
    }
    return matching;
  }, [members, query]);

  const pick = (index: number) => {
    const item = items[index];
    if (!item) return;
    if (item.kind === "member" && !item.canMention) return;
    if (item.kind === "member") onSelect({ id: item.member.userId, label: item.name });
    else onSelect({ id: item.kind, label: item.kind });
  };

  const { activeIndex, setActiveIndex } = useSuggestionKeys(ref, items.length, pick);

  return (
    <div className="flex flex-col py-1">
      <p className="text-muted-foreground px-3 pt-1 pb-1.5 text-[11px] font-semibold tracking-wide uppercase">
        {t("title")}
      </p>
      {isLoading && <p className="text-muted-foreground px-3 py-2 text-sm">{t("loading")}</p>}
      {isError && (
        <p role="alert" className="text-destructive px-3 py-2 text-sm">
          {t("loadError")}{" "}
          <button type="button" className="underline" onClick={() => void refetch()}>
            {t("retry")}
          </button>
        </p>
      )}
      {!isLoading && items.length === 0 && (
        <p className="text-muted-foreground px-3 py-2 text-sm">{t("empty")}</p>
      )}
      <ul role="listbox" aria-label={t("title")} className="flex flex-col">
        {items.map((item, index) => (
          <li
            key={item.key}
            role="option"
            aria-selected={index === activeIndex}
            aria-disabled={item.kind === "member" && !item.canMention ? true : undefined}
            title={item.kind === "member" && !item.canMention ? tAccess("noAccessHint") : undefined}
            onMouseEnter={() => setActiveIndex(index)}
            // Keeps the editor focused, so the choice lands at the caret.
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => pick(index)}
            className={cn(
              "mx-1 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm",
              index === activeIndex && "bg-accent text-accent-foreground",
              item.kind === "member" && !item.canMention && "cursor-not-allowed opacity-60",
            )}
          >
            {item.kind === "member" ? (
              <>
                <span className="relative inline-flex">
                  <UserAvatar
                    user={{
                      firstName: item.member.firstName ?? undefined,
                      lastName: item.member.lastName ?? undefined,
                      email: item.member.email ?? undefined,
                    }}
                    size="sm"
                    showName={false}
                  />
                  <span
                    className={cn(
                      "ring-popover absolute -right-0.5 -bottom-0.5 size-2.5 rounded-full ring-2",
                      item.member.online ? "bg-emerald-500" : "bg-muted-foreground/40",
                    )}
                    aria-hidden
                  />
                </span>
                <span className="text-foreground truncate font-medium">{item.name}</span>
                {item.member.email && item.member.email !== item.name && (
                  <span className="text-muted-foreground truncate text-xs">{item.member.email}</span>
                )}
                {!item.canMention && (
                  <span className="text-muted-foreground shrink-0 text-xs">({tAccess("noAccess")})</span>
                )}
                <span className="sr-only">{item.member.online ? t("online") : t("offline")}</span>
              </>
            ) : (
              <>
                <span className="bg-muted text-muted-foreground inline-flex size-6 items-center justify-center rounded-full">
                  {item.kind === CHAT_MENTION_EVERYONE ? (
                    <Megaphone className="size-3.5" aria-hidden />
                  ) : (
                    <Radio className="size-3.5" aria-hidden />
                  )}
                </span>
                <span className="text-foreground font-medium">@{item.kind}</span>
                <span className="text-muted-foreground truncate text-xs">
                  {item.kind === CHAT_MENTION_EVERYONE ? t("everyoneHint") : t("hereHint")}
                </span>
              </>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
