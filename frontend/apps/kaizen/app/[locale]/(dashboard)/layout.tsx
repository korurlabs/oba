/**
 * Dashboard Layout
 *
 * Layout wrapper for authenticated dashboard pages.
 *
 * Hosts the `@modal` parallel-route slot. The slot has no routes of its own:
 * `@modal/default.tsx` matches every url under `(dashboard)` and mounts
 * `TicketModalHost`, which opens the ticket dialog from the `t_ticket` search
 * param. The slot lives here rather than inside the board layout so a ticket
 * opened from the board-less `/dashboard/tickets` list — or a ticket that
 * lives on a DIFFERENT board than the page you are on — still renders as a
 * dialog over the list instead of navigating away.
 *
 * Hydrates the current user ("me") and hands the server's Kaizen access answer
 * to the client, so the no-access panel and the queries of every dashboard
 * page know on the first render whether Kaizen is open in the selected
 * organization, without waiting for the session and `/users/me` requests.
 */

import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { AccountSessionSync } from "@/components/layout/account-session-sync";
import { AppAccessGate } from "@/components/layout/app-access-gate";
import { getServerKaizenAccess, loadServerMe } from "@/components/layout/kaizen-access-server";
import { KaizenAccessProvider } from "@/components/layout/use-kaizen-access";
import { ChatUnreadTitle } from "@/components/layout/chat-unread-title";
import { ContentSidebarTriggerBar } from "@/components/layout/content-sidebar-trigger";
import { Sidebar } from "@/components/layout/sidebar";
import { IncidentAlertWatcher } from "@/features/incidents/components/incident-alert-watcher";
import { ResizableSidebarProvider } from "@/components/layout/sidebar-context";
import { RealtimeProvider } from "@/features/realtime/realtime-provider";
import { SidebarInset } from "@oba/ui/components/sidebar";
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
      accountOnboardingUrl("/home", readRuntimeConfig().baseUrl || appUrl("kaizen"))
    );
  }

  // The page shares this request's `/users/me` call (`loadServerMe` is cached
  // per request). A failed load leaves the cache empty; `useGetMe` then
  // fetches on mount and the server answer stays pending.
  const queryClient = makeQueryClient();
  const [me, kaizenAccess] = await Promise.all([loadServerMe(), getServerKaizenAccess()]);
  if (me) queryClient.setQueryData(USERS_KEYS.me, me);

  return (
    // No global header: `Sidebar` and `SidebarInset` are the direct children
    // of the provider, which is what makes the sidebar run the full height of
    // the viewport and lets page content (the board tabs row) sit flush
    // against the top edge. `RealtimeProvider` renders no element, so they
    // stay DOM siblings (the inset's `peer-data-*` styles need that); it wraps
    // them so the sidebar's unread badge and every page share the tab's one
    // realtime session.
    //
    // The sidebar is `variant="inset"`, so the provider paints the page ground
    // with `--sidebar` and this `SidebarInset` is the rounded `--background`
    // panel floating on it (margin/radius/shadow come from the primitive; the
    // border is added here so the panel edge reads on a low-contrast ground).
    // Height is `h-svh` minus the inset margin so the panel never overflows the
    // viewport.
    //
    // `AccountSessionSync` renders nothing. It sits beside the sidebar, not in
    // it, so the session side effects run at every viewport width (on a phone
    // the sidebar is a closed sheet and its content is unmounted).
    // `IncidentAlertWatcher` sits beside it for the same reason: the red
    // alert for open P1 and P2 incidents must reach every dashboard page.
    // So does `ChatUnreadTitle`, which puts the unread chat count in front of
    // the tab title on every page.
    //
    // The desktop sidebar width is the viewer's own choice, made by dragging
    // the sidebar's edge and remembered in this browser, up to 20rem so
    // nested, indented board names stay readable. The provider sets
    // `--sidebar-width`, which reaches every `width: var(--sidebar-width)`
    // consumer of the primitive; the icon rail and the mobile sheet set their
    // own widths and keep them.
    <HydrationBoundary state={dehydrate(queryClient)}>
      <KaizenAccessProvider serverAccess={kaizenAccess}>
        <ResizableSidebarProvider>
          <RealtimeProvider>
            <Sidebar />
            <AccountSessionSync />
            <IncidentAlertWatcher />
            <ChatUnreadTitle />
            <SidebarInset className="border-border h-svh min-w-0 overflow-hidden md:peer-data-[variant=inset]:h-[calc(100svh-1rem)] md:peer-data-[variant=inset]:border">
              <ContentSidebarTriggerBar />
              <div className="min-h-0 flex-1 overflow-auto">
                {/* One gate holds the page and the ticket dialog, so an
                organization without Kaizen shows one panel and no dialog over
                it. The dialog content renders through a portal, so its place
                inside the scroll area does not clip it. */}
                <AppAccessGate>
                  {children}
                  {modal}
                </AppAccessGate>
              </div>
            </SidebarInset>
          </RealtimeProvider>
        </ResizableSidebarProvider>
      </KaizenAccessProvider>
    </HydrationBoundary>
  );
}
