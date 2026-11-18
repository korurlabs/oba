import { useLocalSearchParams } from "expo-router";
import { ChevronLeft } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { chatMemberName } from "@/components/chat/chatDisplay";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import {
  setPullRequestFilter,
  usePullRequestFilter,
} from "@/components/pull-requests/pullRequestFilterStore";
import {
  NO_PULL_REQUEST_FILTER,
  PULL_REQUEST_STATUSES,
  SHARE_STATUS_STYLE,
  pullRequestFilterCount,
  pullRequestPersonOptions,
  togglePullRequestStatus,
} from "@/components/pull-requests/pullRequestModel";
import { PullRequestStatusPill } from "@/components/pull-requests/PullRequestPills";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatMembers } from "@/hooks/queries/chat";
import { usePullRequestChannelAuthors } from "@/hooks/queries/pullRequests";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatMemberResponse } from "@/lib/schemas/chat";
import type { PullRequestChannelFilter } from "@/lib/schemas/pullRequests";

type FilterView = "main" | "status" | "person";

/**
 * Narrows the pull requests channel by share status (any number) and by the
 * person who wrote (one). Each choice applies at once; Clear all lifts both.
 * With a status chosen the server leaves out plain messages, which the
 * footer says.
 */
export default function PullRequestFilterSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ channelId: string }>();
  const channelId: string | null = params.channelId || null;
  const orgId: string | null = useActiveOrganizationId();
  const filter: PullRequestChannelFilter = usePullRequestFilter(orgId);
  const [view, setView] = React.useState<FilterView>("main");

  const apply = (next: PullRequestChannelFilter): void => {
    if (orgId) setPullRequestFilter(orgId, next);
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
        <FilterMain filter={filter} channelId={channelId} onOpen={setView} onChange={apply} />
      ) : view === "status" ? (
        <FilterStatus filter={filter} onBack={() => setView("main")} onChange={apply} />
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
  channelId,
  onOpen,
  onChange,
}: {
  filter: PullRequestChannelFilter;
  channelId: string | null;
  onOpen: (view: FilterView) => void;
  onChange: (filter: PullRequestChannelFilter) => void;
}) {
  const { t } = useTranslation();
  const personName: string | null = usePersonName(filter.personId);
  const statusValue: string =
    filter.statuses.length === 0
      ? t("prFilterAnyStatus")
      : filter.statuses.length === 1
        ? t(SHARE_STATUS_STYLE[filter.statuses[0]].labelKey)
        : t("prFilterSelected", { count: filter.statuses.length });

  return (
    <View className="pb-6">
      <ListSection footer={t("prFilterFooter")}>
        <ListRow
          title={t("prFilterStatus")}
          value={statusValue}
          badge={filter.statuses.length > 0 ? filter.statuses.length : null}
          accessory="chevron"
          onPress={() => onOpen("status")}
        />
        <ListRow
          title={t("prFilterPerson")}
          value={personName ?? t("prFilterAnyone")}
          accessory="chevron"
          disabled={!channelId}
          onPress={channelId ? () => onOpen("person") : undefined}
        />
      </ListSection>
      {pullRequestFilterCount(filter) > 0 ? (
        <View className="px-4 pt-6">
          <Button variant="secondary" block onPress={() => onChange(NO_PULL_REQUEST_FILTER)}>
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

function FilterStatus({
  filter,
  onBack,
  onChange,
}: {
  filter: PullRequestChannelFilter;
  onBack: () => void;
  onChange: (filter: PullRequestChannelFilter) => void;
}) {
  const { t } = useTranslation();
  return (
    <View className="pb-6">
      <BackRow onBack={onBack} />
      <ListSection
        header={
          filter.statuses.length > 0
            ? t("prFilterSelected", { count: filter.statuses.length })
            : t("prFilterStatus")
        }
        footer={t("prFilterStatusFooter")}
      >
        {PULL_REQUEST_STATUSES.map((status) => {
          const checked: boolean = filter.statuses.includes(status);
          const toggle = (): void =>
            onChange({ ...filter, statuses: togglePullRequestStatus(filter.statuses, status) });
          return (
            <ListRow
              key={status}
              title={t(SHARE_STATUS_STYLE[status].labelKey)}
              trailing={<PullRequestStatusPill status={status} />}
              accessory="checkbox"
              checked={checked}
              onCheckedChange={toggle}
              onPress={toggle}
            />
          );
        })}
      </ListSection>
    </View>
  );
}

function FilterPerson({
  filter,
  channelId,
  onBack,
  onChange,
}: {
  filter: PullRequestChannelFilter;
  channelId: string | null;
  onBack: () => void;
  onChange: (filter: PullRequestChannelFilter) => void;
}) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const [search, setSearch] = React.useState<string>("");
  const authors = usePullRequestChannelAuthors(channelId);
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
