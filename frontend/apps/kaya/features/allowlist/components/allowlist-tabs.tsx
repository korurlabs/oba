"use client";

import { useMemo, useState, type ReactNode } from "react";
import { parseAsStringEnum, useQueryState } from "nuqs";

import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { nextFileHistoryPage, useFileHistory } from "@/features/changes/hooks/use-changes";
import type { FileHistoryResponse } from "@/features/changes/schemas";
import { ChecksPanel } from "@/features/editor/components/checks-panel";
import { DependencyPanel } from "@/features/editor/components/dependency-panel";
import { chooseHistoryEntry, OlderVersion } from "@/features/editor/components/entity-document";
import { FileContent } from "@/features/editor/components/file-content";
import { historyCount, linksCount, tabLabel } from "@/features/editor/components/file-detail-data";
import { HistoryPanel } from "@/features/editor/components/history-panel";
import { historyEntriesOf } from "@/features/editor/components/version-menu-data";
import { useEditorParams } from "@/features/editor/hooks/use-editor-params";
import { dependencyVersionIds, useDependencies } from "@/features/links/hooks/use-dependencies";
import type { ReadyScope } from "@/features/scope/components";
import { ListToolbar } from "@/features/scope/components/list-toolbar";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import { useMembers } from "@/features/members/hooks/use-members";
import { useConfirmLeave } from "@/features/versions/components/draft-context";
import { FileEditor } from "@/features/versions/components/file-editor";
import type { EditSource, SubmittedChange } from "@/features/versions/hooks/use-change-write";
import { useDraftSession } from "@/features/versions/hooks/use-draft-session";
import { describeError } from "@/lib/api/error-text";

import { toAllowRules, type PermissionLists } from "./allowlist-data";
import {
  ALLOWLIST_LINK_TABS,
  allowlistTabAfter,
  allowlistTabsOf,
  hookScriptsHref,
  shownAllowlistTab,
  type AllowlistLinkTab,
  type AllowlistTab,
} from "./allowlist-tabs-data";
import { HookList } from "./hook-list";
import { RuleList } from "./rule-list";
import { useSettings } from "../hooks/use-settings";
import type { SettingsHookResponse, SettingsResponse } from "../schemas";

export type Allowlist = PermissionLists & { hooks: readonly SettingsHookResponse[] };

const TAB_LABELS: Readonly<Record<AllowlistTab, string>> = {
  rules: "Rules",
  hooks: "Hooks",
  content: "Content",
  links: "Links",
  history: "History",
  checks: "Checks",
};

/** The open tab lives in the URL, so a link such as `?tab=hooks` opens that tab. */
export const allowlistTabParam = parseAsStringEnum<AllowlistLinkTab>([
  ...ALLOWLIST_LINK_TABS,
]).withDefault("rules");

/** The settings file's Links and History counts while they are known; null without a file. */
function useFileCounts(
  file: ScopeFile | null,
  files: readonly ScopeFile[]
): Partial<Record<AllowlistTab, number | null>> {
  const dependencies = useDependencies(
    file ? dependencyVersionIds(files, file) : [],
    file?.path ?? ""
  );
  const history = useFileHistory(file?.versionId ?? "", file?.path ?? "");
  const pages = history.data?.pages ?? [];
  const last = pages[pages.length - 1];
  return {
    links: linksCount(dependencies.data?.data),
    history: historyCount(
      historyEntriesOf(pages).length,
      last !== undefined && nextFileHistoryPage(last) === undefined
    ),
  };
}

/**
 * The settings file itself: an older version the History tab chose, the file
 * as text while the page edits it in a pack that owns it, else the file read
 * only.
 */
function ContentTab({
  ready,
  file,
  draftId,
  source,
  editing,
  onEditDone,
  onSubmitted,
}: {
  ready: ReadyScope;
  file: ScopeFile;
  draftId: string | null;
  source: EditSource | null;
  editing: boolean;
  onEditDone: () => void;
  onSubmitted?: (submitted: SubmittedChange) => void;
}) {
  const [{ at, compare }, setEditor] = useEditorParams();
  const session = useDraftSession(ready.scope, ready.owners);
  const owner = ready.owners.find((entry: Owner) => entry.id === file.ownerId);
  if (at !== null) {
    return (
      <OlderVersion
        key={at}
        file={file}
        at={at}
        view="preview"
        currentId={owner?.currentVersionId ?? null}
        draftId={draftId}
        compare={compare}
        onCompare={(next: string | null) => void setEditor({ compare: next })}
        onRestored={(id: string) => {
          session.setDraftId(id);
          void setEditor({ at: null, compare: null });
        }}
        onBack={() => void setEditor({ at: null, compare: null })}
        lockedBy={source?.kind === "locked" ? source.openPackName : null}
      />
    );
  }
  const packId =
    source?.kind === "change"
      ? source.draft.packId
      : source?.kind === "single"
        ? source.packId
        : null;
  if (editing && source !== null && source.kind !== "locked" && file.ownerId === packId) {
    return (
      <FileEditor
        key={file.path}
        source={source}
        file={file}
        onSaved={onEditDone}
        onDeleted={onEditDone}
        onSubmitted={onSubmitted}
        onCancel={onEditDone}
      />
    );
  }
  return <FileContent key={file.path} file={file} />;
}

export type AllowlistTabsProps = {
  ready: ReadyScope;
  /**
   * The rules and hooks the settings file holds; null while the page edits
   * the file as text, since the typed text may already say otherwise.
   */
  allowlist: Allowlist | null;
  /** The settings file the scope delivers; null when there is none yet. */
  settings: ScopeFile | null;
  /** Where an edit of the settings file goes; null while the page only reads. */
  source?: EditSource | null;
  /** The rule rows of the member's open change, above the rules while the page edits. */
  rulesEditor?: ReactNode;
  /** Whether the page edits: Content then shows the file as text. */
  editing?: boolean;
  onEditDone?: () => void;
  /** After a one-edit change of the settings file went to review. */
  onSubmitted?: (submitted: SubmittedChange) => void;
  /** The page's Edit or Done, on the right of the tabs. */
  actions?: ReactNode;
};

/**
 * The allowlist in tabs: its rules, its hooks, and the settings file as every
 * document page shows one. Only the open tab is mounted, so the rule rows of
 * a change and the file's text editor never save the same file at once.
 */
export function AllowlistTabs({
  ready,
  allowlist,
  settings,
  source = null,
  rulesEditor,
  editing = false,
  onEditDone = () => undefined,
  onSubmitted,
  actions,
}: AllowlistTabsProps) {
  const rules = useMemo(() => (allowlist !== null ? toAllowRules(allowlist) : []), [allowlist]);
  const [urlTab, setTab] = useQueryState("tab", allowlistTabParam);
  // Local state, so a reload of the allowlist keeps the search.
  const [ruleSearch, setRuleSearch] = useState("");
  const [, setEditor] = useEditorParams();
  const session = useDraftSession(ready.scope, ready.owners);
  const confirmLeave = useConfirmLeave();
  const counts = useFileCounts(settings, ready.files);
  const members = useMembers();
  const memberNames = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  const { edit } = ready;
  const draftId = edit && settings && settings.ownerId === edit.target.id ? edit.draft.id : null;
  const tabs = allowlistTabsOf(settings !== null);
  const tab = shownAllowlistTab(urlTab, tabs);
  const allCounts: Partial<Record<AllowlistTab, number | null>> = {
    ...counts,
    rules: allowlist !== null ? rules.length : null,
    hooks: allowlist?.hooks.length ?? null,
  };

  const choose = (entry: FileHistoryResponse) => {
    if (!confirmLeave()) return;
    // The chosen version shows in Content, where the reader asked to see it.
    void setEditor(chooseHistoryEntry(entry, draftId, session.setDraftId));
    void setTab("content");
  };

  return (
    <Tabs
      value={tab}
      onValueChange={(value: string) => {
        const next = allowlistTabAfter(value, confirmLeave);
        if (next === null || next === tab) return;
        void setTab(next);
        void setEditor({ at: null, compare: null });
      }}
    >
      <ListToolbar
        tabs={
          <TabsList>
            {tabs.map((id: AllowlistTab) => (
              <TabsTrigger key={id} value={id} className="tabular-nums">
                {tabLabel(TAB_LABELS[id], allCounts[id] ?? null)}
              </TabsTrigger>
            ))}
          </TabsList>
        }
        search={
          tab === "rules"
            ? {
                value: ruleSearch,
                onChange: setRuleSearch,
                label: "Search rules",
                placeholder: "Pattern or tool",
              }
            : undefined
        }
        filter={ready.inheritedFilter}
        actions={actions}
      />
      <TabsContent value="rules" className="flex flex-col gap-4">
        {tab === "rules" && allowlist !== null && (
          <>
            {rulesEditor}
            <RuleList rules={rules} search={ruleSearch} onClearSearch={() => setRuleSearch("")} />
          </>
        )}
      </TabsContent>
      <TabsContent value="hooks">
        {tab === "hooks" && allowlist !== null && (
          <HookList hooks={allowlist.hooks} scriptsHref={hookScriptsHref(ready.scope)} />
        )}
      </TabsContent>
      {settings !== null && (
        <>
          <TabsContent value="content">
            {tab === "content" && (
              <ContentTab
                ready={ready}
                file={settings}
                draftId={draftId}
                source={source}
                editing={editing}
                onEditDone={onEditDone}
                onSubmitted={onSubmitted}
              />
            )}
          </TabsContent>
          <TabsContent value="links">
            {tab === "links" && <DependencyPanel file={settings} files={ready.files} />}
          </TabsContent>
          <TabsContent value="history">
            {tab === "history" && (
              <HistoryPanel
                versionId={settings.versionId}
                path={settings.path}
                memberNames={memberNames}
                onChoose={choose}
              />
            )}
          </TabsContent>
        </>
      )}
      <TabsContent value="checks">
        {tab === "checks" && <ChecksPanel draftId={draftId} />}
      </TabsContent>
    </Tabs>
  );
}

/**
 * The allowlist of the `.claude/settings.json` a scope delivers, as the
 * server reads it from the version that holds the file. While the page edits
 * without an open change, Content shows the file as text to submit.
 */
export function SettingsAllowlist({
  ready,
  settings,
  source = null,
  editing = false,
  onEditDone,
  onSubmitted,
  actions,
}: {
  ready: ReadyScope;
  settings: ScopeFile;
} & Pick<AllowlistTabsProps, "source" | "editing" | "onEditDone" | "onSubmitted" | "actions">) {
  const query = useSettings([settings.versionId]);
  const data = query.data?.data?.find(
    (entry: SettingsResponse) => entry.versionId === settings.versionId
  );
  if (query.error !== null && query.data === undefined) {
    return (
      <LoadError
        title="Could not load the allowlist"
        message={describeError(query.error)}
        onRetry={() => void query.refetch()}
      />
    );
  }
  if (query.data === undefined) return <TableLoading label="Loading the allowlist" />;
  const allowlist: Allowlist = data ?? { allow: [], ask: [], deny: [], hooks: [] };
  return (
    <AllowlistTabs
      ready={ready}
      allowlist={allowlist}
      settings={settings}
      source={source}
      editing={editing}
      onEditDone={onEditDone}
      onSubmitted={onSubmitted}
      actions={actions}
    />
  );
}
