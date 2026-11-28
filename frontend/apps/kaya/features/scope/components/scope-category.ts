import type { FilterOption, SingleFilterCategory } from "@oba/ui/sky/filter-popover";

import type { ScopeState } from "../hooks/use-scope";
import { flattenPackTree, packTree } from "./owners";
import { allOptions, scopeValue, type ScopeOption } from "./scope-data";

/** Indents a child pack under its parent; an em space, which HTML does not collapse. */
const LEVEL_INDENT = " ";

/**
 * The scope choice as a filter category, for pages without the pack tree:
 * the packs in tree order, each child indented under its parent, then the
 * repositories. A page always shows one scope, the first until another is
 * chosen, so that one is the default. `confirmLeave` asks before another
 * scope closes an unsaved edit; on a no the scope stays.
 */
export function scopeCategory(
  state: ScopeState,
  confirmLeave: () => boolean
): SingleFilterCategory {
  const { scope, options, owners, setScope } = state;
  const all = allOptions(options);
  const packOptions: FilterOption[] = flattenPackTree(packTree(owners)).map(({ owner, depth }) => ({
    value: scopeValue({ kind: "PACK", packId: owner.id }),
    label: `${LEVEL_INDENT.repeat(depth)}${owner.name}`,
    group: "Packs",
  }));
  const repositoryOptions: FilterOption[] = options.repositories.map((option: ScopeOption) => ({
    value: option.value,
    label: option.label,
    group: "Repositories",
  }));
  return {
    key: "scope",
    label: "Scope",
    mode: "single",
    searchable: true,
    options: [...packOptions, ...repositoryOptions],
    value: scope === null ? null : scopeValue(scope),
    defaultValue: all[0]?.value,
    onChange: (value: string | null) => {
      const next = all.find((option: ScopeOption) => option.value === (value ?? all[0]?.value));
      if (next && confirmLeave()) setScope(next.scope);
    },
  };
}
