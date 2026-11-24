"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { GithubPullRequestState } from "../schemas";

/*
 * Outline chips in the grid inks, as the share card's status chips: open work
 * in the primary ink, a draft muted, a merge in plain ink and a closed,
 * unmerged pull request in red. The word names the state; the ink only ranks it.
 */
const STATE_CLASSES: Record<GithubPullRequestState, string> = {
  [GithubPullRequestState.OPEN]: "border-primary/50 text-korur-blue-strong",
  [GithubPullRequestState.DRAFT]: "border-border border-dashed text-muted-foreground",
  [GithubPullRequestState.MERGED]: "border-foreground/40 text-foreground",
  [GithubPullRequestState.CLOSED]: "border-red-600/40 text-red-700 dark:text-red-400",
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
      className={cn("bg-transparent px-1.5 py-0.5 font-mono text-[11px] leading-none font-medium tracking-[0.08em] whitespace-nowrap uppercase", STATE_CLASSES[state], className)}
    >
      {t(state)}
    </Badge>
  );
}
