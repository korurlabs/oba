import type { QueryClient } from "@tanstack/react-query";

/**
 * Refreshes everything a user or machine change can alter: users carry machine
 * and session counts and their accounts, accounts carry their users and machine
 * counts, machines carry their user, and sessions name both.
 */
export async function invalidatePeopleQueries(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  await Promise.all(
    (["users", "accounts", "machines", "sessions"] as const).map((root) =>
      queryClient.invalidateQueries({ queryKey: [root, organizationId] })
    )
  );
}
