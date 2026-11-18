"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { ATTACHMENT_KEYS } from "@oba/shared/features/attachments/hooks";
import { useOrganizationId } from "@/hooks/use-organization-id";
import {
  copyDocumentTemplateToAttachment,
  createDocumentTemplate,
  deleteDocumentTemplate,
  getDocumentTemplate,
  listDocumentTemplates,
  replaceDocumentTemplateFiles,
  updateDocumentTemplate,
} from "../api";
import {
  documentTemplateAttachmentErrorText,
  documentTemplateErrorText,
} from "../lib/document-template-error";
import type {
  DocumentTemplateAttachmentRequest,
  DocumentTemplateFiles,
  DocumentTemplateQueryRequest,
  DocumentTemplateRequest,
} from "../schemas";
import { DOCUMENT_TEMPLATE_KEYS } from "./query-keys";

export { DOCUMENT_TEMPLATE_KEYS };

/** One page of the organization's templates; waits for the organization. */
export function useDocumentTemplates(
  params: DocumentTemplateQueryRequest,
  { enabled = true }: { enabled?: boolean } = {}
) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: DOCUMENT_TEMPLATE_KEYS.list(organizationId ?? "", params),
    queryFn: () => listDocumentTemplates(params),
    enabled: enabled && !!organizationId,
    // A new filter or page keeps the current rows until its answer arrives,
    // but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/**
 * One template; waits for the organization and an id. It is fetched again
 * whenever it is shown, because its file URLs are signed for a short time
 * only and a cached detail could hand out an expired link.
 */
export function useDocumentTemplate(
  templateId: string,
  { enabled = true }: { enabled?: boolean } = {}
) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: DOCUMENT_TEMPLATE_KEYS.detail(organizationId ?? "", templateId),
    queryFn: () => getDocumentTemplate(templateId),
    enabled: enabled && !!organizationId && templateId !== "",
    staleTime: 0,
    refetchOnMount: "always",
  });
}

/** A template change refreshes every template query of the organization: lists and details. */
function useInvalidateDocumentTemplates() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: DOCUMENT_TEMPLATE_KEYS.all(organizationId) });
    }
  };
}

export function useCreateDocumentTemplate() {
  const invalidate = useInvalidateDocumentTemplates();
  const t = useTranslations("documentTemplates");

  return useMutation({
    mutationFn: (data: DocumentTemplateRequest) => createDocumentTemplate(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(documentTemplateErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateDocumentTemplate() {
  const invalidate = useInvalidateDocumentTemplates();
  const t = useTranslations("documentTemplates");

  return useMutation({
    mutationFn: ({ templateId, data }: { templateId: string; data: DocumentTemplateRequest }) =>
      updateDocumentTemplate(templateId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(documentTemplateErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useReplaceDocumentTemplateFiles() {
  const invalidate = useInvalidateDocumentTemplates();
  const t = useTranslations("documentTemplates");

  return useMutation({
    mutationFn: ({ templateId, files }: { templateId: string; files: DocumentTemplateFiles }) =>
      replaceDocumentTemplateFiles(templateId, files),
    onSuccess: () => {
      toast.success(t("toast.filesUploaded"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(documentTemplateErrorText(error, t, "toast.uploadError"));
    },
  });
}

export function useDeleteDocumentTemplate() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("documentTemplates");

  return useMutation({
    mutationFn: (templateId: string) => deleteDocumentTemplate(templateId),
    onSuccess: (_result, templateId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({
          queryKey: DOCUMENT_TEMPLATE_KEYS.detail(organizationId, templateId),
        });
        void queryClient.invalidateQueries({
          queryKey: DOCUMENT_TEMPLATE_KEYS.all(organizationId),
        });
      }
    },
    onError: (error: unknown) => {
      toast.error(documentTemplateErrorText(error, t, "toast.deleteError"));
    },
  });
}

/**
 * Copies a template's PDF onto a mail or a relation as a new attachment and
 * refreshes that parent's attachment list.
 */
export function useCopyDocumentTemplateToAttachment() {
  const queryClient = useQueryClient();
  const t = useTranslations("documentTemplates");

  return useMutation({
    mutationFn: ({
      templateId,
      data,
    }: {
      templateId: string;
      data: DocumentTemplateAttachmentRequest;
    }) => copyDocumentTemplateToAttachment(templateId, data),
    onSuccess: (_result, { data }) => {
      toast.success(t("attach.toast.added"));
      void queryClient.invalidateQueries({
        queryKey: ATTACHMENT_KEYS.list(data.entityType, data.entityId),
      });
    },
    onError: (error: unknown) => {
      toast.error(documentTemplateAttachmentErrorText(error, t, "attach.toast.addError"));
    },
  });
}
