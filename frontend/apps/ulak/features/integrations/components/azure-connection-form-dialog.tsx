"use client";

import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Cloud } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { TextField } from "@oba/shared/features/form";
import { isApiError } from "@oba/shared/api/axios";
import {
  azureConnectionFormSchema,
  type AzureConnection,
  type AzureConnectionFormValues,
} from "../schemas";
import {
  toAzureConnectionFormValues,
  toAzureConnectionRequest,
} from "../lib/azure-connection-request";
import {
  AZURE_CONNECTION_IDENTITY_TAKEN,
  AZURE_CONNECTION_NAME_TAKEN,
  AZURE_CONNECTION_NOT_FOUND,
  AZURE_CONNECTIONS_UNAVAILABLE,
  VALIDATION_FAILED,
  useCreateAzureConnection,
  useUpdateAzureConnection,
} from "../hooks/use-azure-connections";

/**
 * The dialog shows these answers on the form, not in a toast: a taken name or
 * identity on its field, the backend's field messages on theirs, and a
 * deployment without an issuer above the buttons.
 */
const FORM_ERROR_CODES = [
  AZURE_CONNECTIONS_UNAVAILABLE,
  AZURE_CONNECTION_IDENTITY_TAKEN,
  AZURE_CONNECTION_NAME_TAKEN,
  VALIDATION_FAILED,
] as const;

const FORM_FIELDS: readonly (keyof AzureConnectionFormValues)[] = ["name", "tenantId", "clientId"];

function isFormField(name: string): name is keyof AzureConnectionFormValues {
  return (FORM_FIELDS as readonly string[]).includes(name);
}

interface AzureConnectionFormDialogProps {
  organizationId: string;
  /** The connection to edit; without it the dialog creates one. */
  connection: AzureConnection | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Create and edit dialog of an Azure connection. A list row carries the whole
 * connection, so editing needs no extra request.
 */
export function AzureConnectionFormDialog({
  organizationId,
  connection,
  open,
  onOpenChange,
}: AzureConnectionFormDialogProps) {
  const t = useTranslations("integrations.azure");
  const createConnection = useCreateAzureConnection(organizationId, {
    formErrorCodes: FORM_ERROR_CODES,
  });
  const updateConnection = useUpdateAzureConnection(organizationId, {
    formErrorCodes: FORM_ERROR_CODES,
  });
  const isEdit = connection !== null;
  const isPending = createConnection.isPending || updateConnection.isPending;

  const handleOpenChange = (isOpen: boolean) => {
    // Closing drops the entered values, which must not happen under a running request.
    if (isPending) return;
    onOpenChange(isOpen);
  };

  const close = () => onOpenChange(false);

  const onSubmit = (values: AzureConnectionFormValues, onError: (error: Error) => void) => {
    const data = toAzureConnectionRequest(values);
    if (connection) {
      updateConnection.mutate({ connectionId: connection.id, data }, { onSuccess: close, onError });
    } else {
      createConnection.mutate(data, { onSuccess: close, onError });
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Cloud className="text-primary h-5 w-5" />
            {isEdit ? t("form.editTitle") : t("form.createTitle")}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? t("form.editDescription") : t("form.createDescription")}
          </DialogDescription>
        </DialogHeader>
        {/* Keyed by the connection, so the form starts from its stored values. */}
        <AzureConnectionForm
          key={connection?.id ?? "new"}
          connection={connection}
          isEdit={isEdit}
          isPending={isPending}
          onSubmit={onSubmit}
          onCancel={() => handleOpenChange(false)}
          onGone={close}
        />
      </DialogContent>
    </Dialog>
  );
}

/**
 * The fields of the dialog. Radix unmounts the dialog content while it is
 * closed, so every opening mounts a fresh form with the values of that moment.
 */
function AzureConnectionForm({
  connection,
  isEdit,
  isPending,
  onSubmit,
  onCancel,
  onGone,
}: {
  connection: AzureConnection | null;
  isEdit: boolean;
  isPending: boolean;
  onSubmit: (values: AzureConnectionFormValues, onError: (error: Error) => void) => void;
  onCancel: () => void;
  /** Closes the dialog when the edited connection no longer exists. */
  onGone: () => void;
}) {
  const t = useTranslations("integrations.azure");
  const schema = useMemo(() => azureConnectionFormSchema(t), [t]);

  const form = useForm<AzureConnectionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: toAzureConnectionFormValues(connection),
    mode: "onChange",
  });

  // The form codes are shown here: the hooks skip their toast for them. A gone
  // connection closes the dialog after the hook's toast. Every other failure
  // is shown by the hook's toast, and the dialog keeps the entered values.
  const showServerError = (error: Error) => {
    if (isApiError(error, AZURE_CONNECTION_NAME_TAKEN)) {
      form.setError("name", { type: "server", message: t("form.nameTaken") });
      return;
    }
    if (isApiError(error, AZURE_CONNECTION_IDENTITY_TAKEN)) {
      form.setError("clientId", { type: "server", message: t("form.identityTaken") });
      return;
    }
    if (isApiError(error, AZURE_CONNECTIONS_UNAVAILABLE)) {
      form.setError("root.server", { type: "server", message: t("form.unavailable") });
      return;
    }
    if (isApiError(error, VALIDATION_FAILED)) {
      const fieldErrors = Object.entries(error.errorValues).filter(([name]) => isFormField(name));
      for (const [name, message] of fieldErrors) {
        form.setError(name as keyof AzureConnectionFormValues, {
          type: "server",
          message: typeof message === "string" ? message : t("createError"),
        });
      }
      if (fieldErrors.length === 0) toast.error(isEdit ? t("updateError") : t("createError"));
      return;
    }
    if (isApiError(error, AZURE_CONNECTION_NOT_FOUND)) onGone();
  };

  const submitLabel = isEdit
    ? isPending
      ? t("form.saving")
      : t("form.save")
    : isPending
      ? t("form.creating")
      : t("form.create");
  const rootError = form.formState.errors.root?.server?.message;

  return (
    <FormProvider {...form}>
      <form
        onSubmit={form.handleSubmit((values) => onSubmit(values, showServerError))}
        className="space-y-4 py-2"
      >
        <TextField
          name="name"
          label={t("form.nameLabel")}
          placeholder={t("form.namePlaceholder")}
          control={form.control}
          required
          disabled={isPending}
        />
        <TextField
          name="tenantId"
          label={t("form.tenantIdLabel")}
          placeholder={t("form.tenantIdPlaceholder")}
          control={form.control}
          required
          disabled={isPending}
          className="font-mono"
        />
        <TextField
          name="clientId"
          label={t("form.clientIdLabel")}
          placeholder={t("form.clientIdPlaceholder")}
          description={t("form.clientIdDescription")}
          control={form.control}
          disabled={isPending}
          className="font-mono"
        />

        {rootError && (
          <p className="text-error text-sm" role="alert">
            {rootError}
          </p>
        )}

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isPending}>
            {t("cancel")}
          </Button>
          <Button type="submit" disabled={isPending || !form.formState.isValid}>
            {submitLabel}
          </Button>
        </DialogFooter>
      </form>
    </FormProvider>
  );
}
