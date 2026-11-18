"use client";

import { NativeSelect } from "@oba/ui/sky/field";

import type { ScopeState } from "../hooks/use-scope";
import { useConfirmLeave } from "@/features/versions/components/draft-context";
import { flattenPackTree, packTree } from "./owners";
import { allOptions, scopeValue, type ScopeOption } from "./scope-data";

/** Indents a child pack under its parent; a native option holds text only. */
const LEVEL_INDENT = "    ";

/**
 * The compact form of the scope choice, for pages without the pack tree:
 * the packs in tree order, each child indented under its parent, then the
 * repositories. Another scope closes the open editor, so it asks first while
 * an edit is unsaved; on a no the select keeps the current scope.
 */
export function ScopeSelect({ state }: { state: ScopeState }) {
  const { scope, options, owners, setScope } = state;
  const confirmLeave = useConfirmLeave();
  const packs = flattenPackTree(packTree(owners));
  return (
    <label className="flex flex-col gap-1 text-xs font-medium">
      <span className="text-muted-foreground">Scope</span>
      <NativeSelect
        className="min-w-56"
        value={scope === null ? "" : scopeValue(scope)}
        disabled={scope === null}
        onChange={(event) => {
          const next = allOptions(options).find(
            (option: ScopeOption) => option.value === event.target.value
          );
          if (next && confirmLeave()) setScope(next.scope);
        }}
      >
        {scope === null && <option value="">No repository or pack</option>}
        {packs.length > 0 && (
          <optgroup label="Packs">
            {packs.map(({ owner, depth }) => {
              const value = scopeValue({ kind: "PACK", packId: owner.id });
              return (
                <option key={value} value={value}>
                  {`${LEVEL_INDENT.repeat(depth)}${owner.name}`}
                </option>
              );
            })}
          </optgroup>
        )}
        {options.repositories.length > 0 && (
          <optgroup label="Repositories">
            {options.repositories.map((option: ScopeOption) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </optgroup>
        )}
      </NativeSelect>
    </label>
  );
}
