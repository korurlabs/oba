import Link from "next/link";
import { AlertTriangle, Cpu, GitPullRequest, Ticket, User } from "lucide-react";

import { StateBadge } from "@/components/common";
import { Badge } from "@oba/ui/sky/badge";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@oba/ui/sky/card";

import type { SessionLinkResponse, SessionListResponse } from "../schemas";
import { sessionState, sessionTitle, relativeTime, subscriptionLabel } from "./wall-data";

export type SessionTileProps = {
  session: SessionListResponse;
  /** The current time in milliseconds, for the relative last activity. */
  now: number;
};

function LinkBadge({ link }: { link: SessionLinkResponse }) {
  const Icon = link.kind === "PULL_REQUEST" ? GitPullRequest : Ticket;
  const text =
    link.kind === "PULL_REQUEST" ? `#${link.reference.split("/").pop()}` : link.reference;
  return (
    <Badge variant="outline" className="font-mono font-normal">
      <Icon className="size-3" aria-hidden />
      {text}
    </Badge>
  );
}

/** One session as a card on the wall; the whole card links to the session. */
export function SessionTile({ session, now }: SessionTileProps) {
  const subscription = subscriptionLabel(session);
  const lastEventAt = session.lastEventAt ?? session.createdAt;

  return (
    <Link
      href={`/sessions/${encodeURIComponent(session.sessionId)}`}
      className="focus-visible:ring-ring/50 group block rounded-xl outline-none focus-visible:ring-[3px]"
    >
      <Card className="group-hover:border-primary/60 h-full gap-4 py-5 transition-colors group-hover:shadow-md">
        <CardHeader className="gap-3 px-5">
          <div className="flex min-h-5 flex-wrap items-center gap-1.5">
            {session.repositoryName && <Badge variant="secondary">{session.repositoryName}</Badge>}
            {session.links.map((link: SessionLinkResponse) => (
              <LinkBadge key={link.id} link={link} />
            ))}
          </div>
          <CardTitle className="line-clamp-3 text-lg leading-snug font-semibold break-words">
            {sessionTitle(session)}
          </CardTitle>
          {session.branch && (
            <p className="text-muted-foreground truncate font-mono text-xs">{session.branch}</p>
          )}
        </CardHeader>

        <CardContent className="mt-auto flex flex-col gap-3 px-5">
          {session.identityConflictUserDisplayName && (
            <p className="text-warning-text flex items-start gap-1.5 text-xs" role="status">
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              <span>Identity of {session.identityConflictUserDisplayName}</span>
            </p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <StateBadge state={sessionState(session)} />
          </div>
          <dl className="text-muted-foreground grid gap-1 text-xs">
            <div className="flex items-center gap-1.5">
              <dt className="sr-only">User</dt>
              <User className="size-3.5 shrink-0" aria-hidden />
              <dd className="truncate">{session.userDisplayName ?? "Unknown user"}</dd>
            </div>
            <div className="flex items-center gap-1.5">
              <dt className="sr-only">Machine</dt>
              <Cpu className="size-3.5 shrink-0" aria-hidden />
              <dd className="truncate">
                {[session.machineName ?? "Unknown machine", session.model]
                  .filter(Boolean)
                  .join(" · ")}
              </dd>
            </div>
          </dl>
        </CardContent>

        <CardFooter className="text-muted-foreground justify-between gap-2 border-t px-5 pt-4 text-xs [.border-t]:pt-4">
          <span className="truncate">{subscription ?? "Subscription unknown"}</span>
          <time dateTime={lastEventAt} title={lastEventAt} className="shrink-0">
            {relativeTime(lastEventAt, now)}
          </time>
        </CardFooter>
      </Card>
    </Link>
  );
}
