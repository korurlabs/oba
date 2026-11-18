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
import { RelationSizeSchema } from "@/features/relations/schemas";
import { countryOptions } from "@/lib/countries";
import { useCreateIcp, useIcp, useUpdateIcp } from "../hooks/use-icps";
import { icpErrorText, icpFieldErrors } from "../lib/icp-error";
import {
  createIcpFormSchema,
  emptyIcpFormValues,
  ICP_LIMITS,
  icpToFormValues,
  IndustrySchema,
  TargetPersonRoleSchema,
  toIcpRequest,
  type IcpFormValues,
} from "../schemas";

interface IcpFormDialogProps {
  /** The ICP to edit, or null for a new one. */
  icpId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Creates an ICP or edits one; closes once the server has stored it. The
 * list has no description, so an edit loads the ICP before showing the form.
 */
export function IcpFormDialog({ icpId, open, onOpenChange }: IcpFormDialogProps) {
  const t = useTranslations("icps");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("icps.form");
  const schema = useMemo(() => createIcpFormSchema(tForm), [tForm]);
  const createIcp = useCreateIcp();
  const updateIcp = useUpdateIcp();
  const isPending = createIcp.isPending || updateIcp.isPending;
  const icp = useIcp(icpId ?? "", { enabled: open && icpId !== null });
  const stored = icp.data?.data;
  const initialValues = useMemo(
    () => (stored ? icpToFormValues(stored) : emptyIcpFormValues()),
    [stored]
  );

  const handleSubmit = (values: IcpFormValues, form: UseFormReturn<IcpFormValues>) => {
    // The schema refuses an empty industry, so a submitted form always has one.
    if (values.industry === "") return;
    const callbacks = {
      onSuccess: () => onOpenChange(false),
      onError: (error: unknown) => applyIcpFieldErrors(form, error, t),
    };
    const data = toIcpRequest(values, values.industry);
    if (icpId) {
      updateIcp.mutate({ icpId, data }, callbacks);
    } else {
      createIcp.mutate(data, callbacks);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{icpId ? t("editTitle") : t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {icpId !== null && icp.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={icpErrorText(icp.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void icp.refetch()}
          />
        ) : icpId !== null && stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          // Mounted per open, so a reopened dialog starts from the ICP, not the last input.
          open && (
            <ObaForm
              key={icpId ?? "new"}
              schema={schema}
              defaultValues={initialValues}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <IcpFormFields isPending={isPending} />
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
                  {isPending ? tForm("saving") : icpId ? tForm("save") : tForm("create")}
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
function applyIcpFieldErrors(
  form: UseFormReturn<IcpFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(icpFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<IcpFormValues>, { type: "server", message });
    }
  }
}

function IcpFormFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("icps");
  const tRelations = useTranslations("relations");
  const locale = useLocale();
  const { control } = useFormContext<IcpFormValues>();

  const industryOptions = useMemo(
    () =>
      IndustrySchema.options.map((industry) => ({
        value: industry,
        label: t(`industries.${industry}`),
      })),
    [t]
  );
  const roleOptions = useMemo(
    () =>
      TargetPersonRoleSchema.options.map((role) => ({ value: role, label: t(`roles.${role}`) })),
    [t]
  );
  const sizeOptions = useMemo(
    () =>
      RelationSizeSchema.options.map((size) => ({
        value: size,
        label: tRelations(`sizes.${size}`),
      })),
    [tRelations]
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
        maxLength={ICP_LIMITS.nameMax}
        disabled={isPending}
      />
      <SelectField
        name="industry"
        label={t("form.industry")}
        placeholder={t("form.industryPlaceholder")}
        control={control}
        options={industryOptions}
        required
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
      <SelectField
        name="targetPersonRole"
        label={t("form.targetPersonRole")}
        placeholder={t("form.targetPersonRolePlaceholder")}
        control={control}
        options={roleOptions}
        clearable
        disabled={isPending}
      />
      <SelectField
        name="companySizeMin"
        label={t("form.companySizeMin")}
        placeholder={t("form.companySizePlaceholder")}
        control={control}
        options={sizeOptions}
        clearable
        disabled={isPending}
      />
      <SelectField
        name="companySizeMax"
        label={t("form.companySizeMax")}
        placeholder={t("form.companySizePlaceholder")}
        control={control}
        options={sizeOptions}
        clearable
        disabled={isPending}
      />
      <div className="md:col-span-2">
        <TextareaField
          name="description"
          label={t("form.description")}
          placeholder={t("form.descriptionPlaceholder")}
          control={control}
          maxLength={ICP_LIMITS.descriptionMax}
          disabled={isPending}
        />
      </div>
    </FormGrid>
  );
}
