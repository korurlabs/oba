"use client";

import { useMemo } from "react";
import { useFormContext, type Path, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { FormGrid, ObaForm, SelectField, TextField } from "@oba/shared/features/form";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { useCreateMailSender } from "../hooks/use-mail-senders";
import { mailSenderFieldErrors } from "../lib/mail-sender-error";
import {
  createMailSenderFormSchema,
  EmailTypeSchema,
  emptyMailSenderFormValues,
  MAIL_SENDER_LIMITS,
  toMailSenderRequest,
  type MailSenderFormValues,
} from "../schemas";

export interface MailSenderFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Adds a mail sender; closes once the server has found the mailbox in the
 * Microsoft 365 tenant and stored it. A sender is never edited: a wrong
 * address is deleted and added again.
 */
export function MailSenderFormDialog({ open, onOpenChange }: MailSenderFormDialogProps) {
  const t = useTranslations("mailSenders");
  const tForm = useTranslations("mailSenders.form");
  const schema = useMemo(() => createMailSenderFormSchema(tForm), [tForm]);
  const createMailSender = useCreateMailSender();
  const isPending = createMailSender.isPending;

  const handleSubmit = (
    values: MailSenderFormValues,
    form: UseFormReturn<MailSenderFormValues>
  ) => {
    createMailSender.mutate(toMailSenderRequest(values), {
      onSuccess: () => onOpenChange(false),
      onError: (error: unknown) => applyMailSenderFieldErrors(form, error, t),
    });
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {/* Mounted per open, so a reopened dialog starts empty, not with the last input. */}
        {open && (
          <ObaForm
            schema={schema}
            defaultValues={emptyMailSenderFormValues()}
            onSubmit={handleSubmit}
            className="space-y-6"
          >
            <MailSenderFormFields isPending={isPending} />
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
                {isPending ? tForm("saving") : tForm("create")}
              </Button>
            </DialogFooter>
          </ObaForm>
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Puts the fields a refused add names on the form. */
function applyMailSenderFieldErrors(
  form: UseFormReturn<MailSenderFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(mailSenderFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<MailSenderFormValues>, { type: "server", message });
    }
  }
}

function MailSenderFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("mailSenders");
  const { control } = useFormContext<MailSenderFormValues>();

  const typeOptions = useMemo(
    () => EmailTypeSchema.options.map((type) => ({ value: type, label: t(`types.${type}`) })),
    [t]
  );

  return (
    <FormGrid>
      <div className="md:col-span-2">
        <SelectField
          name="type"
          label={t("form.type")}
          control={control}
          options={typeOptions}
          required
          disabled={isPending}
        />
      </div>
      <div className="md:col-span-2">
        <TextField
          name="mailAddress"
          label={t("form.mailAddress")}
          placeholder={t("form.mailAddressPlaceholder")}
          description={t("form.mailAddressHint")}
          control={control}
          required
          type="email"
          autoComplete="off"
          maxLength={MAIL_SENDER_LIMITS.mailAddressMax}
          disabled={isPending}
        />
      </div>
    </FormGrid>
  );
}
