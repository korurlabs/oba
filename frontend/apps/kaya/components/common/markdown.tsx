import { Fragment } from "react";

import { cn } from "@/lib/utils";

import {
  parseInline,
  parseMarkdown,
  safeHref,
  type InlinePart,
  type MarkdownBlock,
  type TableAlign,
} from "./markdown-blocks";

export type MarkdownProps = {
  /** Markdown as an agent or a member wrote it; any HTML in it is shown as text. */
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
        if (part.kind === "link") {
          const href = safeHref(part.href);
          if (href === null) {
            return (
              <span key={index} className="underline decoration-dotted" title={part.href}>
                {part.text}
              </span>
            );
          }
          return (
            <a
              key={index}
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline underline-offset-2"
            >
              {part.text}
            </a>
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

const ALIGN_CLASS: Readonly<Record<NonNullable<TableAlign>, string>> = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
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
    case "quote":
      return (
        <blockquote className="text-muted-foreground flex flex-col gap-3 border-l-2 pl-3">
          {block.blocks.map((inner: MarkdownBlock, index: number) => (
            <Block key={index} block={inner} />
          ))}
        </blockquote>
      );
    case "table":
      return (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b">
                {block.header.map((cell: string, index: number) => (
                  <th
                    key={index}
                    className={cn(
                      "px-2 py-1 text-left font-semibold",
                      ALIGN_CLASS[block.align[index] ?? "left"]
                    )}
                  >
                    <Inline text={cell} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row: string[], rowIndex: number) => (
                <tr key={rowIndex} className="border-b last:border-0">
                  {row.map((cell: string, index: number) => (
                    <td
                      key={index}
                      className={cn(
                        "px-2 py-1 align-top break-words",
                        ALIGN_CLASS[block.align[index] ?? "left"]
                      )}
                    >
                      <Inline text={cell} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

/**
 * Renders Markdown with the text style of Kalkan's prose blocks. Headings,
 * lists, paragraphs, code fences, quotes, pipe tables, inline code, bold text
 * and links are read; everything else, raw HTML included, is shown as the
 * text it is.
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
