import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { View } from "react-native";

import {
  chatChannelMemberRows,
  chatMemberSections,
  chatThreadMemberRows,
  type ChatMemberRow,
  type ChatMemberSections,
} from "@/components/chat/chatMembersModel";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import {
  useChatChannelMembers,
  useChatChannels,
  useChatMembers,
  useChatThread,
} from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";
import type { ChatChannelResponse } from "@/lib/schemas/chat";

/**
 * Who is in a channel (`channelId`) or a thread (`threadId`), online first.
 * A public channel's members are the organization's; a private channel's
 * and a thread's are their own. Read-only: members are managed on the web.
 */
export default function ChatMembersSheet() {
  const { t } = useTranslation();
  return (
    <BottomSheet title={t("chatMembersTitle")} cancelLabel={t("chatClose")} scroll>
      <MembersContent />
    </BottomSheet>
  );
}

/** The rows of a channel or a thread, and the state of their load. */
interface MembersLoad {
  rows: ChatMemberRow[] | undefined;
  isError: boolean;
  /** The channel list has loaded without the channel: deleted, or the reader lost access. */
  notFound: boolean;
  refetch: () => void;
}

function useChannelMemberRows(channelId: string | null): MembersLoad {
  const { t } = useTranslation();
  const channelsQuery = useChatChannels();
  const channel: ChatChannelResponse | undefined = channelId
    ? channelsQuery.data?.find((item) => item.id === channelId)
    : undefined;
  const query = useChatChannelMembers(channel);
  return {
    rows: query.data ? chatChannelMemberRows(query.data, t("chatUnknownMember")) : undefined,
    isError: query.isError || channelsQuery.isError,
    notFound: !!channelId && channelsQuery.data !== undefined && !channel,
    refetch: () => {
      void channelsQuery.refetch();
      void query.refetch();
    },
  };
}

function useThreadMemberRows(threadId: string | null): MembersLoad {
  const { t } = useTranslation();
  const threadQuery = useChatThread(threadId);
  const organizationQuery = useChatMembers();
  return {
    rows: threadQuery.data
      ? chatThreadMemberRows(
          threadQuery.data.members,
          organizationQuery.data,
          t("chatUnknownMember")
        )
      : undefined,
    isError: threadQuery.isError,
    notFound: false,
    refetch: () => {
      void threadQuery.refetch();
      void organizationQuery.refetch();
    },
  };
}

function MembersContent() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ channelId?: string; threadId?: string }>();
  const threadId: string | null = params.threadId || null;
  const channelId: string | null = threadId ? null : params.channelId || null;
  const channelLoad: MembersLoad = useChannelMemberRows(channelId);
  const threadLoad: MembersLoad = useThreadMemberRows(threadId);
  const { rows, isError, notFound, refetch } = threadId ? threadLoad : channelLoad;
  const sections: ChatMemberSections | null = React.useMemo(
    () => (rows ? chatMemberSections(rows) : null),
    [rows]
  );

  if (!sections) {
    if (notFound) {
      return <EmptyState title={t("chatChannelNotFound")} className="mt-8" />;
    }
    if (isError) {
      return (
        <InlineError message={t("chatMembersError")} onRetry={refetch} className="mx-4 mt-4" />
      );
    }
    return (
      <ListSection>
        {[0, 1, 2, 3].map((index) => (
          <SkeletonRow key={index} />
        ))}
      </ListSection>
    );
  }
  if (sections.online.length === 0 && sections.offline.length === 0) {
    return <EmptyState title={t("chatMembersEmpty")} className="mt-8" />;
  }
  return (
    <View className="pb-6">
      <ListSection header={t("chatMembersOnline", { count: sections.online.length })}>
        {sections.online.length === 0 ? (
          <Text variant="body" className="px-4 py-3 text-muted-foreground">
            {t("chatMembersNoneOnline")}
          </Text>
        ) : (
          sections.online.map((row) => (
            <ListRow
              key={row.userId}
              title={row.name}
              avatar={{ name: row.name, online: true }}
              accessory="none"
            />
          ))
        )}
      </ListSection>
      {sections.offline.length > 0 ? (
        <ListSection header={t("chatMembersOffline", { count: sections.offline.length })}>
          {sections.offline.map((row) => (
            <ListRow
              key={row.userId}
              title={row.name}
              avatar={{ name: row.name, online: false }}
              accessory="none"
            />
          ))}
        </ListSection>
      ) : null}
    </View>
  );
}
