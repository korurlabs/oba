import { useQueryClient } from "@tanstack/react-query";
import { File } from "expo-file-system";
import { Camera, ClipboardPaste, Images } from "lucide-react-native";
import * as React from "react";

import { pickChatFiles, type ChatFileSource } from "@/components/chat/chatFilePickers";
import type { ActionSheetAction } from "@/components/kazan/actionSheetModel";
import { prepareDescriptionImage } from "@/components/kazan/imagePrep";
import { pastedImageFromBase64 } from "@/components/kazan/pastedImage";
import { useClipboardImage } from "@/components/kazan/useClipboardImage";
import type { InsertedImage, PastedImageData } from "@/components/ticket/DescriptionEditorDom";
import {
  commitPendingImages,
  pendingImageSrc,
  reconcilePendingImages,
  type UploadedImage,
} from "@/components/ticket/descriptionImagesModel";
import { toast } from "@/components/ui/toast";
import { invalidateTicketAttachments, uploadTicketAttachments } from "@/hooks/queries/attachments";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatAttachmentResponse, ChatLocalFile } from "@/lib/schemas/chat";
import type { PendingImage } from "@/lib/schemas/tickets";

/** The image menu of the editor, for an `ActionSheet`. */
export interface DescriptionImagePicker {
  visible: boolean;
  onClose: () => void;
  title: string;
  actions: ActionSheetAction[];
}

/** The images of a description being edited, kept on the phone until the save uploads them. */
export interface DescriptionImages {
  /** The pending images the editor shows, in the order added. */
  images: readonly PendingImage[];
  /** The image button: opens the image menu and gives the prepared image, or null. */
  add: () => Promise<InsertedImage | null>;
  /** An image pasted into the editor, prepared, or null when it was refused. */
  addPasted: (data: PastedImageData) => Promise<InsertedImage | null>;
  /** An image was deleted in the editor. */
  remove: (src: string) => void;
  /** A data URI for a pending `src`, or null when it is not known here. */
  resolve: (src: string) => Promise<string | null>;
  /** The pending images the text still shows, removed ones that came back by Undo included. */
  reconcile: (markdown: string) => PendingImage[];
  /**
   * Uploads the text's pending images to the ticket one by one and returns
   * the text with their tags rewritten as the web writes them. The first
   * failure is thrown; the uploads before it are kept for the next attempt.
   */
  commit: (
    ticketId: string,
    markdown: string,
    onProgress: (current: number, total: number) => void
  ) => Promise<string>;
  /** The image menu the sheet renders. */
  picker: DescriptionImagePicker;
}

/** The image as a data URI, which the editor's WebView can show without file access. */
async function dataUri(file: ChatLocalFile): Promise<string> {
  const base64: string = await new File(file.uri).base64();
  return `data:${file.mimeType ?? "image/jpeg"};base64,${base64}`;
}

/** The stored attachment of a one-file upload. */
function storedImage(attachments: readonly ChatAttachmentResponse[]): UploadedImage {
  const attachment: ChatAttachmentResponse | undefined = attachments[0];
  if (!attachment) throw new Error("The upload returned no attachment");
  return { id: attachment.id, url: attachment.url };
}

function errorText(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * The images of the description editor. An added image (camera, photo
 * library, the clipboard, or a paste into the editor) is prepared as the web
 * prepares one, kept as a file in the cache, and shown in the editor under a
 * `kazan-pending:` src from a data URI. Nothing is uploaded until `commit`.
 * Every image ever added stays known for the editor's lifetime, so an image
 * deleted and brought back by Undo is still uploaded.
 */
export function useDescriptionImages(initial: readonly PendingImage[] = []): DescriptionImages {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const clipboard = useClipboardImage();
  const [images, setImages] = React.useState<readonly PendingImage[]>(initial);
  const known = React.useRef<Map<string, PendingImage>>(
    new Map(initial.map((image) => [image.src, image]))
  );
  const previews = React.useRef<Map<string, string>>(new Map());
  const [pickerOpen, setPickerOpen] = React.useState<boolean>(false);
  const pendingPick = React.useRef<((image: InsertedImage | null) => void) | null>(null);

  /** The resolver of the open image menu, taken once, by a choice or by the close. */
  const takePick = React.useCallback(() => {
    const settle = pendingPick.current;
    pendingPick.current = null;
    return settle;
  }, []);

  /** Prepares a file for the description and keeps it; a refusal is toasted and gives null. */
  const keep = React.useCallback(
    async (file: ChatLocalFile, alt: string): Promise<InsertedImage | null> => {
      const prepared = await prepareDescriptionImage(file);
      if (prepared.kind === "refused") {
        toast.error(t(prepared.reason));
        return null;
      }
      const ready: ChatLocalFile = prepared.file;
      const image: PendingImage = {
        key: ready.key,
        file: ready,
        src: pendingImageSrc(ready.key),
        alt,
        width: ready.width ?? 0,
        height: ready.height ?? 0,
      };
      const previewUri: string = await dataUri(ready);
      known.current.set(image.src, image);
      previews.current.set(image.src, previewUri);
      setImages((current) => [...current, image]);
      return { src: image.src, alt, width: image.width, height: image.height, previewUri };
    },
    [t]
  );

  /** Adds one image from a menu choice; every failure is logged and toasted, giving null. */
  const addFrom = React.useCallback(
    async (source: ChatFileSource | "clipboard"): Promise<InsertedImage | null> => {
      try {
        if (source === "clipboard") {
          const file: ChatLocalFile | null = await clipboard.read();
          if (!file) {
            toast.error(t("pasteImageUnavailable"));
            return null;
          }
          return await keep(file, file.name);
        }
        const result = await pickChatFiles(source, 1);
        if (result.kind === "cameraDenied") {
          toast.error(t("chatCameraDenied"));
          return null;
        }
        const file: ChatLocalFile | undefined = result.files[0];
        return file ? await keep(file, file.name) : null;
      } catch (error) {
        console.warn(`[description] adding an image from ${source} failed`, errorText(error));
        toast.error(t(source === "clipboard" ? "pasteImageFailed" : "descriptionImageFailed"));
        return null;
      }
    },
    [clipboard, keep, t]
  );

  const choose = React.useCallback(
    (source: ChatFileSource | "clipboard") => {
      const settle = takePick();
      if (!settle) return;
      void addFrom(source).then(settle);
    },
    [addFrom, takePick]
  );

  const add = React.useCallback(
    () =>
      new Promise<InsertedImage | null>((resolve) => {
        // A menu still waiting for its answer gives none, so its editor call ends.
        takePick()?.(null);
        pendingPick.current = resolve;
        setPickerOpen(true);
      }),
    [takePick]
  );

  const onPickerClose = React.useCallback(() => {
    setPickerOpen(false);
    // The menu calls onClose just before the chosen action, in the same
    // turn; the answer is null only when no action took the resolver.
    void Promise.resolve().then(() => takePick()?.(null));
  }, [takePick]);

  const addPasted = React.useCallback(
    async (data: PastedImageData): Promise<InsertedImage | null> => {
      try {
        const file: ChatLocalFile = await pastedImageFromBase64({
          base64: data.base64,
          mimeType: data.mimeType,
        });
        return await keep(file, data.name);
      } catch (error) {
        console.warn("[description] pasting an image failed", errorText(error));
        toast.error(t("pasteImageFailed"));
        return null;
      }
    },
    [keep, t]
  );

  const remove = React.useCallback((src: string) => {
    setImages((current) => current.filter((image) => image.src !== src));
  }, []);

  const resolve = React.useCallback(async (src: string): Promise<string | null> => {
    const cached: string | undefined = previews.current.get(src);
    if (cached) return cached;
    const image: PendingImage | undefined = known.current.get(src);
    if (!image) return null;
    try {
      const uri: string = await dataUri(image.file);
      previews.current.set(src, uri);
      return uri;
    } catch (error) {
      // The editor shows the image's box with its name instead; the save
      // still uploads the file, or reports it when it is gone.
      console.warn("[description] reading a pending image failed", errorText(error));
      return null;
    }
  }, []);

  const reconcile = React.useCallback(
    (markdown: string) => reconcilePendingImages(markdown, [...known.current.values()]),
    []
  );

  const markUploaded = React.useCallback((key: string, uploaded: UploadedImage) => {
    for (const [src, image] of known.current) {
      if (image.key === key) known.current.set(src, { ...image, uploaded });
    }
    setImages((current) =>
      current.map((image) => (image.key === key ? { ...image, uploaded } : image))
    );
  }, []);

  const commit = React.useCallback(
    async (
      ticketId: string,
      markdown: string,
      onProgress: (current: number, total: number) => void
    ): Promise<string> => {
      let uploadedAny = false;
      try {
        return await commitPendingImages({
          markdown,
          images: [...known.current.values()],
          upload: async (image) => {
            const stored: UploadedImage = storedImage(
              await uploadTicketAttachments(ticketId, [image.file])
            );
            uploadedAny = true;
            return stored;
          },
          onUploaded: markUploaded,
          onProgress,
        });
      } finally {
        // The uploads bypass the attachment hooks, so the cached list is stale.
        if (uploadedAny) void invalidateTicketAttachments(queryClient, orgId, ticketId);
      }
    },
    [markUploaded, orgId, queryClient]
  );

  const actions: ActionSheetAction[] = [
    { label: t("chatAttachCamera"), icon: Camera, onPress: () => choose("camera") },
    { label: t("chatAttachPhotos"), icon: Images, onPress: () => choose("library") },
    ...(clipboard.available
      ? [{ label: t("pasteImage"), icon: ClipboardPaste, onPress: () => choose("clipboard") }]
      : []),
  ];

  return {
    images,
    add,
    addPasted,
    remove,
    resolve,
    reconcile,
    commit,
    picker: {
      visible: pickerOpen,
      onClose: onPickerClose,
      title: t("descriptionEditorImage"),
      actions,
    },
  };
}
