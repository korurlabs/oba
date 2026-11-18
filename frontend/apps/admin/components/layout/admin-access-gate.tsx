"use client";

import { useEffect } from "react";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";

/**
 * Sends a user who manages no active organization to `/no-access` and renders
 * nothing. The server layouts redirect such a user already when they could
 * load the user; this gate covers the page that rendered after a failed load,
 * and a user who loses their last managing role while Admin is open.
 *
 * Only a successful load decides: while the user loads, or after a failed
 * fetch, the page stays, so a short API failure never locks out a manager.
 */
export function AdminAccessGate() {
  const router = useRouter();
  const { data: meResponse, isSuccess } = useGetMe();
  const managesNothing =
    isSuccess && !!meResponse?.data && !canManageOrganizations(meResponse.data);

  useEffect(() => {
    if (managesNothing) {
      router.replace("/no-access");
    }
  }, [managesNothing, router]);

  return null;
}
