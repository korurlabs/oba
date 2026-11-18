"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Pencil, Trash2 } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { Input, NativeSelect } from "@oba/ui/sky/field";
import { InfoTip } from "@oba/ui/sky/info-tip";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import {
  useLiveEdit,
  type DraftEdit,
  type LiveEdit,
} from "@/features/versions/components/draft-context";
import { FileEditor } from "@/features/versions/components/file-editor";
import { parseSettings } from "@/features/versions/components/file-parsers";
import { useVersionFile } from "@/features/versions/hooks/use-versions";
import { useSaveDraftFile } from "@/features/versions/hooks/use-versions";
import { describeError } from "@/lib/api/error-text";

import {
  PERMISSION_LISTS,
  SETTINGS_FILE,
  ruleIssue,
  settingsWithPermissions,
  type PermissionList,
  type PermissionLists,
} from "./allowlist-data";
import { EffectBadge } from "./rule-row";

const LIST_TITLES: Readonly<Record<PermissionList, string>> = {
  deny: "Deny",
  ask: "Ask",
  allow: "Allow",
};

const EMPTY_LISTS: PermissionLists = { allow: [], ask: [], deny: [] };

/**
 * The live edit of the rule rows: a typed new rule or an open row edit that
 * is not saved yet. It carries no text; it lets the leave guards and the
 * submit blocker see the unsaved rules.
 */
export function rulesLiveEdit(newRule: string, editedRows: number): LiveEdit | null {
  if (newRule === "" && editedRows === 0) return null;
  return { path: SETTINGS_FILE, mode: "FILE", text: null, replaces: null, dirty: true };
}

/**
 * The keys of a list's rows: the rule and its occurrence, not its index, so
 * removing or changing one row keeps the open edits of the others.
 */
export function ruleRowKeys(rules: readonly string[]): string[] {
  const seen = new Map<string, number>();
  return rules.map((rule: string) => {
    const occurrence = seen.get(rule) ?? 0;
    seen.set(rule, occurrence + 1);
    return `${rule}#${occurrence}`;
  });
}

/** One rule of a list with its edit and remove controls. */
function RuleItem({
  rowKey,
  rule,
  busy,
  issueOf,
  onEdited,
  onChange,
  onRemove,
}: {
  rowKey: string;
  rule: string;
  busy: boolean;
  /** Tells the lists whether this row holds an edit that is not saved. */
  onEdited: (rowKey: string, edited: boolean) => void;
  issueOf: (text: string) => string | null;
  /** Resolves to whether the save went through; a refusal is shown by the list. */
  onChange: (text: string) => Promise<boolean>;
  onRemove: () => Promise<boolean>;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(rule);
  const issue = text === rule ? null : issueOf(text);
  const edited = editing && text !== rule;
  useEffect(() => {
    onEdited(rowKey, edited);
    return () => onEdited(rowKey, false);
  }, [rowKey, edited, onEdited]);
  if (editing) {
    return (
      <li className="flex flex-col gap-1 py-2">
        <form
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            void onChange(text).then((saved: boolean) => saved && setEditing(false));
          }}
        >
          <Input
            value={text}
            onChange={(event) => setText(event.target.value)}
            className="font-mono"
            aria-label={`Edit ${rule}`}
          />
          <Button type="submit" size="sm" disabled={busy || issue !== null || text === rule}>
            Save
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={() => {
              setText(rule);
              setEditing(false);
            }}
          >
            Cancel
          </Button>
        </form>
        {issue && (
          <span className="text-error text-xs" role="alert">
            {issue}
          </span>
        )}
      </li>
    );
  }
  return (
    <li className="flex items-center justify-between gap-2 py-2">
      <code className="font-mono text-sm break-all">{rule}</code>
      <div className="flex shrink-0 gap-1">
        <Button
          size="icon-sm"
          variant="ghost"
          aria-label={`Edit ${rule}`}
          onClick={() => setEditing(true)}
          disabled={busy}
        >
          <Pencil />
        </Button>
        <Button
          size="icon-sm"
          variant="ghost"
          aria-label={`Remove ${rule}`}
          onClick={() => void onRemove()}
          disabled={busy}
        >
          <Trash2 />
        </Button>
      </div>
    </li>
  );
}

/**
 * The three permission lists of the draft's `.claude/settings.json` as rows
 * to add, edit and remove. Each change saves the file at once with every
 * other key kept; a change of permissions makes the change executable, so an
 * owner approves it.
 */
export function PermissionListsEditor({
  draftId,
  content,
  onSaved,
}: {
  draftId: string;
  /** The settings file's text; null when the draft has none yet. */
  content: string | null;
  onSaved?: () => void;
}) {
  const save = useSaveDraftFile();
  const [list, setList] = useState<PermissionList>("allow");
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const lists = useMemo(() => (content === null ? EMPTY_LISTS : parseSettings(content)), [content]);
  const issue = text === "" ? null : ruleIssue(text, lists[list]);
  const [editedRows, setEditedRows] = useState<ReadonlySet<string>>(() => new Set());
  const markEdited = useCallback((rowKey: string, edited: boolean) => {
    setEditedRows((rows: ReadonlySet<string>) => {
      if (rows.has(rowKey) === edited) return rows;
      const next = new Set(rows);
      if (edited) next.add(rowKey);
      else next.delete(rowKey);
      return next;
    });
  }, []);
  const { setLive } = useLiveEdit();
  useEffect(() => {
    setLive(rulesLiveEdit(text, editedRows.size));
  }, [text, editedRows.size, setLive]);
  useEffect(() => () => setLive(null), [setLive]);

  /** Saves the lists; a refusal is shown above the lists, so the result only says whether it saved. */
  const write = async (next: PermissionLists): Promise<boolean> => {
    setError(null);
    try {
      await save.mutateAsync({
        draftId,
        data: {
          path: SETTINGS_FILE,
          encoding: "UTF_8",
          content: settingsWithPermissions(content, next),
        },
      });
      onSaved?.();
      return true;
    } catch (failure: unknown) {
      setError(describeError(failure));
      return false;
    }
  };

  const rowKeys = useMemo(
    () => ({
      allow: ruleRowKeys(lists.allow),
      ask: ruleRowKeys(lists.ask),
      deny: ruleRowKeys(lists.deny),
    }),
    [lists]
  );

  const add = async (event: FormEvent) => {
    event.preventDefault();
    if (ruleIssue(text, lists[list]) !== null) return;
    if (await write({ ...lists, [list]: [...lists[list], text] })) setText("");
  };

  return (
    <Card className="gap-4">
      <CardHeader>
        <CardTitle className="flex items-center gap-1.5 text-base">
          Rules in your change
          <InfoTip label="How are rules saved?">
            Each edit saves {SETTINGS_FILE} in your change. A change of permissions needs an
            owner&apos;s approval.
          </InfoTip>
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <form className="flex flex-col gap-1" onSubmit={(event) => void add(event)}>
          <div className="flex flex-wrap gap-2">
            <NativeSelect
              value={list}
              onChange={(event) => setList(event.target.value as PermissionList)}
              aria-label="List"
            >
              {PERMISSION_LISTS.map((key: PermissionList) => (
                <option key={key} value={key}>
                  {LIST_TITLES[key]}
                </option>
              ))}
            </NativeSelect>
            <Input
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder="Bash(git status *)"
              className="min-w-48 flex-1 font-mono"
              aria-label="New rule"
            />
            <Button type="submit" disabled={save.isPending || text === "" || issue !== null}>
              Add rule
            </Button>
          </div>
          {issue && (
            <span className="text-error text-xs" role="alert">
              {issue}
            </span>
          )}
        </form>
        {error !== null && (
          <p className="text-error text-sm" role="alert">
            {error}
          </p>
        )}
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          {PERMISSION_LISTS.map((key: PermissionList) => (
            <section key={key} className="flex flex-col gap-1" aria-label={LIST_TITLES[key]}>
              <h3 className="flex items-center gap-2 text-sm font-semibold">
                <EffectBadge effect={key} />
                <span className="text-muted-foreground font-normal">{lists[key].length}</span>
              </h3>
              {lists[key].length === 0 ? (
                <p className="text-muted-foreground text-sm">No rule.</p>
              ) : (
                <ul className="divide-y">
                  {lists[key].map((rule: string, index: number) => (
                    <RuleItem
                      key={rowKeys[key][index]}
                      rowKey={`${key}:${rowKeys[key][index]}`}
                      rule={rule}
                      busy={save.isPending}
                      onEdited={markEdited}
                      issueOf={(next: string) =>
                        ruleIssue(
                          next,
                          lists[key].filter((_entry: string, other: number) => other !== index)
                        )
                      }
                      onChange={(next: string) =>
                        write({
                          ...lists,
                          [key]: lists[key].map((entry: string, other: number) =>
                            other === index ? next : entry
                          ),
                        })
                      }
                      onRemove={() =>
                        write({
                          ...lists,
                          [key]: lists[key].filter(
                            (_entry: string, other: number) => other !== index
                          ),
                        })
                      }
                    />
                  ))}
                </ul>
              )}
            </section>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * The allowlist of the viewer's draft: the rule rows when the settings file
 * is JSON, otherwise the file as text to fix. The draft has no settings file
 * yet when `settings` is null; the first rule creates it.
 */
export function DraftAllowlist({
  edit,
  settings,
  view,
}: {
  edit: DraftEdit;
  settings: ScopeFile | null;
  /** The view of the draft's parsed lists, given the rule rows to place. */
  view: (lists: ReturnType<typeof parseSettings>, rulesEditor: ReactNode) => ReactNode;
}) {
  const file = useVersionFile(settings ? edit.draft.id : "", settings?.path ?? "");
  const data = file.data?.data;
  const parsed = useMemo(() => {
    if (!settings) return { lists: null, error: null };
    if (typeof data?.content !== "string" || data.encoding === "BASE64") return null;
    try {
      return { lists: parseSettings(data.content), error: null };
    } catch (error: unknown) {
      // The file is offered as text to fix instead of as an empty allowlist.
      return { lists: null, error: describeError(error) };
    }
  }, [settings, data]);

  if (settings && file.error !== null && data === undefined) {
    return (
      <LoadError
        title="Could not load the settings file"
        message={describeError(file.error)}
        onRetry={() => void file.refetch()}
      />
    );
  }
  if (settings && data?.encoding === "BASE64") {
    return (
      <p className="text-muted-foreground text-sm">
        {SETTINGS_FILE} of your change is not text, so its rules cannot be edited here.
      </p>
    );
  }
  if (parsed === null) return <TableLoading label="Loading the settings file" />;
  if (settings && parsed.lists === null) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-error text-sm" role="alert">
          {SETTINGS_FILE} of your change is not valid JSON ({parsed.error}). Fix it below.
        </p>
        <FileEditor
          key={settings.sha256 ?? settings.path}
          source={{ kind: "change", draft: edit.draft }}
          file={settings}
        />
      </div>
    );
  }
  const lists = parsed.lists ?? { allow: [], ask: [], deny: [], hooks: [] };
  return view(
    lists,
    <PermissionListsEditor
      draftId={edit.draft.id}
      content={settings && typeof data?.content === "string" ? data.content : null}
    />
  );
}
