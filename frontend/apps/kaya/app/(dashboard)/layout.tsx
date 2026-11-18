import { Suspense, type ReactNode } from "react";

import { AppShell, IdentityGate } from "@/components/layout";
import { LiveUpdates } from "@/features/sessions/components";

/**
 * Every page of Kaya sits behind the identity gate: it signs the visitor in
 * and resolves the organization before the shell and the page render. The
 * live stream is opened once here, so it survives moving between pages.
 */
export default function DashboardLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <Suspense>
      <IdentityGate>
        <AppShell>
          <LiveUpdates />
          {children}
        </AppShell>
      </IdentityGate>
    </Suspense>
  );
}
