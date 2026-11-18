"use client";

import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { getSession } from "next-auth/react";
import { useEffect, useState } from "react";

import { getRuntimeConfig } from "@oba/shared/lib/runtime-config";

import { reportAccessLoss } from "@/lib/api/access";
import { KayaApiError } from "@/lib/api/errors";
import { API_ROUTES } from "@/lib/api-routes";
import { useIdentity } from "@/lib/identity/identity-context";
import { createSseParser, parseSessionUpdated } from "@/lib/live/sse";
import type { ApiResponse } from "@/types/api";

/** How often the lists reload while the stream is down. */
export const STREAM_FALLBACK_POLL_MS = 10_000;

/** The first wait before reopening a failed stream; it doubles up to the maximum. */
const RETRY_FIRST_MS = 5_000;
const RETRY_MAX_MS = 60_000;

/**
 * Batches arrive every two seconds per running session; changes that land within
 * this window reload the wall once.
 */
const COALESCE_MS = 1_000;

/**
 * `connecting` until the first event, `live` while it flows, `paused` after a
 * failure, when the lists reload every ten seconds instead.
 */
export type LiveState = "connecting" | "live" | "paused";

/** The stream's refusal as a KayaApiError, so a lost session or membership reaches the identity gate. */
async function refusalOf(response: Response): Promise<KayaApiError> {
  let body: ApiResponse<unknown> | null = null;
  try {
    body = (await response.json()) as ApiResponse<unknown>;
  } catch (error: unknown) {
    console.warn("[stream] Refusal without a JSON body", error);
  }
  const detail = body?.error;
  return new KayaApiError(
    detail?.message ?? `Kaya answered ${response.status} to the live stream`,
    response.status,
    detail?.code ?? null,
    detail?.errorValues ?? {}
  );
}

/** Reloads the wall, the wall's filters and the sessions the events named. */
function makeInvalidator(queryClient: QueryClient, organizationId: string) {
  let pending = new Set<string>();
  let timer: ReturnType<typeof setTimeout> | null = null;

  const flush = () => {
    timer = null;
    const sessions = pending;
    pending = new Set<string>();
    void queryClient.invalidateQueries({ queryKey: ["sessions", organizationId, "list"] });
    void queryClient.invalidateQueries({ queryKey: ["sessions", organizationId, "filters"] });
    sessions.forEach((sessionId: string) => {
      void queryClient.invalidateQueries({
        queryKey: ["sessions", organizationId, "detail", sessionId],
      });
    });
  };

  return {
    sessionUpdated(sessionId: string) {
      pending.add(sessionId);
      if (timer === null) timer = setTimeout(flush, COALESCE_MS);
    },
    /** Everything under the organization's sessions, after a gap in which events may have been missed. */
    all() {
      void queryClient.invalidateQueries({ queryKey: ["sessions", organizationId] });
    },
    cancel() {
      if (timer !== null) clearTimeout(timer);
    },
  };
}

/**
 * Holds `GET /stream` open for the current organization and reloads the wall
 * and the open session when a `session-updated` event names a change, so new events
 * show within seconds without polling. The stream needs the bearer token and
 * `X-Organization-ID`, which EventSource cannot send, so it is read through
 * fetch. The server ends a stream after an hour; it is reopened at once. When
 * it fails, the sessions reload every ten seconds and the stream is retried with
 * a growing wait until it is back.
 */
export function useKayaStream(): LiveState {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  const organizationId = organization.organizationId;
  const [current, setCurrent] = useState<{ organizationId: string; state: LiveState }>({
    organizationId,
    state: "connecting",
  });

  useEffect(() => {
    const controller = new AbortController();
    const invalidate = makeInvalidator(queryClient, organizationId);
    let poll: ReturnType<typeof setInterval> | null = null;
    let retry: ReturnType<typeof setTimeout> | null = null;
    let retryMs = RETRY_FIRST_MS;
    let opened = false;
    const setState = (state: LiveState) => setCurrent({ organizationId, state });

    const stopPolling = () => {
      if (poll !== null) clearInterval(poll);
      poll = null;
    };

    const pause = (error: unknown) => {
      if (controller.signal.aborted) return;
      console.warn("[stream] Live updates paused", error);
      setState("paused");
      if (poll === null) poll = setInterval(invalidate.all, STREAM_FALLBACK_POLL_MS);
      retry = setTimeout(() => void connect(), retryMs);
      retryMs = Math.min(retryMs * 2, RETRY_MAX_MS);
    };

    const connect = async () => {
      retry = null;
      try {
        const session = await getSession();
        const headers: Record<string, string> = {
          Accept: "text/event-stream",
          "X-Organization-ID": organizationId,
        };
        if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
        const apiBaseUrl: string = getRuntimeConfig().apiBaseUrl ?? "";
        const response = await fetch(`${apiBaseUrl}${API_ROUTES.stream}`, {
          headers,
          signal: controller.signal,
          cache: "no-store",
        });
        if (!response.ok || response.body === null) {
          const refusal = await refusalOf(response);
          reportAccessLoss(refusal);
          throw refusal;
        }

        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        const parse = createSseParser();
        let first = true;
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          for (const event of parse(value)) {
            if (first) {
              first = false;
              setState("live");
              stopPolling();
              retryMs = RETRY_FIRST_MS;
              // Changes made while no stream was open were not announced.
              if (opened) invalidate.all();
              opened = true;
            }
            if (event.event === "session-updated") {
              const update = parseSessionUpdated(event.data);
              if (update !== null) invalidate.sessionUpdated(update.sessionId);
            }
          }
        }
        if (first) {
          // A stream that ends before its first heartbeat is broken, not finished.
          throw new Error("The live stream closed before its first event");
        }
        // The server closes a stream after an hour: reopen it straight away.
        if (!controller.signal.aborted) void connect();
      } catch (error: unknown) {
        if (controller.signal.aborted) return;
        pause(error);
      }
    };

    void connect();

    return () => {
      controller.abort();
      invalidate.cancel();
      stopPolling();
      if (retry !== null) clearTimeout(retry);
    };
  }, [queryClient, organizationId]);

  // A switch to another organization starts over until its own stream answers.
  return current.organizationId === organizationId ? current.state : "connecting";
}
