"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

import { Markdown } from "@/components/common/markdown";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import { FileViewer } from "@/features/versions/components/file-viewer";
import { useVersionFile } from "@/features/versions/hooks/use-versions";

import { isMarkdownPath } from "./editor-language";
import { frontmatterSummary } from "./file-detail-data";
import { splitFrontmatter, type FrontmatterField } from "./frontmatter";

/**
 * A Markdown file as it reads, its frontmatter closed to one line that opens
 * on a click, so the body starts at the top of the page.
 */
export function ReadPreview({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  const { fields, body } = splitFrontmatter(text);
  return (
    <div className="flex min-w-0 flex-col gap-3 rounded-md border p-4" aria-label="Preview">
      {fields !== null && fields.length > 0 && (
        <div className="flex flex-col gap-2">
          <button
            type="button"
            aria-expanded={open}
            onClick={() => setOpen(!open)}
            className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-xs"
          >
            {open ? (
              <ChevronDown className="size-3.5" aria-hidden />
            ) : (
              <ChevronRight className="size-3.5" aria-hidden />
            )}
            {frontmatterSummary(fields.length)}
          </button>
          {open && (
            <table className="w-full text-sm" aria-label="Frontmatter">
              <tbody>
                {fields.map((field: FrontmatterField) => (
                  <tr key={field.key} className="border-b last:border-0">
                    <th className="text-muted-foreground w-40 py-1 pr-3 text-left align-top font-mono text-xs font-normal">
                      {field.key}
                    </th>
                    <td className="py-1 font-mono text-xs break-words whitespace-pre-wrap">
                      {field.value}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {body.trim() === "" ? (
        <p className="text-muted-foreground text-sm">The file has no body.</p>
      ) : (
        <Markdown source={body} />
      )}
    </div>
  );
}

/**
 * The Content of a file's read view: a Markdown text file in the read
 * preview; a link, a binary file, any other file, and a file still loading
 * or failing to load, in the file viewer, which reads the same cached file.
 */
export function FileContent({ file }: { file: ScopeFile }) {
  const readable = file.mode !== "SYMLINK" && isMarkdownPath(file.path);
  const content = useVersionFile(readable ? file.versionId : "", file.path);
  const data = content.data?.data;
  if (readable && data && data.encoding !== "BASE64") {
    return <ReadPreview text={data.content ?? ""} />;
  }
  return <FileViewer file={file} view="preview" showHeader={false} />;
}
