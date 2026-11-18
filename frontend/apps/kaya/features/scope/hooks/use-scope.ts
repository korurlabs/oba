"use client";

import { parseAsInteger, parseAsString, useQueryState } from "nuqs";

import {
  requestedRelease,
  resolveScope,
  scopeOptions,
  scopeValue,
  type Scope,
  type ScopeOptions,
} from "../components/scope-data";
import type { Owner } from "../schemas";
import { useOwners } from "./use-owners";

export type ScopeState = {
  /** Null while the options load, or when the organization has no repository and no pack. */
  scope: Scope | null;
  options: ScopeOptions;
  /** Every pack; empty while they load. */
  owners: Owner[];
  /** The repository release the URL asks for; null for the newest, and always null for a pack. */
  release: number | null;
  setScope: (scope: Scope) => void;
  loading: boolean;
  /** The failed read of the options, when one failed. */
  error: unknown;
  retry: () => void;
};

const NO_OPTIONS: ScopeOptions = { repositories: [], packs: [] };

/**
 * The scope in the URL (`scope`), checked against the repositories and packs
 * that exist, and the repository release it asks for (`release`).
 * Choosing another scope drops the release, since it numbers one
 * repository's releases.
 */
export function useScope(): ScopeState {
  const [value, setValue] = useQueryState("scope", parseAsString);
  const [release, setRelease] = useQueryState("release", parseAsInteger);
  const { owners, repositories, error, retry } = useOwners();
  const ready = repositories !== undefined && owners !== undefined;
  const options = ready ? scopeOptions(repositories, owners) : NO_OPTIONS;
  const scope = ready ? resolveScope(value, options) : null;

  return {
    scope,
    options,
    owners: owners ?? [],
    release: requestedRelease(value, scope, release),
    setScope: (next: Scope) => {
      void setRelease(null);
      void setValue(scopeValue(next));
    },
    loading: !ready && error === null,
    error,
    retry,
  };
}
