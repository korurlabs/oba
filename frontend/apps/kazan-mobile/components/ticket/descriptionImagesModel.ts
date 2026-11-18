import { parseAttachmentIds } from "@/components/ticket/ticketAttachmentsModel";
import { PENDING_IMAGE_SRC_PREFIX, type PendingImage } from "@/lib/schemas/tickets";

/*
 * The images of a ticket description, without React or native modules, so
 * the rules can be tested alone. A description is saved in the form Kazan
 * web writes, so these functions mirror the web's helpers
 * (`commit-pending-images.ts`, `orphan-gc.ts`, `parse-description-images.ts`)
 * and the tests pin the same output.
 */

/** The `src` an image holds in the description until it is uploaded. */
export function pendingImageSrc(key: string): string {
  return `${PENDING_IMAGE_SRC_PREFIX}${key}`;
}

/** Whether a `src` is an image that is not uploaded yet. */
export function isPendingImageSrc(src: string): boolean {
  return src.startsWith(PENDING_IMAGE_SRC_PREFIX);
}

/** Escapes a text so a regular expression matches it literally. */
function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Escapes a text for a double-quoted HTML attribute value, as the web does. */
function escapeAttribute(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

/** Where an uploaded image is stored. */
export interface UploadedImage {
  id: string;
  url: string;
}

/**
 * Rewrites the `<img>` tag of a pending image once it is uploaded, as the
 * web does: the `src` becomes the attachment URL, ` data-attachment-id` is
 * appended at the end (or replaced when present), and width and height are
 * appended only when the tag lacks them. Every tag with the pending `src` is
 * rewritten, so an image copied within the editor is saved too; the web
 * rewrites the first only, which gives the same text for a single copy.
 * Other tags are left alone.
 */
export function rewritePendingImageTag(
  markdown: string,
  image: Pick<PendingImage, "src" | "width" | "height">,
  uploaded: UploadedImage
): string {
  const escapedSrc: string = escapeRegex(image.src);
  const tagPattern = new RegExp(`<img\\b([^>]*?\\ssrc="${escapedSrc}"[^>]*)>`, "gi");
  return markdown.replace(tagPattern, (_tag: string, rawAttributes: string) => {
    let attributes: string = rawAttributes.replace(
      new RegExp(`(\\ssrc=")${escapedSrc}(")`),
      `$1${escapeAttribute(uploaded.url)}$2`
    );
    const idAttribute = ` data-attachment-id="${escapeAttribute(uploaded.id)}"`;
    if (/\sdata-attachment-id="[^"]*"/i.test(attributes)) {
      attributes = attributes.replace(/\sdata-attachment-id="[^"]*"/i, idAttribute);
    } else {
      attributes += idAttribute;
    }
    if (!/\swidth="[^"]*"/i.test(attributes)) attributes += ` width="${image.width}"`;
    if (!/\sheight="[^"]*"/i.test(attributes)) attributes += ` height="${image.height}"`;
    return `<img${attributes}>`;
  });
}

/**
 * The pending images the description still shows, in the order they were
 * added. An image removed from the text, by its delete button, Backspace or
 * select-and-type, is left out, so it is never uploaded.
 */
export function reconcilePendingImages(
  markdown: string,
  images: readonly PendingImage[]
): PendingImage[] {
  return images.filter((image) => markdown.includes(`src="${image.src}"`));
}

/** The `src` of every image tag in the description that is not uploaded yet, in order. */
export function pendingImageSrcs(markdown: string): string[] {
  const srcs: string[] = [];
  const pattern = new RegExp(
    `<img\\b[^>]*?\\ssrc="(${escapeRegex(PENDING_IMAGE_SRC_PREFIX)}[^"]*)"`,
    "gi"
  );
  for (const match of markdown.matchAll(pattern)) {
    const src: string | undefined = match[1];
    if (src !== undefined) srcs.push(src);
  }
  return srcs;
}

/**
 * The attachments a save leaves behind: the ids of images the previous
 * description showed and the new one does not, each once. They are deleted
 * after the save so storage does not keep images nobody can see.
 */
export function orphanAttachmentIds(
  previous: string | null | undefined,
  next: string | null | undefined
): string[] {
  const kept: ReadonlySet<string> = new Set(parseAttachmentIds(next));
  return [...new Set(parseAttachmentIds(previous))].filter((id) => !kept.has(id));
}

/** Thrown when the description holds a pending image whose file is not known here any more. */
export class MissingPendingImageError extends Error {
  constructor(readonly srcs: readonly string[]) {
    super(`Description images without a file: ${srcs.join(", ")}`);
    this.name = "MissingPendingImageError";
  }
}

/** What committing the pending images of a description needs. */
export interface CommitPendingImagesOptions {
  markdown: string;
  /** Every pending image known to the editor, removed ones included, in the order added. */
  images: readonly PendingImage[];
  /** Uploads one image to the ticket; rejects on failure. */
  upload: (image: PendingImage) => Promise<UploadedImage>;
  /** Records an upload at once, so a later attempt does not upload the image again. */
  onUploaded: (key: string, uploaded: UploadedImage) => void;
  /** Called before each upload with its 1-based number and the count still to upload. */
  onProgress?: (current: number, total: number) => void;
}

/**
 * Uploads the description's pending images one by one and returns the text
 * with each tag rewritten to its attachment. Images already uploaded by an
 * earlier attempt are rewritten without a new upload. The first failure is
 * thrown, with the uploads before it recorded. A pending tag whose image is
 * unknown would be saved as a broken image, so it is refused instead.
 */
export async function commitPendingImages({
  markdown,
  images,
  upload,
  onUploaded,
  onProgress,
}: CommitPendingImagesOptions): Promise<string> {
  const present: PendingImage[] = reconcilePendingImages(markdown, images);
  const unknown: string[] = pendingImageSrcs(markdown).filter(
    (src) => !present.some((image) => image.src === src)
  );
  if (unknown.length > 0) throw new MissingPendingImageError([...new Set(unknown)]);

  const total: number = present.filter((image) => !image.uploaded).length;
  let current = 0;
  let committed: string = markdown;
  for (const image of present) {
    let uploaded: UploadedImage | undefined = image.uploaded;
    if (!uploaded) {
      current += 1;
      onProgress?.(current, total);
      uploaded = await upload(image);
      onUploaded(image.key, uploaded);
    }
    committed = rewritePendingImageTag(committed, image, uploaded);
  }
  return committed;
}

/** One image of a ticket description, as the viewer opens it. */
export interface DescriptionImageSource {
  /** The URL as the editor loads it; a signed storage link once read from the API. */
  src: string;
  /** The alternative text, or "". */
  alt: string;
  /** The `data-attachment-id` of an uploaded image, or null. */
  attachmentId: string | null;
  /** The stored pixel size, or null when the tag has none. */
  width: number | null;
  height: number | null;
}

/*
 * Finding the images the editor shows. The editor turns the description into
 * HTML with markdown-it (raw HTML allowed) and keeps every `<img>` with a
 * `src` that is not a `data:` URL. The viewer opens "image n" of the
 * description, so the list below has to hold exactly those images in the same
 * order: an image written inside code, a comment or an HTML block's Markdown
 * is shown as text there and must not be counted. The rules below follow
 * markdown-it's CommonMark rules for the parts that decide this: containers
 * (quotes, list items), fenced and indented code, HTML blocks, reference
 * definitions, code spans, escapes, inline HTML, links and images.
 */

/** A link reference definition, `[label]: destination`. */
type LinkReferences = Map<string, string>;

/** A run of description text the browser sees as Markdown inline content or as raw HTML. */
interface TextSegment {
  kind: "inline" | "html";
  text: string;
}

const ATTRIBUTE_NAME = "[a-zA-Z_:][a-zA-Z0-9:._-]*";
const ATTRIBUTE_VALUE = "(?:[^\"'=<>`\\x00-\\x20]+|'[^']*'|\"[^\"]*\")";
const ATTRIBUTE = `(?:\\s+${ATTRIBUTE_NAME}(?:\\s*=\\s*${ATTRIBUTE_VALUE})?)`;
const OPEN_TAG = `<[A-Za-z][A-Za-z0-9\\-]*${ATTRIBUTE}*\\s*\\/?>`;
const CLOSE_TAG = "<\\/[A-Za-z][A-Za-z0-9\\-]*\\s*>";

/** Raw HTML that markdown-it passes through inside a paragraph. */
const INLINE_HTML = new RegExp(
  `^(?:${OPEN_TAG}|${CLOSE_TAG}|<!---?>|<!--(?:[^-]|-[^-]|--[^>])*-->|<[?][\\s\\S]*?[?]>|<![A-Za-z][^>]*>|<!\\[CDATA\\[[\\s\\S]*?\\]\\]>)`
);
const AUTOLINK = /^<[a-zA-Z][a-zA-Z0-9+.-]{1,31}:[^<>\x00-\x20]*>/;
const EMAIL_AUTOLINK =
  /^<[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*>/;

const HTML_BLOCK_NAMES =
  "address|article|aside|base|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog|dir|div|dl|dt|fieldset|figcaption|figure|footer|form|frame|frameset|h1|h2|h3|h4|h5|h6|head|header|hr|html|iframe|legend|li|link|main|menu|menuitem|nav|noframes|ol|optgroup|option|p|param|search|section|summary|table|tbody|td|tfoot|th|thead|title|tr|track|ul";

/** HTML block starts: the start, the end (null: a blank line), and whether it may end a paragraph. */
const HTML_BLOCKS: readonly (readonly [RegExp, RegExp | null, boolean])[] = [
  [/^<(script|pre|style|textarea)(?=(\s|>|$))/i, /<\/(script|pre|style|textarea)>/i, true],
  [/^<!--/, /-->/, true],
  [/^<\?/, /\?>/, true],
  [/^<![A-Z]/, />/, true],
  [/^<!\[CDATA\[/, /\]\]>/, true],
  [new RegExp(`^</?(${HTML_BLOCK_NAMES})(?=(\\s|/?>|$))`, "i"), null, true],
  [new RegExp(`^(?:${OPEN_TAG}|${CLOSE_TAG})\\s*$`), null, false],
];

const BLOCK_QUOTE = /^ {0,3}> ?/;
const LIST_ITEM = /^( {0,3})([-+*]|(\d{1,9})[.)])( +|$)/;
const ATX_HEADING = /^ {0,3}#{1,6}(?: |$)/;
const THEMATIC_BREAK = /^ {0,3}(?:(?:\* *){3,}|(?:- *){3,}|(?:_ *){3,})$/;
const SETEXT_UNDERLINE = /^ {0,3}(?:=+|-+) *$/;
const FENCE_OPEN = /^ {0,3}(`{3,}|~{3,})(.*)$/;
const FENCE_CLOSE = /^ {0,3}(`{3,}|~{3,}) *$/;
const ASCII_PUNCTUATION = /^[!-/:-@[-`{-~]$/;

/** The `<img>` tags of raw HTML, and its comments, which hide what they hold. */
const HTML_IMAGE_TAG = /<img(?=[\s/>])(?:[^>"']|"[^"]*"|'[^']*')*>/gi;
const HTML_COMMENT = /<!--(?:>|->|[\s\S]*?(?:--!?>|$))/g;

/** An attribute of a tag: quoted, unquoted or without a value. */
const ATTRIBUTE_PATTERN = /[\s/]([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;

/** Characters a URL keeps as written; markdown-it percent-encodes every other one. */
const URL_SAFE = /^[A-Za-z0-9;/?:@&=+$,\-_.!~*'()#]$/;

function isBlank(line: string): boolean {
  return line.trim() === "";
}

function leadingSpaces(line: string): number {
  return line.length - line.replace(/^ +/, "").length;
}

/** Turns the tabs at the start of a line into spaces up to the next multiple of four. */
function expandLeadingTabs(line: string): string {
  let column = 0;
  let index = 0;
  let expanded = "";
  for (; index < line.length; index++) {
    const ch: string = line[index] ?? "";
    if (ch === " ") {
      expanded += " ";
      column += 1;
    } else if (ch === "\t") {
      const width: number = 4 - (column % 4);
      expanded += " ".repeat(width);
      column += width;
    } else {
      break;
    }
  }
  return expanded + line.slice(index);
}

/** The text of an attribute value or a Markdown destination; `&amp;` goes last so `&amp;lt;` stays `&lt;`. */
function decodeEntities(value: string): string {
  return value
    .replace(/&#(\d{1,7});/g, (entity: string, code: string) => codePointText(Number(code), entity))
    .replace(/&#[xX]([0-9a-fA-F]{1,6});/g, (entity: string, code: string) =>
      codePointText(parseInt(code, 16), entity)
    )
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&");
}

function codePointText(code: number, entity: string): string {
  if (code === 38) return entity;
  return code > 0 && code <= 0x10ffff && (code < 0xd800 || code > 0xdfff)
    ? String.fromCodePoint(code)
    : "�";
}

/** Backslash escapes and entities resolved, as markdown-it reads a destination or a label. */
function unescapeMarkdown(value: string): string {
  return decodeEntities(value.replace(/\\([!-/:-@[-`{-~])/g, "$1"));
}

/** A destination as the editor loads it: characters a URL may not hold are percent-encoded. */
function normalizeUrl(url: string): string {
  let encoded = "";
  for (let index = 0; index < url.length; index++) {
    const ch: string = url[index] ?? "";
    if (
      URL_SAFE.test(ch) ||
      (ch === "%" && /^[0-9a-fA-F]{2}$/.test(url.slice(index + 1, index + 3)))
    ) {
      encoded += ch;
      continue;
    }
    const code: number = url.charCodeAt(index);
    if (code >= 0xd800 && code <= 0xdbff) {
      const next: number = url.charCodeAt(index + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        encoded += encodeURIComponent(url.slice(index, index + 2));
        index += 1;
        continue;
      }
    }
    encoded += code >= 0xd800 && code <= 0xdfff ? "%EF%BF%BD" : encodeURIComponent(ch);
  }
  return encoded;
}

/** markdown-it refuses script-capable protocols, and `data:` other than common images. */
function isAllowedUrl(url: string): boolean {
  const lower: string = url.trim().toLowerCase();
  return /^(vbscript|javascript|file|data):/.test(lower)
    ? /^data:image\/(gif|png|jpeg|webp);/.test(lower)
    : true;
}

/** The key a reference label is looked up by: trimmed, inner whitespace collapsed, case folded. */
function referenceKey(label: string): string {
  return label.trim().replace(/\s+/g, " ").toLowerCase().toUpperCase();
}

/** The end of a code span starting at `start`, or the end of its backtick run when it is not closed. */
function codeSpanEnd(src: string, start: number): number {
  let runEnd: number = start;
  while (src[runEnd] === "`") runEnd++;
  const length: number = runEnd - start;
  let pos: number = runEnd;
  while (pos < src.length) {
    const next: number = src.indexOf("`", pos);
    if (next < 0) break;
    let closeEnd: number = next;
    while (src[closeEnd] === "`") closeEnd++;
    if (closeEnd - next === length) return closeEnd;
    pos = closeEnd;
  }
  return runEnd;
}

/** The length of an autolink or a raw HTML tag at `pos`, or 0. */
function angleTokenLength(src: string, pos: number): number {
  const rest: string = src.slice(pos);
  const match: RegExpExecArray | null =
    AUTOLINK.exec(rest) ?? EMAIL_AUTOLINK.exec(rest) ?? INLINE_HTML.exec(rest);
  return match ? match[0].length : 0;
}

/** The position after the inline token at `pos`, skipped whole so its brackets do not count. */
function skipInlineToken(src: string, pos: number): number {
  const ch: string | undefined = src[pos];
  if (ch === "\\" && pos + 1 < src.length) return pos + 2;
  if (ch === "`") return codeSpanEnd(src, pos);
  if (ch === "<") return pos + Math.max(1, angleTokenLength(src, pos));
  return pos + 1;
}

/** The position of the `]` that closes the label opened at `open`, or -1. */
function linkLabelEnd(src: string, open: number): number {
  let level = 1;
  let pos: number = open + 1;
  while (pos < src.length) {
    const ch: string | undefined = src[pos];
    if (ch === "]") {
      level -= 1;
      if (level === 0) return pos;
      pos += 1;
    } else if (ch === "[") {
      level += 1;
      pos += 1;
    } else {
      pos = skipInlineToken(src, pos);
    }
  }
  return -1;
}

interface ParsedPart {
  ok: boolean;
  pos: number;
  text: string;
}

const FAILED: ParsedPart = { ok: false, pos: 0, text: "" };

/** A link destination at `start`: `<…>` or a run without spaces and with balanced parentheses. */
function linkDestination(src: string, start: number): ParsedPart {
  let pos: number = start;
  if (src[pos] === "<") {
    pos += 1;
    while (pos < src.length) {
      const ch: string | undefined = src[pos];
      if (ch === "\n" || ch === "<") return FAILED;
      if (ch === ">")
        return { ok: true, pos: pos + 1, text: unescapeMarkdown(src.slice(start + 1, pos)) };
      pos += ch === "\\" && pos + 1 < src.length ? 2 : 1;
    }
    return FAILED;
  }
  let level = 0;
  while (pos < src.length) {
    const code: number = src.charCodeAt(pos);
    if (code === 0x20 || code < 0x20 || code === 0x7f) break;
    if (code === 0x5c && pos + 1 < src.length) {
      if (src.charCodeAt(pos + 1) === 0x20) break;
      pos += 2;
      continue;
    }
    if (code === 0x28) {
      level += 1;
      if (level > 32) return FAILED;
    }
    if (code === 0x29) {
      if (level === 0) break;
      level -= 1;
    }
    pos += 1;
  }
  if (start === pos || level !== 0) return FAILED;
  return { ok: true, pos, text: unescapeMarkdown(src.slice(start, pos)) };
}

/** A link title at `start`: `"…"`, `'…'` or `(…)`. */
function linkTitle(src: string, start: number): ParsedPart {
  const open: string | undefined = src[start];
  if (open !== '"' && open !== "'" && open !== "(") return FAILED;
  const close: string = open === "(" ? ")" : open;
  let pos: number = start + 1;
  while (pos < src.length) {
    const ch: string | undefined = src[pos];
    if (ch === close) return { ok: true, pos: pos + 1, text: src.slice(start + 1, pos) };
    if (ch === "(" && close === ")") return FAILED;
    pos += ch === "\\" && pos + 1 < src.length ? 2 : 1;
  }
  return FAILED;
}

function skipWhitespace(src: string, pos: number): number {
  let next: number = pos;
  while (next < src.length && /[ \t\n]/.test(src[next] ?? "")) next++;
  return next;
}

/** A link or image whose label opens at `open`: its label end, its end and its URL. */
interface ParsedLink {
  labelEnd: number;
  end: number;
  href: string;
}

/** Parses `[label](destination "title")`, `[label][ref]`, `[ref][]` or `[ref]` as markdown-it does. */
function parseLink(src: string, open: number, references: LinkReferences): ParsedLink | null {
  const labelEnd: number = linkLabelEnd(src, open);
  if (labelEnd < 0) return null;
  let pos: number = labelEnd + 1;
  if (src[pos] === "(") {
    pos = skipWhitespace(src, pos + 1);
    if (pos >= src.length) return null;
    let href = "";
    const destination: ParsedPart = linkDestination(src, pos);
    if (destination.ok) {
      const normalized: string = normalizeUrl(destination.text);
      if (isAllowedUrl(normalized)) {
        href = normalized;
        pos = destination.pos;
      }
    }
    const beforeTitle: number = pos;
    pos = skipWhitespace(src, pos);
    const title: ParsedPart = linkTitle(src, pos);
    if (pos < src.length && beforeTitle !== pos && title.ok) {
      pos = skipWhitespace(src, title.pos);
    }
    if (src[pos] !== ")") return null;
    return { labelEnd, end: pos + 1, href };
  }
  let label: string | undefined;
  if (src[pos] === "[") {
    const refEnd: number = linkLabelEnd(src, pos);
    if (refEnd >= 0) {
      label = src.slice(pos + 1, refEnd);
      pos = refEnd + 1;
    } else {
      pos = labelEnd + 1;
    }
  }
  if (!label) label = src.slice(open + 1, labelEnd);
  const href: string | undefined = references.get(referenceKey(label));
  return href === undefined ? null : { labelEnd, end: pos, href };
}

/** A size attribute as a whole positive number of pixels, else null. */
function pixelSize(value: string | undefined): number | null {
  if (value === undefined || !/^\s*\d+\s*$/.test(value)) return null;
  const size: number = Number(value);
  return size > 0 ? size : null;
}

/** The attributes after a tag's name; the browser keeps the first of a repeated name. */
function tagAttributes(attributesText: string): Map<string, string> {
  const attributes: Map<string, string> = new Map<string, string>();
  for (const match of attributesText.matchAll(ATTRIBUTE_PATTERN)) {
    const name: string = (match[1] ?? "").toLowerCase();
    if (!attributes.has(name)) {
      attributes.set(name, decodeEntities(match[2] ?? match[3] ?? match[4] ?? ""));
    }
  }
  return attributes;
}

/** Adds an `<img>` tag the editor keeps: one with a `src` that is not a `data:` URL. */
function pushImageTag(tag: string, images: DescriptionImageSource[]): void {
  const attributes: Map<string, string> = tagAttributes(tag.slice(4));
  const src: string | undefined = attributes.get("src");
  if (src === undefined || src.startsWith("data:")) return;
  images.push({
    src,
    alt: attributes.get("alt") ?? "",
    attachmentId: attributes.get("data-attachment-id") || null,
    width: pixelSize(attributes.get("width")),
    height: pixelSize(attributes.get("height")),
  });
}

/** The images of raw HTML; a comment hides the tags in it. */
function scanHtml(html: string, images: DescriptionImageSource[]): void {
  for (const match of html.replace(HTML_COMMENT, "").matchAll(HTML_IMAGE_TAG)) {
    pushImageTag(match[0], images);
  }
}

/** The images of Markdown inline content: Markdown images and inline `<img>` tags, outside code. */
function scanInline(
  src: string,
  references: LinkReferences,
  images: DescriptionImageSource[]
): void {
  let pos = 0;
  while (pos < src.length) {
    const ch: string | undefined = src[pos];
    if (ch === "\\") {
      pos += ASCII_PUNCTUATION.test(src[pos + 1] ?? "") ? 2 : 1;
    } else if (ch === "`") {
      pos = codeSpanEnd(src, pos);
    } else if (ch === "<") {
      const rest: string = src.slice(pos);
      const autolink: RegExpExecArray | null = AUTOLINK.exec(rest) ?? EMAIL_AUTOLINK.exec(rest);
      const html: RegExpExecArray | null = autolink ? null : INLINE_HTML.exec(rest);
      if (html && /^<img(?=[\s/>])/i.test(html[0])) pushImageTag(html[0], images);
      pos += (autolink ?? html)?.[0].length ?? 1;
    } else if (ch === "!" && src[pos + 1] === "[") {
      const image: ParsedLink | null = parseLink(src, pos + 1, references);
      if (!image) {
        pos += 1;
        continue;
      }
      if (!image.href.startsWith("data:")) {
        images.push({
          src: image.href,
          alt: unescapeMarkdown(src.slice(pos + 2, image.labelEnd)),
          attachmentId: null,
          width: null,
          height: null,
        });
      }
      pos = image.end;
    } else if (ch === "[") {
      const link: ParsedLink | null = parseLink(src, pos, references);
      if (!link) {
        pos += 1;
        continue;
      }
      scanInline(src.slice(pos + 1, link.labelEnd), references, images);
      pos = link.end;
    } else {
      pos += 1;
    }
  }
}

/**
 * Takes the reference definitions off the start of a paragraph into
 * `references` (the first definition of a label wins) and returns the rest.
 */
function takeReferenceDefinitions(paragraph: string, references: LinkReferences): string {
  let rest: string = paragraph;
  for (;;) {
    const start: number = leadingSpaces(rest);
    if (start > 3 || rest[start] !== "[") return rest;
    const labelEnd: number = linkLabelEnd(rest, start);
    if (labelEnd < 0 || rest[labelEnd + 1] !== ":") return rest;
    const label: string = rest.slice(start + 1, labelEnd);
    if (isBlank(label) || label.length > 999) return rest;
    const destination: ParsedPart = linkDestination(rest, skipWhitespace(rest, labelEnd + 2));
    if (!destination.ok) return rest;
    const href: string = normalizeUrl(destination.text);
    if (!isAllowedUrl(href)) return rest;
    const lineEnd = (from: number): number => {
      let pos: number = from;
      while (rest[pos] === " " || rest[pos] === "\t") pos++;
      if (pos >= rest.length) return rest.length;
      return rest[pos] === "\n" ? pos + 1 : -1;
    };
    const titleStart: number = skipWhitespace(rest, destination.pos);
    const title: ParsedPart = titleStart > destination.pos ? linkTitle(rest, titleStart) : FAILED;
    let end: number = title.ok ? lineEnd(title.pos) : -1;
    if (end < 0) end = lineEnd(destination.pos);
    if (end < 0) return rest;
    const key: string = referenceKey(label);
    if (!references.has(key)) references.set(key, href);
    rest = rest.slice(end);
  }
}

type BlockContainer = { kind: "quote" } | { kind: "item"; indent: number };

type OpenLeaf =
  | { kind: "none" }
  | { kind: "paragraph"; lines: string[] }
  | { kind: "fence"; marker: string; length: number }
  | { kind: "indentedCode" }
  | { kind: "html"; end: RegExp | null; lines: string[] };

/** A list item that starts the line: its content indent and the rest of the line. */
function listItemStart(
  line: string,
  interruptsParagraph: boolean
): { indent: number; rest: string } | null {
  const match: RegExpExecArray | null = LIST_ITEM.exec(line);
  if (!match) return null;
  const markerEnd: number = (match[1] ?? "").length + (match[2] ?? "").length;
  const spaces: number = (match[4] ?? "").length;
  const content: string = line.slice(markerEnd + spaces);
  if (interruptsParagraph) {
    if (isBlank(content)) return null;
    if (match[3] !== undefined && Number(match[3]) !== 1) return null;
  }
  const indent: number = spaces === 0 || spaces > 4 ? markerEnd + 1 : markerEnd + spaces;
  return { indent, rest: line.slice(Math.min(indent, line.length)) };
}

function openFence(line: string): { marker: string; length: number } | null {
  const match: RegExpExecArray | null = FENCE_OPEN.exec(line);
  const run: string = match?.[1] ?? "";
  if (!match || (run.startsWith("`") && (match[2] ?? "").includes("`"))) return null;
  return { marker: run.charAt(0), length: run.length };
}

function htmlBlockStart(line: string, interruptsParagraph: boolean): RegExp | null | undefined {
  if (leadingSpaces(line) > 3) return undefined;
  const text: string = line.trimStart();
  const block = HTML_BLOCKS.find(([start]) => start.test(text));
  if (!block || (interruptsParagraph && !block[2])) return undefined;
  return block[1];
}

/** Whether a line starts a block, so it cannot continue a paragraph lazily. */
function startsBlock(line: string, inList: boolean): boolean {
  if (leadingSpaces(line) > 3) return false;
  return (
    BLOCK_QUOTE.test(line) ||
    THEMATIC_BREAK.test(line) ||
    ATX_HEADING.test(line) ||
    openFence(line) !== null ||
    htmlBlockStart(line, true) !== undefined ||
    listItemStart(line, !inList) !== null
  );
}

/**
 * Splits a description into the runs the browser receives as Markdown inline
 * content (paragraphs, headings, table rows) and as raw HTML (HTML blocks), in
 * order, and collects the reference definitions. Code blocks are left out.
 */
function scanBlocks(markdown: string): { segments: TextSegment[]; references: LinkReferences } {
  const segments: TextSegment[] = [];
  const references: LinkReferences = new Map<string, string>();
  const containers: BlockContainer[] = [];
  let leaf: OpenLeaf = { kind: "none" };

  const closeLeaf = (): void => {
    if (leaf.kind === "paragraph") {
      const text: string = takeReferenceDefinitions(leaf.lines.join("\n"), references);
      if (!isBlank(text)) segments.push({ kind: "inline", text });
    } else if (leaf.kind === "html") {
      segments.push({ kind: "html", text: leaf.lines.join("\n") });
    }
    leaf = { kind: "none" };
  };

  for (const rawLine of markdown.replace(/\r\n?/g, "\n").split("\n")) {
    let rest: string = expandLeadingTabs(rawLine);
    let matched = 0;
    for (const container of containers) {
      if (container.kind === "quote") {
        const quote: RegExpExecArray | null = BLOCK_QUOTE.exec(rest);
        if (!quote) break;
        rest = rest.slice(quote[0].length);
      } else if (isBlank(rest)) {
        rest = "";
      } else if (leadingSpaces(rest) >= container.indent) {
        rest = rest.slice(container.indent);
      } else {
        break;
      }
      matched += 1;
    }

    if (matched < containers.length) {
      const inList: boolean = containers.some((container) => container.kind === "item");
      if (leaf.kind === "paragraph" && !isBlank(rest) && !startsBlock(rest, inList)) {
        leaf.lines.push(rest);
        continue;
      }
      closeLeaf();
      containers.length = matched;
    }

    if (leaf.kind === "fence") {
      const close: RegExpExecArray | null = FENCE_CLOSE.exec(rest);
      const run: string = close?.[1] ?? "";
      if (run.startsWith(leaf.marker) && run.length >= leaf.length) leaf = { kind: "none" };
      continue;
    }
    if (leaf.kind === "html") {
      if (leaf.end === null) {
        if (isBlank(rest)) closeLeaf();
        else leaf.lines.push(rest);
      } else {
        leaf.lines.push(rest);
        if (leaf.end.test(rest)) closeLeaf();
      }
      continue;
    }
    if (leaf.kind === "indentedCode") {
      if (isBlank(rest) || leadingSpaces(rest) >= 4) continue;
      leaf = { kind: "none" };
    }

    for (;;) {
      if (leadingSpaces(rest) > 3) break;
      const quote: RegExpExecArray | null = BLOCK_QUOTE.exec(rest);
      if (quote) {
        closeLeaf();
        containers.push({ kind: "quote" });
        rest = rest.slice(quote[0].length);
        continue;
      }
      if (THEMATIC_BREAK.test(rest)) break;
      const item = listItemStart(rest, leaf.kind === "paragraph");
      if (!item) break;
      closeLeaf();
      containers.push({ kind: "item", indent: item.indent });
      rest = item.rest;
    }

    if (isBlank(rest)) {
      if (leaf.kind === "paragraph") closeLeaf();
      continue;
    }
    if (leaf.kind === "paragraph") {
      if (leadingSpaces(rest) > 3) {
        leaf.lines.push(rest);
        continue;
      }
      if (SETEXT_UNDERLINE.test(rest)) {
        closeLeaf();
        continue;
      }
      const interrupts: boolean =
        ATX_HEADING.test(rest) ||
        THEMATIC_BREAK.test(rest) ||
        openFence(rest) !== null ||
        htmlBlockStart(rest, true) !== undefined;
      if (!interrupts) {
        leaf.lines.push(rest);
        continue;
      }
      closeLeaf();
    }

    if (leadingSpaces(rest) > 3) {
      leaf = { kind: "indentedCode" };
      continue;
    }
    const fence = openFence(rest);
    if (fence) {
      leaf = { kind: "fence", ...fence };
      continue;
    }
    if (ATX_HEADING.test(rest)) {
      segments.push({ kind: "inline", text: rest });
      continue;
    }
    if (THEMATIC_BREAK.test(rest)) continue;
    const htmlEnd: RegExp | null | undefined = htmlBlockStart(rest, false);
    if (htmlEnd !== undefined) {
      leaf = { kind: "html", end: htmlEnd, lines: [rest] };
      if (htmlEnd !== null && htmlEnd.test(rest)) closeLeaf();
      continue;
    }
    leaf = { kind: "paragraph", lines: [rest] };
  }
  closeLeaf();
  return { segments, references };
}

/**
 * Every image the description editor shows, in document order, so the
 * position of an image in the rendered description is its `index` in the
 * viewer's link. Kazan web writes each uploaded image as an `<img>` tag with
 * its attachment id and size; older or pasted text may hold a Markdown image
 * instead, inline or by reference. Images inside code, HTML comments or
 * escaped are text in the editor and are not listed; neither is an `<img>`
 * without a `src` or with a `data:` URL, which the editor drops.
 */
export function descriptionImageSources(
  description: string | null | undefined
): DescriptionImageSource[] {
  if (!description) return [];
  const { segments, references } = scanBlocks(description);
  const images: DescriptionImageSource[] = [];
  for (const segment of segments) {
    if (segment.kind === "html") scanHtml(segment.text, images);
    else scanInline(segment.text, references, images);
  }
  return images;
}
