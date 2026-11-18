import { useNavigationState, useRoute } from "@react-navigation/native";
import { useQueryClient } from "@tanstack/react-query";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import {
  ArrowLeft,
  ChevronLeft,
  Ellipsis,
  EllipsisVertical,
  Lock,
  Share,
  Share2,
} from "lucide-react-native";
import * as React from "react";
import { Platform, RefreshControl, View } from "react-native";

import { canWriteBoard } from "@/components/boards/boardModel";
import { chatShareTicketHref } from "@/components/chat/chatShare";
import { ActionSheet } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { useRoleGatedMenu } from "@/components/kazan/useRoleGatedMenu";
import { CommentComposer } from "@/components/ticket/CommentComposer";
import { SubTicketList } from "@/components/ticket/SubTicketList";
import { TicketAttachments } from "@/components/ticket/TicketAttachments";
import { TicketComments } from "@/components/ticket/TicketComments";
import { TicketDetailSkeleton } from "@/components/ticket/TicketDetailSkeleton";
import { TicketForm } from "@/components/ticket/TicketForm";
import { normalizeTicketKey, type CommentDraft } from "@/components/ticket/ticketModel";
import { newTicketHref, ticketExitHref } from "@/components/ticket/ticketRoutes";
import { useMentionPeople } from "@/components/ticket/useMentionPeople";
import { useTicketActions } from "@/components/ticket/useTicketActions";
import { useTicketLinks } from "@/components/ticket/useTicketLinks";
import { IconButton } from "@/components/ui/icon-button";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { attachmentKeys } from "@/hooks/queries/attachments";
import { useMyBoardRoleState } from "@/hooks/queries/boards";
import { useTicketDetail, useTicketRef } from "@/hooks/queries/ticketDetail";
import { ticketKeys } from "@/hooks/queries/tickets";
import { useMe } from "@/hooks/queries/users";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation, type TranslationKey } from "@/i18n";
import { toApiError, type ApiErrorKind } from "@/lib/api";
import { TICKET_COMMENTS_ENABLED } from "@/lib/features";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { Comment } from "@/lib/schemas/tickets";

const TICKET_KEY_TITLE_STYLE = { fontFamily: "JetBrainsMono-SemiBold" } as const;

/**
 * Whether the screen is the first of its stack, with nothing under it for
 * Back to return to. Read from the stack's state, so it holds while a sheet
 * covers the screen and after the sheet closes.
 */
function useIsStackRoot(): boolean {
  const routeKey: string = useRoute().key;
  return useNavigationState((state) => state.routes[0]?.key === routeKey);
}

/** The message of a ticket that could not be shown, by why it failed. */
function failureTitle(kind: ApiErrorKind): TranslationKey {
  if (kind === "forbidden") return "ticketNoAccess";
  if (kind === "not-found") return "ticketNotFound";
  return "ticketLoadError";
}

/**
 * The ticket screen of the `ticketKey` route parameter: header, fields,
 * description, attachments, sub-tickets and comments, with the comment
 * composer pinned under them. Links carry only the key, so the screen first
 * resolves the key to its board and id. Everyone who may open the ticket
 * reads it, its attachments and its comments. Writing, editing or deleting a
 * comment, changing the ticket (status, priority, rank, every field, the
 * description, moves, delete), uploading or deleting an attachment and
 * adding a sub-ticket need write rights on its board, as the API checks: a
 * guest or a board viewer gets no composer, no comment menu and no
 * attachment upload or delete. The Board row opens Move to board for such a user.
 * Sub-tickets are one level deep, so a sub-ticket offers no "Add sub-ticket",
 * and it stays on its parent's board, so its Board row is fixed.
 * While `TICKET_COMMENTS_ENABLED` is off the screen has no comments, no
 * composer and reads neither the comments nor the board members that name
 * their mentions.
 */
export function TicketDetailScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const colors = useThemeColors();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const ticketKey: string | null = normalizeTicketKey(params.ticketKey);

  const orgId: string | null = useActiveOrganizationId();
  const ticketRef = useTicketRef(ticketKey);
  const detail = useTicketDetail(ticketRef.data);
  const meId: string | undefined = useMe(true).data?.id;
  const organizationRole: string | null = useOrganizationRole();
  const people = useMentionPeople(
    TICKET_COMMENTS_ENABLED ? ticketRef.data?.boardId : undefined,
    meId
  );
  const links = useTicketLinks();

  const [menuOpen, setMenuOpen] = React.useState<boolean>(false);
  const [editing, setEditing] = React.useState<Comment | null>(null);
  // The text a new comment starts with after an edit that could not be saved.
  const [draft, setDraft] = React.useState<CommentDraft | null>(null);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const startEditing = React.useCallback((comment: Comment) => {
    setDraft(null);
    setEditing(comment);
  }, []);
  const stopEditing = React.useCallback((keep?: CommentDraft) => {
    setDraft(keep ?? null);
    setEditing(null);
  }, []);

  const boardId: string | undefined = ticketRef.data?.boardId;
  const isStackRoot: boolean = useIsStackRoot();
  // The tabs are not under the screen when it is the first of its stack, so
  // leaving replaces it with the board, anchored on the Boards list.
  const leave = React.useCallback(() => {
    router.replace(ticketExitHref(boardId), { withAnchor: true });
  }, [boardId, router]);
  const goBack = React.useCallback(() => {
    if (router.canGoBack()) router.back();
    else leave();
  }, [leave, router]);

  const boardRole = useMyBoardRoleState(boardId);
  const canWrite: boolean = canWriteBoard(organizationRole, boardRole.role);
  const closeMenu = React.useCallback(() => setMenuOpen(false), []);
  // The menu's write actions depend on the board role.
  const menuVisible: boolean = useRoleGatedMenu({
    requested: menuOpen,
    role: boardRole,
    onCancel: closeMenu,
    logTag: "ticket",
  });
  const actions = useTicketActions({
    ticketKey: ticketKey ?? "",
    ticketRef: ticketRef.data,
    ticket: detail.data,
    canWrite,
    onDeleted: goBack,
  });
  const ticketId: string | undefined = ticketRef.data?.ticketId;
  const refresh = React.useCallback(() => {
    if (!orgId || !boardId || !ticketId) return;
    setRefreshing(true);
    Promise.all([
      queryClient.refetchQueries({ queryKey: ticketKeys.detail(orgId, boardId, ticketId) }),
      queryClient.refetchQueries({ queryKey: attachmentKeys.list(orgId, ticketId) }),
      TICKET_COMMENTS_ENABLED
        ? queryClient.refetchQueries({ queryKey: ticketKeys.comments(orgId, boardId, ticketId) })
        : undefined,
    ])
      .catch((error: unknown) => {
        // The ticket, its attachments and the comments show their own failure
        // with a retry; this only ends the pull.
        console.warn("[ticket] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [boardId, orgId, queryClient, ticketId]);

  const retry = React.useCallback(() => {
    if (ticketRef.isError) void ticketRef.refetch();
    else void detail.refetch();
  }, [detail, ticketRef]);

  const headerLeft = () => (
    <IconButton
      icon={Platform.OS === "ios" ? ChevronLeft : ArrowLeft}
      label={t("ticketBack")}
      onPress={leave}
    />
  );

  const headerRight = () => {
    const shared = detail.data;
    if (!ticketKey || !shared) return null;
    return (
      <View className="flex-row items-center">
        <IconButton
          icon={Platform.OS === "ios" ? Share : Share2}
          label={t("ticketShare")}
          onPress={() =>
            router.push(chatShareTicketHref({ id: shared.id, key: ticketKey, title: shared.title }))
          }
        />
        <IconButton
          icon={Platform.OS === "ios" ? Ellipsis : EllipsisVertical}
          label={t("ticketActions")}
          onPress={() => setMenuOpen(true)}
        />
      </View>
    );
  };

  // The title is the ticket key, shown in the mono face keys use everywhere else.
  const screenOptions = (
    <Stack.Screen
      options={{
        title: ticketKey ?? "",
        headerTitleStyle: { ...TICKET_KEY_TITLE_STYLE, color: colors.foreground },
        headerLeft: isStackRoot ? headerLeft : undefined,
        headerRight,
      }}
    />
  );

  if (!ticketKey) {
    return (
      <View className="flex-1 bg-screen">
        {screenOptions}
        <EmptyState
          title={t("ticketNotValid")}
          actionLabel={t("ticketBack")}
          onAction={goBack}
          className="mt-16"
        />
      </View>
    );
  }

  const loadError: unknown = ticketRef.error ?? detail.error;
  if (loadError) {
    const kind: ApiErrorKind = toApiError(loadError).kind;
    const noAccess: boolean = kind === "forbidden" || kind === "not-found";
    return (
      <View className="flex-1 bg-screen">
        {screenOptions}
        <EmptyState
          variant={noAccess ? "empty" : "error"}
          icon={noAccess ? Lock : undefined}
          title={t(failureTitle(kind))}
          actionLabel={noAccess ? t("ticketBack") : undefined}
          onAction={noAccess ? goBack : retry}
          className="mt-16"
        />
      </View>
    );
  }

  if (!ticketRef.data || !detail.data) {
    return (
      <View className="flex-1 bg-screen">
        {screenOptions}
        <TicketDetailSkeleton />
      </View>
    );
  }

  const ticket = detail.data;
  return (
    <>
      {screenOptions}
      <TicketForm
        header={{
          ticket,
          onChangeStatus: actions.onChangeStatus,
          onChangePriority: actions.onChangePriority,
          onChangeType: actions.onChangeType,
          onOpenSizing: actions.onOpenSizing,
        }}
        fields={{
          ticket,
          onEditField: actions.onEditField,
          editableFields: actions.editableFields,
          board: {
            name: ticket.board.name,
            note: ticket.parentTicketId ? t("ticketMoveBoardSubTicketHint") : undefined,
            noteTone: "muted",
            onPress: actions.onMoveToBoard,
          },
        }}
        dueCalendar={actions.dueCalendar}
        description={{ description: ticket.description, onEdit: actions.onEditDescription }}
        picker={actions.picker}
        onClosePicker={actions.closePicker}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
        footer={
          TICKET_COMMENTS_ENABLED && canWrite ? (
            <CommentComposer
              key={editing?.id ?? "new"}
              ticketRef={ticketRef.data}
              people={people}
              editing={editing}
              draft={draft}
              onStopEditing={stopEditing}
            />
          ) : null
        }
        overlays={
          <>
            <ActionSheet
              visible={menuVisible}
              onClose={closeMenu}
              title={ticketKey}
              actions={actions.menuActions}
              cancelLabel={t("kitCancel")}
            />
            <ActionSheet
              visible={!!actions.followUp}
              onClose={actions.closeFollowUp}
              title={actions.followUp?.title}
              actions={actions.followUp?.actions ?? []}
              cancelLabel={t("kitCancel")}
            />
          </>
        }
      >
        <TicketAttachments
          ticketId={ticket.id}
          ticketKey={ticketKey}
          description={ticket.description}
          canWrite={canWrite}
        />
        <SubTicketList
          subTickets={ticket.subTickets}
          onOpenTicket={links.openTicket}
          onAddSubTicket={
            canWrite && !ticket.parentTicketId
              ? () => router.push(newTicketHref({ parentKey: ticket.key }))
              : undefined
          }
        />
        {TICKET_COMMENTS_ENABLED ? (
          <TicketComments
            ticketRef={ticketRef.data}
            meId={meId}
            canWrite={canWrite}
            names={people.names}
            editingId={editing?.id ?? null}
            onEdit={startEditing}
          />
        ) : null}
      </TicketForm>
    </>
  );
}
