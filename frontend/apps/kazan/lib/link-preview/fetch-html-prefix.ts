import http from "node:http";
import type { IncomingHttpHeaders } from "node:http";
import https from "node:https";
import type { Readable } from "node:stream";

import { createGuardedLookup } from "./guarded-lookup";
import { LinkPreviewBlockedError, parsePreviewTarget } from "./target-policy";

/** The part of a response the preview reads: status, headers and the body stream. */
export type PreviewResponse = Readable & {
  statusCode?: number;
  headers: IncomingHttpHeaders;
};

/** Sends one GET without following redirects. */
export type RequestOnce = (url: URL, signal: AbortSignal) => Promise<PreviewResponse>;

export interface FetchHtmlPrefixOptions {
  /** Upper bound on the body bytes read; a title sits near the top of the page. */
  maxBytes?: number;
  /** Deadline for the whole chain of requests, redirects included. */
  timeoutMs?: number;
  maxRedirects?: number;
  requestOnce?: RequestOnce;
}

const REDIRECT_STATUSES = new Set([301, 302, 303, 307, 308]);

const REQUEST_HEADERS = {
  "User-Agent": "Mozilla/5.0 (compatible; ObaLinkBot/1.0)",
  Accept: "text/html",
};

const guardedLookup = createGuardedLookup();

/**
 * Sends the request through the guarded lookup, so the connection can only reach a public
 * address. No shared agent is used: a pooled socket would skip the lookup.
 */
const requestWithGuardedLookup: RequestOnce = (url, signal) => {
  const client = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const request = client.request(
      url,
      { method: "GET", headers: REQUEST_HEADERS, lookup: guardedLookup, signal, agent: false },
      resolve
    );
    request.on("error", reject);
    request.end();
  });
};

async function readPrefix(response: PreviewResponse, maxBytes: number): Promise<string> {
  const chunks: Buffer[] = [];
  let total = 0;
  try {
    for await (const chunk of response) {
      const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk as Uint8Array);
      const kept = buffer.subarray(0, maxBytes - total);
      chunks.push(kept);
      total += kept.length;
      if (total >= maxBytes) break;
      if (Buffer.concat(chunks).toString("utf8").toLowerCase().includes("</title>")) break;
    }
  } finally {
    response.destroy();
  }
  return Buffer.concat(chunks).toString("utf8");
}

/**
 * Fetches the start of an HTML page for a link preview. The target must already have passed
 * parsePreviewTarget. Every redirect hop is checked again before it is followed, and each
 * connection resolves its host through the guarded lookup. Returns null for a non-success
 * status or a non-HTML body; throws LinkPreviewBlockedError for a refused hop.
 */
export async function fetchHtmlPrefix(
  target: URL,
  options: FetchHtmlPrefixOptions = {}
): Promise<string | null> {
  const {
    maxBytes = 16384,
    timeoutMs = 5000,
    maxRedirects = 3,
    requestOnce = requestWithGuardedLookup,
  } = options;
  const signal = AbortSignal.timeout(timeoutMs);

  let current = target;
  for (let hop = 0; hop <= maxRedirects; hop++) {
    const response = await requestOnce(current, signal);
    const status = response.statusCode ?? 0;

    if (REDIRECT_STATUSES.has(status)) {
      const location = response.headers.location;
      response.destroy();
      if (!location) return null;
      const next = parsePreviewTarget(new URL(location, current).toString());
      if (!next) throw new LinkPreviewBlockedError("Redirect to a refused target");
      current = next;
      continue;
    }

    const contentType = response.headers["content-type"];
    if (
      status < 200 ||
      status >= 300 ||
      (contentType !== undefined && !contentType.toLowerCase().includes("html"))
    ) {
      response.destroy();
      return null;
    }

    return readPrefix(response, maxBytes);
  }
  throw new LinkPreviewBlockedError("Too many redirects");
}
