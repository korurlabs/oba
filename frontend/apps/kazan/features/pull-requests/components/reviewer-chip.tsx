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
 * One chosen reviewer of a share, as a square outline chip. An approval gives
 * the chip a primary edge and a tick at its bottom-right corner; screen
 * readers hear "Approved", so the state does not rest on colour alone.
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
        "relative inline-flex max-w-48 items-center rounded-none border px-2 py-0.5 text-[11px] font-medium",
        approved
          ? "border-primary/50 bg-primary/5 text-primary"
          : "border-border bg-background text-foreground",
        className,
      )}
    >
      <span className="truncate">{name}</span>
      {approved && (
        <>
          <span
            aria-hidden
            className="ring-background bg-primary text-primary-foreground absolute -right-1 -bottom-1 flex size-3.5 items-center justify-center rounded-none ring-2"
          >
            <Check className="size-2.5" strokeWidth={3} />
          </span>
          <span className="sr-only">{t("approved")}</span>
        </>
      )}
    </span>
  );
}
