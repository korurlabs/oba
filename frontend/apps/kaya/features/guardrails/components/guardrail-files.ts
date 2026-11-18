/**
 * Which rules of a scope are guardrails, and where a new one starts. A
 * guardrail is a rule under `.agents/rules/guardrails/`: child packs and
 * repositories inherit it locked, so it is the one rule nobody below its pack
 * can override. A pack mounted in a folder holds none, as guardrails steer
 * the whole repository.
 */

import type { MountRef } from "@/features/scope/components/mount-paths";
import type { ScopeFile } from "@/features/scope/components/scope-files";

/** The folder every guardrail lives in. */
export const GUARDRAILS_FOLDER = ".agents/rules/guardrails/";

export function isGuardrailPath(path: string): boolean {
  return path.startsWith(GUARDRAILS_FOLDER) && path.length > GUARDRAILS_FOLDER.length;
}

/** The guardrails among a scope's files, in the scope's path order. */
export function guardrailFiles(files: readonly ScopeFile[]): ScopeFile[] {
  return files.filter((file: ScopeFile) => isGuardrailPath(file.path));
}

/** Where a new guardrail starts; null for a pack mounted in a folder, which cannot hold one. */
export function newGuardrailStart(mount: MountRef | null): string | null {
  return mount === null ? GUARDRAILS_FOLDER : null;
}
