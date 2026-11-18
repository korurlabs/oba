"use client";

import { useState, type FormEvent } from "react";
import { Plus, X } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Input, NativeSelect } from "@oba/ui/sky/field";
import { InfoTip } from "@oba/ui/sky/info-tip";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import {
  PACK_LIMITS,
  type PackDetailResponse,
  type PackRepositoryResponse,
} from "@/features/scope/schemas";
import type { RepositoryResponse } from "@/features/sessions/schemas";
import { describeError } from "@/lib/api/error-text";
import { useIdentity } from "@/lib/identity/identity-context";

import { useEditRepositoryPacks } from "../hooks/use-packs";
import { attachRows, attachableRepositories, removeRows } from "./pack-data";

const NO_VALUE = "-";

const REPOSITORIES_HELP =
  "The repositories whose pack list names this pack. Each receives the pack with its parent packs, at its root or in the mounted folder. A required pack is composed by every active repository.";

const FOLDER_HELP =
  "Leave it empty to use the pack at the repository root. A folder such as projects/kagi mounts the pack there: its AGENTS.md lands in that folder and its rules must be scoped to it.";

function AttachRepositoryDialog({
  pack,
  repositories,
}: {
  pack: PackDetailResponse;
  repositories: readonly RepositoryResponse[];
}) {
  const save = useEditRepositoryPacks();
  const [open, setOpen] = useState(false);
  const [repositoryId, setRepositoryId] = useState("");
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const choices = attachableRepositories(repositories, pack.repositories);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (repositoryId === "") {
      setError("Choose a repository.");
      return;
    }
    setError(null);
    save.mutate(
      { repositoryId, edit: (entries) => attachRows(entries, pack.id, folder) },
      {
        onSuccess: () => setOpen(false),
        onError: (failure: unknown) => setError(describeError(failure)),
      }
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        setError(null);
        if (next) {
          setRepositoryId("");
          setFolder("");
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" disabled={choices.length === 0}>
          <Plus aria-hidden />
          Attach repository
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Attach {pack.name}</DialogTitle>
            <DialogDescription>Saving publishes a new release of the repository.</DialogDescription>
          </DialogHeader>
          <Field label="Repository">
            <NativeSelect
              value={repositoryId}
              disabled={save.isPending}
              onChange={(event) => setRepositoryId(event.target.value)}
            >
              <option value="">Choose a repository</option>
              {choices.map((repository: RepositoryResponse) => (
                <option key={repository.id} value={repository.id}>
                  {repository.name}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <div className="flex items-end gap-1.5">
            <Field label="Mount folder" className="flex-1">
              <Input
                placeholder="Repository root"
                className="font-mono"
                value={folder}
                maxLength={PACK_LIMITS.rootMax}
                disabled={save.isPending}
                onChange={(event) => setFolder(event.target.value)}
              />
            </Field>
            <div className="pb-2.5">
              <InfoTip label="What is a mount folder?">{FOLDER_HELP}</InfoTip>
            </div>
          </div>
          {error !== null && (
            <p className="text-error text-sm" role="alert">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={save.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={save.isPending}>
              Attach
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function RemoveRepositoryButton({
  pack,
  repository,
}: {
  pack: PackDetailResponse;
  repository: PackRepositoryResponse;
}) {
  const save = useEditRepositoryPacks();
  return (
    <ConfirmDialog
      trigger={
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={`Remove ${pack.name} from ${repository.name}`}
          title="Remove"
        >
          <X aria-hidden />
        </Button>
      }
      title={`Remove ${pack.name} from ${repository.name}?`}
      description="The repository stops receiving this pack. Saving publishes a new release of the repository."
      confirmLabel="Remove"
      destructive
      onConfirm={() =>
        save.mutateAsync({
          repositoryId: repository.id,
          edit: (entries) => removeRows(entries, pack.id),
        })
      }
    />
  );
}

/**
 * The repositories that compose the pack. An owner attaches it to another
 * repository or removes it from one, through the repository's pack list; a
 * required pack is composed by every repository, so its list is read only.
 */
export function PackRepositories({
  pack,
  repositories,
}: {
  pack: PackDetailResponse;
  /** Every repository of the organization, offered for attach. */
  repositories: readonly RepositoryResponse[];
}) {
  const { can } = useIdentity();
  const editable = can("OWNER") && !pack.required;
  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5">
          <h3 className="text-base font-semibold">Repositories</h3>
          <InfoTip label="Which repositories are listed?">{REPOSITORIES_HELP}</InfoTip>
        </div>
        {editable && <AttachRepositoryDialog pack={pack} repositories={repositories} />}
      </div>
      {pack.repositories.length === 0 ? (
        <p className="text-muted-foreground text-sm">No repository uses this pack yet.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Remote</TableHead>
              <TableHead>Mounted folder</TableHead>
              <TableHead>Latest release</TableHead>
              {editable && (
                <TableHead>
                  <span className="sr-only">Actions</span>
                </TableHead>
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {pack.repositories.map((repository: PackRepositoryResponse) => (
              <TableRow key={repository.id}>
                <TableCell className="font-medium">{repository.name}</TableCell>
                <TableCell className="font-mono text-xs">{repository.remoteUrl}</TableCell>
                <TableCell className="font-mono text-xs">
                  {repository.rootPath ?? (
                    <span className="text-muted-foreground font-sans">Repository root</span>
                  )}
                </TableCell>
                <TableCell>{repository.latestReleaseNumber ?? NO_VALUE}</TableCell>
                {editable && (
                  <TableCell className="text-right">
                    <RemoveRepositoryButton pack={pack} repository={repository} />
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </section>
  );
}
