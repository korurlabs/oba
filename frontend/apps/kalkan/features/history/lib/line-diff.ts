/**
 * One row of a side-by-side compare: the line of the older text on the left,
 * the line of the newer text on the right. A side is null where the other
 * text has a line the first one lacks.
 */
export type CompareRow =
  | { kind: "same"; left: string; right: string }
  | { kind: "changed"; left: string; right: string }
  | { kind: "removed"; left: string; right: null }
  | { kind: "added"; left: null; right: string };

/**
 * The largest table of line pairs the compare builds. Beyond it the texts are
 * shown side by side without marks, because the table grows with the product
 * of the line counts and would stall the browser on very long pages.
 */
export const MAX_COMPARE_CELLS = 4_000_000;

function splitLines(text: string): string[] {
  return text === "" ? [] : text.replace(/\r\n/g, "\n").split("\n");
}

/**
 * Compares two texts line by line on their longest common subsequence and
 * pairs them for a side-by-side view: equal lines side by side, a run of
 * removed lines beside the run of added lines that replaced it. Returns null
 * when the texts are too long to compare (see `MAX_COMPARE_CELLS`).
 */
export function compareLines(older: string, newer: string): CompareRow[] | null {
  const left = splitLines(older);
  const right = splitLines(newer);

  // The shared head and tail need no table, which keeps a small edit to a long page cheap.
  let head = 0;
  while (head < left.length && head < right.length && left[head] === right[head]) head += 1;
  let tail = 0;
  while (
    tail < left.length - head &&
    tail < right.length - head &&
    left[left.length - 1 - tail] === right[right.length - 1 - tail]
  ) {
    tail += 1;
  }
  const a = left.slice(head, left.length - tail);
  const b = right.slice(head, right.length - tail);
  if ((a.length + 1) * (b.length + 1) > MAX_COMPARE_CELLS) return null;

  // lcs[i * width + j] is the common subsequence length of a[i..] and b[j..].
  const width = b.length + 1;
  const lcs = new Uint32Array((a.length + 1) * width);
  for (let i = a.length - 1; i >= 0; i -= 1) {
    for (let j = b.length - 1; j >= 0; j -= 1) {
      lcs[i * width + j] =
        a[i] === b[j]
          ? lcs[(i + 1) * width + j + 1]! + 1
          : Math.max(lcs[(i + 1) * width + j]!, lcs[i * width + j + 1]!);
    }
  }

  const rows: CompareRow[] = left.slice(0, head).map((line) => ({
    kind: "same" as const,
    left: line,
    right: line,
  }));
  let removed: string[] = [];
  let added: string[] = [];
  const flush = () => {
    const paired = Math.min(removed.length, added.length);
    for (let k = 0; k < paired; k += 1) {
      rows.push({ kind: "changed", left: removed[k]!, right: added[k]! });
    }
    for (const line of removed.slice(paired))
      rows.push({ kind: "removed", left: line, right: null });
    for (const line of added.slice(paired)) rows.push({ kind: "added", left: null, right: line });
    removed = [];
    added = [];
  };

  let i = 0;
  let j = 0;
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) {
      flush();
      rows.push({ kind: "same", left: a[i]!, right: b[j]! });
      i += 1;
      j += 1;
    } else if (
      j < b.length &&
      (i === a.length || lcs[i * width + j + 1]! >= lcs[(i + 1) * width + j]!)
    ) {
      added.push(b[j]!);
      j += 1;
    } else {
      removed.push(a[i]!);
      i += 1;
    }
  }
  flush();

  for (const line of left.slice(left.length - tail)) {
    rows.push({ kind: "same", left: line, right: line });
  }
  return rows;
}
