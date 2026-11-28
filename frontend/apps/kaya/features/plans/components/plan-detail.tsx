"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { ArrowLeft } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { NativeSelect } from "@oba/ui/sky/field";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { FileDiff } from "@/features/changes/components/diff-view";
import { Markdown } from "@/features/sessions/components/markdown";
import { describeError } from "@/lib/api/error-text";
import { ERROR_CODES, KayaApiError } from "@/lib/api/errors";
import { dateTimeText } from "@/lib/format-time";
import { handoffSection, parseHandoff } from "@/lib/handoff";
import { useIdentity } from "@/lib/identity/identity-context";

import { usePlan, usePlanDiff, usePlanVersion, useWithdrawFeedback } from "../hooks/use-plans";
import type {
  PlanDetailResponse,
  PlanFeedbackResponse,
  PlanHandoffResponse,
  PlanVersionSummaryResponse,
} from "../schemas";
import { KIND_LABELS, REVIEW_BADGES, referenceName } from "./plan-data";
import { DECISION_LABELS, mayWithdraw, planRefusalText, versionLabel } from "./plan-detail-data";
import { PlanDocument } from "./plan-document";
import { PlanFeedbackForm } from "./plan-feedback-form";
import { PlanPack } from "./plan-pack";
import { HandoffStatusBadge, PlanOutlineView } from "./plan-outline-view";
import { rangeText } from "./line-selection";

function Meta({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="text-sm break-words">{children}</dd>
    </div>
  );
}

function SessionLink({ sessionId }: { sessionId: string }) {
  return (
    <Link
      href={`/sessions/${encodeURIComponent(sessionId)}`}
      className="font-mono text-xs hover:underline"
    >
      {sessionId.slice(0, 8)}
    </Link>
  );
}

/** Whether feedback has reached a session, and which one. */
function DeliveryState({ feedback }: { feedback: PlanFeedbackResponse }) {
  if (feedback.deliveredAt == null) {
    return <span className="text-muted-foreground text-xs">Waiting for the session</span>;
  }
  return (
    <span className="text-muted-foreground text-xs">
      Delivered to{" "}
      {feedback.deliveredSessionId ? (
        <SessionLink sessionId={feedback.deliveredSessionId} />
      ) : (
        "a session"
      )}{" "}
      at {dateTimeText(feedback.deliveredAt)}
    </span>
  );
}

function WithdrawButton({ planId, feedback }: { planId: string; feedback: PlanFeedbackResponse }) {
  const withdraw = useWithdrawFeedback();
  return (
    <ConfirmDialog
      trigger={
        <Button size="xs" variant="ghost">
          Withdraw
        </Button>
      }
      title="Withdraw this feedback"
      description={<p>No session has received it yet. It is removed and Claude never reads it.</p>}
      confirmLabel="Withdraw"
      destructive
      onConfirm={async () => {
        try {
          await withdraw.mutateAsync({ planId, feedbackId: feedback.id });
        } catch (failure: unknown) {
          throw new Error(planRefusalText(failure, "withdraw"));
        }
      }}
    />
  );
}

export type PlanFeedbackListProps = {
  planId: string;
  feedback: readonly PlanFeedbackResponse[];
  /** The version on screen; feedback given on another is marked with its version. */
  shownVersion: number;
  userId: string;
};

/** Every feedback of the plan, newest first, with its delivery and the reviewer's own withdraw. */
export function PlanFeedbackList({
  planId,
  feedback,
  shownVersion,
  userId,
}: PlanFeedbackListProps) {
  if (feedback.length === 0) {
    return <p className="text-muted-foreground text-sm">No feedback yet.</p>;
  }
  return (
    <ol className="flex flex-col gap-3">
      {[...feedback].reverse().map((item: PlanFeedbackResponse) => (
        <li key={item.id} className="flex flex-col gap-1.5 rounded-md border p-3">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium">{referenceName(item.reviewer)}</span>
            <span>{DECISION_LABELS[item.decision]}</span>
            {item.versionNumber !== shownVersion && (
              <Badge variant="muted">on version {item.versionNumber}</Badge>
            )}
            <span className="text-muted-foreground text-xs">{dateTimeText(item.createdAt)}</span>
            {mayWithdraw(item, userId) && <WithdrawButton planId={planId} feedback={item} />}
          </div>
          {item.lineStart != null && item.lineEnd != null && (
            <p className="text-muted-foreground text-xs">
              {rangeText({ start: item.lineStart, end: item.lineEnd })}
            </p>
          )}
          {item.quote && (
            <blockquote className="text-muted-foreground border-l-2 pl-2 font-mono text-xs break-words whitespace-pre-wrap">
              {item.quote}
            </blockquote>
          )}
          {item.comment && (
            <p className="text-sm break-words whitespace-pre-wrap">{item.comment}</p>
          )}
          <DeliveryState feedback={item} />
        </li>
      ))}
    </ol>
  );
}

/** The planning handoff that names this spec: its status, open items and next step. */
export function PlanningHandoffPanel({ handoff }: { handoff: PlanHandoffResponse }) {
  const parsed = parseHandoff(handoff.content);
  const open = handoffSection(parsed, "Open questions and items");
  const next = handoffSection(parsed, "Next step");
  return (
    <Card className="gap-3" aria-label="Planning handoff">
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          Planning handoff
          <HandoffStatusBadge status={handoff.status ?? parsed.status} />
        </CardTitle>
        <code className="text-muted-foreground font-mono text-xs break-all">{handoff.path}</code>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <section className="flex flex-col gap-1.5">
          <h3 className="text-sm font-semibold">Open questions and items</h3>
          {open ? (
            <Markdown source={open} />
          ) : (
            <p className="text-muted-foreground text-sm">None</p>
          )}
        </section>
        <section className="flex flex-col gap-1.5">
          <h3 className="text-sm font-semibold">Next step</h3>
          {next ? (
            <Markdown source={next} />
          ) : (
            <p className="text-muted-foreground text-sm">None</p>
          )}
        </section>
      </CardContent>
    </Card>
  );
}

/** The planning handoff of the plan's feature, when the detail names one that is loaded. */
export function planningHandoff(plan: PlanDetailResponse): PlanHandoffResponse | null {
  if (!plan.planningHandoffPath) return null;
  return (
    plan.handoffs.find(
      (handoff: PlanHandoffResponse) => handoff.path === plan.planningHandoffPath
    ) ?? null
  );
}

function VersionTabs({ plan, version }: { plan: PlanDetailResponse; version: number }) {
  const [tab, setTab] = useState("outline");
  const content = usePlanVersion(plan.id, version);
  const diff = usePlanDiff(plan.id, tab === "changes" ? version : undefined);
  const text = content.data?.data?.content;
  const isLatest = version === plan.latestVersionNumber;
  const anchored = new Set<number>();
  for (const item of plan.feedback) {
    if (item.versionNumber !== version || item.lineStart == null || item.lineEnd == null) continue;
    for (let line = item.lineStart; line <= item.lineEnd; line += 1) anchored.add(line);
  }

  const body = (render: (source: string) => ReactNode) => {
    if (content.error !== null && text === undefined) {
      return (
        <LoadError
          title="Could not load this version"
          message={describeError(content.error)}
          onRetry={() => void content.refetch()}
        />
      );
    }
    if (text === undefined) return <TableLoading label="Loading the version" />;
    return render(text);
  };

  const changes = () => {
    if (version === 1) {
      return (
        <EmptyState
          title="The first version"
          description="Version 1 has no version before it to compare with."
        />
      );
    }
    const unifiedDiff = diff.data?.data?.diff;
    if (diff.error !== null && unifiedDiff === undefined) {
      return (
        <LoadError
          title="Could not load the changes"
          message={describeError(diff.error)}
          onRetry={() => void diff.refetch()}
        />
      );
    }
    if (unifiedDiff === undefined) return <TableLoading label="Loading the changes" />;
    if (unifiedDiff === "") {
      return (
        <EmptyState title="No change" description={`Version ${version} equals the one before.`} />
      );
    }
    return (
      <FileDiff
        file={{
          path: plan.path ?? "session-plan.md",
          change: "MODIFIED",
          binary: false,
          unifiedDiff,
        }}
      />
    );
  };

  return (
    <Tabs value={tab} onValueChange={setTab}>
      <TabsList>
        <TabsTrigger value="outline">Outline</TabsTrigger>
        <TabsTrigger value="document">Document</TabsTrigger>
        <TabsTrigger value="changes">Changes</TabsTrigger>
      </TabsList>
      <TabsContent value="outline">
        {body((source: string) => (
          <PlanOutlineView kind={plan.kind} content={source} handoffs={plan.handoffs} />
        ))}
      </TabsContent>
      <TabsContent value="document" className="flex flex-col gap-2">
        <p className="text-muted-foreground text-sm">
          Click a line number to comment on it; Shift-click, or Shift and the arrow keys, select a
          range.
        </p>
        {body((source: string) => (
          <PlanDocument
            key={version}
            content={source}
            anchoredLines={anchored}
            renderForm={({ range, quote, clear, textareaRef }) => (
              <PlanFeedbackForm
                planId={plan.id}
                versionNumber={version}
                isLatestVersion={isLatest}
                canApprove={plan.canApprove}
                range={range}
                quote={quote}
                onSaved={clear}
                onCancel={clear}
                textareaRef={textareaRef}
              />
            )}
          />
        ))}
      </TabsContent>
      <TabsContent value="changes">{changes()}</TabsContent>
    </Tabs>
  );
}

function PlanView({ plan }: { plan: PlanDetailResponse }) {
  const identity = useIdentity();
  // Null follows the latest version, so a new capture shows without choosing it.
  const [chosen, setChosen] = useState<number | null>(null);
  const version = chosen ?? plan.latestVersionNumber;
  const review = REVIEW_BADGES[plan.reviewState];
  const planning = planningHandoff(plan);
  const session = plan.latestSession;

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={plan.title}
        back={{ href: "/plans", label: "All plans" }}
        description={
          <div className="mt-2 flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary">{KIND_LABELS[plan.kind]}</Badge>
              <Badge variant={review.variant}>{review.label}</Badge>
            </div>
            {plan.path && (
              <code className="text-muted-foreground font-mono text-sm break-all">{plan.path}</code>
            )}
            <dl className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-3 lg:grid-cols-6">
              <Meta label="Repository">{referenceName(plan.repository)}</Meta>
              <Meta label="Pack">
                <PlanPack pack={plan.pack} spec={plan.kind === "SPEC"} />
              </Meta>
              <Meta label="User">{referenceName(plan.user)}</Meta>
              <Meta label="Latest session">
                {session ? (
                  <>
                    <SessionLink sessionId={session.sessionId} />
                    {session.ended ? " (ended)" : " (running)"}
                  </>
                ) : (
                  "Deleted"
                )}
              </Meta>
              <Meta label="Branch">{session?.branch ?? "Unknown"}</Meta>
              <Meta label="Updated">{dateTimeText(plan.lastChangedAt)}</Meta>
            </dl>
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="flex min-w-0 flex-col gap-4">
          <label className="flex max-w-xl flex-col gap-1.5 text-sm">
            <span className="font-medium">Version</span>
            <NativeSelect
              value={version}
              onChange={(event) => {
                const number = Number(event.target.value);
                setChosen(number === plan.latestVersionNumber ? null : number);
              }}
            >
              {[...plan.versions].reverse().map((entry: PlanVersionSummaryResponse) => (
                <option key={entry.number} value={entry.number}>
                  {versionLabel(entry)}
                  {entry.number === plan.latestVersionNumber ? " (latest)" : ""}
                </option>
              ))}
            </NativeSelect>
          </label>
          <VersionTabs plan={plan} version={version} />
        </div>
        {planning !== null && (
          <aside className="flex flex-col gap-4">
            <PlanningHandoffPanel handoff={planning} />
          </aside>
        )}
      </div>

      <section className="flex flex-col gap-3" aria-labelledby="feedback-title">
        <h2 id="feedback-title">Feedback</h2>
        <PlanFeedbackForm
          planId={plan.id}
          versionNumber={version}
          isLatestVersion={version === plan.latestVersionNumber}
          canApprove={plan.canApprove}
          range={null}
        />
        <PlanFeedbackList
          planId={plan.id}
          feedback={plan.feedback}
          shownVersion={version}
          userId={identity.me.id}
        />
      </section>
    </div>
  );
}

/**
 * One plan: its outline, its numbered document with line feedback, the
 * changes of each version, its feedback with delivery state, and the
 * planning handoff of its feature. The server's answers decide what the
 * viewer may do; Approve shows only with `canApprove`.
 */
export function PlanDetail({ planId }: { planId: string }) {
  const plan = usePlan(planId);
  const detail = plan.data?.data;
  if (plan.error !== null && detail === undefined) {
    const gone = plan.error instanceof KayaApiError && plan.error.code === ERROR_CODES.NOT_FOUND;
    return (
      <div className="flex flex-col gap-4">
        <BackLink />
        <LoadError
          title={gone ? "This plan does not exist" : "Could not load the plan"}
          message={describeError(plan.error)}
          onRetry={() => void plan.refetch()}
        />
      </div>
    );
  }
  if (!detail) return <TableLoading label="Loading the plan" />;
  return <PlanView plan={detail} />;
}

function BackLink() {
  return (
    <Link
      href="/plans"
      className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-sm"
    >
      <ArrowLeft className="size-4" aria-hidden />
      All plans
    </Link>
  );
}
