/**
 * The addresses of the Skills list and of one skill's detail page. A skill
 * is addressed by its folder, one URL segment, and its open file by `file`.
 * The scope, release, draft and inherited setting travel with every address,
 * so the target shows the same skills.
 */

import { withKept } from "@/features/rules/components/rule-links";

export function skillsListHref(current: URLSearchParams): string {
  return withKept("/skills", current);
}

/** A skill's page, at one of its files when `file` names one. */
export function skillDetailHref(
  directory: string,
  current: URLSearchParams,
  file: string | null = null
): string {
  return withKept(
    `/skills/${encodeURIComponent(directory)}`,
    current,
    file === null ? [] : [["file", file]]
  );
}

/**
 * Where an older `/skills?skill=<folder>` link now leads: the skill's own
 * page, with every other parameter kept. Null when the link names no skill.
 */
export function legacySkillRedirect(query: URLSearchParams): string | null {
  const skill = query.get("skill");
  if (skill === null || skill === "") return null;
  const rest = new URLSearchParams(query);
  rest.delete("skill");
  const text = rest.toString();
  const page = `/skills/${encodeURIComponent(skill)}`;
  return text === "" ? page : `${page}?${text}`;
}
