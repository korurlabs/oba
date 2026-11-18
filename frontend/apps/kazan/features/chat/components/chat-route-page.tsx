import { cache } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary, type QueryClient } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { USERS_KEYS, type usersApi } from "@oba/shared/features/users/hooks/use-get-me";
import type { UserPreferences } from "@oba/shared/features/users/schema";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { getChatUnread, listChannelMessages, listChatChannels, requireChatData } from "../api";
import { CHAT_KEYS, chatFirstPageParam, type ChatChannelFilter } from "../hooks/query-keys";
import {
  CHAT_CHANNEL_CONTENT_PARAMS,
  CHATS_PATH,
  chatRouteHref,
  LEGACY_CHANNEL_ID_PARAM,
} from "../lib/chat-route";
import { CHAT_PAGE_SIZE, type ChatChannelPurpose, type ChatChannelResponse } from "../schemas";
import { ChatView } from "./chat-view";

/** Runs a prefetch request; a failure is logged and rethrown, so nothing is cached and the client fetches. */
async function logFailure<T>(what: string, request: () => Promise<T>): Promise<T> {
  try {
    return await request();
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error(`[chat-page] ${what} prefetch failed — client will refetch:`, message);
    throw error;
  }
}

export interface ChatRouteData {
  /** The request's query client; the page that renders dehydrates it. */
  queryClient: QueryClient;
  /** The session's default organization, the organization every chat request goes to. */
  orgId: string | undefined;
  /**
   * The channels the reader sees, or null when they are unknown: no session
   * token (the dashboard layout sends the user to sign in), no organization,
   * an organization that does not grant Kazan, or a failed request. A page then resolves nothing on the server and the
   * client view, which fetches the list itself, decides.
   */
  channels: ChatChannelResponse[] | null;
  /** The reader's preferences, which hold their own channel order. */
  preferences: UserPreferences | null | undefined;
  /**
   * Chat requests can be made: the session holds an access token, and the
   * selected organization grants Kazan (or the server could not tell).
   */
  canFetch: boolean;
}

/**
 * The current user ("me") and the channel list of a chat request, prefetched
 * under the keys the client hooks use. Once per request: the page that
 * resolves its channel from the list and the `ChatRoutePage` it renders share
 * one query client and one pair of requests. Both are best-effort: TanStack
 * Query does not cache a failed query, so on a failure the client fetches it
 * itself.
 */
export const loadChatRoute = cache(async (): Promise<ChatRouteData> => {
  const queryClient = makeQueryClient();
  const session = await auth();
  const orgId = session?.user?.defaultOrganizationId || undefined;
  // Without a token the dashboard layout sends the user to sign in; without
  // Kazan in the organization the no-access panel takes the place of the
  // chat. A request would only fail.
  if (!session?.accessToken || (await isKazanClosedOnServer())) {
    return { queryClient, orgId, channels: null, preferences: undefined, canFetch: false };
  }

  await Promise.all([
    prefetchMe(queryClient, session.accessToken, "chat-page"),
    orgId
      ? queryClient.prefetchQuery({
          queryKey: CHAT_KEYS.channels(orgId),
          queryFn: () => logFailure("channels", async () => requireChatData(await listChatChannels())),
        })
      : undefined,
  ]);
  const channels = orgId
    ? (queryClient.getQueryData<ChatChannelResponse[]>(CHAT_KEYS.channels(orgId)) ?? null)
    : null;
  const me = queryClient.getQueryData<Awaited<ReturnType<typeof usersApi.getMe>>>(USERS_KEYS.me);
  return { queryClient, orgId, channels, preferences: me?.data?.userPreferences, canFetch: true };
});

/**
 * The title of a chat page: the name of the channel `match` picks when the
 * reader can read it, else the chat's title.
 */
export async function chatRouteMetadata(match: (channel: ChatChannelResponse) => boolean): Promise<Metadata> {
  const { channels } = await loadChatRoute();
  const channel = channels?.find((candidate) => candidate.member && match(candidate));
  if (channel) return { title: channel.name };
  const t = await getTranslations("chat.channel");
  return { title: t("title") };
}

/**
 * The open channel of `/daily-updates` or `/pull-requests`: the organization's
 * one channel of that purpose. When the reader cannot read it (it is
 * private and they are not on it, or the organization has none) the request
 * is redirected to `/chats`, which opens their default channel; the query is
 * kept, less what points into the channel they do not get. Undefined when the
 * channel list is unknown on the server (see `ChatRouteData.channels`).
 */
export async function resolvePurposeChannel(
  purpose: ChatChannelPurpose,
  locale: string,
  query: Record<string, string | string[] | undefined>,
): Promise<string | undefined> {
  const { channels } = await loadChatRoute();
  if (!channels) return undefined;
  const channel = channels.find((candidate) => candidate.purpose === purpose);
  if (!channel?.member) {
    redirect({
      href: chatRouteHref(CHATS_PATH, query, [LEGACY_CHANNEL_ID_PARAM, ...CHAT_CHANNEL_CONTENT_PARAMS]),
      locale,
    });
  }
  return channel?.id;
}

/**
 * A chat page: the chat view on the channel its URL names.
 *
 * Adds to the prefetch of `loadChatRoute` the reader's unread counts (they
 * place the "New messages" divider) and the newest page of the open channel,
 * so the channel renders from the HTML. `channelId` is undefined when the
 * page could not resolve the channel on the server; the client view then
 * picks it from the list it fetches. `filter` narrows the prefetched page to
 * a day, an author and share statuses, under the key the daily updates or
 * pull requests channel builds from the same URL. Every prefetch is
 * best-effort, as in `loadChatRoute`.
 */
export async function ChatRoutePage({ channelId, filter }: { channelId?: string; filter?: ChatChannelFilter }) {
  const { queryClient, orgId, canFetch } = await loadChatRoute();

  if (canFetch && orgId) {
    await Promise.all([
      queryClient.prefetchQuery({
        queryKey: CHAT_KEYS.unread(orgId),
        queryFn: () => logFailure("unread", async () => requireChatData(await getChatUnread())),
      }),
      channelId
        ? queryClient.prefetchInfiniteQuery({
            queryKey: CHAT_KEYS.channel(orgId, channelId, null, filter),
            queryFn: () =>
              logFailure("channel", async () =>
                requireChatData(
                  await listChannelMessages({
                    channelId,
                    limit: CHAT_PAGE_SIZE,
                    day: filter?.day ?? undefined,
                    authorId: filter?.authorId ?? undefined,
                    pullRequestStatuses: filter?.pullRequestStatuses,
                  }),
                ),
              ),
            initialPageParam: chatFirstPageParam(),
          })
        : undefined,
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ChatView />
    </HydrationBoundary>
  );
}
