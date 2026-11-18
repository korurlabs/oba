/**
 * Turns literal code in the chat composer into code as soon as it is
 * complete, as Discord does: `` `x` `` becomes inline code on its closing
 * backtick and ` ```…``` ` a code block on its closing fence, also written on
 * one line or closed at the end of a line. The same rule runs over the whole
 * message before it is sent, so text that is still literal (typed fast, or
 * pasted as HTML) is sent as code too.
 *
 * Keyboard layouts with a dead backtick key commit the backtick together
 * with the next key (a space, a full stop, a letter), as a composition; the
 * conversion then runs when the composition ends, with that key kept after
 * the code.
 *
 * Text typed or pasted after an opening fence that is not closed yet is code
 * being written, so it goes in as it reads: no markdown input or paste rule
 * (bold, italic, strike) may rewrite it, or `__init__` would lose its
 * underscores before the fence closes.
 */
import { Extension } from "@tiptap/core";
import type { Node as ProseMirrorNode, ResolvedPos } from "@tiptap/pm/model";
import { Plugin, PluginKey, TextSelection, type Transaction } from "@tiptap/pm/state";
import type { EditorView } from "@tiptap/pm/view";
import { splitChatCodeFence } from "./chat-code-fence";

/** Stands for a position that cannot be part of a delimiter: a chip or a character already in code. */
const OPAQUE = "￼";
const FENCE = "```";
/** A closed fence with its inner text in group 1. */
const FENCE_PATTERN = /```([\s\S]*?)```/g;
/** An inline code span: single backticks around text on one line, not part of a longer backtick run. */
const INLINE_CODE_PATTERN = /(?<!`)`([^`\n]+)`(?!`)/g;

interface CodeSpan {
  kind: "fence" | "inline";
  /** Offsets in the textblock's content, `end` exclusive. */
  start: number;
  end: number;
}

/**
 * The textblock's content as one character per position: line breaks as
 * `\n`, every other inline node and every character already in code as
 * {@link OPAQUE}, so offsets map one to one onto document positions and no
 * backtick inside existing code counts as a delimiter.
 */
function scanText(block: ProseMirrorNode): string {
  let text = "";
  block.forEach((child) => {
    if (child.isText) {
      const value = child.text ?? "";
      text += child.marks.some((mark) => mark.type.spec.code) ? OPAQUE.repeat(value.length) : value;
    } else if (child.type.name === "hardBreak") {
      text += "\n";
    } else {
      text += OPAQUE.repeat(child.nodeSize);
    }
  });
  return text;
}

function pushInlineSpans(text: string, from: number, to: number, spans: CodeSpan[]): void {
  const pattern = new RegExp(INLINE_CODE_PATTERN.source, "g");
  pattern.lastIndex = from;
  for (let match = pattern.exec(text); match; match = pattern.exec(text)) {
    const end = match.index + match[0].length;
    if (end > to) break;
    if ((match[1] ?? "").trim().length > 0) spans.push({ kind: "inline", start: match.index, end });
  }
}

/** The closed fences of the text that hold code, left to right; one with nothing between its delimiters is not code. */
function findClosedFences(text: string): CodeSpan[] {
  const fences: CodeSpan[] = [];
  for (const match of text.matchAll(FENCE_PATTERN)) {
    if ((match[1] ?? "").trim().length > 0) fences.push({ kind: "fence", start: match.index, end: match.index + match[0].length });
  }
  return fences;
}

/** The offset of the opening fence after the last closed one, which is still open, or -1. */
function findOpenFence(text: string, fences: CodeSpan[]): number {
  return text.indexOf(FENCE, fences[fences.length - 1]?.end ?? 0);
}

/**
 * The complete code spans of a textblock, left to right as the renderer
 * reads them: closed fences first, inline code between them. After an
 * opening fence that is not closed yet nothing is inline code, because the
 * fence will hold it.
 */
function findCodeSpans(text: string): CodeSpan[] {
  const spans: CodeSpan[] = [];
  const fences = findClosedFences(text);
  let last = 0;
  for (const fence of fences) {
    pushInlineSpans(text, last, fence.start, spans);
    spans.push(fence);
    last = fence.end;
  }
  const openFence = findOpenFence(text, fences);
  pushInlineSpans(text, last, openFence >= 0 ? openFence : text.length, spans);
  return spans;
}

/**
 * Whether `$pos` lies in a fence opened before it in the same textblock and
 * not closed yet, so text typed or pasted there is code of that fence.
 */
export function isInsideOpenFence($pos: ResolvedPos): boolean {
  const block = $pos.parent;
  if (!block.isTextblock || block.type.spec.code) return false;
  const textBefore = scanText(block).slice(0, $pos.parentOffset);
  return findOpenFence(textBefore, findClosedFences(textBefore)) >= 0;
}

/** The text a node shows, e.g. `@name` for a member chip; Tiptap keeps it as the node's `toText`. */
function leafText(node: ProseMirrorNode): string {
  const toText = (node.type.spec as { toText?: (props: Record<string, unknown>) => string }).toText;
  if (!toText) return "";
  return toText({ node, pos: 0, parent: node, index: 0, range: { from: 0, to: 0 } });
}

/**
 * Replaces one span of the textblock whose content starts at `contentStart`:
 * inline code becomes text with the code mark, a fence becomes a code block
 * between the text before and after it. An empty block left before the code
 * block is dropped, and so is an empty one after it when `dropEmptyAfter`
 * (the caret needs it while typing). Returns false when the schema does not
 * allow the result there.
 */
function convertSpan(tr: Transaction, contentStart: number, span: CodeSpan, dropEmptyAfter: boolean): boolean {
  const { schema } = tr.doc.type;
  const from = contentStart + span.start;
  const to = contentStart + span.end;
  const $from = tr.doc.resolve(from);
  const block = $from.parent;

  if (span.kind === "inline") {
    const codeType = schema.marks.code;
    if (!codeType || !block.type.allowsMarkType(codeType)) return false;
    const code = tr.doc.textBetween(from + 1, to - 1, "\n", leafText);
    if (code.length === 0) return false;
    tr.replaceWith(from, to, schema.text(code, [codeType.create()]));
    return true;
  }

  const codeBlockType = schema.nodes.codeBlock;
  if (!codeBlockType) return false;
  const { language, code } = splitChatCodeFence(tr.doc.textBetween(from + FENCE.length, to - FENCE.length, "\n", leafText));
  if (code.trim().length === 0) return false;
  const container = $from.node(-1);
  const index = $from.index(-1);
  const codeBlock = codeBlockType.create({ language }, schema.text(code));
  if (!container.canReplaceWith(index + 1, index + 1, codeBlockType)) return false;

  const blockStart = $from.before();
  tr.delete(from, to);
  // The text before the fence stays in this block, the text after it moves
  // to a new one after the code block.
  tr.split(from);
  tr.insert(from + 1, codeBlock);
  const afterStart = from + 1 + codeBlock.nodeSize;
  const after = tr.doc.nodeAt(afterStart);
  const $after = tr.doc.resolve(afterStart);
  if (dropEmptyAfter && after && after.content.size === 0 && $after.parent.canReplace($after.index(), $after.index() + 1)) {
    tr.delete(afterStart, afterStart + after.nodeSize);
  }
  const before = tr.doc.nodeAt(blockStart);
  const parentAfter = tr.doc.resolve(blockStart).parent;
  const indexAfter = tr.doc.resolve(blockStart).index();
  if (before && before.content.size === 0 && parentAfter.canReplace(indexAfter, indexAfter + 1)) {
    tr.delete(blockStart, blockStart + before.nodeSize);
  }
  return true;
}

/**
 * Converts the span whose closing delimiter ends at `closeEnd`, if a complete
 * one does, and keeps the caret at `caret` (both positions in `tr.doc`).
 * Typing then goes on outside the code.
 */
export function convertCodeEndingAt(tr: Transaction, closeEnd: number, caret: number): boolean {
  const $close = tr.doc.resolve(closeEnd);
  const block = $close.parent;
  if (!block.isTextblock || block.type.spec.code) return false;
  const contentStart = $close.start();
  const span = findCodeSpans(scanText(block)).find((candidate) => candidate.end === closeEnd - contentStart);
  if (!span) return false;
  const stepsBefore = tr.steps.length;
  if (!convertSpan(tr, contentStart, span, false)) return false;
  const mapped = tr.mapping.slice(stepsBefore).map(caret);
  tr.setSelection(TextSelection.near(tr.doc.resolve(mapped)));
  const codeType = tr.doc.type.schema.marks.code;
  if (codeType) tr.removeStoredMark(codeType);
  return true;
}

/** Converts every complete span of literal code in the document; true when it changed anything. */
export function convertAllLiteralCode(tr: Transaction): boolean {
  const blocks: { pos: number; node: ProseMirrorNode }[] = [];
  tr.doc.descendants((node, pos) => {
    if (!node.isTextblock) return true;
    if (!node.type.spec.code) blocks.push({ pos, node });
    return false;
  });
  let changed = false;
  // From the end, so the positions of the spans still to convert stay valid.
  for (const { pos, node } of blocks.reverse()) {
    const spans = findCodeSpans(scanText(node)).reverse();
    for (const span of spans) changed = convertSpan(tr, pos + 1, span, true) || changed;
  }
  return changed;
}

/**
 * Inserts pasted text into a fence that is still open as it reads, with a
 * line break between its lines. The transaction is no paste, so neither the
 * markdown parser nor a paste rule rewrites the code (`__init__`, `**x**`)
 * before the fence closes, and all lines stay in the fence's textblock. A
 * paste that closes the fence converts it, as typing the closing fence
 * would, and so does every later span the pasted text completes.
 * Returns false, inserting nothing, when there is no text or the schema has
 * no line break.
 */
export function insertLiteralCodeText(view: EditorView, text: string): boolean {
  const { state } = view;
  const hardBreak = state.schema.nodes.hardBreak;
  if (text.length === 0 || !hardBreak) return false;
  const nodes: ProseMirrorNode[] = [];
  text.split(/\r\n?|\n/).forEach((line, index) => {
    if (index > 0) nodes.push(hardBreak.create());
    if (line.length > 0) nodes.push(state.schema.text(line));
  });
  const { from, to } = state.selection;
  const tr = state.tr.replaceWith(from, to, nodes);
  const caret = tr.mapping.map(to);
  tr.setSelection(TextSelection.near(tr.doc.resolve(caret)));
  if (text.includes("`")) convertSpansClosedIn(tr, from, caret);
  view.dispatch(tr.scrollIntoView());
  return true;
}

/**
 * Converts the spans whose closing delimiter lies in the inserted range
 * `from`..`caret` of one textblock, as typing each delimiter would, and
 * keeps the caret after the last of them. The last span goes first, so the
 * positions of the spans before it stay valid.
 */
function convertSpansClosedIn(tr: Transaction, from: number, caret: number): void {
  const $caret = tr.doc.resolve(caret);
  const block = $caret.parent;
  if (!block.isTextblock || block.type.spec.code) return;
  const contentStart = $caret.start();
  const insertedFrom = from - contentStart;
  const insertedTo = caret - contentStart;
  const closed = findCodeSpans(scanText(block)).filter((span) => span.end > insertedFrom && span.end <= insertedTo);
  let current = caret;
  for (const span of closed.reverse()) {
    if (convertCodeEndingAt(tr, contentStart + span.end, current)) current = tr.selection.from;
  }
}

/** Converts the literal code of the view's document, e.g. right before the message is read for sending. */
export function convertViewLiteralCode(view: EditorView): void {
  const tr = view.state.tr;
  if (convertAllLiteralCode(tr)) view.dispatch(tr.setMeta("addToHistory", false));
}

/** Runs the conversion for the text a finished composition committed, e.g. `` ` `` plus a full stop. */
function convertAfterComposition(view: EditorView, committed: string): void {
  if (view.isDestroyed || view.composing) return;
  const { selection } = view.state;
  if (!(selection instanceof TextSelection) || !selection.empty) return;
  const caret = selection.from;
  const closeEnd = caret - (committed.length - 1 - committed.lastIndexOf("`"));
  const $caret = selection.$from;
  if (closeEnd <= $caret.start() || view.state.doc.textBetween(closeEnd - 1, closeEnd) !== "`") return;
  const tr = view.state.tr;
  if (convertCodeEndingAt(tr, closeEnd, caret)) view.dispatch(tr.scrollIntoView());
}

/**
 * The chat composer's code input: conversion on the closing delimiter, typed
 * or composed, and literal text inside a fence that is still open. Its
 * priority puts its text input handler before the markdown input rules of
 * the other extensions, which would otherwise rewrite that text first.
 */
export const ChatCodeInput = Extension.create({
  name: "chatCodeInput",
  priority: 1000,
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: new PluginKey("chatCodeInput"),
        props: {
          handleTextInput: (view, from, to, text, insertAsTyped) => {
            if (view.composing) return false;
            if (text.endsWith("`")) {
              const tr = view.state.tr.insertText(text, from, to);
              const caret = from + text.length;
              if (convertCodeEndingAt(tr, caret, caret)) {
                view.dispatch(tr.scrollIntoView());
                return true;
              }
            }
            if (!isInsideOpenFence(view.state.doc.resolve(from))) return false;
            // Handled here, the text skips the markdown input rules that would run after this handler.
            view.dispatch(insertAsTyped());
            return true;
          },
          handleDOMEvents: {
            compositionend: (view, event) => {
              const committed = event.data ?? "";
              if (!committed.includes("`")) return false;
              // ProseMirror reads the committed text into the document after this event.
              setTimeout(() => convertAfterComposition(view, committed));
              return false;
            },
          },
        },
      }),
    ];
  },
});
