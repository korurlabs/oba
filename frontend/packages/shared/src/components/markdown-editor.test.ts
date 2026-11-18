import { describe, expect, test } from 'bun:test';
import { Editor, type JSONContent } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import TaskList from '@tiptap/extension-task-list';
import TaskItem from '@tiptap/extension-task-item';
import Image from '@tiptap/extension-image';
import { Markdown } from 'tiptap-markdown';
import { serializeImageMarkdown } from './markdown-editor';

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
