import { Inbox, RotateCw, TriangleAlert } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type EmptyStateProps = {
  /** Default: an inbox, or a warning triangle for `variant="error"`. */
  icon?: LucideIcon;
  title: string;
  message?: string;
  /** The one way forward ("New ticket"); `error` defaults it to "Try again". */
  actionLabel?: string;
  onAction?: () => void;
  actionIcon?: LucideIcon;
  /**
   * A second way forward under the first, such as "Create own sprint" under
   * "Join a parent board's sprint"; shown only with the first action.
   */
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
  secondaryActionIcon?: LucideIcon;
  /** `empty` for no data, `error` for a failed load that can be retried. */
  variant?: "empty" | "error";
  className?: string;
};

/**
 * What a list or screen shows when it has nothing, or when its load failed: an
 * icon in a soft circle, a title, a line of help and at most two actions,
 * the main one first.
 */
function EmptyState({
  icon,
  title,
  message,
  actionLabel,
  onAction,
  actionIcon,
  secondaryActionLabel,
  onSecondaryAction,
  secondaryActionIcon,
  variant = "empty",
  className,
}: EmptyStateProps) {
  const { t } = useTranslation();
  const isError = variant === "error";
  const label = actionLabel ?? (isError && onAction ? t("kitTryAgain") : undefined);

  return (
    <View
      accessibilityRole={isError ? "alert" : undefined}
      className={cn("flex-1 items-center justify-center gap-2 px-6 py-8", className)}
    >
      <View
        className={cn(
          "mb-2 size-16 items-center justify-center rounded-full",
          isError ? "bg-error-light" : "bg-primary-light"
        )}
      >
        <Icon
          as={icon ?? (isError ? TriangleAlert : Inbox)}
          size={28}
          className={isError ? "text-destructive" : "text-link"}
        />
      </View>
      <Text accessibilityRole="header" className="text-center font-sans-semibold text-title-3">
        {title}
      </Text>
      {message ? (
        <Text className="max-w-[260px] text-center font-sans text-subhead text-muted-foreground">
          {message}
        </Text>
      ) : null}
      {label && onAction ? (
        <Button
          className="mt-3"
          variant={isError ? "secondary" : "primary"}
          icon={actionIcon ?? (isError ? RotateCw : undefined)}
          onPress={onAction}
        >
          {label}
        </Button>
      ) : null}
      {label && onAction && secondaryActionLabel && onSecondaryAction ? (
        <Button variant="secondary" icon={secondaryActionIcon} onPress={onSecondaryAction}>
          {secondaryActionLabel}
        </Button>
      ) : null}
    </View>
  );
}

type InlineErrorProps = {
  message: string;
  onRetry?: () => void;
  className?: string;
};

/** A failed load inside a screen that still shows other content, such as one dashboard section. */
function InlineError({ message, onRetry, className }: InlineErrorProps) {
  const { t } = useTranslation();
  return (
    <View
      accessibilityRole="alert"
      className={cn("flex-row items-center gap-3 rounded-md bg-error-light px-3 py-2.5", className)}
    >
      <Icon as={TriangleAlert} size={18} className="text-destructive" />
      <Text className="flex-1 font-sans text-footnote text-foreground">{message}</Text>
      {onRetry ? (
        <Button variant="ghost" size="sm" onPress={onRetry}>
          {t("kitTryAgain")}
        </Button>
      ) : null}
    </View>
  );
}

export { EmptyState, InlineError };
export type { EmptyStateProps, InlineErrorProps };
