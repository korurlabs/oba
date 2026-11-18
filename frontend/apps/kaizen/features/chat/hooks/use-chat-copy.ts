"use client";

import { useCallback } from "react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";

/** What was copied, which picks the confirmation text. */
export type ChatCopyKind = "text" | "code" | "link";

/**
 * Returns a handler that copies text of the chat (a message, a code block, a
 * message link) and confirms it with a toast.
 */
export function useChatCopy() {
  const t = useTranslations("chat.copy");

  return useCallback(
    async (text: string, kind: ChatCopyKind) => {
      // `navigator.clipboard` is undefined outside a secure context (HTTP)
      // and in older browsers; that is reported like any other failure.
      if (!navigator.clipboard?.writeText) {
        toast.error(t("error"));
        return;
      }
      try {
        await navigator.clipboard.writeText(text);
        toast.success(t(kind));
      } catch (error) {
        console.warn("[chat] clipboard write failed", error);
        toast.error(t("error"));
      }
    },
    [t],
  );
}
