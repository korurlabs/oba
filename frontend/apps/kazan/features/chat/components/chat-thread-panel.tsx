"use client";

import {
  useCallback,
  useMemo,
  useState,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
  type RefObject,
} from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { format as formatDateFns } from "date-fns";
import { useFormatter, useLocale, useNow, useTranslations } from "next-intl";
import {
  AlertCircle,
  Archive,
  ArchiveRestore,
  Ellipsis,
  Loader2,
  Lock,
  LockOpen,
  LogIn,
  LogOut,
  MessagesSquare,
  Pencil,
  SearchX,
  X,
  type LucideIcon,
} from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { Sheet, SheetContent, SheetTitle } from "@oba/ui/components/sheet";
import { useIsMobile } from "@oba/ui/hooks/use-mobile";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { EmptyState } from "@/components/ui/empty-state";
import { Form, FormField } from "@/components/ui/form";
import { TextField } from "@/components/ui/form-fields";
import { FormFieldWrapper } from "@/components/ui/form-fields/form-field-wrapper";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { getDisplayName, UserAvatarStack, type UserAvatarUser } from "@/components/ui/user-avatar";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { parseDateOnly } from "@oba/shared/lib/date-only";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";
import { updateDayOf } from "@/features/updates/lib/update-day";
import { useChatMeId, useChatThread } from "../hooks/use-chat-messages";
import {
  useChangeChatThreadStatus,
  useCreateChatThread,
  useJoinChatThread,
  useLeaveChatThread,
  useUpdateChatThread,
} from "../hooks/use-chat-mutations";
import { useChatUnreadMarker } from "../hooks/use-mark-read";
import {
  CHAT_AUTO_ARCHIVE_OPTIONS,
  CHAT_DEFAULT_AUTO_ARCHIVE_MINUTES,
  CHAT_ERROR_CODES,
  CHAT_THREAD_TITLE_MAX,
  ChatAutoArchiveMinutesSchema,
  chatThreadTitleFormSchema,
  type ChatAutoArchiveMinutes,
  type ChatMessageResponse,
  type ChatThreadResponse,
  type ChatThreadStatus,
  type ChatThreadTitleFormValues,
  type ChatUserResponse,
} from "../schemas";
import { chatEntrySummary } from "../lib/chat-message-text";
import { CHAT_THREAD_PANEL_ATTRIBUTE } from "../lib/chat-shortcuts";
import { ChatComposer, useChatCanWrite, type ChatComposerHandle } from "./chat-composer";
import { ChatMessage } from "./chat-message";
import { ChatMessageActionsProvider } from "./chat-message-actions";
import { ChatMessageList, type ChatMessageListHandle } from "./chat-message-list";
import { ChatReactions } from "./chat-reactions";
import { chatReplyPreviewOf } from "./chat-reply-preview";
import { ChatThreadStatusChip, chatPastTime } from "./chat-thread-summary";

// ── Default title ───────────────────────────────────────────────────

/** A title derived from the root's text or file name is cut here, as the server does. */
const DEFAULT_TITLE_LENGTH = 40;
const TITLE_ELLIPSIS = "…";
/** Day and short month of a daily update's thread title ("20 Oct"), the server's pattern. */
const DAILY_UPDATE_TITLE_DAY_FORMAT = "d MMM";

function shortenTitle(text: string, length: number): string {
  const characters = Array.from(text);
  return characters.length > length ? `${characters.slice(0, length).join("")}${TITLE_ELLIPSIS}` : text;
}

/**
 * The title a new thread on `message` starts with, mirroring the server's
 * default: the text with mentions and tickets as names, cut at 40
 * characters; for a daily update "Daily update - First Last - 20 Oct"; for a
 * shared pull request the first line of its summary ("KEY: title"), cut the
 * same way; for a message with only files the first file name. The field
 * shows it prefilled and sends what it holds, so the stored title is what
 * the reader saw.
 */
export function chatThreadDefaultTitle(
  message: ChatMessageResponse,
  labels: {
    unknownMember: string;
    fallback: string;
    dailyUpdate: (values: { name: string; day: string }) => string;
  },
  locale: string,
): string {
  if (message.dailyUpdateId != null) {
    const author = message.author;
    const name = [author?.firstName, author?.lastName].filter(Boolean).join(" ").trim() || author?.email || "";
    if (!name) return labels.fallback;
    // The report day in the team's zone, as the server titles the thread, so
    // the panel and the thread list show the same day for every reader.
    // A day key that does not parse is used as it is, so the title still names
    // the day and the reader can correct it in the field.
    const dayKey = updateDayOf(message.createdAt);
    const reportDay = parseDateOnly(dayKey);
    if (!reportDay) console.warn("[chatThreadDefaultTitle] report day is not a YYYY-MM-DD day", dayKey);
    const day = reportDay
      ? formatDateFns(reportDay, DAILY_UPDATE_TITLE_DAY_FORMAT, { locale: getDateFnsLocale(locale) })
      : dayKey;
    return shortenTitle(labels.dailyUpdate({ name, day }), CHAT_THREAD_TITLE_MAX - TITLE_ELLIPSIS.length);
  }
  if (message.pullRequestShareId != null) {
    const firstLine = (chatEntrySummary(message) ?? "").split("\n", 1)[0]?.trim();
    return firstLine ? shortenTitle(firstLine, DEFAULT_TITLE_LENGTH) : labels.fallback;
  }
  const text = chatReplyPreviewOf(message, labels.unknownMember).snippet;
  if (text) return shortenTitle(text, DEFAULT_TITLE_LENGTH);
  const fileName = message.attachments[0]?.fileName;
  if (fileName) return shortenTitle(fileName, DEFAULT_TITLE_LENGTH);
  return labels.fallback;
}

// ── Frame ───────────────────────────────────────────────────────────

/** Width of the thread column on a wide screen, in pixels. */
const PANEL_DEFAULT_WIDTH = 420;
const PANEL_MIN_WIDTH = 320;
const PANEL_MAX_WIDTH = 720;
/** The channel keeps at least this share of the window. */
const PANEL_MAX_WINDOW_SHARE = 0.6;
const PANEL_KEY_STEP = 16;

function clampPanelWidth(width: number): number {
  const windowMax = typeof window === "undefined" ? PANEL_MAX_WIDTH : window.innerWidth * PANEL_MAX_WINDOW_SHARE;
  const max = Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, windowMax));
  return Math.round(Math.min(Math.max(width, PANEL_MIN_WIDTH), max));
}

function PanelResizeHandle({ width, onResize }: { width: number; onResize: (width: number) => void }) {
  const t = useTranslations("chat.thread");

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    const handle = event.currentTarget;
    const startX = event.clientX;
    const startWidth = width;
    handle.setPointerCapture(event.pointerId);
    // The panel is on the right: moving the pointer left widens it.
    const onMove = (move: globalThis.PointerEvent) => onResize(clampPanelWidth(startWidth + startX - move.clientX));
    const onEnd = () => {
      handle.removeEventListener("pointermove", onMove);
      handle.removeEventListener("pointerup", onEnd);
      handle.removeEventListener("pointercancel", onEnd);
    };
    handle.addEventListener("pointermove", onMove);
    handle.addEventListener("pointerup", onEnd);
    handle.addEventListener("pointercancel", onEnd);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "ArrowLeft") onResize(clampPanelWidth(width + PANEL_KEY_STEP));
    else if (event.key === "ArrowRight") onResize(clampPanelWidth(width - PANEL_KEY_STEP));
    else return;
    event.preventDefault();
  };

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={t("resize")}
      aria-valuenow={width}
      aria-valuemin={PANEL_MIN_WIDTH}
      aria-valuemax={PANEL_MAX_WIDTH}
      tabIndex={0}
      onPointerDown={onPointerDown}
      onKeyDown={onKeyDown}
      className="bg-border hover:bg-primary/40 focus-visible:bg-primary/60 hidden w-px shrink-0 cursor-col-resize touch-none outline-none transition-colors after:absolute after:inset-y-0 after:-left-1.5 after:w-3 after:content-[''] md:relative md:block"
    />
  );
}

interface ChatThreadColumnProps {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
}

/**
 * Where the thread panel lives: a resizable column right of the channel on
 * a wide screen, a full-screen sheet over the channel on a phone. Closing
 * the sheet (its Escape, or Back, which drops the `thread` URL parameter)
 * closes the thread.
 */
export function ChatThreadColumn({ open, onClose, children }: ChatThreadColumnProps) {
  const t = useTranslations("chat.thread");
  const isMobile = useIsMobile();
  const [width, setWidth] = useState(PANEL_DEFAULT_WIDTH);

  if (!open) return null;

  if (isMobile) {
    return (
      <Sheet open onOpenChange={(next) => !next && onClose()}>
        <SheetContent
          {...{ [CHAT_THREAD_PANEL_ATTRIBUTE]: "" }}
          side="right"
          showCloseButton={false}
          aria-describedby={undefined}
          className="w-full gap-0 p-0 sm:max-w-none"
        >
          <SheetTitle className="sr-only">{t("panelLabel")}</SheetTitle>
          {children}
        </SheetContent>
      </Sheet>
    );
  }

  return (
    <>
      <PanelResizeHandle width={width} onResize={setWidth} />
      {/* Hidden below md until the phone check has run, so a phone never flashes the column. */}
      <aside
        {...{ [CHAT_THREAD_PANEL_ATTRIBUTE]: "" }}
        aria-label={t("panelLabel")}
        style={{ width }}
        className="bg-card hidden min-h-0 shrink-0 flex-col md:flex"
      >
        {children}
      </aside>
    </>
  );
}

// ── Panel ───────────────────────────────────────────────────────────

export interface ChatThreadPanelProps {
  /** The open thread (the `thread` URL parameter); null while a new thread is drafted. */
  threadId: string | null;
  /** The message a new thread is being started on; shown while `threadId` is null. */
  draftRoot: ChatMessageResponse | null;
  /** Put the caret in the composer when the thread opens, after it was just created. */
  focusComposer: boolean;
  onClose: () => void;
  /** Opens a thread; `focus` puts the caret in its composer. */
  onOpenThread: (threadId: string, options?: { focus?: boolean }) => void;
  /** Shows a message of the channel (a quote in the root message). */
  onJumpToChannelMessage: (messageId: string) => void;
  /** Typing members of the open thread, from the channel's `useTypingUsers`. */
  typingUserIds: readonly string[];
  /** The open thread's messages that arrived while its list was not at its newest page. */
  newMessageCount: number;
  onNewestShown: () => void;
  /** A message of the open thread to show (a search or pin jump); `onJumpConsumed` is called once it is handled. */
  jumpTo?: string | null;
  onJumpConsumed?: () => void;
  /** The open thread's list and composer, for the chat's keys. */
  listRef?: RefObject<ChatMessageListHandle | null>;
  composerRef?: RefObject<ChatComposerHandle | null>;
}

/**
 * The side panel of a thread, Discord style: the header with the title, the
 * members, Join or Leave and the settings of those who may manage it, the
 * root message on top, the thread's messages and its own composer. Before a
 * thread exists it shows the draft: the root and a title field to create it.
 */
export function ChatThreadPanel({ threadId, draftRoot, ...props }: ChatThreadPanelProps) {
  if (threadId) return <OpenThread key={threadId} threadId={threadId} {...props} />;
  if (draftRoot) return <ThreadDraft key={draftRoot.id} root={draftRoot} {...props} />;
  return null;
}

function PanelHeader({
  title,
  onClose,
  children,
  details,
  footer,
}: {
  title: ReactNode;
  onClose: () => void;
  /** Actions next to the close button. */
  children?: ReactNode;
  /** A second line below the title. */
  details?: ReactNode;
  /** A last line across the whole header, below the actions too. */
  footer?: ReactNode;
}) {
  const t = useTranslations("chat.thread");
  return (
    <header className="border-border flex shrink-0 flex-col gap-1.5 border-b px-4 py-3">
      <div className="flex items-start gap-2">
        <MessagesSquare className="text-muted-foreground mt-0.5 h-5 w-5 shrink-0" aria-hidden />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <h2 className="text-foreground truncate text-base font-semibold">{title}</h2>
          {details}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {children}
          <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label={t("close")}>
            <X />
          </Button>
        </div>
      </div>
      {footer}
    </header>
  );
}

/** The root message above the thread, read-only apart from its reactions; scrolls on its own when long. */
function RootMessage({
  message,
  onJumpTo,
}: {
  message: ChatMessageResponse;
  onJumpTo: (messageId: string) => void;
}) {
  const meId = useChatMeId();
  const dates = useDateFormat();
  const canWrite = useChatCanWrite();
  // The panel is the thread: the root's own summary line would only repeat its header.
  const root = useMemo(() => ({ ...message, thread: null }), [message]);
  const renderFooter = useCallback(
    (item: ChatMessageResponse) => (item.deleted ? null : <ChatReactions message={item} canReact={canWrite} />),
    [canWrite],
  );

  return (
    <div className="border-border max-h-[40%] shrink-0 overflow-y-auto border-b pt-1 pb-3">
      <ChatMessage
        message={root}
        meId={meId}
        dates={dates}
        grouped={false}
        highlighted={false}
        onJumpTo={onJumpTo}
        renderFooter={renderFooter}
      />
    </div>
  );
}

// ── Open thread ─────────────────────────────────────────────────────

type OpenThreadProps = Omit<ChatThreadPanelProps, "threadId" | "draftRoot"> & { threadId: string };

function toAvatarUsers(thread: ChatThreadResponse): UserAvatarUser[] {
  return thread.members.map((member) => ({
    firstName: member.firstName ?? undefined,
    lastName: member.lastName ?? undefined,
    email: member.email ?? undefined,
  }));
}

function OpenThread({
  threadId,
  focusComposer: focusComposerOnOpen,
  onClose,
  onJumpToChannelMessage,
  typingUserIds,
  newMessageCount,
  onNewestShown,
  jumpTo,
  onJumpConsumed,
  listRef,
  composerRef,
}: OpenThreadProps) {
  const t = useTranslations("chat.thread");
  const canWrite = useChatCanWrite();
  const { data: thread, error, isPending, isFetching, refetch } = useChatThread(threadId);
  // The thread's read position as the reader found it; the list opens at its divider.
  const { marker, clearMarker } = useChatUnreadMarker(thread?.channelId, threadId);
  const join = useJoinChatThread();
  const leave = useLeaveChatThread();

  // Reply and Edit inside the thread; the message actions set them.
  const [replyTo, setReplyTo] = useState<ChatMessageResponse | null>(null);
  const [editing, setEditing] = useState<ChatMessageResponse | null>(null);
  const cancelReply = useCallback(() => setReplyTo(null), []);
  const finishEdit = useCallback(() => setEditing(null), []);
  const reply = useCallback((message: ChatMessageResponse) => {
    setEditing(null);
    setReplyTo(message);
  }, []);
  const focusComposer = useCallback(() => composerRef?.current?.focus(), [composerRef]);
  const editLast = useCallback((): boolean => {
    const message = listRef?.current?.lastOwnEditable();
    if (!message) return false;
    setEditing(message);
    return true;
  }, [listRef]);
  // Escape in an empty thread composer with nothing to cancel closes the panel.
  const closeWhenEmpty = useCallback(
    (empty: boolean): boolean => {
      if (!empty) return false;
      onClose();
      return true;
    },
    [onClose],
  );

  const renderFooter = useCallback(
    (message: ChatMessageResponse) =>
      message.deleted ? null : <ChatReactions message={message} canReact={canWrite} />,
    [canWrite],
  );

  if (isPending) {
    return (
      <div className="flex h-full min-h-0 flex-col">
        <PanelHeader title={t("panelLabel")} onClose={onClose} />
        <div className="text-muted-foreground flex flex-1 items-center justify-center gap-2 text-sm">
          <Loader2 className="h-5 w-5 animate-spin" />
          {t("loading")}
        </div>
      </div>
    );
  }

  if (!thread) {
    const notFound = isApiError(error, CHAT_ERROR_CODES.threadNotFound);
    return (
      <div className="flex h-full min-h-0 flex-col">
        <PanelHeader title={t("panelLabel")} onClose={onClose} />
        <div role="alert" className="flex flex-1 flex-col items-center justify-center gap-2 p-6 text-center">
          {notFound ? (
            <SearchX className="text-muted-foreground h-8 w-8" />
          ) : (
            <AlertCircle className="text-destructive h-8 w-8" />
          )}
          <p className="text-foreground text-sm font-medium">{notFound ? t("notFound") : t("loadError")}</p>
          {!notFound && error?.message && <p className="text-muted-foreground max-w-xs text-xs">{error.message}</p>}
          {notFound ? (
            <Button variant="outline" className="mt-2" onClick={onClose}>
              {t("close")}
            </Button>
          ) : (
            <Button variant="outline" className="mt-2" disabled={isFetching} onClick={() => void refetch()}>
              {t("retry")}
            </Button>
          )}
        </div>
      </div>
    );
  }

  const membership = join.isPending || leave.isPending;
  const avatars = toAvatarUsers(thread);
  const closed = thread.status === "CLOSED";

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PanelHeader
        title={thread.title}
        onClose={onClose}
        details={
          <div className="text-muted-foreground flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
            <UserAvatarStack users={avatars} max={3} size="xs" />
            <span>{t("members", { count: thread.members.length })}</span>
          </div>
        }
        footer={<ThreadStatusDetails thread={thread} />}
      >
        {thread.canManage && <ThreadSettings thread={thread} />}
        {thread.canManage && <ThreadStatusMenu thread={thread} />}
        {canWrite &&
          (thread.member ? (
            <IconAction label={t("leave")} disabled={membership} onClick={() => leave.mutate(thread.id)}>
              {leave.isPending ? <Loader2 className="animate-spin" /> : <LogOut />}
            </IconAction>
          ) : (
            <Button
              variant="outline"
              size="sm"
              className="h-8 gap-1.5 text-xs"
              disabled={membership}
              onClick={() => join.mutate(thread.id)}
            >
              {join.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <LogIn className="h-3.5 w-3.5" />}
              {t("join")}
            </Button>
          ))}
      </PanelHeader>

      <RootMessage message={thread.rootMessage} onJumpTo={onJumpToChannelMessage} />

      <div className="flex min-h-0 flex-1 flex-col">
        {/* The root above is read-only: only the thread's own messages get actions. */}
        <ChatMessageActionsProvider
          onReply={reply}
          onEdit={setEditing}
          onFocusComposer={focusComposer}
          closed={closed}
        >
          <ChatMessageList
            ref={listRef}
            threadId={thread.id}
            label={t("logLabel", { title: thread.title })}
            startLabel={t("start")}
            jumpTo={jumpTo}
            onJumpConsumed={onJumpConsumed}
            unreadMarker={marker}
            onSent={clearMarker}
            newMessageCount={newMessageCount}
            onNewestShown={onNewestShown}
            renderFooter={renderFooter}
            emptyState={
              <EmptyState Icon={MessagesSquare} title={t("empty.title")} description={t("empty.description")} />
            }
          />
        </ChatMessageActionsProvider>
        {thread.status === "ARCHIVED" && (
          <p className="text-muted-foreground flex shrink-0 items-center gap-1.5 px-4 pt-2 text-xs">
            <Archive className="h-3.5 w-3.5 shrink-0" aria-hidden />
            {t("archivedHint")}
          </p>
        )}
        {closed ? (
          <ClosedThreadNotice thread={thread} />
        ) : (
          <ChatComposer
            ref={composerRef}
            threadId={thread.id}
            placeholder={t("composerPlaceholder")}
            replyTo={replyTo}
            onCancelReply={cancelReply}
            editing={editing}
            onEditDone={finishEdit}
            typingUserIds={typingUserIds}
            autoFocus={focusComposerOnOpen}
            onEditLast={editLast}
            onIdleEscape={closeWhenEmpty}
          />
        )}
      </div>
    </div>
  );
}

// ── Status ──────────────────────────────────────────────────────────

/** Relative times ("5 minutes ago") are refreshed this often. */
const STATUS_TIME_REFRESH_MS = 60_000;

function userName(user: ChatUserResponse): string {
  return getDisplayName({
    firstName: user.firstName ?? undefined,
    lastName: user.lastName ?? undefined,
    email: user.email ?? undefined,
  });
}

/**
 * The status of a closed or archived thread under its title: the chip, who
 * changed it and when ("Closed by Ana · 5 minutes ago"). A thread archived
 * by inactivity has no name, and its time is the end of its window.
 */
function ThreadStatusDetails({ thread }: { thread: ChatThreadResponse }) {
  const t = useTranslations("chat.thread.status");
  const format = useFormatter();
  const dates = useDateFormat();
  const now = useNow({ updateInterval: STATUS_TIME_REFRESH_MS });
  if (thread.status === "ACTIVE") return null;

  const by = thread.statusChangedBy;
  const who = by ? t(thread.status === "CLOSED" ? "closedBy" : "archivedBy", { name: userName(by) }) : null;
  const at = thread.statusChangedAt;
  const shownAt = at ? chatPastTime(at, now) : null;

  return (
    <p className="text-muted-foreground flex min-w-0 items-center gap-1.5 pl-7 text-xs">
      <ChatThreadStatusChip status={thread.status} />
      {who && <span className="truncate">{who}</span>}
      {at && shownAt && (
        <>
          {who && <span aria-hidden>·</span>}
          <time
            dateTime={at}
            title={dates.formatDateTime(at)}
            className="shrink-0"
            suppressHydrationWarning
          >
            {format.relativeTime(shownAt, now)}
          </time>
        </>
      )}
    </p>
  );
}

type StatusActionId = "close" | "reopen" | "archive" | "unarchive";

const STATUS_ACTIONS: Record<StatusActionId, { target: ChatThreadStatus; Icon: LucideIcon }> = {
  close: { target: "CLOSED", Icon: Lock },
  reopen: { target: "ACTIVE", Icon: LockOpen },
  archive: { target: "ARCHIVED", Icon: Archive },
  unarchive: { target: "ACTIVE", Icon: ArchiveRestore },
};

/**
 * The status changes a thread allows from `status`. A closed thread is only
 * reopened: archiving it is refused, as a later reply would bring it back
 * without a Reopen.
 */
function statusActions(status: ChatThreadStatus): StatusActionId[] {
  switch (status) {
    case "ACTIVE":
      return ["close", "archive"];
    case "CLOSED":
      return ["reopen"];
    case "ARCHIVED":
      return ["unarchive", "close"];
  }
}

/** Close, Reopen, Archive and Unarchive, for the creator and the organization's OWNERs and ADMINs. */
function ThreadStatusMenu({ thread }: { thread: ChatThreadResponse }) {
  const t = useTranslations("chat.thread.status");
  const change = useChangeChatThreadStatus();

  return (
    <DropdownMenu>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t("menu")} disabled={change.isPending}>
                {change.isPending ? <Loader2 className="animate-spin" /> : <Ellipsis />}
              </Button>
            </DropdownMenuTrigger>
          </TooltipTrigger>
          <TooltipContent>
            <p>{t("menu")}</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
      <DropdownMenuContent align="end" className="w-48">
        <DropdownMenuLabel className="text-muted-foreground text-xs font-normal">{t("menu")}</DropdownMenuLabel>
        {statusActions(thread.status).map((id) => {
          const { target, Icon } = STATUS_ACTIONS[id];
          return (
            // A refused change is toasted by useChangeChatThreadStatus.
            <DropdownMenuItem key={id} onSelect={() => change.mutate({ threadId: thread.id, status: target })}>
              <Icon aria-hidden />
              {t(`actions.${id}`)}
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * In place of the composer of a closed thread: nobody can post or edit
 * there. Those who may manage the thread reopen it here.
 */
function ClosedThreadNotice({ thread }: { thread: ChatThreadResponse }) {
  const t = useTranslations("chat.thread.status");
  const change = useChangeChatThreadStatus();

  return (
    <div
      role="status"
      className="border-border bg-muted/40 m-3 flex shrink-0 items-center gap-3 rounded-md border px-3 py-2.5"
    >
      <Lock className="text-muted-foreground h-4 w-4 shrink-0" aria-hidden />
      <div className="flex min-w-0 flex-1 flex-col">
        <p className="text-foreground text-sm font-medium">{t("closedNotice")}</p>
        {!thread.canManage && <p className="text-muted-foreground text-xs">{t("closedNoticeHint")}</p>}
      </div>
      {thread.canManage && (
        <Button
          variant="outline"
          size="sm"
          className="h-8 shrink-0 gap-1.5 text-xs"
          disabled={change.isPending}
          // A refused reopen is toasted by useChangeChatThreadStatus.
          onClick={() => change.mutate({ threadId: thread.id, status: "ACTIVE" })}
        >
          {change.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <LockOpen className="h-3.5 w-3.5" />}
          {t("reopen")}
        </Button>
      )}
    </div>
  );
}

function IconAction({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled?: boolean;
  onClick?: () => void;
  children: ReactNode;
}) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="ghost" size="icon-sm" aria-label={label} disabled={disabled} onClick={onClick}>
            {children}
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          <p>{label}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

/** The thread's auto-archive window as the form's value; an unknown window shows the default. */
function toAutoArchiveMinutes(minutes: number): ChatAutoArchiveMinutes {
  const parsed = ChatAutoArchiveMinutesSchema.safeParse(minutes);
  return parsed.success ? parsed.data : CHAT_DEFAULT_AUTO_ARCHIVE_MINUTES;
}

/** Rename and auto-archive window, for the creator and the organization's OWNERs and ADMINs. */
function ThreadSettings({ thread }: { thread: ChatThreadResponse }) {
  const t = useTranslations("chat.thread");
  const [open, setOpen] = useState(false);
  const update = useUpdateChatThread();

  const form = useForm<ChatThreadTitleFormValues>({
    resolver: zodResolver(chatThreadTitleFormSchema),
    defaultValues: { title: thread.title, autoArchiveMinutes: toAutoArchiveMinutes(thread.autoArchiveMinutes) },
  });

  const onOpenChange = (next: boolean) => {
    if (update.isPending) return;
    // Every opening starts from the thread as it is now, which others may have renamed.
    if (next) form.reset({ title: thread.title, autoArchiveMinutes: toAutoArchiveMinutes(thread.autoArchiveMinutes) });
    setOpen(next);
  };

  // A refused update is toasted by useUpdateChatThread; the popover then stays open with the entered values.
  const onSubmit = (values: ChatThreadTitleFormValues) => {
    update.mutate(
      { threadId: thread.id, title: values.title, autoArchiveMinutes: values.autoArchiveMinutes },
      { onSuccess: () => setOpen(false) },
    );
  };

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <PopoverTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label={t("settings.title")}>
                <Pencil />
              </Button>
            </PopoverTrigger>
          </TooltipTrigger>
          <TooltipContent>
            <p>{t("settings.title")}</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
      <PopoverContent align="end" className="w-80 p-3">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3">
            <h3 className="text-foreground text-sm font-semibold">{t("settings.title")}</h3>
            <TextField
              name="title"
              label={t("settings.name")}
              control={form.control}
              required
              maxLength={CHAT_THREAD_TITLE_MAX}
              disabled={update.isPending}
              className="h-9"
            />
            <FormField
              control={form.control}
              name="autoArchiveMinutes"
              render={({ field: { value, onChange, ...field } }) => (
                <FormFieldWrapper label={t("settings.autoArchive")} description={t("settings.autoArchiveHint")}>
                  {/* The window is a number; the select's values are its string form. */}
                  <Select
                    value={String(value)}
                    onValueChange={(next) => onChange(Number(next))}
                    disabled={update.isPending}
                  >
                    <SelectTrigger {...field} className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {CHAT_AUTO_ARCHIVE_OPTIONS.map((minutes) => (
                          <SelectItem key={minutes} value={String(minutes)}>
                            {t(`settings.autoArchiveOptions.${minutes}`)}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </FormFieldWrapper>
              )}
            />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" size="sm" disabled={update.isPending} onClick={() => setOpen(false)}>
                {t("cancel")}
              </Button>
              <Button type="submit" size="sm" disabled={update.isPending}>
                {update.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                {t("settings.save")}
              </Button>
            </div>
          </form>
        </Form>
      </PopoverContent>
    </Popover>
  );
}

// ── Draft ───────────────────────────────────────────────────────────

const draftFormSchema = chatThreadTitleFormSchema.pick({ title: true });
type DraftFormValues = Pick<ChatThreadTitleFormValues, "title">;

type ThreadDraftProps = Omit<
  ChatThreadPanelProps,
  "threadId" | "draftRoot" | "focusComposer" | "listRef" | "composerRef"
> & {
  root: ChatMessageResponse;
};

/**
 * A thread about to be started on `root`: its title, prefilled from the
 * message, and Create. When the message got a thread meanwhile (409), the
 * existing thread opens instead.
 */
function ThreadDraft({ root, onClose, onOpenThread, onJumpToChannelMessage }: ThreadDraftProps) {
  const t = useTranslations("chat.thread");
  const tMessage = useTranslations("chat.message");
  const locale = useLocale();
  const create = useCreateChatThread();

  const defaultTitle = useMemo(
    () =>
      chatThreadDefaultTitle(
        root,
        {
          unknownMember: tMessage("unknownMember"),
          fallback: t("defaultTitle"),
          dailyUpdate: (values) => t("dailyUpdateTitle", values),
        },
        locale,
      ),
    [root, t, tMessage, locale],
  );

  const form = useForm<DraftFormValues>({
    resolver: zodResolver(draftFormSchema),
    defaultValues: { title: defaultTitle },
  });

  // A refused create is toasted by useCreateChatThread; the draft stays with the entered title.
  const onSubmit = (values: DraftFormValues) => {
    create.mutate(
      { rootMessageId: root.id, title: values.title },
      { onSuccess: (result) => onOpenThread(result.threadId, { focus: true }) },
    );
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PanelHeader title={t("newTitle")} onClose={onClose} />
      <RootMessage message={root} onJumpTo={onJumpToChannelMessage} />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3 px-4 py-4">
          <TextField
            name="title"
            label={t("settings.name")}
            control={form.control}
            required
            autoFocus
            maxLength={CHAT_THREAD_TITLE_MAX}
            disabled={create.isPending}
            className="h-9"
          />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" size="sm" disabled={create.isPending} onClick={onClose}>
              {t("cancel")}
            </Button>
            <Button type="submit" size="sm" disabled={create.isPending} className="gap-1.5">
              {create.isPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <MessagesSquare className="h-3.5 w-3.5" />
              )}
              {t("create")}
            </Button>
          </div>
        </form>
      </Form>
    </div>
  );
}
