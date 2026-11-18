"use client";

import { useState } from "react";
import { useQueryState } from "nuqs";
import { Check, Pencil } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { InfoTip } from "@oba/ui/sky/info-tip";
import { EmptyState } from "@oba/ui/sky/page-states";
import { useChange, useFileHistory } from "@/features/changes/hooks/use-changes";
import { documentState, type DocumentState } from "@/features/editor/components/document-state";
import { historyEntriesOf } from "@/features/editor/components/version-menu-data";
import { StateBadge, StateNote } from "@/features/editor/components/state-badge";
import { useEditorParams } from "@/features/editor/hooks/use-editor-params";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { ListToolbar } from "@/features/scope/components/list-toolbar";
import { effectiveOf } from "@/features/scope/components/effective-items";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import { useMembers } from "@/features/members/hooks/use-members";
import { useConfirmLeave } from "@/features/versions/components/draft-context";
import {
  InheritedNote,
  ItemStatusBadges,
  OverrideNote,
} from "@/features/versions/components/override-status";
import { SubmittedNotice } from "@/features/versions/components/change-control";
import { FileEditor } from "@/features/versions/components/file-editor";
import { ChangeLockTip } from "@/features/versions/components/open-change";
import { useEditSource, type SubmittedChange } from "@/features/versions/hooks/use-change-write";
import { useDraftSession, type DraftSession } from "@/features/versions/hooks/use-draft-session";
import { useVersion } from "@/features/versions/hooks/use-versions";

import { SETTINGS_FILE } from "./allowlist-data";
import { DraftAllowlist } from "./allowlist-editor";
import { AllowlistTabs, SettingsAllowlist, allowlistTabParam } from "./allowlist-tabs";
import {
  allowlistTabsOf,
  mayEditSettings,
  shownAllowlistTab,
  type AllowlistLinkTab,
} from "./allowlist-tabs-data";

/** The tabs a link to the allowlist may name, such as `hooks`. */
export type AllowlistTab = AllowlistLinkTab;

const ALLOWLIST_HELP =
  "What Claude may run without asking, what it must ask for and what it may not do, from the delivered .claude/settings.json. A change of permissions needs an owner's approval.";

/** The state of the settings file as its header badge and note tell it. */
function useSettingsState(
  file: ScopeFile,
  ready: ReadyScope,
  session: DraftSession
): { state: DocumentState; memberNames: ReadonlyMap<string, string> } {
  const [{ at }] = useEditorParams();
  const { edit } = ready;
  const draftId = edit && file.ownerId === edit.target.id ? edit.draft.id : null;
  const ownsFile = session.target !== null && file.ownerId === session.target.id;
  const history = useFileHistory(file.versionId, file.path);
  const draftChange = useChange(draftId ?? "");
  const shownVersion = useVersion(at ?? file.versionId).data?.data;
  const members = useMembers();
  const memberNames = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  const state = documentState({
    at,
    version: shownVersion ? { number: shownVersion.number, status: shownVersion.status } : null,
    inherited: edit !== null ? file.ownerId !== edit.target.id : !ownsFile,
    editing: draftId !== null && file.mode !== "SYMLINK",
    draftDecidedAt: edit?.draft.decidedAt,
    draftReviews: draftChange.data?.data?.reviews ?? [],
    history: historyEntriesOf(history.data?.pages.slice(0, 1) ?? []),
  });
  return { state, memberNames };
}

function SettingsBadges({
  file,
  ready,
  session,
}: {
  file: ScopeFile;
  ready: ReadyScope;
  session: DraftSession;
}) {
  const { state } = useSettingsState(file, ready, session);
  return (
    <>
      <StateBadge state={state} />
      <ItemStatusBadges item={effectiveOf(ready.effective, file.path)} owners={ready.owners} />
    </>
  );
}

/** The notes under the header: a review's state, and the file's inheritance or override. */
function SettingsNotes({
  file,
  ready,
  session,
}: {
  file: ScopeFile;
  ready: ReadyScope;
  session: DraftSession;
}) {
  const [{ at }] = useEditorParams();
  const { state, memberNames } = useSettingsState(file, ready, session);
  // A pack's file that an ancestor gives: named with its source and offered for override.
  const inheritedFile = ready.scope.kind === "PACK" && file.ownerId !== ready.scope.packId;
  return (
    <>
      <StateNote state={state} memberNames={memberNames} />
      {at === null &&
        (inheritedFile ? (
          <InheritedNote file={file} ready={ready} />
        ) : (
          <OverrideNote file={file} ready={ready} />
        ))}
    </>
  );
}

/** What a pack's settings file starts with when its first edit creates it. */
const NEW_SETTINGS = "{}\n";

/**
 * The allowlist of a scope as one document, the settings file: one header row
 * with its name, state and Edit, the notes, then its tabs. Inside the member's
 * open change of the pack, Edit shows the rule rows and the file's text, each
 * saving into the change. Without one, Edit opens the file's text, submitted
 * with a reason as a one-edit change; a pack without a settings file gets a
 * new one.
 */
function AllowlistBody({ ready }: { ready: ReadyScope }) {
  const session = useDraftSession(ready.scope, ready.owners);
  const source = useEditSource(ready);
  const confirmLeave = useConfirmLeave();
  const [{ at }] = useEditorParams();
  const [tab, setTab] = useQueryState("tab", allowlistTabParam);
  const [editing, setEditing] = useState(false);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const { edit } = ready;

  const delivered = ready.files.find((file: ScopeFile) => file.path === SETTINGS_FILE) ?? null;
  // Without a change a link is not read as the allowlist; a change shows its own file, link or not.
  const settings = edit !== null || delivered?.mode !== "SYMLINK" ? delivered : null;
  const inheritedInDraft =
    edit !== null && settings !== null && settings.ownerId !== edit.target.id;
  const mayEdit = mayEditSettings(settings, session.target?.id ?? null, at);
  const single = source?.kind === "single" ? source : null;
  const editingNow = editing && (edit !== null ? !inheritedInDraft : single !== null);
  const textSource = source?.kind === "single" || source?.kind === "change" ? source : null;
  const editingText =
    editingNow &&
    textSource !== null &&
    shownAllowlistTab(tab, allowlistTabsOf(true)) === "content";

  const startEditing = () => {
    if (!confirmLeave()) return;
    setSubmitted(null);
    // A one-edit change edits the file's text; the rule rows save into an open change only.
    if (edit === null) void setTab("content");
    else if (tab !== "rules" && tab !== "content") void setTab("rules");
    setEditing(true);
  };

  const stopEditing = () => {
    if (confirmLeave()) setEditing(false);
  };

  const onSubmitted = (change: SubmittedChange) => {
    setEditing(false);
    setSubmitted(change);
  };

  const actions = (
    <>
      {mayEdit && !editingNow && (
        <span className="flex items-center gap-1">
          <Button onClick={startEditing} disabled={source === null || source.kind === "locked"}>
            <Pencil aria-hidden />
            Edit
          </Button>
          {source?.kind === "locked" && <ChangeLockTip packName={source.openPackName} />}
        </span>
      )}
      {editingNow && edit !== null && (
        <Button variant="outline" onClick={stopEditing}>
          <Check aria-hidden />
          Done
        </Button>
      )}
    </>
  );
  // A body without the tabs still gets the toolbar, for its filter and Edit.
  const toolbar = <ListToolbar filter={ready.inheritedFilter} actions={actions} />;

  // The file's text editor keeps one place in the tree whether its edit goes
  // to a one-edit change or to the open change, so a change of this pack
  // started while the member types takes the editor over with its text.
  let body;
  if (editingText && settings === null) {
    body = (
      <>
        {toolbar}
        <FileEditor
          key="new"
          source={textSource}
          file={null}
          initialPath={SETTINGS_FILE}
          initialContent={NEW_SETTINGS}
          onSaved={() => setEditing(false)}
          onSubmitted={onSubmitted}
          onCancel={() => setEditing(false)}
        />
      </>
    );
  } else if (editingText) {
    body = (
      <AllowlistTabs
        ready={ready}
        allowlist={null}
        settings={settings}
        source={source}
        editing
        onEditDone={() => setEditing(false)}
        onSubmitted={onSubmitted}
        actions={actions}
      />
    );
  } else if (edit !== null && !inheritedInDraft) {
    body = (
      <DraftAllowlist
        edit={edit}
        settings={settings}
        view={(lists, rulesEditor) => (
          <AllowlistTabs
            ready={ready}
            allowlist={lists}
            settings={settings}
            source={source}
            rulesEditor={editingNow ? rulesEditor : undefined}
            editing={editingNow}
            onEditDone={() => setEditing(false)}
            actions={actions}
          />
        )}
      />
    );
  } else if (settings !== null) {
    body = (
      <SettingsAllowlist
        ready={ready}
        settings={settings}
        source={source}
        editing={editingNow}
        onEditDone={() => setEditing(false)}
        onSubmitted={onSubmitted}
        actions={actions}
      />
    );
  } else {
    body = (
      <>
        {toolbar}
        <EmptyState
          title="This scope sets no permissions"
          description={`It delivers no ${SETTINGS_FILE}.`}
        />
      </>
    );
  }

  return (
    <article className="flex min-w-0 flex-col gap-4" aria-label={SETTINGS_FILE}>
      <header className="flex flex-wrap items-center gap-2">
        <h2 className="min-w-0 font-mono text-base break-all">{SETTINGS_FILE}</h2>
        {settings !== null && <SettingsBadges file={settings} ready={ready} session={session} />}
        <InfoTip label="What is the allowlist?">{ALLOWLIST_HELP}</InfoTip>
      </header>
      {submitted !== null && (
        <p className="text-sm">
          <SubmittedNotice submitted={submitted} />
        </p>
      )}
      {settings !== null && <SettingsNotes file={settings} ready={ready} session={session} />}
      {body}
    </article>
  );
}

/**
 * The allow, ask and deny rules and registered hooks of a scope, as its
 * settings file's page beside the pack tree, which chooses the scope.
 */
export function AllowlistPage() {
  return (
    <ScopePage
      title="Allowlist"
      description="What Claude may run without asking, what it must ask for and what it may not do."
    >
      {(ready: ReadyScope) => <AllowlistBody ready={ready} />}
    </ScopePage>
  );
}
