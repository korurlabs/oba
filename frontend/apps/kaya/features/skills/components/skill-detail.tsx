"use client";

import { useEffect, useId, useState } from "react";
import { useRouter } from "next/navigation";
import { parseAsString, useQueryStates } from "nuqs";
import { AlertTriangle, Check, ChevronDown, Plus, Search } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Input } from "@oba/ui/sky/field";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/sky/popover";
import { ViewToggle } from "@/features/editor/components/view-toggle";
import { useEditorParams, type EditorView } from "@/features/editor/hooks/use-editor-params";
import {
  BackLink,
  DetailSteps,
  FileDetail,
  FilePartHeading,
  GuardedLink,
  useCurrentQuery,
  type FileHeadingParts,
  type Neighbour,
} from "@/features/rules/components/file-detail";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { skillFiles, type ScopeFile } from "@/features/scope/components/scope-files";
import { editTarget } from "@/features/scope/components/scope-data";
import { ReasonDialog } from "@/components/reason-dialog";
import { SubmittedNotice } from "@/features/versions/components/change-control";
import { useConfirmLeave } from "@/features/versions/components/draft-context";
import { FileEditor } from "@/features/versions/components/file-editor";
import { ChangeLockTip } from "@/features/versions/components/open-change";
import { ItemStatusBadges } from "@/features/versions/components/override-status";
import {
  useChangeWrite,
  useEditSource,
  type EditSource,
  type SubmittedChange,
} from "@/features/versions/hooks/use-change-write";
import { useDeleteDraftFile, useVersionFile } from "@/features/versions/hooks/use-versions";
import { cn } from "@/lib/utils";

import { skillDetailHref, skillsListHref } from "./skill-links";
import {
  fileListLabel,
  filterSkillFileGroups,
  groupSkillFiles,
  hasFileChoice,
  selectedSkillFile,
  skillFileLabel,
  skillOrigin,
  skillPaths,
  type SkillFileEntry,
  type SkillFileGroup,
  type SkillRow,
} from "./skill-rows";
import { SKILLS_DESCRIPTION, SKILLS_TITLE } from "./skills-page";
import { SkillsSource } from "./skills-source";

/** The open file of the skill, kept in the URL so a link opens the same file. */
const skillFileParams = { file: parseAsString };

/**
 * Removes every file of the skill's folder: from the open change, or as one
 * one-edit change submitted with a reason, so the reviewer sees the whole
 * skill go at once.
 */
function DeleteSkill({
  source,
  skill,
  files,
  onDeleted,
  onSubmitted,
}: {
  source: EditSource;
  skill: SkillRow;
  files: readonly ScopeFile[];
  /** After the files left the open change. */
  onDeleted: () => void;
  /** After the one-edit change that deletes them went to review. */
  onSubmitted: (submitted: SubmittedChange) => void;
}) {
  const remove = useDeleteDraftFile();
  const writer = useChangeWrite(source);
  const removeAll = async (draftId: string) => {
    for (const file of files) {
      await remove.mutateAsync({ draftId, path: file.path });
    }
  };
  const count = files.length === 1 ? "file" : `${files.length} files`;
  const trigger = (
    <Button size="sm" variant="outline" disabled={source.kind === "locked"}>
      Delete skill
    </Button>
  );

  if (source.kind === "locked") {
    return (
      <span className="flex items-center gap-1">
        {trigger}
        <ChangeLockTip packName={source.openPackName} />
      </span>
    );
  }
  if (source.kind === "single") {
    return (
      <ReasonDialog
        trigger={trigger}
        title={`Delete ${skill.name}`}
        description={
          <p>
            The {count} under <code className="font-mono">{skill.directory}</code> are removed in a
            change that goes to review. The released version keeps them until it is approved.
          </p>
        }
        confirmLabel="Submit"
        onConfirm={async (reason: string) => {
          const submitted = await writer.run(removeAll, reason);
          if (submitted !== null) onSubmitted(submitted);
        }}
      />
    );
  }
  return (
    <ConfirmDialog
      trigger={trigger}
      title={`Delete ${skill.name} from your change`}
      description={
        <p>
          The {count} under <code className="font-mono">{skill.directory}</code> are removed from
          your change. The released version keeps them until the change is approved.
        </p>
      }
      confirmLabel="Delete"
      destructive
      onConfirm={async () => {
        await writer.run(removeAll);
        onDeleted();
      }}
    />
  );
}

/**
 * The frontmatter `paths` of a skill. The server's skill list does not carry
 * them, so a row built from it has none, and they are read from the skill's
 * `SKILL.md`; that read is shared with the viewer's.
 */
function useSkillPaths(skill: SkillRow, own: readonly ScopeFile[]): string[] | null {
  const skillFile =
    skill.paths === null
      ? (own.find((file: ScopeFile) => file.path === `${skill.directory}/SKILL.md`) ?? null)
      : null;
  const read = useVersionFile(skillFile?.versionId ?? "", skillFile?.path ?? "");
  return skillPaths(skill, read.data?.data?.content);
}

/** A neighbouring skill of the list, named for its arrow; null at either end of it. */
function neighbourOf(row: SkillRow | undefined): Neighbour | null {
  return row === undefined
    ? null
    : {
        to: (current: URLSearchParams) => skillDetailHref(row.directory, current),
        name: row.name,
      };
}

/**
 * The searchable list of a skill's files, grouped by folder. The arrow keys
 * move through it from the search field, Enter opens the highlighted file.
 */
function FileList({
  directory,
  own,
  selected,
  onChoose,
}: {
  directory: string;
  own: readonly ScopeFile[];
  selected: ScopeFile | null;
  onChoose: (path: string) => void;
}) {
  const listId = useId();
  const [search, setSearch] = useState("");
  const groups = filterSkillFileGroups(groupSkillFiles(directory, own), search);
  const entries = groups.flatMap((group: SkillFileGroup) => group.files);
  const [active, setActive] = useState(() =>
    Math.max(
      entries.findIndex((entry: SkillFileEntry) => entry.path === selected?.path),
      0
    )
  );
  const activeIndex = Math.min(active, entries.length - 1);
  const optionId = (index: number) => `${listId}-${index}`;

  useEffect(() => {
    if (activeIndex >= 0) {
      document.getElementById(`${listId}-${activeIndex}`)?.scrollIntoView({ block: "nearest" });
    }
  }, [listId, activeIndex]);

  return (
    <div className="flex flex-col">
      <div className="relative border-b p-1">
        <Search
          aria-hidden
          className="text-muted-foreground pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2"
        />
        <Input
          type="search"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setActive(0);
          }}
          onKeyDown={(event) => {
            if (entries.length === 0) return;
            if (event.key === "ArrowDown" || event.key === "ArrowUp") {
              event.preventDefault();
              const step = event.key === "ArrowDown" ? 1 : -1;
              setActive((activeIndex + step + entries.length) % entries.length);
            } else if (event.key === "Enter") {
              const entry = entries[activeIndex];
              if (entry === undefined) return;
              event.preventDefault();
              onChoose(entry.path);
            }
          }}
          placeholder="Search files"
          aria-label="Search files"
          role="combobox"
          aria-expanded
          aria-controls={listId}
          aria-activedescendant={activeIndex >= 0 ? optionId(activeIndex) : undefined}
          className="h-8 border-0 pl-8 shadow-none focus-visible:ring-0"
        />
      </div>
      {entries.length === 0 ? (
        <p className="text-muted-foreground px-3 py-4 text-center text-sm">No file matches</p>
      ) : (
        <div id={listId} role="listbox" aria-label="Files" className="max-h-72 overflow-y-auto p-1">
          {groups.map((group: SkillFileGroup) => (
            <div key={group.folder ?? ""} role="group" aria-label={group.folder ?? "Skill folder"}>
              {group.folder !== null && (
                <div
                  aria-hidden
                  className="text-muted-foreground px-2 pt-2 pb-1 font-mono text-xs font-medium"
                >
                  {group.folder}/
                </div>
              )}
              {group.files.map((entry: SkillFileEntry) => {
                const position = entries.indexOf(entry);
                const isOpen = entry.path === selected?.path;
                return (
                  <div
                    key={entry.path}
                    id={optionId(position)}
                    role="option"
                    aria-selected={isOpen}
                    data-active={position === activeIndex || undefined}
                    className={cn(
                      "data-[active]:bg-accent data-[active]:text-accent-foreground flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 font-mono text-xs select-none",
                      group.folder !== null && "pl-4"
                    )}
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseMove={() => setActive(position)}
                    onClick={() => onChoose(entry.path)}
                  >
                    <span className="min-w-0 flex-1 truncate">{entry.name}</span>
                    {isOpen && <Check aria-hidden className="size-3.5 shrink-0" />}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * The choice of the skill's files: its `SKILL.md`, then a button that opens
 * the list of every file and names the open one. A skill with one file has none.
 */
function FileChoice({
  skill,
  own,
  selected,
  onChoose,
}: {
  skill: SkillRow;
  own: readonly ScopeFile[];
  selected: ScopeFile | null;
  onChoose: (path: string) => void;
}) {
  const [listOpen, setListOpen] = useState(false);
  const skillFile = own[0];
  if (!hasFileChoice(own) || skillFile === undefined) return null;
  const onSkillFile = selected === null || selected.path === skillFile.path;
  return (
    <div role="group" aria-label="Files of the skill" className="flex flex-wrap items-center gap-2">
      <Button
        size="sm"
        variant="outline"
        aria-pressed={onSkillFile}
        className="aria-pressed:bg-accent aria-pressed:text-accent-foreground font-mono text-xs"
        onClick={() => onChoose(skillFile.path)}
      >
        {skillFileLabel(skill.directory, skillFile.path)}
      </Button>
      <Popover open={listOpen} onOpenChange={setListOpen}>
        <PopoverTrigger asChild>
          <Button
            size="sm"
            variant="outline"
            className={cn(
              "max-w-full min-w-0 font-mono text-xs",
              !onSkillFile && "bg-accent text-accent-foreground"
            )}
          >
            <span className="truncate">{fileListLabel(skill.directory, own, selected)}</span>
            <ChevronDown aria-hidden />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-80 max-w-[calc(100vw-2rem)] p-0">
          <FileList
            directory={skill.directory}
            own={own}
            selected={selected}
            onChoose={(path: string) => {
              setListOpen(false);
              onChoose(path);
            }}
          />
        </PopoverContent>
      </Popover>
    </div>
  );
}

/**
 * One skill on its own page: its name, pack, folder and paths, the arrows to
 * the skills before and after it, a choice of its files when it has several,
 * and the chosen file with the skill's description, its tabs and Edit. The
 * file choice already names the open file, so no path is shown over it. In a
 * scope of the skill's own pack a file can be added and the whole skill
 * deleted.
 */
function SkillView({
  skill,
  rows,
  ready,
}: {
  skill: SkillRow;
  /** Every skill in list order, for the arrows. */
  rows: readonly SkillRow[];
  ready: ReadyScope;
}) {
  const router = useRouter();
  const current = useCurrentQuery();
  const [{ file }, setParams] = useQueryStates(skillFileParams);
  const [{ view }, setEditor] = useEditorParams();
  const confirmLeave = useConfirmLeave();
  const [creating, setCreating] = useState(false);
  const [submitted, setSubmitted] = useState<SubmittedChange | null>(null);
  const source = useEditSource(ready);
  const target = editTarget(ready.scope, ready.owners);
  const own = skillFiles(ready.files, skill.directory);
  const paths = useSkillPaths(skill, own);
  const selected = selectedSkillFile(own, file);
  const editable = target !== null && skill.originOwnerId === target.id;
  const origin = skillOrigin(skill);
  const index = rows.findIndex((row: SkillRow) => row.directory === skill.directory);
  const previous = neighbourOf(rows[index - 1]);
  const next = neighbourOf(rows[index + 1]);
  const toList = () => router.push(skillsListHref(current));
  const onSubmitted = (change: SubmittedChange) => {
    setCreating(false);
    setSubmitted(change);
  };

  // An older version and its comparison belong to the file they were picked in.
  const open = (path: string | null) => {
    setCreating(false);
    void setParams({ file: path });
    void setEditor({ at: null, compare: null });
  };

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeading
        title={skill.name}
        titleVariant="mono"
        back={{ href: skillsListHref(current), label: "Back to Skills" }}
        linkComponent={GuardedLink}
        description={
          <div className="flex min-w-0 flex-col gap-2">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={origin.variant}>{origin.text}</Badge>
              <ItemStatusBadges
                item={ready.effective.get(`${skill.directory}/`)}
                owners={ready.owners}
              />
            </div>
            <code className="text-muted-foreground font-mono text-xs break-all">
              {skill.directory}
            </code>
            {skill.error !== null && (
              <p className="text-error flex items-start gap-1.5 text-sm">
                <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                <span>{skill.error}</span>
              </p>
            )}
            {paths && paths.length > 0 && (
              <p className="text-muted-foreground text-xs break-words">
                Applies to <span className="font-mono">{paths.join(", ")}</span>
              </p>
            )}
          </div>
        }
        actions={
          (editable && source !== null) || index !== -1 ? (
            <>
              {editable && source !== null && (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      if (!confirmLeave()) return;
                      setSubmitted(null);
                      setCreating(true);
                    }}
                    disabled={creating || source.kind === "locked"}
                  >
                    <Plus aria-hidden />
                    Add file
                  </Button>
                  <DeleteSkill
                    source={source}
                    skill={skill}
                    files={own}
                    onDeleted={toList}
                    onSubmitted={onSubmitted}
                  />
                </>
              )}
              {index !== -1 && <DetailSteps previous={previous} next={next} noun="skill" />}
            </>
          ) : undefined
        }
      />
      {submitted !== null && (
        <p className="text-sm">
          <SubmittedNotice submitted={submitted} />
        </p>
      )}
      {editable && creating && source !== null && source.kind !== "locked" ? (
        <div className="flex min-w-0 flex-col gap-3">
          <h2 className="text-sm font-medium">New file</h2>
          <ViewToggle view={view} onChange={(next: EditorView) => void setEditor({ view: next })} />
          <FileEditor
            key="new"
            source={source}
            file={null}
            initialPath={`${skill.directory}/`}
            view={view}
            onSaved={(path: string) => open(path)}
            onSubmitted={onSubmitted}
            onCancel={() => setCreating(false)}
          />
        </div>
      ) : (
        <>
          <FileChoice
            skill={skill}
            own={own}
            selected={selected}
            onChoose={(path: string) => {
              if (path !== selected?.path && confirmLeave()) open(path);
            }}
          />
          {selected ? (
            <FileDetail
              key={selected.path}
              file={selected}
              ready={ready}
              summary={null}
              description={skill.description}
              heading={(parts: FileHeadingParts) => (
                <FilePartHeading
                  name={
                    <h2 className="min-w-0 font-mono text-sm break-all">
                      {skillFileLabel(skill.directory, selected.path)}
                    </h2>
                  }
                  parts={parts}
                />
              )}
              onRenamed={(path: string) => void setParams({ file: path })}
              onDeleted={() => (own.length <= 1 ? toList() : void setParams({ file: null }))}
            />
          ) : (
            <p className="text-muted-foreground text-sm">
              The skill delivers no file in this scope.
            </p>
          )}
        </>
      )}
    </div>
  );
}

/** The page of a skill the scope does not deliver, such as one removed or not inherited here. */
function NotDelivered({ directory }: { directory: string }) {
  return (
    <div className="flex flex-col gap-4">
      <BackLink to={skillsListHref} label="Back to Skills" />
      <p className="text-muted-foreground text-sm">
        This scope does not deliver the skill{" "}
        <code className="font-mono break-all">{directory}</code>.
      </p>
    </div>
  );
}

/** A skill's own page, addressed by its folder, in the scope the URL names. */
export function SkillDetail({ directory }: { directory: string }) {
  return (
    <ScopePage title={SKILLS_TITLE} description={SKILLS_DESCRIPTION} variant="detail">
      {(ready: ReadyScope) => (
        <SkillsSource ready={ready}>
          {(rows: readonly SkillRow[]) => {
            const skill = rows.find((row: SkillRow) => row.directory === directory);
            if (skill === undefined) return <NotDelivered directory={directory} />;
            return <SkillView key={skill.directory} skill={skill} rows={rows} ready={ready} />;
          }}
        </SkillsSource>
      )}
    </ScopePage>
  );
}
