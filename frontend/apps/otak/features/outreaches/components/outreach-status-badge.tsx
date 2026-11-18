"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import type { OutreachStatus } from "../schemas";

const STATUS_VARIANTS = {
  DRAFT: "muted",
  SENT: "success",
  FAILED: "error",
} as const satisfies Record<OutreachStatus, "muted" | "success" | "error">;

/** Where an outreach stands: a draft, sent, or failed and ready to be sent again. */
export function OutreachStatusBadge({ status }: { status: OutreachStatus }) {
  const t = useTranslations("outreaches");
  return <Badge variant={STATUS_VARIANTS[status]}>{t(`statuses.${status}`)}</Badge>;
}
