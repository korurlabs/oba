import * as Clipboard from "expo-clipboard";
import {
  Check,
  CircleCheck,
  Copy,
  ExternalLink,
  GitBranch,
  Pencil,
  Trash2,
  TriangleAlert,
  Undo2,
} from "lucide-react-native";
import * as React from "react";
import { Alert, Pressable, View } from "react-native";

import { chatAuthorName, isOwnChatMessage } from "@/components/chat/chatDisplay";
import {
  ChatReactions,
  ChatThreadSummary,
  type ChatMessageRowProps,
} from "@/components/chat/ChatMessageRow";
import { PullRequestStatusPill } from "@/components/pull-requests/PullRequestPills";
import {
  pullRequestAwaitsMyReview,
  pullRequestCardRights,
  reviewerName,
  type PullRequestCardRights,
} from "@/components/pull-requests/pullRequestModel";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useDates } from "@/hooks/useDates";
import { useDeletePullRequestShare, useReviewPullRequestShare } from "@/hooks/queries/pullRequests";
import { useTranslation } from "@/i18n";
import type { ChatMessageResponse } from "@/lib/schemas/chat";
import type {
  PullRequestShareResponse,
  PullRequestShareReviewerResponse,
  PullRequestShareTicketResponse,
} from "@/lib/schemas/pullRequests";
import { cn } from "@/lib/utils";

export interface PullRequestCardProps {
  /** The chat message that carries the share. */
  message: ChatMessageResponse;
  share: PullRequestShareResponse;
  meId: string | undefined;
  /** Opens a ticket the reader may open. */
  onOpenTicket: (ticketKey: string) => void;
  /** Opens the pull request on GitHub. */
  onOpenLink: (url: string) => void;
  /** Opens the author's edit form. */
  onEdit: (share: PullRequestShareResponse, messageId: string) => void;
  /** Opens the message's actions; without it a long press does nothing. */
  onLongPress?: (message: ChatMessageResponse) => void;
  /** Toggles the reader's reaction; without it the reactions only show. */
  onReactionPress?: ChatMessageRowProps["onReactionPress"];
  /** Opens the thread under the share; without it the summary only shows. */
  onOpenThread?: (threadId: string) => void;
  /** Unread replies in the share's thread that notify the reader. */
  threadMentionCount?: number;
}

/** "Open in GitHub" is a caption; the slop brings its target to 44 pt. */
const LINK_HIT_SLOP = { top: 10, bottom: 10, left: 8, right: 8 } as const;

function Label({ children }: { children: string }) {
  return (
    <Text variant="caption" className="font-sans-medium text-muted-foreground">
      {children}
    </Text>
  );
}

function ReviewerChip({ reviewer }: { reviewer: PullRequestShareReviewerResponse }) {
  const { t } = useTranslation();
  const name: string = reviewerName(reviewer, t("chatUnknownMember"));
  const approved: boolean = reviewer.approvedAt !== null;
  return (
    <View
      accessible
      accessibilityLabel={approved ? t("prCardReviewerApprovedA11y", { name }) : name}
      className={cn(
        "min-h-7 flex-row items-center gap-1.5 rounded-full border px-2",
        approved ? "border-success bg-success-light" : "border-border bg-card"
      )}
    >
      <Avatar name={name} size={18} />
      <Text variant="caption" numberOfLines={1}>
        {name}
      </Text>
      {approved ? <Icon as={CircleCheck} size={14} className="text-success" /> : null}
    </View>
  );
}

function RelatedTicket({
  ticket,
  onOpenTicket,
  onLongPress,
}: {
  ticket: PullRequestShareTicketResponse;
  onOpenTicket: (ticketKey: string) => void;
  onLongPress: (() => void) | undefined;
}) {
  return (
    <Pressable
      disabled={!ticket.canOpen}
      accessibilityRole={ticket.canOpen ? "link" : "text"}
      onPress={() => onOpenTicket(ticket.ticketKey)}
      onLongPress={onLongPress}
      delayLongPress={350}
      className="flex-row items-baseline gap-2 active:opacity-60"
    >
      <Text
        variant="caption"
        className={cn("font-mono-medium", ticket.canOpen ? "text-link" : "text-foreground")}
      >
        {ticket.ticketKey}
      </Text>
      <Text variant="footnote" numberOfLines={1} className="flex-1">
        {ticket.ticketTitle}
      </Text>
      <Text variant="caption" numberOfLines={1} className="max-w-[35%] text-muted-foreground">
        {ticket.boardName}
      </Text>
    </Pressable>
  );
}

/** Copy for anyone, Approve or Withdraw for a chosen reviewer, Edit and Delete for the author. */
function CardActions({
  share,
  messageId,
  rights,
  onEdit,
}: {
  share: PullRequestShareResponse;
  messageId: string;
  rights: PullRequestCardRights;
  onEdit: PullRequestCardProps["onEdit"];
}) {
  const { t } = useTranslation();
  const review = useReviewPullRequestShare();
  const remove = useDeletePullRequestShare();

  const copy = (): void => {
    Clipboard.setStringAsync(share.summaryText)
      .then(() => toast.success(t("prCardCopied")))
      .catch((error: unknown) => {
        console.warn(
          "[pull-requests] copying failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("chatCopyFailed"));
      });
  };

  // A refused review or delete is toasted by the app's error listener.
  const setApproval = (approve: boolean): void => {
    review.mutate(
      { shareId: share.id, messageId, approve },
      { onSuccess: () => toast.success(t(approve ? "prApproved" : "prApprovalWithdrawn")) }
    );
  };

  const confirmDelete = (): void => {
    Alert.alert(t("prDeleteTitle"), t("prDeleteBody"), [
      { text: t("kitCancel"), style: "cancel" },
      {
        text: t("prCardDelete"),
        style: "destructive",
        onPress: () =>
          remove.mutate(
            { shareId: share.id, messageId },
            { onSuccess: () => toast.success(t("prDeleted")) }
          ),
      },
    ]);
  };

  return (
    <View className="flex-row flex-wrap items-center gap-1 border-t border-border pt-2">
      <Button variant="ghost" size="sm" icon={Copy} onPress={copy}>
        {t("prCardCopy")}
      </Button>
      {rights.review === "approve" ? (
        <Button
          variant="outline"
          size="sm"
          icon={Check}
          loading={review.isPending}
          onPress={() => setApproval(true)}
        >
          {t("prCardApprove")}
        </Button>
      ) : rights.review === "withdraw" ? (
        <Button
          variant="ghost"
          size="sm"
          icon={Undo2}
          loading={review.isPending}
          onPress={() => setApproval(false)}
        >
          {t("prCardWithdraw")}
        </Button>
      ) : null}
      {rights.manage ? (
        <>
          <Button variant="ghost" size="sm" icon={Pencil} onPress={() => onEdit(share, messageId)}>
            {t("prCardEdit")}
          </Button>
          <Button variant="ghost" size="sm" loading={remove.isPending} onPress={confirmDelete}>
            <Icon as={Trash2} size={16} className="text-destructive" />
            <Text className="text-destructive">{t("prCardDelete")}</Text>
          </Button>
        </>
      ) : null}
    </View>
  );
}

/**
 * A share in the pull requests channel: who shared it and when, the facts
 * GitHub gave at share time (ticket key, title, repository and number, the
 * GitHub author, branches), the team's status and note, related tickets,
 * reviewers with their approvals, and the card's actions; reactions and the
 * thread follow under the card. A card that waits on the reader's review is
 * highlighted, so it is found while scrolling.
 */
function PullRequestCardImpl({
  message,
  share,
  meId,
  onOpenTicket,
  onOpenLink,
  onEdit,
  onLongPress,
  onReactionPress,
  onOpenThread,
  threadMentionCount = 0,
}: PullRequestCardProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const author: string = chatAuthorName(message, t("chatUnknownMember"));
  const time: string | null = dates.time(message.createdAt);
  const rights: PullRequestCardRights = pullRequestCardRights(share, meId);
  const awaitsMe: boolean = pullRequestAwaitsMyReview(share, meId);
  const own: boolean = isOwnChatMessage(message, meId);
  const linked = share.linkedTicket;
  // The links inside the card take the card's long press too: a link without
  // one would open on release after a hold instead of the message actions.
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
          {share.editedAt ? (
            <Text variant="caption" className="text-muted-foreground">
              {`(${t("chatEdited")})`}
            </Text>
          ) : null}
        </View>
        <View
          accessibilityLabel={t("prCardA11y", { name: author })}
          className={cn(
            "gap-2.5 rounded-lg border p-3",
            awaitsMe
              ? "border-warning bg-warning-light"
              : own
                ? "border-primary-medium bg-primary-light"
                : "border-border bg-card"
          )}
        >
          {awaitsMe ? (
            <View className="flex-row items-center gap-1.5">
              <Icon as={TriangleAlert} size={14} className="text-warning" />
              <Text variant="caption" className="font-sans-semibold text-foreground">
                {t("prCardAwaitsYou")}
              </Text>
            </View>
          ) : null}
          <View className="gap-1">
            {/* The whole title line opens the linked ticket, not only its short key. */}
            <Pressable
              disabled={!linked}
              accessibilityRole={linked ? "link" : "text"}
              onPress={linked ? () => onOpenTicket(linked.ticketKey) : undefined}
              onLongPress={longPress}
              delayLongPress={350}
              className="active:opacity-60"
            >
              <Text variant="subhead" className="font-sans-semibold">
                {share.ticketKey ? (
                  <Text
                    variant="subhead"
                    className={cn("font-mono-medium", linked ? "text-link" : "text-foreground")}
                  >
                    {`${share.ticketKey}: `}
                  </Text>
                ) : null}
                {share.title}
              </Text>
            </Pressable>
            <View className="flex-row flex-wrap items-center gap-x-2 gap-y-1">
              <Text variant="caption" className="text-muted-foreground">
                {`${share.repositoryFullName} #${share.number}`}
              </Text>
              <Text variant="caption" className="text-muted-foreground">
                {t("prCardByLogin", { login: share.githubAuthorLogin })}
              </Text>
              <Pressable
                accessibilityRole="link"
                onPress={() => onOpenLink(share.url)}
                onLongPress={longPress}
                delayLongPress={350}
                hitSlop={LINK_HIT_SLOP}
                className="flex-row items-center gap-1 py-1 active:opacity-60"
              >
                <Text variant="caption" className="font-sans-medium text-link">
                  {t("prCardOpenInGithub")}
                </Text>
                <Icon as={ExternalLink} size={12} className="text-link" />
              </Pressable>
            </View>
            <View className="flex-row items-center gap-1">
              <Icon as={GitBranch} size={12} className="text-muted-foreground" />
              <Text
                variant="caption"
                numberOfLines={1}
                className="flex-1 font-mono text-muted-foreground"
              >
                {`${share.headRef} → ${share.baseRef}`}
              </Text>
            </View>
          </View>
          <View className="flex-row flex-wrap items-center gap-2">
            <Label>{t("prCardStatus")}</Label>
            <PullRequestStatusPill status={share.status} />
          </View>
          {share.note ? <Text variant="footnote">{share.note}</Text> : null}
          {share.relatedTickets.length > 0 ? (
            <View className="gap-1">
              <Label>{t("prCardRelatedTickets")}</Label>
              {share.relatedTickets.map((ticket) => (
                <RelatedTicket
                  key={ticket.ticketId}
                  ticket={ticket}
                  onOpenTicket={onOpenTicket}
                  onLongPress={longPress}
                />
              ))}
            </View>
          ) : null}
          <View className="gap-1">
            <Label>{t("prCardReviewers")}</Label>
            {share.reviewers.length > 0 ? (
              <View className="flex-row flex-wrap gap-1.5">
                {share.reviewers.map((reviewer) => (
                  <ReviewerChip key={reviewer.userId} reviewer={reviewer} />
                ))}
              </View>
            ) : (
              <Text variant="caption" className="text-muted-foreground">
                {t("prCardNoReviewers")}
              </Text>
            )}
          </View>
          <CardActions share={share} messageId={message.id} rights={rights} onEdit={onEdit} />
        </View>
        <ChatReactions message={message} onReactionPress={onReactionPress} className="mt-0.5" />
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

export const PullRequestCard = React.memo(PullRequestCardImpl);
