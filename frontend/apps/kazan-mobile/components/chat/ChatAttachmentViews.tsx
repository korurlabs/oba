import { Image } from "expo-image";
import { Download, FileText } from "lucide-react-native";
import * as React from "react";
import { Pressable, View, useWindowDimensions } from "react-native";

import {
  chatImageBox,
  formatChatFileSize,
  isChatPreviewableImage,
  splitChatAttachments,
  type ChatImageBox,
} from "@/components/chat/chatAttachments";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import type { ChatAttachmentResponse } from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

/**
 * Room a message body leaves on each side of the screen: the reader's own
 * row has its wide empty gutter and the right padding; the others' row has
 * the left padding, the avatar column and its gap, and the gutter on the
 * right.
 */
const OWN_ROW_CHROME = 48 + 16;
const OTHERS_ROW_CHROME = 16 + 32 + 12 + 32;
/** An image in a message is at most this wide and this tall, as on the web. */
export const CHAT_MESSAGE_IMAGE_MAX: ChatImageBox = { width: 256, height: 192 };
/** The image's frame: a 1 px border on each side. */
const IMAGE_FRAME = 2;
/** Side of the thumbnail in a quoted reply. */
const QUOTE_THUMB = 40;

/**
 * The largest box an image of a message takes: the web's 256 by 192, or
 * narrower when the screen leaves the message body less room, so the image
 * and its frame never run past the row.
 */
export function useChatMessageImageMax(own: boolean): ChatImageBox {
  const window = useWindowDimensions();
  const room: number = window.width - (own ? OWN_ROW_CHROME : OTHERS_ROW_CHROME) - IMAGE_FRAME;
  return {
    width: Math.max(1, Math.min(CHAT_MESSAGE_IMAGE_MAX.width, room)),
    height: CHAT_MESSAGE_IMAGE_MAX.height,
  };
}

type ChatImageFrameProps = {
  box: ChatImageBox;
  children: React.ReactNode;
};

/** The frame every message image sits in: a 1 px border and the medium corners. */
export function ChatImageFrame({ box, children }: ChatImageFrameProps) {
  return (
    <View
      style={{ width: box.width + IMAGE_FRAME, height: box.height + IMAGE_FRAME }}
      className="overflow-hidden rounded-md border border-border bg-muted"
    >
      {children}
    </View>
  );
}

type ChatMessageAttachmentsProps = {
  attachments: readonly ChatAttachmentResponse[];
  /** Opens a file in the viewer; without it the files only show. */
  onOpen?: (attachment: ChatAttachmentResponse) => void;
  /** The reader's own message: the files line up on the right. */
  own?: boolean;
};

/**
 * The files of a message, as the web chat draws them: each PNG or JPEG
 * image at its own aspect, at most 256 wide and 192 high, in a box sized
 * from its stored dimensions so the list does not jump when it loads, the
 * images wrapping in a row with an 8 px gap; other files as a row with
 * their name and size. Tapping either opens the viewer.
 */
export function ChatMessageAttachments({
  attachments,
  onOpen,
  own = false,
}: ChatMessageAttachmentsProps) {
  const { t } = useTranslation();
  const max: ChatImageBox = useChatMessageImageMax(own);
  if (attachments.length === 0) return null;
  const { images, files } = splitChatAttachments(attachments);
  return (
    <View className={cn("mt-1 gap-1.5", own ? "items-end" : "items-start")}>
      {images.length > 0 ? (
        <View className={cn("flex-row flex-wrap gap-2", own && "justify-end")}>
          {images.map((image) => {
            const box: ChatImageBox = chatImageBox(image.width, image.height, max);
            return (
              <Pressable
                key={image.id}
                disabled={!onOpen}
                onPress={() => onOpen?.(image)}
                accessibilityRole={onOpen ? "imagebutton" : "image"}
                accessibilityLabel={t("chatImageA11y", { name: image.fileName })}
                className="active:opacity-80"
              >
                <ChatImageFrame box={box}>
                  <Image
                    source={{ uri: image.url, cacheKey: image.id }}
                    style={box}
                    contentFit="cover"
                    transition={150}
                    recyclingKey={image.id}
                  />
                </ChatImageFrame>
              </Pressable>
            );
          })}
        </View>
      ) : null}
      {files.map((file) => (
        <Pressable
          key={file.id}
          disabled={!onOpen}
          onPress={() => onOpen?.(file)}
          accessibilityRole={onOpen ? "button" : "text"}
          className="min-w-[200px] flex-row items-center gap-2 rounded-md border border-border bg-card p-2 active:opacity-60"
        >
          <Icon as={FileText} size={20} className="text-muted-foreground" />
          <View className="shrink grow">
            <Text variant="footnote" numberOfLines={1} className="text-link">
              {file.fileName}
            </Text>
            {file.fileSize ? (
              <Text variant="caption" className="text-muted-foreground">
                {formatChatFileSize(file.fileSize)}
              </Text>
            ) : null}
          </View>
          {onOpen ? <Icon as={Download} size={18} className="text-muted-foreground" /> : null}
        </Pressable>
      ))}
    </View>
  );
}

/** The first drawable image of a quoted reply as a small square, or nothing. */
export function ChatQuoteThumbnail({
  attachments,
}: {
  attachments: readonly ChatAttachmentResponse[];
}) {
  const image: ChatAttachmentResponse | undefined = attachments.find((attachment) =>
    isChatPreviewableImage(attachment.contentType)
  );
  if (!image) return null;
  return (
    <Image
      source={{ uri: image.url, cacheKey: image.id }}
      style={{ width: QUOTE_THUMB, height: QUOTE_THUMB, borderRadius: 4 }}
      contentFit="cover"
    />
  );
}
