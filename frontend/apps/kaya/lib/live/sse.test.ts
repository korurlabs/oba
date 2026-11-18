import { describe, expect, test } from "bun:test";

import { createSseParser, parseSessionUpdated } from "./sse";

describe("createSseParser", () => {
  test("reads named events and ignores comments", () => {
    const parse = createSseParser();
    const events = parse(
      ': hello\nevent:heartbeat\ndata:{"at":"t"}\n\nevent: session-updated\ndata: {"sessionId":"s1"}\n\n'
    );
    expect(events).toEqual([
      { event: "heartbeat", data: '{"at":"t"}' },
      { event: "session-updated", data: '{"sessionId":"s1"}' },
    ]);
  });

  test("keeps an event split across chunks until it ends", () => {
    const parse = createSseParser();
    expect(parse("event:session-upd")).toEqual([]);
    expect(parse('ated\r\ndata:{"sessionId":')).toEqual([]);
    expect(parse('"s2"}\r\n\r\n')).toEqual([
      { event: "session-updated", data: '{"sessionId":"s2"}' },
    ]);
  });

  test("names an event without a name message and joins its data lines", () => {
    const parse = createSseParser();
    expect(parse("data:a\ndata:b\n\n")).toEqual([{ event: "message", data: "a\nb" }]);
  });
});

describe("parseSessionUpdated", () => {
  test("reads the session and its last event", () => {
    expect(parseSessionUpdated('{"sessionId":"s","lastEventAt":"2026-10-23T10:00:00Z"}')).toEqual({
      sessionId: "s",
      lastEventAt: "2026-10-23T10:00:00Z",
    });
  });

  test("a deleted session has no last event", () => {
    expect(parseSessionUpdated('{"sessionId":"s","lastEventAt":null}')?.lastEventAt).toBeNull();
  });

  test("reads the data of a session-updated event from the stream parser", () => {
    const [event] = createSseParser()(
      'event: session-updated\ndata: {"sessionId":"s9","lastEventAt":"2026-10-23T10:00:00Z"}\n\n'
    );
    expect(event?.event).toBe("session-updated");
    expect(parseSessionUpdated(event?.data ?? "")?.sessionId).toBe("s9");
  });

  test("refuses data of another shape", () => {
    expect(parseSessionUpdated("not json")).toBeNull();
    expect(parseSessionUpdated('{"lastEventAt":"x"}')).toBeNull();
  });
});
