import { onlineManager } from "@tanstack/react-query";
import { Check, SendHorizontal, X } from "lucide-react-native";
import * as React from "react";
import {
  Pressable,
  View,
  type NativeSyntheticEvent,
  type TextInputSelectionChangeEventData,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  COMMENT_NOT_FOUND_CODE,
  decodeMentions,
  encodeMentions,
  insertMention,
  isCommentEditGone,
  mentionCandidates,
  typedMentionQuery,
  type CommentDraft,
  type MentionPerson,
  type MentionQuery,
} from "@/components/ticket/ticketModel";
import type { MentionPeople } from "@/components/ticket/useMentionPeople";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { Avatar } from "@/components/ui/avatar";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "@/components/ui/toast";
import { useCreateComment, useUpdateComment, type TicketRef } from "@/hooks/queries/ticketDetail";
import { useKeyboardVisible } from "@/hooks/useKeyboardVisible";
import { useTranslation } from "@/i18n";
import { toApiError, type ApiError } from "@/lib/api";
import { COMMENT_CONTENT_MAX, type Comment } from "@/lib/schemas/tickets";

type Selection = { start: number; end: number };

const EMPTY_DRAFT: CommentDraft = { text: "", picked: [] };

type CommentComposerProps = {
  ticketRef: TicketRef;
  people: MentionPeople;
  /** The comment being edited; null writes a new one. Key the composer by its id. */
  editing: Comment | null;
  /** The text a new comment starts with, when an edit ended without being saved. */
  draft: CommentDraft | null;
  /**
   * Leaves edit mode: after a save or on Cancel without `keep`, and with the
   * typed text as `keep` when the edited comment turned out to be gone.
   */
  onStopEditing: (keep?: CommentDraft) => void;
};

/**
 * The bar under the comments for writing or editing a comment. Typing `@`
 * lists the board's members; a picked person is shown as `@Name` and stored
 * as a mention token, the form Kazan web and chat read. A failed send keeps
 * the draft, so Retry or a second tap sends it again. An edit of a comment
 * that was deleted meanwhile cannot be saved, so it ends and the typed text
 * stays as a new comment: the user keeps their words and still decides
 * whether to send them.
 */
export function CommentComposer({
  ticketRef,
  people,
  editing,
  draft,
  onStopEditing,
}: CommentComposerProps) {
  const { t } = useTranslation();
  const insets = useSafeAreaInsets();
  const keyboardVisible: boolean = useKeyboardVisible();
  const create = useCreateComment(ticketRef);
  const update = useUpdateComment(ticketRef);
  const showFailure = useWriteFailure();

  // The parent keys the composer by the comment being edited, so a new edit
  // starts from that comment's text and Cancel starts an empty draft.
  const [initial] = React.useState<CommentDraft>(() =>
    editing ? decodeMentions(editing.content, people.names) : (draft ?? EMPTY_DRAFT)
  );
  const [text, setText] = React.useState<string>(initial.text);
  const [picked, setPicked] = React.useState<MentionPerson[]>(initial.picked);
  const [cursor, setCursor] = React.useState<number>(initial.text.length);
  const [forcedSelection, setForcedSelection] = React.useState<Selection | undefined>(
    initial.text ? { start: initial.text.length, end: initial.text.length } : undefined
  );
  const [error, setError] = React.useState<string | null>(null);
  const editingId: string | null = editing?.id ?? null;
  // The text as typed now, for an edit that fails after more typing.
  const latest = React.useRef<CommentDraft>({ text, picked });
  latest.current = { text, picked };

  const query: MentionQuery | null = typedMentionQuery(text, cursor, initial.text);
  const suggestions: MentionPerson[] = query
    ? mentionCandidates(people.candidates, query.query)
    : [];
  const pending: boolean = create.isPending || update.isPending;

  const pick = (person: MentionPerson) => {
    if (!query) return;
    const next = insertMention(text, cursor, query, person);
    setText(next.text);
    setCursor(next.cursor);
    setForcedSelection({ start: next.cursor, end: next.cursor });
    setPicked((current) =>
      current.some((item) => item.userId === person.userId) ? current : [...current, person]
    );
  };

  const onSelectionChange = (event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) => {
    setCursor(event.nativeEvent.selection.end);
    setForcedSelection(undefined);
  };

  const submit = (content: string, commentId: string | null) => {
    if (!onlineManager.isOnline()) {
      toast.error(t("offlineWriteBlocked"));
      return;
    }
    const onSuccess = () => {
      setText("");
      setPicked([]);
      setError(null);
      if (commentId) onStopEditing();
    };
    const onError = (failure: Error) => {
      const apiError: ApiError = toApiError(failure);
      if (apiError.kind === "validation") {
        setError(apiError.fieldErrors.content ?? apiError.message);
        return;
      }
      if (commentId && isCommentEditGone(apiError)) {
        if (apiError.code === COMMENT_NOT_FOUND_CODE) toast.error(t("ticketCommentEditGone"));
        else showFailure(apiError, "put");
        onStopEditing(latest.current);
        return;
      }
      showFailure(apiError, commentId ? "put" : "post", () => submit(content, commentId));
    };
    if (commentId) {
      update.mutate({ commentId, content }, { onSuccess, onError });
    } else {
      create.mutate({ content }, { onSuccess, onError });
    }
  };

  const send = () => {
    const content: string = encodeMentions(text, picked).trim();
    if (!content || pending) return;
    if (content.length > COMMENT_CONTENT_MAX) {
      setError(t("ticketCommentTooLong", { max: COMMENT_CONTENT_MAX }));
      return;
    }
    setError(null);
    submit(content, editingId);
  };

  const canSend: boolean = text.trim().length > 0 && !pending;

  return (
    <View
      className="gap-2 border-t border-border bg-background px-4 pt-2"
      // The keyboard covers the home indicator, so its inset only applies while the keyboard is closed.
      style={{ paddingBottom: keyboardVisible ? 8 : Math.max(insets.bottom, 8) }}
    >
      {editing ? (
        <View className="flex-row items-center gap-2">
          <Text variant="footnote" className="flex-1 text-muted-foreground">
            {t("ticketCommentEditing")}
          </Text>
          <IconButton
            icon={X}
            label={t("ticketCommentCancelEdit")}
            onPress={() => onStopEditing()}
          />
        </View>
      ) : null}
      {suggestions.length > 0 ? (
        <View
          accessibilityLabel={t("ticketMentionSuggestions")}
          className="overflow-hidden rounded-md border border-border bg-card"
        >
          {suggestions.map((person) => (
            <Pressable
              key={person.userId}
              accessibilityRole="button"
              accessibilityLabel={person.name}
              onPress={() => pick(person)}
              className="min-h-11 flex-row items-center gap-3 px-3 py-2 active:opacity-70"
            >
              <Avatar name={person.name} size="sm" />
              <Text variant="body" numberOfLines={1} className="flex-1">
                {person.name}
              </Text>
            </Pressable>
          ))}
        </View>
      ) : null}
      <View className="flex-row items-end gap-2">
        <Textarea
          value={text}
          onChangeText={(value: string) => {
            setText(value);
            if (error) setError(null);
          }}
          onSelectionChange={onSelectionChange}
          selection={forcedSelection}
          placeholder={t("ticketCommentPlaceholder")}
          accessibilityLabel={t("ticketCommentPlaceholder")}
          invalid={!!error}
          rows={1}
          className="max-h-32 flex-1"
        />
        <IconButton
          icon={editing ? Check : SendHorizontal}
          label={editing ? t("ticketCommentSave") : t("ticketCommentSend")}
          variant="filled"
          disabled={!canSend}
          onPress={send}
        />
      </View>
      {error ? (
        <Text variant="footnote" accessibilityRole="alert" className="text-destructive">
          {error}
        </Text>
      ) : null}
    </View>
  );
}
