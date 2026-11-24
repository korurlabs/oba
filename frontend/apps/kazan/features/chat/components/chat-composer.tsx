"use client";

import {
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type ComponentPropsWithoutRef,
  type DragEvent,
  type ReactNode,
  type Ref,
  type RefObject,
} from "react";
import { useTranslations } from "next-intl";
import { Extension, mergeAttributes, Node, type AnyExtension, type Editor } from "@tiptap/core";
import CodeBlockLowlight from "@tiptap/extension-code-block-lowlight";
import Mention from "@tiptap/extension-mention";
import Placeholder from "@tiptap/extension-placeholder";
import type { Node as ProseMirrorNode } from "@tiptap/pm/model";
import { Plugin, PluginKey, TextSelection, type EditorState } from "@tiptap/pm/state";
import type { EditorView } from "@tiptap/pm/view";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import type { SuggestionOptions, SuggestionProps } from "@tiptap/suggestion";
import { Markdown } from "tiptap-markdown";
import { Paperclip, Pencil, SendHorizontal, Smile, Upload, X } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { OrganizationRole } from "@oba/shared/features/users/schema";
import { cn } from "@oba/ui/lib/utils";
import { getDisplayName, UserAvatar } from "@/components/ui/user-avatar";
import { useRealtimeContext } from "@/features/realtime/realtime-provider";
import { useEditChatMessage, useSendChatMessage } from "../hooks/use-chat-mutations";
import { useChatReadableChannelId } from "../hooks/use-chat-channels";
import { useChatMembers, useChatOrganizationId, useChatOutbox } from "../hooks/use-chat-messages";
import { useChatThreadDraft, type ChatThreadDraft } from "../hooks/use-chat-thread-draft";
import { chatCodeLowlight, normalizeChatCodeFences } from "../lib/chat-code-fence";
import { ChatCodeInput, convertViewLiteralCode, insertLiteralCodeText, isInsideOpenFence } from "../lib/chat-code-input";
import { CHAT_TYPING_DESTINATION } from "../lib/chat-realtime";
import { chatComposerKeymap } from "../lib/chat-shortcuts";
import { chatMessagePlainText } from "../lib/chat-message-text";
import { chatUserDisplayName } from "../lib/render-chat-markdown";
import {
  CHAT_ERROR_CODES,
  CHAT_MAX_FILES,
  CHAT_MAX_REQUEST_BYTES,
  CHAT_MAX_TICKET_REFS,
  CHAT_MAX_USER_MENTIONS,
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  CHAT_MESSAGE_COUNTER_FROM,
  CHAT_MESSAGE_MAX_LENGTH,
  CHAT_TICKET_TOKEN_PATTERN,
  CHAT_TYPING_INTERVAL_MS,
  CHAT_USER_TOKEN_PATTERN,
  chatComposerSchema,
  chatTicketToken,
  chatUserToken,
  type ChatMessageResponse,
  type ChatTypingRequest,
} from "../schemas";
import { ChatComposerFiles, formatChatFileSize } from "./chat-attachments";
import { ChatEmojiPicker } from "./chat-emoji-picker";
import { ChatMentionList, type ChatSuggestionChoice, type ChatSuggestionListHandle } from "./chat-mention-list";
import {
  CHAT_CONVERT_MESSAGE_LINKS_META,
  ChatMessageLinkNode,
  convertEditorChatMessageLinks,
} from "./chat-message-link-node";
import { ChatQuote, chatQuoteImage, chatQuoteText } from "./chat-reply-preview";
import { ChatTicketSuggestionList, chatTicketChipLabel } from "./chat-ticket-suggestion-list";

// ── Access ──────────────────────────────────────────────────────────

/**
 * False for a `GUEST` of the active organization: guests read the channel
 * but cannot send, edit or react (the server answers 403 `ORG_SEC_003`).
 * True while the profile loads, so members never see the composer flash
 * away; the server still decides.
 */
export function useChatCanWrite(): boolean {
  return useChatRole() !== "GUEST";
}

/** The reader's role in the chat's organization; undefined until `/me` is in. */
export function useChatRole(): OrganizationRole | undefined {
  const orgId = useChatOrganizationId();
  const { data: meResponse } = useGetMe();
  return meResponse?.data?.organizations?.find((organization) => organization.id === orgId)?.role;
}

// ── Editor extensions ───────────────────────────────────────────────

/** The trigger character of a suggestion; also stored on the chip it inserts. */
type ChatSuggestionKind = "@" | "#";

/** The open suggestion: which list, the typed query and the command that inserts the chip. */
interface ActiveSuggestion {
  kind: ChatSuggestionKind;
  query: string;
  command: (choice: ChatSuggestionChoice) => void;
}

/** What the composer's keys call; read through a ref, so the editor is built once. */
export interface ChatComposerKeyHandlers {
  onSubmit: () => void;
  /** Returns true when Escape did something (cancelled an edit or a reply, or the conversation's own action). */
  onEscape: () => boolean;
  /** ArrowUp in an empty composer; returns true when it started editing the reader's last message. */
  onEditLast: () => boolean;
}

export interface ChatComposerExtensionOptions {
  placeholder: () => string;
  keyHandlers: RefObject<ChatComposerKeyHandlers | null>;
  /** Called on open, update and close (null) of the `@` and `#` suggestions. */
  onSuggestionChange: (active: ActiveSuggestion | null) => void;
  /** The open list, which receives Up, Down, Enter and Tab. */
  suggestionList: RefObject<ChatSuggestionListHandle | null>;
}

const MENTION_SUGGESTION_KEY = new PluginKey("chatMentionSuggestion");
const TICKET_SUGGESTION_KEY = new PluginKey("chatTicketSuggestion");
const SUGGESTION_KEYS = [MENTION_SUGGESTION_KEY, TICKET_SUGGESTION_KEY];

function isSuggestionOpen(state: EditorState): boolean {
  return SUGGESTION_KEYS.some((key) => (key.getState(state) as { active?: boolean } | undefined)?.active === true);
}

/** A markdown serializer state, the part of prosemirror-markdown's API the chat nodes use. */
interface MarkdownSerializerState {
  text: (text: string, escape?: boolean) => void;
  write: (content: string) => void;
}

/**
 * Plain text without the HTML escaping tiptap-markdown applies by default:
 * the message is markdown read by the chat renderer, which never reads
 * HTML, so `<` and `>` stay as typed. Markdown characters are still
 * escaped, and the renderer reads the backslash escapes back.
 */
const ChatText = Node.create({
  name: "text",
  group: "inline",
  addStorage() {
    return {
      markdown: {
        serialize(state: MarkdownSerializerState, node: ProseMirrorNode) {
          state.text(node.text ?? "");
        },
        parse: {},
      },
    };
  },
});

// Square outline chips, the same as the posted message renders them, so the
// draft shows the mention and the ticket reference as they will appear.
const USER_CHIP_CLASS = "border-primary/25 text-primary rounded-none border px-1 py-px font-medium";
const TICKET_CHIP_CLASS = "border-primary/30 bg-primary/5 text-primary rounded-none border px-1 py-px text-[13px]";

/**
 * Member and ticket chips. `@` chips are sent as `<@userId>`, `<@everyone>`
 * or `<@here>`, `#` chips as `<#ticketId>`; the label is only for the
 * author, the server renders names from the IDs.
 */
const ChatMention = Mention.extend({
  addStorage() {
    return {
      markdown: {
        serialize(state: MarkdownSerializerState, node: ProseMirrorNode) {
          const id = String(node.attrs.id ?? "");
          state.write(node.attrs.mentionSuggestionChar === "#" ? chatTicketToken(id) : chatUserToken(id));
        },
        parse: {},
      },
    };
  },
});

/**
 * A suggestion opens only where a chip can stand and never inside code,
 * where tokens are literal text.
 */
function allowSuggestion({ state, range }: { state: EditorState; range: { from: number } }): boolean {
  const $from = state.doc.resolve(range.from);
  if ($from.parent.type.spec.code) return false;
  if ($from.marks().some((mark) => mark.type.spec.code)) return false;
  const mentionType = state.schema.nodes[ChatMention.name];
  return !!mentionType && !!$from.parent.type.contentMatch.matchType(mentionType);
}

function suggestionRenderer(
  kind: ChatSuggestionKind,
  options: Pick<ChatComposerExtensionOptions, "onSuggestionChange" | "suggestionList" | "keyHandlers">,
): SuggestionOptions["render"] {
  return () => {
    const publish = (props: SuggestionProps) =>
      options.onSuggestionChange({
        kind,
        query: props.query,
        command: (choice) => props.command({ id: choice.id, label: choice.label }),
      });
    return {
      onStart: publish,
      onUpdate: publish,
      onKeyDown: ({ event }) => {
        if (event.key === "Escape") return true;
        // Shift+Enter breaks the line, as it does without the list.
        if (event.key === "Enter" && event.shiftKey) return false;
        if (options.suggestionList.current?.onKeyDown(event)) return true;
        // With nothing to pick, Enter sends the message as it would without the list.
        if (event.key === "Enter" && !event.shiftKey) {
          options.keyHandlers.current?.onSubmit();
          return true;
        }
        return false;
      },
      onExit: () => options.onSuggestionChange(null),
    };
  };
}

/** A paragraph holding only an opening fence, e.g. ` ```ts `; group 1 is the language. */
const OPENING_FENCE_PATTERN = /^```([\w#+.-]*)$/;
/** The fence that closes a code block, typed alone on its last line. */
const CLOSING_FENCE = "```";
/** Tab inside a code block indents by this. */
const CODE_INDENT = "  ";

/** True while the caret (an empty selection) stands in a code block. */
function inCodeBlock(state: EditorState): boolean {
  return state.selection.empty && !!state.selection.$from.parent.type.spec.code;
}

/** True when nothing but the current line follows the caret in its code block. */
function onLastCodeLine(state: EditorState): boolean {
  const { $from } = state.selection;
  return !$from.parent.textContent.slice($from.parentOffset).includes("\n");
}

/**
 * Leaves the code block at the caret: drops its last `dropTail` characters
 * (the typed closing fence and its line break) and puts the caret in a
 * paragraph after the block, creating one when nothing follows. A block left
 * empty becomes that paragraph.
 */
function leaveCodeBlock(editor: Editor, dropTail: number): boolean {
  return editor.commands.command(({ state, tr, dispatch }) => {
    const { $from } = state.selection;
    const block = $from.parent;
    const blockStart = $from.before();
    const paragraph = state.schema.nodes.paragraph;
    if (!paragraph) return false;
    if (!dispatch) return true;
    const contentEnd = blockStart + 1 + block.content.size;
    if (dropTail > 0) tr.delete(contentEnd - dropTail, contentEnd);
    if (tr.doc.nodeAt(blockStart)?.content.size === 0) {
      tr.setNodeMarkup(blockStart, paragraph);
      tr.setSelection(TextSelection.create(tr.doc, blockStart + 1));
      return true;
    }
    const after = tr.mapping.map(blockStart + block.nodeSize);
    const next = tr.doc.nodeAt(after);
    if (!next || !next.isTextblock) tr.insert(after, paragraph.create());
    tr.setSelection(TextSelection.create(tr.doc, after + 1));
    tr.scrollIntoView();
    return true;
  });
}

/** Inserts plain text at the caret, past the markdown parser of `insertContent`. */
function insertPlainText(editor: Editor, text: string): boolean {
  return editor.commands.command(({ tr, dispatch }) => {
    if (dispatch) tr.insertText(text);
    return true;
  });
}

/**
 * The composer keys. Enter sends and Shift+Enter breaks the line (a new item
 * inside a list); in a code block both add a line, Tab indents and Mod+Enter
 * sends, and typing the closing fence on an empty last line or ArrowDown on
 * the last line leaves the block. ` ```lang ` and Enter opens a block.
 * ArrowUp in an empty composer edits the reader's last message, Escape
 * cancels or does the conversation's own action, and the formatting keys
 * toggle bold, italic, strike, inline code and a code block. Enter and
 * Escape leave an open suggestion to its list. The bindings come from the
 * chat's shortcut list, which the shortcuts dialog shows. Runs before the
 * StarterKit keys and the suggestion plugins.
 */
function createChatComposerKeys(keyHandlers: RefObject<ChatComposerKeyHandlers | null>) {
  return Extension.create({
    name: "chatComposerKeys",
    priority: 1000,
    addKeyboardShortcuts() {
      const keys = chatComposerKeymap({
        send: () => {
          const { state } = this.editor;
          if (isSuggestionOpen(state)) return false;
          if (inCodeBlock(state)) {
            const { $from } = state.selection;
            const lineStart = $from.parent.textContent.lastIndexOf("\n", $from.parentOffset - 1) + 1;
            const line = $from.parent.textContent.slice(lineStart);
            if (onLastCodeLine(state) && line === CLOSING_FENCE) {
              return leaveCodeBlock(this.editor, lineStart > 0 ? CLOSING_FENCE.length + 1 : CLOSING_FENCE.length);
            }
            return this.editor.commands.newlineInCode();
          }
          const { $from } = state.selection;
          const fence = state.selection.empty && $from.parent.type.name === "paragraph"
            ? OPENING_FENCE_PATTERN.exec($from.parent.textContent)
            : null;
          if (fence && $from.parentOffset === $from.parent.content.size) {
            return this.editor
              .chain()
              .deleteRange({ from: $from.start(), to: $from.end() })
              .setCodeBlock({ language: fence[1] || null } as { language: string })
              .run();
          }
          keyHandlers.current?.onSubmit();
          return true;
        },
        newLine: () => {
          if (inCodeBlock(this.editor.state)) return this.editor.commands.newlineInCode();
          return this.editor.commands.first(({ commands }) => [
            () => commands.splitListItem("listItem"),
            () => commands.setHardBreak(),
          ]);
        },
        sendFromCode: () => {
          // An open suggestion list takes Mod+Enter as it takes Enter.
          if (isSuggestionOpen(this.editor.state)) return false;
          keyHandlers.current?.onSubmit();
          return true;
        },
        codeIndent: () => {
          if (!inCodeBlock(this.editor.state)) return false;
          return insertPlainText(this.editor, CODE_INDENT);
        },
        editLast: () => {
          if (isSuggestionOpen(this.editor.state) || !this.editor.isEmpty) return false;
          return keyHandlers.current?.onEditLast() ?? false;
        },
        escape: () => {
          if (isSuggestionOpen(this.editor.state)) return false;
          return keyHandlers.current?.onEscape() ?? false;
        },
        bold: () => this.editor.commands.toggleBold(),
        italic: () => this.editor.commands.toggleItalic(),
        strike: () => this.editor.commands.toggleStrike(),
        inlineCode: () => this.editor.commands.toggleCode(),
        codeBlock: () => this.editor.commands.toggleCodeBlock(),
      });
      return {
        ...keys,
        // Leaving a code block is an editing key, not a listed shortcut.
        ArrowDown: () => {
          const { state } = this.editor;
          if (!inCodeBlock(state) || !onLastCodeLine(state)) return false;
          return leaveCodeBlock(this.editor, 0);
        },
      };
    },
    addProseMirrorPlugins() {
      const editor = this.editor;
      return [
        new Plugin({
          key: new PluginKey("chatCodeBlockExit"),
          props: {
            // The third backtick of a closing fence typed alone on the last
            // line of a code block leaves the block instead of being typed.
            handleTextInput: (view, from, to, text) => {
              const { state } = view;
              if (text !== "`" || from !== to || !inCodeBlock(state) || !onLastCodeLine(state)) return false;
              const { $from } = state.selection;
              if ($from.parentOffset !== $from.parent.content.size) return false;
              const before = $from.parent.textContent.slice(0, $from.parentOffset);
              const lineStart = before.lastIndexOf("\n") + 1;
              if (before.slice(lineStart) !== "``") return false;
              return leaveCodeBlock(editor, lineStart > 0 ? 3 : 2);
            },
          },
        }),
      ];
    },
  });
}

/**
 * The extensions whose markdown input rules the composer keeps. Code is not
 * among them: `ChatCodeInput` alone turns backticks into inline code and
 * fences into code blocks, on the closing delimiter. Tiptap's own rules would
 * open a block on ``` and a space, swallowing a fence written on one line,
 * and turn `x` into inline code inside a fence that is still open.
 */
export const CHAT_COMPOSER_INPUT_RULES = ["blockquote", "bold", "italic", "strike", "bulletList", "orderedList"];

/**
 * The composer's editor extensions: a StarterKit subset for the markdown
 * the chat renders, the member, ticket and message link chips, the composer keys and the
 * markdown serializer. Extensions for further syntax or keys are added to
 * this list.
 */
export function createChatComposerExtensions(options: ChatComposerExtensionOptions): AnyExtension[] {
  return [
    StarterKit.configure({
      text: false,
      heading: false,
      horizontalRule: false,
      codeBlock: false,
      underline: false,
      link: { openOnClick: false, autolink: false },
    }),
    // Stored as a fenced block (```lang); the language defaults to none.
    CodeBlockLowlight.configure({
      lowlight: chatCodeLowlight,
      defaultLanguage: null,
      exitOnTripleEnter: false,
      exitOnArrowDown: false,
      enableTabIndentation: false,
    }),
    ChatText,
    ChatCodeInput,
    ChatMessageLinkNode,
    Placeholder.configure({ placeholder: options.placeholder }),
    ChatMention.configure({
      deleteTriggerWithBackspace: true,
      renderText: ({ node }) =>
        node.attrs.mentionSuggestionChar === "#"
          ? String(node.attrs.label ?? "")
          : `@${String(node.attrs.label ?? node.attrs.id ?? "")}`,
      renderHTML: ({ node, options: mentionOptions }) => {
        const isTicket = node.attrs.mentionSuggestionChar === "#";
        return [
          "span",
          mergeAttributes(mentionOptions.HTMLAttributes, { class: isTicket ? TICKET_CHIP_CLASS : USER_CHIP_CLASS }),
          isTicket ? String(node.attrs.label ?? "") : `@${String(node.attrs.label ?? node.attrs.id ?? "")}`,
        ];
      },
      suggestions: [
        {
          char: "@",
          pluginKey: MENTION_SUGGESTION_KEY,
          allow: allowSuggestion,
          items: () => [],
          render: suggestionRenderer("@", options),
        },
        {
          char: "#",
          pluginKey: TICKET_SUGGESTION_KEY,
          allow: allowSuggestion,
          items: () => [],
          render: suggestionRenderer("#", options),
        },
      ],
    }),
    createChatComposerKeys(options.keyHandlers),
    Markdown.configure({
      html: false,
      breaks: true,
      tightLists: true,
      bulletListMarker: "-",
      linkify: false,
      transformPastedText: true,
      transformCopiedText: false,
    }),
  ];
}

// ── Markdown in and out ─────────────────────────────────────────────

interface MarkdownStorage {
  getMarkdown: () => string;
}

/** The editor's content as the markdown with tokens that is sent; trailing white space dropped. */
function readMarkdown(editor: Editor): string {
  const storage = (editor.storage as unknown as { markdown?: MarkdownStorage }).markdown;
  if (!storage) throw new Error("The chat composer has no markdown serializer");
  return storage.getMarkdown().replace(/\s+$/, "");
}

/**
 * Loads a stored message for editing: its markdown becomes rich text and
 * each token outside code becomes the chip it was sent from, labelled from
 * the message's own users and ticket references.
 */
function loadMessageForEdit(
  editor: Editor,
  message: ChatMessageResponse,
  labels: { unknownMember: string; unknownTicket: string },
): void {
  editor.commands.setContent(message.content, { emitUpdate: false });
  const { state } = editor;
  const mentionType = state.schema.nodes[ChatMention.name];
  if (!mentionType) return;

  const ticketLabels = new Map<string, string>();
  for (const ref of message.ticketRefs) {
    if (ref.ticketId) ticketLabels.set(ref.ticketId.toLowerCase(), chatTicketChipLabel({ key: ref.ticketKey, title: ref.ticketTitle }));
  }
  const userLabel = (id: string): string =>
    id === CHAT_MENTION_EVERYONE || id === CHAT_MENTION_HERE
      ? id
      : chatUserDisplayName(message.users[id], labels.unknownMember);

  const replacements: { from: number; to: number; node: ProseMirrorNode }[] = [];
  state.doc.descendants((node, pos, parent) => {
    if (parent?.type.spec.code) return false;
    if (!node.isText || node.marks.some((mark) => mark.type.spec.code)) return true;
    const text = node.text ?? "";
    for (const match of text.matchAll(CHAT_USER_TOKEN_PATTERN)) {
      const id = match[1];
      if (!id || match.index === undefined) continue;
      replacements.push({
        from: pos + match.index,
        to: pos + match.index + match[0].length,
        node: mentionType.create({ id, label: userLabel(id), mentionSuggestionChar: "@" }),
      });
    }
    for (const match of text.matchAll(CHAT_TICKET_TOKEN_PATTERN)) {
      const id = match[1];
      if (!id || match.index === undefined) continue;
      replacements.push({
        from: pos + match.index,
        to: pos + match.index + match[0].length,
        node: mentionType.create({
          id,
          label: ticketLabels.get(id.toLowerCase()) ?? labels.unknownTicket,
          mentionSuggestionChar: "#",
        }),
      });
    }
    return true;
  });
  if (replacements.length === 0) return;

  // From the end, so earlier positions stay valid.
  replacements.sort((a, b) => b.from - a.from);
  const tr = state.tr;
  for (const replacement of replacements) tr.replaceWith(replacement.from, replacement.to, replacement.node);
  tr.setMeta("addToHistory", false);
  editor.view.dispatch(tr);
}

// ── Composer ────────────────────────────────────────────────────────

/** What a parent may do with the composer, e.g. focus it from a shortcut. */
export interface ChatComposerHandle {
  focus: () => void;
  /** Focuses the composer and types `text` at its end, for a key pressed outside it. */
  typeText: (text: string) => void;
}

interface ChatComposerProps {
  /** Null writes to the channel. */
  threadId: string | null;
  placeholder: string;
  /** The message being replied to; shows the reply bar. */
  replyTo: ChatMessageResponse | null;
  onCancelReply: () => void;
  /** The reader's own message being edited in place; null composes a new one. */
  editing: ChatMessageResponse | null;
  /** Called when the edit was saved or cancelled. */
  onEditDone: () => void;
  /** The members typing in this conversation, from `useTypingUsers`. */
  typingUserIds: readonly string[];
  /** Puts the caret in the editor once it is ready, e.g. in a thread that was just created. */
  autoFocus?: boolean;
  /** ArrowUp in an empty composer: starts editing the reader's last message; false when there is none. */
  onEditLast?: () => boolean;
  /**
   * Escape with neither a reply nor an edit to cancel, told whether the
   * composer is empty; returns true when it did something.
   */
  onIdleEscape?: (empty: boolean) => boolean;
  ref?: Ref<ChatComposerHandle>;
}

/**
 * The message composer of the channel or a thread, with the typing line
 * above it. A `GUEST` sees a read-only notice instead of the editor.
 */
export function ChatComposer({ typingUserIds, ...props }: ChatComposerProps) {
  const t = useTranslations("chat.composer");
  const canWrite = useChatCanWrite();

  return (
    <div className="shrink-0 px-4 pb-3 lg:px-6">
      <ChatTypingLine userIds={typingUserIds} />
      {canWrite ? (
        <ChatComposerEditor {...props} />
      ) : (
        <p className="border-border bg-muted/40 text-muted-foreground rounded-none border px-3 py-2.5 text-sm">
          {t("guest")}
        </p>
      )}
    </div>
  );
}

type ChatComposerEditorProps = Omit<ChatComposerProps, "typingUserIds">;

function ChatComposerEditor({
  threadId,
  placeholder,
  replyTo,
  onCancelReply,
  editing,
  onEditDone,
  autoFocus = false,
  onEditLast,
  onIdleEscape,
  ref,
}: ChatComposerEditorProps) {
  const t = useTranslations("chat.composer");
  const tMessage = useTranslations("chat.message");
  const orgId = useChatOrganizationId();
  const role = useChatRole();
  const { publish } = useRealtimeContext();
  const { send } = useSendChatMessage();
  const editMessage = useEditChatMessage();
  // Sends, typing signals and the outbox follow the channel the reader can read, never a URL about to be replaced.
  const channelId = useChatReadableChannelId();
  const outbox = useChatOutbox(orgId, channelId, threadId);

  const [content, setContent] = useState("");
  const [suggestion, setSuggestion] = useState<ActiveSuggestion | null>(null);
  const [dragDepth, setDragDepth] = useState(0);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const suggestionListRef = useRef<ChatSuggestionListHandle | null>(null);
  const keyHandlersRef = useRef<ChatComposerKeyHandlers | null>(null);
  const placeholderRef = useRef(placeholder);
  const addFilesRef = useRef<(added: File[]) => void>(() => undefined);
  const lastTypingAtRef = useRef(0);
  const editingRef = useRef<ChatMessageResponse | null>(editing);
  /** The unsent draft, kept while an edit borrows the editor. */
  const draftRef = useRef<ReturnType<Editor["getJSON"]> | null>(null);
  /** The editor for its own paste handler, which is created with it. */
  const editorRef = useRef<Editor | null>(null);
  const filesRef = useRef<File[]>([]);

  // A thread's composer goes when the thread is closed under the writer, and
  // the unsent text and files come back when it is reopened.
  const readDraft = useCallback((): ChatThreadDraft | null | undefined => {
    const current = editorRef.current;
    if (!current || current.isDestroyed) return undefined;
    // While an edit borrows the editor, the unsent text waits in draftRef.
    const text = editingRef.current ? draftRef.current : current.isEmpty ? null : current.getJSON();
    if (!text && filesRef.current.length === 0) return null;
    return { content: text, files: filesRef.current };
  }, []);
  const initialDraft = useChatThreadDraft(threadId, readDraft);

  const [files, setFiles] = useState<File[]>(() => initialDraft?.files ?? []);
  useEffect(() => {
    filesRef.current = files;
  }, [files]);

  useEffect(() => {
    placeholderRef.current = placeholder;
  }, [placeholder]);

  const extensions = useMemo(
    () =>
      createChatComposerExtensions({
        placeholder: () => placeholderRef.current,
        keyHandlers: keyHandlersRef,
        onSuggestionChange: setSuggestion,
        suggestionList: suggestionListRef,
      }),
    [],
  );

  const onEditorUpdate = useCallback(
    (current: Editor) => {
      setContent(readMarkdown(current));
      // A typing signal for a new message with text, at most once per interval;
      // the server drops faster ones. Only a known role above GUEST sends it:
      // the server drops a guest's signal, and the editor shows while the role
      // is still loading.
      if (editingRef.current || current.isEmpty) return;
      if (role === undefined || role === "GUEST") return;
      if (!channelId) return;
      const now = Date.now();
      if (now - lastTypingAtRef.current < CHAT_TYPING_INTERVAL_MS) return;
      const body: ChatTypingRequest = { channelId, threadId };
      if (publish(CHAT_TYPING_DESTINATION, body)) lastTypingAtRef.current = now;
    },
    [publish, role, channelId, threadId],
  );
  const onEditorUpdateRef = useRef(onEditorUpdate);
  useEffect(() => {
    onEditorUpdateRef.current = onEditorUpdate;
  }, [onEditorUpdate]);

  const editor = useEditor({
    immediatelyRender: false,
    autofocus: autoFocus ? "end" : false,
    content: initialDraft?.content ?? undefined,
    extensions,
    enableInputRules: CHAT_COMPOSER_INPUT_RULES,
    editorProps: {
      attributes: {
        class: cn(
          "max-h-[40vh] min-h-6 overflow-y-auto py-2.5 text-sm leading-relaxed outline-none break-words",
          "[&_p.is-editor-empty:first-child]:before:text-muted-foreground [&_p.is-editor-empty:first-child]:before:pointer-events-none [&_p.is-editor-empty:first-child]:before:float-left [&_p.is-editor-empty:first-child]:before:h-0 [&_p.is-editor-empty:first-child]:before:content-[attr(data-placeholder)]",
          "[&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5 [&_blockquote]:border-border [&_blockquote]:border-l-4 [&_blockquote]:pl-3",
          "[&_code]:bg-muted [&_code]:rounded [&_code]:px-1 [&_code]:font-mono [&_code]:text-[13px] [&_a]:text-primary [&_a]:underline",
          "[&_pre]:bg-muted/70 [&_pre]:border-border [&_pre]:my-1 [&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:border [&_pre]:px-3 [&_pre]:py-2 [&_pre]:font-mono [&_pre]:text-xs [&_pre]:leading-5 [&_pre_code]:bg-transparent [&_pre_code]:p-0 [&_pre_code]:text-xs",
          // Token colours of the code block, the palette of the rendered blocks.
          "[&_.hljs-comment]:text-muted-foreground [&_.hljs-comment]:italic [&_.hljs-keyword]:text-[oklch(0.55_0.2_300)] [&_.hljs-type]:text-[oklch(0.55_0.2_300)] [&_.hljs-string]:text-[oklch(0.52_0.15_155)] [&_.hljs-number]:text-[oklch(0.58_0.16_50)] [&_.hljs-literal]:text-[oklch(0.58_0.16_50)] [&_.hljs-built_in]:text-[oklch(0.55_0.15_250)] [&_.hljs-title]:text-[oklch(0.55_0.15_250)] [&_.hljs-attr]:text-[oklch(0.55_0.12_200)] [&_.hljs-variable]:text-[oklch(0.55_0.18_10)] [&_.hljs-meta]:text-[oklch(0.58_0.14_85)]",
        ),
        "aria-label": placeholder,
        "aria-multiline": "true",
        role: "textbox",
      },
      handlePaste: (view: EditorView, event: ClipboardEvent) => {
        const pasted = Array.from(event.clipboardData?.files ?? []);
        if (pasted.length > 0 && !editingRef.current) {
          addFilesRef.current(pasted);
          return true;
        }
        // Plain text pasted into a fence that is still open goes in literally,
        // and text with ``` fences elsewhere becomes code blocks; both also
        // when the clipboard carries HTML (an editor or a web page), which
        // would otherwise win. Inside a code block text stays literal.
        const text = event.clipboardData?.getData("text/plain") ?? "";
        if (isInsideOpenFence(view.state.selection.$from) && insertLiteralCodeText(view, text)) return true;
        if (!text.includes("```") || view.state.selection.$from.parent.type.spec.code) return false;
        const current = editorRef.current;
        if (!current) return false;
        current.chain().setMeta(CHAT_CONVERT_MESSAGE_LINKS_META, true).insertContent(normalizeChatCodeFences(text)).run();
        return true;
      },
      handleDrop: (_view: EditorView, event: globalThis.DragEvent, _slice: unknown, moved: boolean) => {
        const dropped = Array.from(event.dataTransfer?.files ?? []);
        if (moved || dropped.length === 0 || editingRef.current) return false;
        addFilesRef.current(dropped);
        return true;
      },
    },
    onUpdate: ({ editor: current }) => onEditorUpdateRef.current(current),
  });
  useEffect(() => {
    editorRef.current = editor;
  }, [editor]);

  // ── Files ──

  const addFiles = useCallback((added: File[]) => {
    if (added.length > 0) setFiles((current) => [...current, ...added]);
  }, []);
  useEffect(() => {
    addFilesRef.current = addFiles;
  }, [addFiles]);

  const removeFile = (index: number) => setFiles((current) => current.filter((_, i) => i !== index));

  const onFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    addFiles(Array.from(event.target.files ?? []));
    // The same file can be picked again after it was removed.
    event.target.value = "";
  };

  // ── Edit mode ──

  useEffect(() => {
    editingRef.current = editing;
    if (!editor) return;
    if (editing) {
      if (!draftRef.current) draftRef.current = editor.getJSON();
      loadMessageForEdit(editor, editing, {
        unknownMember: tMessage("unknownMember"),
        unknownTicket: tMessage("unknownTicket"),
      });
      convertEditorChatMessageLinks(editor);
      editor.commands.focus("end");
    } else if (draftRef.current) {
      editor.commands.setContent(draftRef.current, { emitUpdate: false });
      draftRef.current = null;
    }
    setContent(readMarkdown(editor));
    // Only a change of the edited message reloads the editor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor, editing?.id]);

  // A new reply target moves the caret into the composer.
  useEffect(() => {
    if (replyTo && editor) editor.commands.focus("end");
  }, [replyTo, editor]);

  useImperativeHandle(
    ref,
    () => ({
      focus: () => editor?.commands.focus("end"),
      typeText: (text: string) => {
        if (!editor) return;
        editor.commands.focus("end");
        insertPlainText(editor, text);
      },
    }),
    [editor],
  );

  // ── Validation ──

  const issues = useMemo(() => {
    const result = chatComposerSchema.safeParse({ content, files: editing ? [] : files });
    return result.success ? [] : result.error.issues.map((issue) => issue.message);
  }, [content, files, editing]);

  const errorText = (code: string): string | null => {
    switch (code) {
      case "chat.composer.tooLong":
        return t("tooLong", { max: CHAT_MESSAGE_MAX_LENGTH });
      case "chat.composer.tooManyMentions":
        return t("tooManyMentions", { max: CHAT_MAX_USER_MENTIONS });
      case "chat.composer.tooManyTicketRefs":
        return t("tooManyTicketRefs", { max: CHAT_MAX_TICKET_REFS });
      case "chat.composer.tooManyFiles":
        return t("tooManyFiles", { max: CHAT_MAX_FILES });
      case "chat.composer.filesTooLarge":
        return t("filesTooLarge", { max: formatChatFileSize(CHAT_MAX_REQUEST_BYTES) });
      default:
        // An empty composer only disables sending; it is not an error to show.
        return null;
    }
  };
  const visibleErrors = issues.map(errorText).filter((text): text is string => text !== null);
  const editKeepsFiles = !!editing && editing.attachments.length > 0;
  const blank = content.trim().length === 0;
  // An edit names its message; a new message needs the readable open channel, unknown while the channels load.
  const canSubmit = editing
    ? issues.every((code) => code === "chat.composer.empty") && (!blank || editKeepsFiles)
    : issues.length === 0 && !!channelId;

  const tooFast = outbox.some(
    (row) => row.status === "failed" && row.errorCode === CHAT_ERROR_CODES.rateLimited,
  );

  // ── Submit and cancel ──

  const finishEdit = useCallback(() => {
    onEditDone();
  }, [onEditDone]);

  const submit = useCallback(() => {
    if (!editor || !canSubmit) return;
    // Code still written out (typed fast, or pasted as HTML) is sent as code.
    convertViewLiteralCode(editor.view);
    const markdown = readMarkdown(editor);
    if (editing) {
      if (markdown === editing.content) {
        finishEdit();
        return;
      }
      editMessage.mutate({ messageId: editing.id, content: markdown }, { onSuccess: finishEdit });
      return;
    }
    if (!channelId) return;
    send({ content: markdown, channelId, threadId, replyToMessageId: replyTo?.id ?? null, files });
    editor.commands.clearContent(true);
    setFiles([]);
    lastTypingAtRef.current = 0;
    if (replyTo) onCancelReply();
  }, [editor, canSubmit, editing, editMessage, finishEdit, send, channelId, threadId, replyTo, files, onCancelReply]);

  const escape = useCallback((): boolean => {
    if (editing) {
      finishEdit();
      return true;
    }
    if (replyTo) {
      onCancelReply();
      return true;
    }
    return onIdleEscape?.(editor?.isEmpty ?? true) ?? false;
  }, [editing, replyTo, finishEdit, onCancelReply, onIdleEscape, editor]);

  const editLast = useCallback((): boolean => {
    if (editing) return false;
    return onEditLast?.() ?? false;
  }, [editing, onEditLast]);

  useEffect(() => {
    keyHandlersRef.current = { onSubmit: submit, onEscape: escape, onEditLast: editLast };
  }, [submit, escape, editLast]);

  // ── Drag and drop onto the whole composer ──

  const dragHasFiles = (event: DragEvent) => Array.from(event.dataTransfer.types).includes("Files");
  const onDragEnter = (event: DragEvent) => {
    if (editing || !dragHasFiles(event)) return;
    event.preventDefault();
    setDragDepth((depth) => depth + 1);
  };
  const onDragOver = (event: DragEvent) => {
    if (editing || !dragHasFiles(event)) return;
    event.preventDefault();
  };
  const onDragLeave = (event: DragEvent) => {
    if (editing || !dragHasFiles(event)) return;
    setDragDepth((depth) => Math.max(0, depth - 1));
  };
  const onDrop = (event: DragEvent) => {
    setDragDepth(0);
    // A drop on the text itself was taken by the editor already.
    if (editing || event.defaultPrevented) return;
    const dropped = Array.from(event.dataTransfer.files);
    if (dropped.length === 0) return;
    event.preventDefault();
    addFiles(dropped);
  };

  const insertEmoji = (emoji: string) => {
    editor?.chain().focus().insertContent({ type: "text", text: emoji }).run();
  };

  const replyName = replyTo?.author
    ? getDisplayName({
        firstName: replyTo.author.firstName ?? undefined,
        lastName: replyTo.author.lastName ?? undefined,
        email: replyTo.author.email ?? undefined,
      })
    : tMessage("unknownMember");

  const showCounter = content.length >= CHAT_MESSAGE_COUNTER_FROM;

  return (
    <div className="relative">
      {suggestion && (
        <div className="border-border bg-popover text-popover-foreground absolute right-0 bottom-full left-0 z-20 mb-2 max-h-72 overflow-y-auto rounded-lg border shadow-md">
          {suggestion.kind === "@" ? (
            <ChatMentionList ref={suggestionListRef} query={suggestion.query} onSelect={suggestion.command} />
          ) : (
            <ChatTicketSuggestionList ref={suggestionListRef} query={suggestion.query} onSelect={suggestion.command} />
          )}
        </div>
      )}

      {tooFast && (
        <p role="alert" className="text-destructive mb-1.5 text-xs font-medium">
          {t("tooFast")}
        </p>
      )}

      <div
        className={cn(
          "border-border bg-muted/30 relative flex flex-col rounded-none border transition-colors",
          dragDepth > 0 && "border-primary",
          visibleErrors.length > 0 && "border-destructive",
        )}
        onDragEnter={onDragEnter}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
      >
        {editing ? (
          <ComposerBar
            icon={<Pencil className="size-3.5" aria-hidden />}
            text={t("editing")}
            hint={t("editHint")}
            cancelLabel={t("cancelEdit")}
            onCancel={finishEdit}
          />
        ) : (
          replyTo && <ComposerReplyBar replyTo={replyTo} authorName={replyName} onCancel={onCancelReply} />
        )}

        {!editing && <ChatComposerFiles files={files} onRemove={removeFile} />}

        <div className="flex items-end gap-1 px-1.5">
          {!editing && (
            <>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                className="hidden"
                onChange={onFileInputChange}
                tabIndex={-1}
                aria-hidden
              />
              <ComposerIconButton label={t("attach")} onClick={() => fileInputRef.current?.click()}>
                <Paperclip className="size-4" aria-hidden />
              </ComposerIconButton>
            </>
          )}
          <EditorContent editor={editor} className="min-w-0 flex-1 px-1.5" />
          <ChatEmojiPicker
            onSelect={insertEmoji}
            onCloseAutoFocus={(event) => {
              // The caret goes back to the text, not to the emoji button.
              event.preventDefault();
              editor?.commands.focus();
            }}
          >
            <ComposerIconButton label={t("emoji")}>
              <Smile className="size-4" aria-hidden />
            </ComposerIconButton>
          </ChatEmojiPicker>
          <ComposerIconButton
            label={editing ? t("save") : t("send")}
            onClick={submit}
            disabled={!canSubmit}
            className="text-primary"
          >
            <SendHorizontal className="size-4" aria-hidden />
          </ComposerIconButton>
        </div>

        {dragDepth > 0 && (
          <div className="bg-background/90 text-primary pointer-events-none absolute inset-0 flex items-center justify-center gap-2 rounded-none text-sm font-medium">
            <Upload className="size-4" aria-hidden />
            {t("dropFiles")}
          </div>
        )}
      </div>

      {(visibleErrors.length > 0 || showCounter) && (
        <div className="mt-1 flex items-start justify-between gap-3 text-xs">
          <div role="alert" className="text-destructive flex flex-col">
            {visibleErrors.map((text) => (
              <span key={text}>{text}</span>
            ))}
          </div>
          {showCounter && (
            <span
              className={cn(
                "shrink-0 tabular-nums",
                content.length > CHAT_MESSAGE_MAX_LENGTH ? "text-destructive font-medium" : "text-muted-foreground",
              )}
              aria-live="polite"
            >
              {t("counter", { count: content.length, max: CHAT_MESSAGE_MAX_LENGTH })}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

interface ComposerBarProps {
  icon: ReactNode;
  text: ReactNode;
  hint?: string;
  cancelLabel: string;
  onCancel: () => void;
}

/** The bar above the text for a reply or an edit, with its cancel button (Escape does the same). */
function ComposerBar({ icon, text, hint, cancelLabel, onCancel }: ComposerBarProps) {
  return (
    <div className="border-border text-muted-foreground flex items-center gap-2 border-b px-3 py-1.5 text-xs">
      {icon}
      <span className="min-w-0 flex-1 truncate">
        {text}
        {hint && <span className="ml-2">{hint}</span>}
      </span>
      <button
        type="button"
        onClick={onCancel}
        aria-label={cancelLabel}
        title={cancelLabel}
        className="hover:bg-muted hover:text-foreground focus-visible:ring-ring rounded p-0.5 focus-visible:ring-2 focus-visible:outline-none"
      >
        <X className="size-3.5" aria-hidden />
      </button>
    </div>
  );
}

interface ComposerReplyBarProps {
  replyTo: ChatMessageResponse;
  authorName: string;
  onCancel: () => void;
}

/**
 * The bar above the text while replying: a line saying whom the reply goes to,
 * with the cancel button (Escape does the same), over the quote of the message
 * answered, its whole text clamped to two lines. The avatar and name render as
 * one unit where the translated sentence puts the name, so they stay together
 * in every language. The quote leaves out the author's name, which the line
 * above already shows.
 */
function ComposerReplyBar({ replyTo, authorName, onCancel }: ComposerReplyBarProps) {
  const t = useTranslations("chat");
  const text = chatQuoteText(
    chatMessagePlainText(replyTo, t("message.unknownMember"), { withFileNames: false }),
    replyTo.attachments,
    { photo: t("reply.photo"), attachment: t("reply.attachmentOnly") },
  );
  const cancelLabel = t("composer.cancelReply");

  return (
    <div
      role="group"
      aria-label={t("composer.replyingToLabel", { name: authorName })}
      className="border-border flex flex-col gap-2 border-b px-3 py-1.5"
    >
      <div className="text-muted-foreground flex items-center gap-2 text-xs">
        {/* A flex row, not a truncated line: an ellipsis on the line would hide
            the whole avatar and name unit once it overflows at the end of the
            sentence. Here the translated words keep their width and the unit
            shrinks, so a long name ends in its own ellipsis wherever the
            language puts it. */}
        <span className="flex min-w-0 flex-1 items-center gap-1 overflow-hidden whitespace-nowrap">
          {t.rich("composer.replyingTo", {
            name: authorName,
            user: () => (
              <UserAvatar
                user={{
                  firstName: replyTo.author?.firstName ?? undefined,
                  lastName: replyTo.author?.lastName ?? undefined,
                  email: replyTo.author?.email ?? undefined,
                }}
                size="xs"
                name={authorName}
                nameClassName="font-semibold"
              />
            ),
          })}
        </span>
        <button
          type="button"
          onClick={onCancel}
          aria-label={cancelLabel}
          title={cancelLabel}
          className="hover:bg-muted hover:text-foreground focus-visible:ring-ring rounded p-0.5 focus-visible:ring-2 focus-visible:outline-none"
        >
          <X className="size-3.5" aria-hidden />
        </button>
      </div>
      <ChatQuote text={text} image={chatQuoteImage(replyTo.attachments)} />
    </div>
  );
}

interface ComposerIconButtonProps extends ComponentPropsWithoutRef<"button"> {
  label: string;
}

function ComposerIconButton({ label, className, children, ...props }: ComposerIconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={cn(
        "text-muted-foreground hover:text-foreground hover:bg-muted focus-visible:ring-ring mb-1 inline-flex size-8 shrink-0 items-center justify-center rounded-md transition-colors focus-visible:ring-2 focus-visible:outline-none disabled:pointer-events-none disabled:opacity-40",
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}

// ── Typing line ─────────────────────────────────────────────────────

/**
 * "Name is typing…" above the composer. Its height is always reserved, so
 * the composer does not jump when someone starts or stops typing.
 */
export function ChatTypingLine({ userIds }: { userIds: readonly string[] }) {
  const t = useTranslations("chat.typing");
  const { data: members } = useChatMembers();

  const names = useMemo(() => {
    const byId = new Map((members ?? []).map((member) => [member.userId, member]));
    return userIds.map((id) => {
      const member = byId.get(id);
      return member
        ? getDisplayName({
            firstName: member.firstName ?? undefined,
            lastName: member.lastName ?? undefined,
            email: member.email ?? undefined,
          }) || t("someone")
        : t("someone");
    });
  }, [members, userIds, t]);

  let text = "";
  if (names.length === 1) text = t("one", { name: names[0] ?? "" });
  else if (names.length === 2) text = t("two", { first: names[0] ?? "", second: names[1] ?? "" });
  else if (names.length > 2) text = t("several");

  return (
    <p className="text-muted-foreground h-6 truncate pt-1.5 text-xs" aria-live="polite">
      {text && (
        <>
          <span className="mr-1.5 inline-flex gap-0.5 align-middle" aria-hidden>
            <span className="bg-muted-foreground size-1 animate-bounce rounded-full [animation-delay:-0.3s]" />
            <span className="bg-muted-foreground size-1 animate-bounce rounded-full [animation-delay:-0.15s]" />
            <span className="bg-muted-foreground size-1 animate-bounce rounded-full" />
          </span>
          <span className="font-medium">{text}</span>
        </>
      )}
    </p>
  );
}
