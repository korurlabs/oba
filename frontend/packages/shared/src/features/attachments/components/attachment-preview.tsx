"use client";

import { Download, FileText } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@oba/ui/components/button";
import type { AttachmentResponse } from "../schemas";

function isImageContentType(contentType: string | null | undefined): boolean {
  if (!contentType) return false;
  return (
    contentType === "image/png" ||
    contentType === "image/jpeg" ||
    contentType === "image/jpg"
  );
}

function isPdfContentType(contentType: string | null | undefined): boolean {
  return contentType === "application/pdf";
}

interface AttachmentPreviewProps {
  attachment: AttachmentResponse;
}

export function AttachmentPreview({ attachment }: AttachmentPreviewProps) {
  const t = useTranslations("attachments");

  if (isImageContentType(attachment.contentType)) {
    return (
      <div className="overflow-hidden rounded-lg border">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={attachment.url}
          alt={attachment.fileName}
          className="h-auto max-h-[500px] w-full object-contain"
        />
      </div>
    );
  }

  if (isPdfContentType(attachment.contentType)) {
    return (
      <div className="overflow-hidden rounded-lg border">
        <iframe
          src={attachment.url}
          title={attachment.fileName}
          className="h-[600px] w-full"
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-8 text-center">
      <FileText className="h-10 w-10 text-muted-foreground" />
      <p className="text-sm text-muted-foreground">
        {t("preview.unsupportedType")}
      </p>
      <Button variant="outline" size="sm" asChild>
        <a
          href={attachment.url}
          download={attachment.fileName}
          target="_blank"
          rel="noopener noreferrer"
        >
          <Download className="h-4 w-4" />
          {t("preview.download")}
        </a>
      </Button>
    </div>
  );
}
