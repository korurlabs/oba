import { AtSign } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Pressable, ScrollView, View } from "react-native";

import {
  chatMentionPickable,
  chatMentionSuggestions,
  type ChatMentionSuggestion,
} from "@/components/chat/chatMentions";
import { InlineError } from "@/components/kaizen/EmptyState";
import { Avatar } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { CHAT_MENTION_EVERYONE, type ChatMemberResponse } from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

/** The list stays above the keyboard and the field; longer lists scroll. */
const LIST_MAX_HEIGHT = 232;

/** What the composer asks of an open suggestion list. */
export interface ChatSuggestionListHandle {
  /** Picks the highlighted first suggestion; false when there is none to pick yet. */
  pickFirst: () => boolean;
}

type ChatMentionListProps = {
  /** The text typed after `@`. */
  query: string;
  /** Who can be mentioned; undefined while loading. */
  members: readonly ChatMemberResponse[] | undefined;
  error: boolean;
  onRetry: () => void;
  onPick: (suggestion: ChatMentionSuggestion) => void;
  ref?: React.Ref<ChatSuggestionListHandle>;
};

function SuggestionRow({
  suggestion,
  active,
  onPick,
}: {
  suggestion: ChatMentionSuggestion;
  /** The first pickable row, which Return picks. */
  active: boolean;
  onPick: (suggestion: ChatMentionSuggestion) => void;
}) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const member: boolean = suggestion.kind === "member";
  const disabled: boolean = !chatMentionPickable(suggestion);
  const title: string = suggestion.kind === "member" ? suggestion.name : `@${suggestion.kind}`;
  // A member who cannot be mentioned says why in place of the email.
  const subtitle: string | null =
    suggestion.kind === "member"
      ? disabled
        ? t("memberNoKaizenAccess")
        : suggestion.member.email
      : t(suggestion.kind === CHAT_MENTION_EVERYONE ? "chatMentionEveryone" : "chatMentionHere");
  const online: boolean = suggestion.kind === "member" && suggestion.member.online;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={[title, subtitle, online ? t("chatOnline") : null]
        .filter(Boolean)
        .join(", ")}
      accessibilityState={{ selected: active, disabled }}
      android_ripple={disabled ? undefined : { color: colors.accent }}
      disabled={disabled}
      onPress={() => onPick(suggestion)}
      className={cn(
        "min-h-12 flex-row items-center gap-3 px-4 py-1.5",
        !disabled && "active:bg-accent",
        active && "bg-accent"
      )}
    >
      {member ? (
        <Avatar name={title} size={28} online={online} />
      ) : (
        <View className="size-7 items-center justify-center rounded-full bg-secondary">
          <Icon as={AtSign} size={16} className="text-muted-foreground" />
        </View>
      )}
      <View className="flex-1">
        <Text variant="subhead" numberOfLines={1} className={cn(disabled && "opacity-50")}>
          {title}
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
 * The `@` suggestions above the composer: matching members with email and
 * online dot, then `@everyone` ("Notify every member") and `@here`
 * ("Notify the members who are online"). A member without Kaizen is listed
 * but cannot be picked, because the server refuses that mention. The first
 * pickable suggestion is highlighted, and Return in the field picks it.
 */
export function ChatMentionList({
  query,
  members,
  error,
  onRetry,
  onPick,
  ref,
}: ChatMentionListProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const suggestions: ChatMentionSuggestion[] = React.useMemo(
    () => chatMentionSuggestions(members ?? [], query),
    [members, query]
  );
  // Nothing is shown to pick while the members load.
  const first: ChatMentionSuggestion | undefined = members
    ? suggestions.find(chatMentionPickable)
    : undefined;

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
    if (error && !members) {
      return (
        <InlineError message={t("chatMentionsError")} onRetry={onRetry} className="mx-4 my-2" />
      );
    }
    if (!members) {
      return <ActivityIndicator className="py-3" color={colors.mutedForeground} />;
    }
    if (suggestions.length === 0) {
      return (
        <Text variant="footnote" className="px-4 py-3 text-muted-foreground">
          {t("chatMentionsEmpty")}
        </Text>
      );
    }
    return (
      <ScrollView keyboardShouldPersistTaps="always" style={{ maxHeight: LIST_MAX_HEIGHT }}>
        {suggestions.map((suggestion) => (
          <SuggestionRow
            key={suggestion.key}
            suggestion={suggestion}
            active={suggestion === first}
            onPick={onPick}
          />
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
        {t("chatMentionsTitle")}
      </Text>
      {body}
    </View>
  );
}
