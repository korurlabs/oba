import {
  CalendarDays,
  CornerDownRight,
  Flag,
  FolderOpen,
  Kanban,
  Repeat,
  Tag,
  User,
  Users,
} from "lucide-react-native";
import { View } from "react-native";

import { dueState, type DueState } from "@/components/home/homeModel";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { createdWhen } from "@/components/kaizen/ticketCardModel";
import type { TicketField, TicketFieldsValue } from "@/components/ticket/ticketFieldsModel";
import { personName, reporterName } from "@/components/ticket/ticketModel";
import { AvatarStack } from "@/components/ui/avatar";
import { Text } from "@/components/ui/text";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

export type { TicketField } from "@/components/ticket/ticketFieldsModel";

/** The board row: where a new ticket is created, or the board a ticket is on. */
export interface TicketBoardField {
  /** The board's name; empty while the boards load. */
  name: string | undefined;
  /** A note under the row, such as that the board is read-only. */
  note?: string;
  /** `muted` for a plain hint, such as why a sub-ticket's board is fixed. Default `destructive`. */
  noteTone?: "muted" | "destructive";
  /** Opens the board picker or the move to another board; without it the board is fixed. */
  onPress?: () => void;
}

type TicketFieldsProps = {
  ticket: TicketFieldsValue;
  /** Opens the picker of a field; without it every field is read-only. */
  onEditField?: (field: TicketField) => void;
  /** The fields that open a picker; the default is every field when `onEditField` is set. */
  editableFields?: readonly TicketField[];
  /** The ticket's board, as the first row. */
  board?: TicketBoardField;
  /** The parent of a new sub-ticket. It lands where its parent is, so there is no sprint row. */
  parentKey?: string | null;
  /** Messages under rows, such as a missing required field or one a create request was refused for. */
  errors?: Partial<Record<TicketField, string>>;
  /** The "Created by" row, who created the ticket and when; a new ticket has none yet. Default true. */
  showReporter?: boolean;
};

/**
 * The ticket's fields as a list: assignees, labels, sprint, milestone,
 * project, due date and who created it when. That row is never editable. A due
 * day before today shows in the late colour until the ticket is done. A row
 * with an error shows its title and message in the destructive colour. The
 * board, when given, is the first row; a new ticket shows the same rows from
 * its draft. The mission rank is a chip in the header.
 */
export function TicketFields({
  ticket,
  onEditField,
  editableFields,
  board,
  parentKey,
  errors,
  showReporter = true,
}: TicketFieldsProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const unknown: string = t("ticketUnknownPerson");
  const none: string = t("ticketNone");

  const people = (ticket.assignees ?? []).map((person) => ({ name: personName(person, unknown) }));
  const labels: string = (ticket.labels ?? []).map((label) => label.name).join(", ");
  const due: DueState | null = dueState(ticket.dueDate);
  const dueLabel: string | null = due
    ? due.today
      ? t("dateToday")
      : dates.shortDate(due.day)
    : null;
  const late: boolean = !!due && (due.overdue || due.today) && ticket.status !== "DONE";
  const edit = (field: TicketField) =>
    onEditField && (!editableFields || editableFields.includes(field))
      ? () => onEditField(field)
      : undefined;
  const stateOf = (field: TicketField) =>
    errors?.[field]
      ? { subtitle: errors[field], subtitleTone: "destructive" as const, invalid: true, wrap: true }
      : {};

  return (
    <ListSection>
      {board ? (
        <ListRow
          icon={Kanban}
          title={t("newTicketBoard")}
          value={board.name}
          subtitle={board.note}
          subtitleTone={board.note ? (board.noteTone ?? "destructive") : undefined}
          onPress={board.onPress}
        />
      ) : null}
      {parentKey ? (
        <ListRow
          icon={CornerDownRight}
          title={t("newTicketParent")}
          subtitle={t("newTicketParentHint")}
          value={parentKey}
        />
      ) : null}
      <ListRow
        icon={Users}
        title={t("ticketFieldAssignees")}
        value={people.length === 0 ? t("ticketUnassigned") : undefined}
        trailing={people.length > 0 ? <AvatarStack people={people} size="sm" /> : undefined}
        accessibilityHint={
          people.length > 0 ? people.map((person) => person.name).join(", ") : undefined
        }
        onPress={edit("assignees")}
        {...stateOf("assignees")}
      />
      <ListRow
        icon={Tag}
        title={t("ticketFieldLabels")}
        value={labels || none}
        onPress={edit("labels")}
        {...stateOf("labels")}
      />
      {parentKey ? null : (
        <ListRow
          icon={Repeat}
          title={t("ticketFieldSprint")}
          value={ticket.sprint?.name ?? t("ticketBacklog")}
          onPress={edit("sprint")}
          {...stateOf("sprint")}
        />
      )}
      <ListRow
        icon={Flag}
        title={t("ticketFieldMilestone")}
        value={ticket.milestone?.name ?? none}
        onPress={edit("milestone")}
        {...stateOf("milestone")}
      />
      <ListRow
        icon={FolderOpen}
        title={t("ticketFieldProject")}
        value={ticket.project?.name ?? none}
        onPress={edit("project")}
        {...stateOf("project")}
      />
      <ListRow
        icon={CalendarDays}
        title={t("ticketFieldDueDate")}
        value={dueLabel ? undefined : none}
        trailing={
          dueLabel ? (
            <View className="flex-row items-center">
              <Text
                variant="body"
                className={cn(late ? "text-destructive" : "text-muted-foreground")}
              >
                {dueLabel}
              </Text>
            </View>
          ) : undefined
        }
        accessibilityHint={dueLabel ?? undefined}
        onPress={edit("dueDate")}
        {...stateOf("dueDate")}
      />
      {showReporter ? (
        <ListRow
          icon={User}
          title={t("ticketFieldCreatedBy")}
          value={[
            reporterName(ticket, unknown),
            ticket.createdAt ? createdWhen(ticket.createdAt, dates, t) : null,
          ]
            .filter(Boolean)
            .join(" · ")}
        />
      ) : null}
    </ListSection>
  );
}
