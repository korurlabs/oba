/**
 * How the tool view names the tools, when a tool loads a path and what the
 * scope gives it. The toggle has to render before the server answers, so the
 * tool names it offers live here; the view's heading uses the server's name.
 */

import type { badgeVariants } from "@oba/ui/sky/badge";
import type { VariantProps } from "class-variance-authority";

import type { AgentTool, ToolLoading, ToolPathState } from "../schemas";

export const TOOL_LABELS: Readonly<Record<AgentTool, string>> = {
  CLAUDE_CODE: "Claude Code",
  CODEX: "OpenAI Codex",
  CURSOR: "Cursor",
  GEMINI_CLI: "Gemini CLI",
};

export type LabelText = {
  label: string;
  /** One sentence a reader sees beside or behind the label. */
  description: string;
};

export const LOADING_LABELS: Readonly<Record<ToolLoading, LabelText>> = {
  ALWAYS: { label: "At start", description: "loaded at the start of every session" },
  ON_DEMAND: {
    label: "When used",
    description:
      "loaded when the tool picks the skill, runs the subagent or command, or works in that folder",
  },
  ON_REFERENCE: {
    label: "When referenced",
    description:
      "read only when another file names it, such as settings.json registering a hook or AGENTS.md naming a rule",
  },
};

export type StateLabel = LabelText & {
  variant: NonNullable<VariantProps<typeof badgeVariants>["variant"]>;
};

/** The states in which the scope delivers something for a path. */
export type DeliveredState = Exclude<ToolPathState, "NOT_DELIVERED">;

/** The delivered states in the order a reader meets them, best case first. */
export const DELIVERED_STATES: readonly DeliveredState[] = ["LINKED", "DIRECT", "LINK_MISSING"];

export const STATE_LABELS: Readonly<Record<DeliveredState, StateLabel>> = {
  LINKED: {
    label: "Linked",
    description: "the scope delivers the link and the files it leads to",
    variant: "success",
  },
  DIRECT: {
    label: "Direct",
    description: "the scope delivers the file at the path the tool reads",
    variant: "info",
  },
  LINK_MISSING: {
    label: "Link missing",
    description: "the scope delivers the files but not the link, so the tool does not see them",
    variant: "warning",
  },
};

/**
 * A pack scope shows one pack's own files, while a repository's tool links
 * may come from another pack it composes at the root. So a link-missing read
 * in a pack scope is not a gap the tool would hit.
 */
export const PACK_LINK_MISSING: StateLabel = {
  label: "From the repository",
  description: "the scope delivers the files and a pack at the repository root provides the link",
  variant: "secondary",
};

/** The label a state shows in a scope; a link-missing read reads From the repository where a pack at its root provides the link. */
export function stateLabelOf(state: DeliveredState, fromRepository: boolean): StateLabel {
  return fromRepository && state === "LINK_MISSING" ? PACK_LINK_MISSING : STATE_LABELS[state];
}

/** "1 file" or "3 files". */
export function fileCount(count: number): string {
  return count === 1 ? "1 file" : `${count} files`;
}
