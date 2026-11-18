"use client";

import { useMemo } from "react";
import { useFormContext, type Path, type UseFormReturn } from "react-hook-form";
import { useLocale, useTranslations } from "next-intl";
import {
  FormGrid,
  ObaForm,
  SelectField,
  TextareaField,
  TextField,
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
import { countryOptions } from "@/lib/countries";
import { useCreateEvent, useEvent, useUpdateEvent } from "../hooks/use-events";
import { eventErrorText, eventFieldErrors } from "../lib/event-error";
import {
  createEventFormSchema,
  emptyEventFormValues,
  EVENT_LIMITS,
  eventToFormValues,
  EventTypeSchema,
  toEventRequest,
  type EventFormValues,
} from "../schemas";

interface EventFormDialogProps {
  /** The event to edit, or null for a new one. */
  eventId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Creates an event or edits one; closes once the server has stored it. The
 * list has no notes, so an edit loads the event before showing the form.
 */
export function EventFormDialog({ eventId, open, onOpenChange }: EventFormDialogProps) {
  const t = useTranslations("events");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("events.form");
  const schema = useMemo(() => createEventFormSchema(tForm), [tForm]);
  const createEvent = useCreateEvent();
  const updateEvent = useUpdateEvent();
  const isPending = createEvent.isPending || updateEvent.isPending;
  const event = useEvent(eventId ?? "", { enabled: open && eventId !== null });
  const stored = event.data?.data;
  const initialValues = useMemo(
    () => (stored ? eventToFormValues(stored) : emptyEventFormValues()),
    [stored]
  );

  const handleSubmit = (values: EventFormValues, form: UseFormReturn<EventFormValues>) => {
    // The schema refuses an empty type, so a submitted form always has one.
    if (values.type === "") return;
    const callbacks = {
      onSuccess: () => onOpenChange(false),
      onError: (error: unknown) => applyEventFieldErrors(form, error, t),
    };
    const data = toEventRequest(values, values.type);
    if (eventId) {
      updateEvent.mutate({ eventId, data }, callbacks);
    } else {
      createEvent.mutate(data, callbacks);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{eventId ? t("editTitle") : t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {eventId !== null && event.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={eventErrorText(event.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void event.refetch()}
          />
        ) : eventId !== null && stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          // Mounted per open, so a reopened dialog starts from the event, not the last input.
          open && (
            <ObaForm
              key={eventId ?? "new"}
              schema={schema}
              defaultValues={initialValues}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <EventFormFields isPending={isPending} />
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
                  {isPending ? tForm("saving") : eventId ? tForm("save") : tForm("create")}
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
function applyEventFieldErrors(
  form: UseFormReturn<EventFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(eventFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<EventFormValues>, { type: "server", message });
    }
  }
}

function EventFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("events");
  const locale = useLocale();
  const { control } = useFormContext<EventFormValues>();

  const typeOptions = useMemo(
    () => EventTypeSchema.options.map((type) => ({ value: type, label: t(`types.${type}`) })),
    [t]
  );
  const countrySelectOptions = useMemo(() => countryOptions(locale), [locale]);

  return (
    <FormGrid>
      <TextField
        name="name"
        label={t("form.name")}
        placeholder={t("form.namePlaceholder")}
        control={control}
        required
        maxLength={EVENT_LIMITS.nameMax}
        disabled={isPending}
      />
      <SelectField
        name="type"
        label={t("form.type")}
        placeholder={t("form.typePlaceholder")}
        control={control}
        options={typeOptions}
        required
        disabled={isPending}
      />
      {/* A date input gives YYYY-MM-DD, a calendar day without a time zone. */}
      <TextField
        name="startDate"
        type="date"
        label={t("form.startDate")}
        control={control}
        required
        disabled={isPending}
      />
      <TextField
        name="endDate"
        type="date"
        label={t("form.endDate")}
        control={control}
        disabled={isPending}
      />
      <TextField
        name="city"
        label={t("form.city")}
        placeholder={t("form.cityPlaceholder")}
        control={control}
        maxLength={EVENT_LIMITS.cityMax}
        disabled={isPending}
      />
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
      <div className="md:col-span-2">
        <TextField
          name="website"
          label={t("form.website")}
          placeholder={t("form.websitePlaceholder")}
          control={control}
          maxLength={EVENT_LIMITS.websiteMax}
          disabled={isPending}
        />
      </div>
      <div className="md:col-span-2">
        <TextareaField
          name="notes"
          label={t("form.notes")}
          placeholder={t("form.notesPlaceholder")}
          control={control}
          maxLength={EVENT_LIMITS.notesMax}
          disabled={isPending}
        />
      </div>
    </FormGrid>
  );
}
