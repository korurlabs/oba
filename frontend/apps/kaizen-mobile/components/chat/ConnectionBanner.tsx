import { CloudOff, Lock, RefreshCw, WifiOff, type LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";

import {
  CHAT_RECONNECTING_BANNER_DELAY_MS,
  chatConnectionBanner,
  type ChatConnectionBanner,
} from "@/components/chat/chatScreenModel";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation, type TranslationKey } from "@/i18n";
import { realtime, useRealtimeStatus } from "@/lib/realtime";
import { cn } from "@/lib/utils";

interface BannerLook {
  icon: LucideIcon;
  message: TranslationKey;
  /** The strip's ground. */
  ground: string;
  /** The ink of its icon and text. */
  ink: string;
}

/**
 * A reconnect that may still succeed is tinted like a highlight; a paused
 * or missing connection is quiet; a refusal reads as an error.
 */
const BANNER: Record<ChatConnectionBanner, BannerLook> = {
  networkOffline: {
    icon: WifiOff,
    message: "chatNetworkOffline",
    ground: "bg-secondary",
    ink: "text-muted-foreground",
  },
  noAccess: {
    icon: Lock,
    message: "chatNoAccess",
    ground: "bg-secondary",
    ink: "text-destructive",
  },
  offline: {
    icon: CloudOff,
    message: "chatLiveOffline",
    ground: "bg-secondary",
    ink: "text-muted-foreground",
  },
  reconnecting: {
    icon: RefreshCw,
    message: "chatReconnecting",
    ground: "bg-chat-highlight",
    ink: "text-foreground",
  },
};

/**
 * True once the connection has not been live for the banner delay, so a
 * short reconnect never flashes a banner.
 */
function useStalled(live: boolean): boolean {
  const [stalled, setStalled] = React.useState<boolean>(false);
  React.useEffect(() => {
    if (live) {
      setStalled(false);
      return;
    }
    const timer = setTimeout(() => setStalled(true), CHAT_RECONNECTING_BANNER_DELAY_MS);
    return () => clearTimeout(timer);
  }, [live]);
  return stalled;
}

/**
 * The strip under the navigation bar of a chat screen while live updates do
 * not arrive: reconnecting, paused with Retry, refused, or no network. It
 * renders nothing while connected.
 */
export function ConnectionBanner() {
  const { t } = useTranslation();
  const snapshot = useRealtimeStatus();
  const stalled: boolean = useStalled(snapshot.status === "connected");
  const state: ChatConnectionBanner | null = chatConnectionBanner(snapshot, stalled);
  if (!state) return null;
  const { icon, message, ground, ink } = BANNER[state];

  const retry = (): void => {
    realtime.retry().catch((error: unknown) => {
      // The banner stays up, which is the visible state of the failure.
      console.error("[chat] realtime retry failed", error instanceof Error ? error.message : error);
    });
  };

  return (
    <View
      accessibilityRole="alert"
      accessibilityLiveRegion="polite"
      className={cn("flex-row items-center justify-center gap-1.5 px-3 py-1", ground)}
    >
      <Icon as={icon} size={14} className={ink} />
      <Text variant="caption" className={cn("shrink font-sans-medium", ink)}>
        {t(message)}
      </Text>
      {state === "offline" ? (
        <Pressable
          accessibilityRole="button"
          onPress={retry}
          hitSlop={{ top: 12, bottom: 12, left: 8, right: 8 }}
          className="pl-1 active:opacity-60"
        >
          <Text variant="caption" className="font-sans-semibold text-link">
            {t("chatRetry")}
          </Text>
        </Pressable>
      ) : null}
    </View>
  );
}
