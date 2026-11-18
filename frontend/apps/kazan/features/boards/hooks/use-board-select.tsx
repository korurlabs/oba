import { useCallback, useMemo } from "react";
import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import { BoardMemberRoleChip } from "@/features/boards/components/board-member-role-chip";
import type { SelectOption } from "@oba/shared/features/form/components";
import type { BoardListWithChildrenResponse, BoardMemberRole } from "../schemas";

// ── Tree utilities ──────────────────────────────────────────────────

function collectChildBoardIds(boards: BoardListWithChildrenResponse[]): Set<string> {
  const childIds = new Set<string>();
  for (const board of boards) {
    if (board.childBoards?.length) {
      for (const child of board.childBoards as BoardListWithChildrenResponse[]) {
        childIds.add(child.id);
      }
      const nested = collectChildBoardIds(board.childBoards as BoardListWithChildrenResponse[]);
      for (const id of nested) {
        childIds.add(id);
      }
    }
  }
  return childIds;
}

export function flattenBoardsForSelect(
  boards: BoardListWithChildrenResponse[],
  depth = 0,
  childBoardIds?: Set<string>,
): Array<BoardListWithChildrenResponse & { depth: number }> {
  const knownChildIds = childBoardIds ?? collectChildBoardIds(boards);
  const result: Array<BoardListWithChildrenResponse & { depth: number }> = [];

  for (const board of boards) {
    if (depth === 0 && knownChildIds.has(board.id)) {
      continue;
    }
    result.push({ ...board, depth });
    if (board.childBoards?.length) {
      result.push(
        ...flattenBoardsForSelect(
          board.childBoards as BoardListWithChildrenResponse[],
          depth + 1,
          knownChildIds,
        ),
      );
    }
  }
  return result;
}

// ── Types ───────────────────────────────────────────────────────────

interface BoardOptionMeta {
  excludeIds?: Set<string>;
  disabledIds?: Set<string>;
  disabledHintMap?: Map<string, string>;
  /** When set, boards matching these IDs get their role shown as a chip and are disabled */
  disabledRoleMap?: Map<string, BoardMemberRole>;
  /**
   * @deprecated Use `disabledRoleMap` instead.
   * When set, boards matching these IDs get their role shown as a chip.
   */
  viewerBoardIds?: Set<string>;
}

interface UseBoardSelectReturn {
  options: SelectOption[];
  renderBoardOption: (option: SelectOption) => React.ReactNode;
  renderBoardValue: (option: SelectOption) => React.ReactNode;
}

// ── Hook ────────────────────────────────────────────────────────────

export function useBoardSelect(
  boards: BoardListWithChildrenResponse[],
  meta?: BoardOptionMeta,
): UseBoardSelectReturn {
  const {
    excludeIds = new Set(),
    disabledIds = new Set(),
    disabledHintMap = new Map(),
    disabledRoleMap = new Map(),
    viewerBoardIds = new Set(),
  } = meta ?? {};

  const options = useMemo((): SelectOption[] => {
    const flatBoards = flattenBoardsForSelect(boards);
    return flatBoards
      .filter((b) => !excludeIds.has(b.id))
      .map((board) => {
        const hint = disabledHintMap.get(board.id);
        const roleFromMap = disabledRoleMap.get(board.id);
        const isViewer = viewerBoardIds.has(board.id);
        const isDisabled = disabledIds.has(board.id) || !!hint || !!roleFromMap || isViewer;

        return {
          value: board.id,
          label: board.name,
          disabled: isDisabled,
          disabledLabel: hint,
          meta: {
            color: board.color,
            prefix: board.prefix,
            depth: board.depth,
            disabledRole: roleFromMap ?? (isViewer ? ("VIEWER" as BoardMemberRole) : undefined),
          },
        };
      });
  }, [boards, excludeIds, disabledIds, disabledHintMap, disabledRoleMap, viewerBoardIds]);

  const renderBoardOption = useCallback((option: SelectOption) => {
    const color = option.meta?.color as string | undefined;
    const prefix = option.meta?.prefix as string | undefined;
    const depth = (option.meta?.depth as number) ?? 0;
    const disabledRole = option.meta?.disabledRole as BoardMemberRole | undefined;

    return (
      <span
        className="flex w-full items-center gap-1.5"
        style={{ paddingLeft: depth > 0 ? `${depth * 16}px` : undefined }}
      >
        {depth > 0 && <span className="text-muted-foreground text-xs">{"\u21B3"}</span>}
        <BoardDot color={color} />
        <span className="min-w-0 flex-1 truncate">{option.label}</span>
        {prefix && (
          <Badge variant="secondary" className="ml-auto shrink-0 px-1.5 py-0 text-[10px]">
            {prefix}
          </Badge>
        )}
        {disabledRole && <BoardMemberRoleChip role={disabledRole} size="sm" />}
        {option.disabled && option.disabledLabel && (
          <span className="text-muted-foreground text-xs">({option.disabledLabel})</span>
        )}
      </span>
    );
  }, []);

  /**
   * The selected board in a select trigger. The trigger does not grow to its
   * full width, so the tree layout of `renderBoardOption` (depth padding,
   * arrow, a name that only fills leftover space) would squeeze the name to
   * nothing; here the name takes its own width and truncates instead.
   */
  const renderBoardValue = useCallback((option: SelectOption) => {
    const color = option.meta?.color as string | undefined;
    const prefix = option.meta?.prefix as string | undefined;

    return (
      <span className="flex min-w-0 items-center gap-1.5">
        <BoardDot color={color} />
        <span className="truncate">{option.label}</span>
        {prefix && (
          <Badge variant="secondary" className="shrink-0 px-1.5 py-0 text-[10px]">
            {prefix}
          </Badge>
        )}
      </span>
    );
  }, []);

  return { options, renderBoardOption, renderBoardValue };
}
