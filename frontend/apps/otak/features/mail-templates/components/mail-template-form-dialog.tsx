"use client";

import { useMemo } from "react";
import { useFormContext, type Path, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
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
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import {
  useCreateMailTemplate,
  useMailTemplate,
  useUpdateMailTemplate,
} from "../hooks/use-mail-templates";
import { mailTemplateErrorText, mailTemplateFieldErrors } from "../lib/mail-template-error";
import {
  createMailTemplateFormSchema,
  emptyMailTemplateFormValues,
  MAIL_TEMPLATE_LIMITS,
  mailTemplateToFormValues,
  toMailTemplateRequest,
  type MailTemplateFormValues,
} from "../schemas";

export interface MailTemplateFormDialogProps {
  /** The mail template to edit, or null for a new one. */
  mailTemplateId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Creates a mail template or edits one; closes once the server has stored it. */
export function MailTemplateFormDialog({
  mailTemplateId,
  open,
  onOpenChange,
}: MailTemplateFormDialogProps) {
  const t = useTranslations("mailTemplates");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("mailTemplates.form");
  const schema = useMemo(() => createMailTemplateFormSchema(tForm), [tForm]);
  const createTemplate = useCreateMailTemplate();
  const updateTemplate = useUpdateMailTemplate();
  const isPending = createTemplate.isPending || updateTemplate.isPending;
  const template = useMailTemplate(mailTemplateId ?? "", {
    enabled: open && mailTemplateId !== null,
  });
  const stored = template.data?.data;
  const initialValues = useMemo(
    () => (stored ? mailTemplateToFormValues(stored) : emptyMailTemplateFormValues()),
    [stored]
  );

  const handleSubmit = (
    values: MailTemplateFormValues,
    form: UseFormReturn<MailTemplateFormValues>
  ) => {
    const options = {
      onSuccess: () => onOpenChange(false),
      onError: (error: unknown) => applyMailTemplateFieldErrors(form, error, t),
    };
    const data = toMailTemplateRequest(values);
    if (mailTemplateId) {
      updateTemplate.mutate({ mailTemplateId, data }, options);
    } else {
      createTemplate.mutate(data, options);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{mailTemplateId ? t("editTitle") : t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {mailTemplateId !== null && template.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={mailTemplateErrorText(template.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void template.refetch()}
          />
        ) : mailTemplateId !== null && stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          // Mounted per open, so a reopened dialog starts from the stored template, not the last input.
          open && (
            <ObaForm
              key={mailTemplateId ? `${mailTemplateId}-${stored?.updatedAt ?? ""}` : "new"}
              schema={schema}
              defaultValues={initialValues}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <MailTemplateFormFields isPending={isPending} />
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
                  {isPending ? tForm("saving") : mailTemplateId ? tForm("save") : tForm("create")}
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
function applyMailTemplateFieldErrors(
  form: UseFormReturn<MailTemplateFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(mailTemplateFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<MailTemplateFormValues>, { type: "server", message });
    }
  }
}

function MailTemplateFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("mailTemplates");
  const tTemplates = useTranslations("documentTemplates");
  const { control } = useFormContext<MailTemplateFormValues>();
  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((value) => ({
        value,
        label: tTemplates(`languages.${value}`),
      })),
    [tTemplates]
  );

  return (
    <FormGrid>
      <TextField
        name="name"
        label={t("form.name")}
        placeholder={t("form.namePlaceholder")}
        control={control}
        required
        maxLength={MAIL_TEMPLATE_LIMITS.nameMax}
        disabled={isPending}
      />
      <SelectField
        name="language"
        label={t("form.language")}
        control={control}
        options={languageOptions}
        required
        disabled={isPending}
      />
      <div className="md:col-span-2">
        <TextField
          name="subject"
          label={t("form.subject")}
          placeholder={t("form.subjectPlaceholder")}
          control={control}
          required
          maxLength={MAIL_TEMPLATE_LIMITS.subjectMax}
          disabled={isPending}
        />
      </div>
      <div className="md:col-span-2">
        <TextareaField
          name="body"
          label={t("form.body")}
          placeholder={t("form.bodyPlaceholder")}
          control={control}
          required
          rows={10}
          maxLength={MAIL_TEMPLATE_LIMITS.bodyMax}
          disabled={isPending}
        />
      </div>
    </FormGrid>
  );
}
