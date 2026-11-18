import { QueryClient } from "@tanstack/react-query";

/**
 * Invalidates all TanStack Query caches on organization switch.
 * All cached data was fetched with the previous org's X-Organization-ID
 * header, so everything is stale. Only mounted queries will refetch;
 * unmounted ones refetch lazily when their component mounts.
 *
 * Call it only once the new organization is rendered, because the refetch
 * targets every query that is enabled at the moment of the call.
 */
export const invalidateAllOrganizationQueries = async (queryClient: QueryClient) => {
  await queryClient.invalidateQueries();
};

/**
 * Marks every cached query stale on an organization switch, without a refetch.
 *
 * The switch handler runs before React renders the new session. At that
 * moment the queries of the old organization are still mounted and enabled,
 * but the API client already sends the new organization, or none for the
 * personal workspace. A refetch then would call organization-scoped endpoints
 * without an organization, or in one that does not grant the app. The refetch
 * is left to `OrganizationSyncProvider`, which runs after the render, when
 * each app's access gate has disabled or unmounted the queries the new
 * organization cannot serve.
 */
export const markAllOrganizationQueriesStale = async (queryClient: QueryClient) => {
  await queryClient.invalidateQueries({ refetchType: "none" });
};
