"use client";

import {
  createContext,
  Fragment,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type MouseEvent,
  type PointerEvent,
  type ReactNode,
  type RefObject,
  type WheelEvent,
} from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  Copy,
  Ellipsis,
  Link2,
  MessageSquarePlus,
  MessagesSquare,
  Pencil,
  Pin,
  PinOff,
  Reply,
  SmilePlus,
  Trash2,
  type LucideIcon,
} from "lucide-react";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuGroup,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from "@oba/ui/components/context-menu";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@oba/ui/components/sheet";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useChatChannels } from "../hooks/use-chat-channels";
import { useChatCopy } from "../hooks/use-chat-copy";
import { useChatMeId } from "../hooks/use-chat-messages";
import { useDeleteChatMessage, usePinChatMessage } from "../hooks/use-chat-mutations";
import { serializeChatParams } from "../hooks/use-chat-params";
import { chatVibrate, useSwipeToReply } from "../hooks/use-swipe-to-reply";
import { chatEntrySummary, chatMessageText, isChatEntryCard } from "../lib/chat-message-text";
import { chatChannelPath } from "../lib/chat-route";
import { CHAT_MESSAGE_SHORTCUTS, findChatShortcut, type ChatMessageShortcutId } from "../lib/chat-shortcuts";
import type { ChatChannelResponse, ChatMessageResponse } from "../schemas";
import { useChatCanWrite, useChatRole } from "./chat-composer";
import { ChatDeleteMessageDialog } from "./chat-delete-message-dialog";
import { ChatEmojiPicker } from "./chat-emoji-picker";
import { useChatReact } from "./chat-reactions";

/** How long a finger rests on a message before its actions open. */
const LONG_PRESS_MS = 500;
/** A finger that moves farther than this scrolls or swipes; it is not a long press. */
const LONG_PRESS_SLOP_PX = 10;
/** A `contextmenu` or `dblclick` event this soon after a touch comes from that touch, not from a mouse or the keyboard. */
const TOUCH_CONTEXT_MENU_MS = 1000;

/** The attribute on every message row that has actions; keyboard navigation moves between these rows. */
export const CHAT_MESSAGE_ROW_ATTRIBUTE = "data-chat-message";

/**
 * The attribute on the parts of a message the reader reads or uses: the
 * header, the bubble, the reply quote, the files, the footer and the toolbar.
 * The rest of the row is margin, where a double-click replies; on a part a
 * double-click keeps selecting a word.
 */
export const CHAT_MESSAGE_PART_ATTRIBUTE = "data-chat-part";

// ── Actions ─────────────────────────────────────────────────────────

/** Every action on a message, in the order every menu lists them. */
export type ChatMessageActionId =
  | "reply"
  | "react"
  | "thread"
  | "pin"
  | "copyText"
  | "copyLink"
  | "edit"
  | "delete";

const ACTION_ORDER: readonly ChatMessageActionId[] = [
  "reply",
  "react",
  "thread",
  "pin",
  "copyText",
  "copyLink",
  "edit",
  "delete",
];

/** The actions that move focus elsewhere (the composer, a picker, a panel, a dialog). */
const FOCUS_MOVING_ACTIONS: ReadonlySet<ChatMessageActionId> = new Set(["reply", "react", "thread", "edit", "delete"]);

interface ChatActionRights {
  meId: string | undefined;
  /** Not a GUEST: may send, react, pin and start threads. */
  canWrite: boolean;
  /** OWNER or ADMIN: may pin, unpin and delete any message. */
  canModerate: boolean;
  /** The conversation can open threads: the channel, not a thread panel. */
  threads: boolean;
  /** The conversation is a closed thread: read-only, so no reply and no edit. */
  closed: boolean;
}

/**
 * The actions the reader may take on a message, in menu order, following
 * the server's rules: writing needs a role above GUEST; a thread opens from
 * any message that has one and starts only on a channel message; pins are
 * for the author and the organization's OWNERs and ADMINs; edit only on the
 * reader's own message, delete on it and, for an OWNER or ADMIN, on anyone's,
 * so they can moderate; neither on a daily update or a shared pull request,
 * which are changed through their own screens. A closed thread takes no reply and no
 * edit; reactions, pins and deleting stay.
 */
function allowedActions(message: ChatMessageResponse, rights: ChatActionRights): ChatMessageActionId[] {
  if (message.deleted) return [];
  const own = !!rights.meId && message.author?.userId === rights.meId;
  const summary = chatEntrySummary(message);
  const hasText = summary !== null ? !!summary.trim() : message.content.trim().length > 0;
  const chatEditable = !isChatEntryCard(message);
  const allowed: Record<ChatMessageActionId, boolean> = {
    reply: rights.canWrite && !rights.closed,
    react: rights.canWrite,
    thread: rights.threads && (!!message.thread || (!message.threadId && rights.canWrite)),
    pin: rights.canWrite && (own || rights.canModerate),
    copyText: hasText,
    copyLink: true,
    edit: rights.canWrite && !rights.closed && own && chatEditable,
    delete: rights.canWrite && (own || rights.canModerate) && chatEditable,
  };
  return ACTION_ORDER.filter((id) => allowed[id]);
}

const ACTION_ICONS: Record<ChatMessageActionId, LucideIcon> = {
  reply: Reply,
  react: SmilePlus,
  thread: MessageSquarePlus,
  pin: Pin,
  copyText: Copy,
  copyLink: Link2,
  edit: Pencil,
  delete: Trash2,
};

function actionIcon(id: ChatMessageActionId, message: ChatMessageResponse): LucideIcon {
  if (id === "thread" && message.thread) return MessagesSquare;
  if (id === "pin" && message.pinnedAt) return PinOff;
  return ACTION_ICONS[id];
}

type ActionLabelKey =
  | "reply"
  | "react"
  | "createThread"
  | "openThread"
  | "pin"
  | "unpin"
  | "copyText"
  | "copyLink"
  | "edit"
  | "delete";

function actionLabelKey(id: ChatMessageActionId, message: ChatMessageResponse): ActionLabelKey {
  if (id === "thread") return message.thread ? "openThread" : "createThread";
  if (id === "pin") return message.pinnedAt ? "unpin" : "pin";
  return id;
}

/**
 * The link that opens the chat at a message: its channel's path
 * (`/chats/<slug>`, `/daily-updates`, `/pull-requests`) with `jump`, or with
 * `thread` and `threadMessage` for a message of a thread. The path names the
 * channel, so the link opens there from anywhere.
 */
function chatMessageLink(message: ChatMessageResponse, channel: ChatChannelResponse): string {
  const base = `${window.location.origin}${chatChannelPath(channel)}`;
  return message.threadId
    ? serializeChatParams(base, { thread: message.threadId, threadMessage: message.id })
    : serializeChatParams(base, { jump: message.id });
}

// ── Provider ────────────────────────────────────────────────────────

export interface ChatMessageActionsConfig {
  /** Starts a reply to the message in this conversation's composer. */
  onReply: (message: ChatMessageResponse) => void;
  /** Starts editing the reader's own message in this conversation's composer. */
  onEdit: (message: ChatMessageResponse) => void;
  /** Opens the message's thread or starts one; omitted in a thread panel, where no thread starts. */
  onOpenThread?: (message: ChatMessageResponse) => void;
  /** Puts the caret back in this conversation's composer (Escape on a focused message). */
  onFocusComposer: () => void;
  /** True in a closed thread, which has no composer to reply or edit in. */
  closed?: boolean;
}

interface ChatMessageActionsContextValue extends ChatActionRights {
  config: RefObject<ChatMessageActionsConfig>;
  /** The row whose margin took the latest first press; null when that press landed anywhere else. */
  marginPressRow: RefObject<Element | null>;
  /** Runs an action that needs no picker; `skipConfirm` deletes without asking. */
  run: (id: Exclude<ChatMessageActionId, "react">, message: ChatMessageResponse, skipConfirm: boolean) => void;
  react: (message: ChatMessageResponse, emoji: string) => void;
}

const ChatMessageActionsContext = createContext<ChatMessageActionsContextValue | null>(null);

/**
 * Gives the messages of one conversation (the channel, or a thread's list)
 * their actions: the hover toolbar with its "…" menu, the right-click menu,
 * the long-press sheet, swipe to reply, the margin double-click and the keys
 * of a focused message. Messages outside a provider (a thread's root above
 * its panel) have none.
 */
export function ChatMessageActionsProvider({
  children,
  ...config
}: ChatMessageActionsConfig & { children: ReactNode }) {
  const tMessage = useTranslations("chat.message");
  const meId = useChatMeId();
  const role = useChatRole();
  const canWrite = useChatCanWrite();
  const { mutate: pin } = usePinChatMessage();
  const { mutate: deleteMessage } = useDeleteChatMessage();
  const react = useChatReact();
  const copy = useChatCopy();
  const tCopy = useTranslations("chat.copy");
  // Read when a link is copied, so a refetched list never re-renders every message.
  const { data: channels } = useChatChannels();
  const channelsRef = useRef(channels);
  useLayoutEffect(() => {
    channelsRef.current = channels;
  });
  const [confirmingDelete, setConfirmingDelete] = useState<ChatMessageResponse | null>(null);

  // The handlers are read when an action runs, so new handler objects never re-render every message.
  const configRef = useRef<ChatMessageActionsConfig>(config);
  useLayoutEffect(() => {
    configRef.current = config;
  });

  // Read on the whole document, so a first press outside every row replaces an older one on a row's margin.
  const marginPressRowRef = useRef<Element | null>(null);
  useEffect(() => {
    const onPress = (event: globalThis.MouseEvent) => {
      if (event.detail === 1) marginPressRowRef.current = marginRow(event.target);
    };
    document.addEventListener("mousedown", onPress, true);
    return () => document.removeEventListener("mousedown", onPress, true);
  }, []);

  const unknownMember = tMessage("unknownMember");
  const threads = !!config.onOpenThread;
  const closed = !!config.closed;
  const canModerate = role === "OWNER" || role === "ADMIN";

  const run = useCallback<ChatMessageActionsContextValue["run"]>(
    (id, message, skipConfirm) => {
      const handlers = configRef.current;
      switch (id) {
        case "reply":
          handlers.onReply(message);
          return;
        case "thread":
          handlers.onOpenThread?.(message);
          return;
        case "pin":
          pin({ messageId: message.id, pinned: !message.pinnedAt });
          return;
        case "copyText":
          void copy(chatMessageText(message, unknownMember), "text");
          return;
        case "copyLink": {
          // The message is shown in an open channel, so the cached list holds it.
          const channel = channelsRef.current?.find((candidate) => candidate.id === message.channelId);
          if (!channel) {
            console.error("[chat] copy link: the message's channel is not in the channel list", message.channelId);
            toast.error(tCopy("error"));
            return;
          }
          void copy(chatMessageLink(message, channel), "link");
          return;
        }
        case "edit":
          handlers.onEdit(message);
          return;
        case "delete":
          // A failed delete is toasted by the mutation.
          if (skipConfirm) deleteMessage({ messageId: message.id, threadId: message.threadId });
          else setConfirmingDelete(message);
          return;
      }
    },
    [pin, deleteMessage, copy, tCopy, unknownMember],
  );

  const value = useMemo<ChatMessageActionsContextValue>(
    () => ({
      config: configRef,
      marginPressRow: marginPressRowRef,
      meId,
      canWrite,
      canModerate,
      threads,
      closed,
      run,
      react,
    }),
    [meId, canWrite, canModerate, threads, closed, run, react],
  );

  return (
    <ChatMessageActionsContext.Provider value={value}>
      {children}
      <ChatDeleteMessageDialog message={confirmingDelete} onClose={() => setConfirmingDelete(null)} />
    </ChatMessageActionsContext.Provider>
  );
}


// ── Row ─────────────────────────────────────────────────────────────

/** What a message row spreads on its element: focus, keys, swipe, long press and the margin double-click. */
export interface ChatMessageRowProps {
  ref: RefObject<HTMLDivElement | null>;
  tabIndex: number;
  onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void;
  onPointerDown: (event: PointerEvent<HTMLDivElement>) => void;
  onPointerMove: (event: PointerEvent<HTMLDivElement>) => void;
  onPointerUp: (event: PointerEvent<HTMLDivElement>) => void;
  onPointerCancel: (event: PointerEvent<HTMLDivElement>) => void;
  onWheel: (event: WheelEvent<HTMLDivElement>) => void;
  onContextMenu: (event: MouseEvent<HTMLDivElement>) => void;
  onClickCapture: (event: MouseEvent<HTMLDivElement>) => void;
  onMouseDown: (event: MouseEvent<HTMLDivElement>) => void;
  onDoubleClick: (event: MouseEvent<HTMLDivElement>) => void;
}

/** Where an action was chosen; it decides where focus goes afterwards. */
type ActionSource = "toolbar" | "menu" | "sheet" | "key";

export interface ChatMessageRowController {
  message: ChatMessageResponse;
  /** The reader's actions on the message, in menu order; never empty. */
  actions: readonly ChatMessageActionId[];
  rowProps: ChatMessageRowProps;
  /** Swipe to reply is on (the reader may reply). */
  swipe: boolean;
  pickerOpen: boolean;
  onPickerOpenChange: (open: boolean) => void;
  onPickerCloseAutoFocus: (event: Event) => void;
  sheetOpen: boolean;
  onSheetOpenChange: (open: boolean) => void;
  onSheetCloseAutoFocus: (event: Event) => void;
  choose: (id: ChatMessageActionId, source: ActionSource, shiftKey: boolean) => void;
  onMenuCloseAutoFocus: (event: Event) => void;
  react: (emoji: string) => void;
}

const SHORTCUT_ACTIONS: Partial<Record<ChatMessageShortcutId, ChatMessageActionId>> = {
  reply: "reply",
  edit: "edit",
  thread: "thread",
  pin: "pin",
  react: "react",
  copy: "copyText",
  delete: "delete",
};

/** A horizontal scroller inside a message (a code block) keeps horizontal moves for itself. */
function inHorizontalScroller(target: EventTarget): boolean {
  return target instanceof Element && !!target.closest("pre");
}

/** Where a double-click keeps its usual meaning: the message's parts and everything clickable or editable. */
const NOT_MARGIN_SELECTOR = [
  `[${CHAT_MESSAGE_PART_ATTRIBUTE}]`,
  "a",
  "button",
  "input",
  "textarea",
  "select",
  "label",
  "[role=button]",
  "[role=toolbar]",
  "[contenteditable]",
].join(",");

/** The message row whose empty space a mouse event landed on; null anywhere else. */
function marginRow(target: EventTarget | null): Element | null {
  if (!(target instanceof Element) || target.closest(NOT_MARGIN_SELECTOR)) return null;
  return target.closest(`[${CHAT_MESSAGE_ROW_ATTRIBUTE}]`);
}

/**
 * Whether a mouse event landed on the row's empty space. React also bubbles
 * events out of the row's portals (the emoji picker, the toolbar's menu, a
 * card's dialog), which sit elsewhere in the page and are not its margin.
 */
function isRowMargin({ target, currentTarget }: MouseEvent<HTMLDivElement>): boolean {
  return marginRow(target) === currentTarget;
}

/**
 * The interaction of one message row inside a `ChatMessageActionsProvider`;
 * null outside one, for a deleted message, and when the reader may do
 * nothing with it.
 */
export function useChatMessageRow(message: ChatMessageResponse): ChatMessageRowController | null {
  const context = useContext(ChatMessageActionsContext);
  const rowRef = useRef<HTMLDivElement | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  /** The action whose menu is closing, so the menu does not pull focus back from where the action moved it. */
  const chosenRef = useRef<ChatMessageActionId | null>(null);
  /** "Add reaction" from a menu: the picker opens once the menu has closed and released focus. */
  const pickerAfterCloseRef = useRef(false);
  /** The picker was opened from the row (a key or a menu), so focus returns to the row, not the toolbar. */
  const pickerReturnsToRowRef = useRef(false);
  const pressRef = useRef<{ pointerId: number; x: number; y: number; timer: number } | null>(null);
  const lastTouchAtRef = useRef(0);
  const suppressClickRef = useRef(false);

  const actions = useMemo(
    () =>
      context
        ? allowedActions(message, {
            meId: context.meId,
            canWrite: context.canWrite,
            canModerate: context.canModerate,
            threads: context.threads,
            closed: context.closed,
          })
        : [],
    [context, message],
  );
  const canReply = actions.includes("reply");

  const run = context?.run;
  const reply = useCallback(() => run?.("reply", message, false), [run, message]);
  const swipe = useSwipeToReply({ rowRef, enabled: canReply, onReply: reply });

  const cancelPress = useCallback(() => {
    if (pressRef.current) window.clearTimeout(pressRef.current.timer);
    pressRef.current = null;
  }, []);
  useEffect(() => cancelPress, [cancelPress]);

  const choose = useCallback(
    (id: ChatMessageActionId, source: ActionSource, shiftKey: boolean) => {
      if (!run) return;
      if (source === "menu" || source === "sheet") chosenRef.current = id;
      if (id === "react") {
        if (source === "menu" || source === "sheet") {
          pickerAfterCloseRef.current = true;
        } else {
          pickerReturnsToRowRef.current = source === "key";
          setPickerOpen(true);
        }
        return;
      }
      run(id, message, shiftKey);
    },
    [run, message],
  );

  const openPickerAfterClose = useCallback(() => {
    if (!pickerAfterCloseRef.current) return;
    pickerAfterCloseRef.current = false;
    pickerReturnsToRowRef.current = true;
    setPickerOpen(true);
  }, []);

  const onMenuCloseAutoFocus = useCallback(
    (event: Event) => {
      const chosen = chosenRef.current;
      chosenRef.current = null;
      if (chosen && FOCUS_MOVING_ACTIONS.has(chosen)) event.preventDefault();
      openPickerAfterClose();
    },
    [openPickerAfterClose],
  );

  // On touch the sheet's actions are tapped; focus has nowhere useful to return.
  const onSheetCloseAutoFocus = useCallback(
    (event: Event) => {
      event.preventDefault();
      chosenRef.current = null;
      openPickerAfterClose();
    },
    [openPickerAfterClose],
  );

  const onPickerCloseAutoFocus = useCallback((event: Event) => {
    if (!pickerReturnsToRowRef.current) return;
    pickerReturnsToRowRef.current = false;
    event.preventDefault();
    rowRef.current?.focus({ preventScroll: true });
  }, []);

  const onPickerOpenChange = useCallback((open: boolean) => setPickerOpen(open), []);
  const onSheetOpenChange = useCallback((open: boolean) => setSheetOpen(open), []);
  const react = useCallback((emoji: string) => context?.react(message, emoji), [context, message]);

  const rowProps = useMemo<ChatMessageRowProps>(
    () => ({
      ref: rowRef,
      tabIndex: -1,
      onKeyDown: (event) => {
        // Keys on the message itself, reached by keyboard; its buttons and links keep theirs, and a
        // message focused by a click lets typed letters go to the composer.
        if (event.target !== event.currentTarget || !context || !event.currentTarget.matches(":focus-visible")) return;
        const shortcut = findChatShortcut(event.nativeEvent, CHAT_MESSAGE_SHORTCUTS);
        if (!shortcut) return;
        // Taken even when not allowed, so the key does nothing rather than typing into the composer.
        event.preventDefault();
        if (shortcut === "backToComposer") {
          context.config.current.onFocusComposer();
          return;
        }
        if (shortcut === "openThread") {
          if (message.thread && actions.includes("thread")) choose("thread", "key", false);
          return;
        }
        const action = SHORTCUT_ACTIONS[shortcut];
        if (action && actions.includes(action)) choose(action, "key", event.shiftKey);
      },
      onPointerDown: (event) => {
        if (event.pointerType === "mouse" || !event.isPrimary) return;
        lastTouchAtRef.current = event.timeStamp;
        suppressClickRef.current = false;
        // The menu primitive opens its own menu on a long touch; on touch the sheet opens instead.
        event.preventDefault();
        if (inHorizontalScroller(event.target)) return;
        swipe.onPointerDown(event);
        cancelPress();
        const { pointerId, clientX, clientY } = event;
        pressRef.current = {
          pointerId,
          x: clientX,
          y: clientY,
          timer: window.setTimeout(() => {
            pressRef.current = null;
            // The finger lifts after the sheet opened; that tap is not a click on the message.
            suppressClickRef.current = true;
            chatVibrate();
            setSheetOpen(true);
          }, LONG_PRESS_MS),
        };
      },
      onPointerMove: (event) => {
        swipe.onPointerMove(event);
        const press = pressRef.current;
        if (
          press &&
          press.pointerId === event.pointerId &&
          Math.hypot(event.clientX - press.x, event.clientY - press.y) > LONG_PRESS_SLOP_PX
        ) {
          cancelPress();
        }
      },
      onPointerUp: (event) => {
        swipe.onPointerUp(event);
        cancelPress();
      },
      onPointerCancel: (event) => {
        swipe.onPointerCancel(event);
        cancelPress();
      },
      onWheel: (event) => {
        if (!inHorizontalScroller(event.target)) swipe.onWheel(event);
      },
      onContextMenu: (event) => {
        // A long touch also fires `contextmenu` on Android: the sheet answers it, not the browser or the menu.
        if (event.timeStamp - lastTouchAtRef.current < TOUCH_CONTEXT_MENU_MS) event.preventDefault();
      },
      onClickCapture: (event) => {
        if (!suppressClickRef.current) return;
        suppressClickRef.current = false;
        event.preventDefault();
        event.stopPropagation();
      },
      onMouseDown: (event) => {
        // The second press of a double-click would select the nearest word. Only that press is
        // stopped, so a drag that starts in the margin still selects text.
        if (event.detail >= 2 && isRowMargin(event)) event.preventDefault();
      },
      onDoubleClick: (event) => {
        // Touch replies by swipe only, so a double-tap does nothing.
        if (!canReply || event.timeStamp - lastTouchAtRef.current < TOUCH_CONTEXT_MENU_MS) return;
        // The first press can move the list (a reply quote jumps to its original), which puts
        // another row under the second press; that row was not the one double-clicked.
        if (isRowMargin(event) && context?.marginPressRow.current === event.currentTarget) reply();
      },
    }),
    [context, message, actions, canReply, reply, choose, swipe, cancelPress],
  );

  if (!context || actions.length === 0) return null;
  return {
    message,
    actions,
    rowProps,
    swipe: canReply,
    pickerOpen,
    onPickerOpenChange,
    onPickerCloseAutoFocus,
    sheetOpen,
    onSheetOpenChange,
    onSheetCloseAutoFocus,
    choose,
    onMenuCloseAutoFocus,
    react,
  };
}

// ── Toolbar ─────────────────────────────────────────────────────────

function useActionLabel(message: ChatMessageResponse): (id: ChatMessageActionId) => string {
  const t = useTranslations("chat.actions");
  return useCallback((id: ChatMessageActionId) => t(actionLabelKey(id, message)), [t, message]);
}

const toolbarButtonClass = "text-muted-foreground hover:text-foreground size-7";

/**
 * The hover toolbar of a message: Add reaction, Reply and Create or Open
 * thread where allowed, and "…" with every allowed action. It shows while
 * the row is hovered or focused, and stays while its picker or menu is open.
 */
export function ChatMessageToolbar({ row }: { row: ChatMessageRowController }) {
  const t = useTranslations("chat.actions");
  const label = useActionLabel(row.message);
  const { message, actions } = row;

  const quick = (["reply", "thread"] as const).filter((id) => actions.includes(id));

  return (
    <TooltipProvider delayDuration={300}>
      <div
        role="toolbar"
        aria-label={t("toolbar")}
        className="bg-background border-border flex items-center gap-0.5 rounded-md border p-0.5 shadow-sm"
      >
        {actions.includes("react") && (
          <Tooltip>
            <ChatEmojiPicker
              onSelect={row.react}
              open={row.pickerOpen}
              onOpenChange={row.onPickerOpenChange}
              onCloseAutoFocus={row.onPickerCloseAutoFocus}
              align="end"
              side="top"
            >
              <TooltipTrigger asChild>
                <Button variant="ghost" size="icon-sm" className={toolbarButtonClass} aria-label={label("react")}>
                  <SmilePlus />
                </Button>
              </TooltipTrigger>
            </ChatEmojiPicker>
            <TooltipContent>
              <p>{label("react")}</p>
            </TooltipContent>
          </Tooltip>
        )}
        {quick.map((id) => {
          const Icon = actionIcon(id, message);
          return (
            <Tooltip key={id}>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className={toolbarButtonClass}
                  aria-label={label(id)}
                  onClick={(event) => row.choose(id, "toolbar", event.shiftKey)}
                >
                  <Icon />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>{label(id)}</p>
              </TooltipContent>
            </Tooltip>
          );
        })}
        <Tooltip>
          <DropdownMenu modal={false}>
            <DropdownMenuTrigger asChild>
              <TooltipTrigger asChild>
                <Button variant="ghost" size="icon-sm" className={toolbarButtonClass} aria-label={t("more")}>
                  <Ellipsis />
                </Button>
              </TooltipTrigger>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52" onCloseAutoFocus={row.onMenuCloseAutoFocus}>
              {actionGroups(actions).map((ids, groupIndex) => (
                <Fragment key={ids.join("-")}>
                  {groupIndex > 0 && <DropdownMenuSeparator />}
                  <DropdownMenuGroup>
                    {ids.map((id) => {
                      const Icon = actionIcon(id, message);
                      return (
                        <DropdownMenuItem
                          key={id}
                          // `onClick` rather than `onSelect`: a Shift-click deletes without asking.
                          onClick={(event) => row.choose(id, "menu", event.shiftKey)}
                          className={cn(id === "delete" && "text-destructive focus:text-destructive")}
                        >
                          <Icon />
                          {label(id)}
                        </DropdownMenuItem>
                      );
                    })}
                  </DropdownMenuGroup>
                </Fragment>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
          <TooltipContent>
            <p>{t("more")}</p>
          </TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );
}

/** The menu's sections: conversation actions, then copying, then editing and deleting. */
const ACTION_GROUPS: readonly (readonly ChatMessageActionId[])[] = [
  ["reply", "react", "thread", "pin"],
  ["copyText", "copyLink"],
  ["edit", "delete"],
];

function actionGroups(actions: readonly ChatMessageActionId[]): ChatMessageActionId[][] {
  return ACTION_GROUPS.map((ids) => ids.filter((id) => actions.includes(id))).filter((ids) => ids.length > 0);
}

// ── Right-click menu and long-press sheet ───────────────────────────

/**
 * Wraps a message row: right-click opens the message's actions as a context
 * menu, a long press on touch opens them as a bottom sheet.
 */
export function ChatMessageMenus({ row, children }: { row: ChatMessageRowController; children: ReactNode }) {
  const t = useTranslations("chat.actions");
  const label = useActionLabel(row.message);
  const { message, actions } = row;

  return (
    <>
      <ContextMenu modal={false}>
        <ContextMenuTrigger asChild>{children}</ContextMenuTrigger>
        <ContextMenuContent className="w-52" onCloseAutoFocus={row.onMenuCloseAutoFocus}>
          {actionGroups(actions).map((ids, groupIndex) => (
            <Fragment key={ids.join("-")}>
              {groupIndex > 0 && <ContextMenuSeparator />}
              <ContextMenuGroup>
                {ids.map((id) => {
                  const Icon = actionIcon(id, message);
                  return (
                    <ContextMenuItem
                      key={id}
                      variant={id === "delete" ? "destructive" : "default"}
                      onClick={(event) => row.choose(id, "menu", event.shiftKey)}
                    >
                      <Icon />
                      {label(id)}
                    </ContextMenuItem>
                  );
                })}
              </ContextMenuGroup>
            </Fragment>
          ))}
        </ContextMenuContent>
      </ContextMenu>
      {row.sheetOpen && (
        <Sheet open onOpenChange={row.onSheetOpenChange}>
          <SheetContent
            side="bottom"
            showCloseButton={false}
            aria-describedby={undefined}
            onCloseAutoFocus={row.onSheetCloseAutoFocus}
            className="gap-0 rounded-t-xl pb-[max(0.75rem,env(safe-area-inset-bottom))]"
          >
            <SheetHeader className="pb-2">
              <SheetTitle className="text-sm">{t("sheetTitle")}</SheetTitle>
            </SheetHeader>
            <div className="flex flex-col px-2">
              {actions.map((id) => {
                const Icon = actionIcon(id, message);
                return (
                  <Button
                    key={id}
                    variant="ghost"
                    className={cn("h-12 justify-start gap-3 px-3 text-base", id === "delete" && "text-destructive hover:text-destructive")}
                    onClick={() => {
                      row.onSheetOpenChange(false);
                      row.choose(id, "sheet", false);
                    }}
                  >
                    <Icon className="size-5" />
                    {label(id)}
                  </Button>
                );
              })}
            </div>
          </SheetContent>
        </Sheet>
      )}
    </>
  );
}
