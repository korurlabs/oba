"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import {
  createMailTemplate,
  deleteMailTemplate,
  getMailTemplate,
  listMailTemplates,
  updateMailTemplate,
} from "../api";
import { mailTemplateErrorText } from "../lib/mail-template-error";
import type { MailTemplateQueryRequest, MailTemplateRequest } from "../schemas";
import { MAIL_TEMPLATE_KEYS } from "./query-keys";

export { MAIL_TEMPLATE_KEYS };

/** One page of the organization's mail templates; waits for the organization. */
export function useMailTemplates(params: MailTemplateQueryRequest) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: MAIL_TEMPLATE_KEYS.list(organizationId ?? "", params),
    queryFn: () => listMailTemplates(params),
    enabled: !!organizationId,
    // A new filter or page keeps the current rows until its answer arrives,
    // but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/** One mail template; waits for the organization and an id. */
export function useMailTemplate(
  mailTemplateId: string,
  { enabled = true }: { enabled?: boolean } = {}
) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: MAIL_TEMPLATE_KEYS.detail(organizationId ?? "", mailTemplateId),
    queryFn: () => getMailTemplate(mailTemplateId),
    enabled: enabled && !!organizationId && mailTemplateId !== "",
  });
}

/** A mail template change refreshes every mail template query of the organization. */
function useInvalidateMailTemplates() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: MAIL_TEMPLATE_KEYS.all(organizationId) });
    }
  };
}

export function useCreateMailTemplate() {
  const invalidate = useInvalidateMailTemplates();
  const t = useTranslations("mailTemplates");

  return useMutation({
    mutationFn: (data: MailTemplateRequest) => createMailTemplate(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(mailTemplateErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateMailTemplate() {
  const invalidate = useInvalidateMailTemplates();
  const t = useTranslations("mailTemplates");

  return useMutation({
    mutationFn: ({ mailTemplateId, data }: { mailTemplateId: string; data: MailTemplateRequest }) =>
      updateMailTemplate(mailTemplateId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(mailTemplateErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useDeleteMailTemplate() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("mailTemplates");

  return useMutation({
    mutationFn: (mailTemplateId: string) => deleteMailTemplate(mailTemplateId),
    onSuccess: (_result, mailTemplateId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({
          queryKey: MAIL_TEMPLATE_KEYS.detail(organizationId, mailTemplateId),
        });
        void queryClient.invalidateQueries({ queryKey: MAIL_TEMPLATE_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(mailTemplateErrorText(error, t, "toast.deleteError"));
    },
  });
}
