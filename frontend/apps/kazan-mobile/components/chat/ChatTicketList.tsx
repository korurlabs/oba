import * as React from "react";
import { ActivityIndicator, Pressable, ScrollView, View } from "react-native";

import type { ChatSuggestionListHandle } from "@/components/chat/ChatMentionList";
import { InlineError } from "@/components/kazan/EmptyState";
import { TicketTypeBadge } from "@/components/kazan/TicketMeta";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatTicketSuggestions } from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";
import type { ChatTicketSuggestion } from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

/** The list stays above the keyboard and the field; longer lists scroll. */
const LIST_MAX_HEIGHT = 232;

/** Typing pauses this long before the search runs, so a word is one request. */
export const CHAT_TICKET_SEARCH_DEBOUNCE_MS = 200;

type ChatTicketListProps = {
  /** The text typed after `#`. */
  query: string;
  onPick: (ticket: ChatTicketSuggestion) => void;
  ref?: React.Ref<ChatSuggestionListHandle>;
};

function useDebounced(value: string, delay: number): string {
  const [debounced, setDebounced] = React.useState<string>(value);
  React.useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function TicketRow({
  ticket,
  active,
  onPick,
}: {
  ticket: ChatTicketSuggestion;
  /** The first row, which Return picks. */
  active: boolean;
  onPick: (ticket: ChatTicketSuggestion) => void;
}) {
  const colors = useThemeColors();
  const subtitle: string | null = ticket.board?.name ?? null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={[ticket.key, ticket.title, subtitle].filter(Boolean).join(", ")}
      accessibilityState={{ selected: active }}
      android_ripple={{ color: colors.accent }}
      onPress={() => onPick(ticket)}
      className={cn(
        "min-h-12 flex-row items-center gap-3 px-4 py-1.5 active:bg-accent",
        active && "bg-accent"
      )}
    >
      <TicketTypeBadge type={ticket.type} size="sm" />
      <View className="flex-1">
        <Text variant="subhead" numberOfLines={1}>
          <Text variant="subhead" className="font-sans-semibold">
            {ticket.key}
          </Text>{" "}
          {ticket.title}
        </Text>
        {subtitle ? (
          <Text variant="caption" numberOfLines={1} className="text-muted-foreground">
            {subtitle}
          </Text>
        ) : null}
      </View>
    </Pressable>
  );
}

/**
 * The `#` suggestions above the composer: the organization's tickets whose
 * key or title holds the typed text, searched on the server. A picked
 * ticket shows as its key and is sent as a ticket link. The first is
 * highlighted, and Return in the field picks it once the typed text has
 * been searched.
 */
export function ChatTicketList({ query, onPick, ref }: ChatTicketListProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const search: string = useDebounced(query.trim(), CHAT_TICKET_SEARCH_DEBOUNCE_MS);
  const {
    data: tickets,
    isError,
    isPending,
    isPlaceholderData,
    refetch,
  } = useChatTicketSuggestions(search);
  // Results of an earlier search stay shown while the new one runs, but Return waits for it.
  const first: ChatTicketSuggestion | undefined =
    search === query.trim() && !isPlaceholderData ? tickets?.[0] : undefined;

  React.useImperativeHandle(
    ref,
    () => ({
      pickFirst: (): boolean => {
        if (!first) return false;
        onPick(first);
        return true;
      },
    }),
    [first, onPick]
  );

  const body = (() => {
    if (isError && !tickets) {
      return (
        <InlineError
          message={t("chatTicketsError")}
          onRetry={() => void refetch()}
          className="mx-4 my-2"
        />
      );
    }
    if (isPending || !tickets) {
      return <ActivityIndicator className="py-3" color={colors.mutedForeground} />;
    }
    if (tickets.length === 0) {
      return (
        <Text variant="footnote" className="px-4 py-3 text-muted-foreground">
          {t("chatTicketsEmpty")}
        </Text>
      );
    }
    return (
      <ScrollView keyboardShouldPersistTaps="always" style={{ maxHeight: LIST_MAX_HEIGHT }}>
        {tickets.map((ticket, index) => (
          <TicketRow key={ticket.id} ticket={ticket} active={index === 0} onPick={onPick} />
        ))}
      </ScrollView>
    );
  })();

  return (
    <View className="border-b border-border pb-1">
      <Text
        accessibilityRole="header"
        className="px-4 pb-1 pt-2 font-sans-semibold text-caption uppercase text-muted-foreground"
      >
        {t("chatTicketsTitle")}
      </Text>
      {body}
    </View>
  );
}
