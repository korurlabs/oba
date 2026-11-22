import { describe, expect, test } from "bun:test";
import type { IncomingHttpHeaders } from "node:http";
import { Readable } from "node:stream";

import { fetchHtmlPrefix } from "./fetch-html-prefix";
import type { PreviewResponse, RequestOnce } from "./fetch-html-prefix";
import { LinkPreviewBlockedError } from "./target-policy";

function response(
  statusCode: number,
  headers: IncomingHttpHeaders,
  body: string[] = []
): PreviewResponse {
  return Object.assign(Readable.from(body.map((part) => Buffer.from(part))), {
    statusCode,
    headers,
  });
}

function recordingRequests(responses: PreviewResponse[]): {
  requested: string[];
  requestOnce: RequestOnce;
} {
  const requested: string[] = [];
  const requestOnce: RequestOnce = async (url) => {
    requested.push(url.toString());
    const next = responses.shift();
    if (!next) throw new Error("unexpected request");
    return next;
  };
  return { requested, requestOnce };
}

const START = new URL("https://example.com/start");

describe("fetchHtmlPrefix", () => {
  test("refuses a redirect to the cloud metadata address without requesting it", async () => {
    const { requested, requestOnce } = recordingRequests([
      response(302, { location: "http://169.254.169.254/latest/meta-data/" }),
    ]);
    await expect(fetchHtmlPrefix(START, { requestOnce })).rejects.toBeInstanceOf(
      LinkPreviewBlockedError
    );
    expect(requested).toEqual([START.toString()]);
  });

  test("refuses a relative redirect onto a non-default port", async () => {
    const { requested, requestOnce } = recordingRequests([
      response(301, { location: "//example.com:6443/" }),
    ]);
    await expect(fetchHtmlPrefix(START, { requestOnce })).rejects.toBeInstanceOf(
      LinkPreviewBlockedError
    );
    expect(requested).toHaveLength(1);
  });

  test("refuses a redirect to a cluster service name", async () => {
    const { requestOnce } = recordingRequests([response(307, { location: "http://kazan-api/" })]);
    await expect(fetchHtmlPrefix(START, { requestOnce })).rejects.toBeInstanceOf(
      LinkPreviewBlockedError
    );
  });

  test("follows a redirect to another public page", async () => {
    const { requested, requestOnce } = recordingRequests([
      response(301, { location: "/moved" }),
      response(200, { "content-type": "text/html" }, ["<title>Moved</title>"]),
    ]);
    expect(await fetchHtmlPrefix(START, { requestOnce })).toBe("<title>Moved</title>");
    expect(requested).toEqual([START.toString(), "https://example.com/moved"]);
  });

  test("stops after the redirect limit", async () => {
    const { requestOnce } = recordingRequests([
      response(302, { location: "/a" }),
      response(302, { location: "/b" }),
    ]);
    await expect(fetchHtmlPrefix(START, { requestOnce, maxRedirects: 1 })).rejects.toBeInstanceOf(
      LinkPreviewBlockedError
    );
  });

  test("reads no more than the byte limit", async () => {
    const { requestOnce } = recordingRequests([
      response(200, { "content-type": "text/html" }, ["a".repeat(10), "b".repeat(10)]),
    ]);
    expect(await fetchHtmlPrefix(START, { requestOnce, maxBytes: 15 })).toBe(
      "a".repeat(10) + "b".repeat(5)
    );
  });

  test("returns null for a non-HTML body", async () => {
    const { requestOnce } = recordingRequests([
      response(200, { "content-type": "application/json" }, ["{}"]),
    ]);
    expect(await fetchHtmlPrefix(START, { requestOnce })).toBeNull();
  });

  test("returns null for an error status", async () => {
    const { requestOnce } = recordingRequests([response(404, { "content-type": "text/html" })]);
    expect(await fetchHtmlPrefix(START, { requestOnce })).toBeNull();
  });
});
