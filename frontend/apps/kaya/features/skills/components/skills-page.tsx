"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Input } from "@oba/ui/sky/field";
import { EmptyState } from "@oba/ui/sky/page-states";
import { useCurrentQuery } from "@/features/rules/components/file-detail";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { ListToolbar, NewButton } from "@/features/scope/components/list-toolbar";
import { mountRefOf } from "@/features/scope/components/mount-paths";
import { editTarget } from "@/features/scope/components/scope-data";
import type { Owner } from "@/features/scope/schemas";
import { newSkillContent, skillNameIssue } from "@/features/versions/components/draft-rules";
import {
  useChangeWrite,
  useEditSource,
  type EditSource,
} from "@/features/versions/hooks/use-change-write";
import { useSaveDraftFile } from "@/features/versions/hooks/use-versions";
import { describeError } from "@/lib/api/error-text";

import { skillDetailHref } from "./skill-links";
import { filterSkillRows, type SkillRow } from "./skill-rows";
import { SkillsSource } from "./skills-source";
import { SkillsTable } from "./skills-table";

export const SKILLS_TITLE = "Skills";
export const SKILLS_DESCRIPTION =
  "The skills a repository's release or a pack delivers, where each comes from, and their files.";

/**
 * Creates a skill's folder with a `SKILL.md` to fill in. A new skill is
 * followed by more edits of its files, so it goes into the member's open
 * change of the pack, which it starts when none is open.
 */
function NewSkillDialog({
  target,
  source,
  rows,
  onCreated,
}: {
  /** The pack the skill is added to. */
  target: Owner;
  source: EditSource;
  rows: readonly SkillRow[];
  onCreated: (directory: string) => void;
}) {
  const save = useSaveDraftFile();
  const writer = useChangeWrite(source);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const taken = rows.map((row: SkillRow) => row.directory.split("/").pop() ?? row.directory);
  // A mounted pack's skills share the root's skills folder, so their names carry the pack's prefix.
  const mountedName = mountRefOf(target)?.name ?? null;
  const issue = name === "" ? null : skillNameIssue(name, taken, mountedName);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const refusal = skillNameIssue(name, taken, mountedName);
    if (refusal !== null) {
      setError(refusal);
      return;
    }
    const directory = `.agents/skills/${name.trim()}`;
    setError(null);
    try {
      const draftId =
        source.kind === "change" ? source.draft.id : await writer.startChange(target.id);
      await save.mutateAsync({
        draftId,
        data: {
          path: `${directory}/SKILL.md`,
          encoding: "UTF_8",
          content: newSkillContent(name),
        },
      });
      setOpen(false);
      setName("");
      onCreated(directory);
    } catch (failure: unknown) {
      setError(describeError(failure));
    }
  };

  if (source.kind === "locked") {
    return <NewButton label="New skill" lockedBy={source.openPackName} />;
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <NewButton label="New skill" />
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>New skill</DialogTitle>
            <DialogDescription>
              Your change of {target.name} gets <code>.agents/skills/&lt;name&gt;/SKILL.md</code>{" "}
              with a frontmatter to fill in; a change is started when none is open.
            </DialogDescription>
          </DialogHeader>
          <Field
            label="Name"
            hint={
              mountedName === null
                ? undefined
                : `A skill of pack ${mountedName}, which is mounted in a folder, is named ${mountedName}-<name>.`
            }
            error={issue ?? error}
          >
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="font-mono"
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={save.isPending || writer.isPending || issue !== null || name.trim() === ""}
            >
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** The toolbar and the table; a row opens the skill's own page. */
function SkillsView({ rows, ready }: { rows: readonly SkillRow[]; ready: ReadyScope }) {
  const router = useRouter();
  const current = useCurrentQuery();
  const [search, setSearch] = useState("");
  const visible = filterSkillRows(rows, search);
  const source = useEditSource(ready);
  const target = editTarget(ready.scope, ready.owners);
  const create =
    target !== null && source !== null ? (
      <NewSkillDialog
        target={target}
        source={source}
        rows={rows}
        onCreated={(directory: string) => router.push(skillDetailHref(directory, current))}
      />
    ) : null;

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        filter={ready.inheritedFilter}
        search={
          rows.length === 0
            ? undefined
            : {
                value: search,
                onChange: setSearch,
                label: "Search skills",
                placeholder: "Search skills by name, description or origin",
              }
        }
        create={create}
      />
      {rows.length === 0 ? (
        <EmptyState
          title="No skills in this scope"
          description="No SKILL.md under .agents/skills/ or .claude/skills/ is delivered here."
        />
      ) : (
        <SkillsTable
          rows={visible}
          hrefOf={(directory: string) => skillDetailHref(directory, current)}
          effective={ready.effective}
          owners={ready.owners}
        />
      )}
    </div>
  );
}

/** Every skill the chosen repository or pack delivers, with its `SKILL.md` and supporting files. */
export function SkillsPage() {
  return (
    <ScopePage title={SKILLS_TITLE} description={SKILLS_DESCRIPTION}>
      {(ready: ReadyScope) => (
        <SkillsSource ready={ready}>
          {(rows: readonly SkillRow[]) => <SkillsView rows={rows} ready={ready} />}
        </SkillsSource>
      )}
    </ScopePage>
  );
}
