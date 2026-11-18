"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Lock } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Field, Textarea } from "@oba/ui/sky/field";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { FileDiff } from "@/features/changes/components/diff-view";
import type { ReadyScope } from "@/features/scope/components";
import {
  effectiveOf,
  itemOf,
  lockText,
  overridesLabel,
  overrideStatusText,
} from "@/features/scope/components/effective-items";
import { scopeOfOwner, scopeValue } from "@/features/scope/components/scope-data";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type {
  EffectiveItemResponse,
  EffectiveOverrideResponse,
  Owner,
} from "@/features/scope/schemas";
import { firstIssue, reasonSchema } from "@/lib/forms";
import { describeError } from "@/lib/api/error-text";

import { getVersion, getVersionFile } from "../api";
import {
  useChangeWrite,
  useEditSource,
  type EditSource,
  type SubmittedChange,
} from "../hooks/use-change-write";
import {
  useClearOverride,
  useDeleteDraftFile,
  useSaveDraftFile,
  useSetOverrideBase,
  useVersionDiff,
} from "../hooks/use-versions";
import type { FileDiffResponse, VersionFileResponse } from "../schemas";
import { SubmittedNotice } from "./change-control";
import { useConfirmLeave, useGuardedLink } from "./draft-context";
import { mayBeExecutable } from "./file-editor";
import { ChangeLockTip } from "./open-change";

/**
 * What a list row says about an item's place in the pack tree: an own item
 * that overrides an ancestor's, and whether that ancestor moved since; an
 * inherited item that is locked. Nothing for a plain item.
 */
export function ItemStatusBadges({
  item,
  owners,
}: {
  item: EffectiveItemResponse | undefined;
  owners: readonly Owner[];
}) {
  if (!item) return null;
  if (!item.own) {
    const lock = lockText(item, owners);
    if (lock === null) return null;
    return (
      <Badge variant="muted" title={lock} aria-label={`Locked: ${lock}`}>
        <Lock aria-hidden />
        Locked
      </Badge>
    );
  }
  const { override } = item;
  if (!override) return null;
  return (
    <span className="flex flex-wrap items-center gap-1.5">
      <Badge variant="info">{overridesLabel(override, owners)}</Badge>
      {override.status === "PARENT_UPDATED" && <Badge variant="warning">Parent updated</Badge>}
      {override.status === "PARENT_REMOVED" && <Badge variant="warning">Parent removed</Badge>}
    </span>
  );
}

/** What an override action ended with: a refusal to show, or the one-edit change now in review. */
type ActionResult = { error: string | null; submitted: SubmittedChange | null };

/** The pack a source writes to; null while another pack's change is open or the scope reads only. */
function packOf(source: EditSource | null): string | null {
  if (source?.kind === "change") return source.draft.packId;
  if (source?.kind === "single") return source.packId;
  return null;
}

/**
 * The writes behind the override actions, each resolved to its refusal text
 * or the change it sent to review. Override and Update override are followed
 * by more edits, so they write into the member's open change of the pack,
 * starting one when none is open. Keep, Drop and Keep as own go into the
 * open change, else into a one-edit change submitted with `reason`.
 */
function useOverrideActions(ready: ReadyScope) {
  const source = useEditSource(ready);
  const change = useChangeWrite(source);
  const save = useSaveDraftFile();
  const remove = useDeleteDraftFile();
  const setBase = useSetOverrideBase();
  const clear = useClearOverride();
  const pending =
    change.isPending || save.isPending || remove.isPending || setBase.isPending || clear.isPending;

  const settle = async (act: () => Promise<SubmittedChange | null>): Promise<ActionResult> => {
    try {
      return { error: null, submitted: await act() };
    } catch (failure: unknown) {
      return { error: describeError(failure), submitted: null };
    }
  };

  /** Writes into the open change of the pack, started first when none is open. */
  const inOpenChange = (write: (draftId: string) => Promise<unknown>) =>
    settle(async () => {
      const packId = packOf(source);
      if (packId === null) return change.run(write);
      await write(await change.startChange(packId));
      return null;
    });

  const inChange = (write: (draftId: string) => Promise<unknown>, reason: string | undefined) =>
    settle(() => change.run(write, reason));

  /** The files of an item a version holds; links are rendered by Kaya and never copied. */
  const filesOf = async (versionId: string, item: string): Promise<VersionFileResponse[]> => {
    const version = (await getVersion(versionId)).data;
    if (!version) throw new Error("Kaya did not return the version.");
    return version.files.filter(
      (file: VersionFileResponse) => itemOf(file.path) === item && file.mode !== "SYMLINK"
    );
  };

  const moveBase =
    (path: string, override: EffectiveOverrideResponse) => async (draftId: string) => {
      if (!override.parentVersionId) throw new Error("No ancestor gives this item any more.");
      await setBase.mutateAsync({
        draftId,
        data: { path, baseVersionId: override.parentVersionId },
      });
    };

  return {
    source,
    pending,
    /** Whether Keep, Drop and Keep as own each send a change of their own, which needs a reason. */
    needsReason: source?.kind === "single",
    /** Copies the inherited item into the change; the server records the ancestor's version as its base. */
    override: (item: EffectiveItemResponse) =>
      inOpenChange(async (draftId: string) => {
        const sourceId = item.sourceVersionId;
        if (!sourceId) throw new Error("Kaya did not name the version that gives this item.");
        for (const file of await filesOf(sourceId, item.path)) {
          const content = (await getVersionFile(sourceId, file.path)).data;
          if (!content) throw new Error(`Kaya did not return ${file.path}.`);
          await save.mutateAsync({
            draftId,
            data: {
              path: file.path,
              encoding: content.encoding ?? "UTF_8",
              content: content.content ?? "",
              ...(mayBeExecutable(file.path) ? { executable: file.mode === "EXECUTABLE" } : {}),
            },
          });
        }
      }),
    /** Bases the override on the ancestor's current version in the open change, for the member to edit next. */
    update: (path: string, override: EffectiveOverrideResponse) =>
      inOpenChange(moveBase(path, override)),
    /** Keeps the override as it is and bases it on the ancestor's current version. */
    keep: (path: string, override: EffectiveOverrideResponse, reason?: string) =>
      inChange(moveBase(path, override), reason),
    /** Deletes the item from the pack, so the ancestor's item is inherited again. */
    drop: (path: string, reason?: string) =>
      inChange(async (draftId: string) => {
        for (const file of await filesOf(draftId, path)) {
          await remove.mutateAsync({ draftId, path: file.path });
        }
      }, reason),
    /** Keeps an item the parent removed as a plain own item. */
    keepAsOwn: (path: string, reason?: string) =>
      inChange((draftId: string) => clear.mutateAsync({ draftId, path }), reason),
  };
}

/** What the ancestor changed in the item between the override's base and its current version. */
function ParentChange({ item, override }: { item: string; override: EffectiveOverrideResponse }) {
  const directory = item.endsWith("/");
  const diff = useVersionDiff(
    override.parentVersionId ?? "",
    override.baseVersionId ?? undefined,
    directory ? undefined : item
  );
  if (!override.parentVersionId || !override.baseVersionId) {
    return (
      <p className="text-muted-foreground text-sm">
        The version your override is based on is not known, so no comparison can be shown.
      </p>
    );
  }
  if (diff.error !== null && diff.data === undefined) {
    return (
      <LoadError
        title="Could not load the parent's change"
        message={describeError(diff.error)}
        onRetry={() => void diff.refetch()}
      />
    );
  }
  if (diff.data === undefined) return <TableLoading label="Loading the parent's change" />;
  const files = (diff.data.data ?? []).filter(
    (file: FileDiffResponse) => itemOf(file.path) === item
  );
  if (files.length === 0) {
    return <EmptyState title="No difference" description="The parent's item has the same text." />;
  }
  return (
    <div className="flex flex-col gap-3">
      {files.map((file: FileDiffResponse) => (
        <FileDiff key={file.path} file={file} />
      ))}
    </div>
  );
}

/**
 * The decision an own item's override waits for once its parent moved:
 * update it (the open change takes the new base and the member edits the file),
 * keep it as it is, drop it to inherit the parent's again, or, once the
 * parent removed the item, keep it as an own item.
 */
function OverrideDialog({
  item,
  override,
  ready,
  open,
  onOpenChange,
  onSubmitted,
}: {
  item: string;
  override: EffectiveOverrideResponse;
  ready: ReadyScope;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmitted: (submitted: SubmittedChange) => void;
}) {
  const actions = useOverrideActions(ready);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const removed = override.status === "PARENT_REMOVED";
  const lock = actions.source?.kind === "locked" ? actions.source.openPackName : null;
  const disabled = actions.pending || lock !== null;

  const run = async (action: () => Promise<ActionResult>) => {
    setError(null);
    const result = await action();
    if (result.error !== null) {
      setError(result.error);
      return;
    }
    setReason("");
    onOpenChange(false);
    if (result.submitted !== null) onSubmitted(result.submitted);
  };

  /** A one-edit action checks its reason before anything is sent. */
  const withReason = (action: (reason: string | undefined) => Promise<ActionResult>) => () => {
    if (!actions.needsReason) return action(undefined);
    const parsed = reasonSchema.safeParse(reason);
    if (!parsed.success) {
      return Promise.resolve({ error: firstIssue(parsed.error), submitted: null });
    }
    return action(parsed.data);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{removed ? "Parent removed" : "Parent updated"}</DialogTitle>
          <DialogDescription>{overrideStatusText(override, ready.owners)}</DialogDescription>
        </DialogHeader>
        {!removed && (
          <div className="max-h-[50vh] overflow-auto">
            <ParentChange item={item} override={override} />
          </div>
        )}
        <p className="text-muted-foreground text-sm">
          {removed
            ? "Keep as own leaves the item in your pack without a parent."
            : "Update override moves the base in your open change, where you then take the parent's change. Keep override only moves the base. Drop override deletes your item, so the parent's is inherited again."}{" "}
          {actions.needsReason
            ? "Each goes to review as a change of its own; Update override starts a change you submit from the top bar."
            : "Each goes into your open change."}
        </p>
        {actions.needsReason && (
          <Field
            label="Reason"
            hint={`${reason.trim().length} of 10 to 1000 characters; sent with the change for review.`}
          >
            <Textarea
              value={reason}
              maxLength={1000}
              onChange={(event) => setReason(event.target.value)}
            />
          </Field>
        )}
        {error !== null && (
          <p className="text-error text-sm" role="alert">
            {error}
          </p>
        )}
        <DialogFooter className="items-center">
          {lock !== null && <ChangeLockTip packName={lock} />}
          {removed ? (
            <Button
              disabled={disabled}
              onClick={() => void run(withReason((given) => actions.keepAsOwn(item, given)))}
            >
              Keep as own
            </Button>
          ) : (
            <>
              <Button
                variant="outline"
                disabled={disabled}
                onClick={() => void run(withReason((given) => actions.drop(item, given)))}
              >
                Drop override
              </Button>
              <Button
                variant="outline"
                disabled={disabled}
                onClick={() => void run(withReason((given) => actions.keep(item, override, given)))}
              >
                Keep override
              </Button>
              <Button
                disabled={disabled}
                onClick={() => void run(() => actions.update(item, override))}
              >
                Update override
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** An own item's override that waits for a decision, with the dialog that makes it. */
export function OverrideNote({ file, ready }: { file: ScopeFile; ready: ReadyScope }) {
  const [open, setOpen] = useState(false);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const confirmLeave = useConfirmLeave();
  const item = effectiveOf(ready.effective, file.path);
  const override = item?.own ? item.override : null;
  if (!item || !override || override.status === "CURRENT") return null;
  return (
    <Alert>
      <AlertTitle>
        {override.status === "PARENT_REMOVED" ? "Parent removed" : "Parent updated"}
      </AlertTitle>
      <AlertDescription>
        <p>{overrideStatusText(override, ready.owners)}</p>
        {submitted !== null && <SubmittedNotice submitted={submitted} />}
        <Button
          size="sm"
          variant="outline"
          className="mt-2"
          onClick={() => {
            if (confirmLeave()) setOpen(true);
          }}
        >
          Review
        </Button>
      </AlertDescription>
      <OverrideDialog
        item={item.path}
        override={override}
        ready={ready}
        open={open}
        onOpenChange={setOpen}
        onSubmitted={setSubmitted}
      />
    </Alert>
  );
}

/**
 * Says which ancestor a pack's inherited file comes from, with a link to it,
 * and offers Override, which copies the item into the member's open change
 * of the pack, or names the lock that forbids it.
 */
export function InheritedNote({ file, ready }: { file: ScopeFile; ready: ReadyScope }) {
  const pathname = usePathname();
  const guard = useGuardedLink();
  const confirmLeave = useConfirmLeave();
  const actions = useOverrideActions(ready);
  const [error, setError] = useState<string | null>(null);
  const owner = ready.owners.find((entry: Owner) => entry.id === file.ownerId);
  const item = effectiveOf(ready.effective, file.path);
  const lock = item ? lockText(item, ready.owners) : null;
  const canOverride = item !== undefined && !item.own && lock === null && file.mode !== "SYMLINK";
  const changeLock = actions.source?.kind === "locked" ? actions.source.openPackName : null;

  return (
    <div className="flex flex-col gap-2">
      <p className="text-muted-foreground text-sm">
        {owner ? `Inherited from pack ${owner.name}.` : "Inherited from an ancestor pack."}{" "}
        {owner && (
          <Link
            href={`${pathname}?scope=${encodeURIComponent(scopeValue(scopeOfOwner(owner)))}`}
            onClick={guard}
            className="text-foreground underline underline-offset-2"
          >
            Open {owner.name}
          </Link>
        )}
      </p>
      {lock !== null && (
        <p className="flex items-center gap-1.5 text-sm">
          <Lock className="size-3.5 shrink-0" aria-hidden />
          {lock}, so this pack cannot override it.
        </p>
      )}
      {canOverride && (
        <div className="flex items-center gap-1.5">
          <Button
            size="sm"
            variant="outline"
            className="w-fit"
            disabled={actions.pending || changeLock !== null || actions.source === null}
            onClick={async () => {
              if (!confirmLeave()) return;
              setError(null);
              setError((await actions.override(item)).error);
            }}
          >
            Override
          </Button>
          {changeLock !== null && <ChangeLockTip packName={changeLock} />}
        </div>
      )}
      {error !== null && (
        <p className="text-error text-sm" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
