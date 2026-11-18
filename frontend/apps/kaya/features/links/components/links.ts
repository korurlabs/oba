/**
 * The page that opens a link's target: convention docs on the Conventions page,
 * skills on the Skills page, settings on the Allowlist page, and rule and
 * agent files on their Rules detail page.
 */

import { ruleDetailHref } from "@/features/rules/components/rule-links";
import { ruleTabOf, type ScopeFile } from "@/features/scope/components/scope-files";

export const CONVENTIONS_PREFIX = "docs/private/conventions/";

const SKILL_DIRECTORY = /^(\.(agents|claude)\/skills\/[^/]+)(\/|$)/;
const SETTINGS = ".claude/settings.json";

/** The query parameters a link keeps, so the target opens in the same scope and release. */
const KEPT_PARAMS = ["scope", "release"] as const;

/**
 * The delivered file a target opens: the file itself, or for a directory the
 * first file below it in path order; null when the scope delivers neither.
 */
export function fileOfTarget(targetPath: string, files: readonly ScopeFile[]): string | null {
  if (!targetPath.endsWith("/") && files.some((file: ScopeFile) => file.path === targetPath)) {
    return targetPath;
  }
  const prefix = targetPath.endsWith("/") ? targetPath : `${targetPath}/`;
  return files.find((file: ScopeFile) => file.path.startsWith(prefix))?.path ?? null;
}

/** A relative symlink target resolved against the symlink's own directory. */
function resolveTarget(linkPath: string, target: string): string {
  const segments = linkPath.split("/").slice(0, -1);
  for (const segment of target.split("/")) {
    if (segment === "..") segments.pop();
    else if (segment !== "." && segment !== "") segments.push(segment);
  }
  return segments.join("/");
}

/**
 * The skill directory the Skills page lists for a link into a skill: the
 * directory whose `SKILL.md` is a delivered regular file. A `.claude/skills`
 * entry that is a symlink into `.agents/skills` lists under its target, since
 * the page, like Claude Code, keys a skill on its real `SKILL.md`.
 */
function skillRowOf(directory: string, files: readonly ScopeFile[]): string | null {
  const isSkill = (candidate: string): boolean =>
    files.some(
      (file: ScopeFile) => file.path === `${candidate}/SKILL.md` && file.mode !== "SYMLINK"
    );
  if (isSkill(directory)) return directory;
  const link = files.find(
    (file: ScopeFile) =>
      file.mode === "SYMLINK" &&
      file.target !== null &&
      (file.path === directory || file.path === `${directory}/SKILL.md`)
  );
  if (!link?.target) return null;
  const resolved = resolveTarget(link.path, link.target);
  const real = link.path === directory ? resolved : resolved.replace(/\/SKILL\.md$/, "");
  return SKILL_DIRECTORY.exec(real)?.[1] === real && isSkill(real) ? real : null;
}

function withKept(page: string, own: [string, string][], current: URLSearchParams): string {
  const query = new URLSearchParams();
  for (const key of KEPT_PARAMS) {
    const value = current.get(key);
    if (value !== null) query.set(key, value);
  }
  for (const [key, value] of own) query.set(key, value);
  const text = query.toString();
  return text === "" ? page : `${page}?${text}`;
}

/**
 * The page that shows a link's target, keeping the current scope and
 * release, with the file to open where the page has one; null when the scope delivers nothing at the target or no page
 * lists it.
 */
export function linkHref(
  targetPath: string,
  files: readonly ScopeFile[],
  current: URLSearchParams
): string | null {
  const skill = SKILL_DIRECTORY.exec(targetPath);
  if (skill?.[1]) {
    const row = skillRowOf(skill[1], files);
    if (row === null) return null;
    // A file of the skill other than its SKILL.md opens in the skill's document.
    const part =
      targetPath.startsWith(`${row}/`) && targetPath !== `${row}/SKILL.md`
        ? fileOfTarget(targetPath, files)
        : null;
    return withKept(
      "/skills",
      part === null
        ? [["skill", row]]
        : [
            ["skill", row],
            ["file", part],
          ],
      current
    );
  }
  const file = fileOfTarget(targetPath, files);
  if (file === null) return null;
  if (file.startsWith(CONVENTIONS_PREFIX)) {
    return withKept("/conventions", [["file", file]], current);
  }
  if (file === SETTINGS) return withKept("/allowlist", [], current);
  const tab = ruleTabOf(file);
  // The Rules detail page also keeps the inherited setting, since an inherited file is listed only with it.
  return tab === null ? null : ruleDetailHref(tab, file, current);
}
