import { randomUUID } from "expo-crypto";
import { File } from "expo-file-system";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";

import type { ChatLocalFile } from "@/lib/schemas/chat";
import { DESCRIPTION_IMAGE_MAX_BYTES, DESCRIPTION_IMAGE_MAX_WIDTH } from "@/lib/schemas/tickets";

/*
 * Prepares an image for a ticket description the way the web editor does
 * before it inserts one: SVG is refused, files over 10 MB are refused, GIF
 * keeps its animation, and every other image is drawn again at most 1200 px
 * wide as WebP at quality 0.8, so a description stays light on mobile data and
 * a phone's image is stored exactly as one added on the web. WebP also keeps
 * the transparency of a PNG. Chat files and ticket attachments are uploaded as
 * picked and skip this.
 */

export { DESCRIPTION_IMAGE_MAX_BYTES, DESCRIPTION_IMAGE_MAX_WIDTH };

/** WebP quality of a prepared image, the web editor's. */
const DESCRIPTION_IMAGE_QUALITY = 0.8;

/** Why an image cannot go into a description; also its message key. */
export type ImagePrepRefusal = "imageTypeBlocked" | "imageTooLarge";

/** What preparing an image gives: the file to upload, or why there is none. */
export type ImagePrepResult =
  | { kind: "ready"; file: ChatLocalFile }
  | { kind: "refused"; reason: ImagePrepRefusal };

/** What to do with an image, decided from its type, size and width alone. */
export type ImagePrepPlan =
  | { kind: "refuse"; reason: ImagePrepRefusal }
  | { kind: "keep" }
  | { kind: "encode" };

/** The media type without parameters, lower case; empty when unknown. */
function baseType(mimeType: string | null): string {
  return (mimeType ?? "").split(";")[0]?.trim().toLowerCase() ?? "";
}

/**
 * The plan for one image. A file that is not an image, or is SVG, which can
 * carry script, is refused; one over the size cap is refused before it is
 * decoded. GIF is kept as it is. Every other image is encoded as WebP.
 */
export function imagePrepPlan(file: Pick<ChatLocalFile, "mimeType" | "size">): ImagePrepPlan {
  const type: string = baseType(file.mimeType);
  if (!type.startsWith("image/") || type === "image/svg+xml") {
    return { kind: "refuse", reason: "imageTypeBlocked" };
  }
  if ((file.size ?? 0) > DESCRIPTION_IMAGE_MAX_BYTES) {
    return { kind: "refuse", reason: "imageTooLarge" };
  }
  return type === "image/gif" ? { kind: "keep" } : { kind: "encode" };
}

/** The width to scale a decoded image to, or null when it already fits. */
export function scaledImageWidth(width: number): number | null {
  return width > DESCRIPTION_IMAGE_MAX_WIDTH ? DESCRIPTION_IMAGE_MAX_WIDTH : null;
}

/** The name of the prepared file: the original name with a `.webp` extension. */
export function preparedImageName(name: string): string {
  const dot: number = name.lastIndexOf(".");
  const base: string = (dot >= 0 ? name.slice(0, dot) : name).trim();
  return `${base || "image"}.webp`;
}

/**
 * Prepares one image for a description. The size of a file whose size is
 * unknown is read from the file first. The width is read from the decoded
 * image, not from the file's description, which a paste may leave unknown.
 * A failure to read or draw the image is thrown to the caller, which tells
 * the user.
 */
export async function prepareDescriptionImage(file: ChatLocalFile): Promise<ImagePrepResult> {
  const size: number | null = file.size ?? new File(file.uri).size;
  const plan: ImagePrepPlan = imagePrepPlan({ ...file, size });
  if (plan.kind === "refuse") return { kind: "refused", reason: plan.reason };
  if (plan.kind === "keep") {
    if (file.width && file.height) return { kind: "ready", file: { ...file, size } };
    // The description's <img> carries width and height so the text does not
    // jump; a GIF without them gets them from its first frame, unchanged.
    const frame = await ImageManipulator.manipulate(file.uri).renderAsync();
    return { kind: "ready", file: { ...file, size, width: frame.width, height: frame.height } };
  }

  const decoded = await ImageManipulator.manipulate(file.uri).renderAsync();
  const width: number | null = scaledImageWidth(decoded.width);
  const image =
    width === null
      ? decoded
      : await ImageManipulator.manipulate(decoded).resize({ width }).renderAsync();
  const saved = await image.saveAsync({
    format: SaveFormat.WEBP,
    compress: DESCRIPTION_IMAGE_QUALITY,
  });
  return {
    kind: "ready",
    file: {
      key: randomUUID(),
      uri: saved.uri,
      name: preparedImageName(file.name),
      mimeType: "image/webp",
      size: new File(saved.uri).size,
      width: saved.width,
      height: saved.height,
    },
  };
}
