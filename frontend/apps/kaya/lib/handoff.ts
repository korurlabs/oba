/**
 * Reading a handoff file: its title, its `**Status:**` line and its sections.
 *
 * Every handoff follows the shared template: a `# Handoff: ...` title, one
 * status line, then `## ` sections such as "Done so far" and "Open questions
 * and items". The status line tells whether the work is done; the sections
 * are what the session view shows.
 */

import type { HandoffStatus } from "@/types/run";

export type HandoffSection = {
  heading: string;
  /** The markdown below the heading, trimmed. */
  body: string;
};

export type ParsedHandoff = {
  /** The text of the first `# ` heading, without the "Handoff:" prefix. */
  title: string | null;
  /** Null when the file has no status line, or one with an unknown value. */
  status: HandoffStatus | null;
  /** Text between the title and the first section, without the status line. */
  preamble: string;
  sections: HandoffSection[];
};

/** Section headings of the template, in its order. */
export const HANDOFF_SECTIONS = [
  "Scope given",
  "Done so far",
  "Verification",
  "Next step",
  "Open questions and items",
  "Decisions",
  "Placeholders",
] as const;

const STATUS_LINE = /^\*\*Status:\*\*\s*`?(PARTIAL|DONE|BLOCKED)\b/;
const FENCE = /^\s*(```|~~~)/;

/**
 * Parses handoff markdown. Headings inside code fences are text, not sections.
 *
 * @param markdown - The file as written.
 * @returns The title, the status and the sections in file order.
 */
export function parseHandoff(markdown: string): ParsedHandoff {
  let title: string | null = null;
  let status: HandoffStatus | null = null;
  const preamble: string[] = [];
  const sections: { heading: string; lines: string[] }[] = [];
  let fence: string | null = null;

  for (const line of markdown.replace(/\r\n/g, "\n").split("\n")) {
    const fenceMatch = FENCE.exec(line);
    if (fenceMatch) {
      const marker = fenceMatch[1] ?? "";
      fence = fence === null ? marker : fence === marker ? null : fence;
    }
    const inFence = fence !== null || fenceMatch !== null;
    if (!inFence && title === null && sections.length === 0 && line.startsWith("# ")) {
      title = line
        .slice(2)
        .replace(/^Handoff:\s*/, "")
        .trim();
      continue;
    }
    if (!inFence && line.startsWith("## ")) {
      sections.push({ heading: line.slice(3).trim(), lines: [] });
      continue;
    }
    const statusMatch = inFence ? null : STATUS_LINE.exec(line.trim());
    if (statusMatch && status === null && sections.length === 0) {
      status = statusMatch[1] as HandoffStatus;
      continue;
    }
    const current = sections[sections.length - 1];
    if (current) {
      current.lines.push(line);
    } else {
      preamble.push(line);
    }
  }

  return {
    title,
    status,
    preamble: preamble.join("\n").trim(),
    sections: sections.map((section) => ({
      heading: section.heading,
      body: section.lines.join("\n").trim(),
    })),
  };
}

/** The body of the section with this heading, or null when the handoff has none. */
export function handoffSection(handoff: ParsedHandoff, heading: string): string | null {
  const section = handoff.sections.find(
    (candidate: HandoffSection) => candidate.heading === heading
  );
  return section === undefined ? null : section.body;
}
