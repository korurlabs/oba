"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Check, Loader2 } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { getDisplayName, UserAvatar } from "@/components/ui/user-avatar";
import { cn } from "@/lib/utils";
import { useChatMembers } from "@/features/chat/hooks/use-chat-messages";
import type { ChatMemberResponse } from "@/features/chat/schemas";

/** A person the Person field offers: a channel author, or the chosen person. */
type ChatFilterPerson = Pick<ChatMemberResponse, "userId" | "firstName" | "lastName" | "email">;

const OPTION_ROW_CLASS = "flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-left text-sm transition-colors";

function memberName(member: ChatFilterPerson): string {
  return getDisplayName({
    firstName: member.firstName ?? undefined,
    lastName: member.lastName ?? undefined,
    email: member.email ?? undefined,
  });
}

interface ChatPersonFilterOptionsProps {
  channelId: string;
  /** The chosen author's user ID. */
  chosen: string | null;
  /** Chooses the person, or clears the choice when it is the chosen one. */
  onToggle: (userId: string) => void;
}

/**
 * The Person pane of a channel filter: the channel's message authors with a
 * search, one of whom can be chosen. It fills the options column of
 * `ChatChannelFilterPopover`.
 */
export function ChatPersonFilterOptions({ channelId, chosen, onToggle }: ChatPersonFilterOptionsProps) {
  const tFilters = useTranslations("filters");
  const [optionSearch, setOptionSearch] = useState("");

  const authorsQuery = useChatMembers({ channelId, authorsOnly: true });
  const authors = useMemo(() => authorsQuery.data ?? [], [authorsQuery.data]);
  // A chosen person who has no live message any more is not an author: named from the organization's list.
  const personListed = !chosen || authors.some((author) => author.userId === chosen);
  const organizationQuery = useChatMembers({ enabled: !authorsQuery.isLoading && !personListed });

  const people = useMemo((): ChatFilterPerson[] => {
    if (personListed || !chosen) return authors;
    const known = organizationQuery.data?.find((member) => member.userId === chosen);
    const chosenPerson: ChatFilterPerson = known ?? { userId: chosen, firstName: null, lastName: null, email: null };
    return [chosenPerson, ...authors];
  }, [authors, personListed, chosen, organizationQuery.data]);

  const needle = optionSearch.toLowerCase();
  const visiblePeople = people.filter(
    (member) =>
      member.userId === chosen ||
      memberName(member).toLowerCase().includes(needle) ||
      (member.email ?? "").toLowerCase().includes(needle),
  );

  return (
    <>
      <div className="border-b px-3 py-2">
        <SenseySearchInput
          placeholder={tFilters("searchOptions")}
          value={optionSearch}
          onSearchChange={(value) => setOptionSearch(value ?? "")}
        />
      </div>
      <div className="flex-1 overflow-y-auto px-3 py-2">
        <PersonOptionList
          people={visiblePeople}
          chosen={chosen}
          isLoading={authorsQuery.isLoading}
          isUnavailable={authorsQuery.isError}
          onToggle={onToggle}
        />
      </div>
      <div className="text-muted-foreground shrink-0 border-t px-3 py-1.5 text-right text-xs">
        {tFilters("optionCount", { visible: visiblePeople.length, total: people.length })}
      </div>
    </>
  );
}

function PersonOptionList({
  people,
  chosen,
  isLoading,
  isUnavailable,
  onToggle,
}: {
  people: ChatFilterPerson[];
  chosen: string | null;
  isLoading: boolean;
  /** The authors failed to load: a chosen person stays listed, so it can be cleared. */
  isUnavailable: boolean;
  onToggle: (userId: string) => void;
}) {
  const t = useTranslations("chat.filters");
  const tFilters = useTranslations("filters");
  const tCommon = useTranslations("common");
  const tMessage = useTranslations("chat.message");

  return (
    <div className="space-y-1">
      {isUnavailable && (
        <p role="alert" className="text-destructive flex items-start gap-1.5 px-2 py-1 text-xs">
          <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden />
          {t("peopleUnavailable")}
        </p>
      )}
      {people.map((member) => {
        const isChosen = member.userId === chosen;
        const name = memberName(member) || tMessage("unknownMember");
        return (
          <button
            key={member.userId}
            type="button"
            aria-pressed={isChosen}
            onClick={() => onToggle(member.userId)}
            title={member.email ?? undefined}
            className={cn(OPTION_ROW_CLASS, "hover:bg-muted/50 cursor-pointer", isChosen && "bg-accent")}
          >
            <UserAvatar
              user={{
                firstName: member.firstName ?? undefined,
                lastName: member.lastName ?? undefined,
                email: member.email ?? undefined,
              }}
              size="xs"
              name={name}
              className="flex-1 gap-2.5"
              nameClassName="text-inherit text-[length:inherit] font-normal"
            />
            {isChosen && <Check className="text-primary h-4 w-4 shrink-0" aria-hidden />}
          </button>
        );
      })}
      {isLoading ? (
        <div role="status" className="flex justify-center py-2">
          <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" aria-hidden />
          <span className="sr-only">{tCommon("loading")}</span>
        </div>
      ) : (
        people.length === 0 &&
        !isUnavailable && (
          <p className="text-muted-foreground px-2 py-8 text-center text-sm">{tFilters("noMembers")}</p>
        )
      )}
    </div>
  );
}
