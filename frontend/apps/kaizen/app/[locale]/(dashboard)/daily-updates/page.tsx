import type { Metadata } from "next";
import type { SearchParams } from "nuqs/server";
import { ChatRoutePage, chatRouteMetadata, resolvePurposeChannel } from "@/features/chat/components/chat-route-page";
import { ChatChannelPurpose } from "@/features/chat/schemas";
import { resolveUpdateDay } from "@/features/updates/lib/update-day";

interface DailyUpdatesPageProps {
  params: Promise<{ locale: string }>;
  searchParams: Promise<SearchParams>;
}

export function generateMetadata(): Promise<Metadata> {
  return chatRouteMetadata((channel) => channel.purpose === ChatChannelPurpose.DAILY_UPDATES);
}

/**
 * The organization's daily updates channel, in the chat view, narrowed to
 * the day and person of the URL (today and everyone by default), as the
 * client view reads them.
 */
export default async function DailyUpdatesPage({ params, searchParams }: DailyUpdatesPageProps) {
  const [{ locale }, query] = await Promise.all([params, searchParams]);
  const channelId = await resolvePurposeChannel(ChatChannelPurpose.DAILY_UPDATES, locale, query);
  const filter = {
    day: resolveUpdateDay(query.day),
    authorId: typeof query.person === "string" && query.person ? query.person : null,
    pullRequestStatuses: [],
  };
  return <ChatRoutePage channelId={channelId} filter={filter} />;
}
