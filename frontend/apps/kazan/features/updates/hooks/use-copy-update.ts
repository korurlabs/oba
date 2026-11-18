"use client";

import { useCallback } from "react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";

/**
 * Returns a handler that copies an update's `summaryText`, the paste-ready
 * text for Teams, and confirms it with a toast. Every reader copies the same
 * text, whatever boards they can open.
 */
export function useCopyUpdate() {
  const t = useTranslations("updates");

  return useCallback(
    async (summaryText: string) => {
      // `navigator.clipboard` is undefined outside a secure context (HTTP)
      // and in older browsers; that is reported like any other failure.
      if (!navigator.clipboard?.writeText) {
        toast.error(t("messages.copyError"));
        return;
      }
      try {
        await navigator.clipboard.writeText(summaryText);
        toast.success(t("messages.copied"));
      } catch {
        toast.error(t("messages.copyError"));
      }
    },
    [t],
  );
}
