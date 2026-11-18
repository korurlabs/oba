"use client";

import type { ReactNode } from "react";

import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import type { ReadyScope } from "@/features/scope/components";
import { skillsFromFiles, type ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import { useVersionFiles } from "@/features/versions/hooks/use-versions";
import { describeError } from "@/lib/api/error-text";

import { SKILL_VERSIONS_MAX, useSkills } from "../hooks/use-skills";
import { fileSkillRows, serverSkillRows, type SkillFileRead, type SkillRow } from "./skill-rows";

type RowsChildren = (rows: readonly SkillRow[]) => ReactNode;

function ownerOf(ready: ReadyScope) {
  return (ownerId: string) => ready.owners.find((owner: Owner) => owner.id === ownerId);
}

/**
 * The skills of a scope, named by the server's skill list of the versions
 * that hold its `SKILL.md` files. A draft's list reloads with its writes.
 */
function ServerSkills({
  ready,
  versionIds,
  children,
}: {
  ready: ReadyScope;
  versionIds: string[];
  children: RowsChildren;
}) {
  const skills = useSkills(versionIds);
  const list = skills.data?.data;
  if (skills.error !== null && list === undefined) {
    return (
      <LoadError
        title="Could not load the skills"
        message={describeError(skills.error)}
        onRetry={() => void skills.refetch()}
      />
    );
  }
  if (!list) return <TableLoading label="Loading skills" />;
  return children(serverSkillRows(ready.files, ownerOf(ready), list));
}

/** A scope whose `SKILL.md` files sit in more versions than one list reads: they are parsed here. */
function FileSkills({ ready, children }: { ready: ReadyScope; children: RowsChildren }) {
  const skillFileList = skillsFromFiles(ready.files).map((location) => location.skillFile);
  const reads = useVersionFiles(skillFileList);
  const readOf = (skillFile: ScopeFile): SkillFileRead => {
    const index = skillFileList.findIndex((file: ScopeFile) => file.path === skillFile.path);
    const query = reads[index];
    if (query?.error) return { error: `Could not read SKILL.md: ${describeError(query.error)}` };
    const content = query?.data?.data?.content;
    return typeof content === "string" ? { content } : null;
  };
  return children(fileSkillRows(ready.files, ownerOf(ready), readOf));
}

/** The skill rows of a scope in list order, from the server's list or, past its limit, the files. */
export function SkillsSource({ ready, children }: { ready: ReadyScope; children: RowsChildren }) {
  const versionIds = [
    ...new Set(skillsFromFiles(ready.files).map((location) => location.skillFile.versionId)),
  ];
  if (versionIds.length > SKILL_VERSIONS_MAX) {
    return <FileSkills ready={ready}>{children}</FileSkills>;
  }
  return (
    <ServerSkills ready={ready} versionIds={versionIds}>
      {children}
    </ServerSkills>
  );
}
