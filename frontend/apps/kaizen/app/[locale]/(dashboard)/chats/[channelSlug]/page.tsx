import type { Metadata } from "next";
import type { SearchParams } from "nuqs/server";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { ChatRoutePage, chatRouteMetadata, loadChatRoute } from "@/features/chat/components/chat-route-page";
import {
  CHAT_CHANNEL_CONTENT_PARAMS,
  chatChannelPath,
  chatRouteHref,
  CHATS_PATH,
  LEGACY_CHANNEL_ID_PARAM,
} from "@/features/chat/lib/chat-route";
import { ChatChannelPurpose } from "@/features/chat/schemas";

interface ChannelPageProps {
  params: Promise<{ locale: string; channelSlug: string }>;
  searchParams: Promise<SearchParams>;
}

export async function generateMetadata({ params }: Pick<ChannelPageProps, "params">): Promise<Metadata> {
  const { channelSlug } = await params;
  return chatRouteMetadata((channel) => channel.slug === channelSlug);
}

/**
 * A channel by its slug, among the channels of the session's default
 * organization. A slug the reader does not see or cannot read (unknown,
 * renamed, deleted, private) redirects to `/chats`, which opens their default
 * channel; what pointed into the lost channel is dropped. The daily updates
 * and pull requests channels have their own URLs, so their slug redirects
 * there with the query kept.
 */
export default async function ChannelPage({ params, searchParams }: ChannelPageProps) {
  const [{ locale, channelSlug }, query] = await Promise.all([params, searchParams]);
  const { channels } = await loadChatRoute();

  // The list is unknown on the server: the client view resolves the channel.
  if (!channels) return <ChatRoutePage />;

  const channel = channels.find((candidate) => candidate.slug === channelSlug);
  if (!channel?.member) {
    return redirect({
      href: chatRouteHref(CHATS_PATH, query, [LEGACY_CHANNEL_ID_PARAM, ...CHAT_CHANNEL_CONTENT_PARAMS]),
      locale,
    });
  }
  if (channel.purpose !== ChatChannelPurpose.GENERAL) {
    redirect({ href: chatRouteHref(chatChannelPath(channel), query, [LEGACY_CHANNEL_ID_PARAM]), locale });
  }

  return <ChatRoutePage channelId={channel.id} />;
}
