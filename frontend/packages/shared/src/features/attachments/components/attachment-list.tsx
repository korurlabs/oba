"use client";

import { useState, type ReactNode } from "react";
import {
  AlertCircle,
  ExternalLink,
  FileText,
  Loader2,
  Trash2,
} from "lucide-react";
import { Button } from "@oba/ui/components/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@oba/ui/components/alert-dialog";
import { Skeleton } from "@oba/ui/components/skeleton";
import { useTranslations } from "next-intl";
import type { AttachmentResponse, StorageEntityType } from "../schemas";
import {
  useAttachments,
  useDeleteAttachment,
} from "../hooks/use-attachments";

function isImageContentType(contentType: string | null | undefined): boolean {
  if (!contentType) return false;
  return contentType.startsWith("image/");
}

function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

interface AttachmentListProps {
  entityType: StorageEntityType;
  entityId: string | undefined;
  /**
   * Attachment ids to omit from the rendered list. Attachments are a
   * single flat collection per (entityType, entityId) server-side, so a
   * caller that already renders some of them on another surface passes
   * their ids here. The kazan ticket sheet uses this for images
   * embedded in the description: the editor renders them inline, so
   * listing them again would both duplicate them and expose a delete
   * button that would leave a dangling img in the description.
   */
  excludeIds?: readonly string[];
  /**
   * Hides the delete action. Set by callers whose own RBAC role is
   * read-only, so the list never renders a control the backend would
   * reject.
   */
  readOnly?: boolean;
  /**
   * Keeps only the attachments it returns true for. A caller that shows one
   * entity's attachments in sections, such as a mail's document snapshots
   * and its uploaded files, renders one list per section.
   */
  include?: (attachment: AttachmentResponse) => boolean;
  /** Extra actions of one row, shown before the open and delete buttons. */
  renderActions?: (attachment: AttachmentResponse) => ReactNode;
  /** The text of an empty list; defaults to the generic "no attachments" text. */
  emptyText?: string;
  /**
   * Runs after a delete succeeded, for a caller that also shows the file in
   * a list under another query key.
   */
  onDeleteSuccess?: () => void;
}

function AttachmentListSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 rounded-lg border p-2">
          <Skeleton className="h-10 w-10 rounded-md" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-3 w-16" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function AttachmentList({
  entityType,
  entityId,
  excludeIds,
  readOnly = false,
  include,
  renderActions,
  emptyText,
  onDeleteSuccess,
}: AttachmentListProps) {
  const t = useTranslations("attachments");
  const { data, isLoading, isError, isFetching, refetch } = useAttachments(
    entityType,
    entityId,
  );
  const deleteMutation = useDeleteAttachment();
  const [deletingId, setDeletingId] = useState<string | null>(null);

  if (isLoading) {
    return <AttachmentListSkeleton />;
  }

  // A failed list request must NOT fall through to the empty state --
  // "No attachments yet" would tell the user the entity has no files when
  // in fact we simply couldn't read them. See rules/10-no-silent-failures.
  if (isError) {
    return (
      // role="alert" so the failure is announced: this block replaces the
      // skeleton asynchronously, which is a silent swap for a screen
      // reader otherwise. Matches the repo's other error surfaces.
      <div
        role="alert"
        className="border-destructive/40 flex flex-col items-center justify-center rounded-lg border border-dashed p-6 text-center"
      >
        <AlertCircle className="text-destructive h-8 w-8" />
        <p className="text-destructive mt-2 text-sm">{t("list.loadError")}</p>
        {/* The global query defaults set retry: false and
            refetchOnWindowFocus: false, so nothing retries on its own --
            without this button the only way out is to close and reopen
            the containing view. The raw error is deliberately NOT shown:
            it is untranslated axios boilerplate ("Request failed with
            status code 500") that would read as English noise in an
            otherwise localized UI. */}
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-3"
          disabled={isFetching}
          onClick={() => {
            void refetch();
          }}
        >
          {isFetching ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              {t("list.retrying")}
            </>
          ) : (
            t("list.retry")
          )}
        </Button>
      </div>
    );
  }

  const excluded =
    excludeIds && excludeIds.length > 0 ? new Set(excludeIds) : null;
  const attachments = data?.data?.filter(
    (attachment) =>
      !excluded?.has(attachment.id) && (include ? include(attachment) : true),
  );

  if (!attachments || attachments.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-6 text-center">
        <FileText className="h-8 w-8 text-muted-foreground" />
        <p className="mt-2 text-sm text-muted-foreground">
          {emptyText ?? t("list.empty")}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {attachments.map((attachment) => {
        const isImage = isImageContentType(attachment.contentType);
        const isDeleting =
          deleteMutation.isPending && deletingId === attachment.id;

        return (
          <div
            key={attachment.id}
            className="flex items-center gap-3 rounded-lg border bg-card p-2 transition-colors hover:bg-accent/50"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted">
              {isImage ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={attachment.url}
                  alt={attachment.fileName}
                  className="h-full w-full object-cover"
                />
              ) : (
                <FileText className="h-5 w-5 text-muted-foreground" />
              )}
            </div>

            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">
                {attachment.fileName}
              </p>
              <p className="text-xs text-muted-foreground">
                {formatFileSize(attachment.fileSize)}
              </p>
            </div>

            <div className="flex shrink-0 items-center gap-1">
              {renderActions?.(attachment)}
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                asChild
              >
                <a
                  href={attachment.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={t("list.open")}
                >
                  <ExternalLink className="h-4 w-4" />
                </a>
              </Button>

              {!readOnly && (
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 text-destructive hover:text-destructive"
                      disabled={isDeleting}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>
                        {t("list.deleteConfirmTitle")}
                      </AlertDialogTitle>
                      <AlertDialogDescription>
                        {t("list.deleteConfirmDescription")}
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>{t("list.cancel")}</AlertDialogCancel>
                      <AlertDialogAction
                        onClick={() => {
                          if (!entityId) return;
                          setDeletingId(attachment.id);
                          deleteMutation.mutate(
                            {
                              attachmentId: attachment.id,
                              entityType,
                              entityId,
                            },
                            {
                              onSuccess: () => onDeleteSuccess?.(),
                              onSettled: () => setDeletingId(null),
                            },
                          );
                        }}
                      >
                        {t("list.confirmDelete")}
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
