"use client";

import type { MouseEvent } from "react";
import { useTranslations } from "next-intl";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { buttonVariants } from "@/components/ui/button";
import { useDeleteChatMessage } from "../hooks/use-chat-mutations";
import type { ChatMessageResponse } from "../schemas";

interface ChatDeleteMessageDialogProps {
  /** The message to delete, the reader's own or, for an OWNER or ADMIN, anyone's; null keeps the dialog closed. */
  message: ChatMessageResponse | null;
  onClose: () => void;
}

/**
 * Confirms deleting a message for everyone, by its author or by an
 * organization OWNER or ADMIN. Replies quoting it then say the original was
 * deleted, and a thread under it survives.
 */
export function ChatDeleteMessageDialog({ message, onClose }: ChatDeleteMessageDialogProps) {
  const t = useTranslations("chat.deleteMessage");
  const deleteMessage = useDeleteChatMessage();

  const onOpenChange = (open: boolean) => {
    if (!open && !deleteMessage.isPending) onClose();
  };

  const confirm = (event: MouseEvent) => {
    // Kept open until the server answers, so a failure (toasted by the mutation) stays in view.
    event.preventDefault();
    if (!message) return;
    deleteMessage.mutate({ messageId: message.id, threadId: message.threadId }, { onSuccess: onClose });
  };

  return (
    <AlertDialog open={!!message} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("title")}</AlertDialogTitle>
          <AlertDialogDescription>{t("description")}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteMessage.isPending}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            className={buttonVariants({ variant: "destructive" })}
            disabled={deleteMessage.isPending}
            onClick={confirm}
          >
            {t("confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
