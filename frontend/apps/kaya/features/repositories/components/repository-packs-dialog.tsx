"use client";

import { useState, type FormEvent } from "react";
import { Layers, Plus, X } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
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
import { Input, NativeSelect } from "@oba/ui/sky/field";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import {
  PACK_LIMITS,
  type PackResponse,
  type RepositoryPackEntryResponse,
} from "@/features/scope/schemas";
import { describeError } from "@/lib/api/error-text";

import { useRepositoryPackChoice, useUpdateRepositoryPacks } from "../hooks/use-repository-packs";
import {
  checkRows,
  packOptions,
  refusedRowKey,
  requiredEntries,
  rowsOf,
  rowsWith,
  type PackOption,
  type PackRow,
} from "./repository-pack-rows";

/** Indents a child pack under its parent; a native option holds text only. */
const LEVEL_INDENT = "    ";

/** A refusal to show, and the row it points at, if any. */
type Failure = { message: string; rowKey: string | null };

let nextRowKey = 0;
function newRowKey(): string {
  nextRowKey += 1;
  return `new-${nextRowKey}`;
}

function RequiredRows({ entries }: { entries: readonly RepositoryPackEntryResponse[] }) {
  if (entries.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1" aria-label="Required packs">
      {entries.map((entry: RepositoryPackEntryResponse) => (
        <li key={entry.id} className="flex items-center gap-2 text-sm">
          <span className="font-mono">{entry.name}</span>
          <Badge variant="muted">Every repository</Badge>
        </li>
      ))}
    </ul>
  );
}

function PackRowFields({
  row,
  options,
  invalid,
  disabled,
  onChange,
  onRemove,
}: {
  row: PackRow;
  options: readonly PackOption[];
  invalid: boolean;
  disabled: boolean;
  onChange: (next: PackRow) => void;
  onRemove: () => void;
}) {
  return (
    <li className="flex items-start gap-2">
      <NativeSelect
        aria-label="Pack"
        className="w-44 shrink-0 font-mono"
        value={row.packId}
        disabled={disabled}
        aria-invalid={invalid && row.packId === "" ? true : undefined}
        onChange={(event) => onChange({ ...row, packId: event.target.value })}
      >
        <option value="">Choose a pack</option>
        {options.map((option: PackOption) => (
          <option key={option.id} value={option.id}>
            {`${LEVEL_INDENT.repeat(option.depth)}${option.name}`}
          </option>
        ))}
      </NativeSelect>
      <Input
        aria-label="Mount folder"
        placeholder="Repository root"
        className="font-mono"
        value={row.folder}
        maxLength={PACK_LIMITS.rootMax}
        disabled={disabled}
        aria-invalid={invalid ? true : undefined}
        onChange={(event) => onChange({ ...row, folder: event.target.value })}
      />
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label="Remove the pack"
        disabled={disabled}
        onClick={onRemove}
      >
        <X aria-hidden />
      </Button>
    </li>
  );
}

function PacksForm({
  repositoryId,
  packs,
  entries,
  addPackId,
  onSaved,
  onCancel,
}: {
  repositoryId: string;
  packs: readonly PackResponse[];
  entries: readonly RepositoryPackEntryResponse[];
  addPackId: string | undefined;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const update = useUpdateRepositoryPacks();
  const [rows, setRows] = useState<PackRow[]>(() =>
    addPackId === undefined ? rowsOf(entries) : rowsWith(entries, addPackId)
  );
  const [failure, setFailure] = useState<Failure | null>(null);
  const required = requiredEntries(entries);
  const busy = update.isPending;

  const change = (next: PackRow[]) => {
    setRows(next);
    setFailure(null);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const checked = checkRows(rows);
    if (!checked.ok) {
      setFailure({ message: checked.message, rowKey: checked.rowKey });
      return;
    }
    setFailure(null);
    update.mutate(
      { repositoryId, data: checked.data },
      {
        onSuccess: onSaved,
        onError: (error: unknown) =>
          setFailure({ message: describeError(error), rowKey: refusedRowKey(error, rows) }),
      }
    );
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <RequiredRows entries={required} />
      {rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">The repository uses no pack of its own.</p>
      ) : (
        <ul className="flex flex-col gap-2" aria-label="Packs of the repository">
          {rows.map((row: PackRow) => (
            <PackRowFields
              key={row.key}
              row={row}
              options={packOptions(packs, rows, row.key)}
              invalid={failure?.rowKey === row.key}
              disabled={busy}
              onChange={(next: PackRow) =>
                change(rows.map((other: PackRow) => (other.key === row.key ? next : other)))
              }
              onRemove={() => change(rows.filter((other: PackRow) => other.key !== row.key))}
            />
          ))}
        </ul>
      )}
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-fit"
        disabled={busy || rows.length >= PACK_LIMITS.repositoryPacksMax}
        onClick={() => change([...rows, { key: newRowKey(), packId: "", folder: "" }])}
      >
        <Plus aria-hidden />
        Add pack
      </Button>
      <p className="text-muted-foreground text-xs">
        Leave the folder empty to use a pack at the repository root. A folder such as projects/kagi
        mounts the pack there: its AGENTS.md lands in that folder and its rules must be scoped to
        it.
      </p>
      {failure !== null && (
        <p className="text-error text-sm" role="alert">
          {failure.message}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" disabled={busy}>
          Save
        </Button>
      </DialogFooter>
    </form>
  );
}

/**
 * An owner picks the packs a repository uses, each at the repository root
 * or mounted in a folder. This dialog is the one place a repository's pack
 * list is written, from the Repositories page and from a pack's
 * repositories. Required packs come first and stay read only, since every
 * repository composes them. Saving publishes a new release; a refusal (an
 * invalid or overlapping folder, a mounted pack that another used pack
 * inherits, a rule not scoped to its folder) is shown in the dialog, with
 * the row it names marked.
 *
 * Without `open` the dialog brings its own icon button. With `open` the
 * caller opens it, as a pack's repository rows do, and no button is drawn.
 */
export function RepositoryPacksDialog({
  repositoryId,
  repositoryName,
  addPackId,
  open: openProp,
  onOpenChange,
}: {
  repositoryId: string;
  repositoryName: string;
  /** A pack to add: the form opens with it as a new row at the repository root, unless the list holds it. */
  addPackId?: string;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}) {
  const [ownOpen, setOwnOpen] = useState(false);
  const controlled = openProp !== undefined;
  const open = controlled ? openProp : ownOpen;
  const setOpen = (next: boolean) => {
    if (!controlled) setOwnOpen(next);
    onOpenChange?.(next);
  };
  const { packs, list } = useRepositoryPackChoice(repositoryId, open);
  const packData = packs.data?.data ?? undefined;
  const listData = list.data?.data ?? undefined;
  const failed = [packs, list].find((query) => query.error !== null && query.data === undefined);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      {!controlled && (
        <DialogTrigger asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`Packs of ${repositoryName}`}
            title="Packs"
          >
            <Layers aria-hidden />
          </Button>
        </DialogTrigger>
      )}
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Packs of {repositoryName}</DialogTitle>
          <DialogDescription>
            The repository receives each pack with its parent packs. Saving publishes a new release.
          </DialogDescription>
        </DialogHeader>
        {failed ? (
          <LoadError
            title="Could not load the packs"
            message={describeError(failed.error)}
            onRetry={() => void failed.refetch()}
          />
        ) : packData === undefined || listData === undefined ? (
          <TableLoading label="Loading the packs" />
        ) : (
          <PacksForm
            key={`${repositoryId}-${list.dataUpdatedAt}`}
            repositoryId={repositoryId}
            packs={packData}
            entries={listData.packs}
            addPackId={addPackId}
            onSaved={() => setOpen(false)}
            onCancel={() => setOpen(false)}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
