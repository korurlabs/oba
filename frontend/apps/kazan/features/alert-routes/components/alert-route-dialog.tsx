"use client";

import { useEffect, useMemo } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { Siren } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { SelectField, TextField, ToggleField } from "@oba/shared/features/form";
import { useCreateAlertRoute, useUpdateAlertRoute } from "../hooks/use-alert-routes";
import { useAlertRouteBoardOptions } from "../hooks/use-alert-route-board-options";
import {
  createAlertRouteFormSchema,
  SYSTEM_KEY_MAX_LENGTH,
  type AlertRoute,
  type AlertRouteFormValues,
} from "../schemas";

interface AlertRouteDialogProps {
  /** The mapping to edit; absent when a mapping is added. */
  route?: AlertRoute;
  /** Systems already mapped, so a duplicate is refused before it is sent. */
  existingSystemKeys: string[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const EMPTY_VALUES: AlertRouteFormValues = { systemKey: "", boardId: "", isDefault: false };

function initialValues(route?: AlertRoute): AlertRouteFormValues {
  if (!route) return EMPTY_VALUES;
  return { systemKey: route.systemKey, boardId: route.boardId, isDefault: route.isDefault };
}

export function AlertRouteDialog({
  route,
  existingSystemKeys,
  open,
  onOpenChange,
}: AlertRouteDialogProps) {
  const t = useTranslations("alertRoutes");
  const tCommon = useTranslations("common");
  const isEdit = !!route;
  const schema = useMemo(() => createAlertRouteFormSchema(t), [t]);
  const createRoute = useCreateAlertRoute();
  const updateRoute = useUpdateAlertRoute();
  const isPending = createRoute.isPending || updateRoute.isPending;
  const boardOptions = useAlertRouteBoardOptions(route);

  const form = useForm<AlertRouteFormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues(route),
    mode: "onChange",
  });

  // The dialog stays mounted between uses, so each opening starts from the
  // mapping it was opened for.
  useEffect(() => {
    if (open) form.reset(initialValues(route));
  }, [open, route, form]);

  // A failed request is surfaced by the mutation's error toast; the dialog
  // then stays open with the entered values.
  const onSubmit = (values: AlertRouteFormValues) => {
    if (route) {
      updateRoute.mutate(
        { routeId: route.id, data: { boardId: values.boardId, isDefault: values.isDefault } },
        { onSuccess: () => onOpenChange(false) },
      );
      return;
    }
    if (existingSystemKeys.includes(values.systemKey)) {
      form.setError("systemKey", { message: t("errors.duplicate") });
      return;
    }
    createRoute.mutate(values, { onSuccess: () => onOpenChange(false) });
  };

  const handleOpenChange = (isOpen: boolean) => {
    // Closing under a running request would hide its outcome.
    if (isPending) return;
    onOpenChange(isOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Siren className="text-primary h-5 w-5" />
            {isEdit ? t("edit") : t("add")}
          </DialogTitle>
          <DialogDescription>{t("subtitle")}</DialogDescription>
        </DialogHeader>

        <FormProvider {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
            <TextField
              name="systemKey"
              label={t("fields.system")}
              description={t("fields.systemHint")}
              placeholder="acme"
              control={form.control}
              required
              autoComplete="off"
              spellCheck={false}
              maxLength={SYSTEM_KEY_MAX_LENGTH}
              className="font-mono"
              disabled={isEdit || isPending}
            />

            <SelectField
              name="boardId"
              label={t("fields.board")}
              control={form.control}
              required
              options={boardOptions.options}
              renderOption={boardOptions.renderOption}
              isLoading={boardOptions.isLoading}
              placeholder={t("fields.boardPlaceholder")}
              searchPlaceholder={t("fields.boardSearch")}
              disabled={isPending}
            />

            <ToggleField
              name="isDefault"
              label={t("fields.default")}
              description={t("fields.defaultHint")}
              control={form.control}
              slotProps={{ switch: { disabled: isPending } }}
            />

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
