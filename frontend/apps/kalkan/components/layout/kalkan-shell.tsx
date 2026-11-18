import { AccountSessionSync } from "./account-session-sync";
import { KalkanHeader } from "./kalkan-header";
import { KalkanSidebar } from "./kalkan-sidebar";
import { OrganizationGate } from "./organization-gate";
import { OrganizationIdProvider } from "@/hooks/use-organization-id";

/**
 * Frame of every signed-in Kalkan page: the header on top, and below it the
 * page sidebar beside the page content. `organizationId` is the default
 * organization of the session the server rendered with; see
 * `useOrganizationId`. The home page at `/` renders the shell without
 * content of its own.
 */
export function KalkanShell({
  organizationId,
  children,
}: {
  organizationId: string | null;
  children?: React.ReactNode;
}) {
  return (
    <OrganizationIdProvider organizationId={organizationId}>
      <div className="bg-background flex min-h-screen flex-col">
        <KalkanHeader />
        <AccountSessionSync />
        <div className="flex min-h-0 flex-1 flex-col md:flex-row">
          <OrganizationGate>
            <KalkanSidebar />
            <main className="min-h-0 min-w-0 flex-1">{children}</main>
          </OrganizationGate>
        </div>
      </div>
    </OrganizationIdProvider>
  );
}
