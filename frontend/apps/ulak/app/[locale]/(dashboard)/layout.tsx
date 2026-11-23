/**
 * Dashboard Layout
 *
 * The Ulak shell of every signed-in page except the organization list at `/`,
 * which renders the same shell itself because it also serves as the sign-in
 * gateway (see `app/[locale]/page.tsx`). The proxy sends a request without a
 * session to sign-in with a callbackUrl; this gate is the fallback for a
 * session that fails while the page renders.
 *
 * The layout server-prefetches the current-user ("me") query and hydrates it
 * around the shell, not in each page: the shell reads the query itself (see
 * AccountSessionSync), and a HydrationBoundary below a component that already
 * observes the query hydrates it only in an effect. Server and client then
 * render the page's first frame from different data. Hydrated above the shell,
 * the query holds the data before anything reads it, on both sides.
 *
 * Ulak serves only users who manage an active organization; any other user
 * is sent to `/no-access`. The API refuses each admin call on its own, so this
 * gate spares the user a screen of refusals and is not the security check.
 */

import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { redirect as nextRedirect } from "next/navigation";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { auth } from "@oba/shared/libs/auth";
import { AuthError } from "@oba/shared/libs/auth/types";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { USERS_KEYS, type usersApi } from "@oba/shared/features/users/hooks/use-get-me";
import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import { redirect, redirectToAuthApp } from "@oba/shared/libs/i18n/navigation";
import { Locale } from "@oba/shared/types";
import { UlakShell } from "@/components/layout/ulak-shell";

export const dynamic = "force-dynamic";

export default async function DashboardLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const locale = (await params).locale as Locale;
  const session = await auth();

  if (!session?.user || session.error === AuthError.REFRESH_TOKEN_ERROR) {
    return redirectToAuthApp(locale, "login");
  }

  // Pending users onboard in the account app, which returns them to the list.
  if (session.userStatus === "PENDING_ONBOARDING") {
    return nextRedirect(accountOnboardingUrl("/", appUrl("ulak")));
  }

  const queryClient = makeQueryClient();
  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  await prefetchMe(queryClient, session.accessToken, "dashboard-layout");
  // Only a prefetched user decides here. After a failed prefetch the page
  // renders, and UlakAccessGate decides once the client has the user.
  const me = queryClient.getQueryData<Awaited<ReturnType<typeof usersApi.getMe>>>(USERS_KEYS.me);
  if (me?.data && !canManageOrganizations(me.data)) {
    redirect({ href: "/no-access", locale });
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <UlakShell>{children}</UlakShell>
    </HydrationBoundary>
  );
}
