import MarkdownIt from "markdown-it";

import {
  attributeValue,
  elementsByTagName,
  parseHtmlBody,
} from "@/components/ticket/descriptionHtml";
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
 * HTML with markdown-it (raw HTML allowed), lets the browser parse that HTML
 * and keeps every `<img>` with a `src` that is not a `data:` URL. The viewer
 * opens "image n" of the description, so the list below has to hold exactly
 * those images in the same order: an image written inside code, a comment or
 * an HTML block's Markdown is shown as text there and must not be counted.
 * The same two steps run here, with the editor's markdown-it options and a
 * standards-compliant HTML parser, so the count follows the editor's rules
 * instead of a copy of them.
 */

/** markdown-it as the editor's Markdown extension configures it. */
const editorMarkdown: MarkdownIt = new MarkdownIt({ html: true, linkify: false, breaks: false });

/** A size attribute as a whole positive number of pixels, else null. */
function pixelSize(value: string | undefined): number | null {
  if (value === undefined || !/^\s*\d+\s*$/.test(value)) return null;
  const size: number = Number(value);
  return size > 0 ? size : null;
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
  const body = parseHtmlBody(editorMarkdown.render(description));
  const images: DescriptionImageSource[] = [];
  for (const image of elementsByTagName(body, "img")) {
    const src: string | undefined = attributeValue(image, "src");
    if (src === undefined || src.startsWith("data:")) continue;
    images.push({
      src,
      alt: attributeValue(image, "alt") ?? "",
      attachmentId: attributeValue(image, "data-attachment-id") || null,
      width: pixelSize(attributeValue(image, "width")),
      height: pixelSize(attributeValue(image, "height")),
    });
  }
  return images;
}
