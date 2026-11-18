import type { ReactNode } from "react";
import { CheckSquare, Square } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { Markdown } from "@/features/sessions/components/markdown";

import type { PlanHandoffResponse } from "../schemas";
import {
  parsePlanOutline,
  type OutlineStep,
  type OutlineStory,
  type SessionOutline,
  type SpecOutline,
  type StoryCriterion,
  type UnitRow,
} from "./plan-outline";
import { unitStatus } from "./plan-detail-data";

const MISSING = "Not in this plan";

const STATUS_VARIANTS: Readonly<Record<string, "success" | "warning" | "error">> = {
  DONE: "success",
  PARTIAL: "warning",
  BLOCKED: "error",
};

function OutlineSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2" aria-label={title}>
      <h3 className="text-base font-semibold">{title}</h3>
      {children}
    </section>
  );
}

function Missing() {
  return <p className="text-muted-foreground text-sm">{MISSING}</p>;
}

function TextOrMissing({ text }: { text: string | null }) {
  return text === null ? <Missing /> : <Markdown source={text} />;
}

/** A handoff's status as a badge with its word, so it does not rest on colour. */
export function HandoffStatusBadge({ status }: { status: string | null | undefined }) {
  if (!status) return <Badge variant="muted">No status line</Badge>;
  return <Badge variant={STATUS_VARIANTS[status] ?? "muted"}>{status}</Badge>;
}

function SessionOutlineView({ outline }: { outline: SessionOutline }) {
  return (
    <div className="flex flex-col gap-6">
      <OutlineSection title="Goal">
        <TextOrMissing text={outline.goal} />
      </OutlineSection>
      <OutlineSection title="Steps">
        {outline.steps === null ? (
          <Missing />
        ) : (
          <ol className="flex flex-col gap-3">
            {outline.steps.map((step: OutlineStep) => (
              <li key={step.number} className="flex gap-3">
                <span className="text-muted-foreground w-6 shrink-0 text-right text-sm tabular-nums">
                  {step.number}.
                </span>
                <div className="flex min-w-0 flex-col gap-1">
                  <Markdown source={step.text} />
                  {step.paths.length > 0 && (
                    <ul className="flex flex-wrap gap-1" aria-label="Files">
                      {step.paths.map((path: string) => (
                        <li key={path}>
                          <code className="bg-muted rounded px-1 py-0.5 font-mono text-xs break-all">
                            {path}
                          </code>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </li>
            ))}
          </ol>
        )}
      </OutlineSection>
      <OutlineSection title="Verification">
        <TextOrMissing text={outline.verification} />
      </OutlineSection>
      <OutlineSection title="Risks">
        <TextOrMissing text={outline.risks} />
      </OutlineSection>
    </div>
  );
}

function Checkbox({ checked, label }: { checked: boolean; label: string }) {
  const Icon = checked ? CheckSquare : Square;
  return (
    <span className="mt-0.5 shrink-0" role="img" aria-label={label}>
      <Icon className="text-muted-foreground size-4" aria-hidden />
    </span>
  );
}

function Stories({ stories }: { stories: readonly OutlineStory[] }) {
  return (
    <ol className="flex flex-col gap-4">
      {stories.map((story: OutlineStory, index: number) => (
        <li key={story.id ?? index} className="flex flex-col gap-1.5">
          <p className="text-sm">
            {story.id && <span className="mr-1.5 font-semibold">{story.id}.</span>}
            {story.text}
          </p>
          {story.criteria.length > 0 && (
            <ul className="flex flex-col gap-1 pl-1">
              {story.criteria.map((criterion: StoryCriterion, at: number) => (
                <li key={at} className="flex items-start gap-2 text-sm">
                  <Checkbox
                    checked={criterion.checked}
                    label={criterion.checked ? "Met" : "Not checked yet"}
                  />
                  <Markdown source={criterion.text} />
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ol>
  );
}

function Units({
  outline,
  handoffs,
}: {
  outline: SpecOutline;
  handoffs: readonly PlanHandoffResponse[];
}) {
  const units = outline.units;
  if (units === null) return <Missing />;
  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              {units.headers.map((header: string, index: number) => (
                <TableHead key={index}>{header}</TableHead>
              ))}
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {units.rows.map((row: UnitRow, index: number) => {
              const status = unitStatus(handoffs, row.number);
              return (
                <TableRow key={index} className="align-top">
                  {units.headers.map((_header: string, column: number) => (
                    <TableCell key={column} className="max-w-xs break-words whitespace-normal">
                      <Markdown source={row.cells[column] ?? ""} />
                    </TableCell>
                  ))}
                  <TableCell className="whitespace-nowrap">
                    {status.handoff === null ? (
                      <span className="text-muted-foreground text-sm">{status.text}</span>
                    ) : (
                      <HandoffStatusBadge status={status.handoff.status} />
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      {units.notes !== "" && <Markdown source={units.notes} />}
    </div>
  );
}

function FeatureHandoffs({ handoffs }: { handoffs: readonly PlanHandoffResponse[] }) {
  if (handoffs.length === 0) {
    return <p className="text-muted-foreground text-sm">No handoff of this feature yet.</p>;
  }
  return (
    <ul className="flex flex-col gap-1.5">
      {handoffs.map((handoff: PlanHandoffResponse) => (
        <li key={handoff.path} className="flex flex-wrap items-center gap-2 text-sm">
          <HandoffStatusBadge status={handoff.status} />
          <code className="font-mono text-xs break-all">{handoff.path}</code>
          {handoff.unitNumber != null && (
            <span className="text-muted-foreground text-xs">unit {handoff.unitNumber}</span>
          )}
        </li>
      ))}
    </ul>
  );
}

function SpecOutlineView({
  outline,
  handoffs,
}: {
  outline: SpecOutline;
  handoffs: readonly PlanHandoffResponse[];
}) {
  return (
    <div className="flex flex-col gap-6">
      <OutlineSection title="Summary">
        <TextOrMissing text={outline.summary} />
      </OutlineSection>
      <OutlineSection title="User stories">
        {outline.stories === null ? <Missing /> : <Stories stories={outline.stories} />}
      </OutlineSection>
      <OutlineSection title="Implementation units">
        <Units outline={outline} handoffs={handoffs} />
      </OutlineSection>
      <OutlineSection title="Handoffs of the feature">
        <FeatureHandoffs handoffs={handoffs} />
      </OutlineSection>
      <OutlineSection title="Assumptions to confirm">
        {outline.assumptions === null ? (
          <Missing />
        ) : (
          <ul className="flex flex-col gap-1.5">
            {outline.assumptions.map((assumption: string, index: number) => (
              <li key={index} className="flex items-start gap-2 text-sm">
                <Checkbox checked={false} label="To confirm" />
                <Markdown source={assumption} />
              </li>
            ))}
          </ul>
        )}
      </OutlineSection>
    </div>
  );
}

export type PlanOutlineViewProps = {
  kind: "SPEC" | "SESSION";
  content: string;
  handoffs: readonly PlanHandoffResponse[];
};

/** A version read as its outline: the core plan sections, or a spec's stories, units and assumptions. */
export function PlanOutlineView({ kind, content, handoffs }: PlanOutlineViewProps) {
  const outline = parsePlanOutline(kind, content);
  return outline.kind === "SPEC" ? (
    <SpecOutlineView outline={outline} handoffs={handoffs} />
  ) : (
    <SessionOutlineView outline={outline} />
  );
}
