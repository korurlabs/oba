import { useLocalSearchParams } from "expo-router";
import { CalendarDays, ChevronLeft } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { chatMemberName } from "@/components/chat/chatDisplay";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { pullRequestPersonOptions } from "@/components/pull-requests/pullRequestModel";
import { UpdateCalendar } from "@/components/updates/UpdateCalendar";
import { setUpdateFilter, useStoredUpdateFilter } from "@/components/updates/updateFilterStore";
import {
  NO_UPDATE_FILTER,
  resolveUpdateFilter,
  storedDay,
  updateFilterCount,
} from "@/components/updates/updateModel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatMembers } from "@/hooks/queries/chat";
import { useUpdateChannelAuthors } from "@/hooks/queries/updates";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { addDaysToDateOnly, businessToday } from "@/lib/dates";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatMemberResponse } from "@/lib/schemas/chat";
import type { UpdateChannelFilter } from "@/lib/schemas/updates";

type FilterView = "main" | "day" | "person";

/**
 * Narrows the daily updates channel to one business day and one person.
 * Each choice applies at once; Clear all goes back to today and everyone.
 */
export default function UpdatesFilterSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ channelId: string }>();
  const channelId: string | null = params.channelId || null;
  const orgId: string | null = useActiveOrganizationId();
  const today: string = businessToday();
  const stored = useStoredUpdateFilter(orgId);
  const filter: UpdateChannelFilter = resolveUpdateFilter(stored, today);
  const [view, setView] = React.useState<FilterView>("main");

  const apply = (next: UpdateChannelFilter): void => {
    if (orgId) setUpdateFilter(orgId, { day: storedDay(next.day, today), personId: next.personId });
  };

  return (
    <BottomSheet
      title={t("prFilterTitle")}
      cancelLabel={t("chatClose")}
      // A choice applies at once; Done goes back from a list of choices to the filters.
      confirmLabel={view === "main" ? undefined : t("chatDone")}
      onConfirm={view === "main" ? undefined : () => setView("main")}
      scroll
    >
      {view === "main" ? (
        <FilterMain
          filter={filter}
          today={today}
          channelId={channelId}
          onOpen={setView}
          onClear={() => {
            if (orgId) setUpdateFilter(orgId, NO_UPDATE_FILTER);
          }}
        />
      ) : view === "day" ? (
        <FilterDay filter={filter} today={today} onBack={() => setView("main")} onChange={apply} />
      ) : (
        <FilterPerson
          filter={filter}
          channelId={channelId}
          onBack={() => setView("main")}
          onChange={apply}
        />
      )}
    </BottomSheet>
  );
}

/** "Today", "Yesterday" or the date of a picked day. */
function useDayLabel(day: string, today: string): string {
  const { t } = useTranslation();
  const dates = useDates();
  if (day === today) return t("dateToday");
  if (day === addDaysToDateOnly(today, -1)) return t("chatYesterday");
  return dates.date(day) ?? day;
}

/** The name of the chosen person, from the organization's members. */
function usePersonName(personId: string | null): string | null {
  const { t } = useTranslation();
  const members: ChatMemberResponse[] | undefined = useChatMembers().data;
  if (!personId) return null;
  const member: ChatMemberResponse | undefined = members?.find((item) => item.userId === personId);
  return member ? chatMemberName(member) : t("chatUnknownMember");
}

function FilterMain({
  filter,
  today,
  channelId,
  onOpen,
  onClear,
}: {
  filter: UpdateChannelFilter;
  today: string;
  channelId: string | null;
  onOpen: (view: FilterView) => void;
  onClear: () => void;
}) {
  const { t } = useTranslation();
  const dayLabel: string = useDayLabel(filter.day, today);
  const personName: string | null = usePersonName(filter.personId);

  return (
    <View className="pb-6">
      <ListSection footer={t("updFilterFooter")}>
        <ListRow
          title={t("updFilterDay")}
          icon={CalendarDays}
          value={dayLabel}
          // Today is the default, so only another day counts as a filter.
          badge={filter.day === today ? null : 1}
          accessory="chevron"
          onPress={() => onOpen("day")}
        />
        <ListRow
          title={t("prFilterPerson")}
          value={personName ?? t("prFilterAnyone")}
          badge={filter.personId ? 1 : null}
          accessory="chevron"
          disabled={!channelId}
          onPress={channelId ? () => onOpen("person") : undefined}
        />
      </ListSection>
      {updateFilterCount(filter, today) > 0 ? (
        <View className="px-4 pt-6">
          <Button variant="secondary" block onPress={onClear}>
            {t("prFilterClearAll")}
          </Button>
        </View>
      ) : null}
    </View>
  );
}

function BackRow({ onBack }: { onBack: () => void }) {
  const { t } = useTranslation();
  return (
    <View className="flex-row px-2 pt-1">
      <Button variant="ghost" size="sm" icon={ChevronLeft} onPress={onBack}>
        {t("prFilterTitle")}
      </Button>
    </View>
  );
}

function FilterDay({
  filter,
  today,
  onBack,
  onChange,
}: {
  filter: UpdateChannelFilter;
  today: string;
  onBack: () => void;
  onChange: (filter: UpdateChannelFilter) => void;
}) {
  const { t } = useTranslation();
  const dates = useDates();
  const yesterday: string = addDaysToDateOnly(today, -1);
  const picked: boolean = filter.day !== today && filter.day !== yesterday;
  const pick = (day: string): void => onChange({ ...filter, day });
  return (
    <View className="pb-6">
      <BackRow onBack={onBack} />
      <ListSection header={t("updFilterDay")}>
        <ListRow
          title={t("dateToday")}
          subtitle={dates.date(today) ?? undefined}
          accessory="radio"
          checked={filter.day === today}
          onPress={() => pick(today)}
        />
        <ListRow
          title={t("chatYesterday")}
          subtitle={dates.date(yesterday) ?? undefined}
          accessory="radio"
          checked={filter.day === yesterday}
          onPress={() => pick(yesterday)}
        />
        {picked ? (
          <ListRow
            title={dates.date(filter.day) ?? filter.day}
            subtitle={t("updFilterPickedDate")}
            accessory="radio"
            checked
            onPress={() => pick(filter.day)}
          />
        ) : null}
      </ListSection>
      <UpdateCalendar value={filter.day} today={today} onChange={pick} />
      <Text variant="footnote" className="px-8 pt-3 text-muted-foreground">
        {t("updFilterDayFooter")}
      </Text>
    </View>
  );
}

function FilterPerson({
  filter,
  channelId,
  onBack,
  onChange,
}: {
  filter: UpdateChannelFilter;
  channelId: string | null;
  onBack: () => void;
  onChange: (filter: UpdateChannelFilter) => void;
}) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const [search, setSearch] = React.useState<string>("");
  const authors = useUpdateChannelAuthors(channelId);
  const members: ChatMemberResponse[] | undefined = useChatMembers().data;
  const chosen: ChatMemberResponse | string | null = filter.personId
    ? (members?.find((member) => member.userId === filter.personId) ?? filter.personId)
    : null;
  const people: ChatMemberResponse[] = React.useMemo(
    () => pullRequestPersonOptions(authors.data ?? [], chosen, search),
    [authors.data, chosen, search]
  );

  const body = (() => {
    if (authors.isError && !authors.data) {
      return (
        <InlineError
          message={t("prFilterPeopleError")}
          onRetry={() => void authors.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    if (!authors.data) {
      return <ActivityIndicator className="py-4" color={colors.mutedForeground} />;
    }
    return (
      <ListSection
        footer={t("prFilterPeopleFooter", { shown: people.length, total: authors.data.length })}
      >
        <ListRow
          title={t("prFilterAnyone")}
          accessory="check"
          checked={filter.personId === null}
          onPress={() => onChange({ ...filter, personId: null })}
        />
        {people.map((person) => (
          <ListRow
            key={person.userId}
            title={chatMemberName(person)}
            subtitle={person.email ?? undefined}
            avatar={{ name: chatMemberName(person) }}
            accessory="check"
            checked={filter.personId === person.userId}
            onPress={() => onChange({ ...filter, personId: person.userId })}
          />
        ))}
      </ListSection>
    );
  })();

  return (
    <View className="pb-6">
      <BackRow onBack={onBack} />
      <View className="px-4 pt-2">
        <Input
          value={search}
          onChangeText={setSearch}
          placeholder={t("prFilterPeopleSearch")}
          accessibilityLabel={t("prFilterPeopleSearch")}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
        />
      </View>
      {body}
      {people.length === 0 && authors.data && search.trim() ? (
        <Text variant="footnote" className="px-8 pt-2 text-muted-foreground">
          {t("prFilterPeopleNone")}
        </Text>
      ) : null}
    </View>
  );
}
