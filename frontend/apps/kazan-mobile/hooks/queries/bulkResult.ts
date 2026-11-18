/** One ticket a bulk write acts on: the board path needs both ids. */
export interface TicketTarget {
  boardId: string;
  ticketId: string;
}

/** The outcome of a write fanned out over several tickets, as there is no bulk endpoint. */
export interface BulkResult<T> {
  succeeded: T[];
  failed: { target: T; message: string }[];
}

/** Splits `Promise.allSettled` outcomes by their targets, keeping each failure's message. */
export function partitionSettled<T>(
  targets: readonly T[],
  outcomes: readonly PromiseSettledResult<unknown>[]
): BulkResult<T> {
  const result: BulkResult<T> = { succeeded: [], failed: [] };
  outcomes.forEach((outcome, index) => {
    const target: T | undefined = targets[index];
    if (target === undefined) return;
    if (outcome.status === "fulfilled") {
      result.succeeded.push(target);
      return;
    }
    const reason: unknown = outcome.reason;
    result.failed.push({
      target,
      message: reason instanceof Error ? reason.message : String(reason),
    });
  });
  return result;
}
