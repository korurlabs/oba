"use client";

import { useMemo, useCallback, useState, useEffect } from "react";
import type { VisibilityState } from "@tanstack/react-table";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { boardDefaultTab, boardPath, type BoardTab } from "@/features/boards/lib/board-route";
import { useKazanAppearance } from "@/features/settings/hooks/use-kazan-appearance";

// ── Types ────────────────────────────────────────────────────────────

/** Serializable filter state (only the filter-relevant keys, excluding sort/pagination) */
interface SavedFilters {
  search?: string | null;
  statuses?: string[] | null;
  types?: string[] | null;
  priorities?: string[] | null;
  assigneeIds?: string[] | null;
  reviewerIds?: string[] | null;
  createdByIds?: string[] | null;
  labelIds?: string[] | null;
  boardIds?: string[] | null;
  projectId?: string | null;
  dueDateStart?: string | null;
  dueDateEnd?: string | null;
  prStates?: string[] | null;
  prAuthors?: string[] | null;
}

/** Board-specific preference entry */
interface BoardPreference {
  filters?: SavedFilters;
  columnVisibility?: VisibilityState;
  columnOrder?: string[];
}

/** Top-level shape inside userPreferences for kazan boards */
interface KazanBoardPreferences {
  [boardId: string]: BoardPreference;
}

/** A column layout: which columns are hidden and in what order they appear. */
interface ColumnLayout {
  columnVisibility: VisibilityState;
  columnOrder: string[];
}

/**
 * Top-level userPreferences key of the column layout shared by every ticket
 * table. It sits beside `kazan` rather than inside it, so it can never collide
 * with a board id.
 */
const COLUMN_DEFAULT_KEY = "kazanColumnDefault";

const EMPTY_COLUMN_LAYOUT: ColumnLayout = { columnVisibility: {}, columnOrder: [] };

/** A table has its own layout only when it saved a visibility or an order. */
function hasOwnColumnLayout(pref: BoardPreference | null): boolean {
  if (!pref) return false;
  return (
    Object.keys(pref.columnVisibility ?? {}).length > 0 || (pref.columnOrder ?? []).length > 0
  );
}

// ── Filter keys that are persisted ────────────────────────────────────

const FILTER_KEYS: (keyof SavedFilters)[] = [
  "search",
  "statuses",
  "types",
  "priorities",
  "assigneeIds",
  "reviewerIds",
  "createdByIds",
  "labelIds",
  "boardIds",
  "projectId",
  "dueDateStart",
  "dueDateEnd",
  "prStates",
  "prAuthors",
];

function extractSavedFilters(filters: SavedFilters): SavedFilters {
  const saved: SavedFilters = {};
  for (const key of FILTER_KEYS) {
    const value = filters[key];
    if (value !== null && value !== undefined) {
      (saved as Record<string, unknown>)[key] = value;
    }
  }
  return saved;
}

function isEmptyFilter(saved: SavedFilters): boolean {
  return FILTER_KEYS.every((key) => {
    const val = saved[key];
    return val === null || val === undefined || (Array.isArray(val) && val.length === 0);
  });
}

// ── Deep equality helpers ─────────────────────────────────────────────

function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a == null && b == null) return true;
  if (a == null || b == null) return false;
  if (typeof a !== typeof b) return false;

  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false;
    return a.every((val, i) => deepEqual(val, b[i]));
  }

  if (typeof a === "object" && typeof b === "object") {
    const keysA = Object.keys(a as Record<string, unknown>);
    const keysB = Object.keys(b as Record<string, unknown>);
    if (keysA.length !== keysB.length) return false;
    return keysA.every((key) =>
      deepEqual(
        (a as Record<string, unknown>)[key],
        (b as Record<string, unknown>)[key],
      ),
    );
  }

  return false;
}

// ── Column default ────────────────────────────────────────────────────

/**
 * Reads the user's default column layout, or null when none is saved.
 *
 * The layout is derived from the stored arrays and maps themselves, never from
 * `userPreferences` as a whole. Saving anything else (filters, a pinned board,
 * the theme) replaces that wrapper, while the query cache keeps the unchanged
 * layout values identical; a layout derived from the wrapper would change
 * identity on every save and discard a caller's unsaved column edits.
 */
function useStoredColumnDefault(
  userPreferences: Record<string, unknown> | undefined,
): ColumnLayout | null {
  const storedDefault = userPreferences?.[COLUMN_DEFAULT_KEY] as
    | Partial<ColumnLayout>
    | undefined;
  const hasColumnDefault = storedDefault != null;
  const defaultVisibility = storedDefault?.columnVisibility;
  const defaultOrder = storedDefault?.columnOrder;

  return useMemo(
    (): ColumnLayout | null =>
      hasColumnDefault
        ? {
            columnVisibility: defaultVisibility ?? EMPTY_COLUMN_LAYOUT.columnVisibility,
            columnOrder: defaultOrder ?? EMPTY_COLUMN_LAYOUT.columnOrder,
          }
        : null,
    [hasColumnDefault, defaultVisibility, defaultOrder],
  );
}

/**
 * The user's default column layout on its own, for the settings page, where
 * no table is in view. Tables read the default through useBoardPreferences.
 */
export function useColumnDefault() {
  const t = useTranslations("preferences");
  const { data: meResponse, isFetched: isMeFetched, isRefetching: isMeRefetching } = useGetMe();
  const updateMeMutation = useUpdateMe();

  const userPreferences = meResponse?.data?.userPreferences as
    | Record<string, unknown>
    | undefined;
  const columnDefault = useStoredColumnDefault(userPreferences);

  // PUT /users/me replaces the whole map, so every save starts from the cached copy.
  const writeUserPreferences = useCallback(
    async (build: (current: Record<string, unknown>) => Record<string, unknown>) => {
      await updateMeMutation.mutateAsync({ userPreferences: build(userPreferences ?? {}) });
    },
    [userPreferences, updateMeMutation],
  );

  /** Stores the layout as the default of every ticket table. */
  const saveColumnDefault = useCallback(
    async (visibility: VisibilityState, order: string[]) => {
      const layout: ColumnLayout = { columnVisibility: visibility, columnOrder: order };
      try {
        await writeUserPreferences((current) => ({ ...current, [COLUMN_DEFAULT_KEY]: layout }));
        toast.success(t("defaultSaved"));
      } catch (error) {
        console.error("[useColumnDefault] column default save failed:", error);
        toast.error(t("defaultSaveError"));
      }
    },
    [writeUserPreferences, t],
  );

  /**
   * Deletes the default, so every table without a layout of its own shows the
   * built-in layout again. Tables with their own layout are not touched.
   */
  const clearColumnDefault = useCallback(async () => {
    try {
      await writeUserPreferences((current) => {
        const { [COLUMN_DEFAULT_KEY]: _default, ...rest } = current;
        return rest;
      });
      toast.success(t("defaultCleared"));
    } catch (error) {
      console.error("[useColumnDefault] column default reset failed:", error);
      toast.error(t("defaultClearError"));
    }
  }, [writeUserPreferences, t]);

  return {
    columnDefault,
    hasColumnDefault: columnDefault !== null,
    saveColumnDefault,
    clearColumnDefault,
    // Saved values are stale until the refetch of the user after a save ends.
    isSaving: updateMeMutation.isPending || isMeRefetching,
    isMeFetched,
  };
}

// ── Hook ──────────────────────────────────────────────────────────────

export function useBoardPreferences(boardId: string) {
  const t = useTranslations("preferences");
  const { data: meResponse, isFetched: isMeFetched, isRefetching: isMeRefetching } = useGetMe();
  const updateMeMutation = useUpdateMe();

  const userPreferences = meResponse?.data?.userPreferences;

  // Extract board-specific preferences from the user data
  const boardPreferences = useMemo((): BoardPreference | null => {
    if (!userPreferences) return null;
    const kazanPrefs = userPreferences["kazan"] as KazanBoardPreferences | undefined;
    if (!kazanPrefs) return null;
    return kazanPrefs[boardId] ?? null;
  }, [userPreferences, boardId]);

  const savedFilters = useMemo(
    (): SavedFilters | null => boardPreferences?.filters ?? null,
    [boardPreferences],
  );

  // The layouts are derived from the stored arrays and maps themselves, never
  // from `userPreferences` or `boardPreferences` as a whole. Saving anything
  // else (filters, a pinned board, the theme) replaces those wrappers, while
  // the query cache keeps the unchanged layout values identical; a layout
  // derived from a wrapper would re-run the effect below and discard the
  // user's unsaved column edits.
  const columnDefault = useStoredColumnDefault(
    userPreferences as Record<string, unknown> | undefined,
  );
  const hasColumnDefault = columnDefault !== null;
  const overrideVisibility = boardPreferences?.columnVisibility;
  const overrideOrder = boardPreferences?.columnOrder;

  const hasColumnOverride = hasOwnColumnLayout(boardPreferences);

  // The layout the table shows when nothing is changed locally: its own
  // layout, else the user's default, else the built-in layout (every column
  // visible, in definition order). Column ids the table does not have are
  // ignored by the table, and columns missing from the order are appended.
  const effectiveColumnLayout = useMemo((): ColumnLayout => {
    if (hasColumnOverride) {
      return {
        columnVisibility: overrideVisibility ?? EMPTY_COLUMN_LAYOUT.columnVisibility,
        columnOrder: overrideOrder ?? EMPTY_COLUMN_LAYOUT.columnOrder,
      };
    }
    return columnDefault ?? EMPTY_COLUMN_LAYOUT;
  }, [hasColumnOverride, overrideVisibility, overrideOrder, columnDefault]);

  // ── Column visibility & order state ─────────────────────────────────
  // Initialized from the effective layout when boardId changes or when a
  // stored layout loads or changes.

  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [columnOrder, setColumnOrder] = useState<string[]>([]);

  useEffect(() => {
    if (!isMeFetched) return;

    setColumnVisibility(effectiveColumnLayout.columnVisibility);
    setColumnOrder(effectiveColumnLayout.columnOrder);
  }, [isMeFetched, boardId, effectiveColumnLayout]);

  // ── Dirty checks ───────────────────────────────────────────────────

  const hasFilterChanges = useCallback(
    (currentFilters: SavedFilters): boolean => {
      const current = extractSavedFilters(currentFilters);
      const saved = extractSavedFilters(savedFilters ?? {});
      return !deepEqual(current, saved);
    },
    [savedFilters],
  );

  const hasColumnChanges = useCallback(
    (
      currentVisibility: VisibilityState,
      currentOrder: string[],
    ): boolean => {
      return (
        !deepEqual(currentVisibility, effectiveColumnLayout.columnVisibility) ||
        !deepEqual(currentOrder, effectiveColumnLayout.columnOrder)
      );
    },
    [effectiveColumnLayout],
  );

  // ── Save helpers ───────────────────────────────────────────────────

  // PUT /users/me replaces the whole map, so every save starts from the cached copy.
  const writeUserPreferences = useCallback(
    async (build: (current: Record<string, unknown>) => Record<string, unknown>) => {
      const currentPrefs = (userPreferences ?? {}) as Record<string, unknown>;
      await updateMeMutation.mutateAsync({
        userPreferences: build(currentPrefs),
      });
    },
    [userPreferences, updateMeMutation],
  );

  const writeBoardPreference = useCallback(
    async (build: (current: BoardPreference) => BoardPreference) => {
      await writeUserPreferences((currentPrefs) => {
        const currentKazan = (currentPrefs["kazan"] ?? {}) as KazanBoardPreferences;
        const newKazan: KazanBoardPreferences = {
          ...currentKazan,
          [boardId]: build(currentKazan[boardId] ?? {}),
        };
        return { ...currentPrefs, kazan: newKazan };
      });
    },
    [boardId, writeUserPreferences],
  );

  const saveBoardPreference = useCallback(
    async (patch: Partial<BoardPreference>) => {
      await writeBoardPreference((current) => ({ ...current, ...patch }));
    },
    [writeBoardPreference],
  );

  // A saved set lists every field, null when cleared, so a saved "no filters"
  // stays distinct from nothing saved: an empty object is what older versions
  // stored on Reset.
  const saveFilters = useCallback(
    async (currentFilters: SavedFilters) => {
      const filters = Object.fromEntries(
        FILTER_KEYS.map((key) => [key, currentFilters[key] ?? null]),
      ) as SavedFilters;
      try {
        await saveBoardPreference({ filters });
        toast.success(t("filtersSaved"));
      } catch {
        toast.error(t("filtersSaveError"));
      }
    },
    [saveBoardPreference, t],
  );

  const saveColumns = useCallback(
    async (visibility: VisibilityState, order: string[]) => {
      try {
        await saveBoardPreference({
          columnVisibility: visibility,
          columnOrder: order,
        });
        toast.success(t("columnsSaved"));
      } catch {
        toast.error(t("columnsSaveError"));
      }
    },
    [saveBoardPreference, t],
  );

  /**
   * Stores the layout as the default of every ticket table. The current table
   * gets no layout of its own, so it keeps following the default afterwards.
   */
  const saveColumnsAsDefault = useCallback(
    async (visibility: VisibilityState, order: string[]) => {
      const layout: ColumnLayout = { columnVisibility: visibility, columnOrder: order };
      try {
        await writeUserPreferences((current) => ({ ...current, [COLUMN_DEFAULT_KEY]: layout }));
        toast.success(t("defaultSaved"));
      } catch (error) {
        console.error("[useBoardPreferences] column default save failed:", error);
        toast.error(t("defaultSaveError"));
      }
    },
    [writeUserPreferences, t],
  );

  // ── Reset ──────────────────────────────────────────────────────────

  /** Whether a saved set filters anything. */
  const hasSavedFilters = savedFilters !== null && !isEmptyFilter(savedFilters);

  /**
   * Whether the user saved a set, including one that filters nothing. A page
   * with a default of its own (assignee = me, open statuses) uses the saved set
   * in place of that default even when the set is empty.
   */
  const hasSavedFilterSet = savedFilters !== null && Object.keys(savedFilters).length > 0;

  /**
   * Deletes the saved filters, so the page's own default applies again.
   * Resolves false after showing the error toast, so a caller can keep the
   * filters on screen when the saved set did not change.
   */
  const resetFilters = useCallback(async (): Promise<boolean> => {
    try {
      await writeBoardPreference((current) => {
        const { filters: _filters, ...rest } = current;
        return rest;
      });
      toast.success(t("filtersReset"));
      return true;
    } catch {
      toast.error(t("filtersResetError"));
      return false;
    }
  }, [writeBoardPreference, t]);

  /**
   * "Use default": deletes this table's own layout so it follows the user's
   * default again (or the built-in layout when no default is saved).
   */
  const resetColumns = useCallback(async () => {
    try {
      await writeBoardPreference((current) => {
        const { columnVisibility: _visibility, columnOrder: _order, ...rest } = current;
        return rest;
      });
      // Show the default at once rather than after the refetch of the user.
      const fallback = columnDefault ?? EMPTY_COLUMN_LAYOUT;
      setColumnVisibility(fallback.columnVisibility);
      setColumnOrder(fallback.columnOrder);
      toast.success(t("columnsReset"));
    } catch (error) {
      console.error("[useBoardPreferences] column layout reset failed:", error);
      toast.error(t("columnsResetError"));
    }
  }, [writeBoardPreference, columnDefault, t]);

  // Include refetch window: after mutation completes, useGetMe refetches.
  // During refetch, saved values are stale — keep isSaving true until fresh data arrives.
  const isSaving = updateMeMutation.isPending || isMeRefetching;

  return {
    // Saved state
    savedFilters,
    columnDefault,

    // Column visibility & order (initialized from saved prefs)
    columnVisibility,
    setColumnVisibility,
    columnOrder,
    setColumnOrder,

    // Dirty checks
    hasFilterChanges,
    hasColumnChanges,

    // Actions
    saveFilters,
    saveColumns,
    saveColumnsAsDefault,
    resetFilters,
    resetColumns,

    // Flags
    hasSavedFilters,
    hasSavedFilterSet,
    hasColumnOverride,
    hasColumnDefault,
    isSaving,
    isMeFetched,
  };
}

// ── Helper: board href ───────────────────────────────────────────────

/**
 * Returns a function that builds the href of a board tab. The href carries no
 * filter parameter: a ticket table applies the user's saved filters itself and
 * puts in the URL only what differs from them. The path names the board by
 * slug; the preferences stay keyed by board id, so a rename does not lose them.
 */
export function useBoardHref() {
  // Without a tab, a link opens the first tab of the user's layout.
  const defaultTab = boardDefaultTab(useKazanAppearance().layout);
  return useCallback(
    (board: { id: string; slug: string }, tab: BoardTab = defaultTab): string =>
      boardPath(board.slug, tab),
    [defaultTab],
  );
}
