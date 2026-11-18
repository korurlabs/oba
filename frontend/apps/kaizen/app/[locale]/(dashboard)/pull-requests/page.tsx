import type { Metadata } from "next";
import type { SearchParams } from "nuqs/server";
import { ChatRoutePage, chatRouteMetadata, resolvePurposeChannel } from "@/features/chat/components/chat-route-page";
import type { ChatChannelFilter } from "@/features/chat/hooks/query-keys";
import { ChatChannelPurpose } from "@/features/chat/schemas";
import {
  PULL_REQUEST_STATUSES_PARAM,
  resolvePullRequestStatuses,
} from "@/features/pull-requests/lib/pull-request-channel-filter";

interface PullRequestsPageProps {
  params: Promise<{ locale: string }>;
  searchParams: Promise<SearchParams>;
}

export function generateMetadata(): Promise<Metadata> {
  return chatRouteMetadata((channel) => channel.purpose === ChatChannelPurpose.PULL_REQUESTS);
}

/**
 * The organization's pull requests channel, in the chat view, narrowed to
 * the share statuses and person of the URL, as the client view reads them.
 * With neither chosen the channel is unfiltered, under the same key as
 * every other channel's newest page.
 */
export default async function PullRequestsPage({ params, searchParams }: PullRequestsPageProps) {
  const [{ locale }, query] = await Promise.all([params, searchParams]);
  const channelId = await resolvePurposeChannel(ChatChannelPurpose.PULL_REQUESTS, locale, query);
  const pullRequestStatuses = resolvePullRequestStatuses(query[PULL_REQUEST_STATUSES_PARAM]);
  // An empty `person` is no filter, as in the client view.
  const authorId = typeof query.person === "string" && query.person ? query.person : null;
  const filter: ChatChannelFilter | undefined =
    pullRequestStatuses.length > 0 || authorId ? { day: null, authorId, pullRequestStatuses } : undefined;
  return <ChatRoutePage channelId={channelId} filter={filter} />;
}
