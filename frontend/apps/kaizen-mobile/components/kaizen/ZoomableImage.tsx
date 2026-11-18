import { Image, type ImageErrorEventData, type ImageLoadEventData } from "expo-image";
import { Stack } from "expo-router";
import { Download, FileText, X } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Pressable, View, type LayoutChangeEvent } from "react-native";
import { Gesture, GestureDetector, GestureHandlerRootView } from "react-native-gesture-handler";
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { useSafeAreaInsets, type EdgeInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import {
  downloadChatAttachment,
  type ChatDownloadResult,
} from "@/components/chat/chatAttachmentDownload";
import { formatChatFileSize } from "@/components/chat/chatAttachments";
import {
  chatViewerClamp,
  chatViewerContentSize,
  chatViewerDismisses,
  chatViewerPanLimit,
  type ChatViewerSize,
} from "@/components/chat/chatImageViewerModel";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";
import { IMAGE_VIEWER_STATUS_BAR_STYLE } from "@/lib/statusBar";
import type { ChatAttachmentResponse } from "@/lib/schemas/chat";

/*
 * The full-screen image viewer of chat files, ticket attachments and
 * description images: the zoomable image, the black frame with Close, the
 * title and Download around it, the notice for a file without a preview, and
 * the download with its outcome told to the user.
 */

/** Pinch zoom stays between the fitted image and four times its size. */
const MIN_SCALE = 1;
const MAX_SCALE = 4;
/** A double tap zooms to this scale, or back to fit when zoomed. */
const DOUBLE_TAP_SCALE = 2.5;

type ZoomableImageProps = {
  /** The image URL, such as a short-lived signed storage link. */
  uri: string;
  /**
   * What the image cache keys the image by, such as its attachment id, so a
   * re-signed URL of the same file is not downloaded again. Without it the
   * cache keys by `uri`.
   */
  cacheKey?: string;
  /** The stored pixel size, when known; the decoded size replaces it on load. */
  width?: number | null;
  height?: number | null;
  /** Called on a swipe down at fit size. */
  onDismiss: () => void;
  accessibilityLabel: string;
  /** Called with the reason when the image cannot be loaded or decoded. */
  onError?: (message: string) => void;
};

/**
 * An image fitted to the screen, with pinch zoom, a pan while zoomed that
 * stops at the image's edges, a double tap to zoom in or back to fit, and a
 * swipe down at fit size that closes the viewer.
 */
export function ZoomableImage({
  uri,
  cacheKey,
  width,
  height,
  onDismiss,
  accessibilityLabel,
  onError,
}: ZoomableImageProps) {
  const boxWidth = useSharedValue<number>(0);
  const boxHeight = useSharedValue<number>(0);
  // The stored size until the image loads, then the decoded one, which is
  // what `contain` fits to the box.
  const imageSize = useSharedValue<ChatViewerSize | null>(
    width && height ? { width, height } : null
  );
  const scale = useSharedValue<number>(1);
  const savedScale = useSharedValue<number>(1);
  const translateX = useSharedValue<number>(0);
  const translateY = useSharedValue<number>(0);
  const savedX = useSharedValue<number>(0);
  const savedY = useSharedValue<number>(0);

  /** How far the image may move on each axis at `zoom`. */
  const panLimits = (zoom: number): ChatViewerSize => {
    "worklet";
    const content: ChatViewerSize = chatViewerContentSize(
      { width: boxWidth.value, height: boxHeight.value },
      imageSize.value
    );
    return {
      width: chatViewerPanLimit(boxWidth.value, content.width, zoom),
      height: chatViewerPanLimit(boxHeight.value, content.height, zoom),
    };
  };

  const pinch = Gesture.Pinch()
    .onUpdate((event) => {
      scale.value = Math.min(MAX_SCALE, Math.max(MIN_SCALE, savedScale.value * event.scale));
    })
    .onEnd(() => {
      savedScale.value = scale.value;
      // Zooming out shrinks the room to move, so the image is pulled back inside it.
      const limits: ChatViewerSize = panLimits(scale.value);
      const x: number = chatViewerClamp(translateX.value, limits.width);
      const y: number = chatViewerClamp(translateY.value, limits.height);
      translateX.value = withTiming(x);
      translateY.value = withTiming(y);
      savedX.value = x;
      savedY.value = y;
    });

  const pan = Gesture.Pan()
    .averageTouches(true)
    .onUpdate((event) => {
      if (scale.value <= MIN_SCALE) {
        // At fit size only a move down counts: it drags the image towards closing.
        translateY.value = Math.max(0, event.translationY);
        return;
      }
      const limits: ChatViewerSize = panLimits(scale.value);
      translateX.value = chatViewerClamp(savedX.value + event.translationX, limits.width);
      translateY.value = chatViewerClamp(savedY.value + event.translationY, limits.height);
    })
    .onEnd((event) => {
      if (scale.value <= MIN_SCALE) {
        if (chatViewerDismisses(event.translationY, event.velocityY)) {
          scheduleOnRN(onDismiss);
        } else {
          translateY.value = withTiming(0);
        }
        return;
      }
      savedX.value = translateX.value;
      savedY.value = translateY.value;
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      const next: number = scale.value > MIN_SCALE ? MIN_SCALE : DOUBLE_TAP_SCALE;
      scale.value = withTiming(next);
      savedScale.value = next;
      translateX.value = withTiming(0);
      translateY.value = withTiming(0);
      savedX.value = 0;
      savedY.value = 0;
    });

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  const onLayout = (event: LayoutChangeEvent): void => {
    boxWidth.value = event.nativeEvent.layout.width;
    boxHeight.value = event.nativeEvent.layout.height;
  };

  const onLoad = (event: ImageLoadEventData): void => {
    imageSize.value = { width: event.source.width, height: event.source.height };
  };

  const onLoadError = (event: ImageErrorEventData): void => {
    console.warn("[viewer] image failed to load", event.error);
    onError?.(event.error);
  };

  return (
    <GestureDetector gesture={Gesture.Simultaneous(pinch, pan, doubleTap)}>
      <Animated.View onLayout={onLayout} style={[{ flex: 1 }, animatedStyle]}>
        <Image
          source={cacheKey ? { uri, cacheKey } : { uri }}
          style={{ flex: 1 }}
          contentFit="contain"
          onLoad={onLoad}
          onError={onLoadError}
          accessibilityLabel={accessibilityLabel}
        />
      </Animated.View>
    </GestureDetector>
  );
}

/** The Download control of the viewer's top bar. */
export interface ImageViewerDownload {
  onPress: () => void;
  busy: boolean;
}

type ImageViewerScreenProps = {
  /** The file name or image text shown between Close and Download. */
  title: string;
  onClose: () => void;
  /** Download in the top bar; left out where there is nothing to save. */
  download?: ImageViewerDownload | null;
  /** The image, a notice, or a placeholder; it fills the space under the bar. */
  children: React.ReactNode;
  /** Shown under the content, such as a Download button for a file without a preview. */
  footer?: React.ReactNode;
};

/**
 * The full-screen viewer frame, on black in both themes: Close, the title
 * and Download on top, the content in the middle.
 */
export function ImageViewerScreen({
  title,
  onClose,
  download,
  children,
  footer,
}: ImageViewerScreenProps) {
  const { t } = useTranslation();
  const insets: EdgeInsets = useSafeAreaInsets();
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      {/* A light status bar on the black frame in both themes, also where the
          route does not set one itself. */}
      <Stack.Screen options={{ statusBarStyle: IMAGE_VIEWER_STATUS_BAR_STYLE }} />
      {/* The insets come from the app's provider: a SafeAreaView inside this
          full-screen modal reads no top inset, which put Close and Download
          in the status bar. */}
      <View
        className="flex-1 bg-black"
        style={{
          paddingTop: insets.top,
          paddingBottom: insets.bottom,
          paddingLeft: insets.left,
          paddingRight: insets.right,
        }}
      >
        <View className="flex-row items-center gap-2 px-2 py-1">
          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            accessibilityLabel={t("chatClose")}
            hitSlop={8}
            className="size-11 items-center justify-center"
          >
            <Icon as={X} size={24} className="text-white" />
          </Pressable>
          <Text variant="subhead" numberOfLines={1} className="flex-1 text-center text-white">
            {title}
          </Text>
          {download ? (
            <Pressable
              onPress={download.onPress}
              disabled={download.busy}
              accessibilityRole="button"
              accessibilityLabel={t("chatDownloadFile")}
              accessibilityState={{ busy: download.busy, disabled: download.busy }}
              hitSlop={8}
              className="size-11 items-center justify-center"
            >
              {download.busy ? (
                <ActivityIndicator color="white" />
              ) : (
                <Icon as={Download} size={22} className="text-white" />
              )}
            </Pressable>
          ) : (
            <View className="size-11" />
          )}
        </View>
        <View className="flex-1 overflow-hidden">{children}</View>
        {footer}
      </View>
    </GestureHandlerRootView>
  );
}

/** A centred line of text on the viewer's black ground, such as "file gone". */
export function ImageViewerNotice({ text }: { text: string }) {
  return (
    <View className="flex-1 items-center justify-center px-8">
      <Text variant="subhead" className="text-center text-white/70">
        {text}
      </Text>
    </View>
  );
}

/** A file the app does not draw: its icon, name and size, and why it shows no preview. */
export function ImageViewerNoPreview({ attachment }: { attachment: ChatAttachmentResponse }) {
  const { t } = useTranslation();
  return (
    <View className="flex-1 items-center justify-center gap-3 px-8">
      <Icon as={FileText} size={56} className="text-white/70" />
      <Text variant="headline" className="text-center text-white">
        {attachment.fileName}
      </Text>
      {attachment.fileSize ? (
        <Text variant="footnote" className="text-white/70">
          {formatChatFileSize(attachment.fileSize)}
        </Text>
      ) : null}
      <Text variant="subhead" className="text-center text-white/70">
        {t("chatFileNoPreview")}
      </Text>
    </View>
  );
}

/** What the user is told after a download, by where it went. */
function reportDownload(
  result: ChatDownloadResult,
  t: ReturnType<typeof useTranslation>["t"]
): void {
  switch (result) {
    case "photos":
      toast.success(t("chatSavedToPhotos"));
      return;
    case "files":
      toast.success(t("chatSavedToFiles"));
      return;
    case "photosDenied":
      toast.error(t("chatPhotosDenied"));
      return;
    case "cancelled":
      return;
  }
}

/**
 * Download of the viewed attachment: an image into Photos, any other file
 * into a folder the user picks. The outcome and a failure are toasted.
 */
export function useAttachmentDownload(attachment: ChatAttachmentResponse | null | undefined): {
  download: () => void;
  downloading: boolean;
} {
  const { t } = useTranslation();
  const [downloading, setDownloading] = React.useState<boolean>(false);

  const download = (): void => {
    if (!attachment || downloading) return;
    setDownloading(true);
    downloadChatAttachment(attachment)
      .then((result: ChatDownloadResult) => reportDownload(result, t))
      .catch((error: unknown) => {
        console.warn(
          "[viewer] downloading a file failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("chatDownloadFailed"));
      })
      .finally(() => setDownloading(false));
  };

  return { download, downloading };
}
