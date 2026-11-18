import { Image, type ImageErrorEventData } from "expo-image";
import { Paperclip } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { ChatImageFrame, useChatMessageImageMax } from "@/components/chat/ChatAttachmentViews";
import {
  chatImageBox,
  formatChatFileSize,
  isChatPreviewableImage,
  type ChatImageBox,
} from "@/components/chat/chatAttachments";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import type { ChatLocalFile } from "@/lib/schemas/chat";

type ChatPendingImagesProps = {
  /** The files of the reader's message the server has not confirmed yet. */
  files: readonly ChatLocalFile[];
};

/**
 * The files of a message this device has not sent yet, on the reader's
 * side: images at the size and in the layout the message shows once sent,
 * so the row keeps its shape when the server confirms it, and other files
 * as chips with their name and size. An image the device cannot decode is
 * listed as a chip instead, so it still shows by name. The row around it
 * dims the whole message while it is sending.
 */
export function ChatPendingImages({ files }: ChatPendingImagesProps) {
  const max: ChatImageBox = useChatMessageImageMax(true);
  const [undecodable, setUndecodable] = React.useState<ReadonlySet<string>>(() => new Set());
  if (files.length === 0) return null;
  const drawn = (file: ChatLocalFile): boolean =>
    isChatPreviewableImage(file.mimeType) && !undecodable.has(file.key);
  const images: ChatLocalFile[] = files.filter(drawn);
  const others: ChatLocalFile[] = files.filter((file) => !drawn(file));
  const markUndecodable = (key: string, error: string): void => {
    console.warn("[chat] a pending image could not be drawn; it is listed by name", error);
    setUndecodable((current) => new Set(current).add(key));
  };
  return (
    <View className="mt-1 items-end gap-1.5">
      {images.length > 0 ? (
        <View className="flex-row flex-wrap justify-end gap-2">
          {images.map((file) => {
            const box: ChatImageBox = chatImageBox(file.width, file.height, max);
            return (
              <View key={file.key} accessibilityRole="image" accessibilityLabel={file.name}>
                <ChatImageFrame box={box}>
                  <Image
                    source={{ uri: file.uri }}
                    style={box}
                    contentFit="cover"
                    recyclingKey={file.key}
                    onError={(event: ImageErrorEventData) => markUndecodable(file.key, event.error)}
                  />
                </ChatImageFrame>
              </View>
            );
          })}
        </View>
      ) : null}
      {others.length > 0 ? (
        <View className="flex-row flex-wrap justify-end gap-1.5">
          {others.map((file) => (
            <View
              key={file.key}
              className="max-w-64 flex-row items-center gap-1.5 rounded-md border border-border bg-muted px-2 py-1"
            >
              <Icon as={Paperclip} size={14} className="text-muted-foreground" />
              <Text variant="caption" numberOfLines={1} className="shrink text-foreground">
                {file.name}
              </Text>
              {file.size ? (
                <Text variant="caption" className="text-muted-foreground">
                  {formatChatFileSize(file.size)}
                </Text>
              ) : null}
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}
