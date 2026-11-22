"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import type { DocumentTemplateFiles } from "@/features/document-templates/schemas";
import { useOrganizationId } from "@/hooks/use-organization-id";
import {
  createDocument,
  deleteDocument,
  getDocument,
  listDocuments,
  replaceDocumentFiles,
  updateDocument,
} from "../api";
import { documentErrorText } from "../lib/document-error";
import type {
  DocumentCreateRequest,
  DocumentQueryRequest,
  DocumentUpdateRequest,
} from "../schemas";
import { DOCUMENT_KEYS } from "./query-keys";

export { DOCUMENT_KEYS };

/** One page of the organization's documents; waits for the organization. */
export function useDocuments(params: DocumentQueryRequest) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: DOCUMENT_KEYS.list(organizationId ?? "", params),
    queryFn: () => listDocuments(params),
    enabled: !!organizationId,
    // A new filter or page keeps the current rows until its answer arrives,
    // but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/**
 * One document with its snapshots; waits for the organization and an id.
 * The default freshness of a minute stays well inside the lifetime of its
 * signed file URLs, so a document shown again after that is fetched again.
 */
export function useDocument(documentId: string) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: DOCUMENT_KEYS.detail(organizationId ?? "", documentId),
    queryFn: () => getDocument(documentId),
    enabled: !!organizationId && documentId !== "",
  });
}

/** A document change refreshes every document query of the organization: lists and details. */
function useInvalidateDocuments() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: DOCUMENT_KEYS.all(organizationId) });
    }
  };
}

export function useCreateDocument() {
  const invalidate = useInvalidateDocuments();
  const t = useTranslations("documents");

  return useMutation({
    mutationFn: (data: DocumentCreateRequest) => createDocument(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(documentErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateDocument() {
  const invalidate = useInvalidateDocuments();
  const t = useTranslations("documents");

  return useMutation({
    mutationFn: ({ documentId, data }: { documentId: string; data: DocumentUpdateRequest }) =>
      updateDocument(documentId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(documentErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useReplaceDocumentFiles() {
  const invalidate = useInvalidateDocuments();
  const t = useTranslations("documents");

  return useMutation({
    mutationFn: ({ documentId, files }: { documentId: string; files: DocumentTemplateFiles }) =>
      replaceDocumentFiles(documentId, files),
    onSuccess: () => {
      toast.success(t("toast.filesUploaded"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(documentErrorText(error, t, "toast.uploadError"));
    },
  });
}

export function useDeleteDocument() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("documents");

  return useMutation({
    mutationFn: (documentId: string) => deleteDocument(documentId),
    onSuccess: (_result, documentId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({ queryKey: DOCUMENT_KEYS.detail(organizationId, documentId) });
        void queryClient.invalidateQueries({ queryKey: DOCUMENT_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(documentErrorText(error, t, "toast.deleteError"));
    },
  });
}
