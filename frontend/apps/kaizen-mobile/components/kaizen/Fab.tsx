import { Plus } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable } from "react-native";
import Animated, { FadeIn, FadeOut, LinearTransition } from "react-native-reanimated";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useAndroidToastLift } from "@/components/ui/toast";
import { useShadow } from "@/components/ui/use-shadow";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { cn } from "@/lib/utils";

/**
 * Bottom padding a list adds under its last row on Android, so the FAB never
 * covers it: the 56 dp FAB minus the 16 dp margin it already sits in, plus a gap.
 */
export const FAB_LIST_BOTTOM_PADDING = 48;

/** How far the Android snackbar rises over a screen with a FAB: the 56 dp button plus a 16 dp gap. */
const FAB_TOAST_LIFT = 72;

/** `primary` for a create action; `destructive` for an alarm such as Report incident. */
type FabTone = "primary" | "destructive";

/** A light ripple that stays visible on the red fill, where the blue one would not. */
const DESTRUCTIVE_RIPPLE = "rgba(255, 255, 255, 0.24)";

type FabProps = {
  /** Default: a plus. */
  icon?: LucideIcon;
  /** The action ("New ticket"); also the accessibility label when collapsed. */
  label: string;
  /** Shows the label next to the icon; collapse it while the list scrolls down. */
  extended?: boolean;
  onPress: () => void;
  /** Default `primary`. */
  tone?: FabTone;
  /** Placement; default bottom right, 16 dp from the edges. */
  className?: string;
};

/**
 * The Material floating action button of a screen's main create action.
 * Android only: iOS puts that action in the navigation bar, so this renders
 * nothing there.
 */
function Fab({
  icon = Plus,
  label,
  extended = false,
  onPress,
  tone = "primary",
  className,
}: FabProps) {
  const colors = useThemeColors();
  const destructive: boolean = tone === "destructive";
  // The blue glow belongs to the primary fill; a red button gets a neutral lift.
  const shadow = useShadow(destructive ? "card-hover" : "primary-glow");
  useAndroidToastLift(FAB_TOAST_LIFT);
  if (Platform.OS !== "android") return null;
  const foreground: string = destructive
    ? "text-destructive-foreground"
    : "text-primary-foreground";

  return (
    <Animated.View
      layout={LinearTransition.duration(200)}
      style={shadow}
      className={cn("absolute bottom-4 right-4 rounded-2xl", className)}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label}
        onPress={onPress}
        android_ripple={{
          color: destructive ? DESTRUCTIVE_RIPPLE : colors.primaryMedium,
          foreground: true,
        }}
        className={cn(
          "h-14 flex-row items-center justify-center gap-3 overflow-hidden rounded-2xl",
          destructive ? "bg-destructive-fill" : "bg-primary-fill",
          extended ? "pl-4 pr-5" : "w-14"
        )}
      >
        <Icon as={icon} size={24} className={foreground} />
        {extended ? (
          <Animated.View entering={FadeIn.duration(150)} exiting={FadeOut.duration(100)}>
            <Text
              numberOfLines={1}
              className={cn("font-sans-medium text-[14px] leading-5", foreground)}
            >
              {label}
            </Text>
          </Animated.View>
        ) : null}
      </Pressable>
    </Animated.View>
  );
}

export { Fab };
export type { FabProps, FabTone };
