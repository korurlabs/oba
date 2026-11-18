"use client";

import { useMemo, type Ref } from "react";
import { useTranslations } from "next-intl";
import { useDebounce } from "@oba/shared/hooks/use-debounce";
import { cn } from "@oba/ui/lib/utils";
import { useAllTickets } from "@/features/all-tickets/hooks/use-all-tickets";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";
import { useSuggestionKeys, type ChatSuggestionChoice, type ChatSuggestionListHandle } from "./chat-mention-list";

/** Results the `#` suggestion asks for: the server's search on key or title over the reader's boards. */
const TICKET_SUGGESTION_AMOUNT = 10;
const TICKET_SEARCH_DEBOUNCE_MS = 200;

/** The chip label of a ticket reference: key and title, as the message shows it. */
export function chatTicketChipLabel(ticket: { key: string; title: string }): string {
  return `${ticket.key} ${ticket.title}`;
}

interface ChatTicketSuggestionListProps {
  /** The text typed after `#`. */
  query: string;
  onSelect: (choice: ChatSuggestionChoice) => void;
  ref?: Ref<ChatSuggestionListHandle>;
}

/**
 * The `#` suggestion: tickets of the reader's member boards matching the
 * typed key or title, 10 at most. The choice becomes a `<#ticketId>` token;
 * the server refuses a ticket outside the author's boards.
 */
export function ChatTicketSuggestionList({ query, onSelect, ref }: ChatTicketSuggestionListProps) {
  const t = useTranslations("chat.tickets");
  const { debouncedValue: search } = useDebounce(query.trim(), TICKET_SEARCH_DEBOUNCE_MS);
  const params = useMemo(() => ({ search, amount: TICKET_SUGGESTION_AMOUNT }), [search]);
  const { data, isLoading, isError, refetch } = useAllTickets(params);

  const tickets = useMemo(
    (): AllTicketListResponse[] => ((data?.data ?? []) as AllTicketListResponse[]).slice(0, TICKET_SUGGESTION_AMOUNT),
    [data?.data],
  );

  const pick = (index: number) => {
    const ticket = tickets[index];
    if (ticket) onSelect({ id: ticket.id, label: chatTicketChipLabel(ticket) });
  };

  const { activeIndex, setActiveIndex } = useSuggestionKeys(ref, tickets.length, pick);

  return (
    <div className="flex flex-col py-1">
      <p className="text-muted-foreground px-3 pt-1 pb-1.5 text-[11px] font-semibold tracking-wide uppercase">
        {t("title")}
      </p>
      {isLoading && <p className="text-muted-foreground px-3 py-2 text-sm">{t("loading")}</p>}
      {isError && (
        <p role="alert" className="text-destructive px-3 py-2 text-sm">
          {t("loadError")}{" "}
          <button type="button" className="underline" onClick={() => void refetch()}>
            {t("retry")}
          </button>
        </p>
      )}
      {!isLoading && !isError && tickets.length === 0 && (
        <p className="text-muted-foreground px-3 py-2 text-sm">{t("empty")}</p>
      )}
      <ul role="listbox" aria-label={t("title")} className="flex flex-col">
        {tickets.map((ticket, index) => (
          <li
            key={ticket.id}
            role="option"
            aria-selected={index === activeIndex}
            onMouseEnter={() => setActiveIndex(index)}
            // Keeps the editor focused, so the choice lands at the caret.
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => pick(index)}
            className={cn(
              "mx-1 flex cursor-pointer items-baseline gap-2 rounded-md px-2 py-1.5 text-sm",
              index === activeIndex && "bg-accent text-accent-foreground",
            )}
          >
            <span className="text-foreground shrink-0 font-mono text-xs font-semibold">{ticket.key}</span>
            <span className="text-foreground truncate">{ticket.title}</span>
            {ticket.board?.name && (
              <span className="text-muted-foreground ml-auto shrink-0 truncate text-xs">{ticket.board.name}</span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
