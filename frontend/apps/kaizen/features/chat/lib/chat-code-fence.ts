/**
 * The one rule for reading a fenced code block (` ```…``` `), shared by the
 * composer, which turns typed and pasted fences into code blocks, and the
 * message renderer, so a fence shows the same language and code in both.
 */
import { common, createLowlight } from "lowlight";

/** The languages the chat highlights: lowlight's `common` set, as the ticket editor. */
export const chatCodeLowlight = createLowlight(common);

export interface ChatCodeFence {
  language: string | null;
  code: string;
}

/** A fence's first line names its language only when it is a single word. */
const LANGUAGE_LINE_PATTERN = /^[\w+#.-]+$/;
/** A one-line fence: a first word, white space, then the code. */
const ONE_LINE_LANGUAGE_PATTERN = /^([\w+#.-]+)\s+(\S[\s\S]*)$/;
/** A closed fence; its code runs to the nearest closing fence. */
const COMPLETE_FENCE_PATTERN = /```([\s\S]*?)```/g;

/**
 * Splits what stands between the fences into language and code. Over
 * several lines a first line holding a single word (or nothing) is the
 * language. On one line, as in ` ```ts const a = 1``` `, the first word is
 * the language only when lowlight knows it and white space follows it;
 * otherwise the whole text is code, so ` ```hello world``` ` stays as typed.
 */
export function splitChatCodeFence(inner: string): ChatCodeFence {
  const newline = inner.indexOf("\n");
  if (newline >= 0) {
    const firstLine = inner.slice(0, newline).trim();
    if (firstLine.length === 0 || LANGUAGE_LINE_PATTERN.test(firstLine)) {
      return { language: firstLine || null, code: inner.slice(newline + 1).replace(/\n$/, "") };
    }
    return { language: null, code: inner.replace(/\n$/, "") };
  }
  const oneLine = ONE_LINE_LANGUAGE_PATTERN.exec(inner);
  const word = oneLine?.[1];
  if (word && oneLine[2] !== undefined && chatCodeLowlight.registered(word)) {
    return { language: word, code: oneLine[2] };
  }
  return { language: null, code: inner };
}

/**
 * Rewrites every closed fence of pasted text as a fence on lines of its
 * own, which is the only form the markdown parser reads as a code block:
 * written on one line or in the middle of a line it would be inline code
 * or plain text.
 */
export function normalizeChatCodeFences(text: string): string {
  return text.replace(COMPLETE_FENCE_PATTERN, (whole: string, inner: string, offset: number) => {
    const { language, code } = splitChatCodeFence(inner);
    if (code.trim().length === 0) return whole;
    const lineBreak = offset === 0 || text[offset - 1] === "\n" ? "" : "\n";
    return `${lineBreak}\`\`\`${language ?? ""}\n${code}\n\`\`\`\n`;
  });
}
