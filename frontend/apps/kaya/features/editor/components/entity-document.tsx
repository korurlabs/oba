"use client";

import { useState } from "react";
import { Pencil } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Button } from "@oba/ui/sky/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { LoadError } from "@oba/ui/sky/page-states";
import { STATUS_BADGES } from "@/features/changes/components/change-data";
import { useChange, useFileHistory } from "@/features/changes/hooks/use-changes";
import type { ReadyScope } from "@/features/scope/components";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import { useMembers } from "@/features/members/hooks/use-members";
import { useConfirmLeave } from "@/features/versions/components/draft-context";
import { FilePane, type BeforeSave } from "@/features/versions/components/file-editor";
import { FileBadges, OriginBadge } from "@/features/versions/components/file-viewer";
import {
  InheritedNote,
  ItemStatusBadges,
  OverrideNote,
} from "@/features/versions/components/override-status";
import { effectiveOf } from "@/features/scope/components/effective-items";
import { SubmittedNotice } from "@/features/versions/components/change-control";
import { ChangeLockTip } from "@/features/versions/components/open-change";
import { useEditSource, type SubmittedChange } from "@/features/versions/hooks/use-change-write";
import { useDraftSession } from "@/features/versions/hooks/use-draft-session";
import {
  useRestoreVersion,
  useVersion,
  useVersionFile,
} from "@/features/versions/hooks/use-versions";
import type { FileHistoryResponse } from "@/features/changes/schemas";
import { describeError } from "@/lib/api/error-text";

import { useEditorParams, type EditorView } from "../hooks/use-editor-params";
import { CompareView } from "./compare-view";
import { DocumentBody } from "./document-body";
import { DocumentPanels } from "./document-panels";
import { documentState } from "./document-state";
import { isMarkdownPath } from "./editor-language";
import { SharedPartGate } from "./shared-part-gate";
import { StateBadge, StateNote } from "./state-badge";
import { VersionMenu } from "./version-menu";
import { historyEntriesOf, historyTarget } from "./version-menu-data";
import { ViewToggle } from "./view-toggle";

export type EntityDocumentProps = {
  file: ScopeFile;
  ready: ReadyScope;
  onSaved: (path: string) => void;
  onDeleted: () => void;
};

/**
 * An older version of the file, read-only, with a banner naming it, the
 * comparisons with the current version and with the member's open change,
 * and a restore of that whole version as a new change.
 */
export function OlderVersion({
  file,
  at,
  view,
  currentId,
  draftId,
  compare,
  onCompare,
  onRestored,
  onBack,
  lockedBy = null,
}: {
  file: ScopeFile;
  at: string;
  view: EditorView;
  /** The current version of the file's pack; null when it has none. */
  currentId: string | null;
  /** The member's open change in this scope, when it edits the file's owner. */
  draftId: string | null;
  compare: string | null;
  onCompare: (versionId: string | null) => void;
  onRestored: (draftId: string) => void;
  onBack: () => void;
  /** The pack of the member's open change when it is another pack's, which disables Restore. */
  lockedBy?: string | null;
}) {
  const version = useVersion(at);
  const content = useVersionFile(at, file.path);
  const restore = useRestoreVersion();
  const detail = version.data?.data;
  const data = content.data?.data;
  const name = detail
    ? `Version ${detail.number}, ${STATUS_BADGES[detail.status].label.toLowerCase()}`
    : "An older version";

  return (
    <div className="flex min-w-0 flex-col gap-3">
      <Alert>
        <AlertTitle>{name}</AlertTitle>
        <AlertDescription>
          <p>You are reading an older version of this file; it cannot be edited.</p>
          <div className="mt-2 flex flex-wrap gap-2">
            {currentId !== null && currentId !== at && (
              <Button
                size="sm"
                variant={compare === currentId ? "default" : "outline"}
                onClick={() => onCompare(compare === currentId ? null : currentId)}
              >
                Compare with current
              </Button>
            )}
            {draftId !== null && (
              <Button
                size="sm"
                variant={compare === draftId ? "default" : "outline"}
                onClick={() => onCompare(compare === draftId ? null : draftId)}
              >
                Compare with my change
              </Button>
            )}
            {detail && detail.status !== "DRAFT" && lockedBy !== null && (
              <span className="flex items-center gap-1">
                <Button size="sm" variant="outline" disabled>
                  Restore this version
                </Button>
                <ChangeLockTip packName={lockedBy} />
              </span>
            )}
            {detail && detail.status !== "DRAFT" && lockedBy === null && (
              <ConfirmDialog
                trigger={
                  <Button size="sm" variant="outline">
                    Restore this version
                  </Button>
                }
                title={`Restore version ${detail.number}`}
                description={
                  <p>
                    A new change with the files of version {detail.number}, based on the current
                    version. Nothing changes until that change is approved.
                  </p>
                }
                confirmLabel="Restore"
                onConfirm={async () => {
                  const created = await restore.mutateAsync(at);
                  const id = created.data?.id;
                  if (!id)
                    throw new Error("Kaya restored the version but did not name the change.");
                  onRestored(id);
                }}
              />
            )}
            <Button size="sm" variant="ghost" onClick={onBack}>
              Back to the file
            </Button>
          </div>
        </AlertDescription>
      </Alert>
      {compare !== null ? (
        <CompareView
          versionId={compare}
          against={at}
          path={file.path}
          label={compare === draftId ? "your change" : "the current version"}
        />
      ) : content.error !== null && data === undefined ? (
        <LoadError
          title="Could not load this version of the file"
          message={describeError(content.error)}
          onRetry={() => void content.refetch()}
        />
      ) : !data ? (
        <div className="bg-muted h-64 animate-pulse rounded-md" aria-busy="true" />
      ) : data.encoding === "BASE64" || typeof data.content !== "string" ? (
        <p className="text-muted-foreground text-sm">This version of the file is not text.</p>
      ) : (
        <DocumentBody
          path={file.path}
          text={data.content}
          view={view}
          label={`Content of ${file.path} in ${name.toLowerCase()}`}
        />
      )}
    </div>
  );
}

/**
 * What choosing a version in a file's history shows: the member's change
 * opens as the live document; the current version is the live document
 * unless an open change takes its place; any other version shows read-only.
 * Opens the change through `setDraftId` and returns the document's URL state.
 */
export function chooseHistoryEntry(
  entry: FileHistoryResponse,
  /** The member's open change of the file's owner; null when none is open. */
  draftId: string | null,
  setDraftId: (draftId: string) => void
): { at: string | null; compare: null } {
  const target = historyTarget(entry);
  if (target === "draft") {
    setDraftId(entry.versionId);
    return { at: null, compare: null };
  }
  const live = target === "current" && draftId === null;
  return { at: live ? null : entry.versionId, compare: null };
}

/**
 * One entity of a page as a document: its path, state, badges and origin in
 * the header; a version menu, the view choice for Markdown and Edit in the
 * toolbar; the file in an editor while the member edits it, else read-only,
 * or an older version with its comparisons; beside it the Links, History and
 * Checks panels. Inside the member's open change of the file's pack the
 * editor is always open; without one, Edit opens it for a one-edit change,
 * and once that is submitted the editor closes and the page says so.
 */
export function EntityDocument({ file, ready, onSaved, onDeleted }: EntityDocumentProps) {
  const [{ at, view, compare }, setEditor] = useEditorParams();
  const session = useDraftSession(ready.scope, ready.owners);
  const source = useEditSource(ready);
  const confirmLeave = useConfirmLeave();
  const [editingSingle, setEditingSingle] = useState(false);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const { edit } = ready;
  const owner = ready.owners.find((entry: Owner) => entry.id === file.ownerId);
  const ownsFile = session.target !== null && file.ownerId === session.target.id;
  const editable = file.mode !== "SYMLINK";
  const inChange = edit !== null && file.ownerId === edit.target.id && editable;
  const editing = inChange || (editingSingle && source?.kind === "single");
  // The pane edits only while the member does: always inside an open change, else after Edit.
  const paneSource = edit !== null || editing ? source : null;
  const lockedBy = source?.kind === "locked" ? source.openPackName : null;
  const origin = ready.origins.get(file.path);
  // A pack's file that an ancestor gives: named with its source and offered for override.
  const inheritedFile = ready.scope.kind === "PACK" && file.ownerId !== ready.scope.packId;
  const draftId = edit && file.ownerId === edit.target.id ? edit.draft.id : null;

  const members = useMembers();
  const memberNames = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  // The version menu reads the same history, so this costs no extra read.
  const history = useFileHistory(file.versionId, file.path);
  const draftChange = useChange(draftId ?? "");
  const shown = useVersion(at ?? file.versionId).data?.data;
  const state = documentState({
    at,
    version: shown ? { number: shown.number, status: shown.status } : null,
    inherited: edit !== null ? file.ownerId !== edit.target.id : !ownsFile,
    editing,
    draftDecidedAt: edit?.draft.decidedAt,
    draftReviews: draftChange.data?.data?.reviews ?? [],
    history: historyEntriesOf(history.data?.pages.slice(0, 1) ?? []),
  });

  const choose = (entry: FileHistoryResponse) => {
    if (!confirmLeave()) return;
    void setEditor(chooseHistoryEntry(entry, draftId, session.setDraftId));
  };

  const startEditing = () => {
    if (!confirmLeave()) return;
    setSubmitted(null);
    setEditingSingle(true);
  };

  const onSubmitted = (change: SubmittedChange) => {
    setEditingSingle(false);
    setSubmitted(change);
  };

  return (
    <article
      className="grid min-w-0 grid-cols-1 items-start gap-6 2xl:grid-cols-[minmax(0,1fr)_22rem]"
      aria-label={file.path}
    >
      <div className="flex min-w-0 flex-col gap-4">
        <header className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <code className="min-w-0 font-mono text-sm break-all">{file.path}</code>
            <StateBadge state={state} />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <FileBadges file={file} />
            {origin && <OriginBadge origin={origin} />}
            <ItemStatusBadges
              item={effectiveOf(ready.effective, file.path)}
              owners={ready.owners}
            />
          </div>
        </header>
        <div className="flex flex-wrap items-center gap-2" role="toolbar" aria-label="Document">
          <VersionMenu versionId={file.versionId} path={file.path} at={at} onChoose={choose} />
          {isMarkdownPath(file.path) && (
            <ViewToggle
              view={view}
              onChange={(next: EditorView) => void setEditor({ view: next })}
            />
          )}
          {edit === null && at === null && ownsFile && editable && !editing && (
            <span className="flex items-center gap-1">
              <Button
                size="sm"
                onClick={startEditing}
                disabled={source === null || source.kind === "locked"}
              >
                <Pencil aria-hidden />
                Edit
              </Button>
              {lockedBy !== null && <ChangeLockTip packName={lockedBy} />}
            </span>
          )}
        </div>
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
        {at !== null ? (
          <OlderVersion
            key={at}
            file={file}
            at={at}
            view={view}
            currentId={owner?.currentVersionId ?? null}
            draftId={draftId}
            compare={compare}
            onCompare={(next: string | null) => void setEditor({ compare: next })}
            onRestored={(id: string) => {
              session.setDraftId(id);
              void setEditor({ at: null, compare: null });
            }}
            onBack={() => void setEditor({ at: null, compare: null })}
            lockedBy={lockedBy}
          />
        ) : editing ? (
          <SharedPartGate key={file.path} file={file} files={ready.files}>
            {(onBeforeSave: BeforeSave | undefined) => (
              <FilePane
                file={file}
                source={paneSource}
                owners={ready.owners}
                repositoryScope={ready.scope.kind === "REPOSITORY"}
                ownerNote={!inheritedFile}
                view={view}
                onBeforeSave={onBeforeSave}
                onSaved={onSaved}
                onDeleted={onDeleted}
                onSubmitted={onSubmitted}
                onCancel={inChange ? undefined : () => setEditingSingle(false)}
              />
            )}
          </SharedPartGate>
        ) : (
          <FilePane
            file={file}
            source={paneSource}
            owners={ready.owners}
            repositoryScope={ready.scope.kind === "REPOSITORY"}
            ownerNote={!inheritedFile}
            view={view}
            onSaved={onSaved}
            onDeleted={onDeleted}
          />
        )}
      </div>
      <DocumentPanels
        file={file}
        files={ready.files}
        draftId={draftId}
        memberNames={memberNames}
        onChoose={choose}
      />
    </article>
  );
}
