/**
 * Reading a plan as an outline.
 *
 * A session plan follows the core plan format: Goal, Steps, Verification and
 * Risks, each introduced by a bold label or a heading. A spec has `## `
 * sections; its outline takes the summary, the user stories with their
 * acceptance checkboxes, the implementation units table and the assumptions
 * to confirm. The result is plain data, rendered as text: nothing in a plan
 * is ever interpreted as markup.
 */

import type { PlanKind } from "../schemas";

export type OutlineStep = {
  number: number;
  /** The step as written, continuation lines included. */
  text: string;
  /** File paths the step names in inline code, in order, without repeats. */
  paths: string[];
};

export type SessionOutline = {
  kind: "SESSION";
  /** Null for a section the plan does not have. */
  goal: string | null;
  steps: OutlineStep[] | null;
  verification: string | null;
  risks: string | null;
};

export type StoryCriterion = { text: string; checked: boolean };

export type OutlineStory = {
  /** The story's label, such as "PL1"; null when it has none. */
  id: string | null;
  text: string;
  criteria: StoryCriterion[];
};

export type UnitRow = {
  /** The unit's number from the "#" column; null when the cell holds none. */
  number: number | null;
  cells: string[];
};

export type UnitTable = {
  headers: string[];
  rows: UnitRow[];
  /** The section's text besides the table, such as which units run in parallel. */
  notes: string;
};

export type SpecOutline = {
  kind: "SPEC";
  summary: string | null;
  stories: OutlineStory[] | null;
  units: UnitTable | null;
  assumptions: string[] | null;
};

export type PlanOutline = SessionOutline | SpecOutline;

type Line = { text: string; inFence: boolean };

const FENCE = /^\s*(```|~~~)/;

/** The lines of a text, each marked when it lies inside a code fence or is a fence line. */
function readLines(source: string): Line[] {
  let fence: string | null = null;
  return source
    .replace(/\r\n/g, "\n")
    .split("\n")
    .map((text: string): Line => {
      const match = FENCE.exec(text);
      if (match) {
        const marker = match[1] ?? "";
        fence = fence === null ? marker : fence === marker ? null : fence;
        return { text, inFence: true };
      }
      return { text, inFence: fence !== null };
    });
}

function joinTrimmed(lines: readonly string[]): string {
  return lines.join("\n").trim();
}

// ── Session plans ───────────────────────────────────────────────────

type SessionLabel = "goal" | "steps" | "verification" | "risks";

/** `**Goal**: text`, `**Goal:** text` or `1. **Goal**: text`, as a line of its own. */
const BOLD_LABEL =
  /^\s*(?:#{1,6}\s+)?(?:\d+[.)]\s+)?(?:\*\*|__)(goal|steps|verification|risks)\s*:?\s*(?:\*\*|__)\s*:?\s*(.*)$/i;
/** `## Goal` or `### 2. Steps:` with optional text after a colon. */
const HEADING_LABEL =
  /^\s*#{1,6}\s+(?:\d+[.)]\s*)?(goal|steps|verification|risks)\s*(?::\s*(.*))?$/i;

/** The label a line introduces, with the text after it; null for any other line. */
function sessionLabel(line: Line): { label: SessionLabel; rest: string } | null {
  if (line.inFence) return null;
  const match = BOLD_LABEL.exec(line.text) ?? HEADING_LABEL.exec(line.text);
  if (!match) return null;
  return { label: (match[1] ?? "").toLowerCase() as SessionLabel, rest: (match[2] ?? "").trim() };
}

const LIST_ITEM = /^(\s*)(\d+[.)]|[-*+])\s+(.*)$/;
const CODE_SPAN = /`([^`\n]+)`/g;
const FILE_EXTENSION =
  /\.(?:ts|tsx|js|jsx|mjs|cjs|json|md|mdx|java|kt|go|py|sql|ya?ml|sh|css|html|toml|xml|swift|txt|lock|properties)$/i;

/** Inline-code spans of a text that name a file path: a slash, or a file name with an extension. */
export function pathsIn(text: string): string[] {
  const paths: string[] = [];
  for (const match of text.matchAll(CODE_SPAN)) {
    const candidate = (match[1] ?? "").trim();
    if (candidate === "" || /\s/.test(candidate)) continue;
    const isPath = candidate.includes("/") || FILE_EXTENSION.test(candidate);
    if (isPath && !paths.includes(candidate)) paths.push(candidate);
  }
  return paths;
}

/**
 * The steps of a Steps section: the list items at its outermost indentation,
 * numbered ones first; deeper items and other lines continue the step above.
 * Text without any list item is one step.
 */
function parseSteps(lines: readonly Line[]): OutlineStep[] | null {
  const items = lines.map((line: Line) => (line.inFence ? null : LIST_ITEM.exec(line.text)));
  const numbered = items.filter((item) => item !== null && /\d/.test(item[2] ?? ""));
  const candidates = numbered.length > 0 ? numbered : items.filter((item) => item !== null);
  if (candidates.length === 0) {
    const text = joinTrimmed(lines.map((line: Line) => line.text));
    return text === "" ? null : [{ number: 1, text, paths: pathsIn(text) }];
  }
  const indent = Math.min(...candidates.map((item) => (item?.[1] ?? "").length));
  const isStepStart = (item: RegExpExecArray | null): boolean =>
    item !== null && candidates.includes(item) && (item[1] ?? "").length === indent;

  const steps: { lines: string[] }[] = [];
  lines.forEach((line: Line, index: number) => {
    const item = items[index] ?? null;
    if (isStepStart(item)) {
      steps.push({ lines: [item?.[3] ?? ""] });
      return;
    }
    const current = steps[steps.length - 1];
    if (current)
      current.lines.push(line.text.slice(Math.min(indent + 3, line.text.search(/\S|$/))));
  });
  return steps.map((step, index: number) => {
    const text = joinTrimmed(step.lines);
    return { number: index + 1, text, paths: pathsIn(text) };
  });
}

/** The outline of a core-format plan; a section the plan lacks is null. */
export function parseSessionOutline(source: string): SessionOutline {
  const sections = new Map<SessionLabel, Line[]>();
  let current: Line[] | null = null;
  for (const line of readLines(source)) {
    const found = sessionLabel(line);
    if (found !== null) {
      // The first section of a label wins; a repeat is read as part of the section before.
      if (!sections.has(found.label)) {
        current = found.rest === "" ? [] : [{ text: found.rest, inFence: false }];
        sections.set(found.label, current);
        continue;
      }
    }
    current?.push(line);
  }
  const text = (label: SessionLabel): string | null => {
    const lines = sections.get(label);
    if (lines === undefined) return null;
    const body = joinTrimmed(lines.map((line: Line) => line.text));
    return body === "" ? null : body;
  };
  const stepLines = sections.get("steps");
  return {
    kind: "SESSION",
    goal: text("goal"),
    steps: stepLines === undefined ? null : parseSteps(stepLines),
    verification: text("verification"),
    risks: text("risks"),
  };
}

// ── Specs ───────────────────────────────────────────────────────────

type SpecSection = { heading: string; lines: Line[] };

/** The `## ` sections of a spec, outside code fences, with "2. " numbering removed. */
function specSections(source: string): SpecSection[] {
  const sections: SpecSection[] = [];
  for (const line of readLines(source)) {
    if (!line.inFence && /^##\s+/.test(line.text)) {
      sections.push({
        heading: line.text
          .replace(/^##\s+/, "")
          .replace(/^\d+(?:\.\d+)*[.)]?\s+/, "")
          .trim(),
        lines: [],
      });
      continue;
    }
    sections[sections.length - 1]?.lines.push(line);
  }
  return sections;
}

function findSection(sections: readonly SpecSection[], pattern: RegExp): SpecSection | null {
  return sections.find((section: SpecSection) => pattern.test(section.heading)) ?? null;
}

const STORY_START = /^([A-Z]{1,8}\d+[a-z]?)\.\s+(.*)$/;
const CRITERION = /^\s*[-*+]\s+\[( |x|X)\]\s+(.*)$/;

/**
 * The stories of a user stories section. A story starts at a line with a
 * label such as "PL1.", or at an unindented paragraph after a blank line;
 * its checkbox items are its acceptance criteria. Other lines continue the
 * story's text or its last criterion, so wrapped prose stays one story.
 */
function parseStories(section: SpecSection): OutlineStory[] {
  const stories: { id: string | null; text: string[]; criteria: StoryCriterion[] }[] = [];
  let afterBlank = true;
  for (const line of section.lines) {
    if (line.text.trim() === "") {
      afterBlank = true;
      continue;
    }
    const startsParagraph = afterBlank;
    afterBlank = false;
    const criterion = line.inFence ? null : CRITERION.exec(line.text);
    const current = stories[stories.length - 1];
    if (criterion) {
      const entry = { text: (criterion[2] ?? "").trim(), checked: criterion[1] !== " " };
      if (current) {
        current.criteria.push(entry);
      } else {
        stories.push({ id: null, text: [], criteria: [entry] });
      }
      continue;
    }
    const story = line.inFence ? null : STORY_START.exec(line.text);
    const unindentedText = !line.inFence && !/^\s/.test(line.text) && !LIST_ITEM.test(line.text);
    if (story !== null || (startsParagraph && unindentedText) || current === undefined) {
      stories.push({
        id: story ? (story[1] ?? null) : null,
        text: [story ? (story[2] ?? "") : line.text.trim()],
        criteria: [],
      });
      continue;
    }
    const lastCriterion = current.criteria[current.criteria.length - 1];
    if (lastCriterion) {
      lastCriterion.text = `${lastCriterion.text} ${line.text.trim()}`;
    } else {
      current.text.push(line.text.trim());
    }
  }
  return stories.map((story) => ({
    id: story.id,
    text: story.text.join(" ").trim(),
    criteria: story.criteria,
  }));
}

const ESCAPED_PIPE = "\u0000";

/** The cells of a markdown table row; an escaped `\|` stays in its cell. */
export function tableCells(row: string): string[] {
  let text = row.trim().replace(/\\\|/g, ESCAPED_PIPE);
  if (text.startsWith("|")) text = text.slice(1);
  if (text.endsWith("|")) text = text.slice(0, -1);
  return text.split("|").map((cell: string) => cell.replaceAll(ESCAPED_PIPE, "|").trim());
}

const TABLE_ROW = /^\s*\|/;
const SEPARATOR_ROW = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$/;

/** The first table of a section, with the "#" column read as the unit number. */
function parseUnits(section: SpecSection): UnitTable | null {
  const rows: string[][] = [];
  const notes: string[] = [];
  let tableDone = false;
  for (const line of section.lines) {
    const isRow = !line.inFence && TABLE_ROW.test(line.text);
    if (isRow && !tableDone) {
      if (!SEPARATOR_ROW.test(line.text)) rows.push(tableCells(line.text));
      continue;
    }
    if (rows.length > 0) tableDone = true;
    notes.push(line.text);
  }
  const [headers, ...body] = rows;
  if (headers === undefined) return null;
  const numberColumn = Math.max(
    0,
    headers.findIndex((header: string) => header === "#" || /^(unit|no\.?|number)$/i.test(header))
  );
  return {
    headers,
    rows: body.map((cells: string[]): UnitRow => {
      const number = Number.parseInt(cells[numberColumn] ?? "", 10);
      return { number: Number.isNaN(number) ? null : number, cells };
    }),
    notes: joinTrimmed(notes),
  };
}

/** "Assumptions to confirm:" opening a line or a sentence, not mentioned inside one. */
const ASSUMPTIONS_LEAD = /^(?:[\s*#_>-]*|.*[.:!?]\s+[*_]*)assumptions to confirm\b/i;
const NUMBERED_ITEM = /^(\s*)\d+[.)]\s+(.*)$/;

/**
 * The numbered items after "Assumptions to confirm", or of the whole section
 * when it has no such line. Indented lines continue an item; the list ends
 * at the next unindented line that is not an item, such as "Risks:".
 */
function parseAssumptions(section: SpecSection): string[] | null {
  const leadAt = section.lines.findIndex(
    (line: Line) => !line.inFence && ASSUMPTIONS_LEAD.test(line.text)
  );
  const lines = section.lines.slice(leadAt + 1);
  const items: string[] = [];
  let open = false;
  for (const line of lines) {
    if (line.text.trim() === "") continue;
    const item = line.inFence ? null : NUMBERED_ITEM.exec(line.text);
    if (item && (item[1] ?? "") === "") {
      items.push((item[2] ?? "").trim());
      open = true;
      continue;
    }
    if (open && /^\s/.test(line.text)) {
      items[items.length - 1] = `${items[items.length - 1]} ${line.text.trim()}`;
      continue;
    }
    if (items.length > 0) break;
  }
  return items.length === 0 ? null : items;
}

/** The outline of a spec; a section the spec lacks is null. */
export function parseSpecOutline(source: string): SpecOutline {
  const sections = specSections(source);
  const summary = findSection(sections, /summary/i);
  const stories = findSection(sections, /user stories/i);
  const units = findSection(sections, /implementation units/i);
  const assumptions =
    findSection(sections, /assumptions|open questions/i) ??
    sections.find((section: SpecSection) =>
      section.lines.some((line: Line) => !line.inFence && ASSUMPTIONS_LEAD.test(line.text))
    ) ??
    null;
  const summaryText = summary ? joinTrimmed(summary.lines.map((line: Line) => line.text)) : "";
  const storyList = stories ? parseStories(stories) : [];
  return {
    kind: "SPEC",
    summary: summaryText === "" ? null : summaryText,
    stories: storyList.length === 0 ? null : storyList,
    units: units ? parseUnits(units) : null,
    assumptions: assumptions ? parseAssumptions(assumptions) : null,
  };
}

/** The outline of a plan of either kind. */
export function parsePlanOutline(kind: PlanKind, source: string): PlanOutline {
  return kind === "SPEC" ? parseSpecOutline(source) : parseSessionOutline(source);
}
