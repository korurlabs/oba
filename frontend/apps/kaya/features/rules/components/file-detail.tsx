"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import { ArrowLeft, ChevronLeft, ChevronRight, Pencil } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@oba/ui/sky/tooltip";
import {
  nextFileHistoryPage,
  useChange,
  useFileHistory,
} from "@/features/changes/hooks/use-changes";
import type { FileHistoryResponse } from "@/features/changes/schemas";
import { ChecksPanel } from "@/features/editor/components/checks-panel";
import { DependencyPanel } from "@/features/editor/components/dependency-panel";
import { documentState } from "@/features/editor/components/document-state";
import { chooseHistoryEntry, OlderVersion } from "@/features/editor/components/entity-document";
import { FileContent } from "@/features/editor/components/file-content";
import {
  breadcrumbOf,
  historyCount,
  linksCount,
  tabLabel,
} from "@/features/editor/components/file-detail-data";
import { HistoryPanel } from "@/features/editor/components/history-panel";
import { SharedPartGate } from "@/features/editor/components/shared-part-gate";
import { StateBadge, StateNote } from "@/features/editor/components/state-badge";
import { historyEntriesOf } from "@/features/editor/components/version-menu-data";
import { ViewToggle } from "@/features/editor/components/view-toggle";
import {
  EDITOR_PANELS,
  useEditorParams,
  type EditorPanel,
  type EditorView,
} from "@/features/editor/hooks/use-editor-params";
import { dependencyVersionIds, useDependencies } from "@/features/links/hooks/use-dependencies";
import type { ReadyScope } from "@/features/scope/components";
import { effectiveOf } from "@/features/scope/components/effective-items";
import { formatBytes, type ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import { useMembers } from "@/features/members/hooks/use-members";
import { useConfirmLeave, useGuardedLink } from "@/features/versions/components/draft-context";
import { FileEditor, type BeforeSave } from "@/features/versions/components/file-editor";
import { FileBadges, OriginBadge } from "@/features/versions/components/file-viewer";
import {
  InheritedNote,
  ItemStatusBadges,
  OverrideNote,
} from "@/features/versions/components/override-status";
import { SubmittedNotice } from "@/features/versions/components/change-control";
import { ChangeLockTip } from "@/features/versions/components/open-change";
import { useEditSource, type SubmittedChange } from "@/features/versions/hooks/use-change-write";
import { useDraftSession } from "@/features/versions/hooks/use-draft-session";
import { useVersion } from "@/features/versions/hooks/use-versions";

import type { KindSummary } from "./rule-rows";

/** The tabs of a file's read view, in order; Content is the default and stays out of the URL. */
const DETAIL_TABS: readonly { id: EditorPanel; label: string }[] = [
  { id: "content", label: "Content" },
  { id: "links", label: "Links" },
  { id: "history", label: "History" },
  { id: "checks", label: "Checks" },
];

const DEFAULT_DETAIL_TAB: EditorPanel = "content";

/** The query of the page, as a copy the link builders may read. */
export function useCurrentQuery(): URLSearchParams {
  const searchParams = useSearchParams();
  return new URLSearchParams(searchParams.toString());
}

/** The link back to a detail page's list, such as "Back to Rules". */
export function BackLink({
  to,
  label,
}: {
  to: (current: URLSearchParams) => string;
  label: string;
}) {
  const guard = useGuardedLink();
  const current = useCurrentQuery();
  return (
    <Link
      href={to(current)}
      onClick={guard}
      className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-sm"
    >
      <ArrowLeft className="size-4" aria-hidden />
      {label}
    </Link>
  );
}

/**
 * One arrow to a neighbouring entry of the list, named in its tooltip and
 * label when the neighbour's name is given; disabled at either end of it.
 */
export function StepLink({
  to,
  label,
  name,
  children,
}: {
  /** The neighbour's address; null at the end of the list. */
  to: ((current: URLSearchParams) => string) | null;
  label: string;
  /** The neighbour's name, such as its path. */
  name?: string;
  children: ReactNode;
}) {
  const guard = useGuardedLink();
  const current = useCurrentQuery();
  if (to === null) {
    return (
      <Button size="icon-sm" variant="outline" disabled aria-label={label}>
        {children}
      </Button>
    );
  }
  const link = (
    <Button size="icon-sm" variant="outline" asChild>
      <Link
        href={to(current)}
        onClick={guard}
        aria-label={name === undefined ? label : `${label}: ${name}`}
      >
        {children}
      </Link>
    </Button>
  );
  if (name === undefined) return link;
  return (
    <TooltipProvider delayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>{link}</TooltipTrigger>
        <TooltipContent className="max-w-xs font-mono break-all">{name}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

/** A neighbouring entry of a detail page's list: its address and its name. */
export type Neighbour = {
  to: (current: URLSearchParams) => string;
  name: string;
};

/**
 * The row over a file's page: the link back to its list and, outside the
 * editor, the arrows to the files before and after it.
 */
export function DetailNav({
  back,
  backLabel,
  previous,
  next,
  showSteps,
}: {
  back: (current: URLSearchParams) => string;
  backLabel: string;
  /** Null at the start of the list. */
  previous: Neighbour | null;
  /** Null at the end of the list. */
  next: Neighbour | null;
  showSteps: boolean;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <BackLink to={back} label={backLabel} />
      {showSteps && (
        <div className="flex items-center gap-1">
          <StepLink to={previous?.to ?? null} name={previous?.name} label="Previous file">
            <ChevronLeft aria-hidden />
          </StepLink>
          <StepLink to={next?.to ?? null} name={next?.name} label="Next file">
            <ChevronRight aria-hidden />
          </StepLink>
        </div>
      )}
    </div>
  );
}

/** The folders and name of a part of a document, over its header. */
function Breadcrumb({ segments }: { segments: readonly string[] }) {
  return (
    <nav aria-label="Path" className="min-w-0">
      <ol className="text-muted-foreground flex flex-wrap items-center gap-1 font-mono text-xs">
        {segments.map((segment: string, index: number) => (
          <li key={`${index}:${segment}`} className="flex items-center gap-1 break-all">
            {index > 0 && <span aria-hidden>/</span>}
            <span className={index === segments.length - 1 ? "text-foreground" : undefined}>
              {segment}
            </span>
          </li>
        ))}
      </ol>
    </nav>
  );
}

/** A line cut to the page's width, its full text in a tooltip. */
function TruncatedLine({ text, details }: { text: string | null; details: readonly string[] }) {
  const full = [text, ...details].filter((part): part is string => part !== null && part !== "");
  if (full.length === 0) return null;
  return (
    <TooltipProvider delayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>
          <p
            tabIndex={0}
            className="focus-visible:ring-ring min-w-0 truncate rounded-sm text-sm outline-none focus-visible:ring-2"
          >
            {text}
            {details.length > 0 && (
              <span className="text-muted-foreground text-xs">
                {text ? " · " : ""}
                {details.join(" · ")}
              </span>
            )}
          </p>
        </TooltipTrigger>
        <TooltipContent className="max-w-md whitespace-pre-line">{full.join("\n")}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

/**
 * The file's read view tabs, the open one kept in the URL as `panel`: the
 * file itself (or an older version of it), what it links to and what uses
 * it, its versions, and the checks of the viewer's draft. Only the open tab
 * reads.
 */
function ReadTabs({
  file,
  ready,
  draftId,
  lockedBy,
  memberNames,
  counts,
}: {
  file: ScopeFile;
  ready: ReadyScope;
  /** The member's open change of the file's owner; null when none is open. */
  draftId: string | null;
  /** The pack of the member's open change when it is another pack's, which disables Restore. */
  lockedBy: string | null;
  memberNames: ReadonlyMap<string, string>;
  /** Each tab's count while it is known. */
  counts: Partial<Record<EditorPanel, number | null>>;
}) {
  const [{ at, compare, panel }, setEditor] = useEditorParams();
  const session = useDraftSession(ready.scope, ready.owners);
  const confirmLeave = useConfirmLeave();
  const open = panel ?? DEFAULT_DETAIL_TAB;
  const owner = ready.owners.find((entry: Owner) => entry.id === file.ownerId);

  const choose = (entry: FileHistoryResponse) => {
    if (!confirmLeave()) return;
    // The chosen version shows in Content, where the reader asked to see it.
    void setEditor({
      ...chooseHistoryEntry(entry, draftId, session.setDraftId),
      panel: null,
    });
  };

  return (
    <Tabs
      value={open}
      onValueChange={(value: string) => {
        const next = EDITOR_PANELS.find((id: EditorPanel) => id === value);
        if (next) void setEditor({ panel: next === DEFAULT_DETAIL_TAB ? null : next });
      }}
    >
      <TabsList>
        {DETAIL_TABS.map((entry) => (
          <TabsTrigger key={entry.id} value={entry.id} className="tabular-nums">
            {tabLabel(entry.label, counts[entry.id] ?? null)}
          </TabsTrigger>
        ))}
      </TabsList>
      <TabsContent value="content">
        {open === "content" &&
          (at !== null ? (
            <OlderVersion
              key={at}
              file={file}
              at={at}
              view="preview"
              currentId={owner?.currentVersionId ?? null}
              draftId={draftId}
              compare={compare}
              onCompare={(next: string | null) => void setEditor({ compare: next })}
              onRestored={(id: string) => {
                session.setDraftId(id);
                void setEditor({ at: null, compare: null });
              }}
              lockedBy={lockedBy}
              onBack={() => void setEditor({ at: null, compare: null })}
            />
          ) : (
            <FileContent key={file.path} file={file} />
          ))}
      </TabsContent>
      <TabsContent value="links">
        {open === "links" && <DependencyPanel file={file} files={ready.files} />}
      </TabsContent>
      <TabsContent value="history">
        {open === "history" && (
          <HistoryPanel
            versionId={file.versionId}
            path={file.path}
            memberNames={memberNames}
            onChoose={choose}
          />
        )}
      </TabsContent>
      <TabsContent value="checks">
        {open === "checks" && <ChecksPanel draftId={draftId} />}
      </TabsContent>
    </Tabs>
  );
}

export type FileDetailProps = {
  file: ScopeFile;
  ready: ReadyScope;
  /** The file's name in its header row, beside its state and Edit. */
  heading: ReactNode;
  /** What the file's kind list says about it; null when it names nothing. */
  summary: KindSummary | null;
  /**
   * The document's description, one line under the header with its full
   * text in a tooltip; the kind list's title stands in when absent.
   */
  description?: string | null;
  /**
   * The path of the document's main file, such as a skill's `SKILL.md`; any
   * other file of the document shows its full path over the header.
   */
  mainPath?: string | null;
  /** What sits above the header, told whether the editor is open. */
  nav?: (editing: boolean) => ReactNode;
  /** A save moved the file to another path. */
  onRenamed: (path: string) => void;
  onDeleted: () => void;
};

/**
 * One delivered file: its path when it is a part of a document; one header
 * row with its name (its size in the name's tooltip), state, badges and
 * Edit; a line with its description and kind details; the read view with its tabs, or the editor view of the
 * file. Edit opens the editor at once: a save goes into the member's open
 * change of the file's pack, or is submitted as a one-edit change, after
 * which the editor closes and the page says the change is in review.
 */
export function FileDetail({
  file,
  ready,
  heading,
  summary,
  description,
  mainPath,
  nav,
  onRenamed,
  onDeleted,
}: FileDetailProps) {
  const [{ at, view }, setEditor] = useEditorParams();
  const session = useDraftSession(ready.scope, ready.owners);
  const source = useEditSource(ready);
  const confirmLeave = useConfirmLeave();
  const [editing, setEditing] = useState(false);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const { edit } = ready;

  const inDraft = edit !== null && file.ownerId === edit.target.id && file.mode !== "SYMLINK";
  const draftId = edit && file.ownerId === edit.target.id ? edit.draft.id : null;
  const ownsFile = session.target !== null && file.ownerId === session.target.id;
  const mayEdit = ownsFile && file.mode !== "SYMLINK" && at === null;
  // A pack's file that an ancestor gives: named with its source and offered for override.
  const inheritedFile = ready.scope.kind === "PACK" && file.ownerId !== ready.scope.packId;
  const origin = ready.origins.get(file.path);
  // A pack's file has no always-on flag of its own; its rule row tells.
  const shown =
    file.alwaysOn === null && summary?.alwaysOn != null
      ? { ...file, alwaysOn: summary.alwaysOn }
      : file;

  const members = useMembers();
  const memberNames = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  const history = useFileHistory(file.versionId, file.path);
  const draftChange = useChange(draftId ?? "");
  const shownVersion = useVersion(at ?? file.versionId).data?.data;
  const historyPages = history.data?.pages ?? [];
  const lastHistoryPage = historyPages[historyPages.length - 1];
  const dependencies = useDependencies(dependencyVersionIds(ready.files, file), file.path);
  const counts = {
    links: linksCount(dependencies.data?.data),
    history: historyCount(
      historyEntriesOf(historyPages).length,
      lastHistoryPage !== undefined && nextFileHistoryPage(lastHistoryPage) === undefined
    ),
  };
  const breadcrumb = breadcrumbOf(file.path, mainPath);
  const state = documentState({
    at,
    version: shownVersion ? { number: shownVersion.number, status: shownVersion.status } : null,
    inherited: edit !== null ? file.ownerId !== edit.target.id : !ownsFile,
    editing: inDraft,
    draftDecidedAt: edit?.draft.decidedAt,
    draftReviews: draftChange.data?.data?.reviews ?? [],
    history: historyEntriesOf(history.data?.pages.slice(0, 1) ?? []),
  });

  const startEditing = () => {
    if (!confirmLeave()) return;
    setSubmitted(null);
    setEditing(true);
  };

  const onSubmitted = (change: SubmittedChange) => {
    setEditing(false);
    setSubmitted(change);
  };

  const onSaved = (path: string) => {
    setEditing(false);
    if (path !== file.path) onRenamed(path);
  };

  return (
    <article className="flex min-w-0 flex-col gap-4" aria-label={file.path}>
      {nav?.(editing)}
      <header className="flex min-w-0 flex-col gap-1">
        {breadcrumb !== null && <Breadcrumb segments={breadcrumb} />}
        <div className="flex flex-wrap items-center gap-2">
          {file.size === null ? (
            heading
          ) : (
            <TooltipProvider delayDuration={0}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <div
                    tabIndex={0}
                    className="focus-visible:ring-ring min-w-0 rounded-sm outline-none focus-visible:ring-2"
                  >
                    {heading}
                  </div>
                </TooltipTrigger>
                <TooltipContent>{formatBytes(file.size)}</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}
          <StateBadge state={state} />
          <FileBadges file={{ ...shown, size: null }} />
          {origin && <OriginBadge origin={origin} />}
          <ItemStatusBadges item={effectiveOf(ready.effective, file.path)} owners={ready.owners} />
          {mayEdit && !editing && (
            <span className="ml-auto flex items-center gap-1">
              <Button
                size="sm"
                onClick={startEditing}
                disabled={source === null || source.kind === "locked"}
              >
                <Pencil aria-hidden />
                Edit
              </Button>
              {source?.kind === "locked" && <ChangeLockTip packName={source.openPackName} />}
            </span>
          )}
        </div>
        <TruncatedLine
          text={description ?? summary?.title ?? null}
          details={summary?.details ?? []}
        />
      </header>
      {submitted !== null && (
        <p className="text-sm">
          <SubmittedNotice submitted={submitted} />
        </p>
      )}
      <StateNote state={state} memberNames={memberNames} />
      {at === null &&
        (inheritedFile ? (
          <InheritedNote file={file} ready={ready} />
        ) : (
          <OverrideNote file={file} ready={ready} />
        ))}
      {!editing ? (
        <ReadTabs
          file={file}
          ready={ready}
          draftId={draftId}
          lockedBy={source?.kind === "locked" ? source.openPackName : null}
          memberNames={memberNames}
          counts={counts}
        />
      ) : source === null || source.kind === "locked" ? (
        <div className="bg-muted h-64 animate-pulse rounded-md" aria-busy="true" />
      ) : (
        <div className="flex min-w-0 flex-col gap-3">
          <ViewToggle view={view} onChange={(next: EditorView) => void setEditor({ view: next })} />
          <SharedPartGate key={file.path} file={file} files={ready.files}>
            {(onBeforeSave: BeforeSave | undefined) => (
              <FileEditor
                key={file.path}
                source={source}
                file={file}
                view={view}
                onBeforeSave={onBeforeSave}
                onSaved={onSaved}
                onDeleted={onDeleted}
                onSubmitted={onSubmitted}
                onCancel={() => setEditing(false)}
              />
            )}
          </SharedPartGate>
        </div>
      )}
    </article>
  );
}
