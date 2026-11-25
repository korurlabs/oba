"use client";

import { useMemo, type ReactNode } from "react";
import { useFormContext, type Path, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import {
  FormGrid,
  ObaForm,
  SelectField,
  TextareaField,
  TextField,
  type SelectOption,
} from "@oba/shared/features/form";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useEvent, useEventOptions } from "@/features/events/hooks/use-events";
import { useIcp, useIcpOptions } from "@/features/icps/hooks/use-icps";
import { useRelationOwnerCandidates } from "@/features/relations/hooks/use-relations";
import { ownerCandidateOptions } from "@/features/relations/lib/owner-options";
import { useCampaign, useCreateCampaign, useUpdateCampaign } from "../hooks/use-campaigns";
import { campaignErrorText, campaignFieldErrors } from "../lib/campaign-error";
import {
  campaignToFormValues,
  createCampaignFormSchema,
  emptyCampaignFormValues,
  toCampaignRequest,
  type CampaignFormValues,
  type CampaignOwner,
} from "../schemas";

interface CampaignFormDialogProps {
  /** The campaign to edit, or null for a new one. */
  campaignId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Creates a campaign or edits one; closes once the server has stored it. An
 * edit loads the campaign first, so the form starts from the stored values.
 * The contents are added on the campaign page.
 */
export function CampaignFormDialog({ campaignId, open, onOpenChange }: CampaignFormDialogProps) {
  const t = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("campaigns.form");
  const schema = useMemo(() => createCampaignFormSchema(tForm), [tForm]);
  const createCampaign = useCreateCampaign();
  const updateCampaign = useUpdateCampaign();
  const isPending = createCampaign.isPending || updateCampaign.isPending;
  const campaign = useCampaign(campaignId ?? "", { enabled: open && campaignId !== null });
  const stored = campaign.data?.data;
  const initialValues = useMemo(
    () => (stored ? campaignToFormValues(stored) : emptyCampaignFormValues()),
    [stored]
  );

  const handleSubmit = (values: CampaignFormValues, form: UseFormReturn<CampaignFormValues>) => {
    const callbacks = {
      onSuccess: () => onOpenChange(false),
      onError: (error: unknown) => applyCampaignFieldErrors(form, error, t),
    };
    const data = toCampaignRequest(values);
    if (campaignId) {
      updateCampaign.mutate({ campaignId, data }, callbacks);
    } else {
      createCampaign.mutate(data, callbacks);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{campaignId ? t("editTitle") : t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {campaignId !== null && campaign.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={campaignErrorText(campaign.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void campaign.refetch()}
          />
        ) : campaignId !== null && stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          // Mounted per open, so a reopened dialog starts from the campaign, not the last input.
          open && (
            <ObaForm
              key={campaignId ?? "new"}
              schema={schema}
              defaultValues={initialValues}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <CampaignFormFields
                isPending={isPending}
                currentOwner={stored?.owner ?? null}
                currentEventId={initialValues.eventId}
                currentIcpId={initialValues.icpId}
              />
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  disabled={isPending}
                  onClick={() => onOpenChange(false)}
                >
                  {tForm("cancel")}
                </Button>
                <Button type="submit" disabled={isPending}>
                  {isPending ? tForm("saving") : campaignId ? tForm("save") : tForm("create")}
                </Button>
              </DialogFooter>
            </ObaForm>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Puts the fields a refused save names on the form. */
function applyCampaignFieldErrors(
  form: UseFormReturn<CampaignFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(campaignFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<CampaignFormValues>, { type: "server", message });
    }
  }
}

function CampaignFormFields({
  isPending,
  currentOwner,
  currentEventId,
  currentIcpId,
}: {
  isPending: boolean;
  /** The stored owner, listed even when the member has left or been disabled. */
  currentOwner: CampaignOwner | null;
  /** The stored event link, "" for none. */
  currentEventId: string;
  /** The stored ICP link, "" for none. */
  currentIcpId: string;
}) {
  const t = useTranslations("campaigns");
  const { control } = useFormContext<CampaignFormValues>();
  const ownerCandidates = useRelationOwnerCandidates();
  // The event and ICP selects offer the most recent events and the first
  // ICPs; a stored link outside them is fetched on its own so the form can
  // show and keep it.
  const events = useEventOptions();
  const storedEventListed =
    currentEventId === "" || (events.data ?? []).some((option) => option.value === currentEventId);
  const storedEvent = useEvent(currentEventId, {
    enabled: events.isSuccess && !storedEventListed,
  });
  const icps = useIcpOptions();
  const storedIcpListed =
    currentIcpId === "" || (icps.data ?? []).some((option) => option.value === currentIcpId);
  const storedIcp = useIcp(currentIcpId, { enabled: icps.isSuccess && !storedIcpListed });

  const ownerNoAccessLabel = t("form.ownerNoOtakAccess");
  const ownerOptions = useMemo(
    () =>
      ownerCandidateOptions(ownerCandidates.data?.data, {
        currentOwner,
        noAccessLabel: ownerNoAccessLabel,
      }),
    [ownerCandidates.data?.data, currentOwner, ownerNoAccessLabel]
  );
  const eventOptions = useMemo(() => {
    const options = [...(events.data ?? [])];
    const stored = storedEvent.data?.data;
    if (stored && !options.some((option) => option.value === stored.id)) {
      options.push({ value: stored.id, label: stored.name });
    }
    return options;
  }, [events.data, storedEvent.data?.data]);
  const icpOptions = useMemo(() => {
    const options = [...(icps.data ?? [])];
    const stored = storedIcp.data?.data;
    if (stored && !options.some((option) => option.value === stored.id)) {
      options.push({ value: stored.id, label: stored.name });
    }
    return options;
  }, [icps.data, storedIcp.data?.data]);

  return (
    <div className="flex flex-col gap-6">
      <FormGrid>
        <div className="md:col-span-2">
          <TextField
            name="name"
            label={t("form.name")}
            placeholder={t("form.namePlaceholder")}
            control={control}
            required
            disabled={isPending}
          />
        </div>
        <div className="md:col-span-2">
          <TextareaField
            name="description"
            label={t("form.description")}
            placeholder={t("form.descriptionPlaceholder")}
            control={control}
            rows={4}
            disabled={isPending}
          />
        </div>
        {/* A date input gives YYYY-MM-DD, a calendar day without a time zone. */}
        <TextField
          name="startDate"
          type="date"
          label={t("form.startDate")}
          control={control}
          disabled={isPending}
        />
        <TextField
          name="endDate"
          type="date"
          label={t("form.endDate")}
          control={control}
          disabled={isPending}
        />
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
      </FormGrid>
    </div>
  );
}

/** An owner option, with the reason a member without Otak access cannot be picked. */
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

/** The picked owner, by name only. */
function renderOwnerValue(option: SelectOption): ReactNode {
  return <span className="truncate">{option.label}</span>;
}
