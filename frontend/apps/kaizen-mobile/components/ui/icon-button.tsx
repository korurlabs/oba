import { cva } from "class-variance-authority";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View, type PressableProps } from "react-native";

import { formatBadgeCount } from "@/components/ui/badge-count";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { cn } from "@/lib/utils";

/*
 * An icon-only action for nav bars, card corners and toolbars. The touch box is
 * the platform minimum (44 pt iOS, 48 dp Android); tonal and filled draw a
 * smaller circle inside it.
 */
const iconButtonVariants = cva("items-center justify-center rounded-full", {
  variants: {
    variant: {
      plain: "",
      tonal: "bg-primary-light",
      filled: "bg-primary-fill",
    },
  },
  defaultVariants: { variant: "plain" },
});

const ICON_CLASS = {
  // iOS tints bar icons blue like text buttons; Android keeps them neutral.
  plain: Platform.select({ ios: "text-link", default: "text-foreground" }),
  tonal: "text-link",
  filled: "text-primary-foreground",
} as const;

type IconButtonVariant = keyof typeof ICON_CLASS;

type IconButtonProps = Omit<PressableProps, "children" | "style"> & {
  icon: LucideIcon;
  /** Read by VoiceOver and TalkBack; required, the icon has no text. */
  label: string;
  variant?: IconButtonVariant;
  /** A count on the corner; hidden at 0, capped at 99+. */
  badge?: number | null;
  /** `destructive` for notifications, `primary` for a state count such as active filters. */
  badgeTone?: "destructive" | "primary";
  /** Overrides the icon colour class. */
  iconClassName?: string;
  className?: string;
};

const BOX = Platform.select({ ios: "size-11", default: "size-12" });
const CIRCLE = Platform.select({ ios: "size-9", default: "size-10" });

function IconButton({
  icon,
  label,
  variant = "plain",
  badge,
  badgeTone = "destructive",
  iconClassName,
  disabled,
  className,
  ...props
}: IconButtonProps) {
  const colors = useThemeColors();
  const badgeText = formatBadgeCount(badge);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityValue={badgeText ? { text: badgeText } : undefined}
      accessibilityState={{ disabled: !!disabled }}
      disabled={disabled}
      android_ripple={{ color: colors.accent, borderless: true, radius: 24 }}
      className={cn(
        "items-center justify-center",
        BOX,
        Platform.select({ ios: "active:opacity-60" }),
        disabled && "opacity-50",
        className
      )}
      {...props}
    >
      <View className={cn(iconButtonVariants({ variant }), variant === "plain" ? BOX : CIRCLE)}>
        <Icon as={icon} size={22} className={cn(ICON_CLASS[variant], iconClassName)} />
      </View>
      {badgeText ? (
        <View
          pointerEvents="none"
          className={cn(
            "absolute right-0.5 top-0.5 min-h-[18px] min-w-[18px] items-center justify-center rounded-full px-1",
            badgeTone === "primary" ? "bg-primary-fill" : "bg-destructive-fill"
          )}
        >
          <Text
            className={cn(
              "font-sans-semibold text-caption-2",
              badgeTone === "primary" ? "text-primary-foreground" : "text-destructive-foreground"
            )}
          >
            {badgeText}
          </Text>
        </View>
      ) : null}
    </Pressable>
  );
}

export { IconButton };
export type { IconButtonProps, IconButtonVariant };
