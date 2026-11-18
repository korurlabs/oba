"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { GithubPullRequestState } from "../schemas";

/** GitHub's own colours for its states, so the picker reads as GitHub does. */
const STATE_CLASSES: Record<GithubPullRequestState, string> = {
  [GithubPullRequestState.OPEN]: "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
  [GithubPullRequestState.DRAFT]: "border-border bg-muted text-muted-foreground",
  [GithubPullRequestState.MERGED]: "border-purple-500/40 bg-purple-500/10 text-purple-700 dark:text-purple-400",
  [GithubPullRequestState.CLOSED]: "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-400",
};

/** The pull request's state on GitHub, as text and colour. */
export function GithubPullRequestStateBadge({
  state,
  className,
}: {
  state: GithubPullRequestState;
  className?: string;
}) {
  const t = useTranslations("pullRequests.states");
  return (
    <Badge
      variant="outline"
      className={cn("px-1.5 py-0 text-[11px] font-medium whitespace-nowrap", STATE_CLASSES[state], className)}
    >
      {t(state)}
    </Badge>
  );
}
