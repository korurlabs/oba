"use client";

import { useId, useMemo, type ReactNode } from "react";
import { useFormContext, type Path, type UseFormReturn } from "react-hook-form";
import { useLocale, useTranslations } from "next-intl";
import {
  FormGrid,
  ObaForm,
  SelectField,
  TextareaField,
  TextField,
  type SelectOption,
} from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { useCampaignOptions } from "@/features/campaigns/hooks/use-campaigns";
import { useEvent, useEventOptions } from "@/features/events/hooks/use-events";
import { useIcp, useIcpOptions } from "@/features/icps/hooks/use-icps";
import { countryOptions } from "@/lib/countries";
import { useRelationOwnerCandidates } from "../hooks/use-relations";
import { ownerCandidateOptions } from "../lib/owner-options";
import { relationFieldErrors } from "../lib/relation-error";
import {
  createRelationFormSchema,
  RELATION_LIMITS,
  RelationSizeSchema,
  RelationSourceSchema,
  RelationTypeSchema,
  type RelationFormValues,
  type RelationOwner,
} from "../schemas";

interface RelationFormProps {
  mode: "create" | "edit";
  /** The values the form starts from; a new value resets the form. */
  initialValues: RelationFormValues;
  /** The stored owner, listed even when the member has left or been disabled. */
  currentOwner?: RelationOwner | null;
  isPending: boolean;
  onSubmit: (values: RelationFormValues, form: UseFormReturn<RelationFormValues>) => void;
  /** Extra controls before the submit button on the action bar, such as a cancel link. */
  secondaryAction?: ReactNode;
}

/**
 * The relation form of the create page and of the detail page: the Company,
 * Address and Sales panels over a sticky action bar. On edit the type is
 * fixed and not shown; it changes through its own action.
 */
export function RelationForm({
  mode,
  initialValues,
  currentOwner,
  isPending,
  onSubmit,
  secondaryAction,
}: RelationFormProps) {
  const t = useTranslations("relations.form");
  const schema = useMemo(() => createRelationFormSchema(t), [t]);

  return (
    <ObaForm
      schema={schema}
      onSubmit={onSubmit}
      defaultValues={initialValues}
      className="space-y-6"
    >
      <RelationFormFields
        mode={mode}
        currentOwner={currentOwner}
        currentIcpId={initialValues.icpId}
        currentEventId={initialValues.eventId}
        isPending={isPending}
      />
      <RelationFormActions mode={mode} isPending={isPending} secondaryAction={secondaryAction} />
    </ObaForm>
  );
}

/**
 * Puts the fields a refused save names on the form. Returns true when the
 * refusal named at least one field the form shows.
 */
export function applyRelationFieldErrors(
  form: UseFormReturn<RelationFormValues>,
  error: unknown,
  t: (key: string) => string
): boolean {
  const fields = Object.keys(form.getValues());
  let applied = false;
  for (const [field, message] of Object.entries(relationFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<RelationFormValues>, { type: "server", message });
      applied = true;
    }
  }
  return applied;
}

function RelationFormFields({
  mode,
  currentOwner,
  currentIcpId,
  currentEventId,
  isPending,
}: {
  mode: "create" | "edit";
  currentOwner?: RelationOwner | null;
  /** The stored ICP link, "" for none. */
  currentIcpId: string;
  /** The stored event link, "" for none. */
  currentEventId: string;
  isPending: boolean;
}) {
  const t = useTranslations("relations");
  const locale = useLocale();
  const { control } = useFormContext<RelationFormValues>();
  const ownerCandidates = useRelationOwnerCandidates();
  const icps = useIcpOptions();
  // The select offers only the first ICPs by name; a stored link outside them
  // is fetched on its own so the form can show and keep it.
  const storedIcpListed =
    currentIcpId === "" || (icps.data ?? []).some((option) => option.value === currentIcpId);
  const storedIcp = useIcp(currentIcpId, { enabled: icps.isSuccess && !storedIcpListed });
  // The same for events: the select offers the most recent ones only.
  const events = useEventOptions();
  const storedEventListed =
    currentEventId === "" || (events.data ?? []).some((option) => option.value === currentEventId);
  const storedEvent = useEvent(currentEventId, {
    enabled: events.isSuccess && !storedEventListed,
  });
  // Every campaign of the organization: the list is unpaged, so a stored
  // link is always among the options while its campaign exists.
  const campaigns = useCampaignOptions();

  const typeOptions = useMemo(
    () => RelationTypeSchema.options.map((type) => ({ value: type, label: t(`types.${type}`) })),
    [t]
  );
  const sizeOptions = useMemo(
    () => RelationSizeSchema.options.map((size) => ({ value: size, label: t(`sizes.${size}`) })),
    [t]
  );
  const sourceOptions = useMemo(
    () =>
      RelationSourceSchema.options.map((source) => ({
        value: source,
        label: t(`sources.${source}`),
      })),
    [t]
  );
  const ownerNoAccessLabel = t("form.ownerNoOtakAccess");
  const ownerOptions = useMemo(
    () =>
      ownerCandidateOptions(ownerCandidates.data?.data, {
        currentOwner,
        noAccessLabel: ownerNoAccessLabel,
      }),
    [ownerCandidates.data?.data, currentOwner, ownerNoAccessLabel]
  );
  const icpOptions = useMemo(() => {
    const options = [...(icps.data ?? [])];
    const stored = storedIcp.data?.data;
    if (stored && !options.some((option) => option.value === stored.id)) {
      options.push({ value: stored.id, label: stored.name });
    }
    return options;
  }, [icps.data, storedIcp.data?.data]);
  const eventOptions = useMemo(() => {
    const options = [...(events.data ?? [])];
    const stored = storedEvent.data?.data;
    if (stored && !options.some((option) => option.value === stored.id)) {
      options.push({ value: stored.id, label: stored.name });
    }
    return options;
  }, [events.data, storedEvent.data?.data]);
  const countrySelectOptions = useMemo(() => countryOptions(locale), [locale]);

  return (
    <>
      <FormSection title={t("form.sections.company")}>
        <FormGrid className="md:grid-cols-6">
          <div className={mode === "create" ? "md:col-span-4" : "md:col-span-6"}>
            <TextField
              name="name"
              label={t("form.name")}
              control={control}
              required
              maxLength={RELATION_LIMITS.nameMax}
              disabled={isPending}
            />
          </div>
          {mode === "create" && (
            <div className="md:col-span-2">
              <SelectField
                name="type"
                label={t("form.type")}
                placeholder={t("form.typePlaceholder")}
                control={control}
                options={typeOptions}
                required
                disabled={isPending}
              />
            </div>
          )}
          <div className="md:col-span-3">
            <TextField
              name="website"
              label={t("form.website")}
              placeholder={t("form.websitePlaceholder")}
              control={control}
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-3">
            <TextField
              name="sector"
              label={t("form.sector")}
              control={control}
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-2">
            <SelectField
              name="size"
              label={t("form.size")}
              placeholder={t("form.sizePlaceholder")}
              control={control}
              options={sizeOptions}
              clearable
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-2">
            <TextField
              name="registrationNumber"
              label={t("form.registrationNumber")}
              control={control}
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-2">
            <TextField
              name="vatNumber"
              label={t("form.vatNumber")}
              control={control}
              disabled={isPending}
            />
          </div>
        </FormGrid>
      </FormSection>

      <FormSection title={t("form.sections.address")}>
        <FormGrid className="md:grid-cols-6">
          <div className="md:col-span-6">
            <TextField
              name="street"
              label={t("form.street")}
              control={control}
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-2">
            <TextField
              name="postalCode"
              label={t("form.postalCode")}
              control={control}
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-2">
            <TextField name="city" label={t("form.city")} control={control} disabled={isPending} />
          </div>
          <div className="md:col-span-2">
            <SelectField
              name="country"
              label={t("form.country")}
              placeholder={t("form.countryPlaceholder")}
              searchPlaceholder={t("form.countrySearch")}
              control={control}
              options={countrySelectOptions}
              clearable
              disabled={isPending}
            />
          </div>
        </FormGrid>
      </FormSection>

      <FormSection title={t("form.sections.sales")}>
        <FormGrid className="md:grid-cols-6">
          <div className="md:col-span-2">
            <SelectField
              name="ownerUserId"
              label={t("form.owner")}
              placeholder={t("form.ownerPlaceholder")}
              control={control}
              options={ownerOptions}
              renderOption={renderOwnerOption}
              renderValue={renderOwnerValue}
              isLoading={ownerCandidates.isPending && ownerCandidates.fetchStatus !== "idle"}
              clearable
              disabled={isPending}
              description={ownerCandidates.isError ? t("form.ownersLoadError") : undefined}
            />
          </div>
          <div className="md:col-span-2">
            <SelectField
              name="icpId"
              label={t("form.icp")}
              placeholder={t("form.icpPlaceholder")}
              control={control}
              options={icpOptions}
              isLoading={icps.isPending && icps.fetchStatus !== "idle"}
              clearable
              disabled={isPending}
              description={icps.isError ? t("form.icpsLoadError") : undefined}
            />
          </div>
          <div className="md:col-span-2">
            <SelectField
              name="eventId"
              label={t("form.event")}
              placeholder={t("form.eventPlaceholder")}
              control={control}
              options={eventOptions}
              isLoading={events.isPending && events.fetchStatus !== "idle"}
              clearable
              disabled={isPending}
              description={events.isError ? t("form.eventsLoadError") : undefined}
            />
          </div>
          <div className="md:col-span-2">
            <SelectField
              name="campaignId"
              label={t("form.campaign")}
              placeholder={t("form.campaignPlaceholder")}
              control={control}
              options={campaigns.data ?? []}
              isLoading={campaigns.isPending && campaigns.fetchStatus !== "idle"}
              clearable
              disabled={isPending}
              description={campaigns.isError ? t("form.campaignsLoadError") : undefined}
            />
          </div>
          <div className="md:col-span-2">
            <SelectField
              name="source"
              label={t("form.source")}
              placeholder={t("form.sourcePlaceholder")}
              control={control}
              options={sourceOptions}
              clearable
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-2">
            <TextField
              name="expectedValue"
              label={t("form.expectedValue")}
              control={control}
              type="number"
              min={0}
              step="0.01"
              inputMode="decimal"
              disabled={isPending}
            />
          </div>
          <div className="md:col-span-6">
            <TextareaField
              name="notes"
              label={t("form.notes")}
              control={control}
              rows={5}
              maxLength={RELATION_LIMITS.notesMax}
              disabled={isPending}
            />
          </div>
        </FormGrid>
      </FormSection>
    </>
  );
}

/**
 * An owner option: the name, and under it why the member cannot be picked.
 * The reason wraps, because the narrow select would cut a full sentence off.
 */
function renderOwnerOption(option: SelectOption): ReactNode {
  return (
    <span className="flex flex-col">
      <span className="truncate">{option.label}</span>
      {option.disabled && option.disabledLabel && (
        <span className="text-muted-foreground text-xs whitespace-normal">
          {option.disabledLabel}
        </span>
      )}
    </span>
  );
}

/** The chosen owner in the select's trigger: the name only. */
function renderOwnerValue(option: SelectOption): ReactNode {
  return <span className="truncate">{option.label}</span>;
}

/** One group of fields: a white rounded panel headed by its title. */
function FormSection({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId();

  return (
    <Card role="region" aria-labelledby={headingId}>
      <CardHeader>
        <h2 id={headingId} className="text-lg leading-none">
          {title}
        </h2>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

/**
 * The form's actions on a bar that sticks to the bottom of the page while the
 * form scrolls, so saving never needs a scroll to the end. On edit it also
 * says when there are unsaved changes and can put the stored values back.
 */
function RelationFormActions({
  mode,
  isPending,
  secondaryAction,
}: {
  mode: "create" | "edit";
  isPending: boolean;
  secondaryAction?: ReactNode;
}) {
  const t = useTranslations("relations.form");
  const {
    formState: { isDirty },
    reset,
  } = useFormContext<RelationFormValues>();
  const showDirty = mode === "edit" && isDirty;

  return (
    <div className="bg-card/95 sticky bottom-4 z-10 flex flex-wrap items-center justify-end gap-2 rounded-xl border px-4 py-3 shadow-md backdrop-blur">
      <p className="text-warning-text mr-auto text-sm font-medium" role="status">
        {showDirty ? t("unsavedChanges") : null}
      </p>
      {secondaryAction}
      {mode === "edit" && (
        <Button
          type="button"
          variant="outline"
          disabled={!isDirty || isPending}
          onClick={() => reset()}
        >
          {t("discard")}
        </Button>
      )}
      <Button type="submit" disabled={isPending}>
        {mode === "create"
          ? isPending
            ? t("creating")
            : t("create")
          : isPending
            ? t("saving")
            : t("save")}
      </Button>
    </div>
  );
}
