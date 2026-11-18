import { Fragment } from "react";

import { cn } from "@/lib/utils";

import { parseInline, parseMarkdown, type InlinePart, type MarkdownBlock } from "./markdown-blocks";

export type MarkdownProps = {
  /** Markdown as an agent wrote it; any HTML in it is shown as text. */
  source: string;
  className?: string;
};

function Inline({ text }: { text: string }) {
  return (
    <>
      {parseInline(text).map((part: InlinePart, index: number) => {
        if (part.kind === "code") {
          return (
            <code key={index} className="bg-muted rounded px-1 py-0.5 font-mono text-xs">
              {part.text}
            </code>
          );
        }
        if (part.kind === "strong") {
          return (
            <strong key={index} className="text-foreground font-semibold">
              {part.text}
            </strong>
          );
        }
        return <Fragment key={index}>{part.text}</Fragment>;
      })}
    </>
  );
}

const HEADING_CLASS: Record<number, string> = {
  1: "text-lg font-bold",
  2: "text-base font-semibold",
};

function Block({ block }: { block: MarkdownBlock }) {
  switch (block.kind) {
    case "heading": {
      const className = cn(
        "text-foreground",
        HEADING_CLASS[block.level] ?? "text-sm font-semibold"
      );
      return (
        <p role="heading" aria-level={Math.min(block.level + 2, 6)} className={className}>
          <Inline text={block.text} />
        </p>
      );
    }
    case "paragraph":
      return (
        <p className="break-words whitespace-pre-line">
          <Inline text={block.text} />
        </p>
      );
    case "list": {
      const List = block.ordered ? "ol" : "ul";
      return (
        <List
          className={cn("flex flex-col gap-1 pl-5", block.ordered ? "list-decimal" : "list-disc")}
        >
          {block.items.map((item, index: number) => (
            <li
              key={index}
              className="break-words"
              style={item.depth > 0 ? { marginLeft: `${item.depth * 1.25}rem` } : undefined}
            >
              <Inline text={item.text} />
            </li>
          ))}
        </List>
      );
    }
    case "code":
      return (
        <pre className="bg-muted overflow-x-auto rounded-md p-3 font-mono text-xs leading-relaxed">
          <code>{block.text}</code>
        </pre>
      );
  }
}

/**
 * Renders handoff markdown with the text style of Kalkan's prose blocks.
 * Only headings, lists, paragraphs, code fences, inline code and bold text
 * are read; everything else, raw HTML included, is shown as the text it is.
 */
export function Markdown({ source, className }: MarkdownProps) {
  return (
    <div
      className={cn("text-foreground/90 flex flex-col gap-3 text-sm leading-relaxed", className)}
    >
      {parseMarkdown(source).map((block: MarkdownBlock, index: number) => (
        <Block key={index} block={block} />
      ))}
    </div>
  );
}
