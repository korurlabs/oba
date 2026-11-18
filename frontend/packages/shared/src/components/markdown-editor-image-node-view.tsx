'use client';

import * as React from 'react';
import { Trash2 } from 'lucide-react';
import { NodeViewWrapper } from '@tiptap/react';
import type { ReactNodeViewProps } from '@tiptap/react';
import { useTranslations } from 'next-intl';
import { cn } from '@oba/ui/lib/utils';
import { useMarkdownEditorComponents } from './markdown-editor-components';

/**
 * NodeView for the Tiptap Image extension.
 *
 * Renders the image inline (so it flows like an inline-block in the
 * document) wrapped in a positioned <span> that hosts a hover-revealed
 * trash button. The trash button is only rendered when the editor is
 * editable -- read-only / disabled mode shows the image alone.
 *
 * Wiring: in markdown-editor.tsx Phase B, this component is attached to
 * the Image extension via:
 *   Image.extend({ addNodeView() { return ReactNodeViewRenderer(ImageNodeView); } })
 *
 * Spec: handoffs/kaizen-markdown-attachments/planning.md sections 6 and 7 #13.
 */
export function ImageNodeView(props: ReactNodeViewProps): React.JSX.Element {
  const { node, editor, deleteNode } = props;
  const t = useTranslations('markdownEditor');
  const { Button } = useMarkdownEditorComponents();

  const src = (node.attrs.src as string | null | undefined) ?? '';
  const alt = (node.attrs.alt as string | null | undefined) ?? '';
  const widthAttr = node.attrs.width as number | string | null | undefined;
  const heightAttr = node.attrs.height as number | string | null | undefined;

  const isEditable = editor.isEditable;
  const deleteLabel = t('imageDelete');

  const handleTrashClick = React.useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      // Prevent the editor from interpreting the click as a node selection
      // or text-cursor placement on the wrapper.
      event.preventDefault();
      event.stopPropagation();

      // For pending (unsaved) images, eagerly notify the parent so the
      // matching pending-images map entry is removed and its blob URL
      // revoked BEFORE the node disappears. Saved images (those with a
      // `data-attachment-id`) are no-ops in `removePendingImageBySrc` and
      // are handled by the orphan-GC step on save instead.
      // The `reconcilePendingImages` step at submit-time is the safety
      // net for keyboard-delete and other paths that bypass this button.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const storage = (editor.storage as any)[node.type.name] as
        | { onImageDelete?: (src: string) => void }
        | undefined;
      const onImageDelete = storage?.onImageDelete;
      if (onImageDelete) {
        const currentSrc = (node.attrs.src as string | null | undefined) ?? '';
        if (currentSrc) {
          onImageDelete(currentSrc);
        }
      }

      deleteNode();
    },
    [deleteNode, editor, node],
  );

  const handleTrashMouseDown = React.useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      // Stop the editor from moving the selection/cursor on mousedown.
      event.preventDefault();
      event.stopPropagation();
    },
    [],
  );

  return (
    <NodeViewWrapper
      as="span"
      className={cn(
        'group relative inline-block align-baseline',
      )}
    >
      <img
        src={src}
        alt={alt}
        width={widthAttr ?? undefined}
        height={heightAttr ?? undefined}
        loading="lazy"
        decoding="async"
        className="max-w-full rounded-md my-2"
      />
      {isEditable ? (
        <Button
          type="button"
          variant="destructive"
          size="icon-sm"
          aria-label={deleteLabel}
          title={deleteLabel}
          onClick={handleTrashClick}
          onMouseDown={handleTrashMouseDown}
          contentEditable={false}
          className={cn(
            'absolute top-1 right-1 h-7 w-7 p-0',
            'opacity-0 group-hover:opacity-100 focus-visible:opacity-100',
            'transition-opacity duration-150',
          )}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      ) : null}
    </NodeViewWrapper>
  );
}
