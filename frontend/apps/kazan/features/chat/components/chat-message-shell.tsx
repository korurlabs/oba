"use client";

import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
  type RefObject,
} from "react";
import { useTranslations } from "next-intl";
import { Pin, Reply } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { UserAvatar, type UserAvatarUser } from "@/components/ui/user-avatar";
import type { UseDateFormat } from "@oba/shared/i18n/use-date-format";
import { CHAT_SWIPE_OFFSET_VAR, CHAT_SWIPE_PROGRESS_VAR } from "../hooks/use-swipe-to-reply";
import type { ChatMessageSide } from "./chat-attachments";
import {
  CHAT_MESSAGE_PART_ATTRIBUTE,
  CHAT_MESSAGE_ROW_ATTRIBUTE,
  ChatMessageMenus,
  ChatMessageToolbar,
  type ChatMessageRowController,
} from "./chat-message-actions";

/**
 * The attribute every row of a message list carries, with the row's ID: the
 * list finds rows by it to keep the reader's place and to scroll to a message.
 */
export const CHAT_ROW_ATTRIBUTE = "data-chat-row";

const partProps = { [CHAT_MESSAGE_PART_ATTRIBUTE]: "" };

// ── Row ─────────────────────────────────────────────────────────────

interface ChatRowFrameProps {
  rowId: string;
  /** A continuation of the previous message's author group: no header, little space. */
  grouped: boolean;
  /** The message notifies the reader (a mention, or a reply to the reader's message): a primary left rule and a faint tint. */
  mentioned?: boolean;
  /** Briefly marked after a jump to it. */
  highlighted?: boolean;
  /**
   * The reader wrote it: the row is mirrored to the right. Footer parts
   * rendered by the list's caller read it from the row's `data-own` attribute.
   */
  own?: boolean;
  /**
   * The message's actions (menus, swipe, row keys); a row without them
   * (deleted, unsent) is plain. The hover toolbar is not part of the frame:
   * the message places it on its own box with `HoverToolbar`.
   */
  interaction?: ChatMessageRowController | null;
  children: ReactNode;
}

const swipeContentStyle: CSSProperties = {
  transform: `translateX(var(${CHAT_SWIPE_OFFSET_VAR}, 0px))`,
};
const swipeArrowStyle: CSSProperties = { opacity: `var(${CHAT_SWIPE_PROGRESS_VAR}, 0)` };
const swipeArrowIconStyle: CSSProperties = {
  transform: `scale(calc(0.6 + 0.4 * var(${CHAT_SWIPE_PROGRESS_VAR}, 0)))`,
};

/**
 * One row of a message list: the hover background, the row's padding, the
 * swipe and the menus. The first message of a group, every card and every
 * placeholder keep 8px between the row's edge and the message, so the hover
 * background wraps the whole message; a grouped continuation keeps 4px, so
 * one author's messages stay close together.
 */
export function ChatRowFrame({
  rowId,
  grouped,
  mentioned,
  highlighted,
  own,
  interaction,
  children,
}: ChatRowFrameProps) {
  const swipe = !!interaction?.swipe;
  const frame = (
    <div
      {...{ [CHAT_ROW_ATTRIBUTE]: rowId }}
      {...(own ? { "data-own": "" } : {})}
      {...(interaction ? { ...interaction.rowProps, [CHAT_MESSAGE_ROW_ATTRIBUTE]: "" } : {})}
      className={cn(
        "group/row relative border-l-2 px-4 transition-colors duration-500 lg:px-6",
        grouped ? "py-1" : "mt-1 py-2",
        mentioned ? "border-primary bg-primary/5" : "hover:bg-muted/40 border-transparent",
        highlighted && "bg-primary/15",
        // Vertical scrolling stays the browser's; a horizontal move is the swipe.
        interaction &&
          "focus-visible:bg-muted/60 focus-visible:ring-ring touch-pan-y outline-none [-webkit-touch-callout:none] focus-visible:ring-2 focus-visible:ring-inset pointer-coarse:select-none",
        // While its toolbar shows, the row paints over its neighbours, which
        // the toolbar overlaps (the next one when it sits below the message).
        interaction && "focus-within:z-10 hover:z-10 has-[[data-state=open]]:z-10",
        // A swiped message leaves the row; it must not widen the list's scroll area while it does.
        swipe && "overflow-x-clip"
      )}
    >
      {swipe && (
        <div
          aria-hidden
          style={swipeArrowStyle}
          className="pointer-events-none absolute inset-y-0 left-2 flex items-center transition-opacity duration-200 group-data-[swiping]/row:transition-none"
        >
          <span
            style={swipeArrowIconStyle}
            className="bg-primary/15 text-primary group-data-[swipe-armed]/row:bg-primary group-data-[swipe-armed]/row:text-primary-foreground flex size-7 items-center justify-center transition-[background-color,color,scale] duration-150 group-data-[swipe-armed]/row:scale-110 motion-reduce:transition-none"
          >
            <Reply className="size-4" />
          </span>
        </div>
      )}
      {swipe ? (
        <div
          style={swipeContentStyle}
          className="transition-transform duration-200 group-data-[swiping]/row:transition-none motion-reduce:transition-none"
        >
          {children}
        </div>
      ) : (
        children
      )}
    </div>
  );
  return interaction ? <ChatMessageMenus row={interaction}>{frame}</ChatMessageMenus> : frame;
}

// ── Toolbar ─────────────────────────────────────────────────────────

/** The nearest ancestor that scrolls vertically: the message list or the thread panel. */
function scrollParent(element: HTMLElement): HTMLElement | null {
  for (let node = element.parentElement; node; node = node.parentElement) {
    const { overflowY } = getComputedStyle(node);
    if (overflowY === "auto" || overflowY === "scroll") return node;
  }
  return null;
}

/**
 * Where the toolbar goes when its row is entered or takes focus: below the
 * box when the space between the box and the top of the scroll area is too
 * small for it, as on the first visible message; otherwise above.
 */
function useToolbarSide(toolbarRef: RefObject<HTMLDivElement | null>): "top" | "bottom" {
  const [side, setSide] = useState<"top" | "bottom">("top");
  useEffect(() => {
    const toolbar = toolbarRef.current;
    const row = toolbar?.closest<HTMLElement>(`[${CHAT_MESSAGE_ROW_ATTRIBUTE}]`);
    if (!toolbar || !row) return;
    const place = () => {
      const box = toolbar.offsetParent;
      if (!box) return;
      const area = scrollParent(row);
      const areaTop = area ? area.getBoundingClientRect().top : 0;
      const room = box.getBoundingClientRect().top - areaTop;
      setSide(room < toolbar.offsetHeight ? "bottom" : "top");
    };
    row.addEventListener("pointerenter", place);
    row.addEventListener("focusin", place);
    return () => {
      row.removeEventListener("pointerenter", place);
      row.removeEventListener("focusin", place);
    };
  }, [toolbarRef]);
  return side;
}

/**
 * The hover toolbar of a message, placed in the message's box, which is
 * `relative`: entirely above the box, its bottom edge on the box's top edge
 * and its right edge on the box's right edge, so it never covers the message
 * it acts on (it overlaps the previous one instead). On the reader's side it
 * skips the avatar column, so its right edge is the bubble's. Without room
 * above it sits just below the box. Its 2px padding towards the box keeps the
 * pointer inside the row on the way from the message to the toolbar.
 *
 * It shows while the row is hovered or holds keyboard focus, and stays while
 * its picker or menu is open, so they keep their anchor. It stays mounted and
 * fades and slides in and out; hidden it is invisible, so it takes neither
 * pointer nor focus. With reduced motion it only fades.
 */
export function HoverToolbar({
  row,
  side = "start",
  className,
}: {
  row: ChatMessageRowController;
  side?: ChatMessageSide;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const placement = useToolbarSide(ref);
  return (
    <div
      ref={ref}
      data-side={placement}
      {...partProps}
      className={cn(
        "absolute right-0 bottom-full z-10 flex pb-0.5 [--toolbar-shift:4px]",
        side === "end" && "right-11",
        "data-[side=bottom]:top-full data-[side=bottom]:bottom-auto data-[side=bottom]:pt-0.5 data-[side=bottom]:pb-0 data-[side=bottom]:[--toolbar-shift:-4px]",
        // Hidden, nudged towards the box; the exit is quicker than the entry.
        "pointer-events-none invisible opacity-0 transition-[opacity,translate,visibility] duration-100 ease-in motion-safe:translate-y-[var(--toolbar-shift)]",
        "group-hover/row:pointer-events-auto group-hover/row:visible group-hover/row:translate-y-0 group-hover/row:opacity-100 group-hover/row:duration-150 group-hover/row:ease-out",
        "group-focus-within/row:pointer-events-auto group-focus-within/row:visible group-focus-within/row:translate-y-0 group-focus-within/row:opacity-100 group-focus-within/row:duration-150 group-focus-within/row:ease-out",
        "has-[[data-state=open]]:pointer-events-auto has-[[data-state=open]]:visible has-[[data-state=open]]:translate-y-0 has-[[data-state=open]]:opacity-100 has-[[data-state=open]]:duration-150 has-[[data-state=open]]:ease-out",
        className
      )}
    >
      <ChatMessageToolbar row={row} />
    </div>
  );
}

// ── Message ─────────────────────────────────────────────────────────

/** "(edited)" with its time in a tooltip, and the pin mark; nothing when neither applies. */
export function MessageMeta({
  editedAt,
  pinnedAt,
  dates,
}: {
  editedAt: string | null | undefined;
  pinnedAt: string | null | undefined;
  dates: UseDateFormat;
}) {
  const t = useTranslations("chat.message");
  if (!editedAt && !pinnedAt) return null;
  return (
    <span className="text-muted-foreground inline-flex items-center gap-1.5 text-[11px]">
      {editedAt && (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="cursor-default">{t("edited")}</span>
            </TooltipTrigger>
            <TooltipContent>
              <p suppressHydrationWarning>
                {t("editedAt", { time: dates.formatDateTime(editedAt) })}
              </p>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      )}
      {pinnedAt && (
        <span className="inline-flex items-center gap-0.5" title={t("pinned")}>
          <Pin className="h-3 w-3" aria-hidden />
          <span className="sr-only">{t("pinned")}</span>
        </span>
      )}
    </span>
  );
}

/**
 * Marks its content as a part of the message without adding a box: the body
 * column's gap still spaces the content's own elements, and an empty part
 * (no reactions, no files) takes no gap.
 */
export function ChatMessagePart({ children }: { children: ReactNode }) {
  return (
    <div {...partProps} className="contents">
      {children}
    </div>
  );
}

/**
 * Avatar column: the avatar on a group's first message, the time on hover for
 * the others, level with the first line of the bubble.
 */
function Gutter({
  grouped,
  createdAt,
  user,
  dates,
}: {
  grouped: boolean;
  createdAt: string;
  user: UserAvatarUser;
  dates: UseDateFormat;
}) {
  return (
    <div className="flex w-8 shrink-0 justify-center">
      {grouped ? (
        <time
          dateTime={createdAt}
          title={dates.formatDateTime(createdAt)}
          className="text-muted-foreground pt-2.5 text-[10px] leading-5 whitespace-nowrap opacity-0 group-focus-within/row:opacity-100 group-hover/row:opacity-100"
          suppressHydrationWarning
        >
          {dates.formatTime(createdAt)}
        </time>
      ) : (
        <UserAvatar user={user} size="md" showName={false} className="mt-0.5" />
      )}
    </div>
  );
}

function Header({
  name,
  createdAt,
  dates,
  side,
  children,
}: {
  name: string;
  createdAt: string;
  dates: UseDateFormat;
  side: ChatMessageSide;
  children?: ReactNode;
}) {
  return (
    <div
      {...partProps}
      className={cn("flex flex-wrap items-baseline gap-x-2", side === "end" && "flex-row-reverse")}
    >
      <span className="text-foreground text-sm font-semibold">{name}</span>
      <time
        dateTime={createdAt}
        title={dates.formatDateTime(createdAt)}
        className="text-muted-foreground text-xs"
        suppressHydrationWarning
      >
        {dates.formatTime(createdAt)}
      </time>
      {children}
    </div>
  );
}

interface ChatMessageLayoutProps {
  side: ChatMessageSide;
  /** A continuation of the previous message's author group: the time on hover instead of avatar and header. */
  grouped: boolean;
  author: UserAvatarUser;
  name: string;
  createdAt: string;
  dates: UseDateFormat;
  /** The edited and pinned marks: in the header, or under the content of a grouped message. */
  meta?: ReactNode;
  /** The quote of the message this one replies to. */
  quote?: ReactNode;
  /** Below the content: reactions, the thread summary, the send status. */
  footer?: ReactNode;
  /** The hover toolbar; the message's box anchors it. */
  toolbar?: ReactNode;
  /** The bubble and the files. */
  children: ReactNode;
}

/**
 * One message, the same for every kind: the reply quote, the avatar column,
 * the header, the content and the footer, with one gap between them. The
 * reader's own messages are mirrored to the right. It has no row of its own,
 * so a screen outside a message list can show a message too.
 *
 * The box includes the quote, so the toolbar above it never covers the
 * quote's link to the original.
 */
export function ChatMessageLayout({
  side,
  grouped,
  author,
  name,
  createdAt,
  dates,
  meta,
  quote,
  footer,
  toolbar,
  children,
}: ChatMessageLayoutProps) {
  const end = side === "end";
  return (
    <div className="relative">
      {quote && (
        <div className={cn("mb-1 flex", end ? "justify-end pr-11" : "pl-11")}>
          <div {...partProps} className="min-w-0">
            {quote}
          </div>
        </div>
      )}
      <div className={cn("flex gap-3", end && "flex-row-reverse")}>
        <Gutter grouped={grouped} createdAt={createdAt} user={author} dates={dates} />
        <div
          className={cn("flex min-w-0 flex-1 flex-col gap-1.5", end ? "items-end" : "items-start")}
        >
          {!grouped && (
            <Header name={name} createdAt={createdAt} dates={dates} side={side}>
              {meta}
            </Header>
          )}
          {children}
          {grouped && meta && <ChatMessagePart>{meta}</ChatMessagePart>}
          {footer && <ChatMessagePart>{footer}</ChatMessagePart>}
        </div>
      </div>
      {/* After the footer, so the Tab order follows the reading order. */}
      {toolbar}
    </div>
  );
}

/**
 * The square box around a message's content: the card surface for others, a
 * faint blue with a primary edge for the reader's own, so the two sides stay
 * apart without a tint loud enough to compete with the text. A card fills it
 * to the bubble's widest.
 */
export function ChatMessageBubble({
  side,
  fill,
  className,
  children,
}: {
  side: ChatMessageSide;
  fill?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const end = side === "end";
  return (
    <div
      {...partProps}
      className={cn(
        "max-w-full min-w-0 rounded-none border px-3 py-2 text-sm sm:max-w-[85%]",
        end ? "border-primary/25 bg-[#f6fbff] dark:bg-primary/10" : "bg-card border-border",
        fill && "w-full",
        className
      )}
    >
      {children}
    </div>
  );
}
