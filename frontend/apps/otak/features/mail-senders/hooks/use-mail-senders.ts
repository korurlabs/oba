"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { createMailSender, deleteMailSender, listMailSenders } from "../api";
import { mailSenderErrorText } from "../lib/mail-sender-error";
import type { MailSenderRequest } from "../schemas";
import { MAIL_SENDER_KEYS } from "./query-keys";

export { MAIL_SENDER_KEYS };

/** Every mail sender of the organization; waits for the organization. */
export function useMailSenders() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: MAIL_SENDER_KEYS.list(organizationId ?? ""),
    queryFn: () => listMailSenders(),
    enabled: !!organizationId,
  });
}

/** A sender change refreshes every mail sender query of the organization. */
function useInvalidateMailSenders() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: MAIL_SENDER_KEYS.all(organizationId) });
    }
  };
}

export function useCreateMailSender() {
  const invalidate = useInvalidateMailSenders();
  const t = useTranslations("mailSenders");

  return useMutation({
    mutationFn: (data: MailSenderRequest) => createMailSender(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(mailSenderErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useDeleteMailSender() {
  const invalidate = useInvalidateMailSenders();
  const t = useTranslations("mailSenders");

  return useMutation({
    mutationFn: (mailSenderId: string) => deleteMailSender(mailSenderId),
    onSuccess: () => {
      toast.success(t("toast.deleted"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(mailSenderErrorText(error, t, "toast.deleteError"));
    },
  });
}
