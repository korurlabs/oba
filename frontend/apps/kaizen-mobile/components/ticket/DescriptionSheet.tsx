import { useQueryClient } from "@tanstack/react-query";
import type { DOMProps } from "expo/dom";
import * as React from "react";
import { ActivityIndicator, Platform, View } from "react-native";

import { useKeyboardOverlap } from "@/components/chat/useKeyboardOverlap";
import { ActionSheet } from "@/components/kaizen/ActionSheet";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import DescriptionEditorDom, {
  type DescriptionEditorError,
  type DescriptionEditorLabels,
} from "@/components/ticket/DescriptionEditorDom";
import {
  descriptionEditorTheme,
  useDescriptionEditorLabels,
} from "@/components/ticket/descriptionEditorTheme";
import {
  MissingPendingImageError,
  orphanAttachmentIds,
} from "@/components/ticket/descriptionImagesModel";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { descriptionTooLong } from "@/components/ticket/newTicketModel";
import { attachmentMessageText } from "@/components/ticket/TicketAttachments";
import { ticketAttachmentErrorMessage } from "@/components/ticket/ticketAttachmentsModel";
import { descriptionForSave } from "@/components/ticket/ticketFieldsModel";
import { useDescriptionImages } from "@/components/ticket/useDescriptionImages";
import { useDraftTicketField, useSavedTicketField } from "@/components/ticket/useFieldSheet";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { deleteAttachment, invalidateTicketAttachments } from "@/hooks/queries/attachments";
import { useTranslation } from "@/i18n";
import type { ApiError } from "@/lib/api";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { TICKET_DESCRIPTION_MAX, type PendingImage } from "@/lib/schemas/tickets";

/** How long the editor may take to start before the sheet offers Retry. */
const EDITOR_READY_TIMEOUT_MS = 10_000;

type TranslateFn = ReturnType<typeof useTranslation>["t"];

/** Why the images could not be saved, in the words of the attachments section. */
function commitErrorText(t: TranslateFn, error: unknown): string {
  if (error instanceof MissingPendingImageError) return t("descriptionImageMissing");
  return attachmentMessageText(t, ticketAttachmentErrorMessage(error, "upload"));
}

function errorText(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/** Reading-view taps; the editing sheet has none, because the editor handles them itself. */
const noReadingAction = async (): Promise<void> => undefined;

type DescriptionSheetProps = {
  /** The description now; undefined while the ticket loads. */
  value: string | undefined;
  /** The images of the description not uploaded yet, such as those of a New ticket draft. */
  initialImages?: readonly PendingImage[];
  /**
   * The saved ticket that Done uploads the description's new images to
   * before the description is handed over; null for New ticket, whose
   * images stay pending until Create.
   */
  ticketId: string | null;
  /**
   * Receives the description on Done, only when it changed and fits the
   * limit, with the pending images it still shows (none once uploaded); an
   * emptied text arrives as "". `onInvalid` shows a refused text under the editor.
   */
  onChange: (
    description: string,
    images: readonly PendingImage[],
    onInvalid: (message: string) => void
  ) => void;
  /** A spinner on Done while the change is saved. */
  saving?: boolean;
  /** Closes the sheet once true, such as when the change has landed. */
  closed: boolean;
  /** Why the ticket could not be read, with its retry. */
  loadError?: unknown;
  onRetryLoad?: () => void;
};

/**
 * The description editor of a ticket: the web's rich editor with its toolbar
 * and images inline, so a description edited on the phone is stored as the
 * web stores it. New images stay on the phone until Done, which uploads them
 * one by one and writes their tags as the web does. Done without a change
 * writes nothing. A text over the limit, a refused upload or a refused save
 * shows its message under the editor and keeps the sheet open.
 */
export function DescriptionSheet({
  value,
  initialImages,
  ticketId,
  onChange,
  saving = false,
  closed,
  loadError,
  onRetryLoad,
}: DescriptionSheetProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const labels: DescriptionEditorLabels = useDescriptionEditorLabels();
  const images = useDescriptionImages(initialImages);
  const { remove: removeImage } = images;
  // iOS: the sheet itself ends its body at the keyboard (`avoidKeyboard`):
  // a view inside the native form sheet measures its window position without
  // the sheet's offset and would stop short of the keyboard. Android keeps
  // the measured overlap.
  const { overlap, viewRef, onLayout } = useKeyboardOverlap();
  const keyboardPadding: number = Platform.OS === "ios" ? 0 : overlap;

  // The text the editor starts from: the description once loaded, or after
  // a restart, the text as last edited.
  const [seed, setSeed] = React.useState<string | undefined>(value);
  if (seed === undefined && value !== undefined) setSeed(value);
  const [mountKey, setMountKey] = React.useState<number>(0);
  const [ready, setReady] = React.useState<boolean>(false);
  const [loadFailed, setLoadFailed] = React.useState<boolean>(false);
  const [changed, setChanged] = React.useState<boolean>(false);
  const [committing, setCommitting] = React.useState<boolean>(false);
  const [progress, setProgress] = React.useState<{ current: number; total: number } | null>(null);
  const [fieldError, setFieldError] = React.useState<string | null>(null);
  const [unchangedDone, setUnchangedDone] = React.useState<boolean>(false);
  // The editor's Markdown when it loaded, and after the latest edit. Refs,
  // so typing does not re-render the sheet and resend the editor's props.
  const baseline = React.useRef<string | null>(null);
  const markdown = React.useRef<string | null>(null);

  const background: string = Platform.OS === "ios" ? colors.screen : colors.cardBackground;
  const theme = React.useMemo(
    () => descriptionEditorTheme(colors, { background, foreground: colors.foreground }),
    [colors, background]
  );

  React.useEffect(() => {
    if (seed === undefined || ready || loadFailed) return;
    const timer = setTimeout(() => {
      console.warn("[description] the editor did not start in time");
      setLoadFailed(true);
    }, EDITOR_READY_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [seed, ready, loadFailed, mountKey]);

  const failLoad = React.useCallback((reason: string) => {
    console.warn("[description] the editor stopped", reason);
    setLoadFailed(true);
  }, []);

  const restart = (): void => {
    if (markdown.current !== null) setSeed(markdown.current);
    setReady(false);
    setLoadFailed(false);
    setMountKey((key) => key + 1);
  };

  const onReady = React.useCallback(async (loaded: string): Promise<void> => {
    if (baseline.current === null) baseline.current = loaded;
    if (markdown.current === null) markdown.current = loaded;
    setReady(true);
  }, []);

  const onEdit = React.useCallback(async (next: string): Promise<void> => {
    markdown.current = next;
    setChanged(baseline.current !== null && next !== baseline.current);
    setFieldError(null);
  }, []);

  const onEditorError = React.useCallback(
    async (error: DescriptionEditorError): Promise<void> => {
      console.warn(`[description] editor action ${error.action} failed`, error.message);
      toast.error(t("descriptionEditorFailed"));
    },
    [t]
  );

  const onImageDelete = React.useCallback(
    async (src: string): Promise<void> => removeImage(src),
    [removeImage]
  );

  const dom = React.useMemo<DOMProps>(
    () => ({
      scrollEnabled: true,
      keyboardDisplayRequiresUserAction: false,
      hideKeyboardAccessoryView: true,
      style: { flex: 1 },
      onError: (event) => failLoad(event.nativeEvent.description),
      onContentProcessDidTerminate: () => failLoad("content process terminated"),
      onRenderProcessGone: () => failLoad("render process gone"),
    }),
    [failLoad]
  );

  const done = async (): Promise<void> => {
    const text: string | null = markdown.current;
    if (!ready || text === null) return;
    if (!changed) {
      setUnchangedDone(true);
      return;
    }
    const description: string = descriptionForSave(text);
    if (descriptionTooLong(description)) {
      setFieldError(t("newTicketDescriptionTooLong", { max: TICKET_DESCRIPTION_MAX }));
      return;
    }
    if (ticketId === null) {
      onChange(description, images.reconcile(description), setFieldError);
      return;
    }
    setCommitting(true);
    let committed: string;
    try {
      committed = await images.commit(ticketId, description, (current, total) =>
        setProgress({ current, total })
      );
    } catch (error) {
      console.warn("[description] uploading the description images failed", errorText(error));
      setFieldError(commitErrorText(t, error));
      return;
    } finally {
      setCommitting(false);
      setProgress(null);
    }
    // Each uploaded image adds its storage URL, which can push the text over the limit.
    if (descriptionTooLong(committed)) {
      setFieldError(t("ticketDescriptionTooLongAfterImages", { max: TICKET_DESCRIPTION_MAX }));
      return;
    }
    onChange(committed, [], setFieldError);
  };

  return (
    <BottomSheet
      title={t("ticketDescriptionEditTitle")}
      confirmLabel={t("ticketDone")}
      onConfirm={() => void done()}
      confirmDisabled={value === undefined || !ready}
      confirmLoading={saving || committing}
      dirty={changed && !closed}
      avoidKeyboard
    >
      <DismissWhen when={closed || unchangedDone} />
      {seed !== undefined ? (
        <View
          ref={viewRef}
          onLayout={onLayout}
          collapsable={false}
          className="flex-1"
          // Keeps the editor, and the caret in it, above the keyboard.
          style={{ paddingBottom: keyboardPadding }}
        >
          <View className="flex-1">
            {loadFailed ? (
              <InlineError
                message={t("ticketEditorLoadFailed")}
                onRetry={restart}
                className="m-4"
              />
            ) : (
              <>
                <DescriptionEditorDom
                  key={mountKey}
                  initialMarkdown={seed}
                  editable
                  labels={labels}
                  theme={theme}
                  onReady={onReady}
                  onChange={onEdit}
                  onAddImage={images.add}
                  onPasteImage={images.addPasted}
                  onImageDelete={onImageDelete}
                  resolveImage={images.resolve}
                  onOpenImage={noReadingAction}
                  onOpenLink={noReadingAction}
                  onError={onEditorError}
                  dom={dom}
                />
                {ready ? null : (
                  <View
                    className="absolute inset-0 items-center justify-center"
                    style={{ backgroundColor: background }}
                  >
                    <ActivityIndicator color={colors.mutedForeground} />
                  </View>
                )}
              </>
            )}
          </View>
          {progress ? (
            <Text
              accessibilityLiveRegion="polite"
              className="px-4 pt-2 font-sans text-footnote text-muted-foreground"
            >
              {t("descriptionImageUploading", progress)}
            </Text>
          ) : null}
          {fieldError ? (
            <Text
              accessibilityLiveRegion="polite"
              className="px-4 pt-2 font-sans text-footnote text-destructive"
            >
              {fieldError}
            </Text>
          ) : null}
        </View>
      ) : loadError ? (
        <InlineError message={t("ticketLoadError")} onRetry={onRetryLoad} className="m-4" />
      ) : (
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      )}
      <ActionSheet {...images.picker} cancelLabel={t("kitCancel")} />
    </BottomSheet>
  );
}

/** Deletes the attachments of removed images, all at once; gives the ids that failed. */
async function deleteRemovedImages(ids: readonly string[]): Promise<string[]> {
  const results = await Promise.allSettled(ids.map((id) => deleteAttachment(id)));
  return ids.filter((_id, index) => results[index]?.status === "rejected");
}

/**
 * The description editor over a saved ticket: Done uploads the new images,
 * saves the description, and then deletes the images the edit removed.
 */
export function TicketDescriptionSheet() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const { ticket, error, retry, saving, saved, save } = useSavedTicketField();
  // The images the description being saved no longer shows, deleted once it has landed.
  const removed = React.useRef<{ ticketId: string; ids: string[] } | null>(null);

  React.useEffect(() => {
    const pending = removed.current;
    if (!saved || !pending) return;
    removed.current = null;
    if (pending.ids.length === 0) return;
    void deleteRemovedImages(pending.ids).then((failed: string[]) => {
      // The description is saved either way, so the sheet still closes.
      if (failed.length > 0) {
        console.warn("[description] deleting removed images failed", failed);
        toast.error(t("ticketImagesCleanupFailed"));
      }
      void invalidateTicketAttachments(queryClient, orgId, pending.ticketId);
    });
  }, [orgId, queryClient, saved, t]);

  return (
    <DescriptionSheet
      value={ticket ? (ticket.description ?? "") : undefined}
      ticketId={ticket?.id ?? null}
      onChange={(description, _images, onInvalid) => {
        if (!ticket) return;
        removed.current = {
          ticketId: ticket.id,
          ids: orphanAttachmentIds(ticket.description, description),
        };
        save(
          { body: { description }, optimistic: (detail) => ({ ...detail, description }) },
          (apiError: ApiError) => onInvalid(apiError.fieldErrors.description ?? apiError.message)
        );
      }}
      saving={saving}
      closed={saved}
      loadError={error}
      onRetryLoad={() => void retry()}
    />
  );
}

/**
 * The description editor over the New ticket draft: Done keeps the text and
 * its pending images for Create, which uploads them.
 */
export function DraftDescriptionSheet() {
  const { fields, save, closed } = useDraftTicketField();
  return (
    <DescriptionSheet
      value={fields?.description}
      initialImages={fields?.images}
      ticketId={null}
      onChange={(description, images) => save({ description, images })}
      closed={closed}
    />
  );
}
