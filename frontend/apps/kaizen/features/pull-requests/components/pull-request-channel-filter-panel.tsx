"use client";

import { useTranslations } from "next-intl";
import { Checkbox } from "@/components/ui/checkbox";
import { ChatChannelFilterPopover } from "@/features/chat/components/chat-channel-filter-popover";
import type { ChatChannelFilterCategory } from "@/features/chat/components/chat-channel-filter-popover";
import { ChatPersonFilterOptions } from "@/features/chat/components/chat-person-filter-options";
import { cn } from "@/lib/utils";
import {
  countPullRequestChannelFilters,
  usePullRequestChannelFilterParams,
} from "../hooks/use-pull-request-channel-filter-params";
import { resolvePullRequestStatuses } from "../lib/pull-request-channel-filter";
import { PullRequestShareStatusSchema, type PullRequestShareStatus } from "../schemas";
import { PullRequestShareStatusBadge } from "./pull-request-message-card";

/** The categories of the panel; a later filter adds one member, one entry and one options component. */
type PullRequestChannelFilterCategory = "status" | "person";

const STATUS_OPTIONS: readonly PullRequestShareStatus[] = PullRequestShareStatusSchema.options;

const OPTION_ROW_CLASS = "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors";

/**
 * The Status and Person filters of the pull requests channel, behind a funnel
 * badged with the number of filters that narrow the channel. Status keeps the
 * shares with any of the chosen team statuses; Person keeps the messages of
 * the member who shared. The state is the URL's (`prStatuses`, `person`), so
 * a reload or a shared link shows the same messages. Shown to every reader:
 * a filter only narrows what the reader already sees.
 */
export function PullRequestChannelFilterPanel({ channelId }: { channelId: string }) {
  const t = useTranslations("pullRequests.filters");
  const tChatFilters = useTranslations("chat.filters");
  const [filters, setFilters] = usePullRequestChannelFilterParams();

  const statuses = resolvePullRequestStatuses(filters.prStatuses);
  const activeCount = countPullRequestChannelFilters(filters);

  // Written in the order of the status enum, and as no key at all when none is left.
  const toggleStatus = (status: PullRequestShareStatus) => {
    const chosen = statuses.includes(status) ? statuses.filter((value) => value !== status) : [...statuses, status];
    const ordered = STATUS_OPTIONS.filter((value) => chosen.includes(value));
    void setFilters({ prStatuses: ordered.length > 0 ? ordered : null });
  };
  const togglePerson = (userId: string) =>
    void setFilters({ person: filters.person === userId ? null : userId });
  const clearAll = () => void setFilters({ prStatuses: null, person: null });

  const categories: (ChatChannelFilterCategory & { key: PullRequestChannelFilterCategory })[] = [
    { key: "status", label: t("status"), count: statuses.length },
    { key: "person", label: tChatFilters("person"), count: filters.person ? 1 : 0 },
  ];

  const renderOptions = (key: string) =>
    key === "status" ? (
      <div className="flex-1 overflow-y-auto px-3 py-2">
        <StatusOptions chosen={statuses} onToggle={toggleStatus} />
      </div>
    ) : (
      <ChatPersonFilterOptions channelId={channelId} chosen={filters.person} onToggle={togglePerson} />
    );

  return (
    <ChatChannelFilterPopover
      activeCount={activeCount}
      categories={categories}
      renderOptions={renderOptions}
      onClearAll={clearAll}
    />
  );
}

function StatusOptions({
  chosen,
  onToggle,
}: {
  chosen: PullRequestShareStatus[];
  onToggle: (status: PullRequestShareStatus) => void;
}) {
  return (
    <div className="space-y-1">
      {STATUS_OPTIONS.map((status) => (
        <label key={status} className={cn(OPTION_ROW_CLASS, "hover:bg-muted/50 cursor-pointer")}>
          <Checkbox checked={chosen.includes(status)} onCheckedChange={() => onToggle(status)} />
          <PullRequestShareStatusBadge status={status} />
        </label>
      ))}
    </div>
  );
}
