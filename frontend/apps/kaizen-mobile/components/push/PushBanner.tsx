import { Bell, MessageCircle, Siren } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, { SlideInUp, SlideOutUp } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { FullWindowOverlay } from "react-native-screens";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useShadow } from "@/components/ui/use-shadow";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { PushKind, type PushData } from "@/lib/schemas/push";
import { cn } from "@/lib/utils";

/** A push shown while the app is open. */
export interface PushBannerItem {
  /** The notification's identifier; a new one replaces the shown banner. */
  id: string;
  title: string | null;
  body: string | null;
  /** When it was sent, drawn `HH:mm`. */
  sentAt: Date | string;
  data: PushData;
}

/** A swipe up by this many points dismisses the banner. */
const DISMISS_SWIPE = -24;

function kindIcon(kind: PushData["type"]): { icon: LucideIcon; tile: string } {
  if (kind === PushKind.INCIDENT) return { icon: Siren, tile: "bg-destructive" };
  if (kind === PushKind.NUDGE) return { icon: Bell, tile: "bg-primary-fill" };
  return { icon: MessageCircle, tile: "bg-primary-fill" };
}

interface PushBannerProps {
  item: PushBannerItem | null;
  onOpen: (item: PushBannerItem) => void;
  onDismiss: () => void;
}

/**
 * The in-app banner for a push that arrives while Kaizen is open, instead of
 * the system one. It is drawn on `popover` with the dialog shadow, so it reads
 * as a layer above the app rather than as the system's translucent card. iOS
 * leads with the kind tile, the incident (or conversation) as the title, its
 * time, the conversation under an incident title, and the text. Android puts
 * the app name, conversation and time in a header row above the message, as
 * its notification shade does. A tap opens it as a notification tap would; a
 * swipe up dismisses it. It sits under the status bar and draws above sheets
 * on iOS.
 */
export function PushBanner({ item, onOpen, onDismiss }: PushBannerProps) {
  const insets = useSafeAreaInsets();
  if (!item) return null;

  const banner = (
    <View
      pointerEvents="box-none"
      className="absolute left-0 right-0 px-2"
      style={{ top: insets.top + (Platform.OS === "ios" ? 4 : 8) }}
    >
      <BannerCard key={item.id} item={item} onOpen={onOpen} onDismiss={onDismiss} />
    </View>
  );
  // A FullWindowOverlay draws above native formSheet presentations, which an
  // absolutely positioned view under the navigator cannot.
  return Platform.OS === "ios" ? <FullWindowOverlay>{banner}</FullWindowOverlay> : banner;
}

function BannerCard({
  item,
  onOpen,
  onDismiss,
}: {
  item: PushBannerItem;
  onOpen: (item: PushBannerItem) => void;
  onDismiss: () => void;
}) {
  const { t } = useTranslation();
  const dates = useDates();
  const shadow = useShadow("dialog");
  const { icon, tile } = kindIcon(item.data.type);
  const time: string | null = dates.time(item.sentAt);
  const conversation: string | null = item.data.conversation ?? null;
  const title: string = item.title ?? conversation ?? t("pushBannerFallbackTitle");
  // An incident push carries its own title; the conversation then goes on a line of its own.
  const subtitle: string | null = item.title && conversation ? conversation : null;

  const swipe = Gesture.Pan()
    .runOnJS(true)
    .activeOffsetY(-8)
    .onEnd((event) => {
      if (event.translationY < DISMISS_SWIPE) onDismiss();
    });

  return (
    <GestureDetector gesture={swipe}>
      <Animated.View entering={SlideInUp} exiting={SlideOutUp} style={shadow}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={[title, subtitle, item.body].filter(Boolean).join(", ")}
          accessibilityHint={t("pushBannerHint")}
          accessibilityLiveRegion="polite"
          onPress={() => onOpen(item)}
          className={cn(
            "bg-popover active:opacity-80",
            Platform.OS === "ios"
              ? "flex-row gap-3 rounded-[22px] border-hairline border-border px-3.5 py-3"
              : "gap-2 rounded-3xl px-4 py-3.5"
          )}
        >
          {Platform.OS === "ios" ? (
            <>
              <View className={cn("size-[38px] items-center justify-center rounded-lg", tile)}>
                <Icon as={icon} size={20} className="text-primary-foreground" />
              </View>
              <View className="flex-1 gap-px">
                <View className="flex-row items-baseline gap-2">
                  <Text
                    numberOfLines={1}
                    className="flex-1 font-sans-semibold text-subhead text-popover-foreground"
                  >
                    {title}
                  </Text>
                  {time ? (
                    <Text className="font-sans text-footnote text-muted-foreground">{time}</Text>
                  ) : null}
                </View>
                {subtitle ? (
                  <Text
                    numberOfLines={1}
                    className="font-sans text-subhead text-popover-foreground"
                  >
                    {subtitle}
                  </Text>
                ) : null}
                {item.body ? (
                  <Text
                    numberOfLines={4}
                    className="font-sans text-subhead text-popover-foreground"
                  >
                    {item.body}
                  </Text>
                ) : null}
              </View>
            </>
          ) : (
            <>
              <View className="flex-row items-center gap-1.5">
                <View className={cn("size-[18px] items-center justify-center rounded-full", tile)}>
                  <Icon as={icon} size={11} className="text-primary-foreground" />
                </View>
                <Text className="font-sans-medium text-caption text-popover-foreground">
                  {t("pushBannerFallbackTitle")}
                </Text>
                {conversation ? (
                  <Text
                    numberOfLines={1}
                    className="shrink font-sans text-caption text-muted-foreground"
                  >
                    {`• ${conversation}`}
                  </Text>
                ) : null}
                {time ? (
                  <Text className="font-sans text-caption text-muted-foreground">{`• ${time}`}</Text>
                ) : null}
              </View>
              <View className="flex-row items-start gap-3">
                <View className={cn("size-9 items-center justify-center rounded-full", tile)}>
                  <Icon as={icon} size={18} className="text-primary-foreground" />
                </View>
                <View className="flex-1 gap-0.5">
                  {item.title || !conversation ? (
                    <Text
                      numberOfLines={1}
                      className="font-sans-semibold text-subhead text-popover-foreground"
                    >
                      {title}
                    </Text>
                  ) : null}
                  {item.body ? (
                    <Text
                      numberOfLines={2}
                      className="font-sans text-subhead text-muted-foreground"
                    >
                      {item.body}
                    </Text>
                  ) : null}
                </View>
              </View>
            </>
          )}
        </Pressable>
      </Animated.View>
    </GestureDetector>
  );
}
