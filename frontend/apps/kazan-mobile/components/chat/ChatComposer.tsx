import { TextInputWrapper, type PasteEventPayload } from "expo-paste-input";
import {
  Camera,
  Check,
  FileText,
  Images,
  Info,
  Paperclip,
  Pencil,
  Reply,
  Send,
  Smile,
  X,
  type LucideIcon,
} from "lucide-react-native";
import * as React from "react";
import {
  Text as NativeText,
  Platform,
  Pressable,
  TextInput,
  View,
  type NativeSyntheticEvent,
  type TextInputSelectionChangeEventData,
} from "react-native";

import { ChatQuoteThumbnail } from "@/components/chat/ChatAttachmentViews";
import {
  chatFilesProblem,
  isChatPreviewableImage,
  type ChatFilesProblem,
} from "@/components/chat/chatAttachments";
import { chatMessageSnippet } from "@/components/chat/chatDisplay";
import {
  pickChatFiles,
  type ChatFileSource,
  type ChatPickResult,
} from "@/components/chat/chatFilePickers";
import {
  activeChatMentionQuery,
  activeChatTicketQuery,
  chatComposerDraft,
  chatComposerSegments,
  chatMentionPick,
  chatReturnTypedAt,
  chatTicketPick,
  insertChatComposerWord,
  insertChatMention,
  serializeChatComposerText,
  type ChatComposerPick,
  type ChatComposerSegment,
  type ChatMentionQuery,
  type ChatMentionSuggestion,
} from "@/components/chat/chatMentions";
import { ChatMentionList, type ChatSuggestionListHandle } from "@/components/chat/ChatMentionList";
import { ChatEmojiPicker } from "@/components/chat/ChatReactionPicker";
import { ChatStagedFiles } from "@/components/chat/ChatStagedFiles";
import { ChatTicketList } from "@/components/chat/ChatTicketList";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { pastedImageFiles, type PastedImageFiles } from "@/components/kazan/pastedImage";
import { Avatar } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation, type TranslationKey } from "@/i18n";
import {
  CHAT_MAX_FILES,
  CHAT_MAX_TICKET_REFS,
  CHAT_MAX_USER_MENTIONS,
  CHAT_MESSAGE_COUNTER_FROM,
  CHAT_MESSAGE_MAX_LENGTH,
  chatComposerProblem,
  type ChatComposerProblem,
  type ChatLocalFile,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatTicketSuggestion,
} from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

/** What the composer writes besides a new message: a reply, or an edit of an own message. */
export type ChatComposerMode =
  | { type: "reply"; message: ChatMessageResponse; authorName: string }
  | { type: "edit"; message: ChatMessageResponse };

const PROBLEM_TEXT: Record<Exclude<ChatComposerProblem, "empty">, TranslationKey> = {
  tooLong: "chatProblemTooLong",
  tooManyMentions: "chatProblemTooManyMentions",
  tooManyTicketRefs: "chatProblemTooManyTicketRefs",
};

const FILES_PROBLEM_TEXT: Record<ChatFilesProblem, TranslationKey> = {
  tooManyFiles: "chatProblemTooManyFiles",
  filesTooLarge: "chatProblemFilesTooLarge",
};

/** The input grows up to this height, then scrolls. */
const INPUT_MAX_HEIGHT = 140;

/** The quoted text of a reply is cut here; the line truncates it further to fit. */
const REPLY_SNIPPET_LENGTH = 120;

/** The row's buttons share the icon buttons' 44 pt (48 dp) box, so they line up at the bottom. */
const ACTION_BOX = Platform.select({ ios: "h-11", default: "h-12" });

/** Stands in for the name in "Replying to {{name}}", whose word order differs per language. */
const NAME_SLOT = "\u0001";

/** The reach of the small buttons inside the field and the mode bar, about the 44 pt minimum. */
const SMALL_HIT_SLOP = { top: 10, bottom: 10, left: 8, right: 8 } as const;

/**
 * The field and its paste interception share the row's free width; the
 * field inside fills the wrapper's width and grows with its text.
 */
const FIELD_WRAPPER_STYLE = { flex: 1 } as const;

/** The channel's own entry beside the field, such as Share PR or Post update. */
export interface ChatComposerAction {
  label: string;
  icon: LucideIcon;
  onPress: () => void;
}

type ChatComposerProps = {
  channelName: string;
  /** The field's hint; the channel's "Message #name" when not given. */
  placeholder?: string;
  /** False for a guest, who reads but cannot write. */
  canWrite: boolean;
  mode: ChatComposerMode | null;
  onCancelMode: () => void;
  /** Sends a new message; the composer clears at once, the outbox keeps the text and files. */
  onSend: (content: string, replyToMessageId: string | null, files: ChatLocalFile[]) => void;
  /** Saves an edit; resolves when saved, rejects when not (the text then stays). */
  onSaveEdit: (messageId: string, content: string) => Promise<void>;
  /** Called on every change of a non-empty text, for the typing signal. */
  onTyping: () => void;
  /** Who can be mentioned in the channel; undefined while loading. */
  members: readonly ChatMemberResponse[] | undefined;
  membersError: boolean;
  onRetryMembers: () => void;
  /**
   * What the field starts with, such as a shared ticket's chip; the field
   * then takes the focus. Nothing is sent until the reader taps Send.
   */
  initialDraft?: ComposerDraft;
  /**
   * The channel's own entry in place of the attach button, which moves into
   * the field. It shows its label while the field is idle and only its icon
   * once the field has the focus, holds text or writes a reply or an edit,
   * leaving the room to the text.
   */
  action?: ChatComposerAction;
};

/** What the field holds: the text as shown and the mentions picked in it. */
export interface ComposerDraft {
  text: string;
  picks: ChatComposerPick[];
}

const EMPTY_DRAFT: ComposerDraft = { text: "", picks: [] };

/**
 * The field under a channel or a thread: grows with its text, counts
 * characters near the limit, checks the server's limits before a send and
 * shows the reply or edit it is writing. Typing `@` offers the channel's
 * members; a picked member shows as `@Name` and is sent as a mention.
 * Typing `#` offers tickets; a picked ticket shows as its key and is sent
 * as a ticket link. Picks are highlighted in the field, a ticket as a chip.
 * The smile button in the field adds an emoji at the caret. Send shows
 * once there is something to send or a reply or edit is open. A guest sees
 * why the field is missing instead.
 */
export function ChatComposer({
  channelName,
  placeholder,
  canWrite,
  mode,
  onCancelMode,
  onSend,
  onSaveEdit,
  onTyping,
  members,
  membersError,
  onRetryMembers,
  initialDraft,
  action,
}: ChatComposerProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const inputRef = React.useRef<TextInput>(null);
  const suggestionsRef = React.useRef<ChatSuggestionListHandle>(null);
  const [draft, setDraft] = React.useState<ComposerDraft>(initialDraft ?? EMPTY_DRAFT);
  const [caret, setCaret] = React.useState<number>(initialDraft?.text.length ?? 0);
  const [saving, setSaving] = React.useState<boolean>(false);
  const [files, setFiles] = React.useState<ChatLocalFile[]>([]);
  const [attachOpen, setAttachOpen] = React.useState<boolean>(false);
  const [emojiOpen, setEmojiOpen] = React.useState<boolean>(false);
  const [focused, setFocused] = React.useState<boolean>(!!initialDraft);
  const draftRef = React.useRef<ComposerDraft>(initialDraft ?? EMPTY_DRAFT);
  const editingId: string | null = mode?.type === "edit" ? mode.message.id : null;
  const modeKey: string | null = mode ? `${mode.type}:${mode.message.id}` : null;
  const text: string = draft.text;

  // Entering an edit puts the message's text in the field, mentions by name,
  // and keeps the draft; leaving it brings the draft back.
  React.useEffect(() => {
    if (editingId === null) return;
    const kept: ComposerDraft = draftRef.current;
    setDraft(
      mode?.type === "edit" ? chatComposerDraft(mode.message, t("chatUnknownMember")) : EMPTY_DRAFT
    );
    return () => setDraft(kept);
    // The mode object changes with every cache update; the ID decides.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingId]);

  // A reply or an edit started from the message actions puts the caret in the field.
  React.useEffect(() => {
    if (modeKey !== null) inputRef.current?.focus();
  }, [modeKey]);

  if (!canWrite) {
    return (
      <View className="flex-row items-center gap-2 border-t border-border bg-card px-4 py-3">
        <Icon as={Info} size={18} className="text-muted-foreground" />
        <Text variant="footnote" className="shrink text-muted-foreground">
          {t("chatGuestComposer")}
        </Text>
      </View>
    );
  }

  const editing: boolean = mode?.type === "edit";
  // An edit changes the text only; staged files wait for the next new message.
  const stagedFiles: ChatLocalFile[] = editing ? [] : files;
  const content: string = serializeChatComposerText(text, draft.picks);
  const textProblem: ChatComposerProblem | null = chatComposerProblem(content);
  // Files alone make a message, so an empty text only blocks a send without them.
  const problem: ChatComposerProblem | null =
    textProblem === "empty" && stagedFiles.length > 0 ? null : textProblem;
  const filesProblem: ChatFilesProblem | null = chatFilesProblem(stagedFiles);
  const mentionAt: ChatMentionQuery | null = activeChatMentionQuery(
    text,
    Math.min(caret, text.length)
  );
  const ticketAt: ChatMentionQuery | null = mentionAt
    ? null
    : activeChatTicketQuery(text, Math.min(caret, text.length));
  const hint: string = placeholder ?? t("chatComposerPlaceholder", { channel: channelName });
  const problemText: string | null =
    problem && problem !== "empty"
      ? t(PROBLEM_TEXT[problem], {
          max: problem === "tooManyMentions" ? CHAT_MAX_USER_MENTIONS : CHAT_MAX_TICKET_REFS,
        })
      : filesProblem
        ? t(FILES_PROBLEM_TEXT[filesProblem], { max: CHAT_MAX_FILES })
        : null;
  const remainingFiles: number = Math.max(0, CHAT_MAX_FILES - files.length);
  const left: number = CHAT_MESSAGE_MAX_LENGTH - text.length;
  const showCounter: boolean = text.length >= CHAT_MESSAGE_COUNTER_FROM;
  const segments: ChatComposerSegment[] = chatComposerSegments(text, draft.picks);
  // One line of the message being answered: its text, else what kind of files it carries.
  function replyQuoteText(message: ChatMessageResponse): string | null {
    const snippet: string = chatMessageSnippet(
      message,
      t("chatUnknownMember"),
      REPLY_SNIPPET_LENGTH
    );
    if (snippet) return snippet;
    if (message.attachments.length === 0) return null;
    return t(
      message.attachments.every((file) => isChatPreviewableImage(file.contentType))
        ? "chatQuotePhoto"
        : "chatQuoteAttachment"
    );
  }

  const showSend: boolean = textProblem !== "empty" || stagedFiles.length > 0 || mode !== null;
  const replyQuote: string | null = mode?.type === "reply" ? replyQuoteText(mode.message) : null;
  const [replyingBefore = "", replyingAfter = ""] = t("chatReplyingTo", {
    name: NAME_SLOT,
  }).split(NAME_SLOT);
  // The action takes the attach button's place and shrinks to its icon once the text needs the room.
  const actionCompact: boolean = focused || text.length > 0 || mode !== null;
  const invalid: boolean = (!!problem && problem !== "empty") || !!filesProblem;

  const updateDraft = (next: ComposerDraft): void => {
    setDraft(next);
    if (editingId === null) draftRef.current = next;
  };

  const changeText = (value: string): void => {
    // While suggestions are open, Return at the typed word picks the first one instead of a new line.
    const returnAt: number | null = mentionAt || ticketAt ? chatReturnTypedAt(text, value) : null;
    if (returnAt === Math.min(caret, text.length) && suggestionsRef.current?.pickFirst()) return;
    updateDraft({ text: value, picks: draft.picks });
    if (value.trim().length > 0 && editingId === null) onTyping();
  };

  const changeSelection = (
    event: NativeSyntheticEvent<TextInputSelectionChangeEventData>
  ): void => {
    setCaret(event.nativeEvent.selection.end);
  };

  const pickMention = (suggestion: ChatMentionSuggestion): void => {
    if (!mentionAt) return;
    const pick: ChatComposerPick = chatMentionPick(suggestion);
    const inserted = insertChatMention(
      text,
      Math.min(caret, text.length),
      mentionAt,
      pick.display.slice(1)
    );
    updateDraft({
      text: inserted.text,
      picks: [...draft.picks.filter((known) => known.display !== pick.display), pick],
    });
    setCaret(inserted.caret);
  };

  const pickTicket = (ticket: ChatTicketSuggestion): void => {
    if (!ticketAt) return;
    const pick: ChatComposerPick = chatTicketPick(ticket);
    const inserted = insertChatComposerWord(
      text,
      Math.min(caret, text.length),
      ticketAt,
      pick.display
    );
    updateDraft({
      text: inserted.text,
      picks: [...draft.picks.filter((known) => known.display !== pick.display), pick],
    });
    setCaret(inserted.caret);
  };

  const attach = (source: ChatFileSource): void => {
    pickChatFiles(source, remainingFiles)
      .then((result: ChatPickResult) => {
        if (result.kind === "cameraDenied") {
          toast.error(t("chatCameraDenied"));
          return;
        }
        if (result.files.length > 0) setFiles((current) => [...current, ...result.files]);
      })
      .catch((error: unknown) => {
        console.warn("[chat] picking files failed", error instanceof Error ? error.message : error);
        toast.error(t("chatAttachFailed"));
      });
  };

  const attachActions: ActionSheetAction[] = [
    { label: t("chatAttachCamera"), icon: Camera, onPress: () => attach("camera") },
    { label: t("chatAttachPhotos"), icon: Images, onPress: () => attach("library") },
    { label: t("chatAttachDocuments"), icon: FileText, onPress: () => attach("files") },
  ];

  const insertEmoji = (emoji: string): void => {
    const at: number = Math.min(caret, text.length);
    changeText(`${text.slice(0, at)}${emoji}${text.slice(at)}`);
    setCaret(at + emoji.length);
    inputRef.current?.focus();
  };

  const attachButton: React.ReactNode = editing ? null : (
    <IconButton
      icon={Paperclip}
      label={t("chatAttachFiles")}
      disabled={remainingFiles === 0}
      onPress={() => setAttachOpen(true)}
    />
  );

  // A pasted image is staged like a picked one, as long as the message has
  // room for it; an edit changes the text only, so it takes no images.
  // Pasted text is already in the field.
  const pasteIntoField = (payload: PasteEventPayload): void => {
    if (payload.type !== "images" || payload.uris.length === 0 || editing) return;
    const taken: string[] = payload.uris.slice(0, remainingFiles);
    if (taken.length < payload.uris.length) {
      toast.error(t("chatPasteTooMany", { max: CHAT_MAX_FILES }));
    }
    if (taken.length === 0) return;
    pastedImageFiles(taken)
      .then((result: PastedImageFiles) => {
        if (result.files.length > 0) setFiles((current) => [...current, ...result.files]);
        if (result.failed > 0) toast.error(t("pasteImageFailed"));
      })
      .catch((error: unknown) => {
        console.warn(
          "[chat] staging pasted images failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("pasteImageFailed"));
      });
  };

  const removeFile = (key: string): void => {
    setFiles((current) => current.filter((file) => file.key !== key));
  };

  const submit = (): void => {
    if (problem || filesProblem || saving) return;
    if (mode?.type === "edit") {
      setSaving(true);
      onSaveEdit(mode.message.id, content)
        .then(() => {
          setSaving(false);
          onCancelMode();
        })
        .catch((error: unknown) => {
          // The API layer toasted the failure; the text stays for another try.
          console.warn(
            "[chat] saving the edit failed",
            error instanceof Error ? error.message : error
          );
          setSaving(false);
        });
      return;
    }
    onSend(content, mode?.type === "reply" ? mode.message.id : null, files);
    updateDraft(EMPTY_DRAFT);
    setFiles([]);
    if (mode) onCancelMode();
  };

  return (
    <View className="border-t border-border bg-card">
      {mentionAt ? (
        <ChatMentionList
          query={mentionAt.query}
          members={members}
          error={membersError}
          onRetry={onRetryMembers}
          onPick={pickMention}
          ref={suggestionsRef}
        />
      ) : null}
      {ticketAt ? (
        <ChatTicketList query={ticketAt.query} onPick={pickTicket} ref={suggestionsRef} />
      ) : null}
      {mode ? (
        <View className="gap-1.5 px-4 pt-2">
          <View className="flex-row items-center gap-1.5">
            <Icon
              as={mode.type === "edit" ? Pencil : Reply}
              size={14}
              className="text-muted-foreground"
            />
            {mode.type === "reply" ? (
              <View className="flex-1 flex-row items-center gap-1">
                {replyingBefore.trim() ? (
                  <Text variant="footnote" className="text-muted-foreground">
                    {replyingBefore.trim()}
                  </Text>
                ) : null}
                <Avatar name={mode.authorName} size="sm" />
                <Text
                  variant="footnote"
                  numberOfLines={1}
                  className="shrink font-sans-semibold text-foreground"
                >
                  {mode.authorName}
                </Text>
                {replyingAfter.trim() ? (
                  <Text variant="footnote" className="text-muted-foreground">
                    {replyingAfter.trim()}
                  </Text>
                ) : null}
              </View>
            ) : (
              <Text variant="footnote" numberOfLines={1} className="flex-1 text-muted-foreground">
                {t("chatEditing")}
              </Text>
            )}
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={t(mode.type === "reply" ? "chatCancelReply" : "chatCancelEdit")}
              onPress={onCancelMode}
              hitSlop={SMALL_HIT_SLOP}
              className="size-6 items-center justify-center rounded-full bg-secondary active:opacity-60"
            >
              <Icon as={X} size={14} className="text-muted-foreground" />
            </Pressable>
          </View>
          {mode.type === "reply" && (replyQuote || mode.message.attachments.length > 0) ? (
            <View className="flex-row items-center gap-2 rounded-md border-l-2 border-primary bg-muted py-1.5 pl-2.5 pr-2">
              {replyQuote ? (
                <Text variant="footnote" numberOfLines={2} className="flex-1 text-muted-foreground">
                  {replyQuote}
                </Text>
              ) : (
                <View className="flex-1" />
              )}
              <ChatQuoteThumbnail attachments={mode.message.attachments} />
            </View>
          ) : null}
        </View>
      ) : null}
      <ChatStagedFiles files={stagedFiles} onRemove={removeFile} />
      <View
        className={cn(
          "flex-row items-end gap-1 py-2",
          editing && !action ? "pl-4" : "pl-1",
          showSend ? "pr-1" : "pr-3"
        )}
      >
        {action ? (
          <View className={cn("justify-center pl-2 pr-1", ACTION_BOX)}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={action.label}
              onPress={action.onPress}
              className={cn(
                "h-9 flex-row items-center justify-center gap-1.5 rounded-full bg-primary-light active:opacity-60",
                actionCompact ? "w-9" : "pl-3 pr-3.5"
              )}
            >
              <Icon as={action.icon} size={18} className="text-primary" />
              {actionCompact ? null : (
                <Text variant="subhead" className="font-sans-semibold text-primary">
                  {action.label}
                </Text>
              )}
            </Pressable>
          </View>
        ) : (
          attachButton
        )}
        <View
          className={cn(
            "min-h-10 flex-1 flex-row items-end rounded-2xl border bg-background pr-1",
            invalid ? "border-destructive" : focused ? "border-primary" : "border-input"
          )}
        >
          <TextInputWrapper onPaste={pasteIntoField} style={FIELD_WRAPPER_STYLE}>
            {/* The text is given as children, not `value`, so picks can carry their own style. */}
            <TextInput
              ref={inputRef}
              autoFocus={!!initialDraft}
              onChangeText={changeText}
              onSelectionChange={changeSelection}
              onFocus={() => setFocused(true)}
              onBlur={() => setFocused(false)}
              multiline
              placeholder={hint}
              placeholderTextColor={colors.mutedForeground}
              accessibilityLabel={hint}
              textAlignVertical="center"
              style={{ maxHeight: INPUT_MAX_HEIGHT }}
              className="min-h-10 py-2 pl-3.5 pr-1 font-sans text-body text-foreground"
            >
              {segments.map((segment: ChatComposerSegment, index: number) =>
                segment.pick ? (
                  <NativeText
                    key={index}
                    className={cn(
                      "text-link",
                      segment.pick.token.startsWith("<#") ? "bg-primary-light" : null
                    )}
                  >
                    {segment.text}
                  </NativeText>
                ) : (
                  segment.text
                )
              )}
            </TextInput>
          </TextInputWrapper>
          {action && !editing ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={t("chatAttachFiles")}
              disabled={remainingFiles === 0}
              onPress={() => setAttachOpen(true)}
              hitSlop={SMALL_HIT_SLOP}
              className="h-10 w-8 items-center justify-center active:opacity-60 disabled:opacity-40"
            >
              <Icon as={Paperclip} size={20} className="text-muted-foreground" />
            </Pressable>
          ) : null}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={t("chatInsertEmoji")}
            onPress={() => setEmojiOpen(true)}
            hitSlop={SMALL_HIT_SLOP}
            className="h-10 w-8 items-center justify-center active:opacity-60"
          >
            <Icon as={Smile} size={20} className="text-muted-foreground" />
          </Pressable>
        </View>
        {showSend ? (
          <IconButton
            icon={mode?.type === "edit" ? Check : Send}
            label={t(mode?.type === "edit" ? "chatSave" : "chatSend")}
            variant="filled"
            disabled={!!problem || !!filesProblem || saving}
            onPress={submit}
          />
        ) : null}
      </View>
      {problemText || showCounter ? (
        <View className="flex-row gap-2 px-4 pb-2">
          {problemText ? (
            <Text variant="caption" className="flex-1 text-destructive">
              {problemText}
            </Text>
          ) : (
            <View className="flex-1" />
          )}
          {showCounter ? (
            <Text
              variant="caption"
              className={left < 0 ? "text-destructive" : "text-muted-foreground"}
            >
              {t("chatCharactersLeft", { count: left })}
            </Text>
          ) : null}
        </View>
      ) : null}
      <ActionSheet
        visible={attachOpen}
        onClose={() => setAttachOpen(false)}
        title={t("chatAttachFiles")}
        actions={attachActions}
      />
      <ChatEmojiPicker open={emojiOpen} onClose={() => setEmojiOpen(false)} onPick={insertEmoji} />
    </View>
  );
}
