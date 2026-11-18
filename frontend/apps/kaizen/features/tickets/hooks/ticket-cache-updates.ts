/**
 * Optimistic cache helpers for ticket mutations.
 *
 * Drag-and-drop must feel instant: the card has to land in the target column
 * on drop, not after the PUT/POST resolves. These helpers patch every cached
 * shape a ticket can appear in, record exactly what they patched so a failed
 * request can be rolled back, and gate the post-mutation invalidation so
 * overlapping drags do not flicker.
 *
 * Cached shapes covered (all values are the `CustomResponse` envelope returned
 * by the API layer -- `res.data`, i.e. `{ success, message, data, pagination }`):
 *
 *   ["tickets", "list", boardId, params]   -> ApiResponse<TicketListResponse[]>
 *   ["tickets", "all-tickets", "list", params]
 *                                          -> ApiResponse<AllTicketListResponse[]>
 *                                             (the cross-board kanban)
 *   ["tickets", "detail", boardId, id]     -> ApiResponse<TicketDetailResponse>
 *   ["backlog", "detail", boardId]         -> ApiResponse<BacklogDetailResponse>
 *                                             (its optional `tickets` array)
 *
 * Sprint caches (["sprints", ...]) hold no ticket objects -- `sprintListResponse`
 * and `sprintDetailResponse` carry no ticket array -- so nothing to patch there.
 *
 * The cross-board lists are keyed by filters, not by a sprint or backlog, so
 * they only take field patches: a move or reorder transform would see a list
 * that is no move's target and drop the row from it.
 *
 * Sub-tickets are nested inside `subTickets` on both list and detail shapes, so
 * field patches walk the tree. A container move takes the ticket and the
 * sub-tickets that come along, inserts it at its `displayOrder` slot of the
 * target list unless its parent is listed there, and never detaches a
 * sub-ticket from its parent (see `applyOptimisticTicketMove`). A reorder applies to whichever level holds the
 * dragged ticket: the top-level rows for a table row drag, or one parent's
 * `subTickets` for a drag in the ticket detail's sub-ticket list. It also
 * mirrors the server's board-wide `displayOrder` shift, so a second drag issued
 * before the refetch sends the order the server will hold by then.
 */

import { hashKey, type QueryClient, type QueryKey } from "@tanstack/react-query";
import { BACKLOG_KEYS } from "@/features/backlog/hooks/query-keys";
import { SPRINT_KEYS } from "@/features/sprints/hooks/query-keys";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import type {
  SubTicketMoveMode,
  TicketDetailResponse,
  TicketListResponse,
  TicketParentResponse,
  TicketSprintResponse,
  TicketUpdateRequest,
} from "../schemas";
import { TICKET_KEYS } from "./query-keys";

// ── Types ───────────────────────────────────────────────────────────

/**
 * Fields shared by the list and detail response shapes. Only these are merged
 * into cached list rows so an optimistic patch never invents a property the
 * list shape does not have.
 */
export type TicketListPatch = Partial<
  Pick<
    TicketListResponse,
    "title" | "type" | "status" | "priority" | "missionRank" | "percentage" | "weight" | "dueDate"
  >
>;

/** List fields plus the detail-only text fields. */
export type TicketDetailPatch = TicketListPatch &
  Partial<Pick<TicketDetailResponse, "description" | "rca" | "lessonsLearned">>;

/**
 * Target of a move: exactly one of the two container ids is set. `order`, when
 * set, is the `displayOrder` the ticket takes on its board, which places it
 * between the neighbours it was dropped between. `subTicketMode` and
 * `subTicketIds` pick the sub-tickets that come along, as in the request;
 * absent means the unfinished ones placed with the ticket.
 */
export interface TicketMoveTarget {
  sprintId?: string;
  backlogId?: string;
  order?: number;
  subTicketMode?: SubTicketMoveMode;
  subTicketIds?: string[];
}

/**
 * Where a reordered ticket should land.
 *
 * `ticketId` -- the id of the row the ticket was dropped on -- is the reliable
 * locator and should always be supplied by drag handlers. `order` is the drop
 * target's `displayOrder`, the order the request asks the server to store. It
 * sets the moved ticket's new `displayOrder` and bounds the mirrored shift, but
 * locates the target only as a fallback: `displayOrder` is unique per board,
 * so any list running with `includeAllBoards` routinely holds duplicate values
 * and locating by value picks the wrong row.
 */
export interface TicketReorderTarget {
  ticketId?: string;
  order?: number;
}

/** One cache entry an optimistic pass rewrote, with both sides of the write. */
interface TicketCacheEntry {
  readonly queryKey: QueryKey;
  /** Value held before the optimistic write. */
  readonly previous: unknown;
  /** Value the cache held immediately after the optimistic write. */
  readonly applied: unknown;
}

/** The cache entries a single optimistic pass rewrote -- and only those. */
export type TicketCacheSnapshot = readonly TicketCacheEntry[];

/**
 * Shared mutation-key prefix for every ticket mutation that participates in the
 * settle gate. Used to count only sibling ticket mutations when deciding
 * whether to invalidate (see `settleTicketMutation`).
 */
export const TICKET_MUTATION_KEY = [...TICKET_KEYS.all, "mutation"] as const;

const CACHE_ROOTS: readonly QueryKey[] = [TICKET_KEYS.all, BACKLOG_KEYS.all];

// ── Small runtime guards ────────────────────────────────────────────

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function keySegment(queryKey: QueryKey, index: number): unknown {
  return (queryKey as readonly unknown[])[index];
}

/** Filter params embedded in a ticket list key: ["tickets","list",boardId,params]. */
function listKeyParams(queryKey: QueryKey): Record<string, unknown> {
  const params = keySegment(queryKey, 3);
  return isRecord(params) ? params : {};
}

// ── Scoped cache writer ─────────────────────────────────────────────

/**
 * Writes optimistic values while recording the pre-mutation value of every
 * entry it touches.
 *
 * The snapshot is deliberately narrow: only entries this pass actually
 * rewrote. Snapshotting whole cache roots up front made two overlapping drags
 * capture each other's optimistic state, so a rollback could re-apply a patch
 * the other drag had already rolled back.
 *
 * `applied` is read back out of the cache rather than being the object handed
 * to `setQueryData` -- TanStack runs `replaceEqualDeep` on writes, so the
 * stored reference is not the one passed in.
 */
interface TicketCacheWriter {
  write: (queryKey: QueryKey, previous: unknown, next: unknown) => void;
  snapshot: () => TicketCacheSnapshot;
}

function createCacheWriter(queryClient: QueryClient): TicketCacheWriter {
  const entries = new Map<string, TicketCacheEntry>();

  return {
    write: (queryKey, previous, next) => {
      queryClient.setQueryData(queryKey, next);
      const applied = queryClient.getQueryData(queryKey);
      const hash = hashKey(queryKey);
      const existing = entries.get(hash);
      // First write to a key owns `previous`; later writes only update
      // `applied`, so the rollback still targets the pre-mutation value.
      entries.set(hash, {
        queryKey,
        previous: existing ? existing.previous : previous,
        applied,
      });
    },
    snapshot: () => Array.from(entries.values()),
  };
}

// ── Pure list transforms (return null when nothing changed) ─────────

function patchTicketTree(
  tickets: TicketListResponse[],
  ticketId: string,
  patch: TicketListPatch
): TicketListResponse[] | null {
  let changed = false;

  const next = tickets.map((ticket) => {
    if (ticket.id === ticketId) {
      changed = true;
      return { ...ticket, ...patch };
    }

    const subTickets = ticket.subTickets;
    if (subTickets && subTickets.length > 0) {
      const nextSubTickets = patchTicketTree(subTickets, ticketId, patch);
      if (nextSubTickets) {
        changed = true;
        return { ...ticket, subTickets: nextSubTickets };
      }
    }

    return ticket;
  });

  return changed ? next : null;
}

/** Removes every top-level row whose id is in `ticketIds`. */
function removeTickets(
  tickets: TicketListResponse[],
  ticketIds: ReadonlySet<string>
): TicketListResponse[] | null {
  const next = tickets.filter((ticket) => !ticketIds.has(ticket.id));
  return next.length === tickets.length ? null : next;
}

/**
 * Inserts `ticket` as a top-level row at its `displayOrder` slot: before the
 * first row whose `displayOrder` is greater, else at the end. That is where the
 * refetch places it, since every container list is sorted by `displayOrder`
 * ascending; rows of other boards in an `includeAllBoards` list are compared by
 * value too, as the server sorts them. Returns null when the list already holds
 * the ticket.
 */
export function insertTicket<T extends Pick<TicketListResponse, "id" | "displayOrder">>(
  tickets: readonly T[],
  ticket: T
): T[] | null {
  if (tickets.some((existing) => existing.id === ticket.id)) return null;

  const next = [...tickets];
  const slot = next.findIndex((existing) => existing.displayOrder > ticket.displayOrder);
  next.splice(slot === -1 ? next.length : slot, 0, ticket);
  return next;
}

/** Finds a ticket at any depth of `tickets`. */
function findInTicketTree(
  tickets: TicketListResponse[],
  ticketId: string
): TicketListResponse | undefined {
  for (const ticket of tickets) {
    if (ticket.id === ticketId) return ticket;
    const nested = ticket.subTickets ? findInTicketTree(ticket.subTickets, ticketId) : undefined;
    if (nested) return nested;
  }
  return undefined;
}

/**
 * Locates the row a reorder targets.
 *
 * By id when the drag handler supplied one. The `displayOrder` fallback exists
 * only for callers that have not been updated yet: `displayOrder` is unique per
 * board, and every list here can run with `includeAllBoards`, so duplicates are
 * routine and the first match is frequently the wrong row.
 */
function findReorderTargetIndex(
  tickets: TicketListResponse[],
  ticketId: string,
  target: TicketReorderTarget
): number {
  if (target.ticketId !== undefined) {
    if (target.ticketId === ticketId) return -1;
    return tickets.findIndex((ticket) => ticket.id === target.ticketId);
  }

  if (target.order === undefined) return -1;

  return tickets.findIndex(
    (ticket) => ticket.id !== ticketId && ticket.displayOrder === target.order
  );
}

/**
 * Moves `ticketId` into the slot currently held by the drop target.
 * Uses splice-out / splice-in semantics so it matches what the table renders.
 *
 * Only the array order changes here. The `displayOrder` values are rewritten
 * afterwards by `shiftDisplayOrders`, over the whole board at once: rewriting
 * them per array would leave rows of the same board in other arrays with the
 * old values, and two rows sharing one order.
 */
function reorderTickets(
  tickets: TicketListResponse[],
  ticketId: string,
  target: TicketReorderTarget
): TicketListResponse[] | null {
  const from = tickets.findIndex((ticket) => ticket.id === ticketId);
  if (from === -1) return null;

  const to = findReorderTargetIndex(tickets, ticketId, target);
  if (to === -1 || to === from) return null;

  const next = [...tickets];
  const [moved] = next.splice(from, 1);
  if (!moved) return null;
  next.splice(to, 0, moved);
  return next;
}

/**
 * Reorders `ticketId` among its siblings when it is a sub-ticket of one of
 * `tickets`. A sub-ticket has one parent, so the first parent holding it is the
 * only one.
 */
function reorderSubTickets(
  tickets: TicketListResponse[],
  ticketId: string,
  target: TicketReorderTarget
): TicketListResponse[] | null {
  const parentIndex = tickets.findIndex((ticket) =>
    ticket.subTickets?.some((subTicket) => subTicket.id === ticketId)
  );
  const parent = tickets[parentIndex];
  if (!parent?.subTickets) return null;

  const nextSubTickets = reorderTickets(parent.subTickets, ticketId, target);
  if (!nextSubTickets) return null;

  const next = [...tickets];
  next[parentIndex] = { ...parent, subTickets: nextSubTickets };
  return next;
}

/**
 * Mirrors the server's reorder of one ticket on the `displayOrder` values.
 *
 * `displayOrder` is one sequence per board, and the server keeps it unique by
 * shifting every other ticket of the board between the two positions: one up
 * when the ticket moves to an earlier position, one down when it moves to a
 * later one. Without the mirror, a second drag issued before the refetch would
 * send the target's stale order and land somewhere else than it was shown.
 *
 * The shift applies at every depth, to rows whose `board.id` -- else
 * `listBoardId`, the board the cached list is keyed by -- is `boardId`. The
 * moved ticket gets `newOrder` wherever it appears.
 */
export function shiftDisplayOrders(
  tickets: TicketListResponse[],
  boardId: string,
  ticketId: string,
  oldOrder: number,
  newOrder: number,
  listBoardId?: string
): TicketListResponse[] | null {
  const delta = newOrder < oldOrder ? 1 : -1;
  const isShifted = (order: number): boolean =>
    newOrder < oldOrder
      ? order >= newOrder && order < oldOrder
      : order > oldOrder && order <= newOrder;

  let changed = false;

  const next = tickets.map((ticket) => {
    let nextOrder = ticket.displayOrder;
    if (ticket.id === ticketId) {
      nextOrder = newOrder;
    } else if ((ticket.board?.id ?? listBoardId) === boardId && isShifted(ticket.displayOrder)) {
      nextOrder = ticket.displayOrder + delta;
    }

    const nextSubTickets = ticket.subTickets
      ? shiftDisplayOrders(ticket.subTickets, boardId, ticketId, oldOrder, newOrder, listBoardId)
      : null;

    if (nextOrder === ticket.displayOrder && !nextSubTickets) return ticket;
    changed = true;
    return {
      ...ticket,
      displayOrder: nextOrder,
      ...(nextSubTickets ? { subTickets: nextSubTickets } : {}),
    };
  });

  return changed ? next : null;
}

// ── Cache traversal ─────────────────────────────────────────────────

/**
 * What a cached ticket array is keyed by.
 *
 *   - `container`: one board's sprint or backlog list (or the backlog detail's
 *     embedded tickets). Moves and reorders apply.
 *   - `crossBoard`: the cross-board kanban list, keyed by filters only. Only
 *     field patches apply (see the file header).
 */
type CachedTicketListKind = "container" | "crossBoard";

const CONTAINER_LISTS: readonly CachedTicketListKind[] = ["container"];
const ALL_LISTS: readonly CachedTicketListKind[] = ["container", "crossBoard"];

/** A cached envelope that holds an array of tickets, plus how to rewrite it. */
interface CachedTicketList {
  kind: CachedTicketListKind;
  queryKey: QueryKey;
  tickets: TicketListResponse[];
  /** Container params this list is keyed by, used for move targeting. */
  params: Record<string, unknown>;
  /** The board the key names; undefined for a cross-board list. */
  boardId: string | undefined;
  cached: unknown;
  /** Rebuilds the envelope around a new ticket array. */
  withTickets: (next: TicketListResponse[]) => unknown;
}

/** The board segment of ["tickets","list",boardId,...] and ["backlog","detail",boardId]. */
function keyBoardId(queryKey: QueryKey): string | undefined {
  const boardId = keySegment(queryKey, 2);
  return typeof boardId === "string" ? boardId : undefined;
}

/**
 * Every cached ticket array of the given kinds: the ticket list queries, the
 * cross-board list queries, and the `tickets` array embedded in the backlog
 * detail query.
 */
function collectCachedTicketLists(
  queryClient: QueryClient,
  kinds: readonly CachedTicketListKind[]
): CachedTicketList[] {
  const lists: CachedTicketList[] = [];

  queryClient.getQueriesData({ queryKey: TICKET_KEYS.all }).forEach(([queryKey, cached]) => {
    if (!isRecord(cached) || !Array.isArray(cached.data)) return;
    const withTickets = (next: TicketListResponse[]): unknown => ({ ...cached, data: next });

    if (keySegment(queryKey, 1) === "list") {
      lists.push({
        kind: "container",
        queryKey,
        tickets: cached.data as TicketListResponse[],
        params: listKeyParams(queryKey),
        boardId: keyBoardId(queryKey),
        cached,
        withTickets,
      });
      return;
    }

    // ALL_TICKET_KEYS.list: ["tickets", "all-tickets", "list", params]. Its
    // rows are `AllTicketListResponse`, a `TicketListResponse` plus placement.
    if (keySegment(queryKey, 1) === "all-tickets" && keySegment(queryKey, 2) === "list") {
      lists.push({
        kind: "crossBoard",
        queryKey,
        tickets: cached.data as TicketListResponse[],
        params: {},
        boardId: undefined,
        cached,
        withTickets,
      });
    }
  });

  queryClient.getQueriesData({ queryKey: BACKLOG_KEYS.all }).forEach(([queryKey, cached]) => {
    if (!isRecord(cached) || !isRecord(cached.data)) return;
    const backlog = cached.data;
    if (!Array.isArray(backlog.tickets)) return;

    const backlogId = typeof backlog.id === "string" ? backlog.id : undefined;
    lists.push({
      kind: "container",
      queryKey,
      tickets: backlog.tickets as TicketListResponse[],
      params: backlogId ? { backlogId } : {},
      boardId: keyBoardId(queryKey),
      cached,
      withTickets: (next) => ({ ...cached, data: { ...backlog, tickets: next } }),
    });
  });

  return lists.filter((list) => kinds.includes(list.kind));
}

type TicketListTransform = (
  tickets: TicketListResponse[],
  context: { queryKey: QueryKey; params: Record<string, unknown>; boardId: string | undefined }
) => TicketListResponse[] | null;

/** Applies `transform` to every cached ticket array of `kinds`, recording what it rewrote. */
function transformCachedTicketLists(
  queryClient: QueryClient,
  writer: TicketCacheWriter,
  kinds: readonly CachedTicketListKind[],
  transform: TicketListTransform
): void {
  collectCachedTicketLists(queryClient, kinds).forEach((list) => {
    const next = transform(list.tickets, {
      queryKey: list.queryKey,
      params: list.params,
      boardId: list.boardId,
    });
    if (next) writer.write(list.queryKey, list.cached, list.withTickets(next));
  });
}

/**
 * Finds a cached copy of a ticket, used as the payload of a move. A top-level
 * copy wins over a nested one: it is the row the move inserts at the target.
 */
function findCachedTicket(
  lists: readonly CachedTicketList[],
  ticketId: string
): TicketListResponse | undefined {
  for (const list of lists) {
    const found = list.tickets.find((ticket) => ticket.id === ticketId);
    if (found) return found;
  }
  for (const list of lists) {
    const nested = findInTicketTree(list.tickets, ticketId);
    if (nested) return nested;
  }
  return undefined;
}

/**
 * Whether a ticket is already where a move targets, read from the
 * container of a cached list that holds it, else from its cached detail.
 * Undefined when no cache says where it is.
 */
function isCachedTicketAtTarget(
  queryClient: QueryClient,
  lists: readonly CachedTicketList[],
  ticketId: string,
  target: TicketMoveTarget
): boolean | undefined {
  for (const list of lists) {
    if (!list.params.sprintId && !list.params.backlogId) continue;
    if (list.tickets.some((ticket) => ticket.id === ticketId)) {
      return isMoveTarget(list.params, target);
    }
  }

  for (const [queryKey, cached] of queryClient.getQueriesData({ queryKey: TICKET_KEYS.all })) {
    if (keySegment(queryKey, 1) !== "detail") continue;
    if (!isRecord(cached) || !isRecord(cached.data) || cached.data.id !== ticketId) continue;
    const detail = cached.data as TicketDetailResponse;
    if (detail.sprint?.id) return detail.sprint.id === target.sprintId;
    if (detail.backlog?.id) return !target.sprintId && detail.backlog.id === target.backlogId;
  }
  return undefined;
}

/** A list is a move's target when its key names the container the move targets. */
function isMoveTarget(params: Record<string, unknown>, target: TicketMoveTarget): boolean {
  return (
    (!!target.sprintId && params.sprintId === target.sprintId) ||
    (!!target.backlogId && params.backlogId === target.backlogId)
  );
}

// ── Cancel / restore ────────────────────────────────────────────────

/** Stops in-flight fetches that would overwrite the optimistic update. */
export async function cancelTicketCaches(queryClient: QueryClient): Promise<void> {
  await Promise.all(CACHE_ROOTS.map((queryKey) => queryClient.cancelQueries({ queryKey })));
}

/**
 * Rolls an optimistic pass back.
 *
 * Only entries this pass rewrote are touched, and each is only reverted while
 * the cache still holds the exact value this pass wrote. If something newer got
 * there first -- an overlapping drag's patch, or a settle-time refetch -- the
 * restore is skipped rather than clobbering it with a stale value; that entry is
 * already newer than anything this snapshot knows about, and the settle
 * invalidation gives the server the last word either way.
 */
export function restoreTicketCaches(queryClient: QueryClient, snapshot: TicketCacheSnapshot): void {
  snapshot.forEach(({ queryKey, previous, applied }) => {
    if (queryClient.getQueryData(queryKey) !== applied) return;
    queryClient.setQueryData(queryKey, previous);
  });
}

// ── Optimistic operations ───────────────────────────────────────────

export function toTicketListPatch(data: TicketUpdateRequest): TicketListPatch {
  const patch: TicketListPatch = {};
  if (data.title !== undefined) patch.title = data.title;
  if (data.type !== undefined) patch.type = data.type;
  if (data.status !== undefined) patch.status = data.status;
  if (data.priority !== undefined) patch.priority = data.priority;
  if (data.missionRank !== undefined) patch.missionRank = data.missionRank;
  if (data.percentage !== undefined) patch.percentage = data.percentage;
  // The server sets a DONE ticket to 100 percent, whatever percentage the request sends.
  if (data.status === "DONE") patch.percentage = 100;
  if (data.weight !== undefined) patch.weight = data.weight;
  if (data.dueDate !== undefined) patch.dueDate = data.dueDate;
  return patch;
}

export function toTicketDetailPatch(data: TicketUpdateRequest): TicketDetailPatch {
  const patch: TicketDetailPatch = { ...toTicketListPatch(data) };
  if (data.description !== undefined) patch.description = data.description;
  if (data.rca !== undefined) patch.rca = data.rca;
  if (data.lessonsLearned !== undefined) patch.lessonsLearned = data.lessonsLearned;
  return patch;
}

/**
 * Shallow-merges the update payload into every cached copy of the ticket --
 * list rows (cross-board lists included), nested sub-ticket rows, the
 * backlog's embedded tickets, and the ticket detail entry. Returns the
 * rollback snapshot for what it rewrote.
 */
export function applyOptimisticTicketUpdate(
  queryClient: QueryClient,
  ticketId: string,
  data: TicketUpdateRequest
): TicketCacheSnapshot {
  const writer = createCacheWriter(queryClient);
  const listPatch = toTicketListPatch(data);
  const detailPatch = toTicketDetailPatch(data);

  if (Object.keys(listPatch).length > 0) {
    transformCachedTicketLists(queryClient, writer, ALL_LISTS, (tickets) =>
      patchTicketTree(tickets, ticketId, listPatch)
    );
  }

  queryClient.getQueriesData({ queryKey: TICKET_KEYS.all }).forEach(([queryKey, cached]) => {
    if (keySegment(queryKey, 1) !== "detail") return;
    if (!isRecord(cached) || !isRecord(cached.data)) return;
    const detail = cached.data;

    if (detail.id === ticketId) {
      writer.write(queryKey, cached, { ...cached, data: { ...detail, ...detailPatch } });
      return;
    }

    // The dragged card may be a sub-ticket of an open detail entry.
    if (!Array.isArray(detail.subTickets)) return;
    const nextSubTickets = patchTicketTree(
      detail.subTickets as TicketListResponse[],
      ticketId,
      listPatch
    );
    if (nextSubTickets) {
      writer.write(queryKey, cached, {
        ...cached,
        data: { ...detail, subTickets: nextSubTickets },
      });
    }
  });

  return writer.snapshot();
}

/**
 * Moves a ticket between containers the way the backend does. No move changes
 * a parent: a sub-ticket keeps its parent wherever it is planned, and a
 * parent's row keeps listing every sub-ticket wherever it is planned.
 *
 * What moves is the ticket plus the descendants `target.subTicketMode` picks:
 * with no mode (a drag) or `UNFINISHED`, every descendant that is not done and
 * is placed where the ticket was; with `NONE`, none; with `SELECTED`, the
 * listed descendants.
 *
 *   - Every moved ticket leaves the top level of each cached list that names
 *     another container. A copy nested under a parent stays there.
 *   - In each list that names the target container, the ticket becomes a
 *     top-level row at its `displayOrder` slot (`insertTicket`), carrying its
 *     sub-tickets, unless its parent is already in that list: the parent's
 *     row lists it already. That is the list API's rule for list roots, so
 *     rows there whose parent is a moved ticket are nested again.
 *   - In the list the ticket leaves, its sub-tickets that stay become rows
 *     of their own, carrying their parent (`collectLeftBehind`).
 *   - Every cached copy of a moved ticket, and every parent's placement
 *     summary of it, gets the target sprint (`sprint`), so progress bars and
 *     badges show the new placement before the refetch. When no cache holds
 *     the target sprint's name, `sprint` is left for the refetch.
 *
 * Cross-board lists and lists that name no container are left alone: a move
 * does not change what they show.
 *
 * With `target.order`, the server first gives the ticket that order on
 * `boardId`, its own board, and shifts the board's tickets in between. The
 * same shift runs here before the move, so the moved row carries its new
 * order and `insertTicket` puts it between the neighbours it was dropped
 * between. Both steps go through one writer: a failed request restores the
 * placement and every shifted order with one `restoreTicketCaches`.
 */
export function applyOptimisticTicketMove(
  queryClient: QueryClient,
  ticketId: string,
  target: TicketMoveTarget,
  boardId: string
): TicketCacheSnapshot {
  const writer = createCacheWriter(queryClient);
  if (!target.sprintId && !target.backlogId) return writer.snapshot();

  if (target.order !== undefined) {
    const oldOrder = findCachedDisplayOrder(
      queryClient,
      collectCachedTicketLists(queryClient, CONTAINER_LISTS),
      ticketId
    );
    if (oldOrder !== undefined && oldOrder !== target.order) {
      shiftCachedDisplayOrders(queryClient, writer, boardId, ticketId, oldOrder, target.order);
    }
  }

  // Collected after the shift, so the moved copy carries its new order.
  const lists = collectCachedTicketLists(queryClient, CONTAINER_LISTS);
  const moved = findCachedTicket(lists, ticketId);
  // Nothing cached to move; the settle-time refetch covers it.
  if (!moved) return writer.snapshot();
  const alreadyInTargetSprint = !!target.sprintId && moved.sprint?.id === target.sprintId;
  if (
    alreadyInTargetSprint ||
    isCachedTicketAtTarget(queryClient, lists, ticketId, target) === true
  ) {
    return writer.snapshot();
  }

  const movedIds = collectMovedIds(moved, target);
  const targetSprint = target.sprintId ? findCachedSprint(queryClient, target.sprintId) : null;
  const placeMoved = (tickets: TicketListResponse[]): TicketListResponse[] | null =>
    targetSprint === undefined ? null : patchMovedPlacement(tickets, movedIds, targetSprint);
  const [movedRow] = placeMoved([moved]) ?? [moved];
  if (!movedRow) return writer.snapshot();
  const sourceSprintId = moved.sprint?.id ?? null;
  const leftBehind = collectLeftBehind(movedRow, movedIds, sourceSprintId);

  transformCachedTicketLists(queryClient, writer, CONTAINER_LISTS, (tickets, { params }) => {
    if (!params.sprintId && !params.backlogId) return null;

    const isTarget = isMoveTarget(params, target);
    const isSource =
      !isTarget &&
      (params.sprintId ? params.sprintId === sourceSprintId : sourceSprintId === null) &&
      !!findInTicketTree(tickets, ticketId);
    // At the target the moved row is inserted whole, so a stale top-level
    // copy of any moved ticket goes first.
    const removed = removeTickets(tickets, movedIds);
    const remaining = removed ?? tickets;
    const placed = placeMoved(remaining);
    let next = placed ?? remaining;
    let changed = !!removed || !!placed;

    if (isSource) {
      leftBehind.forEach((row) => {
        const inserted = insertTicket(next, row);
        if (inserted) {
          next = inserted;
          changed = true;
        }
      });
    }
    if (!isTarget) return changed ? next : null;

    // A sub-ticket listed on its own because its parent was elsewhere is
    // nested again once that parent arrives.
    const regrouped = next.filter(
      (ticket) => !ticket.parentTicketId || !movedIds.has(ticket.parentTicketId)
    );
    if (regrouped.length !== next.length) {
      next = regrouped;
      changed = true;
    }

    const parentId = movedRow.parentTicketId;
    if (parentId && findInTicketTree(next, parentId)) return changed ? next : null;
    return insertTicket(next, movedRow) ?? (changed ? next : null);
  });

  return writer.snapshot();
}

/**
 * The sub-tickets that stay in the source list while their parent leaves it.
 * They become rows of their own there, carrying their parent, since the list
 * holds only the tickets whose parent is not in it. A sub-ticket that stays
 * keeps its own sub-tickets nested, so the walk stops at it.
 */
function collectLeftBehind(
  movedRow: TicketListResponse,
  movedIds: ReadonlySet<string>,
  sourceSprintId: string | null
): TicketListResponse[] {
  const rows: TicketListResponse[] = [];
  const visit = (parent: TicketListResponse): void => {
    parent.subTickets?.forEach((subTicket) => {
      if (movedIds.has(subTicket.id)) {
        visit(subTicket);
        return;
      }
      if ((subTicket.sprint?.id ?? null) !== sourceSprintId) return;
      rows.push({ ...subTicket, parentTicket: toParentReference(parent) });
    });
  };
  visit(movedRow);
  return rows;
}

/** A ticket as its sub-tickets carry it, with each sub-ticket's placement. */
function toParentReference(parent: TicketListResponse): TicketParentResponse {
  return {
    id: parent.id,
    key: parent.key,
    title: parent.title,
    status: parent.status,
    type: parent.type,
    subTickets: (parent.subTickets ?? []).map((subTicket) => ({
      id: subTicket.id,
      status: subTicket.status,
      sprint: subTicket.sprint ?? null,
    })),
  };
}

/**
 * The ids a move takes: the ticket and the descendants the mode picks. Each
 * descendant is compared with the ticket's own placement before the move;
 * the backend compares with the nearest moved ancestor, whose placement is
 * the same one.
 */
function collectMovedIds(ticket: TicketListResponse, target: TicketMoveTarget): Set<string> {
  const ids = new Set<string>([ticket.id]);
  if (target.subTicketMode === "NONE") return ids;

  const selected = new Set(target.subTicketIds ?? []);
  const sourceSprintId = ticket.sprint?.id ?? null;
  const visit = (subTickets: TicketListResponse[] | null | undefined): void => {
    subTickets?.forEach((subTicket) => {
      const picked =
        target.subTicketMode === "SELECTED"
          ? selected.has(subTicket.id)
          : subTicket.status !== "DONE" && (subTicket.sprint?.id ?? null) === sourceSprintId;
      if (picked) ids.add(subTicket.id);
      visit(subTicket.subTickets);
    });
  };
  visit(ticket.subTickets);
  return ids;
}

/**
 * Sets `sprint` to `sprint` (null for the backlog) on every copy of a moved
 * ticket at any depth, and on the moved entries of each parent's placement
 * summary (`parentTicket.subTickets`).
 */
function patchMovedPlacement(
  tickets: TicketListResponse[],
  movedIds: ReadonlySet<string>,
  sprint: TicketSprintResponse | null
): TicketListResponse[] | null {
  let changed = false;

  const next = tickets.map((ticket) => {
    let result = ticket;
    if (movedIds.has(ticket.id)) result = { ...result, sprint };

    const summary = ticket.parentTicket?.subTickets;
    if (ticket.parentTicket && summary?.some((entry) => movedIds.has(entry.id))) {
      result = {
        ...result,
        parentTicket: {
          ...ticket.parentTicket,
          subTickets: summary.map((entry) =>
            movedIds.has(entry.id) ? { ...entry, sprint } : entry
          ),
        },
      };
    }

    const subTickets = ticket.subTickets
      ? patchMovedPlacement(ticket.subTickets, movedIds, sprint)
      : null;
    if (subTickets) result = { ...result, subTickets };

    if (result !== ticket) changed = true;
    return result;
  });

  return changed ? next : null;
}

/**
 * The target sprint as a ticket carries it, from a cached sprint list, else
 * from a cached ticket already planned in it. Undefined when no cache knows
 * its name.
 */
function findCachedSprint(
  queryClient: QueryClient,
  sprintId: string
): TicketSprintResponse | undefined {
  for (const [, cached] of queryClient.getQueriesData({ queryKey: SPRINT_KEYS.all })) {
    if (!isRecord(cached) || !Array.isArray(cached.data)) continue;
    const sprint = (cached.data as unknown[]).find(
      (entry): entry is TicketSprintResponse => isRecord(entry) && entry.id === sprintId
    );
    if (sprint) return { id: sprint.id, name: sprint.name, status: sprint.status };
  }

  const lists = collectCachedTicketLists(queryClient, ALL_LISTS);
  const visit = (tickets: TicketListResponse[]): TicketSprintResponse | undefined => {
    for (const ticket of tickets) {
      if (ticket.sprint?.id === sprintId) return ticket.sprint;
      const nested = ticket.subTickets ? visit(ticket.subTickets) : undefined;
      if (nested) return nested;
    }
    return undefined;
  };
  for (const list of lists) {
    const found = visit(list.tickets);
    if (found) return found;
  }
  return undefined;
}

/**
 * The `displayOrder` a ticket holds now, read from the first cached copy: a
 * container list row at any depth, else its own detail entry or a detail's
 * `subTickets`. Undefined when no cache holds it.
 */
function findCachedDisplayOrder(
  queryClient: QueryClient,
  lists: readonly CachedTicketList[],
  ticketId: string
): number | undefined {
  for (const list of lists) {
    const found = findInTicketTree(list.tickets, ticketId);
    if (found) return found.displayOrder;
  }

  for (const [queryKey, cached] of queryClient.getQueriesData({ queryKey: TICKET_KEYS.all })) {
    if (keySegment(queryKey, 1) !== "detail") continue;
    if (!isRecord(cached) || !isRecord(cached.data)) continue;
    const detail = cached.data as TicketDetailResponse;
    if (detail.id === ticketId) return detail.displayOrder;
    const nested = detail.subTickets ? findInTicketTree(detail.subTickets, ticketId) : undefined;
    if (nested) return nested.displayOrder;
  }
  return undefined;
}

/**
 * Reorders a ticket within every cached array that contains it: the top-level
 * rows of a container list, the `subTickets` of its parent row in a list, and
 * the `subTickets` of its parent's cached detail entry (what the ticket
 * detail's sub-ticket list renders from). Cross-board lists are left alone:
 * the cross-board kanban does not render by `displayOrder`.
 *
 * Every one of those arrays then gets the server's `displayOrder` shift for
 * `boardId`, the dragged ticket's board (`shiftDisplayOrders`), so each cached
 * row of the board holds the order the server will store. The shift needs the
 * ticket's current order and `target.order`; when either is unknown only the
 * array order changes, and the settle-time refetch brings the values.
 */
export function applyOptimisticTicketReorder(
  queryClient: QueryClient,
  ticketId: string,
  target: TicketReorderTarget,
  boardId: string
): TicketCacheSnapshot {
  const writer = createCacheWriter(queryClient);

  const oldOrder = findCachedDisplayOrder(
    queryClient,
    collectCachedTicketLists(queryClient, CONTAINER_LISTS),
    ticketId
  );

  transformCachedTicketLists(
    queryClient,
    writer,
    CONTAINER_LISTS,
    (tickets) =>
      reorderTickets(tickets, ticketId, target) ?? reorderSubTickets(tickets, ticketId, target)
  );

  transformCachedDetailSubTickets(queryClient, writer, (subTickets) =>
    reorderTickets(subTickets, ticketId, target)
  );

  if (oldOrder !== undefined && target.order !== undefined) {
    shiftCachedDisplayOrders(queryClient, writer, boardId, ticketId, oldOrder, target.order);
  }

  return writer.snapshot();
}

/**
 * Applies the server's `displayOrder` shift for one ticket of `boardId`
 * (`shiftDisplayOrders`) to every container list and every detail entry's
 * `subTickets`, through `writer`, so it joins the caller's rollback snapshot.
 */
function shiftCachedDisplayOrders(
  queryClient: QueryClient,
  writer: TicketCacheWriter,
  boardId: string,
  ticketId: string,
  oldOrder: number,
  newOrder: number
): void {
  transformCachedTicketLists(queryClient, writer, CONTAINER_LISTS, (tickets, context) =>
    shiftDisplayOrders(tickets, boardId, ticketId, oldOrder, newOrder, context.boardId)
  );

  transformCachedDetailSubTickets(queryClient, writer, (subTickets, detailBoardId) =>
    shiftDisplayOrders(subTickets, boardId, ticketId, oldOrder, newOrder, detailBoardId)
  );
}

/**
 * Applies `transform` to the `subTickets` of every cached ticket detail entry,
 * recording what it rewrote. The transform also gets the board the detail key
 * names.
 */
function transformCachedDetailSubTickets(
  queryClient: QueryClient,
  writer: TicketCacheWriter,
  transform: (
    subTickets: TicketListResponse[],
    detailBoardId: string | undefined
  ) => TicketListResponse[] | null
): void {
  queryClient.getQueriesData({ queryKey: TICKET_KEYS.all }).forEach(([queryKey, cached]) => {
    if (keySegment(queryKey, 1) !== "detail") return;
    if (!isRecord(cached) || !isRecord(cached.data)) return;
    const detail = cached.data;
    if (!Array.isArray(detail.subTickets)) return;

    const nextSubTickets = transform(
      detail.subTickets as TicketListResponse[],
      keyBoardId(queryKey)
    );
    if (nextSubTickets) {
      writer.write(queryKey, cached, {
        ...cached,
        data: { ...detail, subTickets: nextSubTickets },
      });
    }
  });
}

// ── Settle ──────────────────────────────────────────────────────────

/**
 * Resyncs with the server once the last in-flight ticket mutation settles.
 *
 * Invalidating while a sibling ticket mutation is still running would refetch
 * a server state that does not yet contain that sibling's change, wiping its
 * optimistic patch and flashing the card back to its old column. Counting only
 * mutations under `TICKET_MUTATION_KEY` keeps unrelated mutations (comments,
 * sprints, ...) from suppressing the resync entirely -- which is why every
 * ticket mutation carries that prefix, including the non-optimistic ones.
 *
 * Callers must not return the promise from `onSettled`/`onSuccess`: doing so
 * keeps the mutation `pending` for the whole refetch, which both blocks the
 * next drag and holds the gate open against the mutations that follow.
 */
export function settleTicketMutation(
  queryClient: QueryClient,
  options?: { excludeKeys?: readonly QueryKey[] }
): Promise<void> | undefined {
  if (queryClient.isMutating({ mutationKey: TICKET_MUTATION_KEY }) > 1) return undefined;
  return invalidateRelatedQueries(queryClient, options);
}

/**
 * Resyncs with the server once at the end of an action that ran its ticket
 * mutations as one batch (see `TicketBatchOption`).
 *
 * It runs after the action's last request has settled, so none of the
 * action's own mutations is counted any more: any ticket mutation still in
 * flight belongs to something else, such as a drag, whose own settle resyncs.
 */
export function settleTicketAction(
  queryClient: QueryClient,
  options?: { excludeKeys?: readonly QueryKey[] }
): Promise<void> | undefined {
  if (queryClient.isMutating({ mutationKey: TICKET_MUTATION_KEY }) > 0) return undefined;
  return invalidateRelatedQueries(queryClient, options);
}
