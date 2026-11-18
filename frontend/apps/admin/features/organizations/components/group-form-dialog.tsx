"use client";

import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { TextareaField, TextField } from "@oba/shared/features/form";
import { UsersRound } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import {
  organizationGroupFormSchema,
  type OrganizationGroup,
  type OrganizationGroupFormValues,
} from "@oba/shared/features/organizations/schemas";
import { toOrganizationGroupRequest } from "@oba/shared/features/organizations/lib/organization-request";
import {
  useCreateOrganizationGroup,
  useUpdateOrganizationGroup,
} from "../hooks/use-organization-groups";

/** The API's answer when another group of the organization has the name. */
const GROUP_NAME_TAKEN = "APP_STA_002";

/** The dialog shows a taken name on the name field, not in a toast. */
const FORM_ERROR_CODES = [GROUP_NAME_TAKEN] as const;

interface GroupFormDialogProps {
  organizationId: string;
  /** The group to rename; without it the dialog creates a group. */
  group: OrganizationGroup | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Create and rename dialog of a custom group. */
export function GroupFormDialog({
  organizationId,
  group,
  open,
  onOpenChange,
}: GroupFormDialogProps) {
  const t = useTranslations("organization");
  const schema = useMemo(() => organizationGroupFormSchema(t), [t]);
  const createGroup = useCreateOrganizationGroup(organizationId, {
    formErrorCodes: FORM_ERROR_CODES,
  });
  const updateGroup = useUpdateOrganizationGroup(organizationId, {
    formErrorCodes: FORM_ERROR_CODES,
  });
  const isRename = group !== null;
  const isPending = createGroup.isPending || updateGroup.isPending;

  const form = useForm<OrganizationGroupFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: group?.name ?? "",
      description: group?.description ?? "",
    },
    mode: "onChange",
  });

  // useForm keeps the default values of its first render, and the dialog stays
  // mounted between openings. Every opening therefore starts from the group as
  // it is now, so a Save after a rename cannot send the old name back.
  useEffect(() => {
    if (open) {
      form.reset({ name: group?.name ?? "", description: group?.description ?? "" });
    }
  }, [open, group, form]);

  const closeAndReset = () => {
    form.reset();
    onOpenChange(false);
  };

  // A taken name is shown on the name field: the hooks skip their toast for
  // that code. Every other failure is shown by the hook's toast, and the
  // dialog stays open with the entered values.
  const showNameTaken = (error: Error) => {
    if (isApiError(error, GROUP_NAME_TAKEN)) {
      form.setError("name", { type: "server", message: t("groups.nameTaken") });
    }
  };

  const onSubmit = (values: OrganizationGroupFormValues) => {
    const data = toOrganizationGroupRequest(values);
    if (group) {
      updateGroup.mutate(
        { groupId: group.id, data },
        { onSuccess: closeAndReset, onError: showNameTaken }
      );
    } else {
      createGroup.mutate(data, { onSuccess: closeAndReset, onError: showNameTaken });
    }
  };

  const handleOpenChange = (isOpen: boolean) => {
    // Closing resets the form, which must not happen under a running request.
    if (isPending) return;
    if (!isOpen) form.reset();
    onOpenChange(isOpen);
  };

  const submitLabel = isRename
    ? isPending
      ? t("groups.saving")
      : t("groups.save")
    : isPending
      ? t("groups.creating")
      : t("groups.create");

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UsersRound className="text-primary h-5 w-5" />
            {isRename ? t("groups.renameTitle") : t("groups.createTitle")}
          </DialogTitle>
          <DialogDescription>
            {isRename ? t("groups.renameDescription") : t("groups.createDescription")}
          </DialogDescription>
        </DialogHeader>

        <FormProvider {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
            <TextField
              name="name"
              label={t("groups.nameLabel")}
              placeholder={t("groups.namePlaceholder")}
              control={form.control}
              required
              disabled={isPending}
            />

            <TextareaField
              name="description"
              label={t("groups.descriptionLabel")}
              placeholder={t("groups.descriptionPlaceholder")}
              control={form.control}
              rows={3}
              disabled={isPending}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="ghost"
                onClick={() => handleOpenChange(false)}
                disabled={isPending}
              >
                {t("groups.cancel")}
              </Button>
              <Button type="submit" disabled={isPending || !form.formState.isValid}>
                {submitLabel}
              </Button>
            </DialogFooter>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  );
}
