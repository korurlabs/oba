"use client";

import { useState } from "react";

import { ListEmptyState, LoadError } from "@oba/ui/sky/page-states";
import { CLEAR_FILTERS_LABEL } from "@/components/common/list-parts";
import { RULE_KIND_VERSIONS_MAX, useRuleTabKind } from "@/features/rules/hooks/use-rule-kinds";
import { filterRuleRows, ruleTabRows, tabVersionIds } from "@/features/rules/components/rule-rows";
import { NewFileButton } from "@/features/rules/components/rules-page";
import { RulesTable } from "@/features/rules/components/rules-table";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { ListToolbar } from "@/features/scope/components/list-toolbar";
import { mountRefOf } from "@/features/scope/components/mount-paths";
import { editTarget } from "@/features/scope/components/scope-data";
import { describeError } from "@/lib/api/error-text";

import { GUARDRAILS_SECTION, guardrailDetailHref, guardrailNewHref } from "./guardrail-links";

const GUARDRAILS_HELP =
  "A guardrail is a rule under .agents/rules/guardrails/. Child packs and repositories inherit it locked, so none of them can override it, and the settings Kaya delivers enforce it.";

const NO_EVENTS: ReadonlyMap<string, readonly string[]> = new Map();

function GuardrailsView({ ready }: { ready: ReadyScope }) {
  const { tab } = GUARDRAILS_SECTION;
  const files = GUARDRAILS_SECTION.files(ready.files);
  const versionIds = tabVersionIds(files, tab);
  const kind = useRuleTabKind(tab, versionIds);
  const rows = ruleTabRows(files, tab, kind.kind);
  const [search, setSearch] = useState("");
  const visible = filterRuleRows(rows, search);
  // The pack an edit changes; null in a repository scope, which is read only.
  const target = ready.edit?.target ?? editTarget(ready.scope, ready.owners);
  const start = target === null ? null : GUARDRAILS_SECTION.newStart(mountRefOf(target));
  const create =
    target !== null && start !== null ? (
      <NewFileButton
        ready={ready}
        newHref={guardrailNewHref}
        label="New guardrail"
        help={GUARDRAILS_HELP}
      />
    ) : null;

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        filter={ready.inheritedFilter}
        search={{
          value: search,
          onChange: setSearch,
          label: "Search guardrails",
          placeholder: "Search by path, title or description",
        }}
        create={create}
      />
      {kind.error !== null && kind.kind === null && (
        <LoadError
          title="Could not load the guardrails"
          message={describeError(kind.error)}
          onRetry={kind.refetch}
        />
      )}
      {versionIds.length > RULE_KIND_VERSIONS_MAX && (
        <p className="text-muted-foreground text-sm">
          These files come from more than {RULE_KIND_VERSIONS_MAX} versions, more than Kaya
          describes at once, so the list shows their paths only.
        </p>
      )}
      {visible.length === 0 ? (
        <ListEmptyState
          filtered={rows.length > 0}
          onClearFilters={() => setSearch("")}
          create={create}
          labels={{
            emptyTitle: "No guardrails yet",
            emptyDescription: "This scope delivers no guardrails.",
            filteredTitle: "No guardrail matches",
            filteredDescription: "No guardrails match the search.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <RulesTable
          tab={tab}
          rows={visible}
          ready={ready}
          hookEvents={NO_EVENTS}
          detailHref={guardrailDetailHref}
        />
      )}
    </div>
  );
}

/** The guardrail rules of a scope: the rules no child pack or repository can override. */
export function GuardrailsPage() {
  return (
    <ScopePage title={GUARDRAILS_SECTION.title} description={GUARDRAILS_SECTION.description}>
      {(ready: ReadyScope) => <GuardrailsView ready={ready} />}
    </ScopePage>
  );
}
