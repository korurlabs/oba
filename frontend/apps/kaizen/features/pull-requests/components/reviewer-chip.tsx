"use client";

import { useTranslations } from "next-intl";
import { Check } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { getDisplayName } from "@/components/ui/user-avatar";
import type { PullRequestShareReviewerResponse } from "../schemas";

interface ReviewerChipProps {
  reviewer: PullRequestShareReviewerResponse;
  className?: string;
}

/**
 * One chosen reviewer of a share. An approval turns the chip green with a
 * tick at its bottom-right corner; screen readers hear "Approved", so the
 * state does not rest on colour alone.
 */
export function ReviewerChip({ reviewer, className }: ReviewerChipProps) {
  const t = useTranslations("pullRequests.card");
  const name =
    getDisplayName({ firstName: reviewer.firstName ?? undefined, lastName: reviewer.lastName ?? undefined }) ||
    t("unknownReviewer");
  const approved = reviewer.approvedAt != null;

  return (
    <span
      className={cn(
        "relative inline-flex max-w-48 items-center rounded-full border px-2 py-0.5 text-[11px] font-medium",
        approved
          ? "border-emerald-500/50 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400"
          : "border-border bg-background text-foreground",
        className,
      )}
    >
      <span className="truncate">{name}</span>
      {approved && (
        <>
          <span
            aria-hidden
            className="ring-background absolute -right-1 -bottom-1 flex size-3.5 items-center justify-center rounded-full bg-emerald-600 text-white ring-2"
          >
            <Check className="size-2.5" strokeWidth={3} />
          </span>
          <span className="sr-only">{t("approved")}</span>
        </>
      )}
    </span>
  );
}
