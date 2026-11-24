"use client";

import {
  useState,
  type MouseEvent as ReactMouseEvent,
  type TouchEvent as ReactTouchEvent,
} from "react";
import { useTranslations } from "next-intl";
import {
  DndContext,
  MouseSensor,
  TouchSensor,
  closestCenter,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  ArrowDown,
  ArrowUp,
  Hash,
  Lock,
  MoreHorizontal,
  Pencil,
  Plus,
} from "lucide-react";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import {
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  useSidebar,
} from "@oba/ui/sky/sidebar";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { useChatChannelOrder } from "../hooks/use-chat-channel-order";
import { useChatOpenChannelId } from "../hooks/use-chat-channels";
import { useChatOrganizationId } from "../hooks/use-chat-messages";
import { chatChannelUnreadById, useChatUnread } from "../hooks/use-mark-read";
import { chatChannelPath } from "../lib/chat-route";
import { CHAT_UNREAD_CAP, ChatChannelVisibility, type ChatChannelResponse } from "../schemas";
import { ChatChannelDialog, useCanManageChatChannels } from "./chat-channel-dialog";
import { ChatChannelMembersDialog } from "./chat-channel-members-dialog";

/** Mouse movement that turns a press on a row into a drag; anything shorter is a click. */
const DRAG_ACTIVATION_DISTANCE_PX = 8;
/** Touch hold that turns a press on a row into a drag, so a quick swipe still scrolls the sidebar. */
const TOUCH_DRAG_DELAY_MS = 250;
/** Finger movement allowed during the touch hold before it counts as a scroll instead. */
const TOUCH_DRAG_TOLERANCE_PX = 5;

/** Keeps a press on a row's control from starting a drag of the row. */
function stopDragStart(event: ReactMouseEvent | ReactTouchEvent) {
  event.stopPropagation();
}

/**
 * The chat's GENERAL channels in the sidebar, in the reader's own order: drag
 * a row (by mouse or touch anywhere on it) or use its menu (Move up, Move
 * down, also by keyboard) to move it among them. The channels of the other
 * purposes (daily updates, pull requests) have their own sidebar rows and are
 * not listed here. Each row shows its unread count. OWNER and ADMIN also see
 * private channels they are not on; such a row opens the member list, where
 * they can add themselves, because its content stays hidden until then.
 *
 * Renders a sub-list, so it goes inside the chat menu item, which opens and
 * closes it.
 */
export function ChatChannelList() {
  const orgId = useChatOrganizationId();
  if (!orgId) return null;
  // Keyed by organization: the local order of one organization never shows in another.
  return <ChannelList key={orgId} />;
}

function ChannelList() {
  const t = useTranslations("chat.channels");
  const router = useRouter();
  const { setOpenMobile } = useSidebar();
  const canManage = useCanManageChatChannels();
  const {
    channels,
    listedChannels,
    isPending,
    isError,
    refetch,
    isFetching,
    moveChannel,
    moveChannelBy,
  } = useChatChannelOrder();
  const { data: unread } = useChatUnread();
  const unreadById = chatChannelUnreadById(unread);
  // The channel the chat's path names; none outside the chat.
  const activeId = useChatOpenChannelId();
  const [createOpen, setCreateOpen] = useState(false);
  // The dialogs look their channel up in every channel, so a change that gives
  // it another purpose does not empty the dialog while it closes.
  const [membersChannelId, setMembersChannelId] = useState<string | null>(null);
  const membersChannel = channels.find((channel) => channel.id === membersChannelId) ?? null;
  const [changeChannelId, setChangeChannelId] = useState<string | null>(null);
  const changeChannel = channels.find((channel) => channel.id === changeChannelId);

  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: DRAG_ACTIVATION_DISTANCE_PX } }),
    useSensor(TouchSensor, {
      activationConstraint: { delay: TOUCH_DRAG_DELAY_MS, tolerance: TOUCH_DRAG_TOLERANCE_PX },
    })
  );

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (over && active.id !== over.id) moveChannel(String(active.id), String(over.id));
  };

  // What a screen reader hears while a channel is dragged, in the reader's language.
  const nameOf = (id: UniqueIdentifier) =>
    listedChannels.find((channel) => channel.id === id)?.name ?? "";
  const positionOf = (id: UniqueIdentifier) =>
    listedChannels.findIndex((channel) => channel.id === id) + 1;
  const announcements: Announcements = {
    onDragStart: ({ active }) => t("drag.picked", { name: nameOf(active.id) }),
    onDragOver: ({ active, over }) =>
      over
        ? t("drag.over", {
            name: nameOf(active.id),
            position: positionOf(over.id),
            total: listedChannels.length,
          })
        : undefined,
    onDragEnd: ({ active, over }) =>
      over
        ? t("drag.dropped", {
            name: nameOf(active.id),
            position: positionOf(over.id),
            total: listedChannels.length,
          })
        : t("drag.cancelled", { name: nameOf(active.id) }),
    onDragCancel: ({ active }) => t("drag.cancelled", { name: nameOf(active.id) }),
  };

  let rows;
  if (isPending) {
    rows = (
      <>
        <Skeleton className="mx-2 my-1 h-5" />
        <Skeleton className="mx-2 my-1 h-5" />
      </>
    );
  } else if (isError) {
    rows = (
      <li className="flex items-center gap-1 px-2 py-1 text-xs">
        <span className="text-muted-foreground truncate">{t("loadError")}</span>
        <button
          type="button"
          className="text-foreground shrink-0 underline-offset-2 hover:underline disabled:opacity-50"
          disabled={isFetching}
          onClick={() => void refetch()}
        >
          {t("retry")}
        </button>
      </li>
    );
  } else if (listedChannels.length === 0) {
    rows = <li className="text-muted-foreground px-2 py-1 text-xs">{t("empty")}</li>;
  } else {
    rows = (
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={onDragEnd}
        accessibility={{ announcements }}
      >
        <SortableContext
          items={listedChannels.map((channel) => channel.id)}
          strategy={verticalListSortingStrategy}
        >
          {listedChannels.map((channel, index) => (
            <SortableChannelRow
              key={channel.id}
              channel={channel}
              active={channel.id === activeId}
              unread={unreadById.get(channel.id)?.unread ?? 0}
              mentions={unreadById.get(channel.id)?.mentionCount ?? 0}
              isFirst={index === 0}
              isLast={index === listedChannels.length - 1}
              onMove={(offset) => moveChannelBy(channel.id, offset)}
              onNavigate={() => setOpenMobile(false)}
              onOpenMembers={() => setMembersChannelId(channel.id)}
              onChange={canManage ? () => setChangeChannelId(channel.id) : undefined}
            />
          ))}
        </SortableContext>
      </DndContext>
    );
  }

  return (
    <>
      <SidebarMenuSub aria-label={t("listLabel")} className="mr-px pr-0">
        {rows}
        {canManage && (
          <SidebarMenuSubItem>
            <SidebarMenuSubButton asChild size="sm" className="text-muted-foreground">
              <button type="button" onClick={() => setCreateOpen(true)}>
                <Plus />
                <span>{t("new")}</span>
              </button>
            </SidebarMenuSubButton>
          </SidebarMenuSubItem>
        )}
      </SidebarMenuSub>
      {canManage && (
        <>
          <ChatChannelDialog
            open={createOpen}
            onOpenChange={setCreateOpen}
            onCreated={(channel) => {
              setOpenMobile(false);
              router.push(chatChannelPath(channel));
            }}
          />
          <ChatChannelDialog
            open={!!changeChannel}
            onOpenChange={(open) => !open && setChangeChannelId(null)}
            channel={changeChannel}
          />
          <ChatChannelMembersDialog
            channel={membersChannel}
            onClose={() => setMembersChannelId(null)}
          />
        </>
      )}
    </>
  );
}

interface ChannelRowProps {
  channel: ChatChannelResponse;
  active: boolean;
  unread: number;
  mentions: number;
  isFirst: boolean;
  isLast: boolean;
  onMove: (offset: -1 | 1) => void;
  onNavigate: () => void;
  onOpenMembers: () => void;
  /** Opens the change dialog; undefined for a reader who may not change channels. */
  onChange?: () => void;
}

function SortableChannelRow({
  channel,
  active,
  unread,
  mentions,
  isFirst,
  isLast,
  onMove,
  onNavigate,
  onOpenMembers,
  onChange,
}: ChannelRowProps) {
  const t = useTranslations("chat.channels");
  // Mouse and touch drag the whole row. The row selects no text and shows no
  // link callout, so a touch hold becomes a drag. The sortable attributes are
  // not spread: they would make the row a role="button" tab stop around its
  // link while there is no keyboard drag; the keyboard moves a channel from the
  // row menu instead.
  const { listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: channel.id,
  });
  const Icon = channel.visibility === ChatChannelVisibility.PRIVATE ? Lock : Hash;
  const shownUnread = unread >= CHAT_UNREAD_CAP ? `${CHAT_UNREAD_CAP - 1}+` : String(unread);
  const unreadLabel =
    unread === 0
      ? undefined
      : mentions > 0
        ? t("unreadMentions", { count: shownUnread, mentions })
        : t("unread", { count: shownUnread });

  const content = (
    <>
      <Icon />
      <span
        className={cn("min-w-0 flex-1 truncate", unread > 0 && "text-foreground font-semibold")}
      >
        {channel.name}
        {unreadLabel && <span className="sr-only">, {unreadLabel}</span>}
      </span>
      {unread > 0 && (
        <Badge
          aria-hidden
          variant={mentions > 0 ? "destructive" : "secondary"}
          className="h-4 min-w-4 shrink-0 justify-center rounded-full px-1 py-0 text-[10px] tabular-nums"
        >
          {shownUnread}
        </Badge>
      )}
    </>
  );

  return (
    <SidebarMenuSubItem
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        "group/channel flex items-center select-none [-webkit-touch-callout:none]",
        isDragging && "z-50 opacity-50"
      )}
      {...listeners}
    >
      {/*
        While the row is dragged its link takes no pointer events, so the drop's
        mouseup lands on the row and the click that follows never reaches the link.
        Otherwise the browser follows the href: the drag sensor stops that click
        before the Link's own handler can cancel it, so it opens the channel with a
        full page load and loses the new order.
      */}
      <SidebarMenuSubButton
        asChild
        size="sm"
        isActive={active}
        className={cn("min-w-0 flex-1 pr-1", isDragging && "pointer-events-none")}
      >
        {channel.member ? (
          // Not natively draggable: the browser's link drag would take over the row's drag.
          <Link
            href={chatChannelPath(channel)}
            onClick={onNavigate}
            draggable={false}
          >
            {content}
          </Link>
        ) : (
          // An admin's private channel they are not on: its content is hidden, so the row opens its member list.
          <button
            type="button"
            className="opacity-70"
            title={t("notMember")}
            onClick={onOpenMembers}
          >
            {content}
          </button>
        )}
      </SidebarMenuSubButton>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            aria-label={t("rowActions", { name: channel.name })}
            className="text-muted-foreground hover:text-foreground hover:bg-sidebar-accent flex h-6 w-6 shrink-0 items-center justify-center rounded opacity-0 group-hover/channel:opacity-100 focus-visible:opacity-100 data-[state=open]:opacity-100"
            onMouseDown={stopDragStart}
            onTouchStart={stopDragStart}
          >
            <MoreHorizontal className="h-3.5 w-3.5" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          align="end"
          className="w-44"
          onMouseDown={stopDragStart}
          onTouchStart={stopDragStart}
        >
          <DropdownMenuItem disabled={isFirst} onClick={() => onMove(-1)}>
            <ArrowUp className="h-3.5 w-3.5" />
            {t("moveUp")}
          </DropdownMenuItem>
          <DropdownMenuItem disabled={isLast} onClick={() => onMove(1)}>
            <ArrowDown className="h-3.5 w-3.5" />
            {t("moveDown")}
          </DropdownMenuItem>
          {onChange && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={onChange}>
                <Pencil className="h-3.5 w-3.5" />
                {t("change")}
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuSubItem>
  );
}
