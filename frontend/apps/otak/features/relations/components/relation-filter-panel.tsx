"use client";

import { useId, useMemo } from "react";
import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { SelectFieldStandalone, type SelectOption } from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { FilterLabel } from "@/components/page-states";
import { useEvent, useEventOptions } from "@/features/events/hooks/use-events";
import { useOrganizationMembers } from "@/features/members/hooks/use-organization-members";
import { memberOptions } from "@/features/members/lib/member-options";
import {
  hasActiveRelationFilter,
  RELATION_TAB_STAGES,
  tabHasStage,
  type RelationFilterParams,
} from "../lib/relation-filters";
import { PipelineStageSchema, type PipelineStage } from "../schemas";

function isPipelineStage(value: string): value is PipelineStage {
  return PipelineStageSchema.safeParse(value).success;
}

interface RelationFilterPanelProps {
  filters: RelationFilterParams;
  /** Applies a change; the list goes back to its first page. */
  onChange: (change: Partial<RelationFilterParams>) => void;
}

/**
 * The relations list's filters, each under its label as on Kaya's filter
 * rows: a search on name, the owner, the event and, on the leads and
 * customers tabs, the stages of that tab. Without a stage the leads list hides lost and
 * parked leads and the customers list shows every customer; picking stages
 * lists exactly those. It sits in the toolbar row after the tabs, so it takes
 * the remaining width and wraps inside itself.
 */
export function RelationFilterPanel({ filters, onChange }: RelationFilterPanelProps) {
  const t = useTranslations("relations");
  const members = useOrganizationMembers();
  const showStageFilter = tabHasStage(filters.tab);
  const ownerId = useId();
  const eventId = useId();
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

  const stageOptions = useMemo(
    () =>
      RELATION_TAB_STAGES[filters.tab].map((stage) => ({
        value: stage,
        label: t(`stages.${stage}`),
      })),
    [t, filters.tab]
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

  return (
    <div className="flex min-w-0 flex-1 flex-wrap items-end gap-3 sm:justify-end">
      <FilterLabel label={t("filters.searchLabel")} className="w-full sm:w-56">
        <SenseySearchInput
          value={filters.search}
          placeholder={t("filters.searchPlaceholder")}
          onSearchChange={(search) => onChange({ search: search ?? null })}
          className="w-full"
        />
      </FilterLabel>
      {showStageFilter && (
        <FilterLabel label={t("filters.stageLabel")} className="w-full sm:w-48">
          <SelectFieldStandalone
            isMulti
            value={filters.stage ?? []}
            onValueChange={(values) => {
              const stages = values.filter(isPipelineStage);
              onChange({ stage: stages.length > 0 ? stages : null });
            }}
            options={stageOptions}
            placeholder={
              filters.tab === "customers"
                ? t("filters.stagePlaceholderCustomers")
                : t("filters.stagePlaceholder")
            }
            className="w-full"
          />
        </FilterLabel>
      )}
      <FilterLabel label={t("filters.ownerLabel")} htmlFor={ownerId} className="w-full sm:w-48">
        <NativeSelect
          id={ownerId}
          value={filters.owner ?? ""}
          disabled={members.isPending && members.fetchStatus !== "idle"}
          onChange={(event) => onChange({ owner: event.target.value || null })}
          className="w-full"
        >
          <option value="">{t("filters.ownerPlaceholder")}</option>
          {ownerOptions.map((option: SelectOption) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </NativeSelect>
      </FilterLabel>
      <FilterLabel label={t("filters.eventLabel")} htmlFor={eventId} className="w-full sm:w-48">
        <NativeSelect
          id={eventId}
          value={filteredEventId}
          disabled={events.isPending && events.fetchStatus !== "idle"}
          onChange={(event) => onChange({ event: event.target.value || null })}
          className="w-full"
        >
          <option value="">{t("filters.eventPlaceholder")}</option>
          {eventOptions.map((option: SelectOption) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </NativeSelect>
      </FilterLabel>
      {hasActiveRelationFilter(filters) && (
        <Button
          variant="ghost"
          className="h-9"
          onClick={() => onChange({ search: null, owner: null, event: null, stage: null })}
        >
          <X aria-hidden="true" />
          {t("filters.clear")}
        </Button>
      )}
      {members.isError && (
        <p className="text-warning-text w-full text-xs sm:text-right" role="status">
          {t("filters.ownersLoadError")}
        </p>
      )}
      {events.isError && (
        <p className="text-warning-text w-full text-xs sm:text-right" role="status">
          {t("filters.eventsLoadError")}
        </p>
      )}
    </div>
  );
}
