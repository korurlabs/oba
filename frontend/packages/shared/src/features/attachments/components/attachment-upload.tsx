"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Upload, FileUp, X, FileText, Loader2 } from "lucide-react";
import { Button } from "@oba/ui/components/button";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { cn } from "@oba/ui/lib/utils";
import type { StorageEntityType } from "../schemas";
import { useUploadAttachments } from "../hooks/use-attachments";

const ACCEPTED_TYPES = [
  "image/png",
  "image/jpeg",
  "image/jpg",
  "application/pdf",
];

const ACCEPTED_EXTENSIONS = ".png,.jpg,.jpeg,.pdf";

/**
 * Mirrors the server's `spring.servlet.multipart` max-file-size AND
 * max-request-size (both 12MB), so it is the per-file cap and the cap on the
 * combined batch. Same constant the kaizen CLI enforces
 * (`MaxAttachmentBytes`).
 *
 * Checking client-side turns an opaque 413 into an actionable message. That
 * matters most on the CREATE path, where the ticket is created BEFORE its
 * attachments are uploaded: a rejection discovered server-side would leave a
 * saved ticket whose files silently failed to attach.
 */
const MAX_ATTACHMENT_BYTES = 12 * 1024 * 1024;
const MAX_ATTACHMENT_MB = 12;

interface AttachmentUploadProps {
  entityType: StorageEntityType;
  /**
   * The owning entity. Omit for DEFERRED mode, used while the entity does
   * not exist yet (e.g. a ticket being created): the upload endpoint needs
   * a real UUID in the metadata, so files are staged and handed to
   * `onStagedFilesChange` instead, and the caller uploads them once it has
   * an id.
   */
  entityId?: string;
  onUploadSuccess?: () => void;
  /**
   * Deferred mode only. Receives the staged files on every change. The
   * caller owns uploading them and must surface any failure — nothing here
   * retries or reports on its behalf.
   */
  onStagedFilesChange?: (files: File[]) => void;
}

function FilePreview({ file, onRemove }: { file: File; onRemove: () => void }) {
  const isPdf = file.type === "application/pdf";
  const [previewUrl] = useState(() =>
    isPdf ? null : URL.createObjectURL(file),
  );

  return (
    <div className="group relative flex items-center gap-3 rounded-lg border bg-card p-2">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted">
        {isPdf ? (
          <FileText className="h-5 w-5 text-muted-foreground" />
        ) : previewUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={previewUrl}
            alt={file.name}
            className="h-full w-full object-cover"
          />
        ) : (
          <FileText className="h-5 w-5 text-muted-foreground" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{file.name}</p>
        <p className="text-xs text-muted-foreground">
          {formatFileSize(file.size)}
        </p>
      </div>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="h-7 w-7 shrink-0"
        onClick={onRemove}
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  );
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function AttachmentUpload({
  entityType,
  entityId,
  onUploadSuccess,
  onStagedFilesChange,
}: AttachmentUploadProps) {
  const t = useTranslations("attachments");
  const [isDragOver, setIsDragOver] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const uploadMutation = useUploadAttachments();

  // No entity to upload against yet -> stage only, and let the caller
  // upload once it has an id.
  const isDeferred = !entityId;

  // Held in a ref so a caller passing an inline arrow doesn't re-fire this
  // on every render.
  const onStagedFilesChangeRef = useRef(onStagedFilesChange);
  onStagedFilesChangeRef.current = onStagedFilesChange;
  useEffect(() => {
    if (!isDeferred) return;
    onStagedFilesChangeRef.current?.(selectedFiles);
  }, [isDeferred, selectedFiles]);

  // Rejections are REPORTED, never silently dropped: previously an
  // unsupported file just vanished from the picker with no explanation.
  const handleFiles = useCallback(
    (fileList: FileList | null) => {
      if (!fileList) return;

      const rejections: string[] = [];
      const accepted: File[] = [];
      let batchBytes = selectedFiles.reduce((sum, f) => sum + f.size, 0);

      for (const file of Array.from(fileList)) {
        if (!ACCEPTED_TYPES.includes(file.type)) {
          rejections.push(t("messages.fileTypeNotAllowed", { name: file.name }));
          continue;
        }
        if (file.size > MAX_ATTACHMENT_BYTES) {
          rejections.push(
            t("messages.fileTooLarge", { name: file.name, max: MAX_ATTACHMENT_MB }),
          );
          continue;
        }
        if (batchBytes + file.size > MAX_ATTACHMENT_BYTES) {
          rejections.push(
            t("messages.batchTooLarge", { name: file.name, max: MAX_ATTACHMENT_MB }),
          );
          continue;
        }
        batchBytes += file.size;
        accepted.push(file);
      }

      if (accepted.length > 0) {
        setSelectedFiles((prev) => [...prev, ...accepted]);
      }
      if (rejections.length > 0) {
        toast.error(rejections.join("\n"));
      }
    },
    [selectedFiles, t],
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);
      handleFiles(e.dataTransfer.files);
    },
    [handleFiles],
  );

  const handleClick = useCallback(() => {
    inputRef.current?.click();
  }, []);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleFiles(e.target.files);
      e.target.value = "";
    },
    [handleFiles],
  );

  const removeFile = useCallback((index: number) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const handleUpload = useCallback(() => {
    if (selectedFiles.length === 0 || !entityId) return;
    uploadMutation.mutate(
      { entityType, entityId, files: selectedFiles },
      {
        onSuccess: () => {
          setSelectedFiles([]);
          onUploadSuccess?.();
        },
      },
    );
  }, [selectedFiles, entityType, entityId, uploadMutation, onUploadSuccess]);

  const isUploading = uploadMutation.isPending;

  return (
    <div className="space-y-3">
      <div
        role="button"
        tabIndex={0}
        onClick={handleClick}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            handleClick();
          }
        }}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={cn(
          "relative flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed p-6 transition-all duration-200 cursor-pointer",
          "min-h-[120px]",
          isDragOver
            ? "border-primary bg-primary/5 scale-[1.01]"
            : "border-muted-foreground/25 hover:border-primary/50 hover:bg-muted/50",
          isUploading && "pointer-events-none opacity-60",
        )}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={ACCEPTED_EXTENSIONS}
          onChange={handleInputChange}
          className="hidden"
          aria-label={t("upload.selectFiles")}
        />

        <div
          className={cn(
            "flex h-10 w-10 items-center justify-center rounded-full transition-colors duration-200",
            isDragOver
              ? "bg-primary/10 text-primary"
              : "bg-muted text-muted-foreground",
          )}
        >
          {isDragOver ? (
            <FileUp className="h-5 w-5 animate-bounce" />
          ) : (
            <Upload className="h-5 w-5" />
          )}
        </div>

        <div className="text-center">
          <p className="text-sm font-medium text-foreground">
            {isDragOver ? t("upload.dropHere") : t("upload.dragAndDrop")}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            {t("upload.supportedFormats")}
          </p>
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={isUploading}
          onClick={(e) => {
            e.stopPropagation();
            handleClick();
          }}
        >
          {t("upload.selectFiles")}
        </Button>
      </div>

      {selectedFiles.length > 0 && (
        <div className="space-y-2">
          {selectedFiles.map((file, index) => (
            <FilePreview
              key={`${file.name}-${file.size}-${index}`}
              file={file}
              onRemove={() => removeFile(index)}
            />
          ))}
          {!isDeferred && (
          <Button
            type="button"
            size="sm"
            disabled={isUploading}
            onClick={handleUpload}
            className="w-full"
          >
            {isUploading ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                {t("upload.uploading")}
              </>
            ) : (
              t("upload.uploadFiles", { count: selectedFiles.length })
            )}
          </Button>
          )}
        </div>
      )}
    </div>
  );
}
