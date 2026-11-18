"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import { Button } from "@oba/ui/sky/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field, Input } from "@oba/ui/sky/field";
import { LoadError } from "@oba/ui/sky/page-states";
import { DocumentBody } from "@/features/editor/components/document-body";
import type { EditorView } from "@/features/editor/hooks/use-editor-params";
import { ReasonDialog } from "@/components/reason-dialog";
import { useDeleteDraftFile, useSaveDraftFile } from "../hooks/use-versions";
import { useChangeWrite, type EditSource, type SubmittedChange } from "../hooks/use-change-write";
import type { DraftFileSaveRequest } from "../schemas";
import { describeError } from "@/lib/api/error-text";

import { useVersionFile, useVersion } from "../hooks/use-versions";
import { useOwners } from "@/features/scope/hooks/use-owners";
import type { VersionDetailResponse, VersionFileResponse } from "../schemas";
import { ownerLabel } from "@/features/scope/components/owner-labels";
import type { Owner } from "@/features/scope/schemas";
import { formatBytes, type ScopeFile } from "@/features/scope/components/scope-files";
import { useGuardedLink, useLiveEdit, type EditOrigin, type LiveEdit } from "./draft-context";
import { ChangeLockTip } from "./open-change";
import {
  checkFile,
  isHookPath,
  lineCount,
  measureOf,
  textBytes,
  type BaseMeasure,
  type FileIssue,
} from "./draft-rules";
import { FileViewer } from "./file-viewer";
import { mountRefOf, type MountRef } from "@/features/scope/components/mount-paths";
import { effectiveByItem, lockRefusal } from "@/features/scope/components/effective-items";
import { usePackEffective } from "@/features/scope/hooks/use-effective";
import { scopeOfOwner, scopeValue } from "@/features/scope/components/scope-data";

export type FileEditorProps = {
  /**
   * Where a save goes: into the open change of the file's pack, into a
   * one-edit change submitted with a reason, or nowhere while a change of
   * another pack is open.
   */
  source: EditSource;
  /** The pack's file to edit; null to create one. */
  file: ScopeFile | null;
  /** The path a new file starts with, such as `.agents/rules/`. */
  initialPath?: string;
  /** Whether a new file starts executable, as a hook script does. */
  initialExecutable?: boolean;
  initialContent?: string;
  /** How a Markdown file shows: text, preview or both. */
  view?: EditorView;
  /**
   * Asked before each save, Ctrl+S included; the save goes ahead only when it
   * answers true, and the typed text stays either way.
   */
  onBeforeSave?: BeforeSave;
  /** After a save into the open change, with the path the file now has. */
  onSaved?: (path: string) => void;
  /** After the file was deleted from the open change. */
  onDeleted?: () => void;
  /** After a one-edit change, a save or a delete, went to review. */
  onSubmitted?: (submitted: SubmittedChange) => void;
  onCancel?: () => void;
};

/** Whether a save may go ahead, as a question the page may put to the member. */
export type BeforeSave = () => boolean | Promise<boolean>;

/** Whether a save goes ahead: always without a question, else when its answer is yes. */
export async function saveConfirmed(onBeforeSave: BeforeSave | undefined): Promise<boolean> {
  return onBeforeSave === undefined || (await onBeforeSave());
}

const SKILL_PART = /^\.(agents|claude)\/skills\/[^/]+\/.+$/;

/**
 * Whether a path may carry the executable bit: a hook, or a file of a skill
 * directory other than its `SKILL.md`, which the server then stores as a
 * script. The server refuses the bit on any other path.
 */
export function mayBeExecutable(path: string): boolean {
  if (isHookPath(path)) return true;
  return SKILL_PART.test(path) && !path.endsWith("/SKILL.md");
}

/**
 * The measure of the base version's file at a path, for the size waiver:
 * null when the base has no such file, undefined while it loads.
 */
function useBaseMeasure(baseId: string, path: string): BaseMeasure | null | undefined {
  const base = useVersion(baseId);
  const baseFile = base.data?.data?.files.find((entry: VersionFileResponse) => entry.path === path);
  const content = useVersionFile(baseFile ? baseId : "", baseFile ? path : "");
  if (baseId === "") return null;
  if (!base.data?.data) return base.error !== null ? null : undefined;
  if (!baseFile) return null;
  const data = content.data?.data;
  if (!data)
    return content.error !== null
      ? { bytes: baseFile.size ?? 0, lines: null, listing: null }
      : undefined;
  if (data.encoding === "BASE64" || typeof data.content !== "string") {
    return { bytes: baseFile.size ?? 0, lines: null, listing: null };
  }
  return measureOf(data.content);
}

/**
 * The editor's own text once a save returns: back to the saved file when it
 * still holds what was sent, else the text typed while the save ran, which
 * the draft does not have yet.
 */
export function textAfterSave(now: string | null, sent: string | null): string | null {
  return now === sent ? null : now;
}

/**
 * What the editor publishes as its live edit. A text file carries its text;
 * a binary file has none, yet still reports a rename that is not saved.
 * Nothing is published while the file's text loads.
 */
export function liveEditOf(state: {
  path: string;
  executable: boolean;
  /** The text in the editor; null while it loads. */
  text: string | null;
  /** Whether the editor shows no text: a binary file. */
  textless: boolean;
  savedPath: string | null;
  dirty: boolean;
  /** The hash of the saved content the editor loaded; null for a new file. */
  savedSha256?: string | null;
}): LiveEdit | null {
  if (!state.textless && state.text === null) return null;
  const origin: EditOrigin =
    state.savedPath === null
      ? { path: state.path, sha256: null, isNew: true }
      : { path: state.savedPath, sha256: state.savedSha256 ?? null, isNew: false };
  return {
    path: state.path,
    mode: state.executable ? "EXECUTABLE" : "FILE",
    text: state.textless ? null : state.text,
    replaces: state.savedPath !== null && state.savedPath !== state.path ? state.savedPath : null,
    dirty: state.dirty,
    origin,
  };
}

/**
 * The body a save sends: the whole file as typed, the executable bit only
 * where the server takes it, and the override marker the file carries.
 */
export function saveBody(input: {
  path: string;
  text: string;
  binary: boolean;
  executable: boolean;
  overridesPath: string | null;
}): DraftFileSaveRequest {
  const body: DraftFileSaveRequest = {
    path: input.path,
    encoding: input.binary ? "BASE64" : "UTF_8",
    content: input.text,
  };
  if (mayBeExecutable(input.path)) body.executable = input.executable;
  if (input.overridesPath !== null) body.overridesPath = input.overridesPath;
  return body;
}

/**
 * What a pack may store: its mount, for the namespace and rule scope checks
 * (null when it is mounted nowhere), and the refusal of a path whose item an
 * ancestor gives locked (null while unknown or when free). The version is the
 * one the edit starts from; null reads the pack's current version.
 */
function useDraftLimits(
  packId: string,
  versionId: string | null
): {
  mount: MountRef | null;
  lockOf: (path: string) => string | null;
} {
  const { owners } = useOwners();
  const effective = usePackEffective(packId, versionId);
  const items = effectiveByItem(effective.data?.data ?? []);
  return {
    mount: mountRefOf(owners?.find((owner: Owner) => owner.id === packId)),
    lockOf: (path: string) => lockRefusal(path, items, owners ?? []),
  };
}

/**
 * The version an edit starts from: the open change, else the pack's current
 * version (null for a pack without one, and while another pack's change is open).
 */
function startVersion(source: EditSource): VersionDetailResponse | null {
  if (source.kind === "change") return source.draft;
  if (source.kind === "single") return source.current;
  return null;
}

/**
 * Edits one file of a pack: its path (a new path renames it, as a save at
 * the new path and a delete of the old), its text, and for a hook or a skill
 * script its executable bit. In the open change a save stores the text
 * exactly as typed and the editor keeps it. Without one, Submit asks a reason
 * and sends the edit to review as a change of its own. A refused save keeps
 * the text and shows the server's reason. Ctrl+S saves or submits.
 */
export function FileEditor({
  source,
  file,
  initialPath = "",
  initialExecutable = false,
  initialContent = "",
  view = "edit",
  onBeforeSave,
  onSaved,
  onDeleted,
  onSubmitted,
  onCancel,
}: FileEditorProps) {
  const isNew = file === null;
  const start = startVersion(source);
  const single = source.kind === "single";
  const locked = source.kind === "locked";
  // Another pack's change being open leaves no version to start from; the page's own file is read instead.
  const loaded = useVersionFile(isNew ? "" : (start?.id ?? file.versionId), file?.path ?? "");
  const data = loaded.data?.data;
  const binary = data?.encoding === "BASE64";
  const savedText = isNew ? initialContent : binary ? null : (data?.content ?? null);
  const savedExecutable = isNew ? initialExecutable : file.mode === "EXECUTABLE";

  const [path, setPath] = useState(file?.path ?? initialPath);
  const [executable, setExecutable] = useState(savedExecutable);
  const [text, setText] = useState<string | null>(isNew ? initialContent : null);
  const [error, setError] = useState<string | null>(null);
  const [reasonOpen, setReasonOpen] = useState(false);
  const save = useSaveDraftFile();
  const remove = useDeleteDraftFile();
  const changeWrite = useChangeWrite(source);
  const { setLive } = useLiveEdit();
  const savedPath = file?.path ?? null;
  const savedSha256 = data?.sha256 ?? file?.sha256 ?? null;
  const packId = source.kind === "change" ? source.draft.packId : single ? source.packId : "";
  const { mount, lockOf } = useDraftLimits(
    packId,
    source.kind === "change" ? source.draft.id : null
  );
  // The server stores the marker each save sends, so a save of an overriding file sends it again.
  const overridesPath = file?.overridesPath ?? null;

  // The saved text arrives after the first render; the editor starts from it once.
  const current = text ?? savedText;
  const trimmedPath = path.trim();
  // A one-edit change is based on the current version, so that version is its own base.
  const base = useBaseMeasure(
    (source.kind === "change" ? source.draft.baseVersionId : start?.id) ?? "",
    trimmedPath
  );
  const executableShown = mayBeExecutable(trimmedPath);
  const executableSent = executableShown && executable;

  const dirty =
    isNew ||
    trimmedPath !== file.path ||
    executableSent !== savedExecutable ||
    (!binary && current !== savedText);
  // A new file is worth keeping once the member typed into it; the template alone is not.
  const unsaved = isNew
    ? current !== initialContent ||
      trimmedPath !== initialPath.trim() ||
      executableSent !== initialExecutable
    : dirty;

  useEffect(() => {
    setLive(
      liveEditOf({
        path: trimmedPath,
        executable: executableSent,
        text: current,
        textless: binary,
        savedPath,
        savedSha256,
        dirty: unsaved,
      })
    );
  }, [current, executableSent, binary, trimmedPath, savedPath, savedSha256, unsaved, setLive]);
  useEffect(() => () => setLive(null), [setLive]);

  if (!isNew && loaded.error !== null && data === undefined) {
    return (
      <LoadError
        title="Could not load the file"
        message={describeError(loaded.error)}
        onRetry={() => void loaded.refetch()}
      />
    );
  }
  if (!isNew && data === undefined) {
    return <div className="bg-muted h-64 animate-pulse rounded-md" aria-busy="true" />;
  }

  const taken =
    trimmedPath !== file?.path &&
    (start?.files ?? []).some((entry: VersionFileResponse) => entry.path === trimmedPath);
  const mode = executableSent ? "EXECUTABLE" : "FILE";
  const itemLock = lockOf(trimmedPath);
  const issues: FileIssue[] = [
    ...(itemLock !== null ? [{ rule: "ITEM_LOCKED", message: itemLock }] : []),
    ...(taken
      ? [
          {
            rule: "PATH_TAKEN",
            message: single
              ? "The pack has a file at this path; open that file instead."
              : "The change has a file at this path; open that file instead.",
          },
        ]
      : []),
    ...(binary
      ? checkFile({ path: trimmedPath, mode, content: "" }, base, mount).filter(
          (issue) =>
            issue.rule === "PATH_NOT_ALLOWED" || issue.rule === "MOUNT_PATH_OUTSIDE_NAMESPACE"
        )
      : checkFile({ path: trimmedPath, mode, content: current ?? "" }, base, mount)),
  ];
  const busy = save.isPending || remove.isPending || changeWrite.isPending;
  const blocked = locked || busy || !dirty || issues.length > 0;

  /** Writes the file as typed into a change: a save, and on a rename the delete of the old path. */
  const writeFile = async (draftId: string) => {
    const body = saveBody({
      path: trimmedPath,
      text: binary ? (data?.content ?? "") : (current ?? ""),
      binary,
      executable,
      overridesPath,
    });
    await save.mutateAsync({ draftId, data: body });
    if (file && file.path !== trimmedPath) {
      await remove.mutateAsync({ draftId, path: file.path });
    }
  };

  const submit = async () => {
    if (blocked) return;
    if (!(await saveConfirmed(onBeforeSave))) return;
    setError(null);
    if (single) {
      setReasonOpen(true);
      return;
    }
    const sent = text;
    try {
      await changeWrite.run(writeFile);
      setText((now: string | null) => textAfterSave(now, sent));
      onSaved?.(trimmedPath);
    } catch (failure: unknown) {
      setError(describeError(failure));
    }
  };

  /** A one-edit change: refusals reach the reason dialog, which keeps the reason and the text. */
  const submitWithReason = async (reason: string) => {
    const submitted = await changeWrite.run(writeFile, reason);
    if (submitted !== null) onSubmitted?.(submitted);
  };

  const discard = () => {
    setText(isNew ? initialContent : null);
    setPath(file?.path ?? initialPath);
    setExecutable(savedExecutable);
    setError(null);
  };

  return (
    <form
      className="flex min-w-0 flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        // A reason dialog's own form submits through its portal; React bubbles that here.
        if (event.target !== event.currentTarget) return;
        void submit();
      }}
    >
      {overridesPath !== null && (
        <p className="text-muted-foreground text-sm">
          This file overrides <code className="font-mono break-all">{overridesPath}</code> in the
          folders this pack is mounted at. Once approved, files under those folders follow this
          version only.
        </p>
      )}
      <div className="grid grid-cols-1 items-end gap-3 sm:grid-cols-[1fr_auto]">
        <Field label="Path" hint={isNew ? undefined : "A new path renames the file."}>
          <Input
            value={path}
            onChange={(event) => setPath(event.target.value)}
            className="font-mono"
          />
        </Field>
        {executableShown && (
          <label className="flex h-9 items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={executable}
              onChange={(event) => setExecutable(event.target.checked)}
              disabled={binary}
            />
            Executable
          </label>
        )}
      </div>
      {binary ? (
        <p className="text-muted-foreground text-sm">
          A binary file{data?.size != null ? ` of ${formatBytes(data.size)}` : ""}; it can be
          renamed or deleted here, not edited.
        </p>
      ) : (
        <div className="flex flex-col gap-1">
          <DocumentBody
            path={trimmedPath}
            text={current ?? ""}
            view={view}
            onChange={(next: string) => setText(next)}
            onSave={() => void submit()}
            label={`Content of ${trimmedPath || "the new file"}`}
          />
          <span className="text-muted-foreground text-xs">
            {formatBytes(textBytes(current ?? ""))}, {lineCount(current ?? "")} lines.
            {locked ? "" : single ? " Ctrl+S submits." : " Ctrl+S saves."}
          </span>
        </div>
      )}
      {executableSent && (
        <p className="text-warning-text text-xs">
          An executable file runs on developers&apos; machines; a change to it needs an owner&apos;s
          approval.
        </p>
      )}
      {issues.length > 0 && (
        <ul
          className="text-error flex flex-col gap-1 text-sm"
          role="alert"
          aria-label="Rule checks"
        >
          {issues.map((issue: FileIssue) => (
            <li key={`${issue.rule}:${issue.message}`}>{issue.message}</li>
          ))}
        </ul>
      )}
      {error !== null && (
        <p className="text-error text-sm" role="alert">
          {error}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" size="sm" disabled={blocked}>
          {single || locked ? "Submit" : "Save to change"}
        </Button>
        {source.kind === "locked" && <ChangeLockTip packName={source.openPackName} />}
        {!isNew && dirty && (
          <Button type="button" size="sm" variant="ghost" onClick={discard} disabled={busy}>
            Discard
          </Button>
        )}
        {onCancel && (
          <Button type="button" size="sm" variant="ghost" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
        )}
        {!isNew && source.kind === "change" && (
          <ConfirmDialog
            trigger={
              <Button type="button" size="sm" variant="outline" disabled={busy}>
                Delete file
              </Button>
            }
            title="Delete this file from the change"
            description={
              <p>
                <code className="font-mono break-all">{file.path}</code> is removed from your
                change. The released version keeps it until the change is approved.
              </p>
            }
            confirmLabel="Delete"
            destructive
            onConfirm={async () => {
              await changeWrite.run((draftId: string) =>
                remove.mutateAsync({ draftId, path: file.path })
              );
              onDeleted?.();
            }}
          />
        )}
        {!isNew && single && (
          <ReasonDialog
            trigger={
              <Button type="button" size="sm" variant="outline" disabled={busy}>
                Delete file
              </Button>
            }
            title="Delete this file"
            description={
              <p>
                A change that deletes <code className="font-mono break-all">{file.path}</code> goes
                to review. The released version keeps the file until the change is approved.
              </p>
            }
            confirmLabel="Submit"
            onConfirm={async (reason: string) => {
              const submitted = await changeWrite.run(
                (draftId: string) => remove.mutateAsync({ draftId, path: file.path }),
                reason
              );
              if (submitted !== null) onSubmitted?.(submitted);
            }}
          />
        )}
        {!isNew && locked && (
          <Button type="button" size="sm" variant="outline" disabled>
            Delete file
          </Button>
        )}
        {!isNew && dirty && <span className="text-muted-foreground text-xs">Unsaved changes.</span>}
      </div>
      {single && (
        <ReasonDialog
          open={reasonOpen}
          onOpenChange={setReasonOpen}
          title="Submit this change for review"
          description={
            <p>
              <code className="font-mono break-all">{trimmedPath}</code> goes to review as a change
              of its own. Nothing reaches a repository until the change is approved.
            </p>
          }
          confirmLabel="Submit"
          destructive={false}
          onConfirm={submitWithReason}
        />
      )}
    </form>
  );
}

/** "pack agents", for the middle of a sentence. */
function ownerText(owner: Owner): string {
  return ownerLabel(owner).replace(/^\w+/, (word: string) => word.toLowerCase());
}

/**
 * Says which pack a file comes from, with a link to edit it there: in a
 * repository scope, which is read only, and while another pack is edited.
 */
export function OtherOwnerNote({ file, owners }: { file: ScopeFile; owners: readonly Owner[] }) {
  const pathname = usePathname();
  const guard = useGuardedLink();
  const owner = owners.find((entry: Owner) => entry.id === file.ownerId);
  const scope = owner ? scopeOfOwner(owner) : null;
  return (
    <p className="text-muted-foreground text-sm">
      {owner ? `This file comes from ${ownerText(owner)}` : "This file comes from another pack"}, so
      it is edited there.{" "}
      {scope && (
        <Link
          href={`${pathname}?scope=${encodeURIComponent(scopeValue(scope))}`}
          onClick={guard}
          className="text-foreground underline underline-offset-2"
        >
          Edit it there
        </Link>
      )}
    </p>
  );
}

/** The pack an edit source writes to; null while another pack's change is open. */
function sourcePackId(source: EditSource): string | null {
  if (source.kind === "change") return source.draft.packId;
  if (source.kind === "single") return source.packId;
  return null;
}

/**
 * A page's file pane: the editor for a file of the pack being edited, else
 * the file read-only. A link, such as a skill link, is rendered by Kaya and
 * is never edited by hand, so it stays read-only while editing as well.
 */
export function FilePane({
  file,
  source,
  owners,
  repositoryScope = false,
  ownerNote = true,
  view = "edit",
  onBeforeSave,
  onSaved,
  onDeleted,
  onSubmitted,
  onCancel,
}: {
  file: ScopeFile;
  /** Where the member's edits of the file go while they edit it; null while the page only reads. */
  source: EditSource | null;
  owners: readonly Owner[];
  /** Whether the page shows a repository's release, which is read only, so each file names its pack. */
  repositoryScope?: boolean;
  /** False where the page names the file's pack itself, as for a pack's inherited file. */
  ownerNote?: boolean;
  view?: EditorView;
  /** Asked before each save; see `FileEditorProps.onBeforeSave`. */
  onBeforeSave?: BeforeSave;
  onSaved: (path: string) => void;
  onDeleted: () => void;
  onSubmitted?: (submitted: SubmittedChange) => void;
  onCancel?: () => void;
}) {
  const packId = source === null ? null : sourcePackId(source);
  if (source !== null && packId === file.ownerId && file.mode !== "SYMLINK") {
    return (
      <FileEditor
        key={file.path}
        source={source}
        file={file}
        view={view}
        onBeforeSave={onBeforeSave}
        onSaved={onSaved}
        onDeleted={onDeleted}
        onSubmitted={onSubmitted}
        onCancel={onCancel}
      />
    );
  }
  return (
    <div className="flex min-w-0 flex-col gap-3">
      {ownerNote && (packId !== null ? file.ownerId !== packId : repositoryScope) && (
        <OtherOwnerNote file={file} owners={owners} />
      )}
      <FileViewer key={file.path} file={file} view={view} showHeader={false} />
    </div>
  );
}
