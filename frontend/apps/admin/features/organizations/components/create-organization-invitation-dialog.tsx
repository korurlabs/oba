"use client";

import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useCallback, useMemo } from "react";
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
import {
  SelectField,
  TextField,
  type SelectOption,
} from "@oba/shared/features/form";
import { UserPlus, type LucideIcon } from "lucide-react";
import {
  createInvitationFormSchema,
  type CreateInvitationForm,
  type OrganizationRole,
} from "@oba/shared/features/organizations/schemas";
import { useCreateInvitation } from "../hooks/use-invitations";
import { ROLE_OPTIONS } from "@oba/shared/features/organizations/lib/organization-options";

interface CreateInvitationDialogProps {
  organizationId: string;
  currentUserRole: OrganizationRole | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CreateInvitationDialog({
  organizationId,
  currentUserRole,
  open,
  onOpenChange,
}: CreateInvitationDialogProps) {
  const t = useTranslations("organization");
  const schema = useMemo(() => createInvitationFormSchema(t), [t]);
  const createInvitation = useCreateInvitation(organizationId);

  const form = useForm<CreateInvitationForm>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", role: "MEMBER" },
    mode: "onChange",
  });

  // A failed request is surfaced by the toast in useCreateInvitation's
  // onError; the dialog then stays open with the entered values.
  const onSubmit = (data: CreateInvitationForm) => {
    createInvitation.mutate(data, {
      onSuccess: () => {
        form.reset();
        onOpenChange(false);
      },
    });
  };

  // OWNER is offered only to an OWNER. The role icon travels in `meta` and is
  // drawn by `renderRoleOption`.
  const roleOptions = useMemo<SelectOption[]>(
    () =>
      ROLE_OPTIONS
        .filter((opt) => currentUserRole === "OWNER" || opt.value !== "OWNER")
        .map(({ value, icon }) => ({
          value,
          label: t(`members.roles.${value}`),
          meta: { icon },
        })),
    [t, currentUserRole],
  );

  const renderRoleOption = useCallback((option: SelectOption) => {
    const Icon = option.meta?.icon as LucideIcon | undefined;
    return (
      <div className="flex items-center gap-2">
        {Icon && <Icon className="h-4 w-4" />}
        <span>{option.label}</span>
      </div>
    );
  }, []);

  const handleOpenChange = (isOpen: boolean) => {
    // Closing resets the form, which must not happen under a running request.
    if (createInvitation.isPending) return;
    if (!isOpen) form.reset();
    onOpenChange(isOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserPlus className="h-5 w-5 text-primary" />
            {t("invitations.createTitle")}
          </DialogTitle>
          <DialogDescription>
            {t("invitations.createDescription")}
          </DialogDescription>
        </DialogHeader>

        <FormProvider {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-4 py-2"
          >
            <TextField
              name="email"
              label={t("invitations.email")}
              placeholder={t("invitations.emailPlaceholder")}
              control={form.control}
              required
              disabled={createInvitation.isPending}
            />

            <SelectField
              name="role"
              label={t("invitations.role")}
              control={form.control}
              options={roleOptions}
              renderOption={renderRoleOption}
              disabled={createInvitation.isPending}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="ghost"
                onClick={() => handleOpenChange(false)}
                disabled={createInvitation.isPending}
              >
                {t("invitations.cancel")}
              </Button>
              <Button
                type="submit"
                disabled={createInvitation.isPending || !form.formState.isValid}
              >
                {createInvitation.isPending
                  ? t("invitations.sending")
                  : t("invitations.send")}
              </Button>
            </DialogFooter>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  );
}
