import { describe, expect, test } from 'bun:test';
import { Editor, type JSONContent } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import TaskList from '@tiptap/extension-task-list';
import TaskItem from '@tiptap/extension-task-item';
import Image from '@tiptap/extension-image';
import { Markdown } from 'tiptap-markdown';
import {
  markdownStarterKit,
  safeLinkExtension,
  serializeImageMarkdown,
  syncExternalValue,
} from './markdown-editor';

// The editor's Markdown settings with the image serializer under test. The
// documents are built as JSON, so no DOM is needed to parse them.
function toMarkdown(content: JSONContent[]): string {
  const editor = new Editor({
    extensions: [
      StarterKit.configure({ heading: { levels: [1, 2, 3, 4, 5, 6] } }),
      TaskList,
      TaskItem.configure({ nested: true }),
      Image.extend({
        addAttributes() {
          return {
            ...this.parent?.(),
            'data-attachment-id': { default: null },
            width: { default: null },
            height: { default: null },
          };
        },
        addStorage() {
          return { ...this.parent?.(), markdown: { serialize: serializeImageMarkdown, parse: {} } };
        },
      }),
      Markdown.configure({ html: true, tightLists: true, bulletListMarker: '-' }),
    ],
    content: { type: 'doc', content },
  });
  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (editor.storage as any).markdown.getMarkdown() as string;
  } finally {
    editor.destroy();
  }
}

const image = (name: string): JSONContent => ({
  type: 'image',
  attrs: { src: `https://x.test/${name}.png`, alt: name, width: 1, height: 1 },
});
const tag = (name: string): string =>
  `<img src="https://x.test/${name}.png" alt="${name}" width="1" height="1" loading="lazy" decoding="async">`;
const paragraph = (text: string): JSONContent => ({
  type: 'paragraph',
  content: [{ type: 'text', text }],
});
const listItem = (type: 'listItem' | 'taskItem', text: string, checked?: boolean): JSONContent => ({
  type,
  ...(checked === undefined ? {} : { attrs: { checked } }),
  content: [paragraph(text)],
});

describe('serializeImageMarkdown', () => {
  test('writes every attribute, HTML-escaped', () => {
    expect(
      toMarkdown([
        {
          type: 'image',
          attrs: {
            src: 'https://x.test/i.png?a=1&b=2',
            alt: 'a "q" <b>',
            width: 1200,
            height: 800,
            'data-attachment-id': '6f1c2b9e-1111-4222-8333-944455556666',
          },
        },
      ]),
    ).toBe(
      '<img src="https://x.test/i.png?a=1&amp;b=2" alt="a &quot;q&quot; &lt;b&gt;" width="1200" height="800" data-attachment-id="6f1c2b9e-1111-4222-8333-944455556666" loading="lazy" decoding="async">',
    );
  });

  test('a task list after an image starts on its own line', () => {
    expect(
      toMarkdown([
        image('a'),
        {
          type: 'taskList',
          content: [listItem('taskItem', 'task', false), listItem('taskItem', 'done', true)],
        },
      ]),
      // tiptap-markdown writes task lists loose, image or not.
    ).toBe(`${tag('a')}\n\n- [ ] task\n\n- [x] done`);
  });

  test('a bullet list after an image starts on its own line', () => {
    expect(
      toMarkdown([
        image('a'),
        { type: 'bulletList', content: [listItem('listItem', 'one'), listItem('listItem', 'two')] },
      ]),
    ).toBe(`${tag('a')}\n\n- one\n- two`);
  });

  test('a paragraph after an image is its own block', () => {
    expect(toMarkdown([image('a'), paragraph('Next paragraph')])).toBe(
      `${tag('a')}\n\nNext paragraph`,
    );
  });

  test('a heading after an image starts on its own line', () => {
    expect(
      toMarkdown([
        image('a'),
        { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Heading' }] },
      ]),
    ).toBe(`${tag('a')}\n\n## Heading`);
  });

  test('an image at the end adds no trailing blank line', () => {
    expect(toMarkdown([paragraph('Text'), image('a')])).toBe(`Text\n\n${tag('a')}`);
  });

  test('two images in a row are two blocks', () => {
    expect(toMarkdown([image('a'), image('b')])).toBe(`${tag('a')}\n\n${tag('b')}`);
  });

  test('an image between paragraphs is separated on both sides', () => {
    expect(toMarkdown([paragraph('Intro'), image('a'), paragraph('outro')])).toBe(
      `Intro\n\n${tag('a')}\n\noutro`,
    );
  });
});

// A stand-in editor: its Markdown and the setContent calls are all the sync reads and makes.
function fakeEditor(initial: string) {
  const calls: { content: string; emitUpdate: boolean | undefined }[] = [];
  let markdown = initial;
  const editor = {
    storage: { markdown: { getMarkdown: () => markdown } },
    commands: {
      setContent: (content: string, options?: { emitUpdate?: boolean }) => {
        calls.push({ content, emitUpdate: options?.emitUpdate });
        markdown = content;
        return true;
      },
    },
  };
  return {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    editor: editor as any,
    calls,
    type: (next: string) => {
      markdown = next;
    },
  };
}

describe('syncExternalValue', () => {
  test('a link appended by the form shows in the editor after an update that changed nothing', () => {
    const { editor, calls } = fakeEditor('Body');
    const synced = { current: 'Body' as string | null };
    // The editor reports its unchanged Markdown, as it does when it is made
    // editable; the form value stays the same, so no sync runs for it.
    synced.current = 'Body';

    syncExternalValue(editor, 'Body\n\n[file.pdf](https://files/file.pdf)', synced);

    expect(calls).toEqual([
      { content: 'Body\n\n[file.pdf](https://files/file.pdf)', emitUpdate: false },
    ]);
  });

  test('the editor\'s own change echoed back by the form leaves the content alone', () => {
    const { editor, calls, type } = fakeEditor('');
    const synced = { current: '' as string | null };
    type('Typed');
    synced.current = 'Typed';

    syncExternalValue(editor, 'Typed', synced);

    expect(calls).toEqual([]);
  });

  test('a reset followed by a value typed earlier still reaches the editor', () => {
    const { editor, calls, type } = fakeEditor('');
    const synced = { current: '' as string | null };
    type('Draft');
    synced.current = 'Draft';

    syncExternalValue(editor, '', synced);
    syncExternalValue(editor, 'Draft', synced);

    expect(calls).toEqual([
      { content: '', emitUpdate: false },
      { content: 'Draft', emitUpdate: false },
    ]);
  });

  test('a value the editor already shows is not set again', () => {
    const { editor, calls } = fakeEditor('Same');
    const synced = { current: null as string | null };

    syncExternalValue(editor, 'Same', synced);

    expect(calls).toEqual([]);
    expect(synced.current).toBe('Same');
  });
});

describe('editable switch', () => {
  test('switching editable without an update event reports nothing to onChange', () => {
    let updates = 0;
    const editor = new Editor({
      extensions: [StarterKit, Markdown],
      content: { type: 'doc', content: [paragraph('Body')] },
      onUpdate: () => {
        updates += 1;
      },
    });
    try {
      editor.setEditable(false, false);
      editor.setEditable(true, false);
      expect(updates).toBe(0);
      // Without the second argument Tiptap reports an update, which once made the editor
      // skip the next value the form set.
      editor.setEditable(false);
      expect(updates).toBe(1);
    } finally {
      editor.destroy();
    }
  });
});

// A stand-in for the `<a>` element the browser parses from stored content: the
// link parse rule reads attributes through getAttribute only.
function anchor(attributes: Record<string, string>) {
  return {
    getAttribute: (name: string): string | null => attributes[name] ?? null,
    hasAttribute: (name: string): boolean => name in attributes,
  } as unknown as HTMLElement;
}

describe('safeLinkExtension', () => {
  // The editor's own StarterKit, so a second link mark from it would show here.
  const linkEditor = () =>
    new Editor({
      extensions: [markdownStarterKit, safeLinkExtension],
      content: { type: 'doc', content: [paragraph('Body')] },
    });

  // What the schema reads from a stored `<a>` and what it renders back.
  function parseAndRender(attributes: Record<string, string>) {
    const editor = linkEditor();
    try {
      const linkType = editor.schema.marks.link!;
      const parsed = linkType.spec.parseDOM![0]!.getAttrs!(anchor(attributes) as never);
      if (parsed === false) return { parsed, rendered: null };
      const spec = linkType.spec.toDOM!(linkType.create(parsed ?? {}), true) as [
        string,
        Record<string, unknown>,
      ];
      return { parsed, rendered: spec[1] };
    } finally {
      editor.destroy();
    }
  }

  test('a stored full-screen phishing link keeps only its href', () => {
    // <a href="https://evil.example/login" class="fixed inset-0 z-50 bg-background" rel="opener">x</a>
    const { parsed, rendered } = parseAndRender({
      href: 'https://evil.example/login',
      class: 'fixed inset-0 z-50 bg-background',
      rel: 'opener',
    });

    expect(parsed).toEqual({ href: 'https://evil.example/login' });
    expect(rendered).toMatchObject({
      href: 'https://evil.example/login',
      class: 'text-primary underline cursor-pointer',
      rel: 'noopener noreferrer',
      target: '_blank',
    });
  });

  test('a stored target and title: the title stays, the target does not', () => {
    const { parsed, rendered } = parseAndRender({
      href: 'https://x.test/',
      target: '_self',
      title: 'Docs',
    });

    expect(parsed).toEqual({ href: 'https://x.test/', title: 'Docs' });
    expect(rendered).toMatchObject({ target: '_blank', rel: 'noopener noreferrer', title: 'Docs' });
  });

  test('a javascript: link is still refused', () => {
    expect(parseAndRender({ href: 'javascript:alert(1)' }).parsed).toBe(false);
  });

  test('a mark that holds author attributes still renders the fixed ones', () => {
    const editor = linkEditor();
    try {
      const linkType = editor.schema.marks.link!;
      const mark = linkType.create({
        href: 'https://evil.example/login',
        class: 'fixed inset-0',
        rel: 'opener',
        target: '_self',
      });
      const [, rendered] = linkType.spec.toDOM!(mark, true) as [string, Record<string, unknown>];

      expect(rendered).toMatchObject({
        class: 'text-primary underline cursor-pointer',
        rel: 'noopener noreferrer',
        target: '_blank',
      });
    } finally {
      editor.destroy();
    }
  });
});
