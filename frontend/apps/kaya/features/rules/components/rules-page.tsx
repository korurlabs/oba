"use client";

import { useState, type ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { parseAsStringEnum, useQueryStates } from "nuqs";

import { LoadError } from "@oba/ui/sky/page-states";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { useSettings } from "@/features/allowlist/hooks/use-settings";
import type { SettingsResponse } from "@/features/allowlist/schemas";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { ListToolbar, NewButton } from "@/features/scope/components/list-toolbar";
import { mountRefOf } from "@/features/scope/components/mount-paths";
import { editTarget } from "@/features/scope/components/scope-data";
import {
  RULE_TAB_IDS,
  RULE_TABS,
  filesForTab,
  hookEventsOf,
  type RuleTab,
  type ScopeFile,
} from "@/features/scope/components/scope-files";
import { useEditSource } from "@/features/versions/hooks/use-change-write";
import { describeError } from "@/lib/api/error-text";

import { RULE_KIND_VERSIONS_MAX, useRuleTabKind } from "../hooks/use-rule-kinds";
import { newFileStart, ruleNewHref } from "./rule-links";
import { filterRuleRows, ruleTabRows, tabVersionIds, type RuleTabRow } from "./rule-rows";
import { RulesTable } from "./rules-table";

/** The open tab, kept in the URL so a link and the detail page's back link open the same tab. */
const ruleParams = {
  tab: parseAsStringEnum<RuleTab>(RULE_TAB_IDS).withDefault("rules"),
};

const SETTINGS_FILE = ".claude/settings.json";
const NO_VERSIONS: readonly string[] = [];

/**
 * The hook events that run each hook script of the scope, by path, from the
 * scope's delivered settings; read only while the Hooks tab is open.
 */
function useHookEvents(
  ready: ReadyScope,
  tab: RuleTab,
  rows: readonly RuleTabRow[]
): ReadonlyMap<string, readonly string[]> {
  const settingsFile =
    tab === "hooks"
      ? ready.files.find((file: ScopeFile) => file.path === SETTINGS_FILE)
      : undefined;
  const query = useSettings(settingsFile ? [settingsFile.versionId] : NO_VERSIONS);
  const settings = (query.data?.data ?? []).find(
    (entry: SettingsResponse) => entry.versionId === settingsFile?.versionId
  );
  const hooks = settings?.hooks ?? [];
  return new Map(rows.map((row: RuleTabRow) => [row.file.path, hookEventsOf(row.file, hooks)]));
}

/**
 * "+ New file": opens the new-file page. Nothing is written until the member
 * submits the file, so no change is opened first; while a change of another
 * pack is open the button is disabled.
 */
export function NewFileButton({
  ready,
  newHref,
  label = "New file",
  help,
}: {
  ready: ReadyScope;
  newHref: (current: URLSearchParams) => string;
  label?: string;
  /** What the button creates, shown in its tooltip. */
  help?: ReactNode;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const source = useEditSource(ready);

  return (
    <NewButton
      label={label}
      help={help}
      lockedBy={source?.kind === "locked" ? source.openPackName : null}
      disabled={source === null}
      onClick={() => router.push(newHref(new URLSearchParams(searchParams.toString())))}
    />
  );
}

/** The create button's text on each tab. */
const NEW_LABELS: Readonly<Record<RuleTab, string>> = {
  rules: "New rule",
  "agents-md": "New AGENTS.md",
  roles: "New role",
  subagents: "New subagent",
  workflows: "New workflow",
  templates: "New template",
  hooks: "New hook",
};

function tabNoun(label: string): string {
  return label === "AGENTS.md" ? "AGENTS.md" : label.toLowerCase();
}

function RulesView({ ready }: { ready: ReadyScope }) {
  const [{ tab }, setParams] = useQueryStates(ruleParams);
  const [search, setSearch] = useState("");
  const versionIds = tabVersionIds(ready.files, tab);
  const tabKind = useRuleTabKind(tab, versionIds);
  const rows = ruleTabRows(ready.files, tab, tabKind.kind);
  const visible = filterRuleRows(rows, search);
  const hookEvents = useHookEvents(ready, tab, rows);
  // The pack an edit changes; null in a repository scope, which is read only.
  const target = ready.edit?.target ?? editTarget(ready.scope, ready.owners);
  const start = target === null ? null : newFileStart(tab, mountRefOf(target));

  return (
    <Tabs
      value={tab}
      onValueChange={(value: string) => {
        const next = RULE_TAB_IDS.find((id: RuleTab) => id === value);
        if (next) void setParams({ tab: next });
      }}
    >
      <ListToolbar
        tabs={
          <TabsList>
            {RULE_TABS.map((entry) => (
              <TabsTrigger key={entry.id} value={entry.id}>
                {entry.label}
                <span className="text-muted-foreground text-xs">
                  {filesForTab(ready.files, entry.id).length}
                </span>
              </TabsTrigger>
            ))}
          </TabsList>
        }
        filter={ready.inheritedFilter}
        search={{
          value: search,
          onChange: setSearch,
          label: "Search files",
          placeholder: "Search by path, title or description",
        }}
        create={
          target !== null && start !== null ? (
            <NewFileButton
              ready={ready}
              label={NEW_LABELS[tab]}
              newHref={(current: URLSearchParams) => ruleNewHref(tab, current)}
            />
          ) : null
        }
      />
      {RULE_TABS.map((entry) => (
        <TabsContent key={entry.id} value={entry.id}>
          {entry.id !== tab ? null : (
            <div className="flex flex-col gap-4">
              {target !== null && start === null && (
                <p className="text-muted-foreground text-sm">
                  A pack mounted in a folder cannot add hooks: they run on every developer&apos;s
                  machine, so they stay with the packs at the repository root.
                </p>
              )}
              {tabKind.error !== null && tabKind.kind === null && (
                <LoadError
                  title={`Could not load the ${entry.label === "AGENTS.md" ? "AGENTS.md files" : entry.label.toLowerCase()}`}
                  message={describeError(tabKind.error)}
                  onRetry={tabKind.refetch}
                />
              )}
              {versionIds.length > RULE_KIND_VERSIONS_MAX && (
                <p className="text-muted-foreground text-sm">
                  These files come from more than {RULE_KIND_VERSIONS_MAX} versions, more than Kaya
                  describes at once, so the list shows their paths only.
                </p>
              )}
              {rows.length === 0 ? (
                <p className="text-muted-foreground text-sm">
                  This scope delivers no {tabNoun(entry.label)}.
                </p>
              ) : visible.length === 0 ? (
                <p className="text-muted-foreground text-sm">
                  No {tabNoun(entry.label)} match the search.
                </p>
              ) : (
                <RulesTable tab={tab} rows={visible} ready={ready} hookEvents={hookEvents} />
              )}
            </div>
          )}
        </TabsContent>
      ))}
    </Tabs>
  );
}

/** The rules, `AGENTS.md`, roles, subagents, workflows, templates and hooks of a scope. */
export function RulesPage() {
  return (
    <ScopePage
      title="Rules"
      description="The rules and agent files a repository's release or a pack delivers."
    >
      {(ready: ReadyScope) => <RulesView ready={ready} />}
    </ScopePage>
  );
}
