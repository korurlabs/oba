"use client";

import { useTranslations } from "next-intl";
import { ExternalLink, GitBranch, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  PullRequestShareStatus,
  type GithubPullRequestState,
  type PullRequestShareFormValues,
  type PullRequestShareTicketResponse,
} from "../schemas";
import { GithubPullRequestStateBadge } from "./github-pull-request-state-badge";
import { PickerError } from "./pull-request-picker";
import { RelatedTicketRow } from "./related-ticket-row";

/**
 * The status labels of the posted text. The text is the same for every
 * reader and is pasted into Teams as it is, so it is not translated; these
 * match the server's renderer.
 */
const SUMMARY_STATUS_LABELS: Record<PullRequestShareStatus, string> = {
  [PullRequestShareStatus.DRAFT]: "Draft",
  [PullRequestShareStatus.OPEN]: "Open",
  [PullRequestShareStatus.IN_REVIEW]: "In Review",
  [PullRequestShareStatus.MERGED]: "Merged",
  [PullRequestShareStatus.DISCUSS]: "Discuss",
};

/** What GitHub says about the pull request; the share cannot change it. */
export interface PullRequestFacts {
  repositoryFullName: string;
  number: number;
  title: string;
  url: string;
  authorLogin: string;
  headRef: string;
  baseRef: string;
  ticketKey: string | null;
  /** The state on GitHub now; absent for a stored share, which keeps no state. */
  state?: GithubPullRequestState;
}

/**
 * The text as the server posts it: "KEY: title" (or the title), the link,
 * and "Status: <label>" followed by the note, one per line.
 */
function summaryText(facts: PullRequestFacts, status: PullRequestShareStatus, note: string): string {
  const heading = facts.ticketKey ? `${facts.ticketKey}: ${facts.title}` : facts.title;
  const trimmedNote = note.trim();
  const statusLine = `Status: ${SUMMARY_STATUS_LABELS[status]}${trimmedNote ? ` ${trimmedNote}` : ""}`;
  return [heading, facts.url, statusLine].join("\n");
}

type PullRequestSharePreviewProps = {
  status: PullRequestShareFormValues["status"];
  note: string;
  reviewerNames: string[];
  /** The chosen related tickets in form order. */
  relatedTickets: PullRequestShareTicketResponse[];
} & (
  | { kind: "empty" }
  | { kind: "loading" }
  | { kind: "error"; error: Error; isFetching: boolean; onRetry: () => void }
  | { kind: "ready"; facts: PullRequestFacts }
);

/**
 * The share as it will be posted: the pull request's facts, the team's
 * status, note, reviewers and related tickets, and the text "Copy text"
 * will copy. Built from
 * the form as it is now, so it follows every change at once.
 */
export function PullRequestSharePreview(props: PullRequestSharePreviewProps) {
  const t = useTranslations("pullRequests");

  let body;
  if (props.kind === "empty") {
    body = <p className="text-muted-foreground text-sm">{t("preview.empty")}</p>;
  } else if (props.kind === "loading") {
    body = (
      <div className="flex items-center gap-2 py-2">
        <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
        <span className="text-muted-foreground text-sm">{t("preview.loading")}</span>
      </div>
    );
  } else if (props.kind === "error") {
    body = (
      <PickerError
        title={t("preview.error")}
        error={props.error}
        isFetching={props.isFetching}
        onRetry={props.onRetry}
      />
    );
  } else {
    body = (
      <PreviewFacts
        facts={props.facts}
        status={props.status}
        note={props.note}
        reviewerNames={props.reviewerNames}
        relatedTickets={props.relatedTickets}
      />
    );
  }

  return (
    <section aria-labelledby="pull-request-preview-title" className="border-border bg-card rounded-lg border">
      <div className="border-border border-b px-4 py-3">
        <h2 id="pull-request-preview-title" className="text-foreground text-sm font-semibold">
          {t("preview.title")}
        </h2>
      </div>
      <div className="px-4 py-3">{body}</div>
    </section>
  );
}

/** Every text from GitHub renders as plain text; the link opens a new tab without access to this page. */
function PreviewFacts({
  facts,
  status,
  note,
  reviewerNames,
  relatedTickets,
}: {
  facts: PullRequestFacts;
  status: PullRequestShareFormValues["status"];
  note: string;
  reviewerNames: string[];
  relatedTickets: PullRequestShareTicketResponse[];
}) {
  const t = useTranslations("pullRequests");
  return (
    <div className="space-y-3">
      <div className="space-y-1">
        <p className="text-foreground text-sm font-medium break-words">
          {facts.ticketKey && <span>{facts.ticketKey}: </span>}
          {facts.title}
        </p>
        <div className="text-muted-foreground flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
          <span className="break-all">
            {facts.repositoryFullName} #{facts.number}
          </span>
          {facts.state && <GithubPullRequestStateBadge state={facts.state} />}
          <span>{t("preview.byAuthor", { login: facts.authorLogin })}</span>
        </div>
        <p className="text-muted-foreground flex min-w-0 items-center gap-1 text-xs">
          <GitBranch className="h-3 w-3 shrink-0" aria-hidden />
          <span className="break-all">
            {facts.headRef} -&gt; {facts.baseRef}
          </span>
        </p>
        <a
          href={facts.url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary inline-flex items-center gap-1 text-xs font-medium hover:underline"
        >
          {t("preview.openInGithub")}
          <ExternalLink className="h-3 w-3" aria-hidden />
        </a>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        {status && (
          <Badge variant="outline" className="px-1.5 py-0 text-[11px] font-medium">
            {t(`statuses.${status}`)}
          </Badge>
        )}
        <span role="group" aria-label={t("preview.reviewers")} className="flex flex-wrap items-center gap-1.5">
          {reviewerNames.length > 0 ? (
            reviewerNames.map((name, index) => (
              <Badge key={`${index}-${name}`} variant="secondary" className="px-1.5 py-0 text-[11px] font-normal">
                {name}
              </Badge>
            ))
          ) : (
            <span className="text-muted-foreground text-xs">{t("preview.noReviewers")}</span>
          )}
        </span>
      </div>

      {relatedTickets.length > 0 && (
        <ul aria-label={t("preview.relatedTickets")} className="space-y-1.5 text-sm">
          {relatedTickets.map((ticket) => (
            <li key={ticket.ticketId}>
              <RelatedTicketRow ticket={ticket} />
            </li>
          ))}
        </ul>
      )}

      <div className="border-border space-y-1 border-t pt-3">
        <p className="text-muted-foreground text-xs font-medium">{t("preview.postedText")}</p>
        {status ? (
          <pre className="text-foreground font-sans text-sm leading-relaxed break-words whitespace-pre-wrap">
            {summaryText(facts, status, note)}
          </pre>
        ) : (
          <p className="text-muted-foreground text-sm">{t("preview.noStatus")}</p>
        )}
      </div>
    </div>
  );
}
