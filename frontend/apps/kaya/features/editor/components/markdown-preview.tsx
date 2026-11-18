import { Markdown } from "@/components/common/markdown";

import { splitFrontmatter, type FrontmatterField } from "./frontmatter";

/**
 * A Markdown file as it reads: its frontmatter as a table of fields, as
 * written, and its body rendered. Raw HTML in the file stays text.
 */
export function MarkdownPreview({ text }: { text: string }) {
  const { fields, body } = splitFrontmatter(text);
  return (
    <div className="flex min-w-0 flex-col gap-4 rounded-md border p-4" aria-label="Preview">
      {fields !== null && fields.length > 0 && (
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
      {body.trim() === "" ? (
        <p className="text-muted-foreground text-sm">The file has no body.</p>
      ) : (
        <Markdown source={body} />
      )}
    </div>
  );
}
