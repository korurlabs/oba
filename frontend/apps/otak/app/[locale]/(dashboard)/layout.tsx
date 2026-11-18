/**
 * Dashboard Layout
 *
 * The Otak shell of every signed-in page. The proxy sends a request without
 * a session to sign-in with a callbackUrl; this gate is the fallback for a
 * session that fails while the page renders.
 *
 * The layout server-prefetches the current-user ("me") query and hydrates it
 * around the shell, not in each page: the shell reads the query itself (see
 * AccountSessionSync), and a HydrationBoundary below a component that already
 * observes the query hydrates it only in an effect. Server and client then
 * render the page's first frame from different data. Hydrated above the shell,
 * the query holds the data before anything reads it, on both sides.
 *
 * The shell also gets the session's default organization: the client reads
 * its own session only after mount, and every Otak query key starts with the
 * organization, so without it the first render could not use what the pages
 * prefetched.
 */

import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { redirect as nextRedirect } from "next/navigation";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { auth } from "@oba/shared/libs/auth";
import { AuthError } from "@oba/shared/libs/auth/types";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { redirectToAuthApp } from "@oba/shared/libs/i18n/navigation";
import { Locale } from "@oba/shared/types";
import { OtakAppShell } from "@/components/layout/app-shell";

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

  // Pending users onboard in the account app, which returns them to the gateway.
  if (session.userStatus === "PENDING_ONBOARDING") {
    return nextRedirect(accountOnboardingUrl("/", appUrl("otak")));
  }

  const queryClient = makeQueryClient();
  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  await prefetchMe(queryClient, session.accessToken, "dashboard-layout");

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <OtakAppShell organizationId={session.user.defaultOrganizationId ?? null}>
        {children}
      </OtakAppShell>
    </HydrationBoundary>
  );
}
