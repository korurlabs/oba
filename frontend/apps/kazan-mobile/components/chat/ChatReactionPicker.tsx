import { SmilePlus } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";
import EmojiPicker, { type EmojiType } from "rn-emoji-keyboard";

import { CHAT_QUICK_REACTIONS } from "@/components/chat/chatMessageActions";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type ChatReactionPickerProps = {
  /** The emoji the reader already reacted with; they are marked and tapping removes them. */
  mine: ReadonlySet<string>;
  onPick: (emoji: string) => void;
  /** Opens the full picker. */
  onMore: () => void;
};

/** The quick reaction row of the message actions: six emoji and "More emoji". */
export function ChatReactionPicker({ mine, onPick, onMore }: ChatReactionPickerProps) {
  const { t } = useTranslation();
  return (
    <View className="flex-row items-center justify-between px-4">
      {CHAT_QUICK_REACTIONS.map((emoji) => {
        const selected: boolean = mine.has(emoji);
        return (
          <Pressable
            key={emoji}
            accessibilityRole="button"
            accessibilityLabel={emoji}
            accessibilityState={{ selected }}
            onPress={() => onPick(emoji)}
            className={cn(
              "size-11 items-center justify-center rounded-full active:opacity-60",
              selected ? "border border-primary bg-primary-light" : "bg-card"
            )}
          >
            <Text className="text-title-3">{emoji}</Text>
          </Pressable>
        );
      })}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t("chatMoreEmoji")}
        onPress={onMore}
        className="size-11 items-center justify-center rounded-full bg-card active:opacity-60"
      >
        <Icon as={SmilePlus} size={22} className="text-muted-foreground" />
      </Pressable>
    </View>
  );
}

type ChatEmojiPickerProps = {
  open: boolean;
  onClose: () => void;
  onPick: (emoji: string) => void;
};

/** The labels the full picker shows: its category names and the search field. */
type ChatEmojiTranslation = NonNullable<React.ComponentProps<typeof EmojiPicker>["translation"]>;

/**
 * The full emoji picker, with search and recently used emoji, in the app's
 * colours and language. The picker ships no Dutch labels, so all of them
 * come from the app's translations.
 */
export function ChatEmojiPicker({ open, onClose, onPick }: ChatEmojiPickerProps) {
  const colors = useThemeColors();
  const { t } = useTranslation();
  const translation: ChatEmojiTranslation = React.useMemo(
    () => ({
      recently_used: t("chatEmojiRecentlyUsed"),
      smileys_emotion: t("chatEmojiSmileys"),
      people_body: t("chatEmojiPeople"),
      animals_nature: t("chatEmojiAnimals"),
      food_drink: t("chatEmojiFood"),
      travel_places: t("chatEmojiTravel"),
      activities: t("chatEmojiActivities"),
      objects: t("chatEmojiObjects"),
      symbols: t("chatEmojiSymbols"),
      flags: t("chatEmojiFlags"),
      search: t("chatEmojiSearch"),
    }),
    [t]
  );
  const theme = React.useMemo(
    () => ({
      backdrop: colors.scrim,
      knob: colors.grabber,
      container: colors.cardBackground,
      header: colors.mutedForeground,
      skinTonesContainer: colors.card,
      category: {
        icon: colors.mutedForeground,
        iconActive: colors.primaryFill,
        container: colors.card,
        containerActive: colors.primaryLight,
      },
      search: {
        background: colors.card,
        text: colors.foreground,
        placeholder: colors.mutedForeground,
        icon: colors.mutedForeground,
      },
      emoji: { selected: colors.primaryLight },
    }),
    [colors]
  );
  return (
    <EmojiPicker
      open={open}
      onClose={onClose}
      onEmojiSelected={(emoji: EmojiType) => onPick(emoji.emoji)}
      enableSearchBar
      enableRecentlyUsed
      categoryPosition="top"
      translation={translation}
      theme={theme}
    />
  );
}
