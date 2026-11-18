/**
 * Query keys of the organization members. Kept out of the "use client" hook
 * file so a server page can prefetch under the exact keys the hook reads.
 */
export const MEMBER_KEYS = {
  all: ["organizationMembers"] as const,
  list: (organizationId: string) => [...MEMBER_KEYS.all, "list", organizationId] as const,
};
