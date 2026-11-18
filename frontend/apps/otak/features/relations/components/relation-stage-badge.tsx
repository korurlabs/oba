"use client";

import type { ComponentProps } from "react";
import { useTranslations } from "next-intl";
import { Badge } from "@oba/ui/sky/badge";
import type { PipelineStage } from "../schemas";

/** The badge look of each stage. */
const STAGE_VARIANTS: Record<PipelineStage, ComponentProps<typeof Badge>["variant"]> = {
  NEW: "info",
  QUALIFIED: "info",
  CONTACTED: "info",
  WARM: "info",
  PROPOSAL: "info",
  CLOSED: "success",
  LOST: "muted",
  PARKED: "muted",
  DIRECT: "secondary",
};

/**
 * A relation's stage as a sky badge: an open lead stage in the brand's light
 * blue, a lead nobody works any more (lost, parked) muted so the eye skips it
 * while scanning the pipeline, a won customer in green and a direct customer
 * plain.
 */
export function RelationStageBadge({ stage }: { stage: PipelineStage }) {
  const t = useTranslations("relations");
  return <Badge variant={STAGE_VARIANTS[stage]}>{t(`stages.${stage}`)}</Badge>;
}
