"use client";

import { FormProvider, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { Bot } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { SelectField, TextField, type SelectOption } from "@oba/shared/features/form";
import { isApiError } from "@oba/shared/api/axios";
import { GrantedAppSchema } from "@oba/shared/features/organizations/schemas";
import { useAppName } from "@/features/organizations/components/app-badges";
import {
  agentFormSchema,
  AgentRoleSchema,
  type Agent,
  type AgentFormValues,
  type AgentProvider,
} from "../schemas";
import { toAgentCreateRequest, toAgentUpdateRequest } from "../lib/agent-request";
import { useAgentOptions, useCreateAgent, useUpdateAgent } from "../hooks/use-agents";

/** The API's answer when another agent of the organization has the name. */
const AGENT_NAME_TAKEN = "AGT_STA_001";

/** The dialog shows a taken name on the name field, not in a toast. */
const FORM_ERROR_CODES = [AGENT_NAME_TAKEN] as const;

/** The form's values for `agent`, or an empty form to create one. */
function formValuesOf(agent: Agent | null): Partial<AgentFormValues> {
  if (!agent) return { name: "", apps: [] };
  const knownApps = GrantedAppSchema.options;
  return {
    name: agent.name,
    flow: agent.flow,
    provider: agent.provider,
    role: agent.role,
    // An app this build does not know cannot be sent back; the API keeps
    // only the apps the update names.
    apps: agent.apps.filter((app): app is AgentFormValues["apps"][number] =>
      (knownApps as readonly string[]).includes(app)
    ),
  };
}

interface AgentFormDialogProps {
  organizationId: string;
  /** The agent to edit; without it the dialog creates an agent. */
  agent: Agent | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Create and edit dialog of an agent. Flow and provider are chosen at
 * creation from the combinations the API offers, and are read-only after:
 * another flow or provider is another agent.
 */
export function AgentFormDialog({
  organizationId,
  agent,
  open,
  onOpenChange,
}: AgentFormDialogProps) {
  const t = useTranslations("agents");
  const appName = useAppName();
  const schema = useMemo(() => agentFormSchema(t), [t]);
  const isEdit = agent !== null;
  const createAgent = useCreateAgent(organizationId, { formErrorCodes: FORM_ERROR_CODES });
  const updateAgent = useUpdateAgent(organizationId, { formErrorCodes: FORM_ERROR_CODES });
  const isPending = createAgent.isPending || updateAgent.isPending;
  const {
    data: optionsResponse,
    isLoading: isOptionsLoading,
    isError: isOptionsError,
  } = useAgentOptions(organizationId, open && !isEdit);

  const form = useForm<AgentFormValues>({
    resolver: zodResolver(schema),
    defaultValues: formValuesOf(agent),
    mode: "onChange",
  });
  const flow = useWatch({ control: form.control, name: "flow" });

  // useForm keeps the default values of its first render, and the dialog stays
  // mounted between openings. Every opening therefore starts from the agent as
  // it is now.
  useEffect(() => {
    if (open) form.reset(formValuesOf(agent));
  }, [open, agent, form]);

  const flowOffers = useMemo(() => optionsResponse?.data?.flows ?? [], [optionsResponse]);

  // A provider is valid only for the flow it was picked under: a flow change
  // clears it unless the new flow accepts it too.
  useEffect(() => {
    const subscription = form.watch((values, { name }) => {
      if (name !== "flow" || isEdit) return;
      const accepted = flowOffers.find((offer) => offer.flow === values.flow)?.providers ?? [];
      if (values.provider && !accepted.includes(values.provider)) {
        form.setValue("provider", "" as AgentProvider, { shouldValidate: true });
      }
    });
    return () => subscription.unsubscribe();
  }, [form, flowOffers, isEdit]);

  const flowOptions = useMemo<SelectOption[]>(() => {
    if (agent) return [{ value: agent.flow, label: t(`flows.${agent.flow}`) }];
    return flowOffers.map((offer) => ({ value: offer.flow, label: t(`flows.${offer.flow}`) }));
  }, [agent, flowOffers, t]);

  const providerOptions = useMemo<SelectOption[]>(() => {
    if (agent) return [{ value: agent.provider, label: t(`providers.${agent.provider}`) }];
    const providers = flowOffers.find((offer) => offer.flow === flow)?.providers ?? [];
    return providers.map((provider) => ({ value: provider, label: t(`providers.${provider}`) }));
  }, [agent, flowOffers, flow, t]);

  const roleOptions = useMemo<SelectOption[]>(
    () => AgentRoleSchema.options.map((role) => ({ value: role, label: t(`roles.${role}`) })),
    [t]
  );

  const appOptions = useMemo<SelectOption[]>(
    () => GrantedAppSchema.options.map((app) => ({ value: app, label: appName(app) })),
    [appName]
  );

  const optionsPlaceholder = isOptionsError ? t("form.optionsLoadError") : undefined;

  const closeAndReset = () => {
    form.reset(formValuesOf(null));
    onOpenChange(false);
  };

  // A taken name is shown on the name field: the hooks skip their toast for
  // that code. Every other failure is shown by the hook's toast, and the
  // dialog stays open with the entered values.
  const showNameTaken = (error: Error) => {
    if (isApiError(error, AGENT_NAME_TAKEN)) {
      form.setError("name", { type: "server", message: t("form.nameTaken") });
    }
  };

  const onSubmit = (values: AgentFormValues) => {
    if (agent) {
      updateAgent.mutate(
        { agentId: agent.id, data: toAgentUpdateRequest(values) },
        { onSuccess: closeAndReset, onError: showNameTaken }
      );
    } else {
      createAgent.mutate(toAgentCreateRequest(values), {
        onSuccess: closeAndReset,
        onError: showNameTaken,
      });
    }
  };

  const handleOpenChange = (isOpen: boolean) => {
    // Closing resets the form, which must not happen under a running request.
    if (isPending) return;
    if (!isOpen) form.reset(formValuesOf(null));
    onOpenChange(isOpen);
  };

  const submitLabel = isEdit
    ? isPending
      ? t("form.saving")
      : t("form.save")
    : isPending
      ? t("form.creating")
      : t("form.create");

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Bot className="text-primary h-5 w-5" />
            {isEdit ? t("form.editTitle") : t("form.createTitle")}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? t("form.editDescription") : t("form.createDescription")}
          </DialogDescription>
        </DialogHeader>

        <FormProvider {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
            <TextField
              name="name"
              label={t("form.nameLabel")}
              placeholder={t("form.namePlaceholder")}
              control={form.control}
              required
              disabled={isPending}
            />

            <SelectField
              name="flow"
              label={t("form.flowLabel")}
              placeholder={optionsPlaceholder ?? t("form.flowPlaceholder")}
              control={form.control}
              options={flowOptions}
              isLoading={!isEdit && isOptionsLoading}
              required
              disabled={isPending || isEdit || isOptionsError}
            />

            <SelectField
              key={flow ?? "none"}
              name="provider"
              label={t("form.providerLabel")}
              placeholder={optionsPlaceholder ?? t("form.providerPlaceholder")}
              control={form.control}
              options={providerOptions}
              isLoading={!isEdit && isOptionsLoading}
              required
              disabled={isPending || isEdit || isOptionsError || !flow}
            />

            {isEdit && <p className="text-muted-foreground text-sm">{t("form.fixedNote")}</p>}
            {flow === "API" && <p className="text-muted-foreground text-sm">{t("apiFlowNote")}</p>}

            <SelectField
              name="role"
              label={t("form.roleLabel")}
              placeholder={t("form.rolePlaceholder")}
              control={form.control}
              options={roleOptions}
              required
              disabled={isPending}
            />

            <SelectField
              isMulti
              name="apps"
              label={t("form.appsLabel")}
              placeholder={t("form.appsPlaceholder")}
              control={form.control}
              options={appOptions}
              required
              disabled={isPending}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="ghost"
                onClick={() => handleOpenChange(false)}
                disabled={isPending}
              >
                {t("cancel")}
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
