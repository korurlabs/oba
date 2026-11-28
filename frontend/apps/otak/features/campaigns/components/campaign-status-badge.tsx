"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import type { CampaignStatus } from "../schemas";

const STATUS_VARIANTS = {
  DRAFT: "muted",
  SCHEDULED: "info",
  PUBLISHED: "success",
  ARCHIVED: "secondary",
} as const satisfies Record<CampaignStatus, "muted" | "info" | "success" | "secondary">;

/** Where a campaign content stands: a draft, planned, out, or put away. */
export function CampaignStatusBadge({ status }: { status: CampaignStatus }) {
  const t = useTranslations("campaigns");
  return <Badge variant={STATUS_VARIANTS[status]}>{t(`statuses.${status}`)}</Badge>;
}
