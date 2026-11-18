"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { useWatch, type Control } from "react-hook-form";
import { AlertCircle } from "lucide-react";
import { SelectField as MultiSelectField } from "@oba/shared/features/form/components";
import { SelectField, TextareaField } from "@/components/ui/form-fields";
import { InlineAlert } from "@/components/ui/inline-alert";
import { memberDisplayName } from "@/features/chat/components/chat-channel-dialog";
import { useChatMeId, useChatMembers } from "@/features/chat/hooks/use-chat-messages";
import { hasKazanAccess } from "@/lib/kazan-member-access";
import {
  PULL_REQUEST_NOTE_MAX,
  PULL_REQUEST_REVIEWERS_MAX,
  PullRequestShareStatus,
  type PullRequestShareFormValues,
  type PullRequestShareReviewerResponse,
} from "../schemas";

/** The order the status select lists, from work in progress to done. */
const STATUS_ORDER: PullRequestShareStatus[] = [
  PullRequestShareStatus.DRAFT,
  PullRequestShareStatus.OPEN,
  PullRequestShareStatus.IN_REVIEW,
  PullRequestShareStatus.MERGED,
  PullRequestShareStatus.DISCUSS,
];

// ── Fields ────────────────────────────────────────────────────────────

const NO_STORED_REVIEWERS: PullRequestShareReviewerResponse[] = [];

interface PullRequestShareFieldsProps {
  control: Control<PullRequestShareFormValues>;
  disabled: boolean;
  /**
   * The share's stored reviewers, when editing. One who has since left the
   * organization stays selectable by name, so the author can see and remove
   * them; the server refuses to keep them.
   */
  storedReviewers?: PullRequestShareReviewerResponse[];
}

/**
 * Status, note and reviewers of a share. Reviewers are the organization's
 * active members with Kazan access except the sharer and guests, at most
 * ten. A member without Kazan access is listed but cannot be picked; a
 * stored reviewer who has lost it stays clickable, so the author can remove
 * them, because the server refuses to keep them. The related tickets are
 * picked in the ticket picker and listed on their own card.
 */
export function PullRequestShareFields({
  control,
  disabled,
  storedReviewers = NO_STORED_REVIEWERS,
}: PullRequestShareFieldsProps) {
  const t = useTranslations("pullRequests");
  const tAccess = useTranslations("kazan.memberAccess");
  const meId = useChatMeId();
  const membersQuery = useChatMembers();
  const note = useWatch({ control, name: "note" }) ?? "";
  const reviewerIds = useWatch({ control, name: "reviewerIds" });

  const reviewerOptions = useMemo(() => {
    const chosen = new Set(reviewerIds ?? []);
    const full = chosen.size >= PULL_REQUEST_REVIEWERS_MAX;
    const options = (membersQuery.data ?? [])
      .filter((member) => member.userId !== meId && member.role !== "GUEST")
      .map((member) => {
        const canReview = hasKazanAccess(member);
        return {
          value: member.userId,
          label: memberDisplayName(member),
          // At the limit only a chosen reviewer can be clicked, to remove them.
          disabled: (full || !canReview) && !chosen.has(member.userId),
          disabledLabel: canReview ? undefined : tAccess("noAccess"),
        };
      });
    const listed = new Set(options.map((option) => option.value));
    const former = storedReviewers
      .filter((reviewer) => !listed.has(reviewer.userId))
      .map((reviewer) => ({
        value: reviewer.userId,
        label: [reviewer.firstName, reviewer.lastName].filter(Boolean).join(" ") || reviewer.userId,
        disabled: !chosen.has(reviewer.userId),
      }));
    return [...options, ...former];
  }, [membersQuery.data, meId, reviewerIds, storedReviewers, tAccess]);

  return (
    <section
      aria-labelledby="pull-request-share-details-title"
      className="border-border bg-card space-y-4 rounded-lg border px-4 py-3"
    >
      <h2 id="pull-request-share-details-title" className="text-foreground text-sm font-semibold">
        {t("form.detailsTitle")}
      </h2>
      <SelectField<PullRequestShareFormValues>
        name="status"
        label={t("form.status")}
        placeholder={t("form.statusPlaceholder")}
        control={control}
        required
        disabled={disabled}
        triggerClassName="h-9"
        options={STATUS_ORDER.map((status) => ({ value: status, label: t(`statuses.${status}`) }))}
      />
      <TextareaField<PullRequestShareFormValues>
        name="note"
        label={t("form.note")}
        placeholder={t("form.notePlaceholder")}
        description={t("form.noteCounter", { count: note.length, max: PULL_REQUEST_NOTE_MAX })}
        control={control}
        maxLength={PULL_REQUEST_NOTE_MAX}
        rows={3}
        disabled={disabled}
      />
      {membersQuery.isError ? (
        <InlineAlert
          variant="danger"
          icon={<AlertCircle />}
          description={
            <>
              <span className="text-foreground font-medium">{t("form.membersError")}</span>{" "}
              {membersQuery.error.message}
            </>
          }
        />
      ) : (
        <MultiSelectField<PullRequestShareFormValues>
          name="reviewerIds"
          label={t("form.reviewers")}
          description={t("form.reviewersHint", {
            count: reviewerIds?.length ?? 0,
            max: PULL_REQUEST_REVIEWERS_MAX,
          })}
          control={control}
          isMulti
          options={reviewerOptions}
          isLoading={membersQuery.isPending}
          placeholder={t("form.reviewersPlaceholder")}
          searchPlaceholder={t("form.reviewersSearch")}
          disabled={disabled}
        />
      )}
    </section>
  );
}
