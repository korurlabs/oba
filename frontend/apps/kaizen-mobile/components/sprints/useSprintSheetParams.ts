import { useLocalSearchParams } from "expo-router";

import type { EditSprintTab } from "@/components/boards/boardRoutes";

function first(value: string | string[] | undefined): string | undefined {
  const raw: string | undefined = Array.isArray(value) ? value[0] : value;
  return raw ? raw : undefined;
}

/** The Edit sprint tab a route parameter names; General for anything else. */
function editSprintTab(value: string | undefined): EditSprintTab {
  return value === "boards" || value === "automation" ? value : "general";
}

/** The board, sprint and tab of a sprint sheet, read from its route parameters. */
export function useSprintSheetParams(): {
  boardId: string | undefined;
  sprintId: string | undefined;
  tab: EditSprintTab;
} {
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  return {
    boardId: first(params.boardId),
    sprintId: first(params.sprintId),
    tab: editSprintTab(first(params.tab)),
  };
}
