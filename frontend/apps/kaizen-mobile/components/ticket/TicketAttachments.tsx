import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { CircleAlert, Camera, FileText, Images, Trash2, Upload, X } from "lucide-react-native";
import * as React from "react";
import { Alert, Pressable, View } from "react-native";

import { formatChatFileSize, isChatPreviewableImage } from "@/components/chat/chatAttachments";
import {
  pickChatFiles,
  type ChatFileSource,
  type ChatPickResult,
} from "@/components/chat/chatFilePickers";
import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import {
  ticketAttachmentErrorMessage,
  ticketAttachmentsProblem,
  visibleTicketAttachments,
  type TicketAttachmentMessage,
} from "@/components/ticket/ticketAttachmentsModel";
import { ticketImageHref } from "@/components/ticket/ticketRoutes";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Skeleton, SkeletonGroup } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import {
  useDeleteAttachment,
  useTicketAttachments,
  useUploadTicketAttachments,
} from "@/hooks/queries/attachments";
import { useTranslation, type TranslationKey } from "@/i18n";
import { openInAppBrowser } from "@/lib/browser";
import type { ChatAttachmentResponse, ChatLocalFile } from "@/lib/schemas/chat";
import { TICKET_ATTACHMENT_TYPES } from "@/lib/schemas/tickets";

/** Side of a row's thumbnail or file icon box, the web list's 40 px. */
const THUMB_STYLE = { width: 40, height: 40 } as const;

/**
 * Files one pick may return. The web has no count limit, only the 12 MB
 * batch; the cap keeps the Android photo picker inside its own maximum, and
 * a second pick adds more.
 */
const PICK_LIMIT = 10;

/** Rows drawn while the list loads, as the web's skeleton. */
const SKELETON_ROWS = [0, 1, 2] as const;

type TranslateFn = ReturnType<typeof useTranslation>["t"];

/**
 * The text of a staging refusal or a failed request. A server refusal may
 * not name its file, so the sentence then speaks of "one of the files".
 */
export function attachmentMessageText(t: TranslateFn, message: TicketAttachmentMessage): string {
  const key: TranslationKey = message.key;
  return t(key, {
    name: message.params.name ?? t("ticketAttachmentsSomeFile"),
    max: message.params.max,
  });
}

type TicketAttachmentsProps = {
  /** The ticket the files belong to. Without one (a ticket not yet created) files are only staged. */
  ticketId?: string;
  /** The ticket's key, for the image viewer route. */
  ticketKey?: string;
  /** The ticket's description; the images it shows are not listed again. */
  description?: string | null;
  /** Board write rights: upload and delete show only with them, as the server allows. */
  canWrite: boolean;
  /**
   * For a ticket not yet created: receives the staged files on every change.
   * The caller uploads them once the ticket exists and reports the outcome.
   */
  onStagedFilesChange?: (files: readonly ChatLocalFile[]) => void;
};

/**
 * The ticket's attachments, as the web's Attachments tab: the files (without
 * the description's own images), each opening in the image viewer or, for a
 * PDF, in the in-app browser, and for a board writer a picker that stages
 * PNG, JPEG and PDF files with a preview and uploads them in one request of
 * at most 12 MB. Without a ticket id the section only stages files for its
 * caller, and shows nothing to a reader who may not write.
 */
export function TicketAttachments({
  ticketId,
  ticketKey,
  description,
  canWrite,
  onStagedFilesChange,
}: TicketAttachmentsProps) {
  const { t } = useTranslation();
  if (!ticketId && (!onStagedFilesChange || !canWrite)) return null;
  return (
    <Card className="mx-4 gap-3">
      <Text variant="headline">{t("ticketAttachments")}</Text>
      {ticketId ? (
        <AttachmentList
          ticketId={ticketId}
          ticketKey={ticketKey}
          description={description}
          canWrite={canWrite}
        />
      ) : null}
      {canWrite ? (
        <AttachmentUpload ticketId={ticketId} onStagedFilesChange={onStagedFilesChange} />
      ) : null}
    </Card>
  );
}

type AttachmentListProps = {
  ticketId: string;
  ticketKey?: string;
  description?: string | null;
  canWrite: boolean;
};

/**
 * The uploaded files. A failed read shows its reason with Retry instead of
 * the empty state, which would tell the user the ticket has no files.
 */
function AttachmentList({ ticketId, ticketKey, description, canWrite }: AttachmentListProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const attachments = useTicketAttachments(ticketId);
  const remove = useDeleteAttachment(ticketId);
  const [deletingId, setDeletingId] = React.useState<string | null>(null);

  const open = React.useCallback(
    (attachment: ChatAttachmentResponse): void => {
      if (isChatPreviewableImage(attachment.contentType) && ticketKey) {
        router.push(ticketImageHref(ticketKey, { attachmentId: attachment.id }));
        return;
      }
      openInAppBrowser(attachment.url).catch(() => {
        // openInAppBrowser has logged the cause.
        toast.error(t("ticketAttachmentOpenFailed"));
      });
    },
    [router, t, ticketKey]
  );

  const confirmDelete = React.useCallback(
    (attachment: ChatAttachmentResponse): void => {
      Alert.alert(t("ticketAttachmentsDeleteTitle"), t("ticketAttachmentsDeleteBody"), [
        { text: t("kitCancel"), style: "cancel" },
        {
          text: t("ticketAttachmentsDeleteConfirm"),
          style: "destructive",
          onPress: () => {
            setDeletingId(attachment.id);
            remove.mutate(attachment.id, {
              onSuccess: () => toast.success(t("ticketAttachmentsDeleted")),
              onError: (error: Error) =>
                toast.error(
                  attachmentMessageText(t, ticketAttachmentErrorMessage(error, "delete"))
                ),
              onSettled: () => setDeletingId(null),
            });
          },
        },
      ]);
    },
    [remove, t]
  );

  if (attachments.isPending) {
    return (
      <SkeletonGroup className="gap-2">
        {SKELETON_ROWS.map((row) => (
          <View
            key={row}
            className="flex-row items-center gap-3 rounded-md border border-border p-2"
          >
            <Skeleton width={40} height={40} />
            <View className="flex-1 gap-1.5">
              <Skeleton width="60%" height={14} />
              <Skeleton width="25%" height={12} />
            </View>
          </View>
        ))}
      </SkeletonGroup>
    );
  }

  if (attachments.isError) {
    return (
      <View
        accessibilityRole="alert"
        className="items-center gap-2 rounded-md border border-dashed border-destructive p-5"
      >
        <Icon as={CircleAlert} size={28} className="text-destructive" />
        <Text variant="footnote" className="text-center text-destructive">
          {attachmentMessageText(t, ticketAttachmentErrorMessage(attachments.error, "list"))}
        </Text>
        <Button
          variant="outline"
          size="sm"
          loading={attachments.isFetching}
          onPress={() => void attachments.refetch()}
        >
          {t("ticketRetry")}
        </Button>
      </View>
    );
  }

  const visible: ChatAttachmentResponse[] = visibleTicketAttachments(attachments.data, description);
  if (visible.length === 0) {
    return (
      <View className="items-center gap-2 rounded-md border border-dashed border-border p-5">
        <Icon as={FileText} size={28} className="text-muted-foreground" />
        <Text variant="footnote" className="text-muted-foreground">
          {t("ticketAttachmentsEmpty")}
        </Text>
      </View>
    );
  }

  return (
    <View className="gap-2">
      {visible.map((attachment) => (
        <AttachmentRow
          key={attachment.id}
          attachment={attachment}
          deleting={remove.isPending && deletingId === attachment.id}
          onOpen={open}
          onDelete={canWrite ? confirmDelete : undefined}
        />
      ))}
    </View>
  );
}

type AttachmentRowProps = {
  attachment: ChatAttachmentResponse;
  deleting: boolean;
  onOpen: (attachment: ChatAttachmentResponse) => void;
  /** Without it the row has no delete button. */
  onDelete?: (attachment: ChatAttachmentResponse) => void;
};

/** One uploaded file: thumbnail or file icon, name and size; a tap opens it. */
function AttachmentRow({ attachment, deleting, onOpen, onDelete }: AttachmentRowProps) {
  const { t } = useTranslation();
  const size: string = formatChatFileSize(attachment.fileSize);
  return (
    <View className="flex-row items-center gap-1 rounded-md border border-border pl-2">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t("ticketAttachmentsOpen", { name: attachment.fileName })}
        onPress={() => onOpen(attachment)}
        className="min-h-14 flex-1 flex-row items-center gap-3 py-2 active:opacity-70"
      >
        <FileThumbnail
          uri={attachment.url}
          cacheKey={attachment.id}
          isImage={isChatPreviewableImage(attachment.contentType)}
        />
        <View className="flex-1">
          <Text variant="subhead" numberOfLines={1}>
            {attachment.fileName}
          </Text>
          {size ? (
            <Text variant="caption" className="text-muted-foreground">
              {size}
            </Text>
          ) : null}
        </View>
      </Pressable>
      {onDelete ? (
        <IconButton
          icon={Trash2}
          label={t("ticketAttachmentsDelete", { name: attachment.fileName })}
          iconClassName="text-destructive"
          disabled={deleting}
          onPress={() => onDelete(attachment)}
        />
      ) : (
        <View className="w-2" />
      )}
    </View>
  );
}

type FileThumbnailProps = {
  uri: string;
  /** Keeps the cached image across the signed URL changing on every read. */
  cacheKey?: string;
  isImage: boolean;
};

/** The 40 px square of a row: the image itself, or a file icon. */
function FileThumbnail({ uri, cacheKey, isImage }: FileThumbnailProps) {
  return (
    <View
      className="items-center justify-center overflow-hidden rounded-md bg-muted"
      style={THUMB_STYLE}
    >
      {isImage ? (
        <Image
          source={cacheKey ? { uri, cacheKey } : { uri }}
          style={THUMB_STYLE}
          contentFit="cover"
          accessibilityIgnoresInvertColors
        />
      ) : (
        <Icon as={FileText} size={20} className="text-muted-foreground" />
      )}
    </View>
  );
}

type AttachmentUploadProps = {
  ticketId?: string;
  onStagedFilesChange?: (files: readonly ChatLocalFile[]) => void;
};

/**
 * Picks files from the camera, the photo library or the files app, refuses
 * what the server would (type, 12 MB per file, 12 MB for the batch) with a
 * message per file, and shows the rest for review before one upload. A
 * refused upload keeps the staged files, so the user can remove the one the
 * message names and try again.
 */
function AttachmentUpload({ ticketId, onStagedFilesChange }: AttachmentUploadProps) {
  const { t } = useTranslation();
  const upload = useUploadTicketAttachments();
  const [staged, setStaged] = React.useState<ChatLocalFile[]>([]);
  const [pickerOpen, setPickerOpen] = React.useState<boolean>(false);
  const [progress, setProgress] = React.useState<number>(0);
  const uploading: boolean = upload.isPending;

  // Held in a ref so a caller passing an inline function does not re-run the report.
  const onStagedRef = React.useRef(onStagedFilesChange);
  onStagedRef.current = onStagedFilesChange;
  React.useEffect(() => {
    if (!ticketId) onStagedRef.current?.(staged);
  }, [staged, ticketId]);

  // The batch limit counts what is staged when the picker returns, not when it opened.
  const stagedRef = React.useRef<ChatLocalFile[]>(staged);
  stagedRef.current = staged;
  const stage = React.useCallback(
    (picked: readonly ChatLocalFile[]): void => {
      const current: ChatLocalFile[] = stagedRef.current;
      const { accepted, refused } = ticketAttachmentsProblem(current, picked);
      if (refused.length > 0) {
        toast.error(refused.map((message) => attachmentMessageText(t, message)).join("\n"));
      }
      if (accepted.length === 0) return;
      const next: ChatLocalFile[] = [...current, ...accepted];
      stagedRef.current = next;
      setStaged(next);
    },
    [t]
  );

  const pick = React.useCallback(
    (source: ChatFileSource): void => {
      pickChatFiles(source, PICK_LIMIT, { documentTypes: TICKET_ATTACHMENT_TYPES })
        .then((result: ChatPickResult) => {
          if (result.kind === "cameraDenied") {
            toast.error(t("chatCameraDenied"));
            return;
          }
          if (result.files.length > 0) stage(result.files);
        })
        .catch((error: unknown) => {
          console.warn(
            "[ticket] picking attachments failed",
            error instanceof Error ? error.message : error
          );
          toast.error(t("chatAttachFailed"));
        });
    },
    [stage, t]
  );

  const pickActions: ActionSheetAction[] = [
    { label: t("chatAttachCamera"), icon: Camera, onPress: () => pick("camera") },
    { label: t("chatAttachPhotos"), icon: Images, onPress: () => pick("library") },
    { label: t("chatAttachDocuments"), icon: FileText, onPress: () => pick("files") },
  ];

  const unstage = (key: string): void =>
    setStaged((current) => current.filter((file) => file.key !== key));

  const send = (): void => {
    if (!ticketId || staged.length === 0) return;
    setProgress(0);
    upload.mutate(
      { ticketId, files: staged, onProgress: setProgress },
      {
        onSuccess: () => {
          setStaged([]);
          toast.success(t("ticketAttachmentsUploaded"));
        },
        onError: (error: Error) =>
          toast.error(attachmentMessageText(t, ticketAttachmentErrorMessage(error, "upload"))),
      }
    );
  };

  return (
    <View className="gap-3">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t("ticketAttachmentsSelectFiles")}
        accessibilityHint={t("ticketAttachmentsSupportedFormats")}
        accessibilityState={{ disabled: uploading }}
        disabled={uploading}
        onPress={() => setPickerOpen(true)}
        className={
          uploading
            ? "items-center gap-2 rounded-lg border-2 border-dashed border-border p-5 opacity-60"
            : "items-center gap-2 rounded-lg border-2 border-dashed border-border p-5 active:bg-muted"
        }
      >
        <View className="size-10 items-center justify-center rounded-full bg-muted">
          <Icon as={Upload} size={20} className="text-muted-foreground" />
        </View>
        <Text variant="caption" className="text-muted-foreground">
          {t("ticketAttachmentsSupportedFormats")}
        </Text>
        <View className="rounded-md border border-border px-3 py-1.5">
          <Text variant="subhead">{t("ticketAttachmentsSelectFiles")}</Text>
        </View>
      </Pressable>
      {staged.length > 0 ? (
        <View accessibilityLabel={t("ticketAttachmentsStaged")} className="gap-2">
          {staged.map((file) => (
            <StagedFileRow key={file.key} file={file} disabled={uploading} onRemove={unstage} />
          ))}
          {ticketId ? (
            <Button block size="sm" icon={Upload} loading={uploading} onPress={send}>
              {uploading
                ? t("ticketAttachmentsUploading", { percent: Math.round(progress * 100) })
                : t("ticketAttachmentsUpload", { count: staged.length })}
            </Button>
          ) : null}
        </View>
      ) : null}
      <ActionSheet
        visible={pickerOpen}
        onClose={() => setPickerOpen(false)}
        title={t("ticketAttachmentsSelectFiles")}
        actions={pickActions}
        cancelLabel={t("kitCancel")}
      />
    </View>
  );
}

type StagedFileRowProps = {
  file: ChatLocalFile;
  disabled: boolean;
  onRemove: (key: string) => void;
};

/** A file waiting for the upload: its preview, name and size, and a remove button. */
function StagedFileRow({ file, disabled, onRemove }: StagedFileRowProps) {
  const { t } = useTranslation();
  const size: string = formatChatFileSize(file.size);
  return (
    <View
      accessibilityLabel={file.name}
      className="min-h-14 flex-row items-center gap-3 rounded-md border border-border py-2 pl-2"
    >
      <FileThumbnail uri={file.uri} isImage={isChatPreviewableImage(file.mimeType)} />
      <View className="flex-1">
        <Text variant="subhead" numberOfLines={1}>
          {file.name}
        </Text>
        {size ? (
          <Text variant="caption" className="text-muted-foreground">
            {size}
          </Text>
        ) : null}
      </View>
      <IconButton
        icon={X}
        label={t("chatRemoveFile", { name: file.name })}
        iconClassName="text-muted-foreground"
        disabled={disabled}
        onPress={() => onRemove(file.key)}
      />
    </View>
  );
}
