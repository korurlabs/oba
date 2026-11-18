import {
  CHAT_MAX_FILES,
  CHAT_MAX_REQUEST_BYTES,
  type ChatAttachmentResponse,
  type ChatLocalFile,
} from "@/lib/schemas/chat";

/*
 * How chat files are staged, laid out and saved, without React or native
 * modules, so the rules can be tested alone.
 */

/** Types the app draws in the message list and in the viewer; others open as a file. */
const PREVIEWABLE_IMAGE_TYPES: ReadonlySet<string> = new Set([
  "image/png",
  "image/jpeg",
  "image/jpg",
]);

/** The media type without parameters, lower case; empty when unknown. */
function baseContentType(contentType: string | null | undefined): string {
  return (contentType ?? "").split(";")[0]?.trim().toLowerCase() ?? "";
}

/** True for an image the app draws itself: PNG and JPEG. */
export function isChatPreviewableImage(contentType: string | null | undefined): boolean {
  return PREVIEWABLE_IMAGE_TYPES.has(baseContentType(contentType));
}

/** True for any image type; a download of one goes to the photo library. */
export function isChatImageType(contentType: string | null | undefined): boolean {
  return baseContentType(contentType).startsWith("image/");
}

/** A byte count as B, KB or MB with one decimal; empty when unknown. */
export function formatChatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export interface ChatImageBox {
  width: number;
  height: number;
}

/** Width over height of an image whose dimensions are unknown: a 4:3 box. */
const UNKNOWN_ASPECT = 4 / 3;

/**
 * The box an image takes in the list, from its stored dimensions: as large
 * as it fits in `max` without growing past its own size, the aspect kept.
 * Without dimensions it gets a 4:3 box of the full width, so the row does
 * not jump when the image arrives.
 */
export function chatImageBox(
  width: number | null | undefined,
  height: number | null | undefined,
  max: ChatImageBox
): ChatImageBox {
  if (!width || !height || width <= 0 || height <= 0) {
    const boxWidth: number = Math.min(max.width, max.height * UNKNOWN_ASPECT);
    return { width: Math.round(boxWidth), height: Math.round(boxWidth / UNKNOWN_ASPECT) };
  }
  const scale: number = Math.min(1, max.width / width, max.height / height);
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

/** The images of a message the list draws, and the files it lists by name. */
export function splitChatAttachments(attachments: readonly ChatAttachmentResponse[]): {
  images: ChatAttachmentResponse[];
  files: ChatAttachmentResponse[];
} {
  const images: ChatAttachmentResponse[] = [];
  const files: ChatAttachmentResponse[] = [];
  for (const attachment of attachments) {
    (isChatPreviewableImage(attachment.contentType) ? images : files).push(attachment);
  }
  return { images, files };
}

/** Why the staged files cannot be sent, or null when they can. */
export type ChatFilesProblem = "tooManyFiles" | "filesTooLarge";

/**
 * The server's limits on one send: at most `CHAT_MAX_FILES` files, and the
 * request at most `CHAT_MAX_REQUEST_BYTES`. A file of unknown size counts as
 * nothing here; the server still refuses an oversized request.
 */
export function chatFilesProblem(files: readonly ChatLocalFile[]): ChatFilesProblem | null {
  if (files.length > CHAT_MAX_FILES) return "tooManyFiles";
  const total: number = files.reduce((sum, file) => sum + (file.size ?? 0), 0);
  return total > CHAT_MAX_REQUEST_BYTES ? "filesTooLarge" : null;
}

/** An image from the camera or the photo library, as `expo-image-picker` returns it. */
export interface ChatPickedImage {
  uri: string;
  fileName?: string | null;
  fileSize?: number;
  mimeType?: string;
  width: number;
  height: number;
}

/** A file from the files app, as `expo-document-picker` returns it. */
export interface ChatPickedDocument {
  uri: string;
  name: string;
  size?: number;
  mimeType?: string;
}

const EXTENSION_TYPES: Readonly<Record<string, string>> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  gif: "image/gif",
  webp: "image/webp",
  heic: "image/heic",
};

/** The last path segment of a URI, without a query. */
function uriFileName(uri: string): string {
  const path: string = uri.split("?")[0] ?? uri;
  return decodeURIComponent(path.slice(path.lastIndexOf("/") + 1));
}

function extensionOf(name: string): string {
  const dot: number = name.lastIndexOf(".");
  return dot < 0 ? "" : name.slice(dot + 1).toLowerCase();
}

/**
 * A picked photo as a file to send. The camera gives no name, so the name
 * comes from the file the picker wrote; the type, when the picker gives
 * none, from the extension. Zero dimensions mean unknown.
 */
export function chatLocalFileFromImage(image: ChatPickedImage, key: string): ChatLocalFile {
  const name: string = image.fileName?.trim() || uriFileName(image.uri) || `photo-${key}.jpg`;
  return {
    key,
    uri: image.uri,
    name,
    mimeType: image.mimeType ?? EXTENSION_TYPES[extensionOf(name)] ?? null,
    size: image.fileSize ?? null,
    width: image.width > 0 ? image.width : null,
    height: image.height > 0 ? image.height : null,
  };
}

/** A picked document as a file to send. */
export function chatLocalFileFromDocument(
  document: ChatPickedDocument,
  key: string
): ChatLocalFile {
  return {
    key,
    uri: document.uri,
    name: document.name || uriFileName(document.uri),
    mimeType: document.mimeType ?? EXTENSION_TYPES[extensionOf(document.name)] ?? null,
    size: document.size ?? null,
    width: null,
    height: null,
  };
}

/**
 * A file name safe to write: path separators and characters file systems
 * refuse become `_`, and an empty name becomes "file".
 */
export function chatSafeFileName(fileName: string): string {
  const safe: string = fileName.replace(/[/\\:*?"<>|\u0000-\u001f]/g, "_").trim();
  return safe || "file";
}

/**
 * The name a download is cached under: the safe name after the
 * attachment's ID, which keeps two files of the same name apart.
 */
export function chatDownloadFileName(
  attachment: Pick<ChatAttachmentResponse, "id" | "fileName">
): string {
  return `${attachment.id}-${chatSafeFileName(attachment.fileName)}`;
}
