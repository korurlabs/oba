"use client";

import { useState, type FormEvent } from "react";
import { Plus } from "lucide-react";

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
import { InfoTip } from "@oba/ui/sky/info-tip";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { RepositoryPacksDialog } from "@/features/repositories/components/repository-packs-dialog";
import type { PackDetailResponse, PackRepositoryResponse } from "@/features/scope/schemas";
import type { RepositoryResponse } from "@/features/sessions/schemas";
import { useIdentity } from "@/lib/identity/identity-context";

import { attachableRepositories } from "./pack-data";

const NO_VALUE = "-";

const REPOSITORIES_HELP =
  "The repositories whose pack list names this pack. Each receives the pack with its parent packs, at its root or in the mounted folder. A required pack is composed by every active repository. A repository's pack list is edited in its Packs dialog.";

/** The repository whose packs dialog is open, and the pack it adds, if any. */
type PacksTarget = { id: string; name: string; addPackId?: string };

/**
 * Picks the repository to add the pack to. It saves nothing: the choice
 * opens that repository's packs dialog with the pack added, where the
 * owner sets its folder and saves the whole list.
 */
function AddToRepositoryDialog({
  pack,
  repositories,
  onChosen,
}: {
  pack: PackDetailResponse;
  repositories: readonly RepositoryResponse[];
  onChosen: (repository: RepositoryResponse) => void;
}) {
  const [open, setOpen] = useState(false);
  const [repositoryId, setRepositoryId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const choices = attachableRepositories(repositories, pack.repositories);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const chosen = choices.find((repository: RepositoryResponse) => repository.id === repositoryId);
    if (chosen === undefined) {
      setError("Choose a repository.");
      return;
    }
    setOpen(false);
    onChosen(chosen);
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        setError(null);
        if (next) setRepositoryId("");
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" disabled={choices.length === 0}>
          <Plus aria-hidden />
          Add to repository
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Add {pack.name} to a repository</DialogTitle>
            <DialogDescription>
              The repository&apos;s packs open next, with {pack.name} added at the repository root.
            </DialogDescription>
          </DialogHeader>
          <Field label="Repository">
            <NativeSelect
              value={repositoryId}
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
          {error !== null && (
            <p className="text-error text-sm" role="alert">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Continue</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/**
 * The repositories that compose the pack, read only. A repository's pack
 * list has one editor, its packs dialog, so the two views cannot drift: an
 * owner opens it from a row, or adds the pack to another repository through
 * it. A required pack is composed by every repository, so it is never added.
 */
export function PackRepositories({
  pack,
  repositories,
}: {
  pack: PackDetailResponse;
  /** Every repository of the organization, offered to add the pack to. */
  repositories: readonly RepositoryResponse[];
}) {
  const { can } = useIdentity();
  const owner = can("OWNER");
  const [target, setTarget] = useState<PacksTarget | null>(null);
  const [open, setOpen] = useState(false);
  const openPacks = (next: PacksTarget) => {
    setTarget(next);
    setOpen(true);
  };

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5">
          <h3 className="text-base font-semibold">Repositories</h3>
          <InfoTip label="Which repositories are listed?">{REPOSITORIES_HELP}</InfoTip>
        </div>
        {owner && !pack.required && (
          <AddToRepositoryDialog
            pack={pack}
            repositories={repositories}
            onChosen={(repository: RepositoryResponse) =>
              openPacks({ id: repository.id, name: repository.name, addPackId: pack.id })
            }
          />
        )}
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
            </TableRow>
          </TableHeader>
          <TableBody>
            {pack.repositories.map((repository: PackRepositoryResponse) => (
              <TableRow key={repository.id}>
                <TableCell className="font-medium">
                  {owner ? (
                    <Button
                      variant="link"
                      className="h-auto p-0 font-medium"
                      aria-label={`Packs of ${repository.name}`}
                      onClick={() => openPacks({ id: repository.id, name: repository.name })}
                    >
                      {repository.name}
                    </Button>
                  ) : (
                    repository.name
                  )}
                </TableCell>
                <TableCell className="font-mono text-xs">{repository.remoteUrl}</TableCell>
                <TableCell className="font-mono text-xs">
                  {repository.rootPath ?? (
                    <span className="text-muted-foreground font-sans">Repository root</span>
                  )}
                </TableCell>
                <TableCell>{repository.latestReleaseNumber ?? NO_VALUE}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {target !== null && (
        <RepositoryPacksDialog
          repositoryId={target.id}
          repositoryName={target.name}
          addPackId={target.addPackId}
          open={open}
          onOpenChange={setOpen}
        />
      )}
    </section>
  );
}
