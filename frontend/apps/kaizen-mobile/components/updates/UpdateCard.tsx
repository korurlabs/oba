import * as Clipboard from "expo-clipboard";
import { Check, Copy, Info, Pencil, Trash2 } from "lucide-react-native";
import * as React from "react";
import { Alert, Pressable, View } from "react-native";

import {
  ChatReactions,
  ChatThreadSummary,
  type ChatMessageRowProps,
} from "@/components/chat/ChatMessageRow";
import { MissionRank } from "@/components/kaizen/TicketMeta";
import {
  canManageUpdate,
  updateAuthorName,
  updateTextLines,
} from "@/components/updates/updateModel";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useDates } from "@/hooks/useDates";
import { useDeleteUpdate } from "@/hooks/queries/updates";
import { useTranslation } from "@/i18n";
import type { ChatMessageResponse } from "@/lib/schemas/chat";
import type {
  UpdateFinishedTicketResponse,
  UpdateResponse,
  UpdateTicketResponse,
} from "@/lib/schemas/updates";
import { cn } from "@/lib/utils";

export interface UpdateCardProps {
  /** The chat message that carries the update. */
  message: ChatMessageResponse;
  update: UpdateResponse;
  meId: string | undefined;
  /** Opens a ticket the reader may open. */
  onOpenTicket: (ticketKey: string) => void;
  /** Opens the author's edit form. */
  onEdit: (update: UpdateResponse, messageId: string) => void;
  /** Opens the message's actions; without it a long press does nothing. */
  onLongPress?: (message: ChatMessageResponse) => void;
  /** Opens the thread under the update; without it the summary only shows. */
  onOpenThread?: (threadId: string) => void;
  /** Unread replies in the update's thread that notify the reader. */
  threadMentionCount?: number;
  /** Toggles the reader's reaction; without it the reactions only show. */
  onReactionPress?: ChatMessageRowProps["onReactionPress"];
}

function Section({
  title,
  info,
  children,
}: {
  title: string;
  /** A small info button after the title. */
  info?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <View className="gap-1">
      <View className="flex-row items-center gap-1">
        <Text
          variant="caption"
          accessibilityRole="header"
          className="shrink font-sans-semibold text-muted-foreground"
        >
          {title}
        </Text>
        {info}
      </View>
      {children}
    </View>
  );
}

const SIZING_LINES = [
  "updSizingXS",
  "updSizingS",
  "updSizingM",
  "updSizingL",
  "updSizingXL",
  "updSizingXXL",
] as const;

/** Explains the ticket sizes that score the finished tickets, as the web's tip does. */
function SizingInfo() {
  const { t } = useTranslation();
  const show = (): void => {
    Alert.alert(t("updSizingLabel"), SIZING_LINES.map((key) => t(key)).join("\n"));
  };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={t("updSizingLabel")}
      onPress={show}
      hitSlop={{ top: 14, bottom: 14, left: 10, right: 14 }}
      className="active:opacity-60"
    >
      <Icon as={Info} size={13} className="text-muted-foreground" />
    </Pressable>
  );
}

function None() {
  const { t } = useTranslation();
  return (
    <Text variant="footnote" className="italic text-muted-foreground">
      {t("updCardNone")}
    </Text>
  );
}

/** Each non-blank line as a bullet, as in the copied text. */
function Bullets({ text }: { text: string | null | undefined }) {
  const lines: string[] = updateTextLines(text);
  if (lines.length === 0) return <None />;
  return (
    <View className="gap-0.5">
      {lines.map((line, index) => (
        <Text key={index} variant="footnote">
          {`• ${line}`}
        </Text>
      ))}
    </View>
  );
}

function TicketKey({ ticketKey, canOpen }: { ticketKey: string; canOpen: boolean }) {
  return (
    <Text
      variant="caption"
      className={cn("font-mono-medium", canOpen ? "text-link" : "text-foreground")}
    >
      {ticketKey}
    </Text>
  );
}

/**
 * A ticket's line: the whole line opens the ticket when the reader may see
 * it, not only its short key. A long press on it is the card's long press, so
 * holding a ticket line opens the message actions rather than the ticket.
 */
function TicketLink({
  ticketKey,
  canOpen,
  onOpenTicket,
  onLongPress,
  children,
}: {
  ticketKey: string;
  canOpen: boolean;
  onOpenTicket: (ticketKey: string) => void;
  onLongPress: (() => void) | undefined;
  children: React.ReactNode;
}) {
  return (
    <Pressable
      disabled={!canOpen}
      onPress={() => onOpenTicket(ticketKey)}
      onLongPress={onLongPress}
      delayLongPress={350}
      accessibilityRole={canOpen ? "link" : "text"}
      className="min-h-6 flex-row flex-wrap items-center gap-x-2 gap-y-0.5 active:opacity-60"
    >
      {children}
    </Pressable>
  );
}

/** The ticket's board as an outline chip, as on the web card. */
function BoardChip({ name }: { name: string }) {
  return (
    <View className="max-w-[40%] rounded-full border border-border px-1.5">
      <Text variant="caption" numberOfLines={1} className="text-muted-foreground">
        {name}
      </Text>
    </View>
  );
}

/** Board, key and title, then how far it is and how big, then the author's note. */
function TicketLine({
  item,
  onOpenTicket,
  onLongPress,
}: {
  item: UpdateTicketResponse;
  onOpenTicket: (ticketKey: string) => void;
  onLongPress: (() => void) | undefined;
}) {
  const { t } = useTranslation();
  const noteLines: string[] = updateTextLines(item.note);
  const canOpen: boolean = item.canOpen && item.ticketId !== null;
  return (
    <View className="gap-1">
      <TicketLink
        ticketKey={item.ticketKey}
        canOpen={canOpen}
        onOpenTicket={onOpenTicket}
        onLongPress={onLongPress}
      >
        <BoardChip name={item.boardName} />
        <TicketKey ticketKey={item.ticketKey} canOpen={canOpen} />
        <Text variant="footnote" className="shrink">
          {item.ticketTitle}
        </Text>
      </TicketLink>
      <View className="flex-row flex-wrap items-center gap-2">
        <Text variant="footnote" className="font-sans-medium">{`${item.percentage}%`}</Text>
        <MissionRank rank={item.missionRank} />
        {item.progressSynced ? (
          <View
            accessible
            accessibilityLabel={t("updCardProgressSyncedA11y")}
            className="flex-row items-center gap-1"
          >
            <Icon as={Check} size={12} className="text-muted-foreground" />
            <Text variant="caption" className="text-muted-foreground">
              {t("updCardProgressSynced")}
            </Text>
          </View>
        ) : null}
      </View>
      {noteLines.map((line, index) => (
        <Text key={index} variant="caption" className="pl-3 text-muted-foreground">
          {`• ${line}`}
        </Text>
      ))}
    </View>
  );
}

function FinishedLine({
  ticket,
  onOpenTicket,
  onLongPress,
}: {
  ticket: UpdateFinishedTicketResponse;
  onOpenTicket: (ticketKey: string) => void;
  onLongPress: (() => void) | undefined;
}) {
  return (
    // The server lists only tickets on boards the reader is a member of, so the line always opens.
    <TicketLink
      ticketKey={ticket.ticketKey}
      canOpen
      onOpenTicket={onOpenTicket}
      onLongPress={onLongPress}
    >
      <TicketKey ticketKey={ticket.ticketKey} canOpen />
      <Text variant="footnote" className="shrink">
        {ticket.ticketTitle}
      </Text>
      <MissionRank rank={ticket.missionRank} />
    </TicketLink>
  );
}

/** Copy for anyone; Edit and Delete for the author. */
function CardActions({
  update,
  messageId,
  manage,
  onEdit,
}: {
  update: UpdateResponse;
  messageId: string;
  manage: boolean;
  onEdit: UpdateCardProps["onEdit"];
}) {
  const { t } = useTranslation();
  const remove = useDeleteUpdate();

  const copy = (): void => {
    Clipboard.setStringAsync(update.summaryText)
      .then(() => toast.success(t("updCardCopied")))
      .catch((error: unknown) => {
        console.warn("[updates] copying failed", error instanceof Error ? error.message : error);
        toast.error(t("chatCopyFailed"));
      });
  };

  // A refused delete is toasted by the app's error listener.
  const confirmDelete = (): void => {
    Alert.alert(t("updDeleteTitle"), t("updDeleteBody"), [
      { text: t("kitCancel"), style: "cancel" },
      {
        text: t("updCardDelete"),
        style: "destructive",
        onPress: () =>
          remove.mutate(
            { updateId: update.id, messageId },
            { onSuccess: () => toast.success(t("updDeleted")) }
          ),
      },
    ]);
  };

  return (
    <View className="flex-row flex-wrap items-center gap-1 border-t border-border pt-2">
      <Button variant="ghost" size="sm" icon={Copy} onPress={copy}>
        {t("updCardCopy")}
      </Button>
      {manage ? (
        <>
          <Button variant="ghost" size="sm" icon={Pencil} onPress={() => onEdit(update, messageId)}>
            {t("updCardEdit")}
          </Button>
          <Button variant="ghost" size="sm" loading={remove.isPending} onPress={confirmDelete}>
            <Icon as={Trash2} size={16} className="text-destructive" />
            <Text className="text-destructive">{t("updCardDelete")}</Text>
          </Button>
        </>
      ) : null}
    </View>
  );
}

/**
 * A daily update in the channel: who posted it and when, then its four
 * parts (the tickets with their progress and notes, today's focus, the
 * tickets finished this sprint, questions), the card's actions, and the
 * reactions and thread under it. The reader's own update sits on the right
 * on `primary-light` without an avatar, as the reader's messages do.
 */
function UpdateCardImpl({
  message,
  update,
  meId,
  onOpenTicket,
  onEdit,
  onLongPress,
  onOpenThread,
  threadMentionCount = 0,
  onReactionPress,
}: UpdateCardProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const author: string = updateAuthorName(update.author, t("chatUnknownMember"));
  const time: string | null = dates.time(update.createdAt);
  const own: boolean = canManageUpdate(update, meId);
  const longPress: (() => void) | undefined = onLongPress ? () => onLongPress(message) : undefined;

  return (
    <Pressable
      onLongPress={longPress}
      delayLongPress={350}
      className={cn("flex-row gap-3 px-4 pt-3", own && "pl-12")}
    >
      {own ? null : (
        <View className="w-8">
          <Avatar name={author} size="md" />
        </View>
      )}
      <View className="flex-1 gap-1 pb-0.5">
        <View className={cn("flex-row items-baseline gap-2", own && "flex-row-reverse")}>
          {own ? null : (
            <Text variant="subhead" numberOfLines={1} className="shrink font-sans-semibold">
              {author}
            </Text>
          )}
          {time ? (
            <Text variant="caption" className="text-muted-foreground">
              {time}
            </Text>
          ) : null}
          {update.editedAt ? (
            <Text variant="caption" className="text-muted-foreground">
              {`(${t("chatEdited")})`}
            </Text>
          ) : null}
        </View>
        <View
          accessibilityLabel={t("updCardA11y", { name: author })}
          className={cn(
            "gap-3 rounded-lg border p-3",
            own ? "border-primary-medium bg-primary-light" : "border-border bg-card"
          )}
        >
          <Section title={t("updCardTickets")}>
            <View className="gap-2">
              {update.items.map((item, index) => (
                <TicketLine
                  key={item.ticketId ?? `line-${index}`}
                  item={item}
                  onOpenTicket={onOpenTicket}
                  onLongPress={longPress}
                />
              ))}
            </View>
          </Section>
          <Section title={t("updCardFocus")}>
            <Bullets text={update.focus} />
          </Section>
          <Section title={t("updCardFinished")} info={<SizingInfo />}>
            {update.finishedTickets.length === 0 ? (
              <None />
            ) : (
              <View className="gap-1">
                {update.finishedTickets.map((ticket) => (
                  <FinishedLine
                    key={ticket.ticketId}
                    ticket={ticket}
                    onOpenTicket={onOpenTicket}
                    onLongPress={longPress}
                  />
                ))}
              </View>
            )}
          </Section>
          <Section title={t("updCardQuestions")}>
            <Bullets text={update.questions} />
          </Section>
          <CardActions update={update} messageId={message.id} manage={own} onEdit={onEdit} />
        </View>
        <ChatReactions
          message={message}
          onReactionPress={onReactionPress}
          className={cn("mt-0.5", own && "justify-end")}
        />
        <ChatThreadSummary
          message={message}
          mentionCount={threadMentionCount}
          onOpenThread={onOpenThread}
          className="mt-0.5"
        />
      </View>
    </Pressable>
  );
}

export const UpdateCard = React.memo(UpdateCardImpl);
