"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import { AlertCircle, ChevronDown, Copy, Loader2 } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@oba/ui/components/collapsible";
import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useUpdateSeedTickets } from "../hooks/use-update-seed-tickets";
import { usePreviousUpdate } from "../hooks/use-updates";
import { updateDayOf } from "../lib/update-day";
import type { UpdateResponse } from "../schemas";
import { UpdateCardBody } from "./update-card-body";

interface PreviousUpdatePanelProps {
  /**
   * Called by "Copy to today" with the previous update and the current
   * tickets of its lines, by key. Keys that did not resolve are absent from
   * the map and listed in `unresolvedKeys`.
   */
  onCopy: (
    previous: UpdateResponse,
    ticketsByKey: ReadonlyMap<string, TicketListResponse>,
    unresolvedKeys: string[],
  ) => void;
}

/**
 * The author's most recent update before today, read-only, beside today's
 * form, so today's update can be written from it. The current tickets of its
 * lines are loaded as soon as it shows, so a copied line starts at the
 * ticket's progress of now rather than the percentage posted back then.
 * Loading or failing here never blocks the form.
 */
export function PreviousUpdatePanel({ onCopy }: PreviousUpdatePanelProps) {
  const t = useTranslations("updates.form.previous");
  const locale = useLocale();
  const { data: meResponse } = useGetMe();
  const previousQuery = usePreviousUpdate(meResponse?.data?.id);
  const previous = previousQuery.data?.data?.[0] ?? null;

  // Only lines whose ticket still exists and can be opened are worth
  // resolving; the others are left out of a copy whatever the server says.
  const ticketKeys = useMemo(
    () =>
      (previous?.items ?? [])
        .filter((item) => item.ticketId !== null && item.canOpen)
        .map((item) => item.ticketKey),
    [previous],
  );
  const seed = useUpdateSeedTickets(ticketKeys);

  const handleCopy = () => {
    if (!previous) return;
    const ticketsByKey = new Map<string, TicketListResponse>();
    seed.tickets.forEach((ticket) => ticketsByKey.set(ticket.key, ticket));
    onCopy(
      previous,
      ticketsByKey,
      seed.unresolved.map(({ ticketKey }) => ticketKey),
    );
  };

  return (
    <Collapsible defaultOpen asChild>
      <section
        aria-labelledby="previous-update-title"
        className="border-border bg-card rounded-lg border"
      >
        <div className="border-border flex flex-wrap items-center gap-2 border-b px-4 py-2">
          <CollapsibleTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="group -ml-2 h-7 w-7 p-0"
              aria-label={t("toggle")}
            >
              <ChevronDown className="h-4 w-4 transition-transform group-data-[state=closed]:-rotate-90" />
            </Button>
          </CollapsibleTrigger>
          <h2 id="previous-update-title" className="text-foreground text-sm font-semibold">
            {t("title")}
          </h2>
          {previous && (
            <span className="text-muted-foreground text-xs">
              {formatDateOnly(updateDayOf(previous.createdAt), locale)}
            </span>
          )}
          {previous && (
            // type="button": the panel never posts; only Confirm does.
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="ml-auto"
              title={t("copyHint")}
              disabled={seed.isLoading}
              onClick={handleCopy}
            >
              {seed.isLoading ? (
                <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
              ) : (
                <Copy className="mr-1.5 h-4 w-4" />
              )}
              {t("copy")}
            </Button>
          )}
        </div>

        <CollapsibleContent>
          <div className="px-4 py-3 xl:max-h-[50vh] xl:overflow-auto">
            {previousQuery.isError ? (
              <InlineAlert
                variant="danger"
                icon={<AlertCircle />}
                description={
                  <div className="flex flex-col items-start gap-2">
                    <span className="text-foreground font-medium">{t("loadError")}</span>
                    {previousQuery.error.message && (
                      <span className="text-xs">{previousQuery.error.message}</span>
                    )}
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => void previousQuery.refetch()}
                    >
                      {t("retry")}
                    </Button>
                  </div>
                }
              />
            ) : previousQuery.isPending ? (
              <div className="flex items-center gap-2 py-2">
                <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" />
                <span className="text-muted-foreground text-sm">{t("loading")}</span>
              </div>
            ) : previous ? (
              <div className="text-sm">
                <UpdateCardBody data={previous} />
              </div>
            ) : (
              <p className="text-muted-foreground text-sm">{t("empty")}</p>
            )}
          </div>
        </CollapsibleContent>
      </section>
    </Collapsible>
  );
}
