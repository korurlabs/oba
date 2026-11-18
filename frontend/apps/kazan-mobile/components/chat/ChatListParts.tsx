import { ArrowDown, MessagesSquare, X } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import type { ChatUnreadMarker } from "@/components/chat/chatListModel";
import {
  chatDayKind,
  chatTypingLine,
  type ChatTypingLine,
} from "@/components/chat/chatScreenModel";
import { Badge } from "@/components/ui/badge";
import { formatBadgeCount } from "@/components/ui/badge-count";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useShadow } from "@/components/ui/use-shadow";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { CHAT_UNREAD_CAP } from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

/** "Today", "Yesterday" or the date above the first message of a day. */
function DaySeparatorImpl({ at }: { at: string }) {
  const { t } = useTranslation();
  const dates = useDates();
  const kind = chatDayKind(at);
  const label: string | null =
    kind === "today"
      ? t("dateToday")
      : kind === "yesterday"
        ? t("chatYesterday")
        : dates.instantDate(at);
  return (
    <View accessibilityRole="header" className="items-center px-4 py-2">
      <View className="rounded-full bg-muted px-2.5 py-0.5">
        <Text variant="caption" className="font-sans-semibold text-muted-foreground">
          {label ?? ""}
        </Text>
      </View>
    </View>
  );
}

export const DaySeparator = React.memo(DaySeparatorImpl);

/** Where the messages that were unread on opening begin. */
function UnreadDividerImpl() {
  const { t } = useTranslation();
  return (
    <View className="flex-row items-center gap-3 px-4 pb-1 pt-3">
      <View className="h-px flex-1 bg-destructive" />
      <Text variant="caption" className="font-sans-semibold text-destructive">
        {t("chatNewMessagesDivider")}
      </Text>
      <View className="h-px flex-1 bg-destructive" />
    </View>
  );
}

export const UnreadDivider = React.memo(UnreadDividerImpl);

type JumpPillProps = {
  /** Messages of others that arrived below; 0 shows "Jump to latest". */
  count: number;
  onPress: () => void;
};

/**
 * Floats above the composer while the reader is away from the newest
 * message: filled while new messages wait below, a plain card otherwise.
 */
export function JumpPill({ count, onPress }: JumpPillProps) {
  const { t } = useTranslation();
  const shadow = useShadow("card");
  const fresh: boolean = count > 0;
  const label: string = fresh ? t("chatJumpNew", { count }) : t("chatJumpLatest");
  return (
    <View pointerEvents="box-none" className="absolute bottom-3 left-0 right-0 items-center">
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={label}
        style={shadow}
        className={cn(
          "flex-row items-center gap-1.5 rounded-full px-4",
          fresh ? "bg-primary-fill" : "border border-border bg-card",
          Platform.OS === "ios" ? "h-hit-ios" : "h-hit-android"
        )}
      >
        <Icon
          as={ArrowDown}
          size={16}
          className={fresh ? "text-primary-foreground" : "text-foreground"}
        />
        <Text
          variant="footnote"
          className={cn(
            "font-sans-semibold",
            fresh ? "text-primary-foreground" : "text-foreground"
          )}
        >
          {label}
        </Text>
      </Pressable>
    </View>
  );
}

type UnreadBarProps = {
  marker: ChatUnreadMarker;
  onMarkRead: () => void;
  onDismiss: () => void;
};

/**
 * Above a conversation opened with unread messages: how many, since when,
 * Mark as read and Dismiss.
 */
export function UnreadBar({ marker, onMarkRead, onDismiss }: UnreadBarProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const count: string =
    marker.count >= CHAT_UNREAD_CAP
      ? t("chatUnreadBarCapped", { count: CHAT_UNREAD_CAP })
      : t("chatUnreadBar", { count: marker.count });
  const since: string | null = marker.lastReadAt ? dates.timestamp(marker.lastReadAt) : null;
  const label: string = since ? `${count} ${t("chatUnreadSince", { time: since })}` : count;
  return (
    <View
      accessibilityLiveRegion="polite"
      className="flex-row items-center gap-3 bg-primary-fill py-1.5 pl-3 pr-1"
    >
      <Text
        variant="footnote"
        numberOfLines={1}
        className="flex-1 font-sans-medium text-primary-foreground"
      >
        {label}
      </Text>
      <Pressable
        onPress={onMarkRead}
        accessibilityRole="button"
        hitSlop={8}
        className="justify-center"
      >
        <Text variant="footnote" className="font-sans-semibold text-primary-foreground">
          {t("chatMarkAsRead")}
        </Text>
      </Pressable>
      <Pressable
        onPress={onDismiss}
        accessibilityRole="button"
        accessibilityLabel={t("chatUnreadDismiss")}
        hitSlop={8}
        className="justify-center px-2"
      >
        <Icon as={X} size={16} className="text-primary-foreground" />
      </Pressable>
    </View>
  );
}

/** "Elif Şahin is typing…" above the composer; empty space keeps the layout still. */
export function TypingLine({ names }: { names: readonly string[] }) {
  const { t } = useTranslation();
  const line: ChatTypingLine | null = chatTypingLine(names);
  return (
    <View className="min-h-5 justify-center px-4" accessibilityLiveRegion="polite">
      {line ? (
        <Text variant="caption" numberOfLines={1} className="text-muted-foreground">
          {t(line.key, line.params)}
        </Text>
      ) : null}
    </View>
  );
}

/**
 * The thread mark of a channel row on Chat home: a count badge with the
 * thread icon for unread replies in the reader's threads of that channel,
 * "@N" when some of them notify the reader. It sits before the channel's own
 * count, so each source of the Chat tab badge reads apart on the row.
 */
export function ChatThreadRepliesMark({
  replies,
  mentions,
}: {
  replies: number;
  mentions: number;
}) {
  const mentionText: string | null = formatBadgeCount(mentions);
  const countText: string | null = mentionText ? `@${mentionText}` : formatBadgeCount(replies);
  if (!countText) return null;
  return (
    <Badge variant="count" className="gap-0.5">
      <Icon as={MessagesSquare} size={12} />
      <Text numberOfLines={1}>{countText}</Text>
    </Badge>
  );
}
