import { Search, X } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, TextInput, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

const IS_ANDROID = Platform.OS === "android";

/**
 * iOS: a 44 pt field in the `input` outline. Android: a 48 dp Material search
 * pill on `secondary`, without an outline.
 */
const FIELD_CLASS = IS_ANDROID
  ? "mx-4 min-h-12 flex-row items-center gap-3 rounded-full bg-secondary px-4"
  : "mx-4 min-h-11 flex-row items-center gap-2 rounded-md border bg-background px-3";
const SEARCH_ICON_SIZE = IS_ANDROID ? 22 : 18;

type SearchFieldProps =
  | {
      /** The placeholder, also read by VoiceOver and TalkBack. */
      label: string;
      /** Opens a search screen instead of taking text here. */
      onPress: () => void;
    }
  | {
      label: string;
      value: string;
      onChangeText: (text: string) => void;
    };

/**
 * The search field under a tab root's title: a magnifier and a placeholder.
 * With `onPress` it is a button that opens a search screen (Chat); with
 * `value` it filters the screen it sits on (Boards), and a clear button
 * empties it (the system draws it on iOS).
 */
export function SearchField(props: SearchFieldProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const [focused, setFocused] = React.useState<boolean>(false);

  if ("onPress" in props) {
    return (
      <Pressable
        accessibilityRole="search"
        accessibilityLabel={props.label}
        onPress={props.onPress}
        className={cn(FIELD_CLASS, !IS_ANDROID && "border-input", "active:opacity-60")}
      >
        <Icon as={Search} size={SEARCH_ICON_SIZE} className="text-muted-foreground" />
        <Text className="font-sans text-[17px] text-muted-foreground">{props.label}</Text>
      </Pressable>
    );
  }

  return (
    <View className={cn(FIELD_CLASS, !IS_ANDROID && (focused ? "border-ring" : "border-input"))}>
      <Icon as={Search} size={SEARCH_ICON_SIZE} className="text-muted-foreground" />
      <TextInput
        value={props.value}
        onChangeText={props.onChangeText}
        placeholder={props.label}
        placeholderTextColor={colors.mutedForeground}
        accessibilityLabel={props.label}
        autoCapitalize="none"
        autoCorrect={false}
        returnKeyType="search"
        clearButtonMode="while-editing"
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        className="min-h-11 flex-1 font-sans text-[17px] text-foreground"
      />
      {IS_ANDROID && props.value.length > 0 ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={t("kitClearSearch")}
          hitSlop={10}
          onPress={() => props.onChangeText("")}
        >
          <Icon as={X} size={22} className="text-muted-foreground" />
        </Pressable>
      ) : null}
    </View>
  );
}
