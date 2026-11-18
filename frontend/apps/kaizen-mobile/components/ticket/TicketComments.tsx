import { onlineManager } from "@tanstack/react-query";
import { Ellipsis, EllipsisVertical, MessageSquare, Pencil, Trash2 } from "lucide-react-native";
import * as React from "react";
import { Alert, Platform, Pressable, View } from "react-native";

import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import {
  canEditComment,
  commentAuthorName,
  commentSegments,
  isCommentEdited,
  orderComments,
  type CommentSegment,
} from "@/components/ticket/ticketModel";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { Avatar } from "@/components/ui/avatar";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useComments, useDeleteComment, type TicketRef } from "@/hooks/queries/ticketDetail";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { Comment } from "@/lib/schemas/tickets";
import { cn } from "@/lib/utils";

/** Room the header keeps free for the actions button, as wide as its touch box. */
const MENU_ROOM = Platform.select({ ios: "w-11", default: "w-12" });

type TicketCommentsProps = {
  ticketRef: TicketRef;
  meId: string | undefined;
  /** The reader may write on the ticket's board (`canWriteBoard`). */
  canWrite: boolean;
  names: ReadonlyMap<string, string>;
  /** The comment being edited in the composer, which the list marks. */
  editingId: string | null;
  onEdit: (comment: Comment) => void;
};

/**
 * The ticket's comments, oldest first so the newest sits above the composer.
 * The author of a comment can edit or delete it from its menu or a long
 * press while they may write on the board; the API refuses everyone else, so
 * others get no menu.
 */
export function TicketComments({
  ticketRef,
  meId,
  canWrite,
  names,
  editingId,
  onEdit,
}: TicketCommentsProps) {
  const { t } = useTranslation();
  const comments = useComments(ticketRef);
  const remove = useDeleteComment(ticketRef);
  const showFailure = useWriteFailure();
  const [menuFor, setMenuFor] = React.useState<Comment | null>(null);

  const deleteComment = React.useCallback(
    (comment: Comment) => {
      if (!onlineManager.isOnline()) {
        toast.error(t("offlineWriteBlocked"));
        return;
      }
      remove.mutate(comment.id, {
        onSuccess: () => toast.success(t("ticketCommentDeleted")),
        onError: (error: Error) => showFailure(error, "delete", () => deleteComment(comment)),
      });
    },
    [remove, showFailure, t]
  );

  const confirmDelete = React.useCallback(
    (comment: Comment) =>
      Alert.alert(t("ticketCommentDeleteTitle"), t("ticketCommentDeleteMessage"), [
        { text: t("kitCancel"), style: "cancel" },
        {
          text: t("ticketCommentDeleteConfirm"),
          style: "destructive",
          onPress: () => deleteComment(comment),
        },
      ]),
    [deleteComment, t]
  );

  const list: Comment[] = React.useMemo(() => orderComments(comments.data ?? []), [comments.data]);

  const menuActions: ActionSheetAction[] = menuFor
    ? [
        { label: t("ticketCommentEdit"), icon: Pencil, onPress: () => onEdit(menuFor) },
        {
          label: t("ticketCommentDelete"),
          icon: Trash2,
          destructive: true,
          onPress: () => confirmDelete(menuFor),
        },
      ]
    : [];

  return (
    <View className="gap-3 px-4">
      <Text variant="headline">
        {comments.data
          ? t("ticketCommentsCount", { count: comments.data.length })
          : t("ticketComments")}
      </Text>
      {comments.isError ? (
        <InlineError message={t("ticketCommentsError")} onRetry={() => void comments.refetch()} />
      ) : comments.isPending ? (
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      ) : list.length === 0 ? (
        <EmptyState icon={MessageSquare} title={t("ticketCommentsEmpty")} className="py-4" />
      ) : (
        list.map((comment) => (
          <CommentItem
            key={comment.id}
            comment={comment}
            names={names}
            editing={comment.id === editingId}
            onOpenMenu={
              canEditComment(comment, meId, canWrite) ? () => setMenuFor(comment) : undefined
            }
          />
        ))
      )}
      <ActionSheet
        visible={menuFor !== null}
        onClose={() => setMenuFor(null)}
        title={t("ticketCommentActions")}
        actions={menuActions}
        cancelLabel={t("kitCancel")}
      />
    </View>
  );
}

type CommentItemProps = {
  comment: Comment;
  names: ReadonlyMap<string, string>;
  editing: boolean;
  /** Opens Edit and Delete; only the author gets it. */
  onOpenMenu?: () => void;
};

/**
 * One comment: avatar, author, time, "edited", and the text with its mentions.
 * Screen readers read the comment as one element and its actions button as a
 * separate one beside it, so the button sits outside the long-press row.
 */
function CommentItem({ comment, names, editing, onOpenMenu }: CommentItemProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const author: string = commentAuthorName(comment, t("ticketUnknownPerson"));
  const time: string | null = dates.timestamp(comment.createdAt);
  const edited: boolean = isCommentEdited(comment);
  const segments: CommentSegment[] = commentSegments(comment.content, names);

  return (
    <View className={cn(editing && "-mx-2 rounded-md bg-primary-light px-2 py-1")}>
      <View className="relative">
        <Pressable onLongPress={onOpenMenu} className="flex-row gap-3">
          <Avatar name={author} size="md" />
          <View className="flex-1 gap-0.5">
            <View className="flex-row items-center gap-2">
              <Text variant="subhead" className="shrink font-sans-semibold" numberOfLines={1}>
                {author}
              </Text>
              {time ? (
                <Text variant="caption" className="text-muted-foreground">
                  {time}
                </Text>
              ) : null}
              {edited ? (
                <Text variant="caption" className="text-muted-foreground">
                  {t("ticketCommentEdited")}
                </Text>
              ) : null}
              {onOpenMenu ? <View className={cn("ml-auto", MENU_ROOM)} /> : null}
            </View>
            <Text variant="body" selectable>
              {segments.map((segment, index) =>
                segment.kind === "text" ? (
                  segment.text
                ) : (
                  <Text key={index} variant="body" className="font-sans-semibold text-link">
                    {segment.name ? `@${segment.name}` : t("ticketMentionUnknown")}
                  </Text>
                )
              )}
            </Text>
          </View>
        </Pressable>
        {onOpenMenu ? (
          <IconButton
            icon={Platform.OS === "ios" ? Ellipsis : EllipsisVertical}
            label={t("ticketCommentActions")}
            onPress={onOpenMenu}
            className="absolute -top-3 right-0"
          />
        ) : null}
      </View>
    </View>
  );
}
