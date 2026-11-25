"use client";

import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Workflow } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { TextField } from "@oba/shared/features/form";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { agentRoutineFormSchema, type Agent, type AgentRoutineFormValues } from "../schemas";
import { routineFieldsOf } from "../lib/routine-fields";
import { toAgentRoutineRequest } from "../lib/agent-request";
import { useRemoveAgentRoutine, useSetAgentRoutine } from "../hooks/use-agents";
import { AgentFireStatusBadge } from "./agent-badges";

/** The API's answer to a routine id in the wrong format for the provider. */
const ROUTINE_ID_FORMAT = "AGT_VAL_002";

/** The dialog shows a malformed id on the id field, not in a toast. */
const FORM_ERROR_CODES = [ROUTINE_ID_FORMAT] as const;

const EMPTY_VALUES: AgentRoutineFormValues = { routineId: "", routineSecret: "" };

interface AgentRoutineDialogProps {
  organizationId: string;
  /** The ROUTINE agent whose routine is managed; null while the dialog is closed. */
  agent: Agent | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Sets, replaces and removes the routine Oba fires when a ticket is assigned
 * to a ROUTINE agent, and shows the last fire. The secret is write-only: the
 * API stores it encrypted and returns only its prefix, so the form always
 * asks for it again.
 */
export function AgentRoutineDialog({
  organizationId,
  agent,
  open,
  onOpenChange,
}: AgentRoutineDialogProps) {
  const t = useTranslations("agents");
  const { formatDateTime } = useDateFormat();
  const fields = routineFieldsOf(agent?.provider ?? "ANTHROPIC");
  const schema = useMemo(() => agentRoutineFormSchema(t, fields.idPattern), [t, fields.idPattern]);
  const setRoutine = useSetAgentRoutine(organizationId, { formErrorCodes: FORM_ERROR_CODES });
  const removeRoutine = useRemoveAgentRoutine(organizationId);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const isPending = setRoutine.isPending || removeRoutine.isPending;

  const form = useForm<AgentRoutineFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { routineId: agent?.routineId ?? "", routineSecret: "" },
    mode: "onChange",
  });

  // The dialog stays mounted between openings; each opening starts from the
  // agent's current routine id and an empty secret.
  useEffect(() => {
    if (open) {
      form.reset({ routineId: agent?.routineId ?? "", routineSecret: "" });
      setConfirmRemove(false);
    }
  }, [open, agent, form]);

  const close = () => {
    form.reset(EMPTY_VALUES);
    setConfirmRemove(false);
    onOpenChange(false);
  };

  const handleOpenChange = (isOpen: boolean) => {
    if (isPending) return;
    if (!isOpen) {
      close();
      return;
    }
    onOpenChange(isOpen);
  };

  // A malformed id is shown on the id field: the hook skips its toast for
  // that code. Every other failure is shown by the hook's toast, and the
  // dialog stays open with the entered values.
  const showIdFormat = (error: Error) => {
    if (isApiError(error, ROUTINE_ID_FORMAT)) {
      form.setError("routineId", { type: "server", message: t("routine.idFormat") });
    }
  };

  const onSubmit = (values: AgentRoutineFormValues) => {
    if (!agent) return;
    setRoutine.mutate(
      { agentId: agent.id, data: toAgentRoutineRequest(values) },
      { onSuccess: close, onError: showIdFormat }
    );
  };

  const remove = () => {
    if (!agent) return;
    removeRoutine.mutate(agent.id, { onSuccess: close });
  };

  const hasRoutine = !!agent?.routineId;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Workflow className="text-primary h-5 w-5" />
            {t("routine.title", { name: agent?.name ?? "" })}
          </DialogTitle>
          <DialogDescription>{t("routine.description")}</DialogDescription>
        </DialogHeader>

        {hasRoutine && agent && (
          <dl className="grid grid-cols-[8rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
            <dt className="text-muted-foreground">{t(`routine.${fields.secretLabelKey}`)}</dt>
            <dd className="font-mono">{agent.routineSecretPrefix ?? ""}…</dd>
            <dt className="text-muted-foreground">{t("routine.lastFired")}</dt>
            <dd className="flex flex-wrap items-center gap-2">
              {agent.lastFiredAt ? formatDateTime(agent.lastFiredAt) : t("routine.neverFired")}
              {agent.lastFireStatus && <AgentFireStatusBadge status={agent.lastFireStatus} />}
            </dd>
            {agent.lastSessionUrl && (
              <>
                <dt className="text-muted-foreground">{t("routine.lastSession")}</dt>
                <dd>
                  <a
                    href={agent.lastSessionUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary break-all underline-offset-4 hover:underline"
                  >
                    {t("routine.openSession")}
                  </a>
                </dd>
              </>
            )}
          </dl>
        )}

        <FormProvider {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
            <TextField
              name="routineId"
              label={t(`routine.${fields.idLabelKey}`)}
              placeholder={fields.idPlaceholder}
              control={form.control}
              required
              autoComplete="off"
              disabled={isPending}
            />

            <TextField
              name="routineSecret"
              type="password"
              label={t(`routine.${fields.secretLabelKey}`)}
              description={hasRoutine ? t("routine.secretReplaceHint") : t("routine.secretHint")}
              control={form.control}
              required
              autoComplete="off"
              disabled={isPending}
            />

            {confirmRemove && (
              <p className="text-error text-sm" role="alert">
                {t("routine.removeConfirm")}
              </p>
            )}

            <DialogFooter>
              {hasRoutine &&
                (confirmRemove ? (
                  <Button type="button" variant="destructive" onClick={remove} disabled={isPending}>
                    {removeRoutine.isPending
                      ? t("routine.removing")
                      : t("routine.removeConfirmButton")}
                  </Button>
                ) : (
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setConfirmRemove(true)}
                    disabled={isPending}
                  >
                    {t("routine.remove")}
                  </Button>
                ))}
              <Button type="submit" disabled={isPending || !form.formState.isValid || !agent}>
                {setRoutine.isPending ? t("routine.saving") : t("routine.save")}
              </Button>
            </DialogFooter>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  );
}
