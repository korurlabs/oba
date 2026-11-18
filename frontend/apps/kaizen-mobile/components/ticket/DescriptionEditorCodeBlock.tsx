import { NodeViewContent, NodeViewWrapper, type ReactNodeViewProps } from "@tiptap/react";
// eslint-disable-next-line import/no-unresolved -- reason: lucide-react exposes its per-icon modules only through package exports, which Metro follows and ESLint's node resolver does not.
import WandSparkles from "lucide-react/icons/wand-sparkles";
import * as React from "react";

import {
  CODE_BLOCK_LANGUAGES,
  DEFAULT_CODE_BLOCK_LANGUAGE,
  FORMATTABLE_LANGUAGES,
  formatCode,
} from "@/components/ticket/descriptionEditorExtensions";

/** The code block's control labels, translated on the native side. */
export type DescriptionEditorCodeBlockLabels = {
  formatCode: string;
  codeLanguage: string;
};

export const DescriptionEditorCodeBlockContext =
  React.createContext<DescriptionEditorCodeBlockLabels | null>(null);

type FormatFeedback = "idle" | "success" | "error";

/** How long the Format button shows its success or error colour. */
const FEEDBACK_MS = 1200;

/**
 * A code block with the web's language list and its Format button, which
 * pretty-prints JSON, XML and HTML. Format turns red when the code does not
 * parse and green when it changed the code.
 */
export function DescriptionEditorCodeBlock({
  node,
  updateAttributes,
  editor,
  getPos,
}: ReactNodeViewProps): React.JSX.Element {
  const labels = React.useContext(DescriptionEditorCodeBlockContext);
  const currentLanguage = (node.attrs.language as string | null) ?? DEFAULT_CODE_BLOCK_LANGUAGE;
  const canFormat = FORMATTABLE_LANGUAGES.has(currentLanguage.toLowerCase());
  const editable = editor.isEditable;
  const [feedback, setFeedback] = React.useState<FormatFeedback>("idle");
  const timeoutRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  React.useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  const showFeedback = React.useCallback((next: FormatFeedback) => {
    setFeedback(next);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => setFeedback("idle"), FEEDBACK_MS);
  }, []);

  const handleFormat = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    const pos = getPos();
    if (typeof pos !== "number") return;

    const code = node.textContent;
    let formatted: string | null;
    try {
      formatted = formatCode(code, currentLanguage);
    } catch (error) {
      // Code that does not parse is the user's to fix: the button turns red.
      console.warn("[DescriptionEditorCodeBlock] format failed", error);
      showFeedback("error");
      return;
    }
    if (formatted === null || formatted === code) return;

    const text = formatted;
    const replaced = editor
      .chain()
      .focus()
      .command(({ tr, state }) => {
        const current = tr.doc.nodeAt(pos);
        const codeBlock = state.schema.nodes.codeBlock;
        if (!current || !codeBlock) return false;
        tr.replaceWith(
          pos,
          pos + current.nodeSize,
          codeBlock.create({ ...current.attrs }, state.schema.text(text))
        );
        return true;
      })
      .run();
    showFeedback(replaced ? "success" : "error");
  };

  const formatClass = [
    "code-block-format-button",
    feedback === "success" ? "code-block-format-success" : "",
    feedback === "error" ? "code-block-format-error" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <NodeViewWrapper as="pre" className="code-block-wrapper" spellCheck={false}>
      {editable ? (
        <div contentEditable={false} className="code-block-controls">
          <button
            type="button"
            className={formatClass}
            onMouseDown={(event) => event.preventDefault()}
            onClick={handleFormat}
            disabled={!canFormat}
            aria-label={labels?.formatCode}
            title={labels?.formatCode}
          >
            <WandSparkles width={14} height={14} aria-hidden />
          </button>
          <select
            className="code-block-language-select"
            aria-label={labels?.codeLanguage}
            value={currentLanguage}
            onChange={(event) => updateAttributes({ language: event.target.value })}
          >
            {CODE_BLOCK_LANGUAGES.map((language) => (
              <option key={language.value} value={language.value}>
                {language.label}
              </option>
            ))}
          </select>
        </div>
      ) : null}
      <div className="code-block-content">
        <NodeViewContent<"code">
          as="code"
          className={node.attrs.language ? `language-${node.attrs.language as string}` : ""}
        />
      </div>
    </NodeViewWrapper>
  );
}
