"use client";

import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { Session } from "next-auth";
import { useGetMe, USERS_KEYS, type usersApi } from "@oba/shared/features/users/hooks/use-get-me";
import { SenseyTable } from "@oba/shared/components/sensey-table";
import { EmptyState, LoadError } from "@oba/ui/sky/page-states";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { usePendingInvitationColumns } from "../hooks/use-pending-invitation-columns";
import { useDecideInvitation, useRemindLater } from "../hooks/use-pending-invitations";
import { InvitationDecision } from "../schemas";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { sanitizeCallbackUrl } from "@oba/shared/libs/auth/callback-url";
import type { InvitationResponse } from "@oba/shared/features/users/schema";

type MeResponse = Awaited<ReturnType<typeof usersApi.getMe>>;

const DEFAULT_CALLBACK_URL = "/";

/**
 * The PENDING invitations of a me-response, or undefined when no user has
 * been loaded (the current-user query failed before its first success).
 */
function getPendingInvitations(
  meResponse: MeResponse | undefined
): InvitationResponse[] | undefined {
  if (!meResponse?.success || !meResponse.data) return undefined;
  return (meResponse.data.pendingInvitations ?? []).filter((inv) => inv.status === "PENDING");
}

/**
 * True when the session still holds a pending invitation without a running
 * reminder. This is the condition on which the proxy sends the user back to
 * the invitations page.
 */
function hasInvitationAwaitingDecision(session: Session): boolean {
  const now = Date.now();
  const remindLater = session.remindLaterInvitations ?? {};
  return (session.pendingInvitationIds ?? []).some((invitationId) => {
    const remindAt = remindLater[invitationId];
    return !remindAt || remindAt <= now;
  });
}

export function PendingInvitationsView() {
  const t = useTranslations("pendingInvitations");
  const tErrors = useTranslations("errors");
  const { data: meResponse, isPending, isFetching, refetch } = useGetMe();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const router = useRouter();

  const loadedInvitations = getPendingInvitations(meResponse);
  const pendingInvitations: InvitationResponse[] = loadedInvitations ?? [];
  const pendingCount = pendingInvitations.length;
  const hasLoadFailed = !isPending && !loadedInvitations;

  const { mutateAsync: decideAsync, isPending: isDeciding } = useDecideInvitation();
  const { mutateAsync: remindLaterAsync, isPending: isReminding } = useRemindLater();

  const handleAfterDecision = useCallback(() => {
    const callbackUrl =
      sanitizeCallbackUrl(searchParams.get("callbackUrl")) ?? DEFAULT_CALLBACK_URL;
    // Another Oba app sends its users here with its own page as an absolute
    // callbackUrl; the locale-aware router only navigates within Account.
    if (/^https?:\/\//.test(callbackUrl)) {
      window.location.assign(callbackUrl);
      return;
    }
    router.push(callbackUrl);
  }, [router, searchParams]);

  const handleDecision = useCallback(
    async (invitationId: string, decision: InvitationDecision) => {
      try {
        await decideAsync({ invitationId, decision });
      } catch (error) {
        // rule-10-no-op -- reason: useDecideInvitation's onError already
        // shows the failure as a toast. This catch only skips the redirect
        // and stops the rejected promise from becoming an unhandled
        // rejection; the log keeps the stack reachable in the console.
        console.error("[PendingInvitationsView] decision failed:", error);
        return;
      }

      // The mutation awaited the me refetch, so the cache holds the list as
      // it is after this decision. If that refetch failed, fall back to the
      // count this handler was created with.
      const remaining = getPendingInvitations(queryClient.getQueryData<MeResponse>(USERS_KEYS.me));
      const remainingCount = remaining
        ? remaining.filter((inv) => inv.id !== invitationId).length
        : pendingCount - 1;

      if (remainingCount <= 0) handleAfterDecision();
    },
    [decideAsync, queryClient, pendingCount, handleAfterDecision]
  );

  const handleAccept = useCallback(
    (invitationId: string) => {
      void handleDecision(invitationId, InvitationDecision.ACCEPT);
    },
    [handleDecision]
  );

  const handleDecline = useCallback(
    (invitationId: string) => {
      void handleDecision(invitationId, InvitationDecision.DECLINE);
    },
    [handleDecision]
  );

  const handleRemindLater = useCallback(
    (invitationId: string) => {
      void (async () => {
        try {
          const session = await remindLaterAsync(invitationId);
          // Leaving while another invitation still waits for a decision
          // would only make the proxy send the user straight back here.
          if (!hasInvitationAwaitingDecision(session)) handleAfterDecision();
        } catch (error) {
          // rule-10-no-op -- reason: useRemindLater's onError already shows
          // the failure as a toast. This catch only skips the redirect and
          // stops the rejected promise from becoming an unhandled rejection.
          console.error("[PendingInvitationsView] remind later failed:", error);
        }
      })();
    },
    [remindLaterAsync, handleAfterDecision]
  );

  const columns = usePendingInvitationColumns({
    onAccept: handleAccept,
    onDecline: handleDecline,
    onRemindLater: handleRemindLater,
    isLoading: isDeciding || isReminding,
  });

  const hasNoInvitations = !isPending && !hasLoadFailed && pendingCount === 0;

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />

      {hasLoadFailed ? (
        <LoadError
          title={tErrors("somethingWentWrong")}
          message={tErrors("unexpectedError")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => {
            if (!isFetching) void refetch();
          }}
        />
      ) : hasNoInvitations ? (
        <EmptyState title={t("noInvitations")} description={t("noInvitationsDescription")} />
      ) : (
        <SenseyTable
          columns={columns}
          data={pendingInvitations}
          isLoading={isPending}
          emptyMessage={t("noInvitations")}
        />
      )}
    </div>
  );
}
