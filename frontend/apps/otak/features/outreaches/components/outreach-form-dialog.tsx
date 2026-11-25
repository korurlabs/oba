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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { EntityAttachments } from "@/features/attachments/components/entity-attachments";
import { useCampaignOptions } from "@/features/campaigns/hooks/use-campaigns";
import { useContacts } from "@/features/contacts/hooks/use-contacts";
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useOutreach, useUpdateOutreach } from "../hooks/use-outreaches";
import { outreachErrorText, outreachFieldErrors } from "../lib/outreach-error";
import { reachableContacts } from "../lib/reach-out";
import {
  canWriteMailAttachments,
  createOutreachFormSchema,
  isMailOutreach,
  OUTREACH_LIMITS,
  outreachToFormValues,
  toOutreachUpdateRequest,
  type OutreachDetail,
  type OutreachFormValues,
} from "../schemas";

export interface OutreachFormDialogProps {
  /** The DRAFT or FAILED outreach to edit; null keeps the dialog closed. */
  outreachId: string | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Edits an outreach that is not sent or done yet: its contact and title,
 * and for a mail also the mail, with its attachments on a tab of their own.
 * An attachment is stored or removed at once; the form closes once the
 * server has stored its changes. The channel can not change.
 */
export function OutreachFormDialog({ outreachId, onOpenChange }: OutreachFormDialogProps) {
  const t = useTranslations("outreaches");
  const tErrors = useTranslations("errors");
  const open = outreachId !== null;
  const outreach = useOutreach(outreachId ?? "", { enabled: open });
  const stored = outreach.data?.data;
  const updateOutreach = useUpdateOutreach();
  const isPending = updateOutreach.isPending;

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("editTitle")}</DialogTitle>
          <DialogDescription>{t("editDescription")}</DialogDescription>
        </DialogHeader>
        {outreach.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={outreachErrorText(outreach.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void outreach.refetch()}
          />
        ) : stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          // Mounted per outreach, so a reopened dialog starts from the stored values.
          <OutreachEditForm
            key={stored.id}
            outreach={stored}
            isPending={isPending}
            onCancel={() => onOpenChange(false)}
            onSubmit={(values, form) => {
              updateOutreach.mutate(
                {
                  outreachId: stored.id,
                  data: toOutreachUpdateRequest(values, stored.type),
                },
                {
                  onSuccess: () => onOpenChange(false),
                  onError: (error) => applyOutreachFieldErrors(form, error, t),
                }
              );
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function OutreachEditForm({
  outreach,
  isPending,
  onCancel,
  onSubmit,
}: {
  outreach: OutreachDetail;
  isPending: boolean;
  onCancel: () => void;
  onSubmit: (values: OutreachFormValues, form: UseFormReturn<OutreachFormValues>) => void;
}) {
  const t = useTranslations("outreaches");
  const tForm = useTranslations("outreaches.form");
  const isAdmin = useIsOrganizationAdmin();
  const schema = useMemo(
    () => createOutreachFormSchema(tForm, outreach.type),
    [tForm, outreach.type]
  );
  const initialValues = useMemo(() => outreachToFormValues(outreach), [outreach]);

  const form = (
    <ObaForm
      schema={schema}
      defaultValues={initialValues}
      onSubmit={(values, form) => onSubmit(values, form)}
      className="space-y-6"
    >
      <OutreachFormFields outreach={outreach} isPending={isPending} />
      <DialogFooter>
        <Button type="button" variant="outline" disabled={isPending} onClick={onCancel}>
          {tForm("cancel")}
        </Button>
        <Button type="submit" disabled={isPending}>
          {isPending ? tForm("saving") : tForm("save")}
        </Button>
      </DialogFooter>
    </ObaForm>
  );

  if (!outreach.mail) {
    return form;
  }
  return (
    <Tabs defaultValue="details">
      <TabsList aria-label={t("detailTabs.label")}>
        <TabsTrigger value="details">{t("detailTabs.details")}</TabsTrigger>
        <TabsTrigger value="attachments">{t("detailTabs.attachments")}</TabsTrigger>
      </TabsList>
      {/* Kept mounted, so a look at the attachments keeps the unsaved edits. */}
      <TabsContent value="details" forceMount className="data-[state=inactive]:hidden">
        {form}
      </TabsContent>
      <TabsContent value="attachments">
        <EntityAttachments
          mailId={outreach.mail.id}
          relationId={outreach.relationId}
          readOnly={!canWriteMailAttachments(outreach, isAdmin)}
        />
      </TabsContent>
    </Tabs>
  );
}

/** Puts the fields a refused save names on the form. */
function applyOutreachFieldErrors(
  form: UseFormReturn<OutreachFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(outreachFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<OutreachFormValues>, { type: "server", message });
    }
  }
}

function OutreachFormFields({
  outreach,
  isPending,
}: {
  outreach: OutreachDetail;
  isPending: boolean;
}) {
  const t = useTranslations("outreaches");
  const tTemplates = useTranslations("documentTemplates");
  const { control } = useFormContext<OutreachFormValues>();
  const contacts = useContacts(outreach.relationId);
  const campaigns = useCampaignOptions();
  const isMail = isMailOutreach(outreach);

  // The stored contact stays a choice even when it no longer has a business address or phone.
  const contactOptions = useMemo(() => {
    const options = reachableContacts(contacts.data?.data ?? [], outreach.type).map((contact) => ({
      value: contact.id,
      label: `${contact.name} (${isMail ? contact.email : contact.phone})`,
    }));
    const current = outreach.contact;
    if (current && !options.some((option) => option.value === current.id)) {
      options.unshift({ value: current.id, label: current.name });
    }
    return options;
  }, [contacts.data, outreach.contact, outreach.type, isMail]);
  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((language) => ({
        value: language,
        label: tTemplates(`languages.${language}`),
      })),
    [tTemplates]
  );

  return (
    <FormGrid>
      <div className="md:col-span-2">
        <TextField
          name="title"
          label={t("form.title")}
          placeholder={t("form.titlePlaceholder")}
          control={control}
          required
          maxLength={OUTREACH_LIMITS.titleMax}
          disabled={isPending}
        />
      </div>
      <SelectField
        name="contactId"
        label={t("form.contact")}
        control={control}
        options={contactOptions}
        isLoading={contacts.isLoading}
        required
        disabled={isPending}
      />
      {contacts.isError && (
        <p className="text-error text-sm md:col-span-2" role="alert">
          {t("form.contactsLoadError")}
        </p>
      )}
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
      {isMail && (
        <>
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
              maxLength={OUTREACH_LIMITS.subjectMax}
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
              maxLength={OUTREACH_LIMITS.bodyMax}
              disabled={isPending}
            />
          </div>
        </>
      )}
    </FormGrid>
  );
}
