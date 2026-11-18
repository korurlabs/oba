"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { CheckIcon, ChevronDown, Lock } from "lucide-react";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@oba/ui/components/command";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/components/popover";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { GithubRepositoryResponse } from "../schemas";

interface RepositoryComboboxProps {
  repositories: GithubRepositoryResponse[];
  /** The chosen repository; undefined until one is chosen. */
  value: number | undefined;
  onValueChange: (githubRepositoryId: number) => void;
  disabled?: boolean;
  className?: string;
}

/**
 * The repositories of the GitHub App installation, searchable by
 * `owner/name`. An installation can hold far more repositories than fit on
 * screen, so the list scrolls inside a popover that is never taller than the
 * space left in the viewport, and flips above the trigger when it does not fit below.
 * It is at least as wide as the trigger and wide enough for a full `owner/name`
 * label, but never wider than the viewport.
 */
export function RepositoryCombobox({
  repositories,
  value,
  onValueChange,
  disabled,
  className,
}: RepositoryComboboxProps) {
  const t = useTranslations("pullRequests.picker");
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");

  const selected = repositories.find((repository) => repository.githubRepositoryId === value);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return repositories;
    return repositories.filter((repository) => repository.fullName.toLowerCase().includes(term));
  }, [repositories, search]);

  // A reopened list starts unfiltered, with the chosen repository highlighted.
  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) setSearch("");
  };

  const choose = (githubRepositoryId: number) => {
    if (githubRepositoryId !== value) onValueChange(githubRepositoryId);
    handleOpenChange(false);
  };

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-label={t("repository")}
          disabled={disabled}
          className={cn(
            "border-input bg-background dark:bg-input/30 text-foreground h-9 justify-between gap-2 rounded-lg border-[1.5px] px-3 text-sm font-normal shadow-xs",
            "hover:border-border hover:bg-background dark:hover:bg-input/50",
            "focus-visible:border-primary focus-visible:ring-primary/20 focus-visible:ring-[3px]",
            open && "border-primary ring-primary/20 ring-[3px]",
            !selected && "text-muted-foreground",
            className,
          )}
        >
          {selected ? (
            <RepositoryLabel repository={selected} />
          ) : (
            <span className="truncate">{t("repositoryPlaceholder")}</span>
          )}
          <ChevronDown className="h-4 w-4 shrink-0 opacity-50" aria-hidden />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        collisionPadding={16}
        className="flex max-h-[min(24rem,var(--radix-popover-content-available-height))] w-[min(24rem,calc(100vw-2rem))] min-w-(--radix-popover-trigger-width) flex-col p-0"
      >
        <Command
          shouldFilter={false}
          defaultValue={selected ? String(selected.githubRepositoryId) : undefined}
          className="min-h-0 flex-1"
        >
          <CommandInput
            placeholder={t("repositorySearchPlaceholder")}
            value={search}
            onValueChange={setSearch}
          />
          <CommandList className="max-h-none min-h-0 flex-1 overscroll-contain">
            <CommandEmpty>{t("noRepositoryMatches")}</CommandEmpty>
            <CommandGroup>
              {filtered.map((repository) => (
                <CommandItem
                  key={repository.githubRepositoryId}
                  value={String(repository.githubRepositoryId)}
                  onSelect={() => choose(repository.githubRepositoryId)}
                >
                  <RepositoryLabel repository={repository} />
                  <CheckIcon
                    aria-hidden
                    className={cn(
                      "ml-auto size-4",
                      repository.githubRepositoryId === value ? "opacity-100" : "opacity-0",
                    )}
                  />
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

function RepositoryLabel({ repository }: { repository: GithubRepositoryResponse }) {
  const t = useTranslations("pullRequests.picker");
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
      <span className="truncate" title={repository.fullName}>
        {repository.fullName}
      </span>
      {repository.privateRepository && (
        <>
          <Lock className="text-muted-foreground h-3 w-3 shrink-0" aria-hidden />
          <span className="sr-only">{t("privateRepository")}</span>
        </>
      )}
    </span>
  );
}
