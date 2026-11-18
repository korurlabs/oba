import { randomUUID } from "expo-crypto";
import { File, Paths } from "expo-file-system";
import { Image } from "expo-image";

import type { ChatLocalFile } from "@/lib/schemas/chat";

/*
 * Images pasted into the app, from a text field's Paste or from the
 * clipboard, turned into files the app can stage and upload. Each is copied
 * into the app's cache under its own name, because the paste's temporary
 * file may be gone by the time a failed send is retried.
 */

/** Image types by file extension, as the paste and the clipboard write them. */
const PASTED_IMAGE_TYPES: Readonly<Record<string, string>> = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  webp: "image/webp",
  heic: "image/heic",
  heif: "image/heif",
  tif: "image/tiff",
  tiff: "image/tiff",
};

/** The extension a pasted image of each type is saved under. */
const PASTED_IMAGE_EXTENSIONS: Readonly<Record<string, string>> = {
  "image/png": "png",
  "image/jpeg": "jpg",
  "image/jpg": "jpg",
  "image/gif": "gif",
  "image/webp": "webp",
  "image/heic": "heic",
  "image/heif": "heif",
  "image/tiff": "tiff",
};

/** A data URI with a base64 payload, as the clipboard returns an image. */
const DATA_URI = /^data:([^;,]+)?(?:;[^,]*)?;base64,(.*)$/s;

/**
 * The extension of the last path segment of `uri`, lower case, without a
 * query; empty when it has none or one no file system would accept.
 */
export function pastedImageExtension(uri: string): string {
  const path: string = uri.split(/[?#]/)[0] ?? uri;
  const name: string = path.slice(path.lastIndexOf("/") + 1);
  const dot: number = name.lastIndexOf(".");
  if (dot < 0) return "";
  const extension: string = name.slice(dot + 1).toLowerCase();
  return /^[a-z0-9]{1,8}$/.test(extension) ? extension : "";
}

/** The media type of a pasted image file by its extension, or null when unknown. */
export function pastedImageType(extension: string): string | null {
  return PASTED_IMAGE_TYPES[extension.toLowerCase()] ?? null;
}

/** The name a pasted image is cached and uploaded under: `pasted-{key}.{ext}`. */
export function pastedImageName(key: string, extension: string): string {
  return extension ? `pasted-${key}.${extension}` : `pasted-${key}`;
}

/** The media type and the bare base64 of `base64`, which may be a data URI. */
export function parseBase64Image(
  base64: string,
  mimeType: string | null
): { mimeType: string | null; base64: string } {
  const match: RegExpMatchArray | null = DATA_URI.exec(base64);
  if (!match) return { mimeType: mimeType?.toLowerCase() ?? null, base64 };
  return { mimeType: (match[1] ?? mimeType)?.toLowerCase() ?? null, base64: match[2] ?? "" };
}

/** Width and height of the image at `uri`, or null when it cannot be decoded. */
async function imageSize(uri: string): Promise<{ width: number; height: number } | null> {
  try {
    const image = await Image.loadAsync(uri);
    return image.width > 0 && image.height > 0
      ? { width: image.width, height: image.height }
      : null;
  } catch (error: unknown) {
    // reason: dimensions only size the preview box before the upload; an image
    // without them is still staged and drawn in a 4:3 box, so the paste goes on.
    console.warn(
      "[paste] reading image dimensions failed",
      error instanceof Error ? error.message : error
    );
    return null;
  }
}

/**
 * Copies one pasted image into the cache and describes it as a file to
 * stage. A copy failure is thrown to the caller.
 */
export async function pastedImageFile(uri: string): Promise<ChatLocalFile> {
  const key: string = randomUUID();
  const extension: string = pastedImageExtension(uri);
  const target = new File(Paths.cache, pastedImageName(key, extension));
  new File(uri).copy(target);
  const size: { width: number; height: number } | null = await imageSize(target.uri);
  return {
    key,
    uri: target.uri,
    name: target.name,
    mimeType: pastedImageType(extension),
    size: target.size,
    width: size?.width ?? null,
    height: size?.height ?? null,
  };
}

/** The pasted images that could be copied, and how many could not. */
export interface PastedImageFiles {
  files: ChatLocalFile[];
  failed: number;
}

/**
 * Copies each image a text field's Paste gave into the cache, in order. An
 * image that cannot be copied is left out and counted, so the caller can
 * stage the others and tell the user about the rest.
 */
export async function pastedImageFiles(uris: readonly string[]): Promise<PastedImageFiles> {
  const files: ChatLocalFile[] = [];
  let failed = 0;
  for (const uri of uris) {
    try {
      files.push(await pastedImageFile(uri));
    } catch (error: unknown) {
      console.warn(
        "[paste] copying a pasted image failed",
        error instanceof Error ? error.message : error
      );
      failed += 1;
    }
  }
  return { files, failed };
}

/** A base64 image from the clipboard or an editor paste. */
export interface Base64Image {
  /** The bare base64, or a data URI that also carries the type. */
  base64: string;
  /** The type when `base64` is bare; a data URI's own type wins. */
  mimeType?: string | null;
  width?: number | null;
  height?: number | null;
}

/**
 * Writes a base64 image into the cache and describes it as a file to stage.
 * Without given dimensions they are read from the written file. A write
 * failure is thrown to the caller.
 */
export async function pastedImageFromBase64(image: Base64Image): Promise<ChatLocalFile> {
  const parsed = parseBase64Image(image.base64, image.mimeType ?? null);
  const key: string = randomUUID();
  const extension: string = parsed.mimeType ? (PASTED_IMAGE_EXTENSIONS[parsed.mimeType] ?? "") : "";
  const target = new File(Paths.cache, pastedImageName(key, extension));
  target.write(parsed.base64, { encoding: "base64" });
  const known: boolean = !!image.width && !!image.height;
  const size: { width: number; height: number } | null = known
    ? { width: image.width as number, height: image.height as number }
    : await imageSize(target.uri);
  return {
    key,
    uri: target.uri,
    name: target.name,
    mimeType: extension ? pastedImageType(extension) : parsed.mimeType,
    size: target.size,
    width: size?.width ?? null,
    height: size?.height ?? null,
  };
}
