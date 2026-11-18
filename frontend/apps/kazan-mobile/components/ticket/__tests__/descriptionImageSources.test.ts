import { GlobalRegistrator } from "@happy-dom/global-registrator";
import { afterAll, beforeAll, describe, expect, it, mock } from "bun:test";
import { Editor } from "@tiptap/core";

import { createDescriptionExtensions } from "@/components/ticket/descriptionEditorExtensions";

// The model reads attachment ids with ticketAttachmentsModel.ts, which loads
// lib/api.ts; these are the stubs lib/__tests__/api.test.ts uses for it.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("expo-secure-store", () => ({
  getItemAsync: async () => null,
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kazanWebUrl: "https://kazan.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

const { descriptionImageSources } = await import("@/components/ticket/descriptionImagesModel");

/**
 * The viewer opens "image n" of a description, and the reading view reports
 * the index of the tapped image among the images the editor shows. Each case
 * pins the expected list and checks it against the editor itself (the same
 * extensions, loading the same Markdown), so the two counts cannot drift.
 */

beforeAll(() => {
  GlobalRegistrator.register();
});

afterAll(async () => {
  await GlobalRegistrator.unregister();
});

/** The `src` of every image node the editor builds from the Markdown, in document order. */
function editorImageSources(markdown: string): string[] {
  const editor = new Editor({
    extensions: createDescriptionExtensions({
      placeholder: "Describe",
      onOpenLinkRow: () => undefined,
    }),
    content: markdown,
  });
  try {
    const sources: string[] = [];
    editor.state.doc.descendants((node) => {
      if (node.type.name === "image") sources.push(String(node.attrs.src ?? ""));
      return true;
    });
    return sources;
  } finally {
    editor.destroy();
  }
}

const A = "https://img.test/a.png";
const B = "https://img.test/b.png";
const C = "https://img.test/c.png";
const tag = (src: string): string => `<img src="${src}" alt="x" width="10" height="10">`;

/** [case, description, the image sources the editor shows]. */
const CASES: readonly (readonly [string, string, readonly string[]])[] = [
  [
    "a fenced code block hides tags and Markdown images",
    `![a](${A})\n\n\`\`\`html\n${tag(B)}\n![b](${B})\n\`\`\`\n\n${tag(C)}`,
    [A, C],
  ],
  ["a tilde fence hides images", `~~~\n![b](${B})\n~~~\n\n![a](${A})`, [A]],
  [
    "an unclosed fence hides everything after it",
    `![a](${A})\n\n\`\`\`\n![b](${B})\n\n${tag(C)}`,
    [A],
  ],
  [
    "a fence inside a list item hides its images",
    `- item\n\n  \`\`\`\n  ![b](${B})\n  \`\`\`\n- ![a](${A})`,
    [A],
  ],
  [
    "an indented code block hides images",
    `Text\n\n    ![b](${B})\n    ${tag(C)}\n\n![a](${A})`,
    [A],
  ],
  ["an indented line right after a paragraph continues it", `Text\n    ![a](${A})`, [A]],
  [
    "inline code spans hide images, also with longer backtick runs",
    `See \`![b](${B})\` and \`\`${tag(C)} \` x\`\` then ![a](${A})`,
    [A],
  ],
  ["an unmatched backtick is plain text", `One \` tick ![a](${A})`, [A]],
  [
    "an escaped image marker is text, an escaped backslash is not",
    `\\![b](${B}) and \\\\![a](${A})`,
    [A],
  ],
  [
    "an HTML comment hides the images in it",
    `<!--\n${tag(B)}\n![b](${B})\n-->\n\nText <!-- ${tag(C)} --> ![a](${A})`,
    [A],
  ],
  ["an image tag after a comment on the same line is shown", `<!-- note --> ${tag(A)}`, [A]],
  [
    "an <img> without src or with a data: src is dropped",
    `<img alt="none">\n\n<img src="data:image/png;base64,AAAA" alt="inline">\n\n${tag(A)}`,
    [A],
  ],
  [
    "a Markdown image with a data: or script URL is dropped",
    `![d](data:image/png;base64,AAAA) ![j](javascript:alert(1)) ![a](${A})`,
    [A],
  ],
  [
    "an angle-bracket destination may hold spaces",
    "![a](<https://img.test/a b.png>) ![b](https://img.test/b.png 'title')",
    ["https://img.test/a%20b.png", B],
  ],
  [
    "reference images: full, collapsed and shortcut; an unknown label is text",
    `![one][r1] ![r2][] ![r3] ![none][missing]\n\n[r1]: ${A}\n[R2]: <${B}> "Title"\n[r3]: ${C}`,
    [A, B, C],
  ],
  [
    "images in links, lists, quotes, tables and headings count in order",
    [
      `# ![h](${A})`,
      `[![l](${B})](https://example.com)`,
      `- one\n  - ![n](${C})`,
      `> ${tag(A)}`,
      `| a | b |\n| - | - |\n| ![t](${B}) | x |`,
    ].join("\n\n"),
    [A, B, C, A, B],
  ],
  ["Markdown inside an HTML block is not parsed", `${tag(A)}\n![b](${B})\n\n![c](${C})`, [A, C]],
  [
    "tags quoted in single quotes, unquoted or in capitals count",
    `<img src='${A}'>\n\n<IMG SRC=${B} ALT=x>\n\nText <img alt="y" src="${C}" /> end`,
    [A, B, C],
  ],
  ["a quote continued lazily keeps its images", `> quoted\n![a](${A})\n\n${tag(B)}`, [A, B]],
  [
    "a web-saved description with a pending image",
    `Steps\n\n${tag(A)}\n\n<img src="kazan-pending:3b2f" alt="IMG_1.jpg" width="1200" height="900" loading="lazy" decoding="async">\n\n- [ ] check`,
    [A, "kazan-pending:3b2f"],
  ],
];

describe("descriptionImageSources counts the images the editor shows", () => {
  for (const [name, markdown, expected] of CASES) {
    it(name, () => {
      const sources: string[] = descriptionImageSources(markdown).map((image) => image.src);
      expect(sources).toEqual([...expected]);
      expect(editorImageSources(markdown)).toEqual([...expected]);
    });
  }
});
