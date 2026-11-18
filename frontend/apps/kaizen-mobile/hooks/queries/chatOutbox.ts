import { useMemo, useSyncExternalStore } from "react";

import type { ChatOutboxMessage } from "@/lib/schemas/chat";

/*
 * Messages this device sent that the server has not confirmed yet. A module
 * store, not the query cache: a refetch of a channel must never drop a row,
 * and a row outlives the screen it was written on, so leaving a channel
 * while a send runs loses nothing.
 */

let outboxRows: readonly ChatOutboxMessage[] = [];
const outboxListeners: Set<() => void> = new Set<() => void>();

function setOutboxRows(rows: readonly ChatOutboxMessage[]): void {
  outboxRows = rows;
  for (const listener of outboxListeners) listener();
}

export function getChatOutboxMessage(clientNonce: string): ChatOutboxMessage | undefined {
  return outboxRows.find((row) => row.clientNonce === clientNonce);
}

export function getChatOutboxMessages(): readonly ChatOutboxMessage[] {
  return outboxRows;
}

/** Adds the row, or replaces the row with the same nonce in place. */
export function putChatOutboxMessage(row: ChatOutboxMessage): void {
  const index: number = outboxRows.findIndex(
    (existing) => existing.clientNonce === row.clientNonce
  );
  if (index < 0) {
    setOutboxRows([...outboxRows, row]);
    return;
  }
  const next: ChatOutboxMessage[] = [...outboxRows];
  next[index] = row;
  setOutboxRows(next);
}

export function removeChatOutboxMessage(clientNonce: string): void {
  if (!outboxRows.some((row) => row.clientNonce === clientNonce)) return;
  setOutboxRows(outboxRows.filter((row) => row.clientNonce !== clientNonce));
}

/**
 * Forgets every unconfirmed message, at sign-out or when the session ends:
 * the rows are the signed-out user's, and the next user must neither see
 * them nor send them under their own name.
 */
export function clearChatOutbox(): void {
  if (outboxRows.length === 0) return;
  setOutboxRows([]);
}

/**
 * Rows of the organization that failed without any response; they are sent
 * again by themselves. Rows of another organization wait until it is the
 * active one again, so they are never sent while another one is on screen.
 */
export function chatOutboxRowsToResend(organizationId: string): ChatOutboxMessage[] {
  return outboxRows.filter(
    (row) => row.organizationId === organizationId && row.status === "failed" && row.failedByNetwork
  );
}

function subscribeOutbox(listener: () => void): () => void {
  outboxListeners.add(listener);
  return () => {
    outboxListeners.delete(listener);
  };
}

const readOutbox = (): readonly ChatOutboxMessage[] => outboxRows;

/** The unconfirmed messages of one conversation, oldest first. */
export function useChatOutbox(
  orgId: string | null,
  channelId: string | null,
  threadId: string | null
): ChatOutboxMessage[] {
  const rows: readonly ChatOutboxMessage[] = useSyncExternalStore(
    subscribeOutbox,
    readOutbox,
    readOutbox
  );
  return useMemo(
    () =>
      rows.filter(
        (row) =>
          row.organizationId === orgId && row.channelId === channelId && row.threadId === threadId
      ),
    [rows, orgId, channelId, threadId]
  );
}

/**
 * Starts `send` for every row of the organization to resend, at most one
 * round at a time, so a burst of reconnect events never sends a row twice in
 * parallel.
 */
export function createChatOutboxResender(
  send: (row: ChatOutboxMessage) => Promise<unknown>
): (organizationId: string) => void {
  let running = false;
  return (organizationId: string): void => {
    if (running) return;
    const rows: ChatOutboxMessage[] = chatOutboxRowsToResend(organizationId);
    if (rows.length === 0) return;
    running = true;
    // Each send reports its own failure on its row; settling only ends the round.
    void Promise.allSettled(rows.map((row) => send(row))).then(() => {
      running = false;
    });
  };
}
