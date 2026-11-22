"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { compareLines, type CompareRow } from "../lib/line-diff";

/** The Markdown source of a text, read-only, kept as written. */
export function MarkdownSource({ body }: { body: string }) {
  const t = useTranslations("history");
  if (body === "") {
    return <p className="text-muted-foreground text-sm">{t("emptyBody")}</p>;
  }
  return (
    <pre className="bg-muted/40 overflow-x-auto rounded-md border p-4 font-mono text-xs leading-relaxed whitespace-pre-wrap">
      {body}
    </pre>
  );
}

function CompareCell({
  text,
  kind,
  side,
}: {
  text: string | null;
  kind: CompareRow["kind"];
  side: "left" | "right";
}) {
  const t = useTranslations("history");
  const marked = kind !== "same" && text !== null;
  return (
    <div
      className={cn(
        "min-h-5 px-3 font-mono text-xs leading-5 break-words whitespace-pre-wrap",
        marked && side === "left" && "bg-error-light",
        marked && side === "right" && "bg-success-light"
      )}
    >
      {marked && <span className="sr-only">{side === "left" ? t("removed") : t("added")}: </span>}
      {text}
    </div>
  );
}

/**
 * The revision beside the current text of the same language, line by line:
 * lines the current text no longer has are marked on the left, lines it
 * gained on the right. Texts too long to compare are shown side by side
 * without marks.
 */
export function RevisionCompare({
  revisionBody,
  currentBody,
}: {
  revisionBody: string;
  currentBody: string;
}) {
  const t = useTranslations("history");
  const rows = useMemo(() => compareLines(revisionBody, currentBody), [revisionBody, currentBody]);

  const headings = (
    <div className="text-muted-foreground grid grid-cols-2 border-b text-xs font-semibold">
      <div className="px-3 py-2">{t("thisRevision")}</div>
      <div className="border-l px-3 py-2">{t("current")}</div>
    </div>
  );

  if (rows === null) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-muted-foreground text-sm" role="status">
          {t("compareTooLarge")}
        </p>
        <div className="overflow-hidden rounded-md border">
          {headings}
          <div className="grid grid-cols-2">
            <div className="py-2">
              <CompareCell text={revisionBody} kind="same" side="left" />
            </div>
            <div className="border-l py-2">
              <CompareCell text={currentBody} kind="same" side="right" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-md border" data-testid="revision-compare">
      {headings}
      <div className="py-2">
        {rows.map((row, index) => (
          <div key={index} className="grid grid-cols-2" data-kind={row.kind}>
            <CompareCell text={row.left} kind={row.kind} side="left" />
            <div className="border-l">
              <CompareCell text={row.right} kind={row.kind} side="right" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
