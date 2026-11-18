import type { Owner } from "../schemas";

/** "Pack village": every page puts the word before a pack's name, so it never reads as a repository. */
export function ownerLabel(owner: Pick<Owner, "name">): string {
  return `Pack ${owner.name}`;
}
