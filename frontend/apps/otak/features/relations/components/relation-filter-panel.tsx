"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { useFilterPopoverLabels } from "@/components/list-labels";
import { useCampaignOptions } from "@/features/campaigns/hooks/use-campaigns";
import { useEvent, useEventOptions } from "@/features/events/hooks/use-events";
import { useOrganizationMembers } from "@/features/members/hooks/use-organization-members";
import { memberOptions } from "@/features/members/lib/member-options";
import {
  RELATION_STAGE_GROUPS,
  RELATION_TYPES,
  type RelationFilterParams,
} from "../lib/relation-filters";
import {
  PipelineStageSchema,
  RelationTypeSchema,
  type PipelineStage,
  type RelationType,
} from "../schemas";

function isPipelineStage(value: string): value is PipelineStage {
  return PipelineStageSchema.safeParse(value).success;
}

function isRelationType(value: string): value is RelationType {
  return RelationTypeSchema.safeParse(value).success;
}

interface RelationFilterPanelProps {
  filters: RelationFilterParams;
  /** Applies a change; the list goes back to its first page. */
  onChange: (change: Partial<RelationFilterParams>) => void;
}

/**
 * The relations list's filter popover: the types, the stages, the owner, the
 * event and the campaign. No type lists every type. The stages are those of
 * leads and customers, grouped under their type, since a stage implies its
 * type; without a stage the list hides lost and parked leads, and picking
 * stages lists exactly those. The name search sits beside it in the list
 * toolbar and is not cleared with these filters.
 */
export function RelationFilterPanel({ filters, onChange }: RelationFilterPanelProps) {
  const t = useTranslations("relations");
  const members = useOrganizationMembers();
  const filterLabels = useFilterPopoverLabels();
  const events = useEventOptions();
  // The select offers only the most recent events; an event from a shared
  // link outside them is fetched on its own so its name shows.
  const filteredEventId = filters.event ?? "";
  const filteredEventListed =
    filteredEventId === "" ||
    (events.data ?? []).some((option) => option.value === filteredEventId);
  const filteredEvent = useEvent(filteredEventId, {
    enabled: events.isSuccess && !filteredEventListed,
  });
  const campaigns = useCampaignOptions();
  const filteredCampaignId = filters.campaign ?? "";

  const typeOptions = useMemo(
    () => RELATION_TYPES.map((type) => ({ value: type, label: t(`types.${type}`) })),
    [t]
  );
  // The stages sit under a heading of their type, since a stage implies its type.
  const stageOptions = useMemo(
    () =>
      RELATION_STAGE_GROUPS.flatMap((group) =>
        group.stages.map((stage) => ({
          value: stage,
          label: t(`stages.${stage}`),
          group: t(`types.${group.type}`),
        }))
      ),
    [t]
  );
  // The filter lists disabled members too: they can still own relations.
  const ownerOptions = useMemo(() => {
    const options = memberOptions(members.data?.data);
    // An owner from a shared link that is not (yet) listed stays selectable, so it can be cleared.
    if (filters.owner !== null && !options.some((option) => option.value === filters.owner)) {
      options.push({ value: filters.owner, label: filters.owner });
    }
    return options;
  }, [members.data?.data, filters.owner]);
  const eventOptions = useMemo(() => {
    const options = [...(events.data ?? [])];
    if (filteredEventId !== "" && !options.some((option) => option.value === filteredEventId)) {
      // Until the event is read, or when it no longer exists, the id stands in
      // for its name, so the filter can still be cleared.
      options.push({
        value: filteredEventId,
        label: filteredEvent.data?.data?.name ?? filteredEventId,
      });
    }
    return options;
  }, [events.data, filteredEventId, filteredEvent.data?.data?.name]);
  const campaignOptions = useMemo(() => {
    const options = [...(campaigns.data ?? [])];
    if (
      filteredCampaignId !== "" &&
      !options.some((option) => option.value === filteredCampaignId)
    ) {
      // The options hold every campaign; a link to a deleted one, or one read
      // before the list arrives, shows its id so the filter can be cleared.
      options.push({ value: filteredCampaignId, label: filteredCampaignId });
    }
    return options;
  }, [campaigns.data, filteredCampaignId]);

  const categories: FilterCategory[] = [
    {
      key: "type",
      label: t("filters.typeLabel"),
      mode: "multi",
      value: filters.type ?? [],
      onChange: (values) => {
        const types = values.filter(isRelationType);
        onChange({ type: types.length > 0 ? types : null });
      },
      options: typeOptions,
    },
    {
      key: "stage",
      label: t("filters.stageLabel"),
      mode: "multi",
      value: filters.stage ?? [],
      onChange: (values) => {
        const stages = values.filter(isPipelineStage);
        onChange({ stage: stages.length > 0 ? stages : null });
      },
      options: stageOptions,
    },
    {
      key: "owner",
      label: t("filters.ownerLabel"),
      mode: "single",
      value: filters.owner,
      onChange: (owner) => onChange({ owner }),
      options: ownerOptions,
      searchable: true,
      isLoading: members.isPending && members.fetchStatus !== "idle",
      error: members.isError ? t("filters.ownersLoadError") : null,
    },
    {
      key: "event",
      label: t("filters.eventLabel"),
      mode: "single",
      value: filters.event,
      onChange: (event) => onChange({ event }),
      options: eventOptions,
      searchable: true,
      isLoading: events.isPending && events.fetchStatus !== "idle",
      error: events.isError ? t("filters.eventsLoadError") : null,
    },
    {
      key: "campaign",
      label: t("filters.campaignLabel"),
      mode: "single",
      value: filters.campaign,
      onChange: (campaign) => onChange({ campaign }),
      options: campaignOptions,
      searchable: true,
      isLoading: campaigns.isPending && campaigns.fetchStatus !== "idle",
      error: campaigns.isError ? t("filters.campaignsLoadError") : null,
    },
  ];

  return (
    <FilterPopover
      categories={categories}
      onClear={() =>
        onChange({ type: null, stage: null, owner: null, event: null, campaign: null })
      }
      labels={filterLabels}
    />
  );
}
