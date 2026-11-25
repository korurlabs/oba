"use client";

import { useEffect, useMemo } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { ShieldCheck } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import {
  SelectFieldStandalone,
  SelectField,
  TextField,
  TextareaField,
  type SelectOption,
} from "@oba/shared/features/form";
import { useCreateCheck, useUpdateCheck } from "../hooks/use-checks";
import { checkFormDefaults, checkFormToRequest, deriveCheckStatus } from "../lib/check-form";
import {
  CHECK_CATEGORIES,
  CHECK_NAME_MAX_LENGTH,
  CHECK_OUTCOMES,
  CHECK_SORT_ORDER_MAX,
  CHECK_SORT_ORDER_MIN,
  CHECK_STATUSES,
  CHECK_TEXT_MAX_LENGTH,
  CHECK_WEIGHT_MAX,
  CHECK_WEIGHT_MIN,
  CheckCategorySchema,
  CheckOutcomeSchema,
  createCheckFormSchema,
  type Check,
  type CheckFormInput,
  type CheckFormValues,
} from "../schemas";

interface CheckDialogProps {
  /** The check to edit; absent when a check is added. */
  check?: Check;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Adds or edits a check. Choosing an outcome fills in the status it implies,
 * as the server would derive it; the admin can still pick another status,
 * which the server then keeps.
 */
export function CheckDialog({ check, open, onOpenChange }: CheckDialogProps) {
  const t = useTranslations("cyberscore");
  const tCommon = useTranslations("common");
  const isEdit = !!check;
  const schema = useMemo(() => createCheckFormSchema(t), [t]);
  const createCheck = useCreateCheck();
  const updateCheck = useUpdateCheck();
  const isPending = createCheck.isPending || updateCheck.isPending;

  const categoryOptions = useMemo(
    (): SelectOption[] =>
      CHECK_CATEGORIES.map((category) => ({
        value: category,
        label: t(`categories.${category}`),
      })),
    [t]
  );
  const outcomeOptions = useMemo(
    (): SelectOption[] =>
      CHECK_OUTCOMES.map((outcome) => ({ value: outcome, label: t(`outcomes.${outcome}`) })),
    [t]
  );
  const statusOptions = useMemo(
    (): SelectOption[] =>
      CHECK_STATUSES.map((status) => ({ value: status, label: t(`statuses.${status}`) })),
    [t]
  );

  const form = useForm<CheckFormInput, unknown, CheckFormValues>({
    resolver: zodResolver(schema),
    defaultValues: checkFormDefaults(check),
    mode: "onChange",
  });

  // The dialog stays mounted between uses, so each opening starts from the
  // check it was opened for.
  useEffect(() => {
    if (open) form.reset(checkFormDefaults(check));
  }, [open, check, form]);

  // A failed request is surfaced by the mutation's error toast; the dialog
  // then stays open with the entered values.
  const onSubmit = (values: CheckFormValues) => {
    const data = checkFormToRequest(values);
    const onSuccess = () => onOpenChange(false);
    if (check) {
      updateCheck.mutate({ checkId: check.id, data }, { onSuccess });
      return;
    }
    createCheck.mutate(data, { onSuccess });
  };

  const handleOpenChange = (isOpen: boolean) => {
    // Closing under a running request would hide its outcome.
    if (isPending) return;
    onOpenChange(isOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[640px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="text-primary h-5 w-5" />
            {isEdit ? t("checks.edit") : t("checks.add")}
          </DialogTitle>
          <DialogDescription>{t("checks.description")}</DialogDescription>
        </DialogHeader>

        <FormProvider {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
            <TextField
              name="name"
              label={t("checks.fields.name")}
              control={form.control}
              required
              autoComplete="off"
              maxLength={CHECK_NAME_MAX_LENGTH}
              disabled={isPending}
            />

            <TextareaField
              name="description"
              label={t("checks.fields.description")}
              control={form.control}
              rows={3}
              maxLength={CHECK_TEXT_MAX_LENGTH}
              disabled={isPending}
            />

            <TextareaField
              name="expected"
              label={t("checks.fields.expected")}
              description={t("checks.fields.expectedHint")}
              control={form.control}
              required
              rows={3}
              maxLength={CHECK_TEXT_MAX_LENGTH}
              disabled={isPending}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                name="weight"
                type="number"
                label={t("checks.fields.weight")}
                description={t("checks.fields.weightHint")}
                control={form.control}
                required
                min={CHECK_WEIGHT_MIN}
                max={CHECK_WEIGHT_MAX}
                step={1}
                disabled={isPending}
              />
              <TextField
                name="sortOrder"
                type="number"
                label={t("checks.fields.sortOrder")}
                description={t("checks.fields.sortOrderHint")}
                control={form.control}
                min={CHECK_SORT_ORDER_MIN}
                max={CHECK_SORT_ORDER_MAX}
                step={1}
                disabled={isPending}
              />
            </div>

            {/* The select holds "" for no choice, the form holds null for no category. */}
            <FormField
              control={form.control}
              name="category"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("checks.fields.category")}</FormLabel>
                  <FormControl>
                    <SelectFieldStandalone
                      value={field.value ?? ""}
                      onValueChange={(value) => {
                        const parsed = CheckCategorySchema.safeParse(value);
                        field.onChange(parsed.success ? parsed.data : null);
                      }}
                      options={categoryOptions}
                      placeholder={t("checks.fields.categoryPlaceholder")}
                      clearable
                      disabled={isPending}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              {/* A new outcome fills in the status it implies. */}
              <FormField
                control={form.control}
                name="outcome"
                render={({ field }) => (
                  <FormItem>
                    <div className="flex items-center gap-1 leading-5">
                      <FormLabel>{t("checks.fields.outcome")}</FormLabel>
                      <span className="text-destructive text-sm">*</span>
                    </div>
                    <FormControl>
                      <SelectFieldStandalone
                        value={field.value}
                        onValueChange={(value) => {
                          const parsed = CheckOutcomeSchema.safeParse(value);
                          if (!parsed.success) return;
                          field.onChange(parsed.data);
                          form.setValue("status", deriveCheckStatus(parsed.data), {
                            shouldValidate: true,
                            shouldDirty: true,
                          });
                        }}
                        options={outcomeOptions}
                        placeholder={t("checks.fields.outcomePlaceholder")}
                        disabled={isPending}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <SelectField
                name="status"
                label={t("checks.fields.status")}
                description={t("checks.fields.statusHint")}
                control={form.control}
                required
                options={statusOptions}
                disabled={isPending}
              />
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="ghost"
                onClick={() => handleOpenChange(false)}
                disabled={isPending}
              >
                {tCommon("cancel")}
              </Button>
              <Button type="submit" disabled={isPending || !form.formState.isValid}>
                {isPending ? tCommon("saving") : tCommon("save")}
              </Button>
            </DialogFooter>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  );
}
