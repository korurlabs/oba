"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { AlertTriangle, ArrowLeft, GitPullRequest, Plus, Ticket, Trash2, X } from "lucide-react";

import { StateBadge } from "@/components/common";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field, Input, NativeSelect } from "@oba/ui/sky/field";
import {
  sessionState,
  sessionTitle,
  relativeTime,
  subscriptionLabel,
} from "@/features/sessions/components/wall-data";
import { SessionLessonsPanel } from "@/features/lessons/components/session-lessons-panel";
import { SessionPlansPanel } from "@/features/plans/components/session-plans-panel";
import { SessionLinkKind, type SessionLinkResponse } from "@/features/sessions/schemas";
import { describeError } from "@/lib/api/error-text";
import { useIdentity } from "@/lib/identity/identity-context";

import {
  isSessionNotFound,
  mayDeleteSession,
  useAddSessionLink,
  useSessionDetail,
  useDeleteSession,
  useRemoveSessionLink,
} from "../hooks/use-session-detail";
import {
  sessionLinkCreateRequestSchema,
  type SessionDetailResponse,
  type SessionHandoffResponse,
} from "../schemas";
import { buildSession, type LoadedHandoff, type SessionData } from "./session-data";
import { ConfigReleaseBadge } from "./config-release-badge";
import { Markdown } from "./markdown";
import { Timeline } from "./timeline";
import { buildTimeline } from "./timeline-data";
import { WaitingBanner } from "./waiting-banner";

export type SessionViewProps = {
  sessionId: string;
};

const NO_HANDOFFS: readonly SessionHandoffResponse[] = [];

/**
 * One session: whether it waits for the user, its plans, its handoffs, its
 * lessons and its timeline. It reloads when the live stream reports a change
 * of the session.
 */
export function SessionView({ sessionId }: SessionViewProps) {
  const { detail, events, handoffs, deleted } = useSessionDetail(sessionId);
  const sessionDetail = detail.data?.data ?? null;
  const records = events.data?.data ?? null;
  const snapshots = handoffs.data?.data ?? (handoffs.error === null ? null : NO_HANDOFFS);

  const session = useMemo(
    () =>
      sessionDetail === null || records === null || snapshots === null
        ? null
        : buildSession(sessionDetail, records, snapshots),
    [sessionDetail, records, snapshots]
  );

  if (deleted) {
    return <SessionDeleted />;
  }
  if (isSessionNotFound(detail.error)) {
    return <SessionNotFound sessionId={sessionId} />;
  }
  const firstError = sessionDetail === null ? detail.error : records === null ? events.error : null;
  if (firstError !== null) {
    return (
      <SessionError
        message={firstError.message}
        onRetry={() => {
          void detail.refetch();
          void events.refetch();
        }}
      />
    );
  }
  if (session === null) {
    return <SessionLoading />;
  }

  const warnings = [
    detail.error === null
      ? null
      : `The latest reload failed, showing the previous data: ${detail.error.message}`,
    events.error === null
      ? null
      : `The latest events could not be reloaded: ${events.error.message}`,
    handoffs.error === null ? null : `The handoffs could not be loaded: ${handoffs.error.message}`,
    session.problems.length > 0
      ? `${session.problems.length} unreadable ${session.problems.length === 1 ? "event was" : "events were"} left out (first: number ${session.problems[0]?.line}, ${session.problems[0]?.reason}).`
      : null,
  ].filter((text): text is string => text !== null);

  return <SessionPage session={session} loadedAt={detail.dataUpdatedAt} warnings={warnings} />;
}

/** A pull request reads as its number, a ticket as its key. */
function linkText(link: SessionLinkResponse): string {
  return link.kind === "PULL_REQUEST" ? `#${link.reference.split("/").pop()}` : link.reference;
}

function LinkBadge({ link }: { link: SessionLinkResponse }) {
  const Icon = link.kind === "PULL_REQUEST" ? GitPullRequest : Ticket;
  const text = linkText(link);
  const badge = (
    <Badge variant="outline" className="font-mono">
      <Icon className="size-3" aria-hidden />
      {text}
    </Badge>
  );
  return link.url ? (
    <a href={link.url} target="_blank" rel="noreferrer">
      {badge}
    </a>
  ) : (
    badge
  );
}

/** The facts a session header lists, each left out when the recorder did not capture it. */
function facts(detail: SessionDetailResponse): { label: string; value: string }[] {
  return [
    { label: "User", value: detail.userDisplayName ?? "Unknown" },
    { label: "Machine", value: detail.machineName ?? "Unknown" },
    { label: "Claude Code", value: detail.claudeCodeVersion ?? "Unknown" },
    { label: "Model", value: detail.model ?? "Unknown" },
    { label: "Subscription", value: subscriptionLabel(detail) ?? "Unknown" },
  ];
}

const LINK_KIND_LABELS: Record<SessionLinkKind, string> = {
  KAZAN_TICKET: "Kazan ticket",
  PULL_REQUEST: "Pull request",
};

function AddSessionLink({ sessionId }: { sessionId: string }) {
  const add = useAddSessionLink();
  const [kind, setKind] = useState<SessionLinkKind>(SessionLinkKind.KAZAN_TICKET);
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const parsed = sessionLinkCreateRequestSchema.safeParse({ kind, reference });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the link entered.");
      return;
    }
    setError(null);
    add.mutate(
      { sessionId, data: parsed.data },
      {
        onSuccess: () => setReference(""),
        onError: (failure: unknown) => setError(describeError(failure)),
      }
    );
  };
  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
      <Field label="Link kind" className={error ? "mb-5" : undefined}>
        <NativeSelect
          value={kind}
          onChange={(event) => setKind(event.target.value as SessionLinkKind)}
        >
          {Object.values(SessionLinkKind).map((value: SessionLinkKind) => (
            <option key={value} value={value}>
              {LINK_KIND_LABELS[value]}
            </option>
          ))}
        </NativeSelect>
      </Field>
      <Field label="Add a link" error={error} className="min-w-64 flex-1">
        <Input
          value={reference}
          maxLength={500}
          placeholder={
            kind === SessionLinkKind.KAZAN_TICKET
              ? "KAYA-42"
              : "https://github.com/owner/repo/pull/42"
          }
          onChange={(event) => setReference(event.target.value)}
        />
      </Field>
      <Button
        type="submit"
        variant="outline"
        disabled={add.isPending}
        className={error ? "mb-5" : undefined}
      >
        <Plus aria-hidden />
        Add
      </Button>
    </form>
  );
}

/**
 * The session's pull requests and Kazan tickets. Every member may add or remove
 * one; a link the recorder found comes back when a later batch names it again.
 */
export function SessionLinks({ detail }: { detail: SessionDetailResponse }) {
  const remove = useRemoveSessionLink();
  return (
    <section className="flex flex-col gap-2" aria-label="Links">
      {detail.links.length === 0 ? (
        <p className="text-muted-foreground text-sm">No pull request or ticket linked yet.</p>
      ) : (
        <ul className="flex flex-wrap items-center gap-1.5">
          {detail.links.map((link: SessionLinkResponse) => (
            <li key={link.id} className="inline-flex items-center gap-0.5">
              <LinkBadge link={link} />
              <ConfirmDialog
                trigger={
                  <Button variant="ghost" size="icon-xs" aria-label={`Remove ${linkText(link)}`}>
                    <X aria-hidden />
                  </Button>
                }
                title={`Remove ${linkText(link)}`}
                description={
                  link.source === "RECORDER"
                    ? "The session no longer shows this link. The recorder found it, so it comes back when a later batch names it again."
                    : "The session no longer shows this link."
                }
                confirmLabel="Remove"
                destructive
                onConfirm={() =>
                  remove.mutateAsync({ sessionId: detail.sessionId, linkId: link.id })
                }
              />
            </li>
          ))}
        </ul>
      )}
      <AddSessionLink sessionId={detail.sessionId} />
    </section>
  );
}

/** Deletes the whole session after a confirmation, then goes back to the wall; for its user or an admin. */
export function DeleteSession({ detail }: { detail: SessionDetailResponse }) {
  const identity = useIdentity();
  const router = useRouter();
  const remove = useDeleteSession();
  if (!mayDeleteSession(identity, detail)) return null;
  return (
    <ConfirmDialog
      trigger={
        <Button variant="outline" size="sm">
          <Trash2 aria-hidden />
          Delete session
        </Button>
      }
      title="Delete this session"
      description="Its events, links and handoffs are deleted for everyone in the organization. This cannot be undone."
      confirmLabel="Delete session"
      destructive
      onConfirm={async () => {
        await remove.mutateAsync(detail.sessionId);
        router.push("/");
      }}
    />
  );
}

const HANDOFF_VARIANTS: Readonly<Record<string, "success" | "warning" | "error" | "muted">> = {
  APPROVED: "success",
  DONE: "success",
  NEEDS_CHANGES: "warning",
  PARTIAL: "warning",
  CRITICAL_ISSUES: "error",
  BLOCKED: "error",
};

/** Every handoff the session wrote, with its status and its latest snapshot rendered. */
function SessionHandoffs({ handoffs }: { handoffs: ReadonlyMap<string, LoadedHandoff> }) {
  if (handoffs.size === 0) {
    return <p className="text-muted-foreground text-sm">This session wrote no handoff.</p>;
  }
  return (
    <ul className="flex flex-col gap-2">
      {[...handoffs.entries()].map(([path, loaded]: [string, LoadedHandoff]) => (
        <li key={path} className="rounded-lg border">
          <details>
            <summary className="flex cursor-pointer flex-wrap items-center gap-2 px-3 py-2">
              <span className="font-mono text-sm break-all">{path}</span>
              {loaded.parsed.status !== null && (
                <Badge variant={HANDOFF_VARIANTS[loaded.parsed.status] ?? "muted"}>
                  {loaded.parsed.status}
                </Badge>
              )}
            </summary>
            <div className="border-t p-3">
              <Markdown source={loaded.markdown} className="max-h-[32rem] overflow-y-auto" />
            </div>
          </details>
        </li>
      ))}
    </ul>
  );
}

function SessionPage({
  session,
  loadedAt,
  warnings,
}: {
  session: SessionData;
  loadedAt: number;
  warnings: readonly string[];
}) {
  const { view, detail } = session;
  const timeline = useMemo(() => buildTimeline(view), [view]);
  const showBanner = view.idle && !view.ended && !detail.ended;
  const lastEventAt = detail.lastEventAt ?? session.events[session.events.length - 1]?.at ?? null;

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-2">
          <Link
            href="/"
            className="text-muted-foreground hover:text-foreground inline-flex w-fit items-center gap-1.5 text-sm"
          >
            <ArrowLeft className="size-4" aria-hidden />
            Sessions
          </Link>
          <DeleteSession detail={detail} />
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          {detail.repositoryName && <Badge variant="secondary">{detail.repositoryName}</Badge>}
          {detail.branch && (
            <Badge variant="outline" className="font-mono font-normal">
              {detail.branch}
            </Badge>
          )}
          <StateBadge state={sessionState(detail)} />
          <ConfigReleaseBadge detail={detail} />
        </div>
        <h1 className="break-words">
          {sessionTitle({ title: detail.title ?? detail.sessionTitle })}
        </h1>
        {detail.identityConflictUserDisplayName && (
          <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>Identity of {detail.identityConflictUserDisplayName}</span>
          </p>
        )}
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-3 lg:grid-cols-5">
          {facts(detail).map((fact) => (
            <div key={fact.label} className="flex min-w-0 flex-col">
              <dt className="text-muted-foreground text-xs">{fact.label}</dt>
              <dd className="truncate" title={fact.value}>
                {fact.value}
              </dd>
            </div>
          ))}
        </dl>
        <SessionLinks detail={detail} />
        <p className="text-muted-foreground text-sm">
          {[
            `${view.agents.length} ${view.agents.length === 1 ? "subagent" : "subagents"}`,
            lastEventAt === null ? null : `last activity ${relativeTime(lastEventAt, loadedAt)}`,
            view.ended || detail.ended ? "session ended" : null,
          ]
            .filter(Boolean)
            .join(" · ")}
        </p>
      </header>

      {warnings.length > 0 && (
        <ul className="text-warning-text flex flex-col gap-1 text-sm" role="status">
          {warnings.map((text: string) => (
            <li key={text} className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
              <span>{text}</span>
            </li>
          ))}
        </ul>
      )}

      {showBanner && <WaitingBanner message={view.lastAssistantMessage} />}

      <PageSection title="Plans" description="Specs and plan mode plans this session wrote.">
        <SessionPlansPanel sessionId={detail.sessionId} />
      </PageSection>

      <PageSection title="Handoffs" description="The latest snapshot of every handoff it wrote.">
        <SessionHandoffs handoffs={session.handoffs} />
      </PageSection>

      <PageSection title="Lessons">
        <SessionLessonsPanel sessionId={detail.sessionId} />
      </PageSection>

      <PageSection title="Timeline">
        <Timeline items={timeline} />
      </PageSection>
    </div>
  );
}

function PageSection({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3" aria-label={title}>
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold">{title}</h2>
        {description && <p className="text-muted-foreground text-sm">{description}</p>}
      </div>
      {children}
    </section>
  );
}

/** The page's outline while the first load runs. */
export function SessionLoading() {
  return (
    <div className="flex flex-col gap-8" aria-busy="true" aria-label="Loading the session">
      <div className="bg-muted h-10 w-2/3 animate-pulse rounded-lg" />
      <div className="flex gap-2 overflow-hidden">
        {Array.from({ length: 6 }, (_, index: number) => (
          <div
            key={index}
            className="bg-muted h-24 w-40 shrink-0 animate-pulse rounded-xl border"
          />
        ))}
      </div>
      <div className="bg-muted h-64 animate-pulse rounded-xl" />
    </div>
  );
}

/** Shown between this user's delete and the move back to the wall. */
function SessionDeleted() {
  return (
    <Card className="items-center py-12 text-center">
      <CardHeader className="w-full justify-items-center">
        <CardTitle className="text-lg">Session deleted</CardTitle>
        <CardDescription>Its events, links and handoffs are gone.</CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <Link href="/">Back to the sessions</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function SessionNotFound({ sessionId }: { sessionId: string }) {
  return (
    <Card className="items-center py-12 text-center">
      <CardHeader className="w-full justify-items-center">
        <CardTitle className="text-lg">Session not found</CardTitle>
        <CardDescription>
          {sessionId === "" ? (
            "The link names no session."
          ) : (
            <>
              Your organization has no session with the id{" "}
              <code className="font-mono">{sessionId}</code>.
            </>
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <Link href="/">Back to the sessions</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function SessionError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Card role="alert" className="border-error/40 bg-error-light gap-4">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <AlertTriangle className="text-error size-5" aria-hidden />
          Could not load this session
        </CardTitle>
        <CardDescription className="text-foreground">{message}</CardDescription>
      </CardHeader>
      <CardContent className="flex gap-2">
        <Button variant="outline" onClick={onRetry}>
          Try again
        </Button>
        <Button asChild variant="ghost">
          <Link href="/">Back to the sessions</Link>
        </Button>
      </CardContent>
    </Card>
  );
}
