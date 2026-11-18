import type { Dependency, DependencyRelation, DependencyResponse } from "@/features/links/schemas";

/** How a file depends on the entry it links to. */
export const LINKS_TO_LABELS: Readonly<Record<DependencyRelation, string>> = {
  MARKDOWN_LINK: "Markdown link",
  RULE_CONVENTION: "Convention the rule cites",
  SKILL_PART: "Part of the skill",
};

/** How the entry that uses a file depends on it. */
export const USED_BY_LABELS: Readonly<Record<DependencyRelation, string>> = {
  MARKDOWN_LINK: "Links to this file",
  RULE_CONVENTION: "Rule citing this convention",
  SKILL_PART: "Skill made of this part",
};

const SKILL_FILE = /^\.(?:agents|claude)\/skills\/([^/]+)\/SKILL\.md$/;

/**
 * The skills that share a part, by name, when two or more do: a save of the
 * part changes every one of them, the skill it is opened in included. Null
 * when the file is no shared part.
 */
export function sharingSkills(dependencies: DependencyResponse | undefined): string[] | null {
  if (dependencies === undefined) return null;
  const names = dependencies.usedBy
    .filter((entry: Dependency) => entry.relation === "SKILL_PART")
    .map((entry: Dependency) => SKILL_FILE.exec(entry.path)?.[1] ?? entry.path);
  const unique = [...new Set(names)].sort();
  return unique.length >= 2 ? unique : null;
}

/** "kazan and kagi", "a, b and c": skill names for a sentence. */
export function namesText(names: readonly string[]): string {
  if (names.length <= 1) return names[0] ?? "";
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1] ?? ""}`;
}

const SKILL_PART = /^\.(?:agents|claude)\/skills\/[^/]+\/.+$/;

/** Whether a path is a part of a skill directory, which several skills can share; `SKILL.md` is not. */
export function isSkillPart(path: string): boolean {
  return SKILL_PART.test(path) && !path.endsWith("/SKILL.md");
}

/**
 * Whether a save of a file of the viewer's draft asks first: a skill part
 * shared by two or more skills asks with their names, and a part whose
 * sharers could not be read asks without them, since a save could still
 * change other skills. A part is LOADING while its sharers load.
 */
export type PartGate =
  { kind: "OPEN" } | { kind: "LOADING" } | { kind: "ASK"; skills: string[] | null };

export function partGate(input: {
  path: string;
  dependencies: DependencyResponse | undefined;
  failed: boolean;
}): PartGate {
  if (!isSkillPart(input.path)) return { kind: "OPEN" };
  if (input.dependencies === undefined) {
    return input.failed ? { kind: "ASK", skills: null } : { kind: "LOADING" };
  }
  const skills = sharingSkills(input.dependencies);
  return skills === null ? { kind: "OPEN" } : { kind: "ASK", skills };
}

/**
 * What a save of the part asks the member, or null when it saves at once. A
 * save while the sharers still load asks too, so a slow read never skips
 * the question.
 */
export function sharedPartWarning(gate: PartGate): string | null {
  if (gate.kind === "OPEN") return null;
  if (gate.kind === "LOADING") {
    return "Kaya is still reading which skills share this part. A save changes every skill made of it.";
  }
  return gate.skills === null
    ? "Kaya could not read which skills share this part. A save changes every skill made of it."
    : `The skills ${namesText(gate.skills)} share this part. A save changes all of them.`;
}
