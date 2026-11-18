import { useQueryClient, type QueryClient } from "@tanstack/react-query";
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
import { normalizeTicketKey } from "@/components/ticket/ticketModel";
import {
  descriptionImageSources,
  type DescriptionImageSource,
} from "@/components/ticket/descriptionImagesModel";
import { parseTicketImageTarget, type TicketImageTarget } from "@/components/ticket/ticketRoutes";
import { Button } from "@/components/ui/button";
import { attachmentKeys } from "@/hooks/queries/attachments";
import type { TicketRef } from "@/hooks/queries/ticketDetail";
import { ticketKeys } from "@/hooks/queries/tickets";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatAttachmentResponse } from "@/lib/schemas/chat";
import type { TicketDetail } from "@/lib/schemas/tickets";

/** What the viewer shows: an attachment of the ticket, or an image of its description. */
type ViewedImage =
  | { kind: "attachment"; attachment: ChatAttachmentResponse }
  | { kind: "description"; image: DescriptionImageSource };

/**
 * The image `target` names, read from the caches the ticket screen under the
 * viewer has filled: the ticket's description and its attachment list.
 */
function readViewedImage(
  queryClient: QueryClient,
  orgId: string,
  ticketKey: string,
  target: TicketImageTarget
): ViewedImage | null {
  const ref: TicketRef | undefined = queryClient.getQueryData<TicketRef>(
    ticketKeys.byKey(orgId, ticketKey)
  );
  if (!ref) return null;
  if ("attachmentId" in target) {
    const attachment: ChatAttachmentResponse | undefined = queryClient
      .getQueryData<ChatAttachmentResponse[]>(attachmentKeys.list(orgId, ref.ticketId))
      ?.find((item) => item.id === target.attachmentId);
    return attachment ? { kind: "attachment", attachment } : null;
  }
  const ticket: TicketDetail | undefined = queryClient.getQueryData<TicketDetail>(
    ticketKeys.detail(orgId, ref.boardId, ref.ticketId)
  );
  const image: DescriptionImageSource | undefined = descriptionImageSources(ticket?.description)[
    target.index
  ];
  return image?.src ? { kind: "description", image } : null;
}

/**
 * The full-screen viewer of a ticket image: an image of the description,
 * or an attachment, which also offers Download. A file without a preview
 * shows its notice with Download under it; an image that cannot be loaded
 * shows the image placeholder the description uses.
 */
export default function TicketImageViewer() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const params = useLocalSearchParams<{
    ticketKey: string;
    index?: string;
    attachmentId?: string;
  }>();
  // Read once: the signed URLs in the caches change on a refetch, and the
  // viewer keeps the one it opened with.
  const [viewed] = React.useState<ViewedImage | null>(() => {
    const ticketKey: string | null = normalizeTicketKey(params.ticketKey);
    const target: TicketImageTarget | null = parseTicketImageTarget(params);
    return orgId && ticketKey && target
      ? readViewedImage(queryClient, orgId, ticketKey, target)
      : null;
  });
  const [failed, setFailed] = React.useState<boolean>(false);
  const attachment: ChatAttachmentResponse | null =
    viewed?.kind === "attachment" ? viewed.attachment : null;
  const { download, downloading } = useAttachmentDownload(attachment);

  const close = (): void => {
    if (router.canGoBack()) router.back();
  };

  const previewable: boolean =
    viewed?.kind === "description" ||
    (!!attachment && isChatPreviewableImage(attachment.contentType));

  const content = (): React.ReactNode => {
    if (!viewed) return <ImageViewerNotice text={t("chatFileGone")} />;
    if (failed) return <ImageViewerNotice text={t("ticketDescriptionImage")} />;
    if (viewed.kind === "description") {
      const { image } = viewed;
      return (
        <ZoomableImage
          uri={image.src}
          cacheKey={image.attachmentId ?? undefined}
          width={image.width}
          height={image.height}
          onDismiss={close}
          onError={() => setFailed(true)}
          accessibilityLabel={
            image.alt ? t("chatImageA11y", { name: image.alt }) : t("ticketDescriptionImage")
          }
        />
      );
    }
    if (!previewable) return <ImageViewerNoPreview attachment={viewed.attachment} />;
    return (
      <ZoomableImage
        uri={viewed.attachment.url}
        cacheKey={viewed.attachment.id}
        width={viewed.attachment.width}
        height={viewed.attachment.height}
        onDismiss={close}
        onError={() => setFailed(true)}
        accessibilityLabel={t("chatImageA11y", { name: viewed.attachment.fileName })}
      />
    );
  };

  return (
    <ImageViewerScreen
      title={attachment?.fileName ?? (viewed?.kind === "description" ? viewed.image.alt : "")}
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
      {content()}
    </ImageViewerScreen>
  );
}
