import { useLocalSearchParams, useRouter } from "expo-router";
import { Copy, Hash, Link, Lock, Share, Share2, type LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, ScrollView, View } from "react-native";

import { chatCanWrite } from "@/components/chat/chatMessageActions";
import {
  chatShareChannelHref,
  chatSharedTicketFromParams,
  chatShareTargets,
  type ChatSharedTicket,
} from "@/components/chat/chatShare";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { SearchField } from "@/components/kazan/SearchField";
import { useTicketLinks } from "@/components/ticket/useTicketLinks";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useChatChannelOrder } from "@/hooks/queries/chatChannelOrder";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { ChatChannelVisibility, type ChatChannelResponse } from "@/lib/schemas/chat";

/** Placeholder tiles while the channels load: two rows of the grid. */
const LOADING_TILES: readonly number[] = [0, 1, 2, 3, 4, 5, 6, 7];

/**
 * The footer pads its content by the sheet's side margin (16 pt on iOS, 24 dp
 * on Android); the action row reaches past it to the sheet edges, so it
 * scrolls edge to edge, and pads its own content back by the same amount.
 */
const ACTION_ROW_INSET: number = Platform.select({ ios: 16, default: 24 });

/**
 * Share a ticket, opened from the ticket screen's Share button, the ticket
 * menu and a held board card. The grid offers the channels the reader can
 * post in: a tap gives the sheet way to that channel with the ticket in its
 * composer, so the reader can add a note before sending. Daily updates and
 * pull requests have composers of their own, so they are not offered; guests
 * read channels but cannot write in them. The row at the foot copies the
 * link or the key, or hands the ticket to the system share menu.
 */
export default function ShareTicketSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{
    ticketId?: string;
    ticketKey?: string;
    ticketTitle?: string;
  }>();
  const ticket: ChatSharedTicket | null = React.useMemo(
    () =>
      chatSharedTicketFromParams({
        ticketId: params.ticketId,
        ticketKey: params.ticketKey,
        ticketTitle: params.ticketTitle,
      }),
    [params.ticketId, params.ticketKey, params.ticketTitle]
  );
  const canWrite: boolean = chatCanWrite(useOrganizationRole());

  if (!ticket) {
    return (
      <BottomSheet title={t("ticketShare")} cancelLabel={t("chatCancel")} scroll>
        <EmptyState variant="error" title={t("chatShareInvalid")} className="mt-8" />
      </BottomSheet>
    );
  }
  return (
    <BottomSheet
      title={t("ticketShare")}
      cancelLabel={t("chatCancel")}
      footer={<ShareActionRow ticket={ticket} />}
    >
      <View className="pt-2">
        <View className="mx-4 mb-3 flex-row items-center gap-2 rounded-lg border border-border bg-card p-3">
          <Text variant="subhead" className="font-mono-medium text-link">
            {ticket.key}
          </Text>
          {ticket.title ? (
            <Text variant="subhead" numberOfLines={1} className="flex-1">
              {ticket.title}
            </Text>
          ) : null}
        </View>
        {canWrite ? (
          <ShareChannelGrid ticket={ticket} />
        ) : (
          <EmptyState title={t("ticketShareGuest")} className="mt-6" />
        )}
      </View>
    </BottomSheet>
  );
}

function ShareChannelGrid({ ticket }: { ticket: ChatSharedTicket }) {
  const { t } = useTranslation();
  const router = useRouter();
  const { query, channels } = useChatChannelOrder();
  const [search, setSearch] = React.useState<string>("");

  const targets: ChatChannelResponse[] = React.useMemo(
    () => chatShareTargets(channels, search),
    [channels, search]
  );

  const body = (() => {
    if (query.isPending) {
      return (
        <View className="flex-row flex-wrap px-2 pt-4">
          {LOADING_TILES.map((index) => (
            <View key={index} className="w-1/4 items-center px-1 pb-5">
              <Skeleton width={64} height={64} round />
              <Skeleton width={48} height={10} className="mt-2" />
            </View>
          ))}
        </View>
      );
    }
    if (query.isError && channels.length === 0) {
      return (
        <InlineError
          message={t("chatChannelsError")}
          onRetry={() => void query.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    if (targets.length === 0) {
      return (
        <EmptyState
          title={t(search.trim() ? "chatSwitcherEmpty" : "chatShareNoChannels")}
          className="mt-8"
        />
      );
    }
    return (
      <View className="flex-row flex-wrap px-2 pt-4">
        {targets.map((channel) => (
          <ShareChannelTile
            key={channel.id}
            channel={channel}
            onPress={() => router.replace(chatShareChannelHref(channel.id, ticket))}
          />
        ))}
      </View>
    );
  })();

  return (
    <>
      <SearchField label={t("chatSwitcherSearch")} value={search} onChangeText={setSearch} />
      {body}
    </>
  );
}

type ShareChannelTileProps = {
  channel: ChatChannelResponse;
  onPress: () => void;
};

/** One channel of the grid: its round icon and its name under it. */
function ShareChannelTile({ channel, onPress }: ShareChannelTileProps) {
  const { t } = useTranslation();
  const isPrivate: boolean = channel.visibility === ChatChannelVisibility.PRIVATE;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={t("ticketShareToChannel", { name: channel.name })}
      accessibilityHint={t("ticketShareChannelHint")}
      onPress={onPress}
      className="w-1/4 items-center px-1 pb-5 active:opacity-60"
    >
      <View className="size-16 items-center justify-center rounded-full bg-primary-light">
        <Icon as={isPrivate ? Lock : Hash} size={26} className="text-link" />
      </View>
      <Text variant="caption" numberOfLines={2} className="mt-1.5 text-center text-foreground">
        {channel.name}
      </Text>
    </Pressable>
  );
}

type ShareAction = {
  id: string;
  icon: LucideIcon;
  label: string;
  onPress: () => void;
};

/**
 * The actions at the foot of the sheet. Copy link and Copy key close the
 * sheet, so their confirmation shows over the ticket. The system share menu
 * opens over the sheet, which closes only once the reader shared the ticket;
 * closing the menu returns to the sheet.
 */
function ShareActionRow({ ticket }: { ticket: ChatSharedTicket }) {
  const { t } = useTranslation();
  const { dismiss } = useSheet();
  const links = useTicketLinks();

  const actions: ShareAction[] = [
    {
      id: "copyLink",
      icon: Link,
      label: t("ticketCopyLink"),
      onPress: () => {
        dismiss();
        links.copyTicketLink(ticket.key);
      },
    },
    {
      id: "shareVia",
      icon: Platform.OS === "ios" ? Share : Share2,
      label: t("ticketShareVia"),
      onPress: () => {
        void links.shareTicket(ticket).then((shared: boolean) => {
          if (shared) dismiss();
        });
      },
    },
    {
      id: "copyKey",
      icon: Copy,
      label: t("ticketCopyKey"),
      onPress: () => {
        dismiss();
        links.copyTicketKey(ticket.key);
      },
    },
  ];

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={{ marginHorizontal: -ACTION_ROW_INSET }}
      contentContainerStyle={{ paddingHorizontal: ACTION_ROW_INSET - 4 }}
    >
      {actions.map((action) => (
        <ShareActionButton key={action.id} action={action} />
      ))}
    </ScrollView>
  );
}

/** A round icon button with its label under it. */
function ShareActionButton({ action }: { action: ShareAction }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={action.label}
      onPress={action.onPress}
      className="w-20 items-center px-1 active:opacity-60"
    >
      <View className="size-14 items-center justify-center rounded-full border border-border bg-secondary">
        <Icon as={action.icon} size={22} className="text-foreground" />
      </View>
      <Text variant="caption" numberOfLines={2} className="mt-1.5 text-center text-foreground">
        {action.label}
      </Text>
    </Pressable>
  );
}
