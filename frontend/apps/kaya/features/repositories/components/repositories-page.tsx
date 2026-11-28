"use client";

import { useState, type FormEvent } from "react";
import { Pencil } from "lucide-react";
import { parseAsStringEnum, useQueryState } from "nuqs";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import {
  CLEAR_FILTERS_LABEL,
  CLEAR_SEARCH_LABEL,
  FILTER_LABELS,
} from "@/components/common/list-parts";
import { firstIssue } from "@/lib/forms";
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
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";

import { useAllRepositories, useUpdateRepository } from "../hooks/use-repositories";
import {
  repositoryUpdateRequestSchema,
  RepositoryStatusSchema,
  type RepositoryListResponse,
  type RepositoryStatus,
} from "../schemas";
import { RepositoryPacksDialog } from "./repository-packs-dialog";

const STATUS_LABELS: Record<RepositoryStatus, string> = {
  ACTIVE: "Active",
  ARCHIVED: "Archived",
};

function EditRepositoryDialog({ repository }: { repository: RepositoryListResponse }) {
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
              <option value="ACTIVE">{STATUS_LABELS.ACTIVE}</option>
              <option value="ARCHIVED">{STATUS_LABELS.ARCHIVED}</option>
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
 * The repositories the search finds by name or remote, ignoring case, with
 * the chosen status only; an empty search and no status keep every one. The
 * list holds every repository of the organization, so it is narrowed in the
 * browser.
 */
export function searchRepositories(
  repositories: readonly RepositoryListResponse[],
  search: string,
  status: RepositoryStatus | null
): RepositoryListResponse[] {
  const needle = search.trim().toLowerCase();
  return repositories.filter(
    (repository: RepositoryListResponse) =>
      (status === null || repository.status === status) &&
      (needle === "" ||
        repository.name.toLowerCase().includes(needle) ||
        repository.remoteUrl.toLowerCase().includes(needle))
  );
}

/**
 * The repositories Kaya connected on their first session; an admin renames,
 * links tickets or archives them, and an owner picks the packs each one uses.
 * The Status filter is bound to `?status`.
 */
export function RepositoriesPage() {
  const { can } = useIdentity();
  const [status, setStatus] = useQueryState(
    "status",
    parseAsStringEnum(RepositoryStatusSchema.options)
  );
  const [search, setSearch] = useState("");
  const repositories = useAllRepositories();
  const rows = searchRepositories(repositories.data?.data ?? [], search, status);
  const statusCategory: FilterCategory = {
    key: "status",
    label: "Status",
    mode: "single",
    options: RepositoryStatusSchema.options.map((value: RepositoryStatus) => ({
      value,
      label: STATUS_LABELS[value],
    })),
    value: status,
    onChange: (value: string | null) =>
      void setStatus(value === null ? null : RepositoryStatusSchema.parse(value)),
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Repositories"
        description="Every repository of your organization, connected when its first session is recorded, with the packs it receives."
      />
      <ListToolbar
        search={
          <SearchInput
            value={search}
            onSearchChange={(next: string | undefined) => setSearch(next ?? "")}
            label="Search repositories"
            placeholder="Name or remote"
            clearLabel={CLEAR_SEARCH_LABEL}
          />
        }
        filters={
          <FilterPopover
            categories={[statusCategory]}
            onClear={() => void setStatus(null)}
            labels={FILTER_LABELS}
          />
        }
      />
      {repositories.error !== null && repositories.data === undefined ? (
        <LoadError
          title="Could not load the repositories"
          message={describeError(repositories.error)}
          onRetry={() => void repositories.refetch()}
        />
      ) : repositories.data === undefined ? (
        <TableLoading label="Loading repositories" />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={status !== null || search !== ""}
          onClearFilters={() => {
            setSearch("");
            void setStatus(null);
          }}
          labels={{
            emptyTitle: "No repositories yet",
            emptyDescription: "Repositories appear once a session in them is recorded.",
            filteredTitle: "No repository matches",
            filteredDescription: "No repository matches the search and filters.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <>
          {repositories.error !== null && (
            <p className="text-warning-text text-sm" role="status">
              The latest reload failed, showing the previous list:{" "}
              {describeError(repositories.error)}
            </p>
          )}
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
              {rows.map((repository: RepositoryListResponse) => (
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
                      {STATUS_LABELS[repository.status]}
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
        </>
      )}
    </div>
  );
}
