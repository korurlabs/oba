"use client";

import { FormProvider, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { KeyRound } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { SelectField, type SelectOption } from "@oba/shared/features/form";
import {
  appGrantFormSchema,
  AppGrantTargetTypeSchema,
  GrantedAppSchema,
  type AppGrantFormValues,
} from "@oba/shared/features/organizations/schemas";
import { toAppGrantRequest } from "@oba/shared/features/organizations/lib/organization-request";
import { useCreateAppGrant } from "../hooks/use-app-access";
import { useOrganizationGroups } from "../hooks/use-organization-groups";
import { useOrganizationMembers } from "../hooks/use-organization-members";
import { useAppName } from "./app-badges";

const DEFAULT_VALUES: Partial<AppGrantFormValues> = { targetType: "USER", targetId: "" };

interface GrantAppDialogProps {
  organizationId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Grants one app to one member or one group. The form holds a single target
 * id with its type, so the request always names exactly one target.
 */
export function GrantAppDialog({ organizationId, open, onOpenChange }: GrantAppDialogProps) {
  const t = useTranslations("organization");
  const appName = useAppName();
  const schema = useMemo(() => appGrantFormSchema(t), [t]);
  const createGrant = useCreateAppGrant(organizationId);
  const isPending = createGrant.isPending;

  const form = useForm<AppGrantFormValues>({
    resolver: zodResolver(schema),
    defaultValues: DEFAULT_VALUES,
    mode: "onChange",
  });
  const targetType = useWatch({ control: form.control, name: "targetType" });

  // A member id is not a group id: the picked target is cleared whenever the
  // target type changes, so the form never sends an id of the other kind.
  useEffect(() => {
    const subscription = form.watch((_, { name }) => {
      if (name === "targetType") {
        form.setValue("targetId", "", { shouldValidate: false });
      }
    });
    return () => subscription.unsubscribe();
  }, [form]);

  const {
    data: membersResponse,
    isLoading: isMembersLoading,
    isError: isMembersError,
  } = useOrganizationMembers(organizationId);
  const {
    data: groupsResponse,
    isLoading: isGroupsLoading,
    isError: isGroupsError,
  } = useOrganizationGroups(organizationId);

  const appOptions = useMemo<SelectOption[]>(
    () => GrantedAppSchema.options.map((app) => ({ value: app, label: appName(app) })),
    [appName]
  );

  const targetTypeOptions = useMemo<SelectOption[]>(
    () =>
      AppGrantTargetTypeSchema.options.map((type) => ({
        value: type,
        label: type === "USER" ? t("access.member") : t("access.group"),
      })),
    [t]
  );

  // Only active members are offered: a disabled member can use no app, so a
  // grant to them would change nothing until they are enabled again.
  const memberOptions = useMemo<SelectOption[]>(
    () =>
      (membersResponse?.data ?? [])
        .filter((member) => member.status === "ACTIVE")
        .map((member) => ({
          value: member.userId,
          label: `${member.firstName} ${member.lastName} (${member.email})`,
        })),
    [membersResponse]
  );

  const groupOptions = useMemo<SelectOption[]>(
    () => (groupsResponse?.data ?? []).map((group) => ({ value: group.id, label: group.name })),
    [groupsResponse]
  );

  const isUserTarget = targetType === "USER";
  const isTargetLoading = isUserTarget ? isMembersLoading : isGroupsLoading;
  const isTargetError = isUserTarget ? isMembersError : isGroupsError;
  const targetOptions = isUserTarget ? memberOptions : groupOptions;
  const targetPlaceholder = isTargetError
    ? isUserTarget
      ? t("access.membersLoadError")
      : t("access.groupsLoadError")
    : isUserTarget
      ? targetOptions.length === 0 && !isTargetLoading
        ? t("access.noMembers")
        : t("access.memberPlaceholder")
      : t("access.groupPlaceholder");

  const closeAndReset = () => {
    form.reset(DEFAULT_VALUES);
    onOpenChange(false);
  };

  // A failed request is shown by the toast of useCreateAppGrant, and the
  // dialog stays open with the entered values.
  const onSubmit = (values: AppGrantFormValues) => {
    createGrant.mutate(toAppGrantRequest(values), { onSuccess: closeAndReset });
  };

  const handleOpenChange = (isOpen: boolean) => {
    // Closing resets the form, which must not happen under a running request.
    if (isPending) return;
    if (!isOpen) form.reset(DEFAULT_VALUES);
    onOpenChange(isOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <KeyRound className="text-primary h-5 w-5" />
            {t("access.grantTitle")}
          </DialogTitle>
          <DialogDescription>{t("access.grantDescription")}</DialogDescription>
        </DialogHeader>

        <FormProvider {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
            <SelectField
              name="app"
              label={t("access.app")}
              placeholder={t("access.appPlaceholder")}
              control={form.control}
              options={appOptions}
              required
              disabled={isPending}
            />

            <SelectField
              name="targetType"
              label={t("access.targetType")}
              control={form.control}
              options={targetTypeOptions}
              required
              disabled={isPending}
            />

            <SelectField
              key={targetType}
              name="targetId"
              label={isUserTarget ? t("access.member") : t("access.group")}
              placeholder={targetPlaceholder}
              control={form.control}
              options={targetOptions}
              isLoading={isTargetLoading}
              required
              disabled={isPending || isTargetError}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="ghost"
                onClick={() => handleOpenChange(false)}
                disabled={isPending}
              >
                {t("access.cancel")}
              </Button>
              <Button type="submit" disabled={isPending || !form.formState.isValid}>
                {isPending ? t("access.granting") : t("access.grant")}
              </Button>
            </DialogFooter>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  );
}
