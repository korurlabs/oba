"use client";

import { useEffect, useMemo } from "react";
import { useFormContext, useWatch, type Path, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import {
  CheckboxField,
  FormGrid,
  ObaForm,
  SelectField,
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
import { useCreateContact, useUpdateContact } from "../hooks/use-contacts";
import { contactFieldErrors } from "../lib/contact-error";
import {
  CONTACT_LIMITS,
  ContactEmailTypeSchema,
  ContactKindSchema,
  contactToFormValues,
  createContactFormSchema,
  emptyContactFormValues,
  toContactRequest,
  type ContactListItem,
  type ContactFormValues,
} from "../schemas";

interface ContactFormDialogProps {
  relationId: string;
  /** The contact to edit, or null for a new one. */
  contact: ContactListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Adds a person or a business contact to the relation or edits one; closes once the server has stored it. */
export function ContactFormDialog({
  relationId,
  contact,
  open,
  onOpenChange,
}: ContactFormDialogProps) {
  const t = useTranslations("contacts");
  const tForm = useTranslations("contacts.form");
  const schema = useMemo(() => createContactFormSchema(tForm), [tForm]);
  const createContact = useCreateContact(relationId);
  const updateContact = useUpdateContact(relationId);
  const isPending = createContact.isPending || updateContact.isPending;
  const initialValues = useMemo(
    () => (contact ? contactToFormValues(contact) : emptyContactFormValues()),
    [contact]
  );

  const handleSubmit = (values: ContactFormValues, form: UseFormReturn<ContactFormValues>) => {
    const callbacks = {
      onSuccess: () => onOpenChange(false),
      onError: (error: unknown) => applyContactFieldErrors(form, error),
    };
    const data = toContactRequest(values);
    if (contact) {
      updateContact.mutate({ contactId: contact.id, data }, callbacks);
    } else {
      createContact.mutate(data, callbacks);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{contact ? t("editTitle") : t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {/* Mounted per open, so a reopened dialog starts from the contact, not the last input. */}
        {open && (
          <ObaForm
            schema={schema}
            defaultValues={initialValues}
            onSubmit={handleSubmit}
            className="space-y-6"
          >
            <ContactFormFields isPending={isPending} />
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
                {isPending ? tForm("saving") : contact ? tForm("save") : tForm("create")}
              </Button>
            </DialogFooter>
          </ObaForm>
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Puts the fields a refused save names on the form. */
function applyContactFieldErrors(form: UseFormReturn<ContactFormValues>, error: unknown): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(contactFieldErrors(error))) {
    if (fields.includes(field)) {
      form.setError(field as Path<ContactFormValues>, { type: "server", message });
    }
  }
}

function ContactFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("contacts.form");
  const tContacts = useTranslations("contacts");
  const { control, getValues, setValue } = useFormContext<ContactFormValues>();
  const email = useWatch({ control, name: "email" });
  const hasEmail = email.trim() !== "";
  const kindOptions = useMemo(
    () =>
      ContactKindSchema.options.map((kind) => ({
        value: kind,
        label: tContacts(`kinds.${kind}`),
      })),
    [tContacts]
  );
  const emailTypeOptions = useMemo(
    () =>
      ContactEmailTypeSchema.options.map((type) => ({
        value: type,
        label: tContacts(`emailTypes.${type}`),
      })),
    [tContacts]
  );

  // A type describes an address, so clearing the address clears its type too.
  useEffect(() => {
    if (!hasEmail && getValues("emailType") !== "") {
      setValue("emailType", "", { shouldDirty: true });
    }
  }, [hasEmail, getValues, setValue]);

  return (
    <FormGrid>
      <SelectField
        name="kind"
        label={t("kind")}
        control={control}
        options={kindOptions}
        required
        disabled={isPending}
      />
      <TextField
        name="name"
        label={t("name")}
        control={control}
        required
        maxLength={CONTACT_LIMITS.nameMax}
        disabled={isPending}
      />
      <TextField
        name="role"
        label={t("role")}
        placeholder={t("rolePlaceholder")}
        control={control}
        maxLength={CONTACT_LIMITS.roleMax}
        disabled={isPending}
      />
      <TextField
        name="email"
        label={t("email")}
        control={control}
        type="email"
        autoComplete="off"
        maxLength={CONTACT_LIMITS.emailMax}
        disabled={isPending}
      />
      <SelectField
        name="emailType"
        label={t("emailType")}
        placeholder={hasEmail ? t("emailTypePlaceholder") : t("emailTypeNeedsEmail")}
        control={control}
        options={emailTypeOptions}
        clearable
        disabled={isPending || !hasEmail}
      />
      <TextField
        name="phone"
        label={t("phone")}
        control={control}
        type="tel"
        autoComplete="off"
        maxLength={CONTACT_LIMITS.phoneMax}
        disabled={isPending}
      />
      <TextField
        name="linkedinUrl"
        label={t("linkedinUrl")}
        placeholder={t("linkedinUrlPlaceholder")}
        control={control}
        maxLength={CONTACT_LIMITS.linkedinUrlMax}
        disabled={isPending}
      />
      {/* A flag on the person, so it gets a row of its own under a rule. */}
      <div className="border-t pt-4 md:col-span-2">
        <CheckboxField
          name="decisionMaker"
          label={t("decisionMaker")}
          description={t("decisionMakerDescription")}
          control={control}
          disabled={isPending}
        />
      </div>
    </FormGrid>
  );
}
