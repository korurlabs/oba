import { parse, type DefaultTreeAdapterTypes } from "parse5";

/*
 * HTML in a ticket description, read the way a browser reads it. A
 * description may hold any HTML a user typed or pasted, so it is never taken
 * apart with regular expressions: parse5 follows the WHATWG parsing rules
 * (comments ended by `--!>`, tags split by other tags, uppercase names,
 * odd attribute spacing) and needs no DOM, so it runs on the phone as well.
 */

type Element = DefaultTreeAdapterTypes.Element;
type ParentNode = DefaultTreeAdapterTypes.ParentNode;
type ChildNode = DefaultTreeAdapterTypes.ChildNode;

/** Elements whose text is code or styling, never part of what a reader sees. */
const HIDDEN_TEXT_ELEMENTS: ReadonlySet<string> = new Set(["script", "style", "noscript"]);

function isElement(node: ChildNode): node is Element {
  return "tagName" in node;
}

/**
 * The `<body>` a browser builds from the HTML, as the description editor
 * builds it (`DOMParser` on `<body>…</body>`).
 */
export function parseHtmlBody(html: string): Element {
  const body: Element | undefined = elementsByTagName(parse(`<body>${html}</body>`), "body")[0];
  if (!body) throw new Error("HTML parser returned a document without a body");
  return body;
}

/**
 * Every element named `tagName` under `root`, in document order. The content
 * of a `<template>` is not part of the page, so it is not searched.
 */
export function elementsByTagName(root: ParentNode, tagName: string): Element[] {
  const found: Element[] = [];
  const visit = (parent: ParentNode): void => {
    for (const node of parent.childNodes) {
      if (!isElement(node)) continue;
      if (node.tagName === tagName) found.push(node);
      visit(node);
    }
  };
  visit(root);
  return found;
}

/** The value of an attribute, decoded; the parser already keeps only the first of a repeated name. */
export function attributeValue(element: Element, name: string): string | undefined {
  return element.attrs.find((attribute) => attribute.name === name)?.value;
}

/**
 * The text a reader sees: text nodes with their entities decoded, each
 * `<img>` as `imagePlaceholder`, each `<br>` as a line break. Comments,
 * scripts and styles add nothing, and no markup is left in the result.
 * `transformText` runs on each text node, before it joins the rest.
 */
export function htmlPlainText(
  root: ParentNode,
  imagePlaceholder: string,
  transformText: (text: string) => string = (text) => text
): string {
  let text = "";
  const visit = (parent: ParentNode): void => {
    for (const node of parent.childNodes) {
      if (node.nodeName === "#text") {
        text += transformText((node as DefaultTreeAdapterTypes.TextNode).value);
      } else if (isElement(node)) {
        if (node.tagName === "img") text += imagePlaceholder;
        else if (node.tagName === "br") text += "\n";
        else if (!HIDDEN_TEXT_ELEMENTS.has(node.tagName)) visit(node);
      }
    }
  };
  visit(root);
  return text;
}
