"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type MouseEvent } from "react";
import { GitPullRequest, X } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, NativeSelect } from "@oba/ui/sky/field";
import { rebaseConflictsOf } from "@/features/changes/components/change-data";
import { useSubmitDraft } from "@/features/changes/hooks/use-changes";
import { ownerById } from "@/features/scope/components/owners";
import { editTarget } from "@/features/scope/components/scope-data";
import { useOwners } from "@/features/scope/hooks/use-owners";
import { useScope } from "@/features/scope/hooks/use-scope";
import type { Owner } from "@/features/scope/schemas";
import { ReasonDialog } from "@/components/reason-dialog";
import { describeError } from "@/lib/api/error-text";

import { useChangeWrite } from "../hooks/use-change-write";
import { useRebaseDraft, useVersionDiff } from "../hooks/use-versions";
import type { RebaseConflict, VersionDetailResponse } from "../schemas";
import {
  UNSAVED_EDIT_BLOCKER,
  mayLeavePage,
  unsavedEditOrigin,
  useHasUnsavedEdit,
} from "./draft-context";
import { useOpenChange } from "./open-change";

/**
 * Why a change cannot be submitted yet: a stale base must be rebased, and
 * text open in an editor would be left out of the change.
 */
export function submitBlocker(stale: boolean, unsaved: boolean): string | null {
  if (stale) return "Rebase the change onto the current version first.";
  if (unsaved) return UNSAVED_EDIT_BLOCKER;
  return null;
}

/** Says that a change went to review, with a link to it. */
export function SubmittedNotice({ submitted }: { submitted: { id: string; number: number } }) {
  return (
    <span role="status">
      Version {submitted.number} is in review.{" "}
      <Link
        href={`/changes/${encodeURIComponent(submitted.id)}`}
        className="underline underline-offset-2"
      >
        Open the change
      </Link>
    </span>
  );
}

const CHANGE_WORDS: Readonly<Record<string, string>> = {
  ADDED: "added",
  MODIFIED: "changed",
  DELETED: "deleted",
};

function sideText(change: string | null | undefined): string {
  return change ? (CHANGE_WORDS[change] ?? change.toLowerCase()) : "left as delivered";
}

/**
 * Moves the change onto its owner's current version. A path both sides
 * changed stops the rebase; the dialog then lists each such path and asks
 * which side wins, and the next rebase sends those choices.
 */
export function RebaseDialog({ draftId }: { draftId: string }) {
  const rebase = useRebaseDraft();
  const [open, setOpen] = useState(false);
  const [conflicts, setConflicts] = useState<RebaseConflict[] | null>(null);
  const [choices, setChoices] = useState<Record<string, "draft" | "current">>({});
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setError(null);
    const keepDraft = Object.keys(choices).filter((path) => choices[path] === "draft");
    const takeCurrent = Object.keys(choices).filter((path) => choices[path] === "current");
    try {
      await rebase.mutateAsync({ draftId, data: { keepDraft, takeCurrent } });
      setOpen(false);
      setConflicts(null);
      setChoices({});
    } catch (failure: unknown) {
      const found = rebaseConflictsOf(failure);
      if (found !== null && found.length > 0) {
        setConflicts(found);
        setChoices((previous) =>
          Object.fromEntries(
            found.map((conflict) => [conflict.path, previous[conflict.path] ?? "draft"])
          )
        );
      }
      setError(describeError(failure));
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        if (!next) setError(null);
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">Rebase</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Rebase this change</DialogTitle>
          <DialogDescription asChild>
            <div>
              The change moves onto the current version and keeps your edits. When a path changed on
              both sides, choose which side wins.
            </div>
          </DialogDescription>
        </DialogHeader>
        {conflicts !== null && conflicts.length > 0 && (
          <ul className="flex max-h-[50vh] flex-col gap-3 overflow-y-auto" aria-label="Conflicts">
            {conflicts.map((conflict: RebaseConflict) => (
              <li key={conflict.path} className="flex flex-col gap-1.5 rounded-md border p-3">
                <code className="font-mono text-sm break-all">{conflict.path}</code>
                <span className="text-muted-foreground text-xs">
                  Your change {sideText(conflict.draftChange)} it; the current version{" "}
                  {sideText(conflict.currentChange)} it.
                </span>
                <div className="flex flex-wrap gap-4 text-sm">
                  {(["draft", "current"] as const).map((side) => (
                    <label key={side} className="flex items-center gap-1.5">
                      <input
                        type="radio"
                        name={`side-${conflict.path}`}
                        checked={choices[conflict.path] === side}
                        onChange={() =>
                          setChoices((previous) => ({ ...previous, [conflict.path]: side }))
                        }
                      />
                      {side === "draft" ? "Keep mine" : "Take the current version's"}
                    </label>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        )}
        {error !== null && (
          <p className="text-error text-sm" role="alert">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={rebase.isPending}>
            Cancel
          </Button>
          <Button onClick={() => void run()} disabled={rebase.isPending}>
            {conflicts ? "Rebase with these choices" : "Rebase"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** The top bar leaves the page and its open editor, so it asks first while an edit is unsaved. */
function guardLeave(event: MouseEvent<HTMLAnchorElement>): void {
  if (!mayLeavePage((question: string) => window.confirm(question))) event.preventDefault();
}

/**
 * The pack the page in view edits: the scope it shows, as the scope menu
 * resolves it, so a page opened without `scope` counts its first option.
 * Null for a repository scope and while the scope loads.
 */
function usePagePackId(): string | null {
  const { scope, owners } = useScope();
  return scope !== null ? (editTarget(scope, owners)?.id ?? null) : null;
}

/**
 * Starts nothing on its own: it explains what a change is and which pack it
 * collects, and only Start opens one, continuing the member's newest change
 * of that pack in status DRAFT before it creates another. An unsaved edit
 * only stops a change of another pack: a change of the page's own pack takes
 * the open editor over with its text, so the edit is saved into the change,
 * unless a continued change no longer holds the edited file as it was.
 */
function StartChangeDialog() {
  const { owners, error: ownersError } = useOwners();
  const pagePackId = usePagePackId();
  const writer = useChangeWrite(null);
  const unsaved = useHasUnsavedEdit();
  const [open, setOpen] = useState(false);
  const [packId, setPackId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const packs = [...(owners ?? [])].sort((left: Owner, right: Owner) =>
    left.name.localeCompare(right.name)
  );
  const blocker = unsaved && packId !== "" && packId !== pagePackId ? UNSAVED_EDIT_BLOCKER : null;
  const loadError = ownersError !== null ? describeError(ownersError) : null;

  const start = async () => {
    setError(null);
    try {
      await writer.startChange(packId, packId === pagePackId ? unsavedEditOrigin() : null);
      setOpen(false);
    } catch (failure: unknown) {
      setError(describeError(failure));
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        setError(null);
        if (next) setPackId(pagePackId ?? "");
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <GitPullRequest aria-hidden />
          Start change
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Start a change</DialogTitle>
          <DialogDescription asChild>
            <ul className="flex list-disc flex-col gap-1 pl-5">
              <li>Every edit you make to files of this pack collects in the change.</li>
              <li>Nothing reaches a repository until the change is submitted and approved.</li>
              <li>Submit and close it from the top bar.</li>
            </ul>
          </DialogDescription>
        </DialogHeader>
        <Field label="Pack">
          <NativeSelect
            value={packId}
            disabled={owners === undefined}
            onChange={(event) => setPackId(event.target.value)}
          >
            <option value="">{owners === undefined ? "Loading packs" : "Choose a pack"}</option>
            {packs.map((owner: Owner) => (
              <option key={owner.id} value={owner.id}>
                {owner.name}
              </option>
            ))}
          </NativeSelect>
        </Field>
        {(blocker ?? error ?? loadError) !== null && (
          <p className="text-error text-sm" role="alert">
            {blocker ?? error ?? loadError}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={writer.isPending}>
            Cancel
          </Button>
          <Button
            onClick={() => void start()}
            disabled={packId === "" || blocker !== null || writer.isPending}
          >
            Start
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function fileCountText(count: number): string {
  return count === 1 ? "1 file" : `${count} files`;
}

/**
 * The open change in the top bar: which pack it changes, how many files it
 * touches, whether it must be rebased, Submit and Close. Rebase and Delete
 * live on the change page, which the pack link opens.
 */
function OpenChangeBar({ change }: { change: VersionDetailResponse }) {
  const router = useRouter();
  const { setChangeId } = useOpenChange();
  const { owners } = useOwners();
  const submit = useSubmitDraft();
  const diff = useVersionDiff(change.id);
  const unsaved = useHasUnsavedEdit();
  const owner = owners ? ownerById(owners, change.packId) : undefined;
  const stale =
    owner !== undefined && (change.baseVersionId ?? null) !== (owner.currentVersionId ?? null);
  const href = `/changes/${encodeURIComponent(change.id)}`;
  const files = diff.data?.data?.length;

  const close = () => {
    if (mayLeavePage((question: string) => window.confirm(question))) setChangeId(null);
  };

  return (
    <div className="flex min-w-0 items-center gap-2 text-sm">
      <Link
        href={href}
        onClick={guardLeave}
        className="text-korur-blue max-w-40 truncate font-medium underline-offset-2 hover:underline sm:max-w-56"
      >
        Change of {owner?.name ?? "a pack"}
      </Link>
      {files !== undefined && (
        <span className="text-muted-foreground hidden shrink-0 sm:inline">
          {fileCountText(files)}
        </span>
      )}
      {stale && (
        <Link
          href={href}
          onClick={guardLeave}
          className="text-warning-text hidden shrink-0 text-xs underline underline-offset-2 sm:inline"
        >
          Rebase needed
        </Link>
      )}
      <ReasonDialog
        trigger={<Button size="sm">Submit</Button>}
        title="Submit this change for review"
        description={
          <p>
            An admin or owner other than you reviews it
            {change.executableChange
              ? "; it touches executable code, hooks or permissions, so an owner"
              : ""}
            . Say why the change is needed; the reviewer reads it.
          </p>
        }
        confirmLabel="Submit"
        blocker={submitBlocker(stale, unsaved)}
        onConfirm={async (reason: string) => {
          await submit.mutateAsync({ draftId: change.id, data: { reason } });
          setChangeId(null);
          router.push(href);
        }}
      />
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="Close change"
        className="hidden sm:inline-flex"
        onClick={close}
      >
        <X aria-hidden />
      </Button>
    </div>
  );
}

/**
 * The member's change in the top bar, on every page: "Start change" while
 * none is open, else the open change with Submit and Close.
 */
export function ChangeControl() {
  const { change, loading } = useOpenChange();
  if (loading) return null;
  if (change === null) return <StartChangeDialog />;
  return <OpenChangeBar change={change} />;
}
