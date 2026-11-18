import { NodeViewWrapper, type ReactNodeViewProps } from "@tiptap/react";
// eslint-disable-next-line import/no-unresolved -- reason: lucide-react exposes its per-icon modules only through package exports, which Metro follows and ESLint's node resolver does not.
import Trash2 from "lucide-react/icons/trash-2";
import * as React from "react";

import { imageIndexAt } from "@/components/ticket/descriptionEditorExtensions";

/** What an image in the description editor needs from the editor around it. */
export type DescriptionEditorImageActions = {
  /** Label of the delete button, for screen readers. */
  deleteLabel: string;
  /** Data URIs of the images inserted while this editor is open, by src. */
  previews: ReadonlyMap<string, string>;
  /** A displayable URI for a src the WebView cannot load itself, or null. */
  resolveImage: (src: string) => Promise<string | null>;
  /** The image with this src was removed with its delete button. */
  onImageDelete: (src: string) => void;
  /** Tapped in the reading view: the image's position among the description's images. */
  onOpenImage: (index: number) => void;
  /** Reports a failure the user should hear about. */
  onError: (action: string, error: unknown) => void;
};

export const DescriptionEditorImageContext =
  React.createContext<DescriptionEditorImageActions | null>(null);

/**
 * A src the WebView loads on its own: a saved attachment's URL, or a data or
 * blob URI. Anything else, such as an image not uploaded yet, is resolved by
 * the native side.
 */
function loadsDirectly(src: string): boolean {
  return /^(https?:|data:|blob:)/i.test(src);
}

type Resolved = { src: string; uri: string | null };

/** The URI to show for `src`: itself, this session's preview, or the native side's answer. */
function useDisplayUri(
  src: string,
  actions: DescriptionEditorImageActions | null
): { uri: string | undefined; missing: boolean } {
  const direct = loadsDirectly(src) ? src : actions?.previews.get(src);
  const [resolved, setResolved] = React.useState<Resolved | null>(null);

  React.useEffect(() => {
    if (direct || !actions || !src) return;
    let active = true;
    actions.resolveImage(src).then(
      (uri) => {
        if (active) setResolved({ src, uri });
      },
      (error: unknown) => {
        if (active) setResolved({ src, uri: null });
        actions.onError("resolveImage", error);
      }
    );
    return () => {
      active = false;
    };
  }, [actions, direct, src]);

  if (direct) return { uri: direct, missing: false };
  if (resolved?.src !== src) return { uri: undefined, missing: false };
  return { uri: resolved.uri ?? undefined, missing: resolved.uri === null };
}

function dimension(value: unknown): number | undefined {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

/**
 * An image in the description, where it sits in the text. While editing it
 * carries a red delete button, always shown because touch has no hover; in
 * the reading view a tap opens the image viewer. Width and height reserve
 * its space before it loads, so the text does not jump.
 */
export function DescriptionEditorImageView({
  node,
  editor,
  deleteNode,
  getPos,
}: ReactNodeViewProps): React.JSX.Element {
  const actions = React.useContext(DescriptionEditorImageContext);
  const src = (node.attrs.src as string | null | undefined) ?? "";
  const alt = (node.attrs.alt as string | null | undefined) ?? "";
  const width = dimension(node.attrs.width);
  const height = dimension(node.attrs.height);
  const { uri, missing } = useDisplayUri(src, actions);
  const editable = editor.isEditable;

  const handleDelete = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    if (src) actions?.onImageDelete(src);
    deleteNode();
  };

  const handleOpen = () => {
    if (editable || !actions) return;
    const pos = getPos();
    if (typeof pos !== "number") return;
    const index = imageIndexAt(editor.state.doc, pos);
    if (index >= 0) actions.onOpenImage(index);
  };

  const aspectRatio = width && height ? `${width} / ${height}` : undefined;

  return (
    <NodeViewWrapper as="span" className="description-editor-image">
      {missing ? (
        <span
          className="description-editor-image-missing"
          style={{ width: width ? `${width}px` : undefined, aspectRatio }}
        >
          {alt}
        </span>
      ) : (
        <img
          src={uri}
          alt={alt}
          width={width}
          height={height}
          loading="lazy"
          decoding="async"
          draggable={false}
          style={aspectRatio ? { aspectRatio } : undefined}
          onClick={handleOpen}
        />
      )}
      {editable ? (
        <button
          type="button"
          className="description-editor-image-delete"
          aria-label={actions?.deleteLabel}
          title={actions?.deleteLabel}
          contentEditable={false}
          onMouseDown={(event) => {
            // Keeps the caret and the keyboard where they are.
            event.preventDefault();
            event.stopPropagation();
          }}
          onClick={handleDelete}
        >
          <Trash2 width={18} height={18} aria-hidden />
        </button>
      ) : null}
    </NodeViewWrapper>
  );
}
