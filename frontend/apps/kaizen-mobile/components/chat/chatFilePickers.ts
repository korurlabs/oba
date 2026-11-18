import { randomUUID } from "expo-crypto";
import * as DocumentPicker from "expo-document-picker";
import * as ImagePicker from "expo-image-picker";

import {
  chatLocalFileFromDocument,
  chatLocalFileFromImage,
} from "@/components/chat/chatAttachments";
import type { ChatLocalFile } from "@/lib/schemas/chat";

/*
 * The three places a chat file comes from. The system photo picker needs no
 * library permission; only the camera asks for access.
 */

export type ChatFileSource = "camera" | "library" | "files";

/** What a pick gave: the files, or that the camera may not be used. */
export type ChatPickResult = { kind: "files"; files: ChatLocalFile[] } | { kind: "cameraDenied" };

/** JPEG quality of a photo: well under the request limit, without visible loss in a chat. */
const PHOTO_QUALITY = 0.8;

/** How a pick is narrowed. */
export interface ChatPickOptions {
  /**
   * The media types the files app offers, such as the types a ticket
   * attachment may have; every type when left out. The camera and the photo
   * library give images only either way.
   */
  documentTypes?: readonly string[];
}

/**
 * Opens the picker of `source` for at most `remaining` files. A cancelled
 * pick gives no files. A picker failure is thrown to the caller, which tells
 * the user.
 */
export async function pickChatFiles(
  source: ChatFileSource,
  remaining: number,
  options: ChatPickOptions = {}
): Promise<ChatPickResult> {
  if (remaining <= 0) return { kind: "files", files: [] };
  if (source === "camera") {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) return { kind: "cameraDenied" };
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ["images"],
      quality: PHOTO_QUALITY,
      exif: false,
    });
    if (result.canceled) return { kind: "files", files: [] };
    return {
      kind: "files",
      files: result.assets.map((asset) => chatLocalFileFromImage(asset, randomUUID())),
    };
  }
  if (source === "library") {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      allowsMultipleSelection: true,
      selectionLimit: remaining,
      quality: PHOTO_QUALITY,
      exif: false,
    });
    if (result.canceled) return { kind: "files", files: [] };
    return {
      kind: "files",
      files: result.assets
        .slice(0, remaining)
        .map((asset) => chatLocalFileFromImage(asset, randomUUID())),
    };
  }
  const result = await DocumentPicker.getDocumentAsync({
    type: options.documentTypes ? [...options.documentTypes] : undefined,
    multiple: true,
    copyToCacheDirectory: true,
  });
  if (result.canceled) return { kind: "files", files: [] };
  return {
    kind: "files",
    files: result.assets
      .slice(0, remaining)
      .map((asset) => chatLocalFileFromDocument(asset, randomUUID())),
  };
}
