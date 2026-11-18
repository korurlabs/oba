import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import type { SearchParams } from "nuqs/server";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { ChatRoutePage, loadChatRoute } from "@/features/chat/components/chat-route-page";
import {
  CHAT_CHANNEL_CONTENT_PARAMS,
  chatChannelPath,
  chatRouteHref,
  defaultChatChannel,
  LEGACY_CHANNEL_ID_PARAM,
} from "@/features/chat/lib/chat-route";

interface ChatsPageProps {
  params: Promise<{ locale: string }>;
  searchParams: Promise<SearchParams>;
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("chat.channel");
  return { title: t("title") };
}

/**
 * The chat without a channel in its URL. It redirects, so every chat URL
 * names its channel:
 *
 * - an old link (`?channelId=<id>`, from a notification, a bookmark or a
 *   message) to the URL of the channel it names, with the rest of its query
 *   (`jump`, `thread`, `threadMessage`, a search) kept;
 * - otherwise, or when the reader cannot read that channel, to the reader's
 *   default channel: the first readable GENERAL channel in their own order,
 *   else the first readable channel. What pointed into another channel is
 *   dropped.
 *
 * Without a readable channel, or when the channel list is unknown on the
 * server, it renders the chat view, which shows the empty state or picks the
 * channel from the list it fetches.
 */
export default async function ChatsPage({ params, searchParams }: ChatsPageProps) {
  const [{ locale }, query] = await Promise.all([params, searchParams]);
  const { channels, orgId, preferences } = await loadChatRoute();

  if (channels && orgId) {
    const legacyValue = query[LEGACY_CHANNEL_ID_PARAM];
    const legacyId = typeof legacyValue === "string" ? legacyValue : undefined;
    const named = legacyId
      ? channels.find((channel) => channel.id === legacyId && channel.member)
      : undefined;
    const target = named ?? defaultChatChannel(channels, preferences, orgId);
    if (target) {
      const drop = named
        ? [LEGACY_CHANNEL_ID_PARAM]
        : [LEGACY_CHANNEL_ID_PARAM, ...CHAT_CHANNEL_CONTENT_PARAMS];
      redirect({ href: chatRouteHref(chatChannelPath(target), query, drop), locale });
    }
  }

  return <ChatRoutePage />;
}
