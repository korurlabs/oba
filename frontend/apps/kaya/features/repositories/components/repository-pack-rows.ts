import { flattenPackTree, ownersOf, packTree } from "@/features/scope/components/owners";
import {
  mountFolderSchema,
  repositoryPackUpdateRequestSchema,
  type PackResponse,
  type RepositoryPackEntryResponse,
  type RepositoryPackUpdateRequest,
} from "@/features/scope/schemas";
import { KayaApiError } from "@/lib/api/errors";

/** One editable row of a repository's pack list; an empty folder means the repository root. */
export type PackRow = { key: string; packId: string; folder: string };

/** A pack a row may choose, in tree order with its depth for the indent. */
export type PackOption = { id: string; name: string; depth: number };

export type RowsCheck =
  | { ok: true; data: RepositoryPackUpdateRequest }
  | { ok: false; message: string; rowKey: string | null };

/** The required packs of a list: every repository composes them, so the dialog shows them read only. */
export function requiredEntries(
  entries: readonly RepositoryPackEntryResponse[]
): RepositoryPackEntryResponse[] {
  return entries.filter((entry: RepositoryPackEntryResponse) => entry.required);
}

/** The repository's own packs as editable rows, in the server's order. */
export function rowsOf(entries: readonly RepositoryPackEntryResponse[]): PackRow[] {
  return entries
    .filter((entry: RepositoryPackEntryResponse) => !entry.required)
    .map((entry: RepositoryPackEntryResponse) => ({
      key: entry.id,
      packId: entry.id,
      folder: entry.rootPath ?? "",
    }));
}

/**
 * The packs a row may choose: every pack but the required ones, which every
 * repository composes already, and those another row holds, since a
 * repository uses a pack once.
 */
export function packOptions(
  packs: readonly PackResponse[],
  rows: readonly PackRow[],
  rowKey: string
): PackOption[] {
  const taken = new Set(
    rows.filter((row: PackRow) => row.key !== rowKey).map((row: PackRow) => row.packId)
  );
  return flattenPackTree(packTree(ownersOf(packs, [])))
    .filter(({ owner }) => !owner.required && !taken.has(owner.id))
    .map(({ owner, depth }) => ({ id: owner.id, name: owner.name, depth }));
}

function inside(folder: string, other: string): boolean {
  return folder.startsWith(`${other}/`);
}

/**
 * Checks the rows as the server does, so the dialog names the row at fault
 * before the save: a pack in every row, each folder valid, no folder given
 * twice or lying inside another. The server checks the same and more (a
 * mounted pack that another used pack inherits, a rule not scoped to its
 * folder), so its refusal is shown too.
 */
export function checkRows(rows: readonly PackRow[]): RowsCheck {
  const folders: { key: string; folder: string }[] = [];
  for (const row of rows) {
    if (row.packId === "") {
      return { ok: false, message: "Choose a pack in every row.", rowKey: row.key };
    }
    const typed = row.folder.trim();
    if (typed === "") continue;
    const parsed = mountFolderSchema.safeParse(typed);
    if (!parsed.success) {
      return {
        ok: false,
        message: parsed.error.issues[0]?.message ?? "Check the folder.",
        rowKey: row.key,
      };
    }
    const clash = folders.find(
      (other) =>
        other.folder === parsed.data ||
        inside(parsed.data, other.folder) ||
        inside(other.folder, parsed.data)
    );
    if (clash) {
      return {
        ok: false,
        message:
          clash.folder === parsed.data
            ? `The folder ${parsed.data} is given twice.`
            : `The folders ${clash.folder} and ${parsed.data} lie inside one another.`,
        rowKey: row.key,
      };
    }
    folders.push({ key: row.key, folder: parsed.data });
  }
  const parsed = repositoryPackUpdateRequestSchema.safeParse({
    packs: rows.map((row: PackRow) => ({
      packId: row.packId,
      rootPath: row.folder.trim() === "" ? null : row.folder.trim(),
    })),
  });
  if (!parsed.success) {
    return {
      ok: false,
      message: parsed.error.issues[0]?.message ?? "Check the values entered.",
      rowKey: null,
    };
  }
  return { ok: true, data: parsed.data };
}

/**
 * The row a server refusal points at, so the dialog marks it: the folder of
 * an unscoped mount rule (KAY_CFG_034 `folder`), or the folder a refused
 * mount names in its reason (KAY_CFG_026 `rootPath`); null when none does.
 */
export function refusedRowKey(error: unknown, rows: readonly PackRow[]): string | null {
  if (!(error instanceof KayaApiError)) return null;
  const mounted = rows.filter((row: PackRow) => row.folder.trim() !== "");
  if (error.code === "KAY_CFG_034") {
    const folder = error.errorValues.folder;
    return mounted.find((row: PackRow) => row.folder.trim() === folder)?.key ?? null;
  }
  if (error.code === "KAY_CFG_026") {
    const reason = error.errorValues.rootPath;
    if (typeof reason !== "string") return null;
    const named = mounted
      .filter((row: PackRow) => reason.includes(row.folder.trim()))
      .sort(
        (left: PackRow, right: PackRow) => right.folder.trim().length - left.folder.trim().length
      );
    return named[0]?.key ?? null;
  }
  return null;
}
