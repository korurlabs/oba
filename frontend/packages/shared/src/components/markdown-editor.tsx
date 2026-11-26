'use client';

import * as React from 'react';
import { useRef, useCallback, useEffect, useState } from 'react';
import { FieldValues, Control, Path } from 'react-hook-form';
import { cn } from '@oba/ui/lib/utils';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@oba/ui/components/form';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@oba/ui/components/popover';
import {
  Bold,
  Italic,
  Strikethrough,
  Code,
  Link2,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  ListChecks,
  Quote,
  CodeSquare,
  Minus,
  Paperclip,
  Upload,
  X,
  WandSparkles,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
import {
  useEditor,
  EditorContent,
  NodeViewWrapper,
  NodeViewContent,
  ReactNodeViewRenderer,
} from '@tiptap/react';
import type { ReactNodeViewProps, Editor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import TaskList from '@tiptap/extension-task-list';
import TaskItem from '@tiptap/extension-task-item';
import Placeholder from '@tiptap/extension-placeholder';
import Image from '@tiptap/extension-image';
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight';
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table';
import { Extension } from '@tiptap/core';
import type { Node as ProseMirrorNode } from '@tiptap/pm/model';
import { Markdown } from 'tiptap-markdown';
import { common, createLowlight } from 'lowlight';
import { ImageNodeView } from './markdown-editor-image-node-view';
import {
  MarkdownEditorComponentsProvider,
  useMarkdownEditorComponents,
  type MarkdownEditorComponents,
} from './markdown-editor-components';

export type { MarkdownEditorComponents } from './markdown-editor-components';

// ── Image Markdown ───────────────────────────────────────────────

/** The part of tiptap-markdown's serializer state that the image serializer uses. */
export interface ImageMarkdownSerializerState {
  write(content: string): void;
  closeBlock(node: ProseMirrorNode): void;
}

/**
 * Writes an image node as a full `<img>` tag rather than `![alt](src)`, so the
 * stored description keeps width and height (no layout shift on load) and the
 * attachment id that ties the image to its attachment for the post-save src
 * rewrite and orphan cleanup.
 *
 * The image is a block node, so the block is closed after the tag. Without it
 * the next block is written on the same line (`<img …>- [ ] task`), which
 * Markdown no longer reads as an HTML block: on the next load a list item or
 * heading there turns into escaped text.
 */
export function serializeImageMarkdown(
  state: ImageMarkdownSerializerState,
  node: ProseMirrorNode,
): void {
  const escapeAttr = (value: string): string =>
    value
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

  const attrs: string[] = [];
  const src = (node.attrs.src as string | null | undefined) ?? '';
  const alt = (node.attrs.alt as string | null | undefined) ?? '';
  const title = (node.attrs.title as string | null | undefined) ?? '';
  const width = node.attrs.width as string | number | null | undefined;
  const height = node.attrs.height as string | number | null | undefined;
  const aid = node.attrs['data-attachment-id'] as string | null | undefined;

  attrs.push(`src="${escapeAttr(src)}"`);
  if (alt) attrs.push(`alt="${escapeAttr(alt)}"`);
  if (title) attrs.push(`title="${escapeAttr(title)}"`);
  if (width !== null && width !== undefined && width !== '') {
    attrs.push(`width="${escapeAttr(String(width))}"`);
  }
  if (height !== null && height !== undefined && height !== '') {
    attrs.push(`height="${escapeAttr(String(height))}"`);
  }
  if (aid) attrs.push(`data-attachment-id="${escapeAttr(aid)}"`);
  attrs.push('loading="lazy"');
  attrs.push('decoding="async"');

  state.write(`<img ${attrs.join(' ')}>`);
  state.closeBlock(node);
}

// ── Links ────────────────────────────────────────────────────────

/**
 * StarterKit without its own code block and link. The editor adds both itself,
 * and a second link mark would read `class`, `rel` and `target` from content
 * again, past {@link safeLinkExtension}.
 */
export const markdownStarterKit = StarterKit.configure({
  heading: { levels: [1, 2, 3, 4, 5, 6] },
  codeBlock: false,
  link: false,
});

/** Link attributes that only the editor sets, never the content. */
const EDITOR_OWNED_LINK_ATTRIBUTES = ['class', 'rel', 'target'] as const;

/**
 * The editor's link mark. Stored content is Markdown with raw HTML, and the
 * backend keeps it as written, so a page author can store any `<a>` tag. The
 * mark therefore reads only `href` and `title` from content: an author's
 * `class` could lay the link over the whole screen as a fake sign-in prompt,
 * and an author's `rel` or `target` could give the opened page access to this
 * window. Every link renders the fixed attributes below instead, whatever its
 * mark holds.
 */
export const safeLinkExtension = Link.extend({
  addAttributes() {
    const attributes = { ...this.parent?.() };
    for (const name of EDITOR_OWNED_LINK_ATTRIBUTES) {
      attributes[name] = { ...attributes[name], parseHTML: () => null, rendered: false };
    }
    return attributes;
  },
}).configure({
  openOnClick: false,
  autolink: true,
  HTMLAttributes: {
    class: 'text-primary underline cursor-pointer',
    rel: 'noopener noreferrer',
    target: '_blank',
  },
});

// ── Lowlight setup ───────────────────────────────────────────────
const lowlight = createLowlight(common);

const CODE_BLOCK_LANGUAGES = [
  { value: '', label: 'auto' },
  { value: 'plaintext', label: 'plaintext' },
  { value: 'javascript', label: 'javascript' },
  { value: 'typescript', label: 'typescript' },
  { value: 'html', label: 'html' },
  { value: 'css', label: 'css' },
  { value: 'json', label: 'json' },
  { value: 'xml', label: 'xml' },
  { value: 'java', label: 'java' },
  { value: 'python', label: 'python' },
  { value: 'sql', label: 'sql' },
  { value: 'bash', label: 'bash' },
  { value: 'yaml', label: 'yaml' },
  { value: 'markdown', label: 'markdown' },
] as const;

// ── Code formatting utilities ────────────────────────────────────

const FORMATTABLE_LANGUAGES = new Set(['json', 'xml', 'html']);

function formatJson(code: string): string {
  const parsed: unknown = JSON.parse(code);
  return JSON.stringify(parsed, null, 2);
}

function formatXmlHtml(code: string): string {
  const trimmed = code.trim();
  // Split between adjacent tags and rejoin with newlines + indentation
  const parts = trimmed.replace(/>\s*</g, '>\n<').split('\n');
  let indent = 0;
  const lines: string[] = [];

  for (const part of parts) {
    const trimmedPart = part.trim();
    if (!trimmedPart) continue;

    // Decrease indent for closing tags
    if (trimmedPart.startsWith('</')) {
      indent = Math.max(0, indent - 1);
    }

    lines.push('  '.repeat(indent) + trimmedPart);

    // Increase indent for opening tags that are not self-closing and not void
    if (
      trimmedPart.startsWith('<') &&
      !trimmedPart.startsWith('</') &&
      !trimmedPart.endsWith('/>') &&
      !trimmedPart.startsWith('<!') &&
      !trimmedPart.startsWith('<?')
    ) {
      // Only increase if this tag also has a separate closing tag (not inline closed)
      const tagMatch = trimmedPart.match(/^<(\w+)/);
      if (tagMatch) {
        const closingInSameLine = trimmedPart.includes(`</${tagMatch[1]}`);
        if (!closingInSameLine) {
          indent++;
        }
      }
    }
  }

  return lines.join('\n');
}

function formatCode(code: string, language: string): string | null {
  const lang = language.toLowerCase();

  if (lang === 'json') {
    return formatJson(code);
  }

  if (lang === 'xml' || lang === 'html') {
    return formatXmlHtml(code);
  }

  // Unsupported language — format button is disabled for these
  return null;
}

// ── Code block NodeView with language selector ───────────────────

type FormatFeedback = 'idle' | 'success' | 'error';

function CodeBlockNodeView({ node, updateAttributes, editor, getPos }: ReactNodeViewProps) {
  const t = useTranslations('markdownEditor');
  const currentLanguage = (node.attrs.language as string) ?? 'json';
  const [formatFeedback, setFormatFeedback] = useState<FormatFeedback>('idle');
  const feedbackTimeoutRef = useRef<ReturnType<typeof setTimeout>>(null);

  const canFormat = FORMATTABLE_LANGUAGES.has(currentLanguage.toLowerCase());

  // Clear the feedback timeout on unmount
  useEffect(() => {
    return () => {
      if (feedbackTimeoutRef.current) {
        clearTimeout(feedbackTimeoutRef.current);
      }
    };
  }, []);

  const setFeedbackWithTimeout = useCallback((feedback: FormatFeedback) => {
    setFormatFeedback(feedback);
    if (feedbackTimeoutRef.current) {
      clearTimeout(feedbackTimeoutRef.current);
    }
    feedbackTimeoutRef.current = setTimeout(() => setFormatFeedback('idle'), 1200);
  }, []);

  const handleFormat = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!editor || typeof getPos !== 'function') return;

    const code = node.textContent;
    let formatted: string | null = null;

    try {
      formatted = formatCode(code, currentLanguage);
    } catch {
      setFeedbackWithTimeout('error');
      return;
    }

    if (formatted === null || formatted === code) {
      return;
    }

    try {
      const pos = getPos();
      if (typeof pos !== 'number') return;

      // Use editor chain to replace the entire code block with new content
      editor
        .chain()
        .focus()
        .command(({ tr }) => {
          const currentNode = tr.doc.nodeAt(pos);
          if (!currentNode) return false;

          const textContent = formatted as string;
          const codeBlockNode = editor.state.schema.nodes.codeBlock;
          if (!codeBlockNode) return false;
          const newNode = codeBlockNode.create(
            { ...currentNode.attrs },
            editor.state.schema.text(textContent),
          );
          tr.replaceWith(pos, pos + currentNode.nodeSize, newNode);
          return true;
        })
        .run();

      setFeedbackWithTimeout('success');
    } catch {
      setFeedbackWithTimeout('error');
    }
  }, [editor, getPos, node, currentLanguage, setFeedbackWithTimeout]);

  return (
    <NodeViewWrapper as="pre" className="code-block-wrapper" spellCheck={false}>
      {/* Formatting or relabelling a block edits it, so an editor that cannot be edited shows the code only. */}
      {editor.isEditable && (
        <div contentEditable={false} className="code-block-controls">
          <button
            type="button"
            className={cn(
              'code-block-format-button',
              formatFeedback === 'success' && 'code-block-format-success',
              formatFeedback === 'error' && 'code-block-format-error',
            )}
            onMouseDown={handleFormat}
            disabled={!canFormat}
            title={t('formatCode')}
          >
            <WandSparkles className="h-3 w-3" />
          </button>
          <select
            className="code-block-language-select"
            value={currentLanguage}
            onChange={(e) => updateAttributes({ language: e.target.value })}
          >
            {CODE_BLOCK_LANGUAGES.map((lang) => (
              <option key={lang.value} value={lang.value}>
                {lang.label}
              </option>
            ))}
          </select>
        </div>
      )}
      <div className="code-block-content">
        <NodeViewContent<'code'> as="code" className={node.attrs.language ? `language-${node.attrs.language}` : ''} />
      </div>
    </NodeViewWrapper>
  );
}

/** The editor's document as Markdown, as `onChange` reports it. */
function editorMarkdown(editor: Pick<Editor, 'storage'>): string {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (editor.storage as any).markdown.getMarkdown() as string;
}

/**
 * Brings a value the form set from outside (a reset, an inserted link) into
 * the editor. `syncedValue` is the last value editor and form agreed on: the
 * editor's own `onChange` echoed back is skipped, so typing never resets the
 * cursor. Keying on the value instead of a one-shot flag matters because the
 * editor also reports updates that leave the value unchanged (`setEditable`
 * emits one), and those must not swallow the next outside change. The
 * content is set without an update event, so a sync never reports a change
 * back to the form.
 */
export function syncExternalValue(
  editor: Pick<Editor, 'storage' | 'commands'>,
  value: string,
  syncedValue: { current: string | null }
): void {
  if (value === syncedValue.current) return;
  if (editorMarkdown(editor) !== value) {
    editor.commands.setContent(value || '', { emitUpdate: false });
  }
  syncedValue.current = value;
}

interface UploadResult {
  url: string;
  isImage: boolean;
  filename: string;
}

/** Hard cap before any compression attempt. UX guard, not a security boundary. */
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

/**
 * Argument shape for the deferred-image registration callback. The
 * editor inserts a `blob:` placeholder; the parent records the file
 * so it can be uploaded on form submit.
 */
export interface PendingImageInput {
  file: File;
  blobUrl: string;
  width: number;
  height: number;
}

interface MarkdownEditorBaseProps {
  placeholder?: string;
  className?: string;
  onFileUpload?: (file: File) => Promise<UploadResult>;
  /**
   * Optional. When provided, image drop/paste/picker events register the
   * (compressed) image with the parent via this callback instead of
   * uploading immediately. The returned localId can be used by callers
   * to correlate the editor blob with their pending-images map. When
   * undefined, image files fall back to `onFileUpload` (the legacy
   * `/api/upload` flow) for graceful degradation.
   */
  onImageDrop?: (image: PendingImageInput) => string;
  /**
   * Optional. Invoked from the trash-button NodeView when a pending
   * (unsaved) image is removed by the user, so the parent can eagerly
   * drop the matching entry from its pending-images map and revoke the
   * blob URL. Receives the `<img src>` (a `blob:` URL for pending,
   * an Azure URL for saved) -- callers should treat unknown srcs as
   * no-ops. Saved images are handled by the orphan-GC step on save and
   * do not need to round-trip through this callback.
   */
  onImageDelete?: (src: string) => void;
  disabled?: boolean;
  /**
   * Shows the content for reading only: no frame, no toolbar, no code block
   * controls, no uploads and full-strength text, where `disabled` keeps the
   * editor's look and dims it.
   */
  readOnly?: boolean;
  /**
   * Optional. The app's own `Button` and `Input`, so the toolbar, link
   * popover and image delete button match the rest of that app. Defaults
   * to the `@oba/ui` components.
   */
  components?: Partial<MarkdownEditorComponents>;
}

/** Standalone usage: caller provides value + onChange directly */
interface MarkdownEditorStandaloneProps extends MarkdownEditorBaseProps {
  value: string;
  onChange: (value: string) => void;
  error?: boolean;
  name?: undefined;
  control?: undefined;
  label?: undefined;
  description?: undefined;
  required?: undefined;
}

/** Form-integrated usage: caller provides name + control, value/onChange come from react-hook-form */
interface MarkdownEditorFormProps<TFieldValues extends FieldValues = FieldValues>
  extends MarkdownEditorBaseProps {
  name: Path<TFieldValues>;
  control: Control<TFieldValues>;
  label?: string;
  description?: string;
  required?: boolean;
  value?: undefined;
  onChange?: undefined;
  error?: undefined;
}

export type MarkdownEditorProps<TFieldValues extends FieldValues = FieldValues> =
  | MarkdownEditorStandaloneProps
  | MarkdownEditorFormProps<TFieldValues>;

interface CompressImageResult {
  file: File;
  width: number;
  height: number;
}

/**
 * Compresses an image to WebP at the configured quality and a maximum
 * long-edge dimension. Returns the compressed file plus its intrinsic
 * dimensions captured from the canvas after scaling.
 *
 * Special cases:
 * - GIF: pass-through (no canvas conversion -- preserves animation).
 *   Dimensions are read from a transient <img> via URL.createObjectURL.
 * - SVG: rejected -- SVGs can carry stored XSS via embedded <script>;
 *   the caller must show the imageTypeBlocked toast and skip insertion.
 * - Non-image: rejected -- defensive guard for callers that forget to
 *   filter by MIME type before invoking.
 */
async function compressImage(
  file: File,
  maxWidth = 1200,
  quality = 0.8,
): Promise<CompressImageResult> {
  if (!file.type.startsWith('image/')) {
    throw new Error('NOT_AN_IMAGE');
  }
  if (file.type === 'image/svg+xml') {
    throw new Error('SVG_BLOCKED');
  }

  if (file.type === 'image/gif') {
    // Read intrinsic dimensions without canvas-converting so animation is
    // preserved on upload.
    return new Promise<CompressImageResult>((resolve, reject) => {
      const objectUrl = URL.createObjectURL(file);
      const img = new window.Image();
      img.onload = () => {
        const width = img.naturalWidth;
        const height = img.naturalHeight;
        URL.revokeObjectURL(objectUrl);
        resolve({ file, width, height });
      };
      img.onerror = () => {
        URL.revokeObjectURL(objectUrl);
        reject(new Error('IMAGE_LOAD_FAILED'));
      };
      img.src = objectUrl;
    });
  }

  return new Promise<CompressImageResult>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new window.Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        let { width, height } = img;

        if (width > maxWidth) {
          height = (height * maxWidth) / width;
          width = maxWidth;
        }

        canvas.width = width;
        canvas.height = height;

        const ctx = canvas.getContext('2d');
        ctx?.drawImage(img, 0, 0, width, height);

        const outputType = 'image/webp';
        const ext = '.webp';
        const baseName = file.name.replace(/\.[^/.]+$/, '');

        canvas.toBlob(
          (blob) => {
            if (blob) {
              resolve({
                file: new File([blob], baseName + ext, { type: outputType }),
                // Use the canvas's final dimensions, not the source's, since
                // we may have scaled to fit `maxWidth`.
                width: canvas.width,
                height: canvas.height,
              });
            } else {
              reject(new Error('CANVAS_TOBLOB_FAILED'));
            }
          },
          outputType,
          quality,
        );
      };
      img.onerror = () => reject(new Error('IMAGE_LOAD_FAILED'));
      img.src = e.target?.result as string;
    };
    reader.onerror = () => reject(new Error('FILE_READ_FAILED'));
    reader.readAsDataURL(file);
  });
}

// ── URL title resolution ──────────────────────────────────────────

/**
 * Extracts a readable name from a URL hostname when server-side title
 * fetching is not available or fails.
 */
function getTitleFromUrl(url: string): string {
  try {
    const parsed = new URL(url);
    const hostname = parsed.hostname.replace(/^www\./, '');

    // Well-known domain mappings
    const knownDomains: Record<string, string> = {
      'github.com': 'GitHub',
      'gitlab.com': 'GitLab',
      'bitbucket.org': 'Bitbucket',
      'stackoverflow.com': 'Stack Overflow',
      'docs.google.com': 'Google Docs',
      'drive.google.com': 'Google Drive',
      'sheets.google.com': 'Google Sheets',
      'slides.google.com': 'Google Slides',
      'youtube.com': 'YouTube',
      'figma.com': 'Figma',
      'notion.so': 'Notion',
      'linear.app': 'Linear',
      'slack.com': 'Slack',
      'discord.com': 'Discord',
      'twitter.com': 'Twitter',
      'x.com': 'X',
      'linkedin.com': 'LinkedIn',
      'medium.com': 'Medium',
      'reddit.com': 'Reddit',
      'wikipedia.org': 'Wikipedia',
      'npm.js.com': 'npm',
      'vercel.com': 'Vercel',
      'netlify.com': 'Netlify',
      'jira.atlassian.com': 'Jira',
      'confluence.atlassian.com': 'Confluence',
      'trello.com': 'Trello',
    };

    // Check exact hostname match and subdomain matches
    for (const [domain, name] of Object.entries(knownDomains)) {
      if (hostname === domain || hostname.endsWith('.' + domain)) {
        return name;
      }
    }

    // Fallback: capitalize the first part of the hostname
    const parts = hostname.split('.');
    const mainPart = parts.length >= 2 ? parts[parts.length - 2] ?? parts[0] : parts[0];
    if (!mainPart) return hostname;
    return mainPart.charAt(0).toUpperCase() + mainPart.slice(1);
  } catch {
    return url;
  }
}

/**
 * Fetches a page title via the server-side API route, falling back to
 * hostname-based title extraction.
 */
async function fetchLinkTitle(url: string): Promise<string> {
  try {
    const response = await fetch(
      `/api/link-metadata?url=${encodeURIComponent(url)}`
    );
    if (response.ok) {
      const data = (await response.json()) as { title: string | null };
      if (data.title) {
        return data.title;
      }
    }
  } catch {
    // Server fetch failed — fall back to hostname extraction
  }
  return getTitleFromUrl(url);
}

// ── /link slash command extension ─────────────────────────────────

function createSlashLinkExtension(
  onOpenLinkPopover: () => void
): Extension {
  return Extension.create({
    name: 'slashLink',

    addKeyboardShortcuts() {
      return {
        Enter: ({ editor: currentEditor }) => {
          const { state } = currentEditor;
          const { $from } = state.selection;
          const textBefore = $from.parent.textContent.slice(0, $from.parentOffset);

          // Match /link at the end of the line, optionally followed by a URL
          const match = textBefore.match(/\/link(?:\s+(https?:\/\/\S+))?\s*$/);
          if (!match) return false;

          const fullMatch = match[0];
          const url = match[1];

          // Delete the /link command text
          const from = $from.pos - fullMatch.length;
          const to = $from.pos;
          currentEditor.chain().focus().deleteRange({ from, to }).run();

          if (url) {
            // /link <url> — fetch title and insert link
            fetchLinkTitle(url).then((title) => {
              currentEditor
                .chain()
                .focus()
                .insertContent({
                  type: 'text',
                  text: title,
                  marks: [{ type: 'link', attrs: { href: url } }],
                })
                .run();
            });
            return true;
          }

          // /link alone — open the popover
          onOpenLinkPopover();
          return true;
        },
      };
    },
  });
}

// ── Toolbar button helper ──────────────────────────────────────────

interface ToolbarButtonProps {
  onClick: () => void;
  isActive?: boolean;
  disabled?: boolean;
  title: string;
  children: React.ReactNode;
}

function ToolbarButton({
  onClick,
  isActive,
  disabled,
  title,
  children,
}: ToolbarButtonProps) {
  const { Button } = useMarkdownEditorComponents();
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className={cn('h-7 w-7 p-0', isActive && 'bg-accent text-accent-foreground')}
      onClick={onClick}
      disabled={disabled}
      title={title}
    >
      {children}
    </Button>
  );
}

// ── Link popover ──────────────────────────────────────────────────

interface LinkPopoverProps {
  editor: Editor;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  disabled: boolean;
  triggerRef: React.RefObject<HTMLButtonElement | null>;
}

function LinkPopover({
  editor,
  open,
  onOpenChange,
  disabled,
  triggerRef,
}: LinkPopoverProps) {
  const t = useTranslations('markdownEditor');
  const { Button, Input } = useMarkdownEditorComponents();
  const [linkUrl, setLinkUrl] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  // When the popover opens, pre-fill with existing link URL or default
  useEffect(() => {
    if (open) {
      const existingUrl = editor.getAttributes('link').href as string | undefined;
      setLinkUrl(existingUrl ?? 'https://');
      // Focus the input after the popover renders
      setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [open, editor]);

  const handleSubmit = (e?: React.SyntheticEvent) => {
    e?.preventDefault();
    const trimmedUrl = linkUrl.trim();

    if (!trimmedUrl || trimmedUrl === 'https://') {
      // Empty URL — remove link if one exists
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
    } else {
      editor
        .chain()
        .focus()
        .extendMarkRange('link')
        .setLink({ href: trimmedUrl })
        .run();
    }

    onOpenChange(false);
  };

  const handleRemoveLink = () => {
    editor.chain().focus().extendMarkRange('link').unsetLink().run();
    onOpenChange(false);
  };

  const hasExistingLink = editor.isActive('link');

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <Button
          ref={triggerRef}
          type="button"
          variant="ghost"
          size="sm"
          className={cn(
            'h-7 w-7 p-0',
            hasExistingLink && 'bg-accent text-accent-foreground'
          )}
          disabled={disabled}
          title={t('link')}
          onClick={() => onOpenChange(!open)}
        >
          <Link2 className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        sideOffset={8}
        className="w-80 p-3"
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            handleSubmit(e);
          }}
          className="flex flex-col gap-2"
        >
          <label className="text-sm font-medium text-foreground">
            {t('linkUrl')}
          </label>
          <div className="flex gap-2">
            <Input
              ref={inputRef}
              type="url"
              value={linkUrl}
              onChange={(e) => setLinkUrl(e.target.value)}
              placeholder={t('linkUrlPlaceholder')}
              className="h-8 text-sm"
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  e.preventDefault();
                  onOpenChange(false);
                  editor.commands.focus();
                }
              }}
            />
            <Button type="button" size="sm" className="h-8 px-3 text-sm" onClick={handleSubmit}>
              {t('linkInsert')}
            </Button>
          </div>
          {hasExistingLink && (
            <button
              type="button"
              onClick={handleRemoveLink}
              className="flex items-center gap-1 text-xs text-destructive hover:underline self-start"
            >
              <X className="h-3 w-3" />
              {t('linkRemove')}
            </button>
          )}
        </form>
      </PopoverContent>
    </Popover>
  );
}

// ── Toolbar ────────────────────────────────────────────────────────

interface ToolbarProps {
  editor: Editor;
  onFileClick?: () => void;
  isUploading: boolean;
  disabled: boolean;
  linkPopoverOpen: boolean;
  onLinkPopoverChange: (open: boolean) => void;
  linkTriggerRef: React.RefObject<HTMLButtonElement | null>;
}

function Toolbar({
  editor,
  onFileClick,
  isUploading,
  disabled,
  linkPopoverOpen,
  onLinkPopoverChange,
  linkTriggerRef,
}: ToolbarProps) {
  const t = useTranslations('markdownEditor');

  return (
    <div className="flex flex-wrap items-center gap-0.5 border-b border-input rounded-t-md bg-muted/50 px-2 py-1">
      {/* Text formatting */}
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleBold().run()}
        isActive={editor.isActive('bold')}
        disabled={disabled}
        title={t('bold')}
      >
        <Bold className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleItalic().run()}
        isActive={editor.isActive('italic')}
        disabled={disabled}
        title={t('italic')}
      >
        <Italic className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleStrike().run()}
        isActive={editor.isActive('strike')}
        disabled={disabled}
        title={t('strikethrough')}
      >
        <Strikethrough className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleCode().run()}
        isActive={editor.isActive('code')}
        disabled={disabled}
        title={t('code')}
      >
        <Code className="h-4 w-4" />
      </ToolbarButton>

      <div className="mx-1 h-5 w-px bg-border" />

      {/* Headings */}
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
        isActive={editor.isActive('heading', { level: 1 })}
        disabled={disabled}
        title={t('heading1')}
      >
        <Heading1 className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
        isActive={editor.isActive('heading', { level: 2 })}
        disabled={disabled}
        title={t('heading2')}
      >
        <Heading2 className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
        isActive={editor.isActive('heading', { level: 3 })}
        disabled={disabled}
        title={t('heading3')}
      >
        <Heading3 className="h-4 w-4" />
      </ToolbarButton>

      <div className="mx-1 h-5 w-px bg-border" />

      {/* Lists */}
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleBulletList().run()}
        isActive={editor.isActive('bulletList')}
        disabled={disabled}
        title={t('bulletList')}
      >
        <List className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleOrderedList().run()}
        isActive={editor.isActive('orderedList')}
        disabled={disabled}
        title={t('orderedList')}
      >
        <ListOrdered className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleTaskList().run()}
        isActive={editor.isActive('taskList')}
        disabled={disabled}
        title={t('taskList')}
      >
        <ListChecks className="h-4 w-4" />
      </ToolbarButton>

      <div className="mx-1 h-5 w-px bg-border" />

      {/* Block elements */}
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleBlockquote().run()}
        isActive={editor.isActive('blockquote')}
        disabled={disabled}
        title={t('blockquote')}
      >
        <Quote className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().toggleCodeBlock().run()}
        isActive={editor.isActive('codeBlock')}
        disabled={disabled}
        title={t('codeBlock')}
      >
        <CodeSquare className="h-4 w-4" />
      </ToolbarButton>
      <ToolbarButton
        onClick={() => editor.chain().focus().setHorizontalRule().run()}
        disabled={disabled}
        title={t('horizontalRule')}
      >
        <Minus className="h-4 w-4" />
      </ToolbarButton>

      <div className="mx-1 h-5 w-px bg-border" />

      {/* Link — inline popover replaces browser prompt */}
      <LinkPopover
        editor={editor}
        open={linkPopoverOpen}
        onOpenChange={onLinkPopoverChange}
        disabled={disabled}
        triggerRef={linkTriggerRef}
      />

      {/* File upload */}
      {onFileClick && (
        <ToolbarButton
          onClick={onFileClick}
          disabled={disabled || isUploading}
          title={t('uploadFile')}
        >
          <Paperclip className={cn('h-4 w-4', isUploading && 'animate-pulse')} />
        </ToolbarButton>
      )}
    </div>
  );
}

// ── Inner editor (standalone, always receives value + onChange) ──────

interface MarkdownEditorInnerProps extends MarkdownEditorBaseProps {
  value: string;
  onChange: (value: string) => void;
  error?: boolean;
}

function MarkdownEditorInner({
  value,
  onChange,
  placeholder,
  className,
  onFileUpload,
  onImageDrop,
  onImageDelete,
  disabled = false,
  readOnly = false,
  error = false,
  components,
}: MarkdownEditorInnerProps) {
  const t = useTranslations('markdownEditor');
  const tAttachments = useTranslations('attachments.messages');
  const [isUploading, setIsUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [linkPopoverOpen, setLinkPopoverOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dragCounterRef = useRef(0);
  // The value the editor was created with, then the last one editor and form agreed on.
  const syncedValue = useRef<string | null>(value);
  const linkTriggerRef = useRef<HTMLButtonElement>(null);

  const handleOpenLinkPopover = useCallback(() => {
    setLinkPopoverOpen(true);
  }, []);

  const editor = useEditor({
    extensions: [
      markdownStarterKit,
      CodeBlockLowlight.extend({
        addNodeView() {
          return ReactNodeViewRenderer(CodeBlockNodeView);
        },
      }).configure({
        lowlight,
        defaultLanguage: 'json',
      }),
      safeLinkExtension,
      TaskList,
      TaskItem.configure({
        nested: true,
      }),
      Image.extend({
        addAttributes() {
          return {
            ...this.parent?.(),
            'data-attachment-id': {
              default: null,
              parseHTML: (el) => el.getAttribute('data-attachment-id'),
              renderHTML: (attrs) =>
                attrs['data-attachment-id']
                  ? { 'data-attachment-id': attrs['data-attachment-id'] }
                  : {},
            },
            width: {
              default: null,
              parseHTML: (el) => el.getAttribute('width'),
              renderHTML: (attrs) => (attrs.width ? { width: attrs.width } : {}),
            },
            height: {
              default: null,
              parseHTML: (el) => el.getAttribute('height'),
              renderHTML: (attrs) => (attrs.height ? { height: attrs.height } : {}),
            },
          };
        },
        addNodeView() {
          return ReactNodeViewRenderer(ImageNodeView);
        },
        // Replace tiptap-markdown's default image serializer, which only
        // writes ![alt](src) and drops width, height and the attachment id.
        //
        // The storage also carries an `onImageDelete` slot used by the
        // ImageNodeView trash button -- the prop is mirrored into this
        // slot via a useEffect below so the NodeView can reach the
        // current callback without prop-drilling through Tiptap.
        addStorage() {
          return {
            ...this.parent?.(),
            onImageDelete: undefined as ((src: string) => void) | undefined,
            markdown: {
              serialize: serializeImageMarkdown,
              parse: {
                // Inherit default markdown-it parsing (handles both
                // ![](url) markdown syntax and raw <img> HTML when
                // html: true is set on the parser below).
              },
            },
          };
        },
      }).configure({
        HTMLAttributes: {
          class: 'max-w-full rounded-md my-2',
          loading: 'lazy',
          decoding: 'async',
        },
      }),
      // GFM tables: markdown-it parses them and tiptap-markdown writes
      // them back as pipe tables, so a table in a stored body survives an
      // edit instead of being flattened into paragraphs.
      Table,
      TableRow,
      TableHeader,
      TableCell,
      Placeholder.configure({
        placeholder: placeholder ?? '',
      }),
      Markdown.configure({
        // html: true so the markdown-it parser accepts raw <img> HTML
        // when re-loading saved descriptions (preserves data-attachment-id,
        // width, height, etc.). Existing pure-markdown descriptions still
        // parse via default markdown-it image rules.
        html: true,
        tightLists: true,
        bulletListMarker: '-',
        transformPastedText: true,
        transformCopiedText: true,
      }),
      createSlashLinkExtension(handleOpenLinkPopover),
    ],
    content: value || '',
    editable: !disabled && !readOnly,
    immediatelyRender: false,
    onUpdate: ({ editor: updatedEditor }) => {
      const markdown = editorMarkdown(updatedEditor);
      syncedValue.current = markdown;
      onChange(markdown);
    },
  });

  // Sync external value changes (e.g. form reset, inserted link) into the editor
  useEffect(() => {
    if (!editor) return;
    syncExternalValue(editor, value, syncedValue);
  }, [editor, value]);

  // Sync disabled/editable state. No update event: the content is unchanged.
  useEffect(() => {
    if (!editor) return;
    editor.setEditable(!disabled && !readOnly, false);
  }, [editor, disabled, readOnly]);

  // Mirror the onImageDelete prop into the Image extension's storage so
  // the ImageNodeView trash button can reach the current callback. The
  // schema's image node name is "image" -- we read from
  // `editor.storage.image` rather than hard-coding strings elsewhere.
  useEffect(() => {
    if (!editor) return;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const imageStorage = (editor.storage as any).image as
      | { onImageDelete?: (src: string) => void }
      | undefined;
    if (imageStorage) {
      imageStorage.onImageDelete = onImageDelete;
    }
  }, [editor, onImageDelete]);

  // Image-specific path: compress, register as a pending image, and
  // insert a blob: <img> at the cursor. Falls back to the legacy
  // /api/upload flow when no `onImageDrop` callback is provided.
  const handleImageFile = useCallback(
    async (file: File) => {
      if (!editor) return;

      // Hard size cap before compression.
      if (file.size > MAX_IMAGE_BYTES) {
        toast.error(tAttachments('imageSizeTooLarge'));
        return;
      }

      // Block SVG outright -- stored XSS risk via embedded scripts.
      if (file.type === 'image/svg+xml') {
        toast.error(tAttachments('imageTypeBlocked'));
        return;
      }

      // Capture the original filename BEFORE compressImage rewrites it
      // to <baseName>.webp so we can stamp the inserted <img alt> with
      // something humans recognise.
      const altFromFilename = file.name;

      // Deferred-upload path (preferred): hand off to parent via prop.
      if (onImageDrop) {
        try {
          const compressed = await compressImage(file);
          const blobUrl = URL.createObjectURL(compressed.file);
          onImageDrop({
            file: compressed.file,
            blobUrl,
            width: compressed.width,
            height: compressed.height,
          });
          // Insert the image node directly so we can attach the
          // extension's extra `width`/`height` attrs (setImage's signature
          // only types `src`, `alt`, `title`).
          editor
            .chain()
            .focus()
            .insertContent({
              type: 'image',
              attrs: {
                src: blobUrl,
                alt: altFromFilename,
                width: compressed.width,
                height: compressed.height,
              },
            })
            .run();
        } catch (compressionError) {
          if (
            compressionError instanceof Error &&
            compressionError.message === 'SVG_BLOCKED'
          ) {
            toast.error(tAttachments('imageTypeBlocked'));
          } else {
            toast.error(tAttachments('imageUploadError'));
          }
        }
        return;
      }

      // Legacy fallback: immediate upload via /api/upload (graceful
      // degradation for any place the editor is used without the new
      // ticket form wiring).
      if (!onFileUpload) return;
      setIsUploading(true);
      try {
        const compressed = await compressImage(file);
        const result: UploadResult = await onFileUpload(compressed.file);
        if (result.isImage) {
          editor.chain().focus().setImage({ src: result.url, alt: result.filename }).run();
        } else {
          editor
            .chain()
            .focus()
            .insertContent({
              type: 'text',
              text: result.filename,
              marks: [{ type: 'link', attrs: { href: result.url } }],
            })
            .run();
        }
      } catch {
        toast.error(tAttachments('imageUploadError'));
      } finally {
        setIsUploading(false);
      }
    },
    [editor, onImageDrop, onFileUpload, tAttachments],
  );

  // Non-image path: stays on the existing /api/upload route via
  // `onFileUpload`. Inserts the resulting node (image or link) into
  // the editor.
  const uploadNonImageFile = useCallback(
    async (file: File) => {
      if (!onFileUpload || !editor) return;

      setIsUploading(true);
      try {
        const result: UploadResult = await onFileUpload(file);
        if (result.isImage) {
          editor.chain().focus().setImage({ src: result.url, alt: result.filename }).run();
        } else {
          editor
            .chain()
            .focus()
            .insertContent({
              type: 'text',
              text: result.filename,
              marks: [{ type: 'link', attrs: { href: result.url } }],
            })
            .run();
        }
      } catch {
        toast.error(tAttachments('imageUploadError'));
      } finally {
        setIsUploading(false);
      }
    },
    [onFileUpload, editor, tAttachments],
  );

  // Routes a file to the correct handler based on MIME type.
  const uploadFile = useCallback(
    async (file: File) => {
      const isImage = file.type.startsWith('image/');
      if (isImage) {
        await handleImageFile(file);
      } else {
        await uploadNonImageFile(file);
      }
    },
    [handleImageFile, uploadNonImageFile],
  );

  const handleFileClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileInputChange = async (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = e.target.files?.[0];
    if (file) {
      await uploadFile(file);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Drag and drop handlers
  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current++;
    if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
      setIsDragging(true);
    }
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current--;
    if (dragCounterRef.current === 0) {
      setIsDragging(false);
    }
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  // Clipboard paste: handle the FIRST image item by routing it through
  // the deferred-image flow (or the legacy fallback). For non-image
  // payloads, do not call preventDefault so Tiptap's own paste handlers
  // (text, HTML) still run.
  const handlePaste = useCallback(
    async (e: React.ClipboardEvent) => {
      if (disabled || readOnly) return;
      if (!onImageDrop && !onFileUpload) return;

      const items = e.clipboardData?.items;
      if (!items || items.length === 0) return;

      for (const item of Array.from(items)) {
        if (item.kind !== 'file') continue;
        if (!item.type.startsWith('image/')) continue;

        const file = item.getAsFile();
        if (!file) continue;

        // We're handling this image -- prevent the browser/Tiptap from
        // also pasting a fallback representation (e.g. a base64 <img>).
        e.preventDefault();
        await handleImageFile(file);
        return;
      }
      // No image item found -- fall through to default paste handling.
    },
    [disabled, readOnly, onImageDrop, onFileUpload, handleImageFile],
  );

  const handleDrop = useCallback(
    async (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);
      dragCounterRef.current = 0;

      if (disabled || readOnly) return;
      // We can handle the drop if either the deferred-image hook or the
      // legacy /api/upload hook is wired. Bail when both are missing so
      // we don't silently swallow user input.
      if (!onImageDrop && !onFileUpload) return;

      const files = Array.from(e.dataTransfer.files);
      for (const file of files) {
        await uploadFile(file);
      }
    },
    [onImageDrop, onFileUpload, disabled, readOnly, uploadFile],
  );

  if (readOnly) {
    return (
      <MarkdownEditorComponentsProvider components={components}>
        <EditorContent
          editor={editor}
          className={cn('wysiwyg-editor min-h-0 overflow-auto border-0', className)}
        />
      </MarkdownEditorComponentsProvider>
    );
  }

  return (
    <MarkdownEditorComponentsProvider components={components}>
      <div
        className={cn(
          'relative flex flex-col rounded-md border border-input focus-within:border-primary focus-within:ring-0',
          error && 'border-error focus-within:border-error focus-within:ring-error-light',
          isDragging && 'border-primary border-2 bg-korur-blue-accent dark:bg-korur-slate',
          className
        )}
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        onPaste={handlePaste}
      >
        {/* Drag overlay */}
        {isDragging && (
          <div className="absolute inset-0 z-10 flex items-center justify-center rounded-md bg-korur-blue-accent/90 dark:bg-korur-slate/90">
            <div className="flex flex-col items-center gap-2 text-primary">
              <Upload className="h-8 w-8" />
              <span className="text-sm font-medium">{t('dropFiles')}</span>
            </div>
          </div>
        )}

        {/* Toolbar */}
        {editor && (
          <Toolbar
            editor={editor}
            onFileClick={onFileUpload || onImageDrop ? handleFileClick : undefined}
            isUploading={isUploading}
            disabled={disabled}
            linkPopoverOpen={linkPopoverOpen}
            onLinkPopoverChange={setLinkPopoverOpen}
            linkTriggerRef={linkTriggerRef}
          />
        )}

        {/* WYSIWYG Editor */}
        <EditorContent
          editor={editor}
          className={cn(
            'wysiwyg-editor flex-1 min-h-0 overflow-auto border-0',
            disabled && 'cursor-not-allowed opacity-50'
          )}
        />

        {/* Hidden file input for file upload */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.csv,.zip"
          className="hidden"
          onChange={handleFileInputChange}
        />
      </div>
    </MarkdownEditorComponentsProvider>
  );
}

// ── Public API: form-integrated or standalone ───────────────────────

export function MarkdownEditor<TFieldValues extends FieldValues = FieldValues>(
  props: MarkdownEditorProps<TFieldValues>
) {
  // Form-integrated mode: name + control are provided
  if (props.name !== undefined && props.control !== undefined) {
    const { name, control, label, description, required, ...editorProps } = props as MarkdownEditorFormProps<TFieldValues>;
    return (
      <FormField
        control={control}
        name={name}
        render={({ field, fieldState }) => (
          <FormItem>
            {label ? (
              <div className="flex items-center gap-1 leading-5">
                <FormLabel>{label}</FormLabel>
                {required ? <span className="text-destructive text-sm leading-none">*</span> : null}
              </div>
            ) : null}
            <FormControl>
              <MarkdownEditorInner
                {...editorProps}
                value={field.value ?? ''}
                onChange={field.onChange}
                error={!!fieldState.error}
              />
            </FormControl>
            {description ? <FormDescription>{description}</FormDescription> : null}
            <FormMessage />
          </FormItem>
        )}
      />
    );
  }

  // Standalone mode: caller provides value + onChange directly
  const { value, onChange, error, ...rest } = props as MarkdownEditorStandaloneProps;
  return (
    <MarkdownEditorInner
      {...rest}
      value={value}
      onChange={onChange}
      error={error}
    />
  );
}
