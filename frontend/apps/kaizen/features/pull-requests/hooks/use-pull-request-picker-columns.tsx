import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useFormatter, useNow, useTranslations } from "next-intl";
import { chatPastTime } from "@/features/chat/components/chat-thread-summary";
import { GithubPullRequestStateBadge } from "../components/github-pull-request-state-badge";
import type { GithubPullRequestSummaryResponse } from "../schemas";

/** Relative times in the list move on once a minute. */
const RELATIVE_TIME_REFRESH_MS = 60_000;

/**
 * Columns of the pull request picker: number, title taking the free width,
 * GitHub state, GitHub author and the time of the last update.
 */
export function usePullRequestPickerColumns() {
  const t = useTranslations("pullRequests.picker.columns");
  const format = useFormatter();
  const now = useNow({ updateInterval: RELATIVE_TIME_REFRESH_MS });

  return useMemo<ColumnDef<GithubPullRequestSummaryResponse, unknown>[]>(
    () => [
      {
        accessorKey: "number",
        header: t("number"),
        cell: ({ row }) => (
          <span className="text-muted-foreground text-xs font-medium tabular-nums whitespace-nowrap">
            #{row.original.number}
          </span>
        ),
      },
      {
        accessorKey: "title",
        header: t("title"),
        cell: ({ row }) => (
          <span className="text-foreground block truncate text-sm" title={row.original.title}>
            {row.original.title}
          </span>
        ),
        meta: { flex: true },
      },
      {
        accessorKey: "state",
        header: t("state"),
        cell: ({ row }) => <GithubPullRequestStateBadge state={row.original.state} />,
      },
      {
        accessorKey: "authorLogin",
        header: t("author"),
        cell: ({ row }) => (
          <span className="text-muted-foreground block max-w-32 truncate text-xs">{row.original.authorLogin}</span>
        ),
      },
      {
        accessorKey: "updatedAt",
        header: t("updated"),
        cell: ({ row }) => (
          <time
            dateTime={row.original.updatedAt}
            className="text-muted-foreground text-xs whitespace-nowrap"
            suppressHydrationWarning
          >
            {format.relativeTime(chatPastTime(row.original.updatedAt, now), now)}
          </time>
        ),
      },
    ],
    [t, format, now],
  );
}
