'use client';

import * as React from 'react';
import { Button } from '@oba/ui/components/button';
import { Input } from '@oba/ui/components/input';

/** The `Button` props the editor uses: only these variants and sizes. */
export type MarkdownEditorButtonProps = React.ComponentProps<'button'> & {
  variant?: 'default' | 'ghost' | 'destructive';
  size?: 'sm' | 'icon-sm';
};

/**
 * The primitives the editor renders its toolbar, link popover and image
 * delete button with. Apps that keep their own `Button` and `Input` (Kaizen
 * has a gradient primary and taller inputs) pass them, so the editor looks
 * like the rest of that app; every other app gets the `@oba/ui` ones.
 */
export interface MarkdownEditorComponents {
  Button: React.ComponentType<MarkdownEditorButtonProps>;
  Input: React.ComponentType<React.ComponentProps<'input'>>;
}

const DEFAULT_COMPONENTS: MarkdownEditorComponents = { Button, Input };

/**
 * A context rather than props, because the image delete button lives in a
 * TipTap node view, which TipTap renders itself and which receives no props
 * from the editor; it is portalled under `EditorContent`, so it still sees
 * the context.
 */
const MarkdownEditorComponentsContext =
  React.createContext<MarkdownEditorComponents>(DEFAULT_COMPONENTS);

export function MarkdownEditorComponentsProvider({
  components,
  children,
}: {
  components?: Partial<MarkdownEditorComponents>;
  children: React.ReactNode;
}): React.JSX.Element {
  const Button = components?.Button ?? DEFAULT_COMPONENTS.Button;
  const Input = components?.Input ?? DEFAULT_COMPONENTS.Input;
  const value = React.useMemo(() => ({ Button, Input }), [Button, Input]);
  return (
    <MarkdownEditorComponentsContext.Provider value={value}>
      {children}
    </MarkdownEditorComponentsContext.Provider>
  );
}

export function useMarkdownEditorComponents(): MarkdownEditorComponents {
  return React.useContext(MarkdownEditorComponentsContext);
}
