"use dom";

// The web editor's document styles first, then this page's. Imported here and
// not through an `@import` in the page's stylesheet, which the development
// bundle never loads.
import "@oba/shared/styles/markdown-editor.css";
import "./descriptionEditor.css";

import { EditorContent, ReactNodeViewRenderer, useEditor, type Editor } from "@tiptap/react";
import type { EditorView } from "@tiptap/pm/view";
import type { DOMProps } from "expo/dom";
import * as React from "react";

import {
  DescriptionEditorCodeBlock,
  DescriptionEditorCodeBlockContext,
  type DescriptionEditorCodeBlockLabels,
} from "@/components/ticket/DescriptionEditorCodeBlock";
import {
  DescriptionEditorImageContext,
  DescriptionEditorImageView,
  type DescriptionEditorImageActions,
} from "@/components/ticket/DescriptionEditorImageView";
import {
  DescriptionEditorToolbar,
  type DescriptionEditorToolbarLabels,
} from "@/components/ticket/DescriptionEditorToolbar";
import {
  createDescriptionExtensions,
  editorMarkdown,
} from "@/components/ticket/descriptionEditorExtensions";

/** An image the native side prepared and the editor inserts at the caret. */
export type InsertedImage = {
  /** Stored in the description: `kazan-pending:{key}` until it is uploaded. */
  src: string;
  alt: string;
  width: number;
  height: number;
  /** A data URI the WebView shows while the src is pending. */
  previewUri: string;
};

/** An image pasted into the editor, read from the clipboard. */
export type PastedImageData = { base64: string; mimeType: string; name: string };

/** Every text the editor shows, translated on the native side. */
export type DescriptionEditorLabels = DescriptionEditorToolbarLabels &
  DescriptionEditorCodeBlockLabels & {
    placeholder: string;
    imageDelete: string;
  };

/** A failure inside the editor, reported so the native side can show it. */
export type DescriptionEditorError = { action: string; message: string };

export type DescriptionEditorDomProps = {
  /** The stored description; read once, when the editor mounts. */
  initialMarkdown: string;
  /** False for the reading view: no toolbar, no caret, taps open images and links. */
  editable: boolean;
  labels: DescriptionEditorLabels;
  /** CSS variables by name (`--primary`, ...), from the app's theme colours. */
  theme: Record<string, string>;
  /** The editor is up; receives its Markdown, the baseline a later change compares to. */
  onReady: (markdown: string) => Promise<void>;
  /** The Markdown after every edit. */
  onChange: (markdown: string) => Promise<void>;
  /** The image button: picks and prepares an image, or null when none was added. */
  onAddImage: () => Promise<InsertedImage | null>;
  /** An image pasted into the editor: prepares it, or null when it was refused. */
  onPasteImage: (data: PastedImageData) => Promise<InsertedImage | null>;
  /** An image was removed with its delete button. */
  onImageDelete: (src: string) => Promise<void>;
  /** A displayable URI for a pending src, or null when it is unknown. */
  resolveImage: (src: string) => Promise<string | null>;
  /** Reading view: an image was tapped, by its position among the description's images. */
  onOpenImage: (index: number) => Promise<void>;
  /** Reading view: a link was tapped. */
  onOpenLink: (href: string) => Promise<void>;
  /** A failure the user should hear about, such as a paste that could not be read. */
  onError?: (error: DescriptionEditorError) => Promise<void>;
  dom?: DOMProps;
};

/** Room kept around the caret when it scrolls into view: the toolbar's height on top. */
const CARET_SCROLL_MARGIN = { top: 64, bottom: 24, left: 0, right: 0 };

/** The latest props, so the editor, built once, always calls the current native actions. */
function useLatest<T>(value: T): React.RefObject<T> {
  const ref = React.useRef(value);
  React.useLayoutEffect(() => {
    ref.current = value;
  });
  return ref;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/** The first image file on the clipboard, as the web editor takes it. */
function firstClipboardImage(clipboard: DataTransfer | null): File | null {
  if (!clipboard) return null;
  for (const item of Array.from(clipboard.items)) {
    if (item.kind !== "file" || !item.type.startsWith("image/")) continue;
    const file = item.getAsFile();
    if (file) return file;
  }
  return null;
}

/** The file's bytes as base64, without the data URI prefix. */
function readAsBase64(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = typeof reader.result === "string" ? reader.result : "";
      const comma = result.indexOf(",");
      if (comma < 0) {
        reject(new Error("Clipboard image did not read as a data URI"));
        return;
      }
      resolve(result.slice(comma + 1));
    };
    reader.onerror = () => reject(reader.error ?? new Error("Clipboard image could not be read"));
    reader.readAsDataURL(file);
  });
}

/** Sets the theme's CSS variables on the page and marks it as editing or reading. */
function usePageTheme(theme: Record<string, string>, editable: boolean): void {
  React.useLayoutEffect(() => {
    const root = document.documentElement;
    for (const [name, value] of Object.entries(theme)) {
      root.style.setProperty(name.startsWith("--") ? name : `--${name}`, value);
    }
  }, [theme]);

  React.useLayoutEffect(() => {
    const root = document.documentElement;
    root.classList.toggle("description-editor-editing", editable);
    root.classList.toggle("description-editor-reading", !editable);
  }, [editable]);
}

/**
 * The ticket description editor: the web's TipTap editor with its toolbar,
 * running in a WebView so a description edited on the phone is stored exactly
 * as the web stores it. With `editable` false it is the reading view.
 *
 * Images are prepared and kept on the native side; the editor only inserts
 * what the native actions return and reports deletions. A pending image's src
 * stays `kazan-pending:{key}` in the text and shows from its preview.
 */
export default function DescriptionEditorDom(props: DescriptionEditorDomProps) {
  const { initialMarkdown, editable, labels, theme } = props;
  const latest = useLatest(props);
  const [linkRowOpen, setLinkRowOpen] = React.useState(false);
  const [addingImage, setAddingImage] = React.useState(false);
  const previews = React.useRef(new Map<string, string>()).current;
  const editorRef = React.useRef<Editor | null>(null);

  usePageTheme(theme, editable);

  /** Logs a failure and hands it to the native side, which shows it. */
  const reportError = React.useCallback(
    (action: string, error: unknown) => {
      console.error(`[DescriptionEditorDom] ${action} failed`, error);
      const onError = latest.current.onError;
      if (!onError) return;
      onError({ action, message: errorMessage(error) }).catch((reportFailure: unknown) => {
        // The bridge itself failed; the console line above is all that is left.
        console.error("[DescriptionEditorDom] onError failed", reportFailure);
      });
    },
    [latest]
  );

  const insertImage = React.useCallback(
    (image: InsertedImage | null) => {
      const editor = editorRef.current;
      if (!image || !editor || editor.isDestroyed) return;
      previews.set(image.src, image.previewUri);
      editor
        .chain()
        .focus()
        .insertContent({
          type: "image",
          attrs: { src: image.src, alt: image.alt, width: image.width, height: image.height },
        })
        .run();
    },
    [previews]
  );

  const pasteImage = React.useCallback(
    async (file: File) => {
      try {
        const base64 = await readAsBase64(file);
        const image = await latest.current.onPasteImage({
          base64,
          mimeType: file.type,
          name: file.name || "image",
        });
        insertImage(image);
      } catch (error) {
        reportError("onPasteImage", error);
      }
    },
    [insertImage, latest, reportError]
  );

  const handlePaste = React.useCallback(
    (_view: EditorView, event: ClipboardEvent): boolean => {
      if (!latest.current.editable) return false;
      const file = firstClipboardImage(event.clipboardData);
      // Text and HTML paste stay TipTap's.
      if (!file) return false;
      event.preventDefault();
      void pasteImage(file);
      return true;
    },
    [latest, pasteImage]
  );

  const extensions = React.useMemo(
    () =>
      createDescriptionExtensions({
        placeholder: labels.placeholder,
        onOpenLinkRow: () => setLinkRowOpen(true),
        imageView: ReactNodeViewRenderer(DescriptionEditorImageView),
        codeBlockView: ReactNodeViewRenderer(DescriptionEditorCodeBlock),
      }),
    // Built once per mount: the editor keeps its extensions for its lifetime.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  const editor = useEditor({
    extensions,
    content: initialMarkdown,
    editable,
    immediatelyRender: true,
    shouldRerenderOnTransaction: false,
    editorProps: {
      handlePaste,
      attributes: { "aria-label": labels.placeholder, autocapitalize: "sentences" },
      // The caret scrolls into view below the sticky toolbar and clear of
      // the keyboard's edge.
      scrollMargin: CARET_SCROLL_MARGIN,
      scrollThreshold: CARET_SCROLL_MARGIN,
    },
    onCreate: ({ editor: created }) => {
      editorRef.current = created;
      // Lets the plugins settle the loaded document before anything else
      // runs: a description that ends with an image gets its empty trailing
      // paragraph here. On iOS the focus command below focuses the page from
      // inside itself, which dispatches a transaction of its own; if that one
      // still added the paragraph, the command's transaction would no longer
      // match the document and the editor would throw. The Markdown stays the same.
      created.view.dispatch(created.state.tr.setMeta("addToHistory", false));
      latest.current.onReady(editorMarkdown(created)).catch((error: unknown) => {
        reportError("onReady", error);
      });
      if (created.isEditable) created.commands.focus("end");
    },
    onUpdate: ({ editor: updated }) => {
      latest.current.onChange(editorMarkdown(updated)).catch((error: unknown) => {
        reportError("onChange", error);
      });
    },
  });

  // The sheet shortens the page when the keyboard comes up, which can leave
  // the caret below the new bottom edge; bring it back into view.
  React.useEffect(() => {
    if (!editable) return;
    const revealCaret = (): void => {
      const current = editorRef.current;
      if (!current || current.isDestroyed || !current.isFocused) return;
      current.commands.scrollIntoView();
    };
    window.addEventListener("resize", revealCaret);
    return () => window.removeEventListener("resize", revealCaret);
  }, [editable]);

  React.useEffect(() => {
    if (editor.isEditable !== editable) editor.setEditable(editable);
    if (!editable) setLinkRowOpen(false);
  }, [editor, editable]);

  const addImage = React.useCallback(async () => {
    setAddingImage(true);
    try {
      insertImage(await latest.current.onAddImage());
    } catch (error) {
      reportError("onAddImage", error);
    } finally {
      setAddingImage(false);
    }
  }, [insertImage, latest, reportError]);

  const imageActions = React.useMemo<DescriptionEditorImageActions>(
    () => ({
      deleteLabel: labels.imageDelete,
      previews,
      resolveImage: (src) => latest.current.resolveImage(src),
      onImageDelete: (src) => {
        previews.delete(src);
        latest.current.onImageDelete(src).catch((error: unknown) => {
          reportError("onImageDelete", error);
        });
      },
      onOpenImage: (index) => {
        latest.current.onOpenImage(index).catch((error: unknown) => {
          reportError("onOpenImage", error);
        });
      },
      onError: reportError,
    }),
    [labels.imageDelete, latest, previews, reportError]
  );

  const codeBlockLabels = React.useMemo<DescriptionEditorCodeBlockLabels>(
    () => ({ formatCode: labels.formatCode, codeLanguage: labels.codeLanguage }),
    [labels.formatCode, labels.codeLanguage]
  );

  // Links never navigate the WebView; the reading view opens them natively.
  const handleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const anchor = target.closest("a[href]");
    if (!anchor) return;
    event.preventDefault();
    if (editable) return;
    const href = anchor.getAttribute("href");
    if (!href) return;
    latest.current.onOpenLink(href).catch((error: unknown) => {
      reportError("onOpenLink", error);
    });
  };

  return (
    <DescriptionEditorImageContext.Provider value={imageActions}>
      <DescriptionEditorCodeBlockContext.Provider value={codeBlockLabels}>
        <div className="description-editor" onClick={handleClick}>
          {editable ? (
            <DescriptionEditorToolbar
              editor={editor}
              labels={labels}
              linkRowOpen={linkRowOpen}
              onLinkRowChange={setLinkRowOpen}
              onAddImage={() => void addImage()}
              addingImage={addingImage}
            />
          ) : null}
          <EditorContent editor={editor} className="wysiwyg-editor" />
        </div>
      </DescriptionEditorCodeBlockContext.Provider>
    </DescriptionEditorImageContext.Provider>
  );
}
