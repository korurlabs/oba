import * as React from "react";
import { ActivityIndicator, ScrollView, View } from "react-native";

import { ActionSheet } from "@/components/kaizen/ActionSheet";
import type { ActionSheetAction } from "@/components/kaizen/actionSheetModel";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { GithubStatePill } from "@/components/pull-requests/PullRequestPills";
import {
  DEFAULT_PICKER_STATES,
  GITHUB_PULL_REQUEST_STATES,
  GITHUB_STATE_STYLE,
  pullRequestErrorKey,
  pullRequestPickerSearch,
  toggleLimited,
} from "@/components/pull-requests/pullRequestModel";
import { Button } from "@/components/ui/button";
import { Chip } from "@/components/ui/chip";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useDates } from "@/hooks/useDates";
import {
  pullRequestErrorCode,
  useGithubContributors,
  useGithubPullRequests,
  useGithubRepositories,
} from "@/hooks/queries/pullRequests";
import { useTranslation, type TranslationKey } from "@/i18n";
import { hapticSelection } from "@/lib/haptics";
import {
  PULL_REQUEST_AUTHORS_MAX,
  type GithubPullRequestState,
  type GithubPullRequestSummaryResponse,
  type GithubRepositoryResponse,
} from "@/lib/schemas/pullRequests";

/** GitHub search allows 30 searches a minute, so typing settles before it searches. */
const SEARCH_DEBOUNCE_MS = 400;

function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = React.useState<T>(value);
  React.useEffect(() => {
    const timer: ReturnType<typeof setTimeout> = setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return settled;
}

/** A picked pull request: its repository and number. */
export interface PickedPullRequest {
  githubRepositoryId: number;
  number: number;
}

export interface PullRequestPickerProps {
  /** The pull request chosen so far, marked in the list. */
  picked?: PickedPullRequest | null;
  onPick: (picked: PickedPullRequest, summary: GithubPullRequestSummaryResponse) => void;
}

/** The filter whose menu is open. */
type PickerMenu = "repository" | "state" | "author";

/**
 * Finds a pull request of one of the GitHub App's repositories: a search by
 * number, title or text, narrowed by GitHub state and author, last updated
 * first, a page at a time. Repository, state and author are dropdown chips,
 * each opening a menu; a state or author menu adds or removes the one tapped.
 */
export function PullRequestPicker({ picked = null, onPick }: PullRequestPickerProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const dates = useDates();
  const repositories = useGithubRepositories();
  const [chosenRepoId, setChosenRepoId] = React.useState<number | null>(null);
  const repoId: number | null = chosenRepoId ?? repositories.data?.[0]?.githubRepositoryId ?? null;
  const [search, setSearch] = React.useState<string>("");
  const [states, setStates] = React.useState<GithubPullRequestState[]>([...DEFAULT_PICKER_STATES]);
  const [authors, setAuthors] = React.useState<string[]>([]);
  const [menu, setMenu] = React.useState<PickerMenu | null>(null);
  const settledSearch: string = useDebounced(pullRequestPickerSearch(search), SEARCH_DEBOUNCE_MS);
  const contributors = useGithubContributors(repoId);
  const pullRequests = useGithubPullRequests(repoId, { search: settledSearch, states, authors });
  const rows: GithubPullRequestSummaryResponse[] = React.useMemo(
    () => pullRequests.data?.pages.flatMap((page) => page.items) ?? [],
    [pullRequests.data]
  );
  const resultsLimited: boolean =
    pullRequests.data?.pages.some((page) => page.resultsLimited) ?? false;

  const errorText = (error: unknown, fallback: TranslationKey): string =>
    t(pullRequestErrorKey(pullRequestErrorCode(error)) ?? fallback);

  if (repositories.isPending) {
    return <ActivityIndicator className="py-6" color={colors.mutedForeground} />;
  }
  if (repositories.isError) {
    return (
      <InlineError
        message={errorText(repositories.error, "prPickerRepositoriesError")}
        onRetry={() => void repositories.refetch()}
        className="mx-4 mt-4"
      />
    );
  }
  if (repositories.data.length === 0) {
    return (
      <EmptyState title={t("prPickerNoRepositories")} message={t("prPickerNoRepositoriesBody")} />
    );
  }

  const chooseRepository = (repository: GithubRepositoryResponse): void => {
    setChosenRepoId(repository.githubRepositoryId);
    // Authors belong to one repository's contributors.
    setAuthors([]);
  };

  const results = (() => {
    if (pullRequests.isPending) {
      return <ActivityIndicator className="py-6" color={colors.mutedForeground} />;
    }
    if (pullRequests.isError && rows.length === 0) {
      return (
        <InlineError
          message={errorText(pullRequests.error, "prPickerError")}
          onRetry={() => void pullRequests.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    if (rows.length === 0) {
      return (
        <Text variant="footnote" className="px-8 pt-4 text-muted-foreground">
          {t("prPickerEmpty")}
        </Text>
      );
    }
    return (
      <ListSection footer={resultsLimited ? t("prPickerResultsLimited") : undefined}>
        {rows.map((row) => (
          <ListRow
            key={row.number}
            title={`#${row.number} ${row.title}`}
            subtitle={[row.authorLogin, dates.age(row.updatedAt)].filter(Boolean).join(" · ")}
            trailing={<GithubStatePill state={row.state} />}
            accessory="radio"
            checked={picked?.githubRepositoryId === repoId && picked?.number === row.number}
            wrap
            onPress={() =>
              repoId !== null && onPick({ githubRepositoryId: repoId, number: row.number }, row)
            }
          />
        ))}
      </ListSection>
    );
  })();

  const toggleState = (state: GithubPullRequestState): void =>
    setStates(
      states.includes(state)
        ? states.filter((value) => value !== state)
        : GITHUB_PULL_REQUEST_STATES.filter((value) => value === state || states.includes(value))
    );

  const stateLabel: string =
    states.length === 0
      ? t("prPickerState")
      : states.map((state) => t(GITHUB_STATE_STYLE[state].labelKey)).join(", ");
  const authorLabel: string =
    authors.length === 1 ? (authors[0] ?? t("prPickerAuthor")) : t("prPickerAuthor");

  const menus: Record<PickerMenu, { title: string; actions: ActionSheetAction[] }> = {
    repository: {
      title: t("prPickerRepository"),
      actions: repositories.data.map((repository) => ({
        label: repository.fullName,
        selected: repository.githubRepositoryId === repoId,
        onPress: () => {
          hapticSelection();
          chooseRepository(repository);
        },
      })),
    },
    state: {
      title: t("prPickerState"),
      actions: GITHUB_PULL_REQUEST_STATES.map((state) => ({
        label: t(GITHUB_STATE_STYLE[state].labelKey),
        selected: states.includes(state),
        onPress: () => {
          hapticSelection();
          toggleState(state);
        },
      })),
    },
    author: {
      title: t("prPickerAuthor"),
      actions: (contributors.data ?? []).map((contributor) => {
        const selected: boolean = authors.includes(contributor.login);
        return {
          label: contributor.login,
          selected,
          disabled: !selected && authors.length >= PULL_REQUEST_AUTHORS_MAX,
          onPress: () => {
            hapticSelection();
            setAuthors(toggleLimited(authors, contributor.login, PULL_REQUEST_AUTHORS_MAX));
          },
        };
      }),
    },
  };
  const repository: GithubRepositoryResponse | undefined = repositories.data.find(
    (item) => item.githubRepositoryId === repoId
  );

  return (
    <View className="gap-2 pb-6">
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerClassName="gap-2 px-4 pt-2"
      >
        <Chip
          label={repository?.fullName ?? t("prPickerRepository")}
          accessibilityLabel={`${t("prPickerRepository")}: ${repository?.fullName ?? ""}`}
          dropdown={repositories.data.length > 1}
          // With one repository there is nothing to choose.
          onPress={repositories.data.length > 1 ? () => setMenu("repository") : undefined}
        />
        <Chip
          label={stateLabel}
          accessibilityLabel={`${t("prPickerState")}: ${stateLabel}`}
          selected={states.length > 0}
          dropdown
          onPress={() => setMenu("state")}
        />
        {contributors.data && contributors.data.length > 0 ? (
          <Chip
            label={authorLabel}
            accessibilityLabel={`${t("prPickerAuthor")}: ${authors.join(", ")}`}
            selected={authors.length > 0}
            count={authors.length > 1 ? authors.length : null}
            dropdown
            onPress={() => setMenu("author")}
          />
        ) : null}
      </ScrollView>
      {contributors.isError ? (
        <InlineError
          message={errorText(contributors.error, "prPickerAuthorsError")}
          onRetry={() => void contributors.refetch()}
          className="mx-4"
        />
      ) : null}
      <View className="px-4">
        <Input
          value={search}
          onChangeText={setSearch}
          placeholder={t("prPickerSearch")}
          accessibilityLabel={t("prPickerSearch")}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
        />
      </View>
      <Text variant="caption" className="px-4 text-muted-foreground">
        {t("prPickerCaption")}
      </Text>
      {results}
      {pullRequests.hasNextPage ? (
        <View className="items-center pt-2">
          <Button
            variant="ghost"
            size="sm"
            loading={pullRequests.isFetchingNextPage}
            onPress={() => {
              pullRequests.fetchNextPage().catch((error: unknown) => {
                // The query keeps its pages and shows the failure under them.
                console.warn(
                  "[pull-requests] next picker page failed",
                  error instanceof Error ? error.message : error
                );
              });
            }}
          >
            {t("prPickerMore")}
          </Button>
        </View>
      ) : null}
      {pullRequests.isFetchNextPageError ? (
        <InlineError
          message={errorText(pullRequests.error, "prPickerError")}
          onRetry={() => {
            pullRequests.fetchNextPage().catch((error: unknown) => {
              console.warn(
                "[pull-requests] retrying the picker page failed",
                error instanceof Error ? error.message : error
              );
            });
          }}
          className="mx-4"
        />
      ) : null}
      <ActionSheet
        visible={menu !== null}
        onClose={() => setMenu(null)}
        title={menu ? menus[menu].title : undefined}
        actions={menu ? menus[menu].actions : []}
        cancelLabel={t("chatClose")}
      />
    </View>
  );
}
