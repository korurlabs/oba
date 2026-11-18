import { canManageSprints } from "@/components/boards/boardModel";
import { useMyBoardRole } from "@/hooks/queries/boards";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";

/**
 * Whether the user may create, edit, start and complete sprints of the board.
 * The board views hide those actions otherwise; the server checks the same.
 */
export function useCanManageSprints(boardId: string): boolean {
  return canManageSprints(useOrganizationRole(), useMyBoardRole(boardId));
}
