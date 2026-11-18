import * as Clipboard from "expo-clipboard";
import * as React from "react";
import { AppState, type AppStateStatus } from "react-native";

import { pastedImageFromBase64 } from "@/components/kazan/pastedImage";
import type { ChatLocalFile } from "@/lib/schemas/chat";

/**
 * How an image is taken from the clipboard: JPEG at the quality the app's
 * photos use, so a pasted screenshot stays well under the upload limits.
 */
const CLIPBOARD_IMAGE_OPTIONS: Clipboard.GetImageOptions = {
  format: "jpeg",
  jpegQuality: 0.8,
};

/** Whether the clipboard holds an image, and a way to take it as a file. */
export interface ClipboardImage {
  available: boolean;
  /**
   * The clipboard's image written into the cache, or null when it holds none
   * any more (or iOS refused the paste). A failure is thrown to the caller.
   */
  read: () => Promise<ChatLocalFile | null>;
}

/**
 * Tracks whether the clipboard holds an image, for the "Paste image" item of
 * the description editor's image menu: on mount, whenever the app comes back
 * to the foreground (the user may have copied one elsewhere) and whenever the
 * clipboard changes while the app is open. Asking whether an image is there
 * never shows the iOS "Allow Paste" prompt; only `read` does, when the user
 * picks the item.
 */
export function useClipboardImage(): ClipboardImage {
  const [available, setAvailable] = React.useState<boolean>(false);

  React.useEffect(() => {
    let mounted = true;
    const check = (): void => {
      Clipboard.hasImageAsync()
        .then((hasImage: boolean) => {
          if (mounted) setAvailable(hasImage);
        })
        .catch((error: unknown) => {
          // reason: the answer only shows or hides the "Paste image" menu
          // item; a failed check hides it, as for an empty clipboard, and
          // the editor's own long-press Paste still works.
          console.warn(
            "[paste] checking the clipboard for an image failed",
            error instanceof Error ? error.message : error
          );
          if (mounted) setAvailable(false);
        });
    };
    check();
    const appState = AppState.addEventListener("change", (state: AppStateStatus) => {
      if (state === "active") check();
    });
    const clipboard = Clipboard.addClipboardListener(check);
    return () => {
      mounted = false;
      appState.remove();
      clipboard.remove();
    };
  }, []);

  const read = React.useCallback(async (): Promise<ChatLocalFile | null> => {
    const image: Clipboard.ClipboardImage | null =
      await Clipboard.getImageAsync(CLIPBOARD_IMAGE_OPTIONS);
    if (!image?.data) return null;
    return pastedImageFromBase64({
      base64: image.data,
      mimeType: "image/jpeg",
      width: image.size.width,
      height: image.size.height,
    });
  }, []);

  return { available, read };
}
