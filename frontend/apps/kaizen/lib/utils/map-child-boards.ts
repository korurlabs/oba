/**
 * Maps raw child board data to the minimal shape used by toolbar and kanban components.
 */
export interface MappedChildBoard {
  id: string;
  name: string;
  prefix: string;
  color: string;
}

export function mapChildBoards(
  childBoardsData: Array<{ id: string; name: string; prefix: string; color?: string | null }> | undefined,
): MappedChildBoard[] {
  return (childBoardsData ?? []).map((b) => ({
    id: b.id,
    name: b.name,
    prefix: b.prefix,
    color: b.color ?? "",
  }));
}
