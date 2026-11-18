"use client";

import { useTranslations } from "next-intl";
import { AlertCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { UpdatePreviewResponse } from "../schemas";
import { UpdateCardBody } from "./update-card-body";

interface UpdatePreviewProps {
  /** The card as it would be posted; absent until the first preview has answered. */
  preview: UpdatePreviewResponse | undefined;
  /** Why no request is sent: no ticket selected, or a field to correct. */
  blockedReason: "noTickets" | "invalid" | null;
  isFetching: boolean;
  error: Error | null;
  onRetry: () => void;
}

/**
 * The card as it will be posted, the same body the channel renders, built by
 * the server from the current form. It saves nothing and follows the form with a short
 * delay; while the form cannot be sent it says why instead.
 */
export function UpdatePreview({
  preview,
  blockedReason,
  isFetching,
  error,
  onRetry,
}: UpdatePreviewProps) {
  const t = useTranslations("updates");

  return (
    <section
      aria-labelledby="update-preview-title"
      aria-busy={isFetching}
      className="border-border bg-card rounded-lg border"
    >
      <div className="border-border flex items-center gap-2 border-b px-4 py-3">
        <h2 id="update-preview-title" className="text-foreground text-sm font-semibold">
          {t("form.previewTitle")}
        </h2>
        {isFetching && !blockedReason && (
          <Loader2
            className="text-muted-foreground ml-auto h-4 w-4 animate-spin"
            aria-label={t("form.previewLoading")}
          />
        )}
      </div>

      <div className="px-4 py-3">
        {blockedReason ? (
          <p className="text-muted-foreground text-sm">
            {blockedReason === "noTickets" ? t("form.previewEmpty") : t("form.previewInvalid")}
          </p>
        ) : error ? (
          <div role="alert" className="flex flex-col items-start gap-2">
            <p className="text-destructive flex items-center gap-1.5 text-sm font-medium">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {t("form.previewError")}
            </p>
            {error.message && <p className="text-muted-foreground text-xs">{error.message}</p>}
            <Button type="button" variant="outline" size="sm" onClick={onRetry}>
              {t("retry")}
            </Button>
          </div>
        ) : preview === undefined ? (
          <div className="flex items-center gap-2 py-2">
            <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
            <span className="text-muted-foreground text-sm">{t("form.previewLoading")}</span>
          </div>
        ) : (
          <div className="text-sm">
            <UpdateCardBody data={preview} />
          </div>
        )}
      </div>
    </section>
  );
}
