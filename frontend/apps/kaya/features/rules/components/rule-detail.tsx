"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { ViewToggle } from "@/features/editor/components/view-toggle";
import { useEditorParams, type EditorView } from "@/features/editor/hooks/use-editor-params";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { mountRefOf } from "@/features/scope/components/mount-paths";
import type { RuleTab, ScopeFile } from "@/features/scope/components/scope-files";
import { editTarget } from "@/features/scope/components/scope-data";
import { SubmittedNotice } from "@/features/versions/components/change-control";
import { FileEditor } from "@/features/versions/components/file-editor";
import { useEditSource, type SubmittedChange } from "@/features/versions/hooks/use-change-write";

import { useRuleTabKind } from "../hooks/use-rule-kinds";
import { BackLink, DetailNav, FileDetail, useCurrentQuery, type Neighbour } from "./file-detail";
import { rulesSection, type RuleSection } from "./rule-links";
import { ruleTabRows, tabVersionIds, type RuleTabRow } from "./rule-rows";

/** The row before or after a file, in list order, as an arrow's target. */
function neighbourOf(section: RuleSection, row: RuleTabRow | undefined): Neighbour | null {
  return row === undefined
    ? null
    : {
        to: (current: URLSearchParams) => section.detailHref(row.file.path, current),
        name: row.file.path,
      };
}

/**
 * One delivered file of a section: the back link and the arrows over the
 * file's header, read view and editor.
 */
function RuleFile({
  section,
  file,
  rows,
  ready,
}: {
  section: RuleSection;
  file: ScopeFile;
  /** The section's rows in list order, for the arrows. */
  rows: readonly RuleTabRow[];
  ready: ReadyScope;
}) {
  const router = useRouter();
  const current = useCurrentQuery();
  const index = rows.findIndex((row: RuleTabRow) => row.file.path === file.path);
  const summary = rows[index]?.summary ?? null;

  return (
    <FileDetail
      file={file}
      ready={ready}
      summary={summary}
      heading={<h1 className="min-w-0 font-mono text-base break-all">{file.path}</h1>}
      nav={(editing: boolean) => (
        <DetailNav
          back={section.listHref}
          backLabel={section.back}
          previous={neighbourOf(section, rows[index - 1])}
          next={neighbourOf(section, rows[index + 1])}
          showSteps={!editing && index !== -1}
        />
      )}
      onRenamed={(path: string) => router.replace(section.savedHref(path, current))}
      onDeleted={() => router.push(section.listHref(current))}
    />
  );
}

/** The page of a file the scope does not deliver, such as one removed or not inherited here. */
function NotDelivered({ section, path }: { section: RuleSection; path: string }) {
  return (
    <div className="flex flex-col gap-4">
      <BackLink to={section.listHref} label={section.back} />
      <p className="text-muted-foreground text-sm">
        This scope does not deliver <code className="font-mono break-all">{path}</code>.
      </p>
    </div>
  );
}

/**
 * A new file of a section, starting at the section's folder (or the pack's
 * namespace in a mounted pack). Saved into the open change, it gets its own
 * page; submitted as a one-edit change, the page says it is in review. Cancel
 * returns to the list.
 */
function NewRuleFile({ section, ready }: { section: RuleSection; ready: ReadyScope }) {
  const router = useRouter();
  const current = useCurrentQuery();
  const [{ view }, setEditor] = useEditorParams();
  const source = useEditSource(ready);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const target = editTarget(ready.scope, ready.owners);
  const start = target === null ? null : section.newStart(mountRefOf(target));

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <BackLink to={section.listHref} label={section.back} />
      <h1>{section.newTitle}</h1>
      {submitted !== null ? (
        <p className="text-sm">
          <SubmittedNotice submitted={submitted} />
        </p>
      ) : target === null ? (
        <p className="text-muted-foreground text-sm">Choose a pack to add a file.</p>
      ) : start === null ? (
        <p className="text-muted-foreground text-sm">{section.mountedRefusal}</p>
      ) : source === null ? null : (
        <div className="flex min-w-0 flex-col gap-3">
          <ViewToggle view={view} onChange={(next: EditorView) => void setEditor({ view: next })} />
          <FileEditor
            key={`new:${section.tab}`}
            source={source}
            file={null}
            initialPath={start.path}
            initialExecutable={start.executable}
            view={view}
            onSaved={(path: string) => router.replace(section.savedHref(path, current))}
            onSubmitted={setSubmitted}
            onCancel={() => router.push(section.listHref(current))}
          />
        </div>
      )}
    </div>
  );
}

/**
 * The file a detail page names, found by its path among the section's rows
 * in list order, else among every file of the scope, so a file whose path
 * fits no tab still opens. The open change's files and, with `inherited`, the
 * inherited files are part of the scope, so a path only the change has, or an
 * inherited one, resolves too.
 */
function RuleDetailView({
  section,
  path,
  ready,
}: {
  section: RuleSection;
  path: string;
  ready: ReadyScope;
}) {
  const files = section.files(ready.files);
  const tabKind = useRuleTabKind(section.tab, tabVersionIds(files, section.tab));
  const rows = ruleTabRows(files, section.tab, tabKind.kind);
  const file =
    rows.find((row: RuleTabRow) => row.file.path === path)?.file ??
    ready.files.find((entry: ScopeFile) => entry.path === path);
  if (file === undefined) return <NotDelivered section={section} path={path} />;
  return <RuleFile key={file.path} section={section} file={file} rows={rows} ready={ready} />;
}

export type RuleSectionDetailProps = {
  section: RuleSection;
  /** The file's repository path; null for a new file. */
  path: string | null;
};

/** A section's file on its own page, or the page that starts a new file, in the scope the URL names. */
export function RuleSectionDetail({ section, path }: RuleSectionDetailProps) {
  return (
    <ScopePage title={section.title} description={section.description} variant="detail">
      {(ready: ReadyScope) =>
        path === null ? (
          <NewRuleFile section={section} ready={ready} />
        ) : (
          <RuleDetailView section={section} path={path} ready={ready} />
        )
      }
    </ScopePage>
  );
}

export type RuleDetailProps = {
  tab: RuleTab;
  /** The file's repository path; null for a new file. */
  path: string | null;
};

/** A Rules file's own page, or the page that starts a new file, in the scope the URL names. */
export function RuleDetail({ tab, path }: RuleDetailProps) {
  return <RuleSectionDetail section={rulesSection(tab)} path={path} />;
}
