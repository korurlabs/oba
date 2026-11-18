"use client";

import { useState, type FormEvent } from "react";
import { Pencil } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { firstIssue } from "@/lib/forms";
import { RepositoryPacksDialog } from "@/features/repositories/components/repository-packs-dialog";
import { describeError } from "@/lib/api/error-text";
import { useIdentity } from "@/lib/identity/identity-context";
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
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";

import { useRepositorySettings, useUpdateRepository } from "../hooks/use-settings";
import {
  repositoryUpdateRequestSchema,
  RepositoryStatusSchema,
  type RepositorySettingsResponse,
} from "../schemas";

function EditRepositoryDialog({ repository }: { repository: RepositorySettingsResponse }) {
  const update = useUpdateRepository();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState(repository.name);
  const [template, setTemplate] = useState(repository.ticketUrlTemplate ?? "");
  const [status, setStatus] = useState(repository.status);
  const [error, setError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const parsed = repositoryUpdateRequestSchema.safeParse({
      name,
      ticketUrlTemplate: template,
      status,
    });
    if (!parsed.success) {
      setError(firstIssue(parsed.error));
      return;
    }
    setError(null);
    update.mutate(
      { id: repository.id, data: parsed.data },
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
          setName(repository.name);
          setTemplate(repository.ticketUrlTemplate ?? "");
          setStatus(repository.status);
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={`Edit ${repository.name}`}>
          <Pencil aria-hidden />
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Edit {repository.name}</DialogTitle>
            <DialogDescription>{repository.remoteUrl}</DialogDescription>
          </DialogHeader>
          <Field label="Name">
            <Input value={name} maxLength={100} onChange={(event) => setName(event.target.value)} />
          </Field>
          <Field
            label="Ticket URL template"
            hint="An https URL with {key} where the ticket key goes; empty for no ticket links."
          >
            <Input
              value={template}
              maxLength={500}
              placeholder="https://tickets.example.com/browse/{key}"
              onChange={(event) => setTemplate(event.target.value)}
            />
          </Field>
          <Field
            label="Status"
            hint="Kaya refuses new sessions of an archived repository; its old sessions stay."
          >
            <NativeSelect
              value={status}
              onChange={(event) => setStatus(RepositoryStatusSchema.parse(event.target.value))}
            >
              <option value="ACTIVE">Active</option>
              <option value="ARCHIVED">Archived</option>
            </NativeSelect>
          </Field>
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
              disabled={update.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={update.isPending}>
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/**
 * The repositories Kaya connected on their first session; an admin renames, links
 * tickets or archives them, and an owner picks the packs each one uses.
 */
export function RepositoriesSettings() {
  const { can } = useIdentity();
  const repositories = useRepositorySettings();
  const rows = repositories.data?.data ?? [];
  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        A repository is connected when its first session is recorded.
      </p>
      {repositories.error !== null && repositories.data === undefined ? (
        <LoadError
          title="Could not load the repositories"
          message={describeError(repositories.error)}
          onRetry={() => void repositories.refetch()}
        />
      ) : repositories.data === undefined ? (
        <TableLoading label="Loading repositories" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No repositories yet"
          description="Repositories appear once a session in them is recorded."
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Remote</TableHead>
              <TableHead>Ticket URL template</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((repository: RepositorySettingsResponse) => (
              <TableRow key={repository.id}>
                <TableCell className="font-medium">{repository.name}</TableCell>
                <TableCell className="font-mono text-xs">{repository.remoteUrl}</TableCell>
                <TableCell className="font-mono text-xs break-all whitespace-normal">
                  {repository.ticketUrlTemplate ?? (
                    <span className="text-muted-foreground font-sans">None</span>
                  )}
                </TableCell>
                <TableCell>
                  <Badge variant={repository.status === "ACTIVE" ? "success" : "muted"}>
                    {repository.status === "ACTIVE" ? "Active" : "Archived"}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  {can("ADMIN") && <EditRepositoryDialog repository={repository} />}
                  {can("OWNER") && (
                    <RepositoryPacksDialog
                      repositoryId={repository.id}
                      repositoryName={repository.name}
                    />
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
