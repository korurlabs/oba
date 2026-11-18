import { MessagesSquare } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { chatUserDisplayName } from "@/components/chat/chatDisplay";
import { AvatarStack } from "@/components/ui/avatar";
import { formatBadgeCount } from "@/components/ui/badge-count";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";
import { ChatThreadStatus, type ChatThreadResponse } from "@/lib/schemas/chat";

type ChatThreadRowProps = {
  thread: ChatThreadResponse;
  /** Unread replies for the reader (`chatThreadListMarks`). */
  unreadCount: number;
  /** The unread replies that notify the reader. */
  mentionCount: number;
  onOpen: (thread: ChatThreadResponse) => void;
};

/**
 * One thread of the Threads list: its title (bold while replies are unread),
 * the reply count and last activity, who closed or archived it, the
 * members' avatars, and a red "@N" when replies notify the reader or a dot
 * for other unread replies. A thread archived by inactivity names no one.
 */
function ChatThreadRowImpl({ thread, unreadCount, mentionCount, onOpen }: ChatThreadRowProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const colors = useThemeColors();
  const unread: boolean = unreadCount > 0;
  const mentionText: string | null = formatBadgeCount(mentionCount);
  const people = React.useMemo(
    () =>
      thread.members.map((member) => ({
        name: chatUserDisplayName(member, t("chatUnknownMember")),
      })),
    [thread.members, t]
  );
  const changedBy: string | null =
    thread.statusChangedBy && thread.status !== ChatThreadStatus.ACTIVE
      ? t(
          thread.status === ChatThreadStatus.CLOSED ? "chatThreadClosedBy" : "chatThreadArchivedBy",
          { name: chatUserDisplayName(thread.statusChangedBy, t("chatUnknownMember")) }
        )
      : null;
  const activity: string = [
    t("chatThreadReplies", { count: thread.messageCount }),
    dates.relativePast(thread.lastMessageAt),
    changedBy,
  ]
    .filter(Boolean)
    .join(" · ");
  const label: string = [
    thread.title,
    activity,
    unread ? t("chatThreadsUnread", { count: unreadCount }) : null,
    mentionCount > 0 ? t("chatThreadsMentions", { count: mentionCount }) : null,
  ]
    .filter(Boolean)
    .join(", ");

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => onOpen(thread)}
      android_ripple={{ color: colors.accent }}
      className={cn(
        "flex-row items-center gap-3 bg-card px-4",
        // Two lines of text: a 44 pt row on iOS, a 72 dp two-line list item on Android.
        Platform.select({ ios: "min-h-row-ios py-2.5", default: "min-h-[72px] py-3" }),
        Platform.select({ ios: "active:bg-accent" })
      )}
    >
      <Icon as={MessagesSquare} size={20} className="text-muted-foreground" />
      <View className="flex-1 gap-0.5">
        <View className="flex-row items-center gap-1.5">
          <Text
            variant="body"
            numberOfLines={1}
            className={cn("shrink", unread ? "font-sans-semibold" : "text-foreground")}
          >
            {thread.title}
          </Text>
          {mentionText ? (
            <View className="min-h-[18px] min-w-[18px] items-center justify-center rounded-full bg-destructive-fill px-1">
              <Text className="font-sans-semibold text-caption-2 text-destructive-foreground">
                {`@${mentionText}`}
              </Text>
            </View>
          ) : unread ? (
            <View className="size-2 rounded-full bg-primary-fill" />
          ) : null}
        </View>
        <Text variant="footnote" numberOfLines={1} className="text-muted-foreground">
          {activity}
        </Text>
      </View>
      {people.length > 0 ? <AvatarStack people={people} size="sm" /> : null}
    </Pressable>
  );
}

export const ChatThreadRow = React.memo(ChatThreadRowImpl);
