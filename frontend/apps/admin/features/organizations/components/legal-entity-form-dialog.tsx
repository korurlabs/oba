"use client";

import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Building2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { TableLoading } from "@oba/ui/sky/page-states";
import { FormGrid, SelectField, TextField } from "@oba/shared/features/form";
import type { SelectOption } from "@oba/shared/features/form";
import { isApiError } from "@oba/shared/api/axios";
import { getCountryOptions } from "@oba/shared/features/organizations/lib/organization-options";
import {
  legalEntityFormSchema,
  type LegalEntityDetail,
  type LegalEntityFormValues,
  type LegalEntityListItem,
} from "../schemas";
import { toLegalEntityFormValues, toLegalEntityRequest } from "../lib/legal-entity-request";
import {
  LEGAL_ENTITY_NOT_FOUND,
  LEGAL_ENTITY_SHORT_NAME_TAKEN,
  VALIDATION_FAILED,
  useCreateLegalEntity,
  useLegalEntity,
  useUpdateLegalEntity,
} from "../hooks/use-legal-entities";
import { OrganizationLoadError } from "./organization-load-error";

/**
 * The dialog shows a taken short name and the backend's field messages on the
 * fields, not in a toast.
 */
const FORM_ERROR_CODES = [LEGAL_ENTITY_SHORT_NAME_TAKEN, VALIDATION_FAILED] as const;

const FORM_FIELDS: readonly (keyof LegalEntityFormValues)[] = [
  "shortName",
  "legalName",
  "country",
  "registrationNumber",
  "vatNumber",
  "street",
  "postalCode",
  "city",
  "governingLaw",
  "court",
];

function isFormField(name: string): name is keyof LegalEntityFormValues {
  return (FORM_FIELDS as readonly string[]).includes(name);
}

interface LegalEntityFormDialogProps {
  organizationId: string;
  /** The legal entity to edit; without it the dialog creates one. */
  legalEntity: LegalEntityListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Create and edit dialog of a legal entity. A list row carries only the
 * columns of the table, so editing loads the full legal entity first.
 */
export function LegalEntityFormDialog({
  organizationId,
  legalEntity,
  open,
  onOpenChange,
}: LegalEntityFormDialogProps) {
  const t = useTranslations("organization");
  const createEntity = useCreateLegalEntity(organizationId, { formErrorCodes: FORM_ERROR_CODES });
  const updateEntity = useUpdateLegalEntity(organizationId, { formErrorCodes: FORM_ERROR_CODES });
  const isEdit = legalEntity !== null;
  const isPending = createEntity.isPending || updateEntity.isPending;

  const detail = useLegalEntity(organizationId, open && isEdit ? legalEntity.id : null);

  const handleOpenChange = (isOpen: boolean) => {
    // Closing drops the entered values, which must not happen under a running request.
    if (isPending) return;
    onOpenChange(isOpen);
  };

  const close = () => onOpenChange(false);

  const onSubmit = (values: LegalEntityFormValues, onError: (error: Error) => void) => {
    const data = toLegalEntityRequest(values);
    if (legalEntity) {
      updateEntity.mutate({ legalEntityId: legalEntity.id, data }, { onSuccess: close, onError });
    } else {
      createEntity.mutate(data, { onSuccess: close, onError });
    }
  };

  let body;
  if (!isEdit) {
    body = (
      <LegalEntityForm
        key="new"
        entity={null}
        isEdit={false}
        isPending={isPending}
        onSubmit={onSubmit}
        onCancel={() => handleOpenChange(false)}
        onGone={close}
      />
    );
  } else if (detail.data?.data) {
    // Keyed by the loaded legal entity, so the form starts from its stored values.
    body = (
      <LegalEntityForm
        key={detail.data.data.id}
        entity={detail.data.data}
        isEdit
        isPending={isPending}
        onSubmit={onSubmit}
        onCancel={() => handleOpenChange(false)}
        onGone={close}
      />
    );
  } else if (detail.isError) {
    body = <OrganizationLoadError onRetry={() => void detail.refetch()} />;
  } else {
    body = <TableLoading label={t("legalEntities.loadingDetail")} />;
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[640px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Building2 className="text-primary h-5 w-5" />
            {isEdit ? t("legalEntities.editTitle") : t("legalEntities.createTitle")}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? t("legalEntities.editDescription") : t("legalEntities.createDescription")}
          </DialogDescription>
        </DialogHeader>
        {body}
      </DialogContent>
    </Dialog>
  );
}

/**
 * The fields of the dialog. Radix unmounts the dialog content while it is
 * closed, so every opening mounts a fresh form with the values of that moment.
 */
function LegalEntityForm({
  entity,
  isEdit,
  isPending,
  onSubmit,
  onCancel,
  onGone,
}: {
  entity: LegalEntityDetail | null;
  isEdit: boolean;
  isPending: boolean;
  onSubmit: (values: LegalEntityFormValues, onError: (error: Error) => void) => void;
  onCancel: () => void;
  /** Closes the dialog when the edited legal entity no longer exists. */
  onGone: () => void;
}) {
  const t = useTranslations("organization");
  const tOnboarding = useTranslations("onboarding");
  const schema = useMemo(() => legalEntityFormSchema(t), [t]);

  const form = useForm<LegalEntityFormValues>({
    resolver: zodResolver(schema),
    defaultValues: toLegalEntityFormValues(entity),
    mode: "onChange",
  });

  // A country stored through the API outside the offered list stays
  // selectable, so opening and saving the form does not drop it.
  const storedCountry = entity?.country;
  const countryOptions = useMemo<SelectOption[]>(() => {
    const options = getCountryOptions(tOnboarding);
    if (!storedCountry || options.some((option) => option.value === storedCountry)) {
      return options;
    }
    return [...options, { label: storedCountry, value: storedCountry }];
  }, [tOnboarding, storedCountry]);

  // A taken short name and the backend's field messages are shown on the
  // fields: the hooks skip their toast for those codes. A gone legal entity
  // closes the dialog after the hook's toast. Every other failure is shown by
  // the hook's toast, and the dialog stays open with the entered values.
  const showServerError = (error: Error) => {
    if (isApiError(error, LEGAL_ENTITY_SHORT_NAME_TAKEN)) {
      form.setError("shortName", {
        type: "server",
        message: t("legalEntities.shortNameTaken"),
      });
      return;
    }
    if (isApiError(error, VALIDATION_FAILED)) {
      const fieldErrors = Object.entries(error.errorValues).filter(([name]) => isFormField(name));
      for (const [name, message] of fieldErrors) {
        form.setError(name as keyof LegalEntityFormValues, {
          type: "server",
          message: typeof message === "string" ? message : t("legalEntities.invalid"),
        });
      }
      if (fieldErrors.length === 0) toast.error(t("legalEntities.invalid"));
      return;
    }
    if (isApiError(error, LEGAL_ENTITY_NOT_FOUND)) onGone();
  };

  const submitLabel = isEdit
    ? isPending
      ? t("legalEntities.saving")
      : t("legalEntities.save")
    : isPending
      ? t("legalEntities.creating")
      : t("legalEntities.create");

  const text = (
    name: keyof LegalEntityFormValues,
    options?: { required?: boolean; span?: boolean }
  ) => (
    <div className={options?.span ? "md:col-span-2" : undefined}>
      <TextField
        name={name}
        label={t(`legalEntities.${name}Label`)}
        placeholder={t(`legalEntities.${name}Placeholder`)}
        control={form.control}
        required={options?.required}
        disabled={isPending}
      />
    </div>
  );

  return (
    <FormProvider {...form}>
      <form
        onSubmit={form.handleSubmit((values) => onSubmit(values, showServerError))}
        className="space-y-4 py-2"
      >
        <FormGrid>
          {text("shortName", { required: true })}
          {text("legalName", { required: true })}
          <SelectField
            name="country"
            label={t("legalEntities.countryLabel")}
            placeholder={t("legalEntities.countryPlaceholder")}
            control={form.control}
            options={countryOptions}
            required
            disabled={isPending}
          />
          {text("registrationNumber")}
          {text("vatNumber")}
          {text("street")}
          {text("postalCode")}
          {text("city")}
          {text("governingLaw")}
          {text("court")}
        </FormGrid>

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isPending}>
            {t("legalEntities.cancel")}
          </Button>
          <Button type="submit" disabled={isPending || !form.formState.isValid}>
            {submitLabel}
          </Button>
        </DialogFooter>
      </form>
    </FormProvider>
  );
}
