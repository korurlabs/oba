import type { LucideIcon } from "lucide-react-native";

/*
 * The pure half of ActionSheet: the action shape and the order the sheet shows
 * them in. Its only lucide import is a type, so `bun test` can load it.
 */

export type ActionSheetAction = {
  label: string;
  /** Leading icon of the Android row; iOS action sheets show no icons. */
  icon?: LucideIcon;
  destructive?: boolean;
  disabled?: boolean;
  /**
   * The current choice of a picker menu, such as the app language. Android
   * marks its row with a check on the accent ground; iOS action sheets have no
   * checked state, so their message names it. The row stays tappable.
   */
  selected?: boolean;
  onPress: () => void;
};

/**
 * Destructive actions go last, nearest the thumb's exit and apart from the
 * safe ones, as both platforms' guidelines place them. The order is otherwise
 * kept. Never mutates the input.
 */
export function orderActions<T extends Pick<ActionSheetAction, "destructive">>(
  actions: readonly T[]
): T[] {
  return [
    ...actions.filter((action) => !action.destructive),
    ...actions.filter((action) => action.destructive),
  ];
}

/** The ActionSheetIOS indices of the destructive actions, after ordering. */
export function destructiveIndices(
  actions: readonly Pick<ActionSheetAction, "destructive">[]
): number[] {
  return actions.flatMap((action, index) => (action.destructive ? [index] : []));
}

/** The label of the selected action, or null when the menu has no current choice. */
export function selectedLabel(
  actions: readonly Pick<ActionSheetAction, "label" | "selected">[]
): string | null {
  return actions.find((action) => action.selected)?.label ?? null;
}

/** The ActionSheetIOS indices of the disabled actions, after ordering. */
export function disabledIndices(actions: readonly Pick<ActionSheetAction, "disabled">[]): number[] {
  return actions.flatMap((action, index) => (action.disabled ? [index] : []));
}

/** The read of the reader's role that a menu's actions depend on. */
export type RoleReadState = {
  /** The role, or null for a non-member and while it is unknown. */
  role: string | null;
  /** True while the role is still being read. */
  isPending: boolean;
  /** True while a pending read waits for the network or retries a failed attempt. */
  isStalled: boolean;
  /** The failed read, if one failed. */
  error: Error | null;
};

/**
 * Whether a menu whose actions depend on the reader's role shows. iOS reads
 * a system action sheet's actions once, when it opens, and cannot change them
 * while it is shown; a menu asked for while the role is being read therefore
 * waits for it, so an action the role grants is never missing from it. A read
 * that failed or stalls opens the menu with what every reader has, instead
 * of leaving the tap unanswered. Once shown (`shown`), the menu stays until
 * it closes, so a stalled read that resumes never takes it away again.
 */
export function roleMenuVisible(
  requested: boolean,
  shown: boolean,
  read: Pick<RoleReadState, "isPending" | "isStalled">
): boolean {
  if (!requested) return false;
  return shown || !read.isPending || read.isStalled;
}

/**
 * How long a menu waits for a role read that is online but slow. Past it the
 * menu opens without the role's actions, as for a stalled read, so a tap is
 * never left unanswered for the length of a request timeout.
 */
export const ROLE_MENU_WAIT_MS = 1500;

/**
 * The read as a waiting menu sees it: once the menu has waited
 * `ROLE_MENU_WAIT_MS` (`waitExpired`), a read still pending counts as stalled.
 */
export function cappedRoleRead<T extends Pick<RoleReadState, "isPending" | "isStalled">>(
  read: T,
  waitExpired: boolean
): T {
  return waitExpired && read.isPending && !read.isStalled ? { ...read, isStalled: true } : read;
}

/**
 * True when the role is unknown because its read failed or stalls, so a menu
 * that opens now lacks the actions the role would grant. A role still held
 * from an earlier read counts as known.
 */
export function roleMenuLacksRole(read: RoleReadState): boolean {
  return read.role === null && (read.error !== null || (read.isPending && read.isStalled));
}
