import { Extension, type AnyExtension, type Editor, type NodeViewRenderer } from "@tiptap/core";
import { CodeBlockLowlight } from "@tiptap/extension-code-block-lowlight";
import { Image } from "@tiptap/extension-image";
import { Link } from "@tiptap/extension-link";
import { Placeholder } from "@tiptap/extension-placeholder";
import { Table, TableCell, TableHeader, TableRow } from "@tiptap/extension-table";
import { TaskItem } from "@tiptap/extension-task-item";
import { TaskList } from "@tiptap/extension-task-list";
import type { Node as ProseMirrorNode } from "@tiptap/pm/model";
import { StarterKit } from "@tiptap/starter-kit";
import { common, createLowlight } from "lowlight";
import { Markdown } from "tiptap-markdown";

/**
 * The TipTap setup of the ticket description editor. It mirrors the web
 * editor (`@oba/shared/components/markdown-editor`) extension for extension,
 * in the same order and with the same options, because both editors write
 * the same stored description: a description saved on the phone must be the
 * text the web writes for the same edit, and the web must read it back
 * unchanged. The web file is not imported because it needs next-intl,
 * sonner and the Radix UI kit; the parity tests pin its output instead.
 *
 * Runs only inside the editor's DOM component and in tests, never in the
 * native bundle.
 */

const lowlight = createLowlight(common);

/** The languages the code block offers, in the web's order; "" is auto-detect. */
export const CODE_BLOCK_LANGUAGES = [
  { value: "", label: "auto" },
  { value: "plaintext", label: "plaintext" },
  { value: "javascript", label: "javascript" },
  { value: "typescript", label: "typescript" },
  { value: "html", label: "html" },
  { value: "css", label: "css" },
  { value: "json", label: "json" },
  { value: "xml", label: "xml" },
  { value: "java", label: "java" },
  { value: "python", label: "python" },
  { value: "sql", label: "sql" },
  { value: "bash", label: "bash" },
  { value: "yaml", label: "yaml" },
  { value: "markdown", label: "markdown" },
] as const;

/** The code block languages Format can pretty-print. */
export const FORMATTABLE_LANGUAGES: ReadonlySet<string> = new Set(["json", "xml", "html"]);

/** The language a new code block gets, as on the web. */
export const DEFAULT_CODE_BLOCK_LANGUAGE = "json";

function formatJson(code: string): string {
  const parsed: unknown = JSON.parse(code);
  return JSON.stringify(parsed, null, 2);
}

/** Puts each tag on its own line, indented by nesting depth (the web's formatter). */
function formatXmlHtml(code: string): string {
  const parts = code.trim().replace(/>\s*</g, ">\n<").split("\n");
  let indent = 0;
  const lines: string[] = [];

  for (const part of parts) {
    const trimmedPart = part.trim();
    if (!trimmedPart) continue;

    if (trimmedPart.startsWith("</")) {
      indent = Math.max(0, indent - 1);
    }

    lines.push("  ".repeat(indent) + trimmedPart);

    // An opening tag indents what follows, unless it closes itself, is a
    // declaration or comment, or is closed on the same line.
    if (
      trimmedPart.startsWith("<") &&
      !trimmedPart.startsWith("</") &&
      !trimmedPart.endsWith("/>") &&
      !trimmedPart.startsWith("<!") &&
      !trimmedPart.startsWith("<?")
    ) {
      const tagMatch = trimmedPart.match(/^<(\w+)/);
      if (tagMatch && !trimmedPart.includes(`</${tagMatch[1]}`)) {
        indent++;
      }
    }
  }

  return lines.join("\n");
}

/**
 * The code pretty-printed for its language, or null when the language has no
 * formatter. Throws when JSON does not parse; the caller shows that as the
 * Format button's error state.
 */
export function formatCode(code: string, language: string): string | null {
  const lang = language.toLowerCase();
  if (lang === "json") return formatJson(code);
  if (lang === "xml" || lang === "html") return formatXmlHtml(code);
  return null;
}

/** Hosts whose page title is well known, so `/link <url>` can name them. */
const KNOWN_LINK_HOSTS: Readonly<Record<string, string>> = {
  "github.com": "GitHub",
  "gitlab.com": "GitLab",
  "bitbucket.org": "Bitbucket",
  "stackoverflow.com": "Stack Overflow",
  "docs.google.com": "Google Docs",
  "drive.google.com": "Google Drive",
  "sheets.google.com": "Google Sheets",
  "slides.google.com": "Google Slides",
  "youtube.com": "YouTube",
  "figma.com": "Figma",
  "notion.so": "Notion",
  "linear.app": "Linear",
  "slack.com": "Slack",
  "discord.com": "Discord",
  "twitter.com": "Twitter",
  "x.com": "X",
  "linkedin.com": "LinkedIn",
  "medium.com": "Medium",
  "reddit.com": "Reddit",
  "wikipedia.org": "Wikipedia",
  "npm.js.com": "npm",
  "vercel.com": "Vercel",
  "netlify.com": "Netlify",
  "jira.atlassian.com": "Jira",
  "confluence.atlassian.com": "Confluence",
  "trello.com": "Trello",
};

/**
 * A readable name for a URL, from its host: the web's fallback when its page
 * title route fails. The app has no title route, so `/link <url>` always uses
 * this. An unparseable URL is its own title.
 */
export function linkTitleFromUrl(url: string): string {
  let hostname: string;
  try {
    hostname = new URL(url).hostname.replace(/^www\./, "");
  } catch (error) {
    // A malformed URL still becomes a link, titled with the text the user typed.
    console.warn("[descriptionEditor] link URL does not parse", error);
    return url;
  }

  for (const [domain, name] of Object.entries(KNOWN_LINK_HOSTS)) {
    if (hostname === domain || hostname.endsWith("." + domain)) {
      return name;
    }
  }

  const parts = hostname.split(".");
  const mainPart = parts.length >= 2 ? (parts[parts.length - 2] ?? parts[0]) : parts[0];
  if (!mainPart) return hostname;
  return mainPart.charAt(0).toUpperCase() + mainPart.slice(1);
}

/** `/link` or `/link <url>` at the end of the text before the caret. */
const SLASH_LINK_PATTERN = /\/link(?:\s+(https?:\/\/\S+))?\s*$/;

/**
 * Return after `/link <url>` replaces the command with a link titled after
 * the host; Return after `/link` alone removes it and opens the link row.
 */
function createSlashLinkExtension(onOpenLinkRow: () => void): Extension {
  return Extension.create({
    name: "slashLink",

    addKeyboardShortcuts() {
      return {
        Enter: ({ editor }) => {
          const { $from } = editor.state.selection;
          const textBefore = $from.parent.textContent.slice(0, $from.parentOffset);
          const match = textBefore.match(SLASH_LINK_PATTERN);
          if (!match) return false;

          const from = $from.pos - match[0].length;
          const url = match[1];
          const chain = editor.chain().focus().deleteRange({ from, to: $from.pos });

          if (url) {
            chain
              .insertContent({
                type: "text",
                text: linkTitleFromUrl(url),
                marks: [{ type: "link", attrs: { href: url } }],
              })
              .run();
            return true;
          }

          chain.run();
          onOpenLinkRow();
          return true;
        },
      };
    },
  });
}

/** The part of tiptap-markdown's serializer state the image serializer uses. */
interface MarkdownSerializerState {
  write(text: string): void;
  closeBlock(node: ProseMirrorNode): void;
}

function escapeAttr(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function hasDimension(value: unknown): value is string | number {
  return value !== null && value !== undefined && value !== "";
}

/**
 * Writes an image as a full `<img>` tag instead of `![alt](src)`, so the
 * stored description keeps the width and height (no layout jump while it
 * loads) and the attachment id (the save step rewrites pending images and
 * deletes removed attachments by it). Same attribute order as the web.
 *
 * The image is a block node, so the block is closed after the tag. Without it
 * the next block is written on the same line (`<img …>- [ ] task`), which
 * Markdown no longer reads as an HTML block: on the next load a list item or
 * heading there turns into escaped text.
 */
function serializeImage(state: MarkdownSerializerState, node: ProseMirrorNode): void {
  const src = (node.attrs.src as string | null | undefined) ?? "";
  const alt = (node.attrs.alt as string | null | undefined) ?? "";
  const title = (node.attrs.title as string | null | undefined) ?? "";
  const width: unknown = node.attrs.width;
  const height: unknown = node.attrs.height;
  const attachmentId = node.attrs["data-attachment-id"] as string | null | undefined;

  const attrs: string[] = [`src="${escapeAttr(src)}"`];
  if (alt) attrs.push(`alt="${escapeAttr(alt)}"`);
  if (title) attrs.push(`title="${escapeAttr(title)}"`);
  if (hasDimension(width)) attrs.push(`width="${escapeAttr(String(width))}"`);
  if (hasDimension(height)) attrs.push(`height="${escapeAttr(String(height))}"`);
  if (attachmentId) attrs.push(`data-attachment-id="${escapeAttr(attachmentId)}"`);
  attrs.push('loading="lazy"');
  attrs.push('decoding="async"');

  state.write(`<img ${attrs.join(" ")}>`);
  state.closeBlock(node);
}

export type DescriptionExtensionsOptions = {
  /** Shown in the empty editor. */
  placeholder: string;
  /** Opens the link row; called by `/link` then Return. */
  onOpenLinkRow: () => void;
  /** The image node view; the plain `<img>` when absent (tests). */
  imageView?: NodeViewRenderer;
  /** The code block node view; the plain `<pre>` when absent (tests). */
  codeBlockView?: NodeViewRenderer;
};

/** The description editor's extensions, in the web editor's order and configuration. */
export function createDescriptionExtensions({
  placeholder,
  onOpenLinkRow,
  imageView,
  codeBlockView,
}: DescriptionExtensionsOptions): AnyExtension[] {
  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3, 4, 5, 6] },
      codeBlock: false,
    }),
    CodeBlockLowlight.extend({
      ...(codeBlockView ? { addNodeView: () => codeBlockView } : {}),
    }).configure({
      lowlight,
      defaultLanguage: DEFAULT_CODE_BLOCK_LANGUAGE,
    }),
    // StarterKit 3 already carries Link; the web adds it again with these
    // options, and the second registration is kept so both editors build the
    // same schema and write the same Markdown.
    Link.configure({
      openOnClick: false,
      autolink: true,
      HTMLAttributes: {
        class: "text-primary underline cursor-pointer",
      },
    }),
    TaskList,
    TaskItem.configure({
      nested: true,
    }),
    Image.extend({
      addAttributes() {
        return {
          ...this.parent?.(),
          "data-attachment-id": {
            default: null,
            parseHTML: (element: HTMLElement) => element.getAttribute("data-attachment-id"),
            renderHTML: (attributes: Record<string, unknown>) =>
              attributes["data-attachment-id"]
                ? { "data-attachment-id": attributes["data-attachment-id"] }
                : {},
          },
          width: {
            default: null,
            parseHTML: (element: HTMLElement) => element.getAttribute("width"),
            renderHTML: (attributes: Record<string, unknown>) =>
              attributes.width ? { width: attributes.width } : {},
          },
          height: {
            default: null,
            parseHTML: (element: HTMLElement) => element.getAttribute("height"),
            renderHTML: (attributes: Record<string, unknown>) =>
              attributes.height ? { height: attributes.height } : {},
          },
        };
      },
      ...(imageView ? { addNodeView: () => imageView } : {}),
      addStorage() {
        return {
          ...this.parent?.(),
          markdown: {
            serialize: serializeImage,
            // markdown-it's own rules parse both `![alt](src)` and, with
            // `html: true` below, a stored `<img>` tag with all its attributes.
            parse: {},
          },
        };
      },
    }).configure({
      HTMLAttributes: {
        class: "max-w-full rounded-md my-2",
        loading: "lazy",
        decoding: "async",
      },
    }),
    // GFM tables survive an edit as pipe tables instead of being flattened.
    Table,
    TableRow,
    TableHeader,
    TableCell,
    Placeholder.configure({
      placeholder,
    }),
    Markdown.configure({
      // Raw HTML on, so a stored `<img>` keeps its attachment id and size.
      html: true,
      tightLists: true,
      bulletListMarker: "-",
      transformPastedText: true,
      transformCopiedText: true,
    }),
    createSlashLinkExtension(onOpenLinkRow),
  ];
}

/** The storage tiptap-markdown adds to the editor. */
interface MarkdownStorage {
  markdown: { getMarkdown(): string };
}

/** The editor's document as the Markdown the description stores. */
export function editorMarkdown(editor: Editor): string {
  return (editor.storage as unknown as MarkdownStorage).markdown.getMarkdown();
}

/**
 * The position of the image at `pos` among the document's images, counted in
 * document order, which is the order the image viewer pages through.
 */
export function imageIndexAt(doc: ProseMirrorNode, pos: number): number {
  let index = 0;
  let found = -1;
  doc.descendants((node, nodePos) => {
    if (found >= 0) return false;
    if (node.type.name === "image") {
      if (nodePos === pos) {
        found = index;
        return false;
      }
      index++;
    }
    return true;
  });
  return found;
}
