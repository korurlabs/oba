/**
 * The addresses of the Conventions list, of one doc's detail page and of the
 * page that starts a new doc. A doc is addressed by its repository path,
 * since a change copies every file into new rows while the path stays the
 * same. The scope, release and inherited setting travel with every
 * address, so the target shows the same docs, and so do the list's open
 * folders, so the way back opens them again.
 */

import {
  OPEN_PARAM,
  openingFolder,
  parseOpenFolders,
  serializeOpenFolders,
} from "./convention-folders";

/** The query parameters that keep the scope, release, inherited setting and open folders. */
const KEPT_PARAMS = ["scope", "release", "inherited", OPEN_PARAM] as const;

function withKept(page: string, current: URLSearchParams, own: [string, string][] = []): string {
  const query = new URLSearchParams();
  for (const key of KEPT_PARAMS) {
    const value = current.get(key);
    if (value !== null) query.set(key, value);
  }
  for (const [key, value] of own) query.set(key, value);
  const text = query.toString();
  return text === "" ? page : `${page}?${text}`;
}

/** The detail page of a doc; the path is one URL segment. */
export function conventionDetailHref(path: string, current: URLSearchParams): string {
  return withKept(`/conventions/${encodeURIComponent(path)}`, current);
}

/** The page that starts a new doc, in a folder when one is named. */
export function conventionNewHref(current: URLSearchParams, folder: string | null = null): string {
  return withKept("/conventions/new", current, folder === null ? [] : [["folder", folder]]);
}

/** The Conventions list, with a folder and the folders above it open when one is named. */
export function conventionsListHref(
  current: URLSearchParams,
  folder: string | null = null
): string {
  if (folder === null) return withKept("/conventions", current);
  const open = openingFolder(parseOpenFolders(current.get(OPEN_PARAM)), folder);
  return withKept("/conventions", current, [[OPEN_PARAM, serializeOpenFolders(open)]]);
}

/**
 * Where an older `/conventions?file=<path>` link now leads: the doc's detail
 * page, with every other parameter kept. Null when the link names no file.
 */
export function legacyConventionRedirect(query: URLSearchParams): string | null {
  const file = query.get("file");
  if (file === null || file === "") return null;
  const rest = new URLSearchParams(query);
  rest.delete("file");
  const text = rest.toString();
  const page = `/conventions/${encodeURIComponent(file)}`;
  return text === "" ? page : `${page}?${text}`;
}
