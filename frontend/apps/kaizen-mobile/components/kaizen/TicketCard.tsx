import { CalendarDays, Clock, CornerDownRight, ListTree, Repeat } from "lucide-react-native";
import * as React from "react";
import {
  Pressable,
  View,
  type AccessibilityActionEvent,
  type AccessibilityActionInfo,
} from "react-native";

import { personName } from "@/components/boards/boardModel";
import { dueState, type DueState } from "@/components/home/homeModel";
import {
  LabelChip,
  MissionRank,
  PriorityIcon,
  StatusPill,
  TicketTypeBadge,
} from "@/components/kaizen/TicketMeta";
import {
  cardFootItems,
  cardHeadItems,
  cardProgress,
  createdByLine,
  MAX_CARD_LABELS,
  otherBoardPrefix,
  type CardFootItem,
  type CardHeadItem,
  type CardTicket,
  type TicketCardTiming,
} from "@/components/kaizen/ticketCardModel";
import {
  resolvePriority,
  resolveStatus,
  resolveTicketType,
} from "@/components/kaizen/ticketMetaModel";
import { AvatarStack } from "@/components/ui/avatar";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useLargeText } from "@/components/ui/use-large-text";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

/**
 * The pixel size of the assignee avatars at the end of the meta row: between
 * the 20 of a dense row and the 32 of a list row, so the initials read
 * clearly without making the row taller than the card's other marks need.
 */
const AVATAR_SIZE = 28;

/** The icon size beside the caption text of the meta row. */
const META_ICON_SIZE = 12;

type TicketCardProps = {
  ticket: CardTicket;
  onPress: () => void;
  /** A hold on the card, such as opening its menu. */
  onLongPress?: () => void;
  /** Shows the status dot at the start of the meta row. Default true. */
  showStatus?: boolean;
  /**
   * Gives the title two lines, for a Kanban column card. A list row keeps it
   * to one line so more tickets fit on the screen.
   */
  wrapTitle?: boolean;
  /** The board's own id: a ticket of a child board gets a "Board: X" line with that board's key. */
  boardId?: string;
  /**
   * The board line above the card, for a list of tickets from every board.
   * When set, it wins over the child-board key that `boardId` would give.
   */
  boardName?: string | null;
  /** Replaces the due day, such as an incident's open time. */
  timing?: TicketCardTiming | null;
  /**
   * Names the sprint the ticket is planned in, for a backlog row: a sub-ticket
   * can sit in a sprint while its parent waits in the backlog.
   */
  showPlanned?: boolean;
  /** Draws the lifted shadow of a dragged card. */
  elevated?: boolean;
  /** Screen-reader actions, such as the moves a drag would make. */
  accessibilityActions?: AccessibilityActionInfo[];
  onAccessibilityAction?: (event: AccessibilityActionEvent) => void;
  className?: string;
};

/**
 * A ticket in any list: its type chip and key, with priority and mission
 * rank at the end of that row; the parent of a sub-ticket, the title and up
 * to three labels; then the status, the due day (or the time slot a list
 * passes), sub-ticket progress and the assignees. The title takes one line
 * in a list and two on a Kanban card. A past due day is drawn in the late
 * colour. A ticket of another board gets a muted "Board: X" line above the
 * card. The last line says who created the ticket and when. The order of the
 * marks is in `ticketCardModel`.
 */
function TicketCardBase({
  ticket,
  onPress,
  onLongPress,
  showStatus = true,
  wrapTitle = false,
  boardId,
  boardName,
  timing,
  showPlanned = false,
  elevated = false,
  accessibilityActions,
  onAccessibilityAction,
  className,
}: TicketCardProps) {
  const { t } = useTranslation();
  // At an accessibility text size a one-line title shows a word or two, so it wraps in full.
  const largeText: boolean = useLargeText();
  const dates = useDates();
  const due: DueState | null = dueState(ticket.dueDate);
  const dueLabel: string | null = due
    ? due.today
      ? t("dateToday")
      : dates.shortDate(due.day)
    : null;
  const slot: TicketCardTiming | null =
    timing !== undefined
      ? timing
      : dueLabel
        ? {
            icon: "calendar",
            label: dueLabel,
            late: !!due && (due.overdue || due.today) && ticket.status !== "DONE",
          }
        : null;
  const progress = cardProgress(ticket.subTickets);
  const labels = ticket.labels ?? [];
  const people = (ticket.assignees ?? []).map((person) => ({ name: personName(person) }));
  const board: string | null =
    boardName !== undefined ? boardName : otherBoardPrefix(ticket, boardId);
  const createdBy: string | null = createdByLine(ticket, dates, t);
  const planned: string | null =
    showPlanned && ticket.sprint ? t("ticketCardPlanned", { sprint: ticket.sprint.name }) : null;
  const type = resolveTicketType(ticket.type);
  const priority = resolvePriority(ticket.priority);
  const status = resolveStatus(ticket.status);
  const head: CardHeadItem[] = cardHeadItems({
    priority: ticket.priority,
    rank: ticket.missionRank,
  });
  const foot: CardFootItem[] = cardFootItems({
    showStatus,
    timing: !!slot,
    progress: !!progress,
    assignees: people.length > 0,
  });

  const headItem = (item: CardHeadItem): React.ReactNode => {
    switch (item) {
      case "type":
        return <TicketTypeBadge key={item} type={ticket.type} size="sm" />;
      case "key":
        return (
          <Text key={item} variant="ticket-key">
            {ticket.key}
          </Text>
        );
      case "spacer":
        return <View key={item} className="flex-1" />;
      case "priority":
        return <PriorityIcon key={item} priority={ticket.priority} size={16} />;
      case "rank":
        return <MissionRank key={item} rank={ticket.missionRank} />;
    }
  };

  const footItem = (item: CardFootItem): React.ReactNode => {
    switch (item) {
      case "status":
        return <StatusPill key={item} status={ticket.status} variant="dot" />;
      case "timing":
        return slot ? (
          <View key={item} className="shrink flex-row items-center gap-1">
            <Icon
              as={slot.icon === "clock" ? Clock : CalendarDays}
              size={META_ICON_SIZE}
              className={slot.late ? "text-destructive" : "text-muted-foreground"}
            />
            <Text
              variant="caption"
              numberOfLines={1}
              className={slot.late ? "text-destructive" : "text-muted-foreground"}
            >
              {slot.label}
            </Text>
          </View>
        ) : null;
      case "progress":
        return progress ? (
          <View
            key={item}
            accessibilityLabel={t("boardSubTicketsDone", {
              done: progress.done,
              total: progress.total,
            })}
            className="flex-row items-center gap-1"
          >
            <Icon as={ListTree} size={META_ICON_SIZE} className="text-muted-foreground" />
            <Text variant="caption" className="text-muted-foreground">
              {`${progress.done}/${progress.total}`}
            </Text>
          </View>
        ) : null;
      case "spacer":
        return <View key={item} className="flex-1" />;
      case "assignees":
        return <AvatarStack key={item} people={people} size={AVATAR_SIZE} max={3} />;
    }
  };

  const spoken: string = [
    board ? t("ticketCardBoard", { name: board }) : null,
    `${type.rawLabel ?? t(type.labelKey)} ${ticket.key}`,
    ticket.title,
    planned,
    priority ? t(priority.labelKey) : null,
    showStatus ? t(status.labelKey) : null,
    slot
      ? slot.icon === "calendar" && timing === undefined
        ? t("myTicketsDue", { date: slot.label })
        : slot.label
      : null,
    people.length > 0 ? people.map((person) => person.name).join(", ") : null,
    createdBy,
  ]
    .filter(Boolean)
    .join(", ");

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={spoken}
      accessibilityActions={accessibilityActions}
      onAccessibilityAction={onAccessibilityAction}
      onPress={onPress}
      onLongPress={onLongPress}
      className={cn("gap-1 active:opacity-70", className)}
    >
      {board ? (
        <Text variant="caption" className="px-1 italic text-muted-foreground" numberOfLines={1}>
          {t("ticketCardBoard", { name: board })}
        </Text>
      ) : null}
      <Card elevated={elevated} className="gap-2">
        <View className="flex-row items-center gap-2">{head.map(headItem)}</View>
        {ticket.parentTicket ? (
          <View className="flex-row items-center gap-1">
            <Icon as={CornerDownRight} size={12} className="text-muted-foreground" />
            <Text variant="caption" className="shrink text-muted-foreground" numberOfLines={1}>
              {`${ticket.parentTicket.key} ${ticket.parentTicket.title}`}
            </Text>
          </View>
        ) : null}
        {planned ? (
          <View className="flex-row items-center gap-1">
            <Icon as={Repeat} size={12} className="text-muted-foreground" />
            <Text variant="caption" className="shrink text-muted-foreground" numberOfLines={1}>
              {planned}
            </Text>
          </View>
        ) : null}
        <Text variant="subhead" numberOfLines={largeText ? undefined : wrapTitle ? 2 : 1}>
          {ticket.title}
        </Text>
        {labels.length > 0 ? (
          <View className="flex-row flex-wrap items-center gap-1.5">
            {labels.slice(0, MAX_CARD_LABELS).map((label) => (
              <LabelChip key={label.id} name={label.name} colour={label.color} />
            ))}
            {labels.length > MAX_CARD_LABELS ? (
              <Text variant="caption" className="text-muted-foreground">
                {`+${labels.length - MAX_CARD_LABELS}`}
              </Text>
            ) : null}
          </View>
        ) : null}
        {foot.length > 0 ? (
          <View className="min-h-[22px] flex-row items-center gap-3">{foot.map(footItem)}</View>
        ) : null}
        {createdBy ? (
          <Text variant="caption" className="text-muted-foreground" numberOfLines={1}>
            {createdBy}
          </Text>
        ) : null}
      </Card>
    </Pressable>
  );
}

const TicketCard = React.memo(TicketCardBase);

export { TicketCard };
export type { TicketCardProps, TicketCardTiming };
