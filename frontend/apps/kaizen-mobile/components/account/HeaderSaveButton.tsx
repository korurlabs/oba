import { Check } from "lucide-react-native";
import { ActivityIndicator, Platform, Pressable } from "react-native";

import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { cn } from "@/lib/utils";

type HeaderSaveButtonProps = {
  /** The visible word on iOS ("Save"). */
  label: string;
  /** The accessible name of the Android check icon ("Save profile"). */
  accessibilityLabel: string;
  disabled: boolean;
  saving: boolean;
  onPress: () => void;
};

/**
 * The save command of an edit screen's navigation bar: a bold text button on
 * iOS, a check icon on Android. A spinner replaces it while the save runs.
 */
export function HeaderSaveButton({
  label,
  accessibilityLabel,
  disabled,
  saving,
  onPress,
}: HeaderSaveButtonProps) {
  const colors = useThemeColors();
  if (saving) {
    return (
      <ActivityIndicator
        accessibilityLabel={accessibilityLabel}
        color={Platform.OS === "ios" ? colors.link : colors.foreground}
        className="px-2"
      />
    );
  }
  if (Platform.OS !== "ios") {
    return (
      <IconButton icon={Check} label={accessibilityLabel} disabled={disabled} onPress={onPress} />
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      hitSlop={8}
      className="px-2 active:opacity-60"
    >
      <Text variant="headline" className={cn("text-link", disabled && "opacity-50")}>
        {label}
      </Text>
    </Pressable>
  );
}
