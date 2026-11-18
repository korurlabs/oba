"use client";

import { useMemo, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import type { ElementContent, Root, RootContent } from "hast";
import { Check, Copy } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import { chatCodeLowlight } from "../lib/chat-code-fence";

/** A block longer than this is collapsed behind "Show more". */
export const CHAT_CODE_COLLAPSED_LINES = 20;

/**
 * Token colours of highlight.js classes. The ticket editor's rules are scoped
 * to its own editor element, so the chat maps the same palette here; the
 * colours read on both the light and the dark muted background.
 */
const TOKEN_CLASSES: Record<string, string> = {
  "hljs-comment": "text-muted-foreground italic",
  "hljs-quote": "text-muted-foreground italic",
  "hljs-keyword": "text-[oklch(0.55_0.2_300)]",
  "hljs-selector-tag": "text-[oklch(0.55_0.2_300)]",
  "hljs-type": "text-[oklch(0.55_0.2_300)]",
  "hljs-string": "text-[oklch(0.52_0.15_155)]",
  "hljs-addition": "text-[oklch(0.52_0.15_155)]",
  "hljs-number": "text-[oklch(0.58_0.16_50)]",
  "hljs-literal": "text-[oklch(0.58_0.16_50)]",
  "hljs-built_in": "text-[oklch(0.55_0.15_250)]",
  "hljs-title": "text-[oklch(0.55_0.15_250)]",
  "hljs-name": "text-[oklch(0.55_0.15_250)]",
  "hljs-section": "text-[oklch(0.55_0.15_250)]",
  "hljs-attr": "text-[oklch(0.55_0.12_200)]",
  "hljs-attribute": "text-[oklch(0.55_0.12_200)]",
  "hljs-variable": "text-[oklch(0.55_0.18_10)]",
  "hljs-template-variable": "text-[oklch(0.55_0.18_10)]",
  "hljs-deletion": "text-[oklch(0.55_0.18_10)] bg-[oklch(0.55_0.18_10/0.1)]",
  "hljs-meta": "text-[oklch(0.58_0.14_85)]",
  "hljs-tag": "text-foreground",
  "hljs-params": "text-foreground",
  "hljs-symbol": "text-[oklch(0.55_0.12_340)]",
  "hljs-bullet": "text-[oklch(0.55_0.12_340)]",
  "hljs-link": "text-[oklch(0.55_0.12_340)]",
  "hljs-regexp": "text-[oklch(0.55_0.15_330)]",
};

function tokenClassName(className: unknown): string | undefined {
  if (!Array.isArray(className)) return undefined;
  const classes = className
    .filter((name): name is string => typeof name === "string")
    .map((name) => TOKEN_CLASSES[name])
    .filter(Boolean);
  return classes.length > 0 ? classes.join(" ") : undefined;
}

/**
 * Builds React elements from lowlight's syntax tree. Lowlight only emits
 * text and `span` elements with token classes, so every element becomes a
 * `span` and no part of the code is ever parsed as HTML.
 */
function renderHast(nodes: readonly (RootContent | ElementContent)[]): ReactNode[] {
  return nodes.map((node, index) => {
    if (node.type === "text") return node.value;
    if (node.type === "element") {
      return (
        <span key={index} className={tokenClassName(node.properties.className)}>
          {renderHast(node.children)}
        </span>
      );
    }
    return null;
  });
}

function highlight(language: string | null, code: string): Root | null {
  if (!language || !chatCodeLowlight.registered(language)) return null;
  return chatCodeLowlight.highlight(language, code);
}

interface ChatCodeBlockProps {
  /** The fence's language, or null when the fence named none. */
  language: string | null;
  /** The raw code between the fences. */
  code: string;
}

/**
 * A fenced code block of a message: the language label, syntax highlighting
 * for the languages lowlight knows, a Copy button for the raw code, and a
 * collapse beyond 20 lines.
 */
export function ChatCodeBlock({ language, code }: ChatCodeBlockProps) {
  const t = useTranslations("chat.codeBlock");
  const tCopy = useTranslations("chat.copy");
  const [expanded, setExpanded] = useState(false);
  const [copied, setCopied] = useState(false);

  const tree = useMemo(() => highlight(language, code), [language, code]);
  const lineCount = useMemo(() => code.split("\n").length, [code]);
  const collapsible = lineCount > CHAT_CODE_COLLAPSED_LINES;
  const collapsed = collapsible && !expanded;

  // The button confirms only a copy that happened. A failed copy shows the
  // chat's copy error toast and leaves the button as it was.
  const onCopy = async () => {
    if (!navigator.clipboard?.writeText) {
      toast.error(tCopy("error"));
      return;
    }
    try {
      await navigator.clipboard.writeText(code);
    } catch (error) {
      console.warn("[chat] clipboard write failed", error);
      toast.error(tCopy("error"));
      return;
    }
    toast.success(tCopy("code"));
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="bg-muted/70 border-border my-1 max-w-full overflow-hidden rounded-md border">
      <div className="border-border flex items-center justify-between gap-2 border-b py-0.5 pr-1 pl-3">
        <span className="text-muted-foreground truncate font-mono text-[11px]">{language ?? t("plain")}</span>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="text-muted-foreground h-6 px-2 text-[11px]"
          aria-label={t("copy")}
          onClick={() => void onCopy()}
        >
          {copied ? <Check /> : <Copy />}
          {copied ? t("copied") : t("copy")}
        </Button>
      </div>
      <pre
        data-language={language ?? undefined}
        className={cn(
          "max-w-full overflow-x-auto px-3 py-2 font-mono text-xs leading-5",
          // Twenty lines of `leading-5` plus the vertical padding.
          collapsed && "max-h-[26rem] overflow-y-hidden",
        )}
      >
        <code>{tree ? renderHast(tree.children) : code}</code>
      </pre>
      {collapsible && (
        <button
          type="button"
          className="text-primary border-border w-full border-t px-3 py-1 text-left text-xs hover:underline"
          aria-expanded={expanded}
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? t("showLess") : t("showMore", { count: lineCount })}
        </button>
      )}
    </div>
  );
}
