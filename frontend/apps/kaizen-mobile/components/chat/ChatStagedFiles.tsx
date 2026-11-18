import { Image } from "expo-image";
import { Paperclip, X } from "lucide-react-native";
import * as React from "react";
import { Pressable, ScrollView, View } from "react-native";

import { formatChatFileSize, isChatImageType } from "@/components/chat/chatAttachments";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import type { ChatLocalFile } from "@/lib/schemas/chat";

/** Side of a staged image's square thumbnail, the web composer's size. */
const THUMB_SIZE = 80;
const THUMB_STYLE = { width: THUMB_SIZE, height: THUMB_SIZE } as const;

/** The small remove button is drawn at the web's size; its reach is the 44 pt minimum. */
const REMOVE_HIT_SLOP = { top: 12, bottom: 12, left: 12, right: 12 } as const;

type ChatStagedFilesProps = {
  files: readonly ChatLocalFile[];
  /** Removes a file before the send. */
  onRemove: (key: string) => void;
};

/**
 * Files picked for a message, as the web composer shows them: images as
 * 80 px square thumbnails with a remove button in the corner, other files as
 * chips with their name, size and a remove button. Each kind keeps one row
 * that scrolls sideways, so the composer stays low above the keyboard.
 */
export function ChatStagedFiles({ files, onRemove }: ChatStagedFilesProps) {
  const { t } = useTranslation();
  if (files.length === 0) return null;
  const images: ChatLocalFile[] = files.filter((file) => isChatImageType(file.mimeType));
  const others: ChatLocalFile[] = files.filter((file) => !isChatImageType(file.mimeType));
  const removeLabel = (file: ChatLocalFile): string => t("chatRemoveFile", { name: file.name });
  return (
    <View accessibilityLabel={t("chatStagedFiles")} className="gap-2 pt-2.5">
      {images.length > 0 ? (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          contentContainerClassName="items-center gap-2 px-4"
        >
          {images.map((file) => (
            <View
              key={file.key}
              accessibilityLabel={file.name}
              className="overflow-hidden rounded-md border border-border bg-background"
            >
              <Image source={{ uri: file.uri }} style={THUMB_STYLE} contentFit="cover" />
              <Pressable
                onPress={() => onRemove(file.key)}
                accessibilityRole="button"
                accessibilityLabel={removeLabel(file)}
                hitSlop={REMOVE_HIT_SLOP}
                className="absolute right-1 top-1 rounded-xs bg-background p-0.5 shadow-sm active:opacity-60"
              >
                <Icon as={X} size={14} className="text-foreground" />
              </Pressable>
            </View>
          ))}
        </ScrollView>
      ) : null}
      {others.length > 0 ? (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          contentContainerClassName="items-center gap-1.5 px-4"
        >
          {others.map((file) => (
            <View
              key={file.key}
              className="max-w-64 flex-row items-center gap-1.5 rounded-md border border-border bg-background py-1 pl-2 pr-1"
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
              <Pressable
                onPress={() => onRemove(file.key)}
                accessibilityRole="button"
                accessibilityLabel={removeLabel(file)}
                hitSlop={REMOVE_HIT_SLOP}
                className="rounded-xs p-0.5 active:opacity-60"
              >
                <Icon as={X} size={14} className="text-muted-foreground" />
              </Pressable>
            </View>
          ))}
        </ScrollView>
      ) : null}
    </View>
  );
}
