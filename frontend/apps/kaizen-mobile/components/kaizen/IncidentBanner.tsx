import { ChevronRight, Siren } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useLargeText } from "@/components/ui/use-large-text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type IncidentBannerProps = {
  /** Open P1 incidents; gives the title "2 open P1 incidents" when `title` is not set. */
  count?: number;
  title?: string;
  /** A second line, such as the newest incident's key and title. */
  message?: string;
  /** Opens the incident list or the single incident. */
  onPress?: () => void;
  className?: string;
};

/**
 * The red banner at the top of Home and the board while a P1 incident is
 * open. It renders nothing when there is no title and the count is zero.
 * At an accessibility text size its lines wrap in full: the banner is the
 * alarm itself, and a heading cut to "1 open P1 in…" says too little.
 */
function IncidentBanner({ count, title, message, onPress, className }: IncidentBannerProps) {
  const { t } = useTranslation();
  const largeText: boolean = useLargeText();
  const heading = title ?? (count && count > 0 ? t("kitIncidentBanner", { count }) : null);
  if (!heading) return null;

  const content = (
    <>
      <View className="size-8 items-center justify-center rounded-full bg-white/20">
        <Icon as={Siren} size={18} className="text-destructive-foreground" />
      </View>
      <View className="flex-1 gap-0.5">
        <Text
          numberOfLines={largeText ? undefined : 1}
          className="font-sans-semibold text-subhead text-destructive-foreground"
        >
          {heading}
        </Text>
        {message ? (
          <Text
            numberOfLines={largeText ? undefined : 2}
            className="font-sans text-footnote text-destructive-foreground"
          >
            {message}
          </Text>
        ) : null}
      </View>
      {onPress ? (
        <Icon as={ChevronRight} size={18} className="text-destructive-foreground" />
      ) : null}
    </>
  );
  const boxClassName = cn(
    "min-h-11 flex-row items-center gap-3 rounded-lg bg-destructive-fill p-3",
    className
  );
  const label = [heading, message].filter(Boolean).join(", ");

  if (!onPress) {
    return (
      <View
        accessible
        accessibilityRole="alert"
        accessibilityLabel={label}
        className={boxClassName}
      >
        {content}
      </View>
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      className={cn(boxClassName, "active:opacity-80")}
    >
      {content}
    </Pressable>
  );
}

export { IncidentBanner };
export type { IncidentBannerProps };
