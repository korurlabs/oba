"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Check, ChevronRight } from "lucide-react";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@oba/ui/components/collapsible";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@/components/ui/badge";
import { InfoTip } from "@/components/ui/info-tip";
import { TicketPriorityIcon } from "@/features/tickets/components/ticket-priority-icon";
import { MissionRank, type TicketPriority } from "@/features/tickets/schemas";
import type {
  UpdateCardData,
  UpdateFinishedTicketResponse,
  UpdateTicketResponse,
} from "../schemas";
import { sortByPriority } from "@/features/tickets/lib/ticket-priority-order";
import { UpdateTicketKey } from "./update-ticket-key";

/**
 * The four parts of an update, rendered from the structured fields. The
 * posted card and the posting preview both render this, so the author sees
 * exactly the card the channel will show. Both lists are shown most urgent
 * first and numbered in that order; the data keeps the author's order, which
 * the edit form and the copied text use.
 */
export function UpdateCardBody({ data }: { data: UpdateCardData }) {
  const t = useTranslations("updates");
  const items = useMemo(() => sortByPriority(data.items), [data.items]);
  const finishedTickets = useMemo(
    () => sortByPriority(data.finishedTickets),
    [data.finishedTickets],
  );

  return (
    <div className="kazan-grid-frame space-y-3">
      <UpdateSection title={t("sections.tickets")}>
        <ol className="space-y-1.5">
          {items.map((item, index) => (
            <li key={item.ticketId ?? `line-${index}`} className="flex min-w-0 items-start gap-2">
              <LineNumber value={index + 1} />
              <TicketLine item={item} />
            </li>
          ))}
        </ol>
      </UpdateSection>

      <UpdateSection title={t("sections.focus")} className="kazan-grid-divider">
        <TextBullets text={data.focus} emptyLabel={t("none")} />
      </UpdateSection>

      <FinishedSection tickets={finishedTickets} />

      <UpdateSection title={t("sections.questions")} className="kazan-grid-divider">
        <TextBullets text={data.questions} emptyLabel={t("none")} />
      </UpdateSection>
    </div>
  );
}

// ── Parts ─────────────────────────────────────────────────────────────

function UpdateSection({
  title,
  info,
  className,
  children,
}: {
  title: string;
  /** Shown after the title, as an explanation the heading itself leaves out. */
  info?: ReactNode;
  /** `kazan-grid-divider` on every section after the first. */
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={cn("space-y-1", className)}>
      <h3 className="kazan-grid-label inline-flex items-center gap-1">
        {title}
        {info}
      </h3>
      {children}
    </section>
  );
}

/**
 * Closed by default, so a long sprint does not bury the rest of the update;
 * the count in the heading tells how much is folded away. The open state is
 * per card and lives only as long as the card is shown.
 */
function FinishedSection({ tickets }: { tickets: UpdateFinishedTicketResponse[] }) {
  const t = useTranslations("updates");
  const [open, setOpen] = useState(false);
  const title = t("sections.finishedCount", { count: tickets.length });

  if (tickets.length === 0) {
    return (
      <UpdateSection title={title} info={<UpdateSizingInfo />} className="kazan-grid-divider">
        <p className="text-muted-foreground italic">{t("none")}</p>
      </UpdateSection>
    );
  }

  return (
    <Collapsible open={open} onOpenChange={setOpen} asChild>
      <section className="kazan-grid-divider space-y-1">
        {/* The sizing tip sits outside the trigger, so opening it does not toggle the list. */}
        <h3 className="kazan-grid-label inline-flex items-center gap-1">
          <CollapsibleTrigger asChild>
            <button
              type="button"
              className="hover:text-foreground focus-visible:ring-ring inline-flex items-center gap-1 rounded-sm outline-none focus-visible:ring-2"
            >
              <ChevronRight
                className={cn(
                  "size-3.5 transition-transform motion-reduce:transition-none",
                  open && "rotate-90",
                )}
              />
              {title}
            </button>
          </CollapsibleTrigger>
          <UpdateSizingInfo />
        </h3>
        <CollapsibleContent>
          <ol className="space-y-1">
            {tickets.map((ticket, index) => (
              <li key={ticket.ticketId} className="flex min-w-0 items-start gap-2">
                <LineNumber value={index + 1} />
                <FinishedTicketLine ticket={ticket} />
              </li>
            ))}
          </ol>
        </CollapsibleContent>
      </section>
    </Collapsible>
  );
}

/** The visible position of a line; the `<ol>` already gives it to assistive technology. */
function LineNumber({ value }: { value: number }) {
  return (
    <span aria-hidden className="kazan-grid-number">
      {value}
    </span>
  );
}

/** Title first, then how urgent, how far and how big it is, then the author's note. */
function TicketLine({ item }: { item: UpdateTicketResponse }) {
  const t = useTranslations("updates");
  const noteLines = toLines(item.note);
  return (
    <div className="min-w-0 flex-1">
      <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
        <Badge variant="outline" className="max-w-48 truncate text-[11px] font-medium">
          {item.boardName}
        </Badge>
        <UpdateTicketKey item={item} />
        <span className="text-foreground min-w-0 break-words">{item.ticketTitle}</span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <UpdatePriorityBadge priority={item.priority} />
        <span className="text-foreground text-[13px] tabular-nums">{item.percentage}%</span>
        <UpdateRankBadge rank={item.missionRank} />
        {item.progressSynced && (
          <span
            className="text-muted-foreground inline-flex items-center gap-1 text-xs"
            title={t("progressSyncedHint")}
          >
            <Check className="h-3 w-3" />
            {t("progressSynced")}
          </span>
        )}
      </div>
      {noteLines.length > 0 && (
        <ul className="text-muted-foreground mt-1 ml-4 list-disc space-y-0.5 pl-4 text-[13px]">
          {noteLines.map((line, index) => (
            <li key={index} className="break-words">
              {line}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** The ticket sits on a board the reader is a member of, so its key always links. */
function FinishedTicketLine({ ticket }: { ticket: UpdateFinishedTicketResponse }) {
  return (
    <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1">
      <UpdateTicketKey item={{ ...ticket, canOpen: true }} />
      <span className="text-foreground min-w-0 break-words">{ticket.ticketTitle}</span>
      <UpdatePriorityBadge priority={ticket.priority} />
      <UpdateRankBadge rank={ticket.missionRank} />
    </div>
  );
}

/** Nothing when the priority is unknown: the ticket was deleted or the reader cannot open it. */
function UpdatePriorityBadge({ priority }: { priority: TicketPriority | null | undefined }) {
  const tTicket = useTranslations("ticket");
  if (!priority) return null;
  return (
    <Badge variant="outline" className="gap-1 text-[11px] font-medium">
      <TicketPriorityIcon priority={priority} size={12} />
      {tTicket(`priority.${priority}`)}
    </Badge>
  );
}

/** Nothing when the rank is unknown: the ticket was deleted or the reader cannot open it. */
function UpdateRankBadge({ rank }: { rank: MissionRank | null | undefined }) {
  const tTicket = useTranslations("ticket");
  if (!rank) return null;
  return (
    <Badge variant="outline" className="text-[11px] font-medium">
      {tTicket(`missionRank.${rank}`)}
    </Badge>
  );
}

/** What each Mission Rank means in time, so a reader can weigh the finished list. */
function UpdateSizingInfo() {
  const t = useTranslations("updates");
  return (
    <InfoTip label={t("sizing.label")}>
      <ul className="space-y-0.5">
        {Object.values(MissionRank).map((rank) => (
          <li key={rank}>{t(`sizing.${rank}`)}</li>
        ))}
      </ul>
    </InfoTip>
  );
}

/** Each non-blank line becomes a bullet, as in the copied text. */
function TextBullets({ text, emptyLabel }: { text: string | null | undefined; emptyLabel: string }) {
  const lines = toLines(text);
  if (lines.length === 0) {
    return <p className="text-muted-foreground italic">{emptyLabel}</p>;
  }
  return (
    <ul className="text-foreground list-disc space-y-0.5 pl-5">
      {lines.map((line, index) => (
        <li key={index} className="break-words">
          {line}
        </li>
      ))}
    </ul>
  );
}

function toLines(text: string | null | undefined): string[] {
  if (!text) return [];
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}
