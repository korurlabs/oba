import { Directory, File, Paths } from "expo-file-system";
import * as MediaLibrary from "expo-media-library";

import {
  chatDownloadFileName,
  chatSafeFileName,
  isChatImageType,
} from "@/components/chat/chatAttachments";
import type { ChatAttachmentResponse } from "@/lib/schemas/chat";

/*
 * "Download file" of the image viewer. The attachment URL is a short-lived
 * signed storage link, so the file is fetched without the API's bearer.
 */

/** Where a download went: the photo library, a folder the user picked, or nowhere. */
export type ChatDownloadResult = "photos" | "files" | "cancelled" | "photosDenied";

/** True for the error both platforms raise when the user closes the folder picker. */
function isPickerCancelled(error: unknown): boolean {
  const code: unknown = (error as { code?: unknown } | null)?.code;
  return typeof code === "string" && /CANCELLED/i.test(code);
}

async function downloadToCache(attachment: ChatAttachmentResponse): Promise<File> {
  const target = new File(Paths.cache, chatDownloadFileName(attachment));
  return File.downloadFileAsync(attachment.url, target, { idempotent: true });
}

/**
 * Saves an attachment on the device: an image into the photo library (write
 * access only), any other file into a folder the user picks in the system
 * picker. Failures are thrown to the caller, which tells the user.
 */
export async function downloadChatAttachment(
  attachment: ChatAttachmentResponse
): Promise<ChatDownloadResult> {
  if (isChatImageType(attachment.contentType)) {
    const permission = await MediaLibrary.requestPermissionsAsync(true, ["photo"]);
    if (!permission.granted) return "photosDenied";
    const cached: File = await downloadToCache(attachment);
    await MediaLibrary.saveToLibraryAsync(cached.uri);
    return "photos";
  }
  let folder: Directory;
  try {
    folder = await Directory.pickDirectoryAsync();
  } catch (error: unknown) {
    if (isPickerCancelled(error)) return "cancelled";
    throw error;
  }
  const cached: File = await downloadToCache(attachment);
  const saved: File = folder.createFile(
    chatSafeFileName(attachment.fileName),
    attachment.contentType ?? null
  );
  saved.write(await cached.bytes());
  return "files";
}
