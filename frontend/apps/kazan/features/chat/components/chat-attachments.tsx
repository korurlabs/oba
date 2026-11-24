"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Download, FileText, Paperclip, X } from "lucide-react";
import { AttachmentPreview } from "@oba/shared/features/attachments/components";
import type { AttachmentResponse } from "@oba/shared/features/attachments/schemas";
import { cn } from "@oba/ui/lib/utils";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { useFileObjectUrl } from "../hooks/use-file-object-url";

/** Whether a file of this content type shows as an image in the chat. */
export function isChatImageType(contentType: string | null | undefined): boolean {
  return !!contentType && contentType.startsWith("image/");
}

/** A byte count as B, KB or MB with one decimal, as the attachment list shows it. */
export function formatChatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** The side a message sits on: the reader's own messages sit at the end. */
export type ChatMessageSide = "start" | "end";

interface ChatAttachmentsProps {
  attachments: readonly AttachmentResponse[];
  side?: ChatMessageSide;
  className?: string;
}

/**
 * The files of a message: images inline as thumbnails that open the
 * attachment preview, other files as cards with name, size and download.
 */
export function ChatAttachments({ attachments, side = "start", className }: ChatAttachmentsProps) {
  const t = useTranslations("chat.attachments");
  const [preview, setPreview] = useState<AttachmentResponse | null>(null);

  if (attachments.length === 0) return null;
  const images = attachments.filter((attachment) => isChatImageType(attachment.contentType));
  const files = attachments.filter((attachment) => !isChatImageType(attachment.contentType));

  return (
    <div className={cn("mt-1 space-y-2", className)}>
      {images.length > 0 && (
        <div className={cn("flex flex-wrap gap-2", side === "end" && "justify-end")}>
          {images.map((image) => (
            <button
              key={image.id}
              type="button"
              onClick={() => setPreview(image)}
              aria-label={t("open", { name: image.fileName })}
              className="border-border focus-visible:ring-ring overflow-hidden rounded-md border focus-visible:ring-2 focus-visible:outline-none"
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- reason: short-lived signed storage URLs, not routed through the Next image optimizer, as in the shared attachment preview */}
              <img
                src={image.url}
                alt={image.fileName}
                loading="lazy"
                className="max-h-48 max-w-64 object-cover"
              />
            </button>
          ))}
        </div>
      )}

      {files.length > 0 && (
        <ul className={cn("flex flex-col gap-1.5", side === "end" && "items-end")}>
          {files.map((file) => (
            <li
              key={file.id}
              className="border-border bg-muted/40 flex max-w-sm items-center gap-3 rounded-md border px-3 py-2"
            >
              <FileText className="text-muted-foreground h-6 w-6 shrink-0" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="text-foreground truncate text-sm font-medium">{file.fileName}</p>
                {file.fileSize ? (
                  <p className="text-muted-foreground text-xs">{formatChatFileSize(file.fileSize)}</p>
                ) : null}
              </div>
              <a
                href={file.url}
                download={file.fileName}
                target="_blank"
                rel="noopener noreferrer"
                aria-label={t("download", { name: file.fileName })}
                className="text-muted-foreground hover:text-foreground focus-visible:ring-ring rounded-sm p-1 focus-visible:ring-2 focus-visible:outline-none"
              >
                <Download className="h-4 w-4" />
              </a>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={!!preview} onOpenChange={(open) => !open && setPreview(null)}>
        <DialogContent className="max-w-3xl">
          <DialogTitle className="truncate pr-6 text-sm">{preview?.fileName}</DialogTitle>
          {preview && <AttachmentPreview attachment={preview} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ── Files not sent yet ──────────────────────────────────────────────

/** Stable React keys for local files, which have no ID, so removing one file does not remount the others. */
const localFileKeys = new WeakMap<File, number>();
let nextLocalFileKey = 0;

function localFileKey(file: File): number {
  let key = localFileKeys.get(file);
  if (key === undefined) {
    key = nextLocalFileKey++;
    localFileKeys.set(file, key);
  }
  return key;
}

interface LocalFileEntry {
  file: File;
  /** The file's position in the list it came from, which removal goes by. */
  index: number;
  key: number;
}

/**
 * Splits local files into those shown as images and the rest, in their
 * order. An image the browser could not decode (e.g. HEIC in most browsers)
 * goes with the rest, so it still shows by name.
 */
function useLocalFileGroups(files: readonly File[]) {
  const [undecodable, setUndecodable] = useState<ReadonlySet<File>>(() => new Set());
  const entries: LocalFileEntry[] = files.map((file, index) => ({ file, index, key: localFileKey(file) }));
  const images = entries.filter(({ file }) => isChatImageType(file.type) && !undecodable.has(file));
  const others = entries.filter(({ file }) => !isChatImageType(file.type) || undecodable.has(file));
  const markUndecodable = (file: File) => setUndecodable((current) => new Set(current).add(file));
  return { images, others, markUndecodable };
}

interface ChatLocalImageProps {
  file: File;
  className: string;
  onError: () => void;
}

/** A local image file drawn from an object URL, which is revoked when the image goes. */
function ChatLocalImage({ file, className, onError }: ChatLocalImageProps) {
  const url = useFileObjectUrl(file);
  if (!url) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- reason: a local object URL, which the Next image optimizer cannot fetch
    <img src={url} alt={file.name} className={className} onError={onError} />
  );
}

interface ChatComposerFilesProps {
  files: readonly File[];
  /** Drops the file at this index of `files` from the message. */
  onRemove: (index: number) => void;
}

/**
 * The files attached in the composer: images as thumbnails of the local
 * file, other files as chips with name and size, each with a remove button.
 */
export function ChatComposerFiles({ files, onRemove }: ChatComposerFilesProps) {
  const t = useTranslations("chat.composer");
  const { images, others, markUndecodable } = useLocalFileGroups(files);

  if (files.length === 0) return null;

  const removeButtonClass =
    "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none";

  return (
    <div role="group" aria-label={t("files")} className="flex flex-col gap-2 px-3 pt-2.5">
      {images.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {images.map(({ file, index, key }) => (
            <li key={key} className="border-border bg-background relative size-20 overflow-hidden rounded-md border">
              <ChatLocalImage
                file={file}
                className="size-full object-cover"
                onError={() => markUndecodable(file)}
              />
              <button
                type="button"
                onClick={() => onRemove(index)}
                aria-label={t("removeFile", { name: file.name })}
                title={t("removeFile", { name: file.name })}
                className={cn(
                  "bg-background/80 text-foreground hover:bg-background absolute top-1 right-1 p-0.5 shadow-sm",
                  removeButtonClass,
                )}
              >
                <X className="size-3.5" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}

      {others.length > 0 && (
        <ul className="flex flex-wrap gap-1.5">
          {others.map(({ file, index, key }) => (
            <li
              key={key}
              className="border-border bg-background text-muted-foreground inline-flex max-w-xs items-center gap-1.5 rounded-md border py-1 pr-1 pl-2 text-xs"
            >
              <Paperclip className="size-3.5 shrink-0" aria-hidden />
              <span className="text-foreground truncate">{file.name}</span>
              <span className="shrink-0">{formatChatFileSize(file.size)}</span>
              <button
                type="button"
                onClick={() => onRemove(index)}
                aria-label={t("removeFile", { name: file.name })}
                className={cn("hover:bg-muted hover:text-foreground p-0.5", removeButtonClass)}
              >
                <X className="size-3.5" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface ChatPendingFilesProps {
  files: readonly File[];
  side?: ChatMessageSide;
}

/**
 * The files of a message this tab has not sent yet: images at the size the
 * chat shows them once sent, so the message keeps its shape when the server
 * confirms it, and other files by name and size.
 */
export function ChatPendingFiles({ files, side = "start" }: ChatPendingFilesProps) {
  const { images, others, markUndecodable } = useLocalFileGroups(files);

  if (files.length === 0) return null;
  return (
    <div className={cn("flex flex-col gap-1.5", side === "end" && "items-end")}>
      {images.length > 0 && (
        <ul className={cn("flex flex-wrap gap-2", side === "end" && "justify-end")}>
          {images.map(({ file, key }) => (
            <li key={key} className="border-border overflow-hidden rounded-md border">
              <ChatLocalImage
                file={file}
                className="max-h-48 max-w-64 object-cover"
                onError={() => markUndecodable(file)}
              />
            </li>
          ))}
        </ul>
      )}

      {others.length > 0 && (
        <ul className={cn("flex flex-wrap gap-1.5", side === "end" && "justify-end")}>
          {others.map(({ file, key }) => (
            <li
              key={key}
              className="border-border bg-muted/40 text-muted-foreground inline-flex max-w-xs items-center gap-1.5 rounded-md border px-2 py-1 text-xs"
            >
              <Paperclip className="h-3.5 w-3.5 shrink-0" aria-hidden />
              <span className="text-foreground truncate">{file.name}</span>
              <span className="shrink-0">{formatChatFileSize(file.size)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
