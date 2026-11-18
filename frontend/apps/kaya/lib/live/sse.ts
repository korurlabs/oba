/** One server-sent event: its name (`message` when the server gave none) and its data lines joined. */
export type SseEvent = {
  event: string;
  data: string;
};

/**
 * Splits a `text/event-stream` body into events as its chunks arrive. A chunk
 * may end anywhere, even inside a line, so the unfinished rest is kept for the
 * next one. Comments and the `id` and `retry` fields are ignored, since Kaya's
 * stream sends neither and a reconnect reloads the lists anyway.
 */
export function createSseParser(): (chunk: string) => SseEvent[] {
  let buffer = "";
  let event = "";
  let data: string[] = [];

  return (chunk: string): SseEvent[] => {
    buffer += chunk;
    const lines = buffer.split(/\r\n|\r|\n/);
    // The last piece has no line end yet; it waits for the next chunk.
    buffer = lines.pop() ?? "";
    const out: SseEvent[] = [];
    for (const line of lines) {
      if (line === "") {
        if (data.length > 0) {
          out.push({ event: event || "message", data: data.join("\n") });
        }
        event = "";
        data = [];
        continue;
      }
      if (line.startsWith(":")) continue;
      const colon = line.indexOf(":");
      const field = colon === -1 ? line : line.slice(0, colon);
      let value = colon === -1 ? "" : line.slice(colon + 1);
      if (value.startsWith(" ")) value = value.slice(1);
      if (field === "event") event = value;
      else if (field === "data") data.push(value);
    }
    return out;
  };
}

/** The data of a `session-updated` event; `lastEventAt` is null when the session was deleted. */
export type SessionUpdated = {
  sessionId: string;
  lastEventAt: string | null;
};

/** The session a `session-updated` event names, or null when its data is not the expected shape. */
export function parseSessionUpdated(data: string): SessionUpdated | null {
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch (error: unknown) {
    console.warn("[stream] session-updated data is not JSON", error);
    return null;
  }
  if (typeof value !== "object" || value === null) return null;
  const record = value as Record<string, unknown>;
  if (typeof record.sessionId !== "string" || record.sessionId === "") return null;
  return {
    sessionId: record.sessionId,
    lastEventAt: typeof record.lastEventAt === "string" ? record.lastEventAt : null,
  };
}
