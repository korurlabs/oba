import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { chatMemberPickSubtitle } from "@/components/chat/chatMembersModel";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import {
  reviewerCandidates,
  reviewerPickBlocked,
  toggleLimited,
  type ReviewerCandidate,
} from "@/components/pull-requests/pullRequestModel";
import { useShareReviewers } from "@/components/pull-requests/shareReviewersStore";
import { sameIds } from "@/components/ticket/ticketActionsModel";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatMeId, useChatMembers } from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";
import { PULL_REQUEST_REVIEWERS_MAX } from "@/lib/schemas/pullRequests";

/**
 * The reviewer picker of the Share pull request form: the organization's
 * members but the author and guests, searchable, up to the reviewer limit,
 * handed back to the form on Done as the full list. A member without Kaizen
 * cannot be picked, since the server refuses them as a reviewer, and a stored
 * reviewer who left is listed by name only to be removed; either one already
 * picked can still be removed. Without an open form, such as after it closed
 * beneath, the sheet closes.
 */
export function ReviewerPickerSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const session = useShareReviewers();
  const meId: string | undefined = useChatMeId();
  const members = useChatMembers();
  const [query, setQuery] = React.useState<string>("");
  const [picked, setPicked] = React.useState<string[] | null>(null);
  const [finished, setFinished] = React.useState<boolean>(false);

  const currentIds: readonly string[] = session?.reviewerIds ?? [];
  const selected: string[] = picked ?? [...currentIds];
  const changed: boolean = picked !== null && !sameIds(picked, currentIds);
  const full: boolean = selected.length >= PULL_REQUEST_REVIEWERS_MAX;
  const rows: ReviewerCandidate[] = reviewerCandidates(
    members.data ?? [],
    meId,
    session?.storedReviewers ?? [],
    query
  );

  const done = (): void => {
    if (changed) session?.change(selected);
    setFinished(true);
  };

  return (
    <BottomSheet
      title={t("prFormReviewers")}
      confirmLabel={t("prFormDone")}
      onConfirm={done}
      confirmDisabled={!members.data}
      detent="medium"
      dirty={changed && !finished && !!session}
      scroll
    >
      <DismissWhen when={finished || !session} />
      <View className="px-4 pt-2">
        <Input
          value={query}
          onChangeText={setQuery}
          placeholder={t("prFormReviewersSearch")}
          accessibilityLabel={t("prFormReviewersSearch")}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
        />
      </View>
      {members.isError && !members.data ? (
        <InlineError
          message={t("prFormReviewersError")}
          onRetry={() => void members.refetch()}
          className="m-4"
        />
      ) : !members.data ? (
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      ) : rows.length === 0 ? (
        <Text className="px-6 py-8 text-center font-sans text-subhead text-muted-foreground">
          {t("prFormReviewersSearchEmpty", { query: query.trim() })}
        </Text>
      ) : (
        <ListSection
          footer={t("prFormReviewersHint", {
            count: selected.length,
            max: PULL_REQUEST_REVIEWERS_MAX,
          })}
        >
          {rows.map((candidate) => {
            const checked: boolean = selected.includes(candidate.userId);
            const blocked: boolean = reviewerPickBlocked(candidate, checked);
            const noAccess: boolean = blocked && candidate.restriction === "noKaizenAccess";
            return (
              <ListRow
                key={candidate.userId}
                title={candidate.name}
                subtitle={chatMemberPickSubtitle(
                  candidate.email,
                  noAccess,
                  t("memberNoKaizenAccess")
                )}
                wrap={noAccess}
                avatar={{ name: candidate.name }}
                accessory="check"
                checked={checked}
                disabled={blocked || (!checked && full)}
                onPress={() =>
                  setPicked(toggleLimited(selected, candidate.userId, PULL_REQUEST_REVIEWERS_MAX))
                }
              />
            );
          })}
        </ListSection>
      )}
    </BottomSheet>
  );
}
