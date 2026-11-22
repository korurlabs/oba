import { afterEach, beforeEach, describe, expect, spyOn, test, type Mock } from "bun:test";

import { realtimeBrokerUrl } from "./stomp-client";

/** Upper bound for one adversarial parse; a linear parse takes well under 1 ms. */
const ADVERSARIAL_BUDGET_MS = 100;
const ADVERSARIAL_LENGTH = 100_000;

let consoleError: Mock<typeof console.error>;

beforeEach(() => {
  consoleError = spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  consoleError.mockRestore();
});

function timed<T>(run: () => T): { result: T; elapsedMs: number } {
  const started: number = performance.now();
  const result: T = run();
  return { result, elapsedMs: performance.now() - started };
}

describe("realtimeBrokerUrl", () => {
  test.each([
    ["https://api.example.test", "wss://api.example.test/ws"],
    ["http://localhost:8080", "ws://localhost:8080/ws"],
    ["https://api.example.test/", "wss://api.example.test/ws"],
    ["https://api.example.test/api///", "wss://api.example.test/api/ws"],
    ["  https://api.example.test/api/v1  ", "wss://api.example.test/api/v1/ws"],
    ["HTTPS://Api.Example.Test/Base", "wss://Api.Example.Test/Base/ws"],
    ["https://user@api.example.test:8443/a//b", "wss://user@api.example.test:8443/a//b/ws"],
  ])("turns %s into %s", (base, expected) => {
    expect(realtimeBrokerUrl(base)).toBe(expected);
    expect(consoleError).not.toHaveBeenCalled();
  });

  test.each([
    [undefined, "is not set"],
    ["", "is not set"],
    ["/api", "is not an absolute URL"],
    ["api.example.test", "is not an absolute URL"],
    ["https://", "is not an absolute URL"],
    ["https:///api", "is not an absolute URL"],
    ["https://?q", "is not an absolute URL"],
    ["1http://api.example.test", "is not an absolute URL"],
    ["ht:tp://api.example.test", "is not an absolute URL"],
    ["://api.example.test", "is not an absolute URL"],
    ["https://api.example .test", "is not an absolute URL"],
    ["https://api.example.test/a\nb", "is not an absolute URL"],
    ["ftp://api.example.test", "has an unsupported protocol: ftp:"],
    ["wss://api.example.test", "has an unsupported protocol: wss:"],
    ["https://api.example.test/api?x=1", "must not carry a query or fragment"],
    ["https://api.example.test#top", "must not carry a query or fragment"],
  ])("refuses %p because it %s", (base, reason) => {
    expect(realtimeBrokerUrl(base)).toBeNull();
    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(String(consoleError.mock.calls[0]?.[0])).toContain(reason);
  });

  test("finishes quickly on a long run of slashes that is not at the end", () => {
    const base: string = `${"/".repeat(ADVERSARIAL_LENGTH)}x`;
    const { result, elapsedMs }: { result: string | null; elapsedMs: number } = timed(() =>
      realtimeBrokerUrl(base)
    );
    expect(result).toBeNull();
    expect(elapsedMs).toBeLessThan(ADVERSARIAL_BUDGET_MS);
  });

  test("finishes quickly on a scheme followed by many quotes and no valid end", () => {
    const base: string = `a://"${'""'.repeat(ADVERSARIAL_LENGTH)}`;
    const { result, elapsedMs }: { result: string | null; elapsedMs: number } = timed(() =>
      realtimeBrokerUrl(base)
    );
    expect(result).toBeNull();
    expect(elapsedMs).toBeLessThan(ADVERSARIAL_BUDGET_MS);
  });

  test("finishes quickly on a long valid URL", () => {
    const path: string = "/a".repeat(ADVERSARIAL_LENGTH);
    const { result, elapsedMs }: { result: string | null; elapsedMs: number } = timed(() =>
      realtimeBrokerUrl(`https://api.example.test${path}${"/".repeat(ADVERSARIAL_LENGTH)}`)
    );
    expect(result).toBe(`wss://api.example.test${path}/ws`);
    expect(elapsedMs).toBeLessThan(ADVERSARIAL_BUDGET_MS);
  });
});
