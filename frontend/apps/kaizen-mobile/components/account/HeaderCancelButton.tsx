import { X } from "lucide-react-native";
import { Platform, Pressable } from "react-native";

import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";

type HeaderCancelButtonProps = {
  /** The visible word on iOS and the accessible name of the Android close icon. */
  label: string;
  onPress: () => void;
};

/**
 * The cancel command of a full-screen form's navigation bar, opposite its
 * save command: a regular text button on iOS, a close icon on Android.
 */
export function HeaderCancelButton({ label, onPress }: HeaderCancelButtonProps) {
  if (Platform.OS !== "ios") {
    return <IconButton icon={X} label={label} onPress={onPress} />;
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      hitSlop={8}
      className="px-2 active:opacity-60"
    >
      <Text variant="body" className="text-link">
        {label}
      </Text>
    </Pressable>
  );
}
