import { onlineManager } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { Trash2 } from "lucide-react-native";
import * as React from "react";
import { Alert, Pressable, View } from "react-native";

import {
  DashSection,
  SectionEmpty,
  SectionError,
  SectionSkeleton,
} from "@/components/home/DashSection";
import { pruneSelection, toggleSelection } from "@/components/home/homeModel";
import { useHomeLinks } from "@/components/home/homeNavigation";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { IconButton } from "@/components/ui/icon-button";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import type { BulkResult, TicketTarget } from "@/hooks/queries/bulkResult";
import { useHomeStaleBacklog } from "@/hooks/queries/home";
import { useDeleteTickets, useRestoreTickets } from "@/hooks/queries/tickets";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { HomeStaleTicket } from "@/lib/schemas/home";

type StaleBacklogSectionProps = {
  /** The organization role may delete tickets; `canDelete` of each row adds the board rule. */
  canWrite: boolean;
};

function toTarget(ticket: HomeStaleTicket): TicketTarget {
  return { boardId: ticket.boardId, ticketId: ticket.id };
}

/**
 * Backlog tickets nobody touched for 30 days, with a way to clear them: pick
 * rows and delete them together, or delete one from its row. A delete asks
 * first and offers Undo, which restores the tickets.
 */
function StaleBacklogSection({ canWrite }: StaleBacklogSectionProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const query = useHomeStaleBacklog();
  const deleteTickets = useDeleteTickets();
  const restoreTickets = useRestoreTickets();
  const [selection, setSelection] = React.useState<Set<string>>(() => new Set());

  const tickets: HomeStaleTicket[] = React.useMemo(() => query.data?.tickets ?? [], [query.data]);

  // A refetch or a delete drops rows; their ids leave the selection with them.
  React.useEffect(() => {
    setSelection((current) =>
      current.size === 0
        ? current
        : pruneSelection(
            current,
            tickets.map((ticket) => ticket.id)
          )
    );
  }, [tickets]);

  const restore = React.useCallback(
    (targets: TicketTarget[]) => {
      restoreTickets
        .mutateAsync(targets)
        .then((result: BulkResult<TicketTarget>) => {
          if (result.succeeded.length > 0) {
            toast.success(t("homeRestored", { count: result.succeeded.length }));
          }
          if (result.failed.length > 0) {
            toast.error(t("homeRestoreFailed", { count: result.failed.length }));
          }
        })
        .catch((error: unknown) => {
          console.warn("[home] restore failed", error instanceof Error ? error.message : error);
          toast.error(t("homeRestoreFailed", { count: targets.length }));
        });
    },
    [restoreTickets, t]
  );

  const remove = React.useCallback(
    (targets: TicketTarget[]) => {
      deleteTickets
        .mutateAsync(targets)
        .then((result: BulkResult<TicketTarget>) => {
          const deletedIds: Set<string> = new Set(
            result.succeeded.map((target) => target.ticketId)
          );
          setSelection((current) => new Set([...current].filter((id) => !deletedIds.has(id))));
          if (result.succeeded.length > 0) {
            toast.success(t("homeStaleDeleted", { count: result.succeeded.length }), {
              action: { label: t("homeUndo"), onPress: () => restore(result.succeeded) },
            });
          }
          if (result.failed.length > 0) {
            toast.error(t("homeStaleDeleteFailed", { count: result.failed.length }));
          }
        })
        .catch((error: unknown) => {
          console.warn("[home] delete failed", error instanceof Error ? error.message : error);
          toast.error(t("homeStaleDeleteFailed", { count: targets.length }));
        });
    },
    [deleteTickets, restore, t]
  );

  const confirmDelete = React.useCallback(
    (rows: HomeStaleTicket[]) => {
      if (rows.length === 0) return;
      if (!onlineManager.isOnline()) {
        toast.error(t("offlineWriteBlocked"));
        return;
      }
      const count: number = rows.length;
      Alert.alert(t("homeStaleDeleteTitle", { count }), t("homeStaleDeleteBody", { count }), [
        { text: t("kitCancel"), style: "cancel" },
        {
          text: t("homeStaleDelete"),
          style: "destructive",
          onPress: () => remove(rows.map(toTarget)),
        },
      ]);
    },
    [remove, t]
  );

  const deletable = (ticket: HomeStaleTicket): boolean => canWrite && ticket.canDelete;
  const selectedRows: HomeStaleTicket[] = tickets.filter((ticket) => selection.has(ticket.id));

  let body: React.ReactNode;
  if (query.isPending) {
    body = <SectionSkeleton rows={3} />;
  } else if (query.isError && !query.data) {
    body = <SectionError message={t("homeStaleError")} onRetry={() => void query.refetch()} />;
  } else if (tickets.length === 0) {
    body = <SectionEmpty message={t("homeStaleEmpty")} />;
  } else {
    body = (
      <View>
        {selectedRows.length > 0 ? (
          <View className="px-4 pb-1">
            <Button
              variant="destructive"
              size="sm"
              block
              icon={Trash2}
              loading={deleteTickets.isPending}
              onPress={() => confirmDelete(selectedRows)}
            >
              {t("homeStaleDeleteSelected", { count: selectedRows.length })}
            </Button>
          </View>
        ) : null}
        {tickets.map((ticket, index) => (
          <View key={ticket.id}>
            {index > 0 ? <Separator className="mx-4" /> : null}
            <StaleTicketRow
              ticket={ticket}
              deletable={deletable(ticket)}
              selected={selection.has(ticket.id)}
              onToggle={() => setSelection((current) => toggleSelection(current, ticket.id))}
              onDelete={() => confirmDelete([ticket])}
            />
          </View>
        ))}
      </View>
    );
  }

  return (
    <DashSection
      title={t("homeStaleTitle", { count: query.data?.total ?? 0 })}
      onInfo={() => router.push({ pathname: "/home/info/[topic]", params: { topic: "stale" } })}
      infoLabel={t("homeStaleInfo")}
    >
      {body}
    </DashSection>
  );
}

type StaleTicketRowProps = {
  ticket: HomeStaleTicket;
  /** False for a guest or a board viewer: no checkbox and no delete. */
  deletable: boolean;
  selected: boolean;
  onToggle: () => void;
  onDelete: () => void;
};

function StaleTicketRow({ ticket, deletable, selected, onToggle, onDelete }: StaleTicketRowProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const { openTicket } = useHomeLinks();
  const age: string | null = dates.relativePast(ticket.lastActivityAt);
  const meta: string = age ? `${ticket.boardName} · ${age}` : ticket.boardName;

  return (
    <View className="min-h-16 flex-row items-center gap-3 py-2 pl-4 pr-1">
      {deletable ? (
        <Checkbox
          checked={selected}
          onCheckedChange={onToggle}
          accessibilityLabel={t("homeStaleSelectRow", { key: ticket.key })}
        />
      ) : null}
      <Pressable
        accessibilityRole="link"
        accessibilityLabel={`${ticket.key}, ${ticket.title}, ${meta}`}
        onPress={() => openTicket(ticket.key)}
        className="flex-1 gap-0.5 active:opacity-60"
      >
        <Text variant="ticket-key" className="text-link">
          {ticket.key}
        </Text>
        <Text variant="body" numberOfLines={2}>
          {ticket.title}
        </Text>
        <Text variant="caption" className="text-muted-foreground" numberOfLines={1}>
          {meta}
        </Text>
      </Pressable>
      {deletable ? (
        <IconButton
          icon={Trash2}
          label={t("homeStaleDeleteRow", { key: ticket.key })}
          onPress={onDelete}
          iconClassName="text-destructive"
        />
      ) : null}
    </View>
  );
}

export { StaleBacklogSection };
export type { StaleBacklogSectionProps };
