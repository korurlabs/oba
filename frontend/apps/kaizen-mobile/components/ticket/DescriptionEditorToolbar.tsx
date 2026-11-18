import { useEditorState, type Editor } from "@tiptap/react";
import type { LucideIcon } from "lucide-react";
/* eslint-disable import/no-unresolved -- reason: lucide-react exposes its per-icon modules only through package exports, which Metro follows and ESLint's node resolver does not. */
import Bold from "lucide-react/icons/bold";
import Code from "lucide-react/icons/code";
import Heading1 from "lucide-react/icons/heading-1";
import Heading2 from "lucide-react/icons/heading-2";
import Heading3 from "lucide-react/icons/heading-3";
import ImagePlus from "lucide-react/icons/image-plus";
import Italic from "lucide-react/icons/italic";
import Link2 from "lucide-react/icons/link-2";
import List from "lucide-react/icons/list";
import ListChecks from "lucide-react/icons/list-checks";
import ListOrdered from "lucide-react/icons/list-ordered";
import Minus from "lucide-react/icons/minus";
import Quote from "lucide-react/icons/quote";
import CodeSquare from "lucide-react/icons/square-code";
import Strikethrough from "lucide-react/icons/strikethrough";
import X from "lucide-react/icons/x";
/* eslint-enable import/no-unresolved */
import * as React from "react";

/** Toolbar and link row texts, translated on the native side. */
export type DescriptionEditorToolbarLabels = {
  toolbar: string;
  bold: string;
  italic: string;
  strikethrough: string;
  code: string;
  heading1: string;
  heading2: string;
  heading3: string;
  bulletList: string;
  orderedList: string;
  taskList: string;
  blockquote: string;
  codeBlock: string;
  horizontalRule: string;
  link: string;
  image: string;
  linkUrl: string;
  linkUrlPlaceholder: string;
  linkInsert: string;
  linkRemove: string;
  linkCancel: string;
};

/** The URL a new link starts from. */
const LINK_PREFIX = "https://";

type ActiveState = {
  bold: boolean;
  italic: boolean;
  strike: boolean;
  code: boolean;
  heading1: boolean;
  heading2: boolean;
  heading3: boolean;
  bulletList: boolean;
  orderedList: boolean;
  taskList: boolean;
  blockquote: boolean;
  codeBlock: boolean;
  link: boolean;
};

function readActiveState({ editor }: { editor: Editor }): ActiveState {
  return {
    bold: editor.isActive("bold"),
    italic: editor.isActive("italic"),
    strike: editor.isActive("strike"),
    code: editor.isActive("code"),
    heading1: editor.isActive("heading", { level: 1 }),
    heading2: editor.isActive("heading", { level: 2 }),
    heading3: editor.isActive("heading", { level: 3 }),
    bulletList: editor.isActive("bulletList"),
    orderedList: editor.isActive("orderedList"),
    taskList: editor.isActive("taskList"),
    blockquote: editor.isActive("blockquote"),
    codeBlock: editor.isActive("codeBlock"),
    link: editor.isActive("link"),
  };
}

type ToolbarButtonProps = {
  icon: LucideIcon;
  label: string;
  active?: boolean;
  disabled?: boolean;
  busy?: boolean;
  onPress: () => void;
};

function ToolbarButton({ icon: Icon, label, active, disabled, busy, onPress }: ToolbarButtonProps) {
  return (
    <button
      type="button"
      className="description-editor-toolbar-button"
      aria-label={label}
      title={label}
      aria-pressed={active === undefined ? undefined : active}
      disabled={disabled}
      // Keeps the caret, the selection and the keyboard in the editor.
      onMouseDown={(event) => event.preventDefault()}
      onClick={onPress}
    >
      <Icon
        width={20}
        height={20}
        aria-hidden
        className={busy ? "description-editor-busy" : undefined}
      />
    </button>
  );
}

function Separator() {
  return <span className="description-editor-toolbar-separator" aria-hidden />;
}

type LinkRowProps = {
  editor: Editor;
  labels: DescriptionEditorToolbarLabels;
  hasLink: boolean;
  onClose: () => void;
};

/**
 * The URL row under the toolbar, prefilled with the selected link or
 * `https://`. An empty URL removes the link, as on the web.
 */
function LinkRow({ editor, labels, hasLink, onClose }: LinkRowProps) {
  const [url, setUrl] = React.useState(
    () => (editor.getAttributes("link").href as string | undefined) ?? LINK_PREFIX
  );
  const inputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const apply = () => {
    const trimmed = url.trim();
    const chain = editor.chain().focus().extendMarkRange("link");
    if (!trimmed || trimmed === LINK_PREFIX) {
      chain.unsetLink().run();
    } else {
      chain.setLink({ href: trimmed }).run();
    }
    onClose();
  };

  const remove = () => {
    editor.chain().focus().extendMarkRange("link").unsetLink().run();
    onClose();
  };

  const cancel = () => {
    onClose();
    editor.commands.focus();
  };

  return (
    <form
      className="description-editor-link-row"
      onSubmit={(event) => {
        event.preventDefault();
        apply();
      }}
    >
      <label className="description-editor-link-label" htmlFor="description-editor-link-url">
        {labels.linkUrl}
      </label>
      <div className="description-editor-link-fields">
        <input
          ref={inputRef}
          id="description-editor-link-url"
          className="description-editor-link-input"
          type="url"
          inputMode="url"
          autoCapitalize="none"
          autoCorrect="off"
          enterKeyHint="done"
          value={url}
          placeholder={labels.linkUrlPlaceholder}
          onChange={(event) => setUrl(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.preventDefault();
              cancel();
            }
          }}
        />
        <button type="submit" className="description-editor-link-insert">
          {labels.linkInsert}
        </button>
        <button
          type="button"
          className="description-editor-toolbar-button"
          aria-label={labels.linkCancel}
          title={labels.linkCancel}
          onClick={cancel}
        >
          <X width={20} height={20} aria-hidden />
        </button>
      </div>
      {hasLink ? (
        <button type="button" className="description-editor-link-remove" onClick={remove}>
          <X width={14} height={14} aria-hidden />
          {labels.linkRemove}
        </button>
      ) : null}
    </form>
  );
}

type DescriptionEditorToolbarProps = {
  editor: Editor;
  labels: DescriptionEditorToolbarLabels;
  linkRowOpen: boolean;
  onLinkRowChange: (open: boolean) => void;
  onAddImage: () => void;
  addingImage: boolean;
};

/**
 * The web editor's toolbar in one horizontally scrolling row of touch-sized
 * buttons, in the web's order and groups: marks, headings, lists, blocks,
 * then link and image. Each button shows whether its format is on at the
 * caret.
 */
export function DescriptionEditorToolbar({
  editor,
  labels,
  linkRowOpen,
  onLinkRowChange,
  onAddImage,
  addingImage,
}: DescriptionEditorToolbarProps) {
  const active = useEditorState({ editor, selector: readActiveState });
  const run =
    (command: (chain: ReturnType<Editor["chain"]>) => ReturnType<Editor["chain"]>) => () =>
      command(editor.chain().focus()).run();

  return (
    <div className="description-editor-toolbar">
      <div className="description-editor-toolbar-row" role="toolbar" aria-label={labels.toolbar}>
        <ToolbarButton
          icon={Bold}
          label={labels.bold}
          active={active.bold}
          onPress={run((chain) => chain.toggleBold())}
        />
        <ToolbarButton
          icon={Italic}
          label={labels.italic}
          active={active.italic}
          onPress={run((chain) => chain.toggleItalic())}
        />
        <ToolbarButton
          icon={Strikethrough}
          label={labels.strikethrough}
          active={active.strike}
          onPress={run((chain) => chain.toggleStrike())}
        />
        <ToolbarButton
          icon={Code}
          label={labels.code}
          active={active.code}
          onPress={run((chain) => chain.toggleCode())}
        />

        <Separator />

        <ToolbarButton
          icon={Heading1}
          label={labels.heading1}
          active={active.heading1}
          onPress={run((chain) => chain.toggleHeading({ level: 1 }))}
        />
        <ToolbarButton
          icon={Heading2}
          label={labels.heading2}
          active={active.heading2}
          onPress={run((chain) => chain.toggleHeading({ level: 2 }))}
        />
        <ToolbarButton
          icon={Heading3}
          label={labels.heading3}
          active={active.heading3}
          onPress={run((chain) => chain.toggleHeading({ level: 3 }))}
        />

        <Separator />

        <ToolbarButton
          icon={List}
          label={labels.bulletList}
          active={active.bulletList}
          onPress={run((chain) => chain.toggleBulletList())}
        />
        <ToolbarButton
          icon={ListOrdered}
          label={labels.orderedList}
          active={active.orderedList}
          onPress={run((chain) => chain.toggleOrderedList())}
        />
        <ToolbarButton
          icon={ListChecks}
          label={labels.taskList}
          active={active.taskList}
          onPress={run((chain) => chain.toggleTaskList())}
        />

        <Separator />

        <ToolbarButton
          icon={Quote}
          label={labels.blockquote}
          active={active.blockquote}
          onPress={run((chain) => chain.toggleBlockquote())}
        />
        <ToolbarButton
          icon={CodeSquare}
          label={labels.codeBlock}
          active={active.codeBlock}
          onPress={run((chain) => chain.toggleCodeBlock())}
        />
        <ToolbarButton
          icon={Minus}
          label={labels.horizontalRule}
          onPress={run((chain) => chain.setHorizontalRule())}
        />

        <Separator />

        <ToolbarButton
          icon={Link2}
          label={labels.link}
          active={active.link || linkRowOpen}
          onPress={() => onLinkRowChange(!linkRowOpen)}
        />
        <ToolbarButton
          icon={ImagePlus}
          label={labels.image}
          disabled={addingImage}
          busy={addingImage}
          onPress={onAddImage}
        />
      </div>
      {linkRowOpen ? (
        <LinkRow
          editor={editor}
          labels={labels}
          hasLink={active.link}
          onClose={() => onLinkRowChange(false)}
        />
      ) : null}
    </div>
  );
}
