/**
 * Dashboard Layout
 *
 * Layout wrapper for authenticated dashboard pages, which it renders in
 * Kazan's app shell (`KazanAppShell`): the top bar, the menu and the access
 * gate around the page.
 *
 * Hosts the `@modal` parallel-route slot. The slot has no routes of its own:
 * `@modal/default.tsx` matches every url under `(dashboard)` and mounts
 * `TicketModalHost`, which opens the ticket dialog from the `t_ticket` search
 * param. The slot lives here rather than inside the board layout so a ticket
 * opened from the board-less `/dashboard/tickets` list — or a ticket that
 * lives on a DIFFERENT board than the page you are on — still renders as a
 * dialog over the list instead of navigating away.
 *
 * Hydrates the current user ("me") and hands the server's Kazan access answer
 * to the client, so the no-access panel and the queries of every dashboard
 * page know on the first render whether Kazan is open in the selected
 * organization, without waiting for the session and `/users/me` requests.
 */

import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { getServerKazanAccess, loadServerMe } from "@/components/layout/kazan-access-server";
import { KazanAccessProvider } from "@/components/layout/use-kazan-access";
import { KazanAppShell } from "@/components/layout/kazan-app-shell";
import { RealtimeProvider } from "@/features/realtime/realtime-provider";
import { auth } from "@oba/shared/libs/auth";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { redirectToAuthApp } from "@oba/shared/libs/i18n/navigation";
import { AuthError } from "@oba/shared/libs/auth/types";
import { Locale } from "@oba/shared/types";
import { redirect as nextRedirect } from "next/navigation";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { readRuntimeConfig } from "@oba/shared/lib/runtime-config";

export const dynamic = "force-dynamic";

export default async function DashboardLayout({
  children,
  modal,
  params,
}: {
  children: React.ReactNode;
  modal: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const locale = (await params).locale as Locale;
  const session = await auth();

  // Redirect to login if not authenticated (belt and suspenders with middleware)
  if (!session?.user) {
    return redirectToAuthApp(locale, "login");
  }

  // Redirect if session has a refresh token error
  if (session.error === AuthError.REFRESH_TOKEN_ERROR) {
    return redirectToAuthApp(locale, "login");
  }

  // Pending users onboard in the account app
  if (session.userStatus === "PENDING_ONBOARDING") {
    return nextRedirect(
      accountOnboardingUrl("/home", readRuntimeConfig().baseUrl || appUrl("kazan"))
    );
  }

  // The page shares this request's `/users/me` call (`loadServerMe` is cached
  // per request). A failed load leaves the cache empty; `useGetMe` then
  // fetches on mount and the server answer stays pending.
  const queryClient = makeQueryClient();
  const [me, kazanAccess] = await Promise.all([loadServerMe(), getServerKazanAccess()]);
  if (me) queryClient.setQueryData(USERS_KEYS.me, me);

  return (
    // `RealtimeProvider` wraps the shell so the menu's unread badges and every
    // page share the tab's one realtime session.
    <HydrationBoundary state={dehydrate(queryClient)}>
      <KazanAccessProvider serverAccess={kazanAccess}>
        <RealtimeProvider>
          <KazanAppShell>
            {children}
            {modal}
          </KazanAppShell>
        </RealtimeProvider>
      </KazanAccessProvider>
    </HydrationBoundary>
  );
}
