"use client";

import { parseAsArrayOf, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";
import {
  MilestoneHealthSchema,
  MilestoneStatusSchema,
  type MilestoneHealth,
  type MilestoneStatus,
} from "@/features/milestones/schemas";

export const ROADMAP_ZOOMS = ["month", "quarter", "year"] as const;
export type RoadmapZoom = (typeof ROADMAP_ZOOMS)[number];

/**
 * Zoom and filters live in the URL so a filtered roadmap can be shared.
 * The filters run on the loaded roadmap, which already holds every milestone
 * of the window, so they change no query. An absent filter means "all".
 */
export const roadmapParams = {
  zoom: parseAsStringEnum<RoadmapZoom>([...ROADMAP_ZOOMS]).withDefault("month"),
  status: parseAsStringEnum<MilestoneStatus>([...MilestoneStatusSchema.options]),
  health: parseAsArrayOf(parseAsStringEnum<MilestoneHealth>([...MilestoneHealthSchema.options])),
};

export type RoadmapParamValues = Values<typeof roadmapParams>;

export function useRoadmapParams() {
  return useQueryStates(roadmapParams);
}
