import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { useMemo } from "react";
import { z } from "zod";

import {
  CHAT_MAX_PAGES,
  chatFirstPageParam,
  chatKeys,
  chatNarrowedListKeys,
  chatNewerPageParam,
  chatOlderPageParam,
  flattenChatMessages,
  patchChatMessage,
} from "@/hooks/queries/chatCache";
import { keepPreviousWithin } from "@/hooks/queries/placeholder";
import { api, isApiError, readData, readPage } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_PAGE_SIZE,
  chatMemberResponseSchema,
  chatMessagePageSchema,
  type ChatMemberResponse,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";
import {
  PULL_REQUEST_FIRST_PAGE,
  PullRequestShareStatus,
  githubContributorResponseSchema,
  githubPullRequestPageResponseSchema,
  githubPullRequestResponseSchema,
  githubRepositoryResponseSchema,
  pullRequestShareResponseSchema,
  type GithubContributorResponse,
  type GithubPullRequestPageResponse,
  type GithubPullRequestResponse,
  type GithubPullRequestState,
  type GithubRepositoryResponse,
  type PullRequestChannelFilter,
  type PullRequestShareCreateRequest,
  type PullRequestShareResponse,
  type PullRequestShareUpdateRequest,
} from "@/lib/schemas/pullRequests";

/*
 * Pull request shares, the pull requests channel narrowed by status and
 * person, and the GitHub picker. Every body is checked against its schema; a
 * body that does not match throws, which a query shows as its error state.
 * Failed writes are toasted by the app's error listener, except a 422
 * validation, which the share form shows under its fields.
 */

// ── Keys ────────────────────────────────────────────────────────────

/**
 * Query keys of pull requests, scoped by organization: GitHub answers only
 * for the organization whose App is installed, and the channel belongs to
 * one organization. A narrowed channel list is kept apart from the chat's
 * own channel keys, because the chat's cache edits would add every new
 * message to it whether it matches the filter or not.
 */
export const pullRequestKeys = {
  all: (orgId: string) => ["pull-requests", orgId] as const,
  /** Every narrowed list of the pull requests channel. */
  channelsAll: (orgId: string) => chatNarrowedListKeys.pullRequests(orgId),
  /** The channel narrowed to statuses (in enum order) and a person. */
  channel: (orgId: string, channelId: string, filter: PullRequestChannelFilter) =>
    [
      ...pullRequestKeys.channelsAll(orgId),
      channelId,
      filter.statuses.join(","),
      filter.personId,
    ] as const,
  authorsAll: (orgId: string) => [...pullRequestKeys.all(orgId), "authors"] as const,
  /** The people who wrote in the channel, the Person filter's options. */
  authors: (orgId: string, channelId: string) =>
    [...pullRequestKeys.authorsAll(orgId), channelId] as const,
  /** How many shares are not merged yet, for the channel's subtitle. */
  openCount: (orgId: string) => [...pullRequestKeys.all(orgId), "open-count"] as const,
  /** Every share detail. */
  sharesAll: (orgId: string) => [...pullRequestKeys.all(orgId), "share"] as const,
  share: (orgId: string, shareId: string) =>
    [...pullRequestKeys.sharesAll(orgId), shareId] as const,
  repositories: (orgId: string) => [...pullRequestKeys.all(orgId), "repositories"] as const,
  /** Everything read from one GitHub repository. */
  githubRepository: (orgId: string, githubRepositoryId: number) =>
    [...pullRequestKeys.all(orgId), "github", githubRepositoryId] as const,
  /** One picker search; `states` and `authors` come sorted, so the order of ticking reads one key. */
  githubPullRequests: (
    orgId: string,
    githubRepositoryId: number,
    search: string,
    states: readonly string[],
    authors: readonly string[]
  ) =>
    [
      ...pullRequestKeys.githubRepository(orgId, githubRepositoryId),
      "list",
      search,
      states.join(","),
      authors.join(","),
    ] as const,
  githubContributors: (orgId: string, githubRepositoryId: number) =>
    [...pullRequestKeys.githubRepository(orgId, githubRepositoryId), "contributors"] as const,
  githubPullRequest: (orgId: string, githubRepositoryId: number, number: number) =>
    [...pullRequestKeys.githubRepository(orgId, githubRepositoryId), "detail", number] as const,
};

/** The server's error code of a failed request, for `pullRequestErrorKey`. */
export function pullRequestErrorCode(error: unknown): string | undefined {
  return isApiError(error) ? error.code : undefined;
}

/** True when the filter narrows the channel. */
export function isPullRequestFilterActive(filter: PullRequestChannelFilter): boolean {
  return filter.statuses.length > 0 || filter.personId !== null;
}

/** The statuses that count as an open share: every status but merged. */
export const OPEN_SHARE_STATUSES: readonly PullRequestShareStatus[] = [
  PullRequestShareStatus.DRAFT,
  PullRequestShareStatus.OPEN,
  PullRequestShareStatus.IN_REVIEW,
  PullRequestShareStatus.DISCUSS,
];

// ── Requests ────────────────────────────────────────────────────────

/**
 * A page of the channel narrowed by `filter`, oldest first, with the chat's
 * cursors. The server binds the statuses comma-separated; with statuses
 * chosen it drops plain messages.
 */
export async function listFilteredChannelMessages(
  channelId: string,
  filter: PullRequestChannelFilter,
  cursor: ChatMessageCursor
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.messages, {
    params: {
      ...cursor,
      channelId,
      limit: CHAT_PAGE_SIZE,
      authorId: filter.personId ?? undefined,
      pullRequestStatuses: filter.statuses.length > 0 ? filter.statuses.join(",") : undefined,
    },
  });
  return readData(chatMessagePageSchema, response.data);
}

/** The members who have a live message in the channel. */
export async function listChannelAuthors(channelId: string): Promise<ChatMemberResponse[]> {
  const response = await api.get(API_ROUTES.chat.members, {
    params: { channelId, authorsOnly: true },
  });
  return readData(z.array(chatMemberResponseSchema), response.data);
}

/** The number of shares not merged yet, read from the total of a one-row page. */
export async function countOpenShares(): Promise<number> {
  const response = await api.get(API_ROUTES.pullRequests.shares, {
    params: { page: 1, amount: 1, statuses: OPEN_SHARE_STATUSES.join(",") },
  });
  const page = readPage(pullRequestShareResponseSchema, response.data);
  return page.pagination?.totalElements ?? page.items.length;
}

export async function getPullRequestShare(shareId: string): Promise<PullRequestShareResponse> {
  const response = await api.get(API_ROUTES.pullRequests.share(shareId));
  return readData(pullRequestShareResponseSchema, response.data);
}

/** Shares the pull request and posts its card in the pull requests channel. */
export async function createPullRequestShare(
  data: PullRequestShareCreateRequest
): Promise<PullRequestShareResponse> {
  const response = await api.post(API_ROUTES.pullRequests.shares, data);
  return readData(pullRequestShareResponseSchema, response.data);
}

/** The author's edit of status, note, reviewers and related tickets. */
export async function editPullRequestShare(
  shareId: string,
  data: PullRequestShareUpdateRequest
): Promise<PullRequestShareResponse> {
  const response = await api.put(API_ROUTES.pullRequests.share(shareId), data);
  return readData(pullRequestShareResponseSchema, response.data);
}

/** Removes the share and its card; the author only. The answer has no data. */
export async function deletePullRequestShare(shareId: string): Promise<void> {
  await api.delete(API_ROUTES.pullRequests.share(shareId));
}

/** The reader's approval as a reviewer; idempotent. */
export async function approvePullRequestShare(shareId: string): Promise<PullRequestShareResponse> {
  const response = await api.put(API_ROUTES.pullRequests.approval(shareId));
  return readData(pullRequestShareResponseSchema, response.data);
}

/** Withdraws the reader's approval; idempotent. */
export async function withdrawPullRequestShareApproval(
  shareId: string
): Promise<PullRequestShareResponse> {
  const response = await api.delete(API_ROUTES.pullRequests.approval(shareId));
  return readData(pullRequestShareResponseSchema, response.data);
}

export async function listGithubRepositories(): Promise<GithubRepositoryResponse[]> {
  const response = await api.get(API_ROUTES.pullRequests.githubRepositories);
  return readData(z.array(githubRepositoryResponseSchema), response.data);
}

/** What one picker page asks GitHub for; empty lists do not narrow. */
export interface GithubPullRequestSearch {
  search: string;
  states: readonly GithubPullRequestState[];
  authors: readonly string[];
}

/**
 * One page of a repository's pull requests, last updated first. "#379" or
 * "379" finds that number and ignores the states and authors. Lists go out
 * comma-separated, which is how the server binds them.
 */
export async function listGithubPullRequests(
  githubRepositoryId: number,
  query: GithubPullRequestSearch,
  page: number
): Promise<GithubPullRequestPageResponse> {
  const response = await api.get(
    API_ROUTES.pullRequests.githubPullRequests(String(githubRepositoryId)),
    {
      params: {
        page,
        search: query.search || undefined,
        states: query.states.length > 0 ? query.states.join(",") : undefined,
        authors: query.authors.length > 0 ? query.authors.join(",") : undefined,
      },
    }
  );
  return readData(githubPullRequestPageResponseSchema, response.data);
}

/** Logins of a repository's GitHub contributors, at most 100; the Author filter's options. */
export async function listGithubContributors(
  githubRepositoryId: number
): Promise<GithubContributorResponse[]> {
  const response = await api.get(
    API_ROUTES.pullRequests.githubContributors(String(githubRepositoryId))
  );
  return readData(z.array(githubContributorResponseSchema), response.data);
}

/** One pull request with its branches and ticket key. */
export async function getGithubPullRequest(
  githubRepositoryId: number,
  number: number
): Promise<GithubPullRequestResponse> {
  const response = await api.get(
    API_ROUTES.pullRequests.githubPullRequest(String(githubRepositoryId), number)
  );
  return readData(githubPullRequestResponseSchema, response.data);
}

// ── Queries ─────────────────────────────────────────────────────────

/**
 * The pull requests channel narrowed by `filter`, oldest first, paged both
 * ways as the chat does. Disabled while the filter narrows nothing: the
 * screen then shows the chat's own channel list.
 */
export function usePullRequestChannelMessages(
  channelId: string | null,
  filter: PullRequestChannelFilter
) {
  const orgId: string | null = useActiveOrganizationId();
  const query = useInfiniteQuery({
    queryKey: pullRequestKeys.channel(orgId ?? "", channelId ?? "", filter),
    queryFn: ({ pageParam }: { pageParam: ChatMessageCursor }) =>
      listFilteredChannelMessages(channelId as string, filter, pageParam),
    initialPageParam: chatFirstPageParam(),
    getPreviousPageParam: (firstPage: ChatMessagePage) => chatOlderPageParam(firstPage),
    getNextPageParam: (lastPage: ChatMessagePage) => chatNewerPageParam(lastPage),
    maxPages: CHAT_MAX_PAGES,
    enabled: !!orgId && !!channelId && isPullRequestFilterActive(filter),
  });
  const messages: ChatMessageResponse[] = useMemo(
    () => flattenChatMessages(query.data),
    [query.data]
  );
  const lastPage: ChatMessagePage | undefined = query.data?.pages[query.data.pages.length - 1];
  const atNewest: boolean = !!lastPage && !lastPage.hasNewer;
  return { ...query, messages, atNewest };
}

/** The Person filter's options: the members who wrote in the channel. */
export function usePullRequestChannelAuthors(channelId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatMemberResponse[]>({
    queryKey: pullRequestKeys.authors(orgId ?? "", channelId ?? ""),
    queryFn: () => listChannelAuthors(channelId as string),
    enabled: !!orgId && !!channelId,
  });
}

/** The count of shares not merged yet; the subtitle leaves it out while unknown. */
export function useOpenPullRequestShareCount() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<number>({
    queryKey: pullRequestKeys.openCount(orgId ?? ""),
    queryFn: () =>
      countOpenShares().catch((error: unknown) => {
        // A subtitle that fails to load is left off, not shown as an error screen.
        console.warn(
          "[pull-requests] open share count failed",
          error instanceof Error ? error.message : error
        );
        throw error;
      }),
    enabled: !!orgId,
  });
}

/** One share, for the edit form; disabled until the share is known. */
export function usePullRequestShare(shareId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<PullRequestShareResponse>({
    queryKey: pullRequestKeys.share(orgId ?? "", shareId ?? ""),
    queryFn: () => getPullRequestShare(shareId as string),
    enabled: !!orgId && !!shareId,
  });
}

/** The GitHub App installation's repositories. */
export function useGithubRepositories(enabled: boolean = true) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<GithubRepositoryResponse[]>({
    queryKey: pullRequestKeys.repositories(orgId ?? ""),
    queryFn: listGithubRepositories,
    enabled: !!orgId && enabled,
  });
}

/** Picker pages stay this long; GitHub search is rate limited, so they are not refetched sooner. */
const GITHUB_STALE_MS = 60_000;

/**
 * The picker list of one repository for one search, 30 rows a page; the
 * next page loads while `hasMore`. Disabled until a repository is chosen.
 * A new search or filter keeps the rows on screen while it loads, but a new
 * repository shows the loading state: a pick is built from the chosen
 * repository and the tapped row's number, so another repository's row would
 * pick the wrong pull request.
 */
export function useGithubPullRequests(
  githubRepositoryId: number | null,
  query: GithubPullRequestSearch
) {
  const orgId: string | null = useActiveOrganizationId();
  const states: string[] = [...query.states].sort();
  const authors: string[] = [...query.authors].sort();
  return useInfiniteQuery({
    queryKey: pullRequestKeys.githubPullRequests(
      orgId ?? "",
      githubRepositoryId ?? 0,
      query.search,
      states,
      authors
    ),
    queryFn: ({ pageParam }: { pageParam: number }) =>
      listGithubPullRequests(githubRepositoryId as number, query, pageParam),
    initialPageParam: PULL_REQUEST_FIRST_PAGE,
    getNextPageParam: (lastPage: GithubPullRequestPageResponse) =>
      lastPage.hasMore ? lastPage.page + 1 : undefined,
    enabled: !!orgId && githubRepositoryId !== null,
    staleTime: GITHUB_STALE_MS,
    placeholderData: keepPreviousWithin(
      pullRequestKeys.githubRepository(orgId ?? "", githubRepositoryId ?? 0)
    ),
  });
}

/** A repository's contributors, the picker's Author options. */
export function useGithubContributors(githubRepositoryId: number | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<GithubContributorResponse[]>({
    queryKey: pullRequestKeys.githubContributors(orgId ?? "", githubRepositoryId ?? 0),
    queryFn: () => listGithubContributors(githubRepositoryId as number),
    enabled: !!orgId && githubRepositoryId !== null,
    staleTime: GITHUB_STALE_MS,
  });
}

/** The picked pull request with its branches, for the preview card. */
export function useGithubPullRequest(githubRepositoryId: number | null, number: number | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<GithubPullRequestResponse>({
    queryKey: pullRequestKeys.githubPullRequest(orgId ?? "", githubRepositoryId ?? 0, number ?? 0),
    queryFn: () => getGithubPullRequest(githubRepositoryId as number, number as number),
    enabled: !!orgId && githubRepositoryId !== null && number !== null,
    staleTime: GITHUB_STALE_MS,
  });
}

// ── Mutations ───────────────────────────────────────────────────────

function requireOrgId(orgId: string | null): string {
  if (!orgId) throw new Error("No active organization for pull requests");
  return orgId;
}

/**
 * Every share is also a message of the pull requests channel. The realtime
 * signal refreshes the chat's list, but only while the socket is up; this
 * keeps the writer's own channel, its narrowed lists and the open count
 * current without it.
 */
async function refreshShareViews(queryClient: QueryClient, orgId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chatKeys.channelMessagesAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: pullRequestKeys.channelsAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: pullRequestKeys.openCount(orgId) }),
    queryClient.invalidateQueries({ queryKey: pullRequestKeys.authorsAll(orgId) }),
  ]);
}

/**
 * The answer replaces the share in every loaded copy of its message, so the
 * card changes before the refetch or the realtime signal arrives.
 */
function storeShare(
  queryClient: QueryClient,
  orgId: string,
  share: PullRequestShareResponse,
  messageId: string | null
): void {
  queryClient.setQueryData(pullRequestKeys.share(orgId, share.id), share);
  if (!messageId) return;
  const withShare = (message: ChatMessageResponse): ChatMessageResponse => ({
    ...message,
    pullRequestShare: share,
  });
  patchChatMessage(queryClient, orgId, messageId, withShare);
}

/** A share action on a card: the share and the chat message that carries it. */
export interface PullRequestShareTarget {
  shareId: string;
  messageId: string | null;
}

/** Shares a pull request; the form keeps its values, since it closes only on success. */
export function useCreatePullRequestShare() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (data: PullRequestShareCreateRequest) => createPullRequestShare(data),
    onSuccess: async (share: PullRequestShareResponse) => {
      const organizationId: string = requireOrgId(orgId);
      storeShare(queryClient, organizationId, share, null);
      await refreshShareViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal; a validation error goes under the fields.
      console.warn("[pull-requests] share failed", error.message);
    },
  });
}

/** The author's edit of status, note, reviewers and related tickets. */
export function useEditPullRequestShare() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({
      shareId,
      data,
    }: {
      shareId: string;
      data: PullRequestShareUpdateRequest;
      messageId: string | null;
    }) => editPullRequestShare(shareId, data),
    onSuccess: async (share: PullRequestShareResponse, { messageId }) => {
      const organizationId: string = requireOrgId(orgId);
      storeShare(queryClient, organizationId, share, messageId);
      await refreshShareViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal; a validation error goes under the fields.
      console.warn("[pull-requests] edit failed", error.message);
    },
  });
}

/** Removes the author's share and its card. */
export function useDeletePullRequestShare() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ shareId }: PullRequestShareTarget) => deletePullRequestShare(shareId),
    onSuccess: async (_: void, { shareId }: PullRequestShareTarget) => {
      const organizationId: string = requireOrgId(orgId);
      // Dropped first, so an open edit form does not refetch a 404.
      queryClient.removeQueries({ queryKey: pullRequestKeys.share(organizationId, shareId) });
      await refreshShareViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal.
      console.warn("[pull-requests] delete failed", error.message);
    },
  });
}

/** The reader approves as a reviewer, or with `approve` false withdraws the approval. */
export function useReviewPullRequestShare() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ shareId, approve }: PullRequestShareTarget & { approve: boolean }) =>
      approve ? approvePullRequestShare(shareId) : withdrawPullRequestShareApproval(shareId),
    onSuccess: async (share: PullRequestShareResponse, { messageId }) => {
      const organizationId: string = requireOrgId(orgId);
      storeShare(queryClient, organizationId, share, messageId);
      await refreshShareViews(queryClient, organizationId);
    },
    onError: (error: Error) => {
      // The app's error listener toasts the refusal.
      console.warn("[pull-requests] review failed", error.message);
    },
  });
}
