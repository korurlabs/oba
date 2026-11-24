"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Copy, Pencil, Trash2 } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import { getDisplayName, type UserAvatarUser } from "@/components/ui/user-avatar";
import type { ChatMessageSide } from "@/features/chat/components/chat-attachments";
import {
  ChatMessageBubble,
  ChatMessageLayout,
  MessageMeta,
} from "@/features/chat/components/chat-message-shell";
import { DAILY_UPDATES_PATH } from "@/features/chat/lib/chat-route";
import { useCopyUpdate } from "../hooks/use-copy-update";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { UpdateResponse } from "../schemas";
import { DeleteUpdateDialog } from "./delete-update-dialog";
import { UpdateCardBody } from "./update-card-body";

/** Route of the edit screen of one update. */
export function updateEditPath(updateId: string): string {
  return `${DAILY_UPDATES_PATH}/${encodeURIComponent(updateId)}/edit`;
}

interface UpdateMessageContentProps {
  update: UpdateResponse;
  /** The reader. Edit and delete are offered only when they wrote the update. */
  currentUserId: string | undefined;
}

/**
 * The content of an update's bubble: the four parts of the update, as
 * {@link UpdateCardBody} renders them, and the card's own actions last.
 * Everyone who can see the update sees every line; only the ticket links and
 * sizing depend on the reader's board access. A reader who is not the author
 * gets the update read-only: Copy only.
 */
export function UpdateMessageContent({ update, currentUserId }: UpdateMessageContentProps) {
  const t = useTranslations("updates");
  const copyUpdate = useCopyUpdate();
  const [deleteOpen, setDeleteOpen] = useState(false);

  const isAuthor = !!currentUserId && currentUserId === update.author.userId;
  const actionClass = "h-7 gap-1.5 px-2 text-xs";

  return (
    <div role="group" aria-label={getDisplayName(updateAuthor(update))} className="space-y-3">
      <UpdateCardBody data={update} />

      <div className="border-border/60 flex flex-wrap items-center gap-1 border-t pt-2">
        <Button
          variant="ghost"
          size="sm"
          className={actionClass}
          onClick={() => void copyUpdate(update.summaryText)}
        >
          <Copy className="h-3.5 w-3.5" />
          {t("actions.copy")}
        </Button>
        {isAuthor && (
          <>
            <Button variant="ghost" size="sm" className={actionClass} asChild>
              <Link href={updateEditPath(update.id)}>
                <Pencil className="h-3.5 w-3.5" />
                {t("actions.edit")}
              </Link>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className={cn(actionClass, "text-destructive hover:text-destructive")}
              onClick={() => setDeleteOpen(true)}
            >
              <Trash2 className="h-3.5 w-3.5" />
              {t("actions.delete")}
            </Button>
          </>
        )}
      </div>

      {isAuthor && (
        <DeleteUpdateDialog updateId={update.id} open={deleteOpen} onOpenChange={setDeleteOpen} />
      )}
    </div>
  );
}

interface UpdateMessageCardProps {
  update: UpdateResponse;
  /** The reader, whose own update sits on the right as their messages do. */
  currentUserId: string | undefined;
  className?: string;
}

/**
 * An update as one chat message, for a screen outside the chat: the same
 * message design, without a row or a toolbar.
 */
export function UpdateMessageCard({ update, currentUserId, className }: UpdateMessageCardProps) {
  const dates = useDateFormat();
  const side: ChatMessageSide =
    !!currentUserId && currentUserId === update.author.userId ? "end" : "start";
  const author = updateAuthor(update);

  return (
    <div className={className}>
      <ChatMessageLayout
        side={side}
        grouped={false}
        author={author}
        name={getDisplayName(author)}
        createdAt={update.createdAt}
        dates={dates}
        meta={<MessageMeta editedAt={update.editedAt} pinnedAt={null} dates={dates} />}
      >
        <ChatMessageBubble side={side} fill>
          <UpdateMessageContent update={update} currentUserId={currentUserId} />
        </ChatMessageBubble>
      </ChatMessageLayout>
    </div>
  );
}

function updateAuthor(update: UpdateResponse): UserAvatarUser {
  return {
    firstName: update.author.firstName ?? undefined,
    lastName: update.author.lastName ?? undefined,
    email: update.author.email,
  };
}
