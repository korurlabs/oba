"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

import { Badge } from "@oba/ui/sky/badge";
import { LoadError } from "@oba/ui/sky/page-states";
import { linkHref } from "@/features/links/components/links";
import { dependencyVersionIds, useDependencies } from "@/features/links/hooks/use-dependencies";
import type { Dependency, DependencyRelation } from "@/features/links/schemas";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import { useGuardedLink } from "@/features/versions/components/draft-context";
import { describeError } from "@/lib/api/error-text";

import { LINKS_TO_LABELS, USED_BY_LABELS } from "./dependency-data";

export type DependencyPanelProps = {
  file: ScopeFile;
  /** The scope's files: their versions are the scope read, and a found entry opens one of them. */
  files: readonly ScopeFile[];
};

function DependencyRow({
  entry,
  labels,
  href,
}: {
  entry: Dependency;
  labels: Readonly<Record<DependencyRelation, string>>;
  href: string | null;
}) {
  const guard = useGuardedLink();
  const broken = entry.status === "BROKEN";
  const name = <code className="font-mono text-xs break-all">{entry.path}</code>;
  return (
    <li className="flex flex-col gap-1 px-3 py-2">
      {href !== null && !broken ? (
        <Link href={href} onClick={guard} className="hover:underline">
          {name}
        </Link>
      ) : (
        <span className={broken ? "text-destructive" : undefined}>{name}</span>
      )}
      <div className="flex flex-wrap items-center gap-2">
        {broken ? (
          <Badge variant="destructive">Missing in this scope</Badge>
        ) : (
          <Badge variant="success">Found</Badge>
        )}
        <span className="text-muted-foreground text-xs">{labels[entry.relation]}</span>
      </div>
    </li>
  );
}

function DependencyList({
  title,
  empty,
  entries,
  labels,
  hrefOf,
}: {
  title: string;
  empty: string;
  entries: readonly Dependency[];
  labels: Readonly<Record<DependencyRelation, string>>;
  hrefOf: (path: string) => string | null;
}) {
  return (
    <section className="flex flex-col gap-2" aria-label={title}>
      <h3 className="text-sm font-medium">
        {title} <span className="text-muted-foreground text-xs">{entries.length}</span>
      </h3>
      {entries.length === 0 ? (
        <p className="text-muted-foreground text-sm">{empty}</p>
      ) : (
        <ul className="flex flex-col divide-y rounded-md border">
          {entries.map((entry: Dependency) => (
            <DependencyRow
              key={`${entry.relation}:${entry.path}`}
              entry={entry}
              labels={labels}
              href={hrefOf(entry.path)}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * What the file links to (Markdown links, a rule's conventions, a skill's
 * parts), each found or missing in the scope, and what uses it (backlinks,
 * rules citing a convention, skills sharing a part). A found entry opens its
 * document.
 */
export function DependencyPanel({ file, files }: DependencyPanelProps) {
  const searchParams = useSearchParams();
  const dependencies = useDependencies(dependencyVersionIds(files, file), file.path);
  const data = dependencies.data?.data ?? undefined;
  if (dependencies.error !== null && data === undefined) {
    return (
      <LoadError
        title="Could not load the links"
        message={describeError(dependencies.error)}
        onRetry={() => void dependencies.refetch()}
      />
    );
  }
  if (data === undefined) {
    return <div className="bg-muted h-24 animate-pulse rounded-md" aria-busy="true" />;
  }
  const search = new URLSearchParams(searchParams.toString());
  const hrefOf = (path: string) => linkHref(path, files, search);
  if (data.linksTo.length === 0 && data.usedBy.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">It links to nothing, and nothing uses it.</p>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <DependencyList
        title="Links to"
        empty="It links to nothing."
        entries={data.linksTo}
        labels={LINKS_TO_LABELS}
        hrefOf={hrefOf}
      />
      <DependencyList
        title="Used by"
        empty="Nothing uses it."
        entries={data.usedBy}
        labels={USED_BY_LABELS}
        hrefOf={hrefOf}
      />
    </div>
  );
}
