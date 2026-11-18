import { cva, type VariantProps } from "class-variance-authority";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Platform, Pressable, type PressableProps } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text, TextClassContext } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { cn } from "@/lib/utils";

/*
 * The one tappable command. iOS draws a rounded rectangle at the 44 pt touch
 * height; Android a Material 3 pill, 40 dp tall inside a 48 dp touch box (the
 * hit slop makes up the difference). `sm` is 32 tall on both, for actions
 * inside content, never the main action of a screen. The heights are
 * minimums: a larger system text size makes the button taller.
 */
const buttonVariants = cva("flex-row items-center justify-center gap-2", {
  variants: {
    variant: {
      // The fill is primary-fill, not primary: the label passes AA on it.
      primary: "bg-primary-fill",
      secondary: "bg-secondary",
      outline: "border border-border bg-transparent",
      ghost: "bg-transparent",
      destructive: "bg-destructive-fill",
    },
    size: {
      md: Platform.select({
        ios: "min-h-11 rounded-lg px-4 py-2.5",
        default: "min-h-10 rounded-full px-6 py-2",
      }),
      sm: Platform.select({
        ios: "min-h-8 rounded-md px-3 py-1.5",
        default: "min-h-8 rounded-full px-4 py-1.5",
      }),
    },
    block: {
      true: "w-full",
      false: "",
    },
  },
  defaultVariants: {
    variant: "primary",
    size: "md",
    block: false,
  },
});

const buttonTextVariants = cva("", {
  variants: {
    variant: {
      primary: "text-primary-foreground",
      secondary: "text-foreground",
      outline: "text-foreground",
      ghost: "text-link",
      destructive: "text-destructive-foreground",
    },
    size: {
      // iOS 15 semibold, Android 14 medium; `sm` is 13 on both.
      md: Platform.select({
        ios: "font-sans-semibold text-button",
        default: "font-sans-medium text-[14px] leading-5",
      }),
      sm: Platform.select({
        ios: "font-sans-semibold text-[13px] leading-[18px]",
        default: "font-sans-medium text-[13px] leading-[18px]",
      }),
    },
  },
  defaultVariants: {
    variant: "primary",
    size: "md",
  },
});

/** Extra touch area that brings each size to the platform minimum. */
const HIT_SLOP = {
  md: Platform.select({ ios: undefined, default: { top: 4, bottom: 4 } }),
  sm: Platform.select({ ios: { top: 6, bottom: 6 }, default: { top: 8, bottom: 8 } }),
} as const;

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

type ButtonProps = Omit<PressableProps, "children" | "style"> & {
  /** A verb in sentence case, at most three words, or custom content. */
  children: React.ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Full width of the container. In a row of equal buttons pass `className="flex-1"` instead. */
  block?: boolean;
  /** A leading lucide icon. */
  icon?: LucideIcon;
  /** Replaces the icon with a spinner and blocks presses; the label stays. */
  loading?: boolean;
  className?: string;
};

function Button({
  children,
  variant = "primary",
  size = "md",
  block = false,
  icon,
  loading = false,
  disabled,
  className,
  accessibilityState,
  ...props
}: ButtonProps) {
  const colors = useThemeColors();
  const textClass = buttonTextVariants({ variant, size });
  const iconSize = size === "sm" ? 16 : 18;
  const spinnerColor =
    variant === "primary"
      ? colors.primaryForeground
      : variant === "destructive"
        ? colors.destructiveForeground
        : colors.mutedForeground;

  return (
    <TextClassContext.Provider value={textClass}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ ...accessibilityState, disabled: !!disabled, busy: loading }}
        disabled={disabled || loading}
        hitSlop={HIT_SLOP[size]}
        android_ripple={{ color: colors.primaryMedium, foreground: true }}
        className={cn(
          buttonVariants({ variant, size, block }),
          // iOS dims on press; Android shows the ripple.
          Platform.select({ ios: "active:opacity-60" }),
          disabled && "opacity-50",
          Platform.OS === "android" && "overflow-hidden",
          className
        )}
        {...props}
      >
        {loading ? (
          <ActivityIndicator size="small" color={spinnerColor} />
        ) : icon ? (
          <Icon as={icon} size={iconSize} />
        ) : null}
        {typeof children === "string" ? <Text>{children}</Text> : children}
      </Pressable>
    </TextClassContext.Provider>
  );
}

export { Button, buttonTextVariants, buttonVariants };
export type { ButtonProps, ButtonSize, ButtonVariant };
