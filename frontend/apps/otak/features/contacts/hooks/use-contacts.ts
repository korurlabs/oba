"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { RELATION_KEYS } from "@/features/relations/hooks/query-keys";
import { createContact, deleteContact, listContacts, updateContact } from "../api";
import { contactErrorText } from "../lib/contact-error";
import type { ContactRequest } from "../schemas";
import { CONTACT_KEYS } from "./query-keys";

export { CONTACT_KEYS };

/** Every contact of the relation, the main contact first, then by name; waits for the organization. */
export function useContacts(relationId: string) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: CONTACT_KEYS.list(organizationId ?? "", relationId),
    queryFn: () => listContacts(relationId),
    enabled: !!organizationId && relationId !== "",
  });
}

/**
 * A contact change refreshes its relation's contact list and the relation
 * lists, whose Contact column shows the main contact's name.
 */
function useInvalidateContacts(relationId: string) {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({
        queryKey: CONTACT_KEYS.list(organizationId, relationId),
      });
      void queryClient.invalidateQueries({ queryKey: RELATION_KEYS.lists(organizationId) });
    }
  };
}

export function useCreateContact(relationId: string) {
  const invalidate = useInvalidateContacts(relationId);
  const t = useTranslations("contacts");

  return useMutation({
    mutationFn: (data: ContactRequest) => createContact(relationId, data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(contactErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateContact(relationId: string) {
  const invalidate = useInvalidateContacts(relationId);
  const t = useTranslations("contacts");

  return useMutation({
    mutationFn: ({ contactId, data }: { contactId: string; data: ContactRequest }) =>
      updateContact(relationId, contactId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(contactErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useDeleteContact(relationId: string) {
  const invalidate = useInvalidateContacts(relationId);
  const t = useTranslations("contacts");

  return useMutation({
    mutationFn: (contactId: string) => deleteContact(relationId, contactId),
    onSuccess: () => {
      toast.success(t("toast.deleted"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(contactErrorText(error, t, "toast.deleteError"));
    },
  });
}
