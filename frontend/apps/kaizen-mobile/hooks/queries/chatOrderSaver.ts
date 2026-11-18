/** A channel order waiting to be saved for one organization. */
export interface PendingChatOrder {
  organizationId: string;
  order: string[];
}

export interface ChatOrderSaver {
  /** Replaces the pending order; it is saved once no other move follows within the delay. */
  schedule: (pending: PendingChatOrder) => void;
  /** Saves the pending order now, if there is one; the switcher calls it when it closes. */
  flush: () => void;
}

/**
 * Saves a burst of channel moves once, after the last. Closing the switcher
 * before the delay is over saves at once instead of dropping the move, which
 * would put the old order back on the next visit without a word.
 */
export function createChatOrderSaver(
  save: (pending: PendingChatOrder) => void,
  delayMs: number
): ChatOrderSaver {
  let pending: PendingChatOrder | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;

  const flush = (): void => {
    clearTimeout(timer);
    timer = undefined;
    const due: PendingChatOrder | null = pending;
    pending = null;
    if (due) save(due);
  };

  const schedule = (next: PendingChatOrder): void => {
    pending = next;
    clearTimeout(timer);
    timer = setTimeout(flush, delayMs);
  };

  return { schedule, flush };
}
