"use client";

import {
  Bot,
  ExternalLink,
  GitPullRequest,
  MessageSquare,
  Puzzle,
  SquareKanban,
  Upload,
} from "lucide-react";
import type { ReactNode } from "react";

import { SkillChip, StateBadge } from "@/components/common";
import { Badge } from "@oba/ui/sky/badge";
import { cn } from "@/lib/utils";

import { clockTime, duration } from "./session-data";
import type { CommandKind, TimelineItem } from "./timeline-data";

export type TimelineProps = {
  items: readonly TimelineItem[];
};

const COMMAND_LABEL: Readonly<Record<CommandKind, string>> = {
  push: "Push",
  "pull-request": "Pull request",
  kazan: "Kazan",
};

const COMMAND_ICON = {
  push: Upload,
  "pull-request": GitPullRequest,
  kazan: SquareKanban,
} as const;

function Row({ at, icon, children }: { at: string; icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 py-2">
      <time
        dateTime={at}
        title={at}
        className="text-muted-foreground w-16 shrink-0 pt-0.5 font-mono text-xs"
      >
        {clockTime(at)}
      </time>
      <span className="text-muted-foreground mt-0.5 shrink-0 [&>svg]:size-4" aria-hidden>
        {icon}
      </span>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-sm">{children}</div>
    </div>
  );
}

function Item({ item }: { item: TimelineItem }) {
  switch (item.kind) {
    case "prompt":
      return (
        <Row at={item.at} icon={<MessageSquare />}>
          <span className="line-clamp-3 break-words">
            <span className="font-medium">Prompt: </span>
            {item.prompt.text}
          </span>
        </Row>
      );
    case "skill": {
      const how =
        item.use.source === "preloaded"
          ? "Preloaded"
          : item.use.source === "command"
            ? "Typed as a command"
            : "Loaded";
      return (
        <Row at={item.at} icon={<Puzzle />}>
          <span className="text-muted-foreground">{how}</span>
          <SkillChip
            name={item.use.name}
            preloaded={item.use.source === "preloaded"}
            inUse={item.use.inUse}
            href={`/?skill=${encodeURIComponent(item.use.name)}`}
          />
        </Row>
      );
    }
    case "command": {
      const Icon = COMMAND_ICON[item.command];
      return (
        <Row at={item.at} icon={<Icon />}>
          <span className="font-medium">{COMMAND_LABEL[item.command]}</span>
          {item.run.ok === null ? (
            <StateBadge state="running" />
          ) : item.run.ok ? (
            <Badge variant="success">ok</Badge>
          ) : (
            <Badge variant="error">failed</Badge>
          )}
          <code
            className="text-muted-foreground min-w-0 truncate font-mono text-xs"
            title={item.run.command}
          >
            {item.run.command}
          </code>
          {item.run.url && (
            <a
              href={item.run.url}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary inline-flex items-center gap-1 text-xs underline underline-offset-4"
            >
              {item.run.url}
              <ExternalLink className="size-3" aria-hidden />
            </a>
          )}
        </Row>
      );
    }
    case "agent": {
      const { run } = item;
      const took = duration(run.startedAt, run.stoppedAt);
      return (
        <>
          <Row at={run.startedAt} icon={<Bot />}>
            <span className="font-mono font-medium">{run.subagentType}</span>
            <span className="text-muted-foreground">started</span>
            {run.description && <span className="min-w-0 truncate">{run.description}</span>}
            {run.active && <StateBadge state="running" />}
          </Row>
          {item.children.length > 0 && (
            <ol
              className="border-border ml-[5.25rem] border-l pl-4"
              aria-label={`Work of ${run.subagentType}`}
            >
              {item.children.map((child: TimelineItem) => (
                <li key={`${child.kind}-${child.index}`}>
                  <Item item={child} />
                </li>
              ))}
            </ol>
          )}
          {run.stoppedAt !== null && (
            <Row at={run.stoppedAt} icon={<Bot />}>
              <span className="font-mono font-medium">{run.subagentType}</span>
              <span className="text-muted-foreground">
                {took === null ? "stopped" : `stopped after ${took}`}
              </span>
            </Row>
          )}
        </>
      );
    }
  }
}

/**
 * The session in recorded order: prompts, skills, subagents, pushes, pull
 * requests and Kazan calls. A subagent's own skills and
 * subagents are indented under it, between its start and its stop.
 */
export function Timeline({ items }: TimelineProps) {
  if (items.length === 0) {
    return <p className="text-muted-foreground text-sm">Nothing recorded yet.</p>;
  }
  return (
    <ol aria-label="Timeline" className={cn("divide-border flex flex-col divide-y")}>
      {items.map((item: TimelineItem) => (
        <li key={`${item.kind}-${item.index}`}>
          <Item item={item} />
        </li>
      ))}
    </ol>
  );
}
