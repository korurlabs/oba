import * as React from "react";

import type { DescriptionEditorLabels } from "@/components/ticket/DescriptionEditorDom";
import type { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";

type ThemeColors = ReturnType<typeof useThemeColors>;

/** Every text the editor shows, in the app's language; the editor's WebView has no i18n. */
export function useDescriptionEditorLabels(): DescriptionEditorLabels {
  const { t } = useTranslation();
  return React.useMemo(
    () => ({
      toolbar: t("descriptionEditorToolbar"),
      bold: t("descriptionEditorBold"),
      italic: t("descriptionEditorItalic"),
      strikethrough: t("descriptionEditorStrikethrough"),
      code: t("descriptionEditorCode"),
      heading1: t("descriptionEditorHeading1"),
      heading2: t("descriptionEditorHeading2"),
      heading3: t("descriptionEditorHeading3"),
      bulletList: t("descriptionEditorBulletList"),
      orderedList: t("descriptionEditorOrderedList"),
      taskList: t("descriptionEditorTaskList"),
      blockquote: t("descriptionEditorBlockquote"),
      codeBlock: t("descriptionEditorCodeBlock"),
      horizontalRule: t("descriptionEditorHorizontalRule"),
      link: t("descriptionEditorLink"),
      image: t("descriptionEditorImage"),
      linkUrl: t("descriptionEditorLinkUrl"),
      linkUrlPlaceholder: t("descriptionEditorLinkUrlPlaceholder"),
      linkInsert: t("descriptionEditorLinkInsert"),
      linkRemove: t("descriptionEditorLinkRemove"),
      linkCancel: t("descriptionEditorLinkCancel"),
      formatCode: t("descriptionEditorFormatCode"),
      codeLanguage: t("descriptionEditorCodeLanguage"),
      placeholder: t("descriptionEditorPlaceholder"),
      imageDelete: t("descriptionEditorImageDelete"),
    }),
    [t]
  );
}

/** The ground the editor's page sits on and the text colour on it. */
export interface DescriptionEditorSurface {
  background: string;
  foreground: string;
}

/**
 * The CSS variables the editor's styles read, from the app's theme, so the
 * editor follows light and dark mode. The surface is what the page sits on:
 * the sheet's ground while editing, the card while reading.
 */
export function descriptionEditorTheme(
  colors: ThemeColors,
  surface: DescriptionEditorSurface
): Record<string, string> {
  return {
    "--accent": colors.accent,
    "--background": surface.background,
    "--border": colors.border,
    "--error": colors.destructive,
    "--foreground": surface.foreground,
    "--muted": colors.muted,
    "--muted-foreground": colors.mutedForeground,
    "--primary": colors.primary,
    "--ring": colors.ring,
    "--secondary": colors.secondary,
    "--success": colors.success,
  };
}
