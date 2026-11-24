"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Check, Copy, ExternalLink, GitBranch, Pencil, Trash2, Undo2 } from "lucide-react";
import type { ApiResponse } from "@oba/shared/schema";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import { useChatCopy } from "@/features/chat/hooks/use-chat-copy";
import { patchChatMessage, useChatOrganizationId } from "@/features/chat/hooks/use-chat-messages";
import { TicketKeyLink } from "@/features/tickets/components/ticket-key-link";
import {
  useApprovePullRequestShare,
  useWithdrawPullRequestShareApproval,
} from "../hooks/use-pull-request-shares";
import { PullRequestShareStatus, type PullRequestShareResponse } from "../schemas";
import { DeletePullRequestShareDialog } from "./delete-pull-request-share-dialog";
import { pullRequestShareEditPath } from "./pull-request-share-paths";
import { RelatedTicketRow } from "./related-ticket-row";
import { ReviewerChip } from "./reviewer-chip";

/**
 * The team's statuses as outline chips, told apart by their text. Only two
 * carry an accent: Merged in primary, as the outcome the channel works
 * towards, and Discuss in red, because it asks the team to talk before the
 * pull request moves on. Draft is muted, as work not yet asked to be read.
 */
const STATUS_CLASSES: Record<PullRequestShareStatus, string> = {
  [PullRequestShareStatus.DRAFT]: "text-muted-foreground",
  [PullRequestShareStatus.OPEN]: "",
  [PullRequestShareStatus.IN_REVIEW]: "",
  [PullRequestShareStatus.MERGED]: "border-primary/50 text-primary",
  [PullRequestShareStatus.DISCUSS]: "border-red-600/40 text-red-700 dark:border-red-400/50 dark:text-red-400",
};

/** The status the team set on a share, as a labelled chip. */
export function PullRequestShareStatusBadge({
  status,
  className,
}: {
  status: PullRequestShareStatus;
  className?: string;
}) {
  const t = useTranslations("pullRequests.statuses");
  return <span className={cn("kazan-grid-chip", STATUS_CLASSES[status], className)}>{t(status)}</span>;
}

interface PullRequestShareContentProps {
  share: PullRequestShareResponse;
  /** The chat message that carries the share; an approval patches its loaded copies at once. */
  messageId: string;
  /** Who shared the pull request, as the message's header names them. */
  authorName: string;
  /** The reader. Edit and Delete only for the author, Approve and Withdraw only for a chosen reviewer. */
  currentUserId: string | undefined;
}

/**
 * The content of a shared pull request's bubble in the PR channel: the facts
 * GitHub gave at share time (ticket key and title, repository and number,
 * link, GitHub author, branches), the team's status, note and reviewers, and
 * the card's own actions last. Every text from GitHub or the author renders
 * as plain text; links open a new tab without access to this page.
 */
export function PullRequestShareContent({
  share,
  messageId,
  authorName,
  currentUserId,
}: PullRequestShareContentProps) {
  const t = useTranslations("pullRequests.card");
  const copy = useChatCopy();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const approval = useShareApproval(messageId);

  const isAuthor = !!currentUserId && currentUserId === share.authorId;
  const myReview = currentUserId ? share.reviewers.find((reviewer) => reviewer.userId === currentUserId) : undefined;
  const actionClass = "h-7 gap-1.5 px-2 text-xs";

  return (
    <div role="group" aria-label={t("label", { name: authorName })} className="space-y-3">
      <div className="kazan-grid-frame space-y-2.5">
        <div className="space-y-1">
          <p className="text-foreground font-medium break-words">
            <ShareTicketKey share={share} />
            {share.title}
          </p>
          <div className="text-muted-foreground flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
            <span className="break-all">
              {share.repositoryFullName} #{share.number}
            </span>
            <span>{t("byGithubAuthor", { login: share.githubAuthorLogin })}</span>
            <a
              href={share.url}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary inline-flex items-center gap-1 font-medium hover:underline"
            >
              {t("openInGithub")}
              <ExternalLink className="h-3 w-3" aria-hidden />
            </a>
          </div>
          <p className="text-muted-foreground flex min-w-0 items-center gap-1 text-xs">
            <GitBranch className="h-3 w-3 shrink-0" aria-hidden />
            <span className="break-all">
              {share.headRef} -&gt; {share.baseRef}
            </span>
          </p>
        </div>

        <div className="kazan-grid-divider flex flex-wrap items-start gap-2">
          <span className="kazan-grid-label inline-flex items-center gap-1 pt-0.5">{t("status")}</span>
          <PullRequestShareStatusBadge status={share.status} />
          {share.note && <span className="text-foreground min-w-0 break-words whitespace-pre-wrap">{share.note}</span>}
        </div>

        {share.relatedTickets.length > 0 && (
          <div className="kazan-grid-divider space-y-1">
            <span className="kazan-grid-label flex items-center gap-1">{t("relatedTickets")}</span>
            <ul className="space-y-1" aria-label={t("relatedTickets")}>
              {share.relatedTickets.map((ticket) => (
                <li key={ticket.ticketId}>
                  <RelatedTicketRow ticket={ticket} />
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="kazan-grid-divider flex flex-wrap items-center gap-2">
          <span className="kazan-grid-label inline-flex items-center gap-1">{t("reviewers")}</span>
          {share.reviewers.length > 0 ? (
            <ul className="flex flex-wrap items-center gap-2" aria-label={t("reviewers")}>
              {share.reviewers.map((reviewer) => (
                <li key={reviewer.userId}>
                  <ReviewerChip reviewer={reviewer} />
                </li>
              ))}
            </ul>
          ) : (
            <span className="text-muted-foreground text-xs">{t("noReviewers")}</span>
          )}
        </div>
      </div>

      <div className="border-border/60 flex flex-wrap items-center gap-1 border-t pt-2">
        <Button variant="ghost" size="sm" className={actionClass} onClick={() => void copy(share.summaryText, "text")}>
          <Copy className="h-3.5 w-3.5" aria-hidden />
          {t("copy")}
        </Button>
        {myReview &&
          (myReview.approvedAt ? (
            <Button
              variant="ghost"
              size="sm"
              className={actionClass}
              disabled={approval.isPending}
              onClick={() => approval.withdraw(share.id)}
            >
              <Undo2 className="h-3.5 w-3.5" aria-hidden />
              {t("withdraw")}
            </Button>
          ) : (
            <Button
              variant="outline"
              size="sm"
              className={cn(actionClass, "border-primary/50 text-primary hover:text-primary")}
              disabled={approval.isPending}
              onClick={() => approval.approve(share.id)}
            >
              <Check className="h-3.5 w-3.5" aria-hidden />
              {t("approve")}
            </Button>
          ))}
        {isAuthor && (
          <>
            <Button variant="ghost" size="sm" className={actionClass} asChild>
              <Link href={pullRequestShareEditPath(share.id)}>
                <Pencil className="h-3.5 w-3.5" aria-hidden />
                {t("edit")}
              </Link>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className={cn(actionClass, "text-destructive hover:text-destructive")}
              onClick={() => setDeleteOpen(true)}
            >
              <Trash2 className="h-3.5 w-3.5" aria-hidden />
              {t("delete")}
            </Button>
          </>
        )}
      </div>

      {isAuthor && <DeletePullRequestShareDialog shareId={share.id} open={deleteOpen} onOpenChange={setDeleteOpen} />}
    </div>
  );
}

/** "KEY: " before the title; linked to the ticket only when the server says this reader can open it. */
function ShareTicketKey({ share }: { share: PullRequestShareResponse }) {
  if (!share.ticketKey) return null;
  const base = "font-mono text-xs font-semibold whitespace-nowrap";
  const linked = share.linkedTicket;
  return (
    <>
      {linked ? (
        <TicketKeyLink
          ticketKey={linked.ticketKey}
          className={cn(
            base,
            "text-primary focus-visible:ring-ring rounded-sm underline-offset-2 hover:underline focus-visible:ring-2 focus-visible:outline-none",
          )}
        >
          {share.ticketKey}
        </TicketKeyLink>
      ) : (
        <span className={cn(base, "text-foreground")}>{share.ticketKey}</span>
      )}
      <span>: </span>
    </>
  );
}

/**
 * Approve and withdraw for the reader as a reviewer. The answer replaces the
 * share in every loaded copy of its message, so the chip changes before the
 * channel's refetch or the realtime signal arrives.
 */
function useShareApproval(messageId: string) {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const approveMutation = useApprovePullRequestShare();
  const withdrawMutation = useWithdrawPullRequestShareApproval();

  const patch = (response: ApiResponse<PullRequestShareResponse>) => {
    const share = response.data;
    if (!orgId || !share) return;
    patchChatMessage(queryClient, orgId, messageId, (message) => ({ ...message, pullRequestShare: share }));
  };

  return {
    isPending: approveMutation.isPending || withdrawMutation.isPending,
    approve: (shareId: string) => approveMutation.mutate(shareId, { onSuccess: patch }),
    withdraw: (shareId: string) => withdrawMutation.mutate(shareId, { onSuccess: patch }),
  };
}
