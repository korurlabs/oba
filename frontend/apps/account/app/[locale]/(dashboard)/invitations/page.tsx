import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { PendingInvitationsView } from "@/features/pending-invitations/components/pending-invitations-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("pendingInvitations");
  return {
    title: t("title"),
    description: t("subtitle"),
  };
}

/**
 * PendingInvitationsView reads the pending invitations from the current-user
 * ("me") query, which the dashboard layout prefetches and hydrates for the
 * whole shell (see server-prefetch-convention). The dashboard layout also
 * gates auth; this page adds neither.
 */
export default function InvitationsPage() {
  return (
    <Suspense>
      <PendingInvitationsView />
    </Suspense>
  );
}
