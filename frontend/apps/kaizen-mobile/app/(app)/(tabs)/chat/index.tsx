import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import {
  GitPullRequest,
  Hash,
  Lock,
  MessageSquare,
  Newspaper,
  Plus,
  type LucideIcon,
} from "lucide-react-native";
import * as React from "react";
import { RefreshControl, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { chatAuthorName } from "@/components/chat/chatDisplay";
import { chatCanModerate } from "@/components/chat/chatMessageActions";
import {
  chatChannelUnread,
  chatHomeSections,
  chatPreviewLine,
} from "@/components/chat/chatScreenModel";
import { ChatThreadRepliesMark } from "@/components/chat/ChatListParts";
import { chatChannelThreadMarks } from "@/components/chat/chatThreadModel";
import { ConnectionBanner } from "@/components/chat/ConnectionBanner";
import { postedCount } from "@/components/updates/updateModel";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { SearchField } from "@/components/kaizen/SearchField";
import { TAB_ROOT_CONTENT_TOP, TabRootHeader } from "@/components/kaizen/TabRootHeader";
import { SkeletonRow } from "@/components/ui/skeleton";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { chatKeys, useChatChannelPreviews, useChatMeId, useChatUnread } from "@/hooks/queries/chat";
import { useChatChannelOrder } from "@/hooks/queries/chatChannelOrder";
import { useMissingUpdates } from "@/hooks/queries/updates";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation, type TranslationKey } from "@/i18n";
import { businessToday } from "@/lib/dates";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  ChatChannelPurpose,
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";
import { UPDATE_DEADLINE_TIME } from "@/lib/schemas/updates";
import { cn } from "@/lib/utils";

const PURPOSE_ROW: Record<string, { icon: LucideIcon; title: TranslationKey }> = {
  [ChatChannelPurpose.DAILY_UPDATES]: { icon: Newspaper, title: "chatDailyUpdates" },
  [ChatChannelPurpose.PULL_REQUESTS]: { icon: GitPullRequest, title: "chatPullRequests" },
};

/**
 * The Chat tab root: a search over every channel, Daily updates and Pull
 * requests, then the channels in the reader's saved order, each with its
 * newest message and unread count. Unread replies in the reader's threads
 * of a channel mark its row too, beside the channel's own count, so every
 * part of the Chat tab badge shows on the row it comes from. Realtime keeps
 * both current; a pull refetches them. Owners and admins also create channels from here and see
 * how many have posted today's update in place of its newest message. A
 * private channel they are not on opens its members, where they can add
 * themselves; for everyone else that row stays closed.
 */
export default function ChatHomeScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useChatMeId();
  const { query, channels } = useChatChannelOrder();
  const unread = useChatUnread().data;
  const previews = useChatChannelPreviews(channels);
  const sections = React.useMemo(() => chatHomeSections(channels), [channels]);
  const canModerate: boolean = chatCanModerate(useOrganizationRole());
  // Only owners and admins may read who has posted; anyone else keeps the preview.
  const report = useMissingUpdates(businessToday(), canModerate).data;
  // With nobody expected (a weekend, or every membership newer than the
  // deadline) "0 of 0 posted" says nothing, so the row keeps its preview.
  const postedLine: string | null =
    report && report.workday && report.expectedCount > 0
      ? t("updPostedDeadline", {
          posted: postedCount(report),
          expected: report.expectedCount,
          time: UPDATE_DEADLINE_TIME,
        })
      : null;
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const refresh = React.useCallback(() => {
    if (!orgId) return;
    setRefreshing(true);
    Promise.all([
      queryClient.refetchQueries({ queryKey: chatKeys.channels(orgId), type: "active" }),
      queryClient.refetchQueries({ queryKey: chatKeys.unread(orgId), type: "active" }),
      queryClient.refetchQueries({ queryKey: chatKeys.previewsAll(orgId), type: "active" }),
    ])
      .catch((error: unknown) => {
        // The list shows its own error state; this only ends the pull.
        console.warn("[chat] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [orgId, queryClient]);

  // The daily updates and pull requests channels open their own screens, with cards and filters.
  const openChannel = React.useCallback(
    (channel: ChatChannelResponse) =>
      channel.purpose === ChatChannelPurpose.DAILY_UPDATES
        ? router.push("/chat/updates")
        : channel.purpose === ChatChannelPurpose.PULL_REQUESTS
          ? router.push("/chat/pull-requests")
          : router.push({
              pathname: "/chat/channel/[channelId]",
              params: { channelId: channel.id },
            }),
    [router]
  );

  const labels = { you: t("chatYou"), unknownMember: t("chatUnknownMember") };
  const authorName = (message: ChatMessageResponse): string =>
    chatAuthorName(message, labels.unknownMember);

  const row = (channel: ChatChannelResponse, purposeRow: boolean) => {
    const purpose = PURPOSE_ROW[channel.purpose];
    const isPrivate: boolean = channel.visibility === ChatChannelVisibility.PRIVATE;
    const preview: ChatMessageResponse | null | undefined = previews.get(channel.id);
    const channelUnread = chatChannelUnread(unread, channel.id);
    const unreadMessages: number = channelUnread?.unread ?? 0;
    const threadMarks = chatChannelThreadMarks(unread, channel.id);
    const notifications: number = (channelUnread?.mentionCount ?? 0) + threadMarks.mentions;
    const unreadValue: string = [
      unreadMessages > 0 ? t("chatHomeUnreadMessages", { count: unreadMessages }) : null,
      threadMarks.unread > 0 ? t("chatHomeThreadReplies", { count: threadMarks.unread }) : null,
      notifications > 0 ? t("chatThreadsMentions", { count: notifications }) : null,
    ]
      .filter(Boolean)
      .join(", ");
    const subtitle: string = !channel.member
      ? t(canModerate ? "chatChannelNotMemberManage" : "chatPrivateNotMember")
      : purposeRow && channel.purpose === ChatChannelPurpose.DAILY_UPDATES && postedLine
        ? postedLine
        : (chatPreviewLine(preview, meId, labels, authorName) ??
          (preview === null ? t("chatNoPreview") : ""));
    return (
      <ListRow
        key={channel.id}
        icon={purposeRow && purpose ? purpose.icon : isPrivate ? Lock : Hash}
        title={purposeRow && purpose ? t(purpose.title) : channel.name}
        subtitle={subtitle || undefined}
        trailing={
          <ChatThreadRepliesMark replies={threadMarks.unread} mentions={threadMarks.mentions} />
        }
        badge={unreadMessages}
        mentions={channelUnread?.mentionCount}
        unread={unreadMessages > 0 || threadMarks.unread > 0}
        accessibilityValue={unreadValue || undefined}
        disabled={!channel.member && !canModerate}
        onPress={
          channel.member
            ? () => openChannel(channel)
            : canModerate
              ? () =>
                  router.push({
                    pathname: "/chat/channel-members",
                    params: { channelId: channel.id },
                  })
              : undefined
        }
      />
    );
  };

  const body = (() => {
    if (query.isPending) {
      return (
        <ListSection>
          {[0, 1, 2, 3].map((index) => (
            <SkeletonRow key={index} />
          ))}
        </ListSection>
      );
    }
    if (query.isError && channels.length === 0) {
      return (
        <EmptyState
          variant="error"
          title={t("chatChannelsError")}
          onAction={() => void query.refetch()}
          className="mt-16"
        />
      );
    }
    if (channels.length === 0) {
      return (
        <EmptyState
          icon={MessageSquare}
          title={t("chatNoChannelsTitle")}
          message={t("chatNoChannelsBody")}
          className="mt-16"
        />
      );
    }
    return (
      <>
        {sections.purpose.length > 0 ? (
          <ListSection>{sections.purpose.map((channel) => row(channel, true))}</ListSection>
        ) : null}
        {sections.chats.length > 0 ? (
          <ListSection header={t("chatSectionChats")}>
            {sections.chats.map((channel) => row(channel, false))}
          </ListSection>
        ) : null}
      </>
    );
  })();

  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-screen">
      <TabRootHeader
        title={t("tabChat")}
        actions={
          canModerate
            ? [
                {
                  icon: Plus,
                  label: t("chatNewChannel"),
                  onPress: () => router.push("/chat/new-channel"),
                },
              ]
            : undefined
        }
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName={cn("gap-6 pb-10", TAB_ROOT_CONTENT_TOP)}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        <ConnectionBanner />
        {channels.length > 0 ? (
          <SearchField
            label={t("chatSearchMessages")}
            onPress={() => router.push("/chat/search")}
          />
        ) : null}
        {body}
      </ScrollView>
    </SafeAreaView>
  );
}
