import { useQueryClient } from "@tanstack/react-query";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Download } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { isChatPreviewableImage } from "@/components/chat/chatAttachments";
import {
  ImageViewerNoPreview,
  ImageViewerNotice,
  ImageViewerScreen,
  useAttachmentDownload,
  ZoomableImage,
} from "@/components/kaizen/ZoomableImage";
import { Button } from "@/components/ui/button";
import { findChatAttachment } from "@/hooks/queries/chatCache";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatAttachmentResponse } from "@/lib/schemas/chat";

/**
 * The full-screen viewer of a chat file: the image with pinch zoom, or a
 * "no preview" notice. A file without a preview also offers Download under
 * its notice, since saving it is all the reader can do here.
 */
export default function ChatImageViewer() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const params = useLocalSearchParams<{ attachmentId: string; messageId: string }>();
  // Read once: the signed URL in the cache can change on a refetch, and the
  // viewer keeps the one it opened with.
  const [attachment] = React.useState<ChatAttachmentResponse | undefined>(() =>
    orgId && params.messageId && params.attachmentId
      ? findChatAttachment(queryClient, orgId, params.messageId, params.attachmentId)
      : undefined
  );
  const { download, downloading } = useAttachmentDownload(attachment);

  const close = (): void => {
    if (router.canGoBack()) router.back();
  };

  const previewable: boolean = !!attachment && isChatPreviewableImage(attachment.contentType);

  return (
    <ImageViewerScreen
      title={attachment?.fileName ?? ""}
      onClose={close}
      download={attachment ? { onPress: download, busy: downloading } : null}
      footer={
        attachment && !previewable ? (
          <View className="items-center px-4 pb-2 pt-3">
            <Button icon={Download} loading={downloading} onPress={download}>
              {t("chatDownloadFile")}
            </Button>
          </View>
        ) : null
      }
    >
      {!attachment ? (
        <ImageViewerNotice text={t("chatFileGone")} />
      ) : previewable ? (
        <ZoomableImage
          uri={attachment.url}
          cacheKey={attachment.id}
          width={attachment.width}
          height={attachment.height}
          onDismiss={close}
          accessibilityLabel={t("chatImageA11y", { name: attachment.fileName })}
        />
      ) : (
        <ImageViewerNoPreview attachment={attachment} />
      )}
    </ImageViewerScreen>
  );
}
