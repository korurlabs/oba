import type { UserMe } from "@oba/shared/features/users/schema";

import { useMe } from "@/hooks/queries/users";
import { useActiveOrganizationId } from "@/lib/organizationStore";

/**
 * The signed-in user's role in the active organization (`OWNER`, `ADMIN`,
 * `MEMBER` or `GUEST`), or null while the profile loads. Screens hide the
 * controls of writes the role may not make; the server enforces the same rule.
 */
export function useOrganizationRole(): string | null {
  const orgId: string | null = useActiveOrganizationId();
  const me: UserMe | undefined = useMe(true).data;
  return me?.organizations?.find((organization) => organization.id === orgId)?.role ?? null;
}
