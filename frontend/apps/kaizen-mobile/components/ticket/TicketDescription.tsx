import type { DOMProps } from "expo/dom";
import { useRouter } from "expo-router";
import * as React from "react";
import { Pressable, StyleSheet, View, type LayoutChangeEvent } from "react-native";

import DescriptionEditorDom, {
  type DescriptionEditorError,
  type DescriptionEditorLabels,
} from "@/components/ticket/DescriptionEditorDom";
import {
  descriptionEditorTheme,
  useDescriptionEditorLabels,
} from "@/components/ticket/descriptionEditorTheme";
import { descriptionText } from "@/components/ticket/ticketModel";
import { ticketImageHref } from "@/components/ticket/ticketRoutes";
import { Card } from "@/components/ui/card";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useOpenKaizenLink } from "@/hooks/useOpenKaizenLink";
import { useTranslation } from "@/i18n";
import { kaizenWebUrl } from "@/lib/browser";

type TicketDescriptionProps = {
  description: string | null | undefined;
  /** Opens the description editor; without it there is no Edit link. */
  onEdit?: () => void;
  /** Why the description was refused, under the text. */
  error?: string | null;
  /**
   * The saved ticket's key. A tapped image opens the ticket's image viewer,
   * which reads the saved ticket; without a key a tap opens nothing.
   */
  ticketKey?: string | null;
  /**
   * A displayable URI for an image that is not uploaded yet, such as one in
   * the New ticket draft; without it such an image shows as its alt text.
   */
  resolveImage?: (src: string) => Promise<string | null>;
};

/** A storage URL with its query string, which holds the read signature. */
const SIGNED_URL_PATTERN = /(https?:\/\/[^\s"'<>()?]*)\?[^\s"'<>()]*/g;

/** An `<img>` tag or a Markdown image. */
const IMAGE_PATTERN = /<img\b[^>]*>|!\[[^\]]*\]\([^)]*\)/gi;

/**
 * What the rendered description depends on: the text with the query strings
 * of image URLs left out. The API signs every image URL again on each read,
 * so a refetch changes the description without changing what it shows; the
 * rendered view is rebuilt only when this changes.
 */
function descriptionRenderKey(description: string): string {
  return description.replace(IMAGE_PATTERN, (image: string) =>
    image.replace(SIGNED_URL_PATTERN, "$1")
  );
}

/** A link as written in the description, made absolute the way the web resolves it. */
function absoluteHref(href: string): string {
  return href.startsWith("/") && !href.startsWith("//") ? kaizenWebUrl(href) : href;
}

/** No image of the description waits for an upload. */
async function noImage(): Promise<string | null> {
  return null;
}

/** The reading view adds no images, so these actions are never called. */
async function addNoImage(): Promise<null> {
  return null;
}

async function ignoreImageDelete(): Promise<void> {
  return undefined;
}

async function ignoreMarkdown(): Promise<void> {
  return undefined;
}

type RenderedDescriptionProps = {
  description: string;
  /** The plain text shown until the rendered view is ready, and if it fails. */
  text: string;
  ticketKey: string | null;
  resolveImage: (src: string) => Promise<string | null>;
};

/**
 * The description as the web renders it: the web's editor in a WebView,
 * read-only. The WebView loads behind the plain text and takes its place once
 * the editor is up and has its height, so a slow start never leaves a gap;
 * if the WebView fails, the plain text stays.
 */
function RenderedDescription({
  description,
  text,
  ticketKey,
  resolveImage,
}: RenderedDescriptionProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const { openLink } = useOpenKaizenLink();
  const renderKey: string = descriptionRenderKey(description);
  const [readyKey, setReadyKey] = React.useState<string | null>(null);
  const [failedKey, setFailedKey] = React.useState<string | null>(null);
  const [height, setHeight] = React.useState<number>(0);
  const failed: boolean = failedKey === renderKey;
  const ready: boolean = !failed && readyKey === renderKey && height > 0;

  // The page takes the card's colours, so the rendered text sits on the card as the plain text does.
  const theme = React.useMemo<Record<string, string>>(
    () =>
      descriptionEditorTheme(colors, {
        background: colors.card,
        foreground: colors.cardForeground,
      }),
    [colors]
  );

  // Read-only, the editor shows no toolbar, link row, code block controls or
  // delete buttons; its accessible name names the description, not the prompt to write one.
  const editorLabels: DescriptionEditorLabels = useDescriptionEditorLabels();
  const labels = React.useMemo<DescriptionEditorLabels>(
    () => ({ ...editorLabels, placeholder: t("ticketDescription") }),
    [editorLabels, t]
  );

  const onReady = React.useCallback(async (): Promise<void> => {
    setReadyKey(renderKey);
  }, [renderKey]);

  const onOpenImage = React.useCallback(
    async (index: number): Promise<void> => {
      // A new ticket's images are not uploaded yet, and the viewer reads saved tickets.
      if (!ticketKey) return;
      router.push(ticketImageHref(ticketKey, { index }));
    },
    [router, ticketKey]
  );

  // openLink logs and toasts a link that cannot be opened.
  const onOpenLink = React.useCallback(
    async (href: string): Promise<void> => openLink(absoluteHref(href)),
    [openLink]
  );

  const onEditorError = React.useCallback(async (error: DescriptionEditorError): Promise<void> => {
    // The actions above surface their own failures; what is left is an image
    // the editor could not resolve, which it already shows as its alt text.
    console.warn(`[TicketDescription] ${error.action} failed`, error.message);
  }, []);

  const dom = React.useMemo<DOMProps>(
    () => ({
      matchContents: true,
      scrollEnabled: false,
      onError: (event) => {
        // The plain text stays in place of the rendered view.
        console.warn("[TicketDescription] rendered view failed", event.nativeEvent.description);
        setFailedKey(renderKey);
      },
    }),
    [renderKey]
  );

  const onLayout = React.useCallback((event: LayoutChangeEvent) => {
    setHeight(event.nativeEvent.layout.height);
  }, []);

  return (
    <View className={ready ? undefined : "overflow-hidden"}>
      {failed ? null : (
        <View
          style={ready ? undefined : styles.behind}
          pointerEvents={ready ? "auto" : "none"}
          accessibilityElementsHidden={!ready}
          importantForAccessibility={ready ? "auto" : "no-hide-descendants"}
          onLayout={onLayout}
        >
          <DescriptionEditorDom
            key={renderKey}
            initialMarkdown={description}
            editable={false}
            labels={labels}
            theme={theme}
            onReady={onReady}
            onChange={ignoreMarkdown}
            onAddImage={addNoImage}
            onPasteImage={addNoImage}
            onImageDelete={ignoreImageDelete}
            resolveImage={resolveImage}
            onOpenImage={onOpenImage}
            onOpenLink={onOpenLink}
            onError={onEditorError}
            dom={dom}
          />
        </View>
      )}
      {ready ? null : (
        <Text variant="body" selectable className="bg-card">
          {text}
        </Text>
      )}
    </View>
  );
}

/**
 * The ticket description, rendered as Kaizen web renders it: formatting,
 * lists, code, tables, links and images where they sit. A tapped image opens
 * the ticket's image viewer, a tapped link opens as other links in the app do.
 * Until the rendered view is ready the plain text shows, with a marker where
 * an image sits.
 */
export function TicketDescription({
  description,
  onEdit,
  error,
  ticketKey,
  resolveImage,
}: TicketDescriptionProps) {
  const { t } = useTranslation();
  const text: string = descriptionText(description, t("ticketDescriptionImage"));

  return (
    <Card className="mx-4">
      <View className="flex-row items-center justify-between">
        <Text variant="headline">{t("ticketDescription")}</Text>
        {onEdit ? (
          <Pressable accessibilityRole="button" onPress={onEdit} hitSlop={10}>
            <Text variant="subhead" className="text-link">
              {t("ticketEdit")}
            </Text>
          </Pressable>
        ) : null}
      </View>
      {description && text ? (
        <RenderedDescription
          description={description}
          text={text}
          ticketKey={ticketKey ?? null}
          resolveImage={resolveImage ?? noImage}
        />
      ) : (
        <Text variant="body" className="text-muted-foreground">
          {t("ticketDescriptionEmpty")}
        </Text>
      )}
      {error ? (
        <Text variant="footnote" className="text-destructive">
          {error}
        </Text>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  // Laid out at full width under the plain text, which covers it until it is ready.
  behind: { position: "absolute", top: 0, left: 0, right: 0 },
});
