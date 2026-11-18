"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Filter, Loader2, RotateCcw, Save, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useGithubContributors } from "../hooks/use-github-pull-requests";
import type { PullRequestPickerFilters } from "../hooks/use-pull-request-picker-filter-params";
import {
  GITHUB_LOGIN_PATTERN,
  GithubPullRequestState,
  PULL_REQUEST_AUTHORS_MAX,
} from "../schemas";
import { GithubPullRequestStateBadge } from "./github-pull-request-state-badge";

type FilterCategory = "state" | "author";

const STATE_OPTIONS = Object.values(GithubPullRequestState);

export interface PullRequestFilterPanelProps {
  filters: PullRequestPickerFilters;
  onChange: (changes: Partial<PullRequestPickerFilters>) => void;
  onClear: () => void;
  /** The number of categories that narrow the list, 0 to 2. */
  activeCount: number;
  /** The repository whose contributors the Author category offers. */
  githubRepositoryId: number | undefined;
  /** The authors of the rows this screen has already listed for the repository. */
  loadedAuthors: string[];
  onSavePreferences: () => void;
  onResetPreferences: () => void;
  /** True when the current filters equal the saved set. */
  saveDisabled: boolean;
  isSaving: boolean;
  /** Reset is offered only when a saved set exists. */
  hasSavedPreferences: boolean;
}

/**
 * Merges the sources of the Author options. GitHub logins ignore case, so one
 * login is listed once; a chosen login comes first, so its option keeps the
 * spelling that is ticked. Anything the server would refuse is left out.
 */
function toAuthorOptions(logins: readonly string[]): string[] {
  const byLogin = new Map<string, string>();
  for (const login of logins) {
    const key = login.toLowerCase();
    if (GITHUB_LOGIN_PATTERN.test(login) && !byLogin.has(key)) byLogin.set(key, login);
  }
  return [...byLogin.values()].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
}

/**
 * The State and Author filters of the pull request picker, behind a funnel
 * badged with the active category count. The popup's content mounts only while
 * it is open, so a member who never filters costs no contributors call, and
 * reopening after a failure tries again.
 */
export function PullRequestFilterPanel(props: PullRequestFilterPanelProps) {
  const tFilters = useTranslations("filters");
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="border-border h-9 shrink-0 gap-2 px-3">
          <Filter className="h-4 w-4" aria-hidden />
          <span className="sr-only">{tFilters("title")}</span>
          {props.activeCount > 0 && (
            <Badge variant="secondary" className="bg-primary/15 hover:bg-primary/15 text-primary ml-1">
              {props.activeCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        collisionPadding={16}
        className="flex max-h-[var(--radix-popover-content-available-height)] w-[min(520px,calc(100vw-2rem))] flex-col overflow-hidden p-0"
      >
        <PullRequestFilterContent {...props} />
      </PopoverContent>
    </Popover>
  );
}

function PullRequestFilterContent({
  filters,
  onChange,
  onClear,
  activeCount,
  githubRepositoryId,
  loadedAuthors,
  onSavePreferences,
  onResetPreferences,
  saveDisabled,
  isSaving,
  hasSavedPreferences,
}: PullRequestFilterPanelProps) {
  const t = useTranslations("pullRequests.picker.filters");
  const tFilters = useTranslations("filters");
  const [selectedCategory, setSelectedCategory] = useState<FilterCategory | null>(null);
  const [optionSearch, setOptionSearch] = useState("");

  const contributorsQuery = useGithubContributors(githubRepositoryId);
  const contributors = contributorsQuery.data?.data;

  const chosenStates = filters.prStates ?? [];
  const chosenAuthors = useMemo(() => filters.prAuthors ?? [], [filters.prAuthors]);

  const authorOptions = useMemo(
    () =>
      toAuthorOptions([
        ...chosenAuthors,
        ...(contributors ?? []).map((contributor) => contributor.login),
        ...loadedAuthors,
      ]),
    [chosenAuthors, contributors, loadedAuthors],
  );

  // Narrows the options in memory only: the list is paged on GitHub, so the
  // rows change through `onChange`, when a box is ticked.
  const needle = optionSearch.toLowerCase();
  const visibleAuthors = authorOptions.filter((login) => login.toLowerCase().includes(needle));

  const toggleState = (state: GithubPullRequestState) => {
    const next = chosenStates.includes(state)
      ? chosenStates.filter((chosen) => chosen !== state)
      : [...chosenStates, state];
    onChange({ prStates: next.length > 0 ? next : null });
  };

  const toggleAuthor = (login: string) => {
    const key = login.toLowerCase();
    const remaining = chosenAuthors.filter((chosen) => chosen.toLowerCase() !== key);
    const next = remaining.length < chosenAuthors.length ? remaining : [...chosenAuthors, login];
    onChange({ prAuthors: next.length > 0 ? next : null });
  };

  const handleCategorySelect = (category: FilterCategory) => {
    setSelectedCategory(category);
    setOptionSearch("");
  };

  const categories: { key: FilterCategory; label: string; count: number }[] = [
    { key: "state", label: t("state"), count: chosenStates.length },
    { key: "author", label: t("author"), count: chosenAuthors.length },
  ];

  const optionCount =
    selectedCategory === "state"
      ? { visible: STATE_OPTIONS.length, total: STATE_OPTIONS.length }
      : { visible: visibleAuthors.length, total: authorOptions.length };

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      {/* A fixed height, because two categories are shorter than their options
          and the popup would otherwise jump when the reader switches between them. */}
      <div className="flex max-h-[320px] min-h-0 basis-80">
        <div className="w-[40%] overflow-y-auto border-r py-1">
          {categories.map((category) => {
            const isActive = selectedCategory === category.key;
            return (
              <button
                key={category.key}
                type="button"
                aria-pressed={isActive}
                onClick={() => handleCategorySelect(category.key)}
                className={cn(
                  "flex w-full items-center justify-between px-4 py-2.5 text-left text-sm transition-colors",
                  isActive
                    ? "bg-accent text-foreground border-primary border-l-2"
                    : "hover:bg-muted/50 border-l-2 border-transparent",
                )}
              >
                <span className="font-medium">{category.label}</span>
                {category.count > 0 && (
                  <span className="bg-primary/15 text-primary flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[10px] font-semibold">
                    {category.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="flex min-h-0 w-[60%] min-w-0 flex-col">
          {selectedCategory === null ? (
            <div className="text-muted-foreground flex flex-1 items-center justify-center px-4 text-center text-sm">
              {tFilters("selectField")}
            </div>
          ) : (
            <>
              {selectedCategory === "author" && (
                <div className="border-b px-3 py-2">
                  <SenseySearchInput
                    placeholder={tFilters("searchOptions")}
                    value={optionSearch}
                    onSearchChange={(value) => setOptionSearch(value ?? "")}
                  />
                </div>
              )}

              <div className="flex-1 overflow-y-auto px-3 py-2">
                {selectedCategory === "state" ? (
                  <StateOptions chosen={chosenStates} onToggle={toggleState} />
                ) : (
                  <AuthorOptions
                    logins={visibleAuthors}
                    chosen={chosenAuthors}
                    isLoading={contributorsQuery.isLoading}
                    isUnavailable={contributorsQuery.isError}
                    onToggle={toggleAuthor}
                  />
                )}
              </div>

              <div className="text-muted-foreground shrink-0 border-t px-3 py-1.5 text-right text-xs">
                {tFilters("optionCount", optionCount)}
              </div>
            </>
          )}
        </div>
      </div>

      <p className="text-muted-foreground shrink-0 border-t px-4 py-2 text-xs">{t("searchNote")}</p>

      <div className="shrink-0 space-y-1 border-t px-4 py-2">
        <TooltipProvider>
          <div className="flex items-center gap-1">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="flex-1 gap-1.5 text-xs"
                  onClick={onSavePreferences}
                  disabled={saveDisabled || isSaving}
                >
                  {isSaving ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Save className="h-3.5 w-3.5" />
                  )}
                  {tFilters("savePreferences")}
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("saveTooltip")}</TooltipContent>
            </Tooltip>
            {hasSavedPreferences && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="flex-1 gap-1.5 text-xs"
                    onClick={onResetPreferences}
                    disabled={isSaving}
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    {tFilters("resetPreferences")}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{tFilters("resetPreferencesTooltip")}</TooltipContent>
              </Tooltip>
            )}
          </div>
        </TooltipProvider>
        {activeCount > 0 && (
          <Button type="button" variant="ghost" size="sm" className="w-full gap-2" onClick={onClear}>
            <X className="h-4 w-4" />
            {tFilters("clearAll")}
          </Button>
        )}
      </div>
    </div>
  );
}

const OPTION_ROW_CLASS = "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors";

function StateOptions({
  chosen,
  onToggle,
}: {
  chosen: GithubPullRequestState[];
  onToggle: (state: GithubPullRequestState) => void;
}) {
  return (
    <div className="space-y-1">
      {STATE_OPTIONS.map((state) => (
        <label key={state} className={cn(OPTION_ROW_CLASS, "hover:bg-muted/50 cursor-pointer")}>
          <Checkbox checked={chosen.includes(state)} onCheckedChange={() => onToggle(state)} />
          <GithubPullRequestStateBadge state={state} />
        </label>
      ))}
    </div>
  );
}

function AuthorOptions({
  logins,
  chosen,
  isLoading,
  isUnavailable,
  onToggle,
}: {
  logins: string[];
  chosen: string[];
  isLoading: boolean;
  /** The contributors failed: the options seen keep working. */
  isUnavailable: boolean;
  onToggle: (login: string) => void;
}) {
  const t = useTranslations("pullRequests.picker.filters");
  const tCommon = useTranslations("common");
  const atLimit = chosen.length >= PULL_REQUEST_AUTHORS_MAX;
  const chosenKeys = new Set(chosen.map((login) => login.toLowerCase()));

  return (
    <div className="space-y-1">
      {isUnavailable && (
        <p role="alert" className="text-destructive flex items-start gap-1.5 px-2 py-1 text-xs">
          <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden />
          {t("authorsUnavailable")}
        </p>
      )}
      {atLimit && (
        <p className="text-muted-foreground px-2 py-1 text-xs">
          {t("authorsLimit", { max: PULL_REQUEST_AUTHORS_MAX })}
        </p>
      )}
      {logins.map((login) => {
        const isChosen = chosenKeys.has(login.toLowerCase());
        const isDisabled = atLimit && !isChosen;
        return (
          <label
            key={login}
            className={cn(
              OPTION_ROW_CLASS,
              isDisabled ? "cursor-not-allowed opacity-50" : "hover:bg-muted/50 cursor-pointer",
            )}
          >
            <Checkbox checked={isChosen} disabled={isDisabled} onCheckedChange={() => onToggle(login)} />
            <span className="truncate" title={login}>
              {login}
            </span>
          </label>
        );
      })}
      {isLoading ? (
        <div role="status" className="flex justify-center py-2">
          <Loader2 className="text-muted-foreground h-4 w-4 animate-spin" aria-hidden />
          <span className="sr-only">{tCommon("loading")}</span>
        </div>
      ) : (
        logins.length === 0 && (
          <p className="text-muted-foreground px-2 py-8 text-center text-sm">{t("noAuthors")}</p>
        )
      )}
    </div>
  );
}
