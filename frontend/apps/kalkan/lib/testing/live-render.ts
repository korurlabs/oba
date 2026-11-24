import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";

/**
 * A render that keeps state, effects and event handlers alive, for tests of
 * components whose behaviour depends on what happens after the first render.
 * No DOM library is installed, so the tree is drawn on a small stand-in for
 * the part of the DOM that React touches; elements are found by their text or
 * their attributes and pressed through the handlers React attached.
 */

type Listener = (event: unknown) => void;

class FakeNode {
  nodeType = 1;
  nodeName: string;
  tagName: string;
  namespaceURI = "http://www.w3.org/1999/xhtml";
  parentNode: FakeNode | null = null;
  childNodes: FakeNode[] = [];
  attributes = new Map<string, string>();
  style: Record<string, string> & { setProperty: (name: string, value: string) => void };
  listeners = new Map<string, Listener[]>();
  nodeValue: string | null = null;
  ownerDocument: FakeDocument;
  [key: string]: unknown;

  constructor(name: string, owner: FakeDocument) {
    this.nodeName = name.toUpperCase();
    this.tagName = this.nodeName;
    this.ownerDocument = owner;
    const style: Record<string, string> = {};
    this.style = Object.assign(style, {
      setProperty: (key: string, value: string) => {
        style[key] = value;
      },
    });
  }

  get firstChild(): FakeNode | null {
    return this.childNodes[0] ?? null;
  }
  get lastChild(): FakeNode | null {
    return this.childNodes[this.childNodes.length - 1] ?? null;
  }
  get nextSibling(): FakeNode | null {
    const siblings = this.parentNode?.childNodes ?? [];
    return siblings[siblings.indexOf(this) + 1] ?? null;
  }
  get previousSibling(): FakeNode | null {
    const siblings = this.parentNode?.childNodes ?? [];
    return siblings[siblings.indexOf(this) - 1] ?? null;
  }
  get textContent(): string {
    if (this.nodeType === 3) return this.nodeValue ?? "";
    return this.childNodes.map((child: FakeNode) => child.textContent).join("");
  }
  set textContent(value: string) {
    this.childNodes.forEach((child: FakeNode) => {
      child.parentNode = null;
    });
    this.childNodes = [];
    if (value !== "") this.appendChild(this.ownerDocument.createTextNode(value));
  }

  /** A select reads its options by position, as the browser's collection does. */
  get options(): FakeNode[] {
    return this.childNodes;
  }
  get value(): string {
    return this.getAttribute("value") ?? this.textContent;
  }
  set value(next: string) {
    this.setAttribute("value", next);
  }

  appendChild(child: FakeNode): FakeNode {
    return this.insertBefore(child, null);
  }
  insertBefore(child: FakeNode, before: FakeNode | null): FakeNode {
    child.parentNode?.removeChild(child);
    const at = before === null ? this.childNodes.length : this.childNodes.indexOf(before);
    this.childNodes.splice(at, 0, child);
    child.parentNode = this;
    return child;
  }
  removeChild(child: FakeNode): FakeNode {
    const at = this.childNodes.indexOf(child);
    if (at !== -1) this.childNodes.splice(at, 1);
    child.parentNode = null;
    return child;
  }
  setAttribute(name: string, value: unknown): void {
    this.attributes.set(name, String(value));
  }
  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null;
  }
  hasAttribute(name: string): boolean {
    return this.attributes.has(name);
  }
  removeAttribute(name: string): void {
    this.attributes.delete(name);
  }
  setAttributeNS(_namespace: string | null, name: string, value: unknown): void {
    this.setAttribute(name, value);
  }
  removeAttributeNS(_namespace: string | null, name: string): void {
    this.removeAttribute(name);
  }
  addEventListener(type: string, listener: Listener): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }
  removeEventListener(type: string, listener: Listener): void {
    this.listeners.set(
      type,
      (this.listeners.get(type) ?? []).filter((entry: Listener) => entry !== listener)
    );
  }
  contains(other: FakeNode | null): boolean {
    for (let node = other; node !== null; node = node.parentNode) if (node === this) return true;
    return false;
  }
  getRootNode(): FakeNode {
    return this.parentNode?.getRootNode() ?? this;
  }
  focus(): void {
    this.ownerDocument.activeElement = this;
  }
  blur(): void {}
}

class FakeText extends FakeNode {
  constructor(value: string, owner: FakeDocument) {
    super("#text", owner);
    this.nodeType = 3;
    this.nodeValue = value;
  }
  get data(): string {
    return this.nodeValue ?? "";
  }
  set data(value: string) {
    this.nodeValue = value;
  }
}

class FakeDocument extends FakeNode {
  activeElement: FakeNode | null = null;
  documentElement: FakeNode;
  body: FakeNode;
  defaultView: unknown = globalThis;

  constructor() {
    super("#document", undefined as unknown as FakeDocument);
    this.nodeType = 9;
    this.ownerDocument = this;
    this.documentElement = this.createElement("html");
    this.body = this.createElement("body");
    this.activeElement = this.body;
  }

  createElement(name: string): FakeNode {
    return new FakeNode(name, this);
  }
  createElementNS(_namespace: string, name: string): FakeNode {
    return this.createElement(name);
  }
  createTextNode(value: string): FakeText {
    return new FakeText(value, this);
  }
  createComment(value: string): FakeText {
    const node = new FakeText(value, this);
    node.nodeType = 8;
    return node;
  }
}

const GLOBAL_NAMES = [
  "IS_REACT_ACT_ENVIRONMENT",
  "document",
  "window",
  "location",
  "HTMLIFrameElement",
  "HTMLElement",
  "Element",
  "Node",
  "HTMLInputElement",
] as const;

const scope = globalThis as Record<string, unknown>;
let saved: Map<string, { present: boolean; value: unknown }> | null = null;
const mounted = new Set<() => Promise<void>>();

/**
 * Defines the globals React reads from a browser, and remembers what they
 * were so `cleanup` puts them back: other test files run in the same process.
 */
function installGlobals(document: FakeDocument): void {
  if (saved === null) {
    saved = new Map(
      GLOBAL_NAMES.map((name: string) => [name, { present: name in scope, value: scope[name] }])
    );
  }
  scope.IS_REACT_ACT_ENVIRONMENT = true;
  scope.document = document;
  scope.location = { pathname: "/", search: "", hash: "", href: "http://localhost/" };
  for (const name of ["HTMLIFrameElement", "HTMLElement", "Element", "Node", "HTMLInputElement"]) {
    scope[name] = class {};
  }
  scope.window = Object.assign(Object.create((scope.window as object | undefined) ?? null), {
    document,
    innerWidth: 1280,
    setTimeout: globalThis.setTimeout.bind(globalThis),
    clearTimeout: globalThis.clearTimeout.bind(globalThis),
    matchMedia: () => ({ addEventListener: () => undefined, removeEventListener: () => undefined }),
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  });
}

/** Unmounts every live render and restores the globals it replaced. */
export async function cleanup(): Promise<void> {
  for (const unmount of [...mounted]) await unmount();
  mounted.clear();
  if (saved === null) return;
  for (const [name, previous] of saved) {
    if (previous.present) scope[name] = previous.value;
    else delete scope[name];
  }
  saved = null;
}

export type Screen = {
  /** The text of the whole tree. */
  text: () => string;
  /** The element with this text, as a handle to press or read. */
  byText: (text: string) => Handle | null;
  /** Every element whose given attribute holds the value. */
  byAttribute: (name: string, value: string) => Handle[];
  /** Every element with this tag name. */
  byTag: (tag: string) => Handle[];
  /** Runs work that updates state and lets React settle. */
  act: (work: () => void | Promise<void>) => Promise<void>;
  /** Lets pending promises, such as a query answer, finish and React settle. */
  settle: () => Promise<void>;
  /** Renders a new element in place of the current one. */
  rerender: (element: ReactElement) => Promise<void>;
  unmount: () => Promise<void>;
};

export type Handle = {
  text: string;
  disabled: boolean;
  /** The value React holds on a form control. */
  value: string | null;
  attribute: (name: string) => string | null;
  /** Runs the handler React attached to a change of a form control, with the value chosen. */
  change: (value: string) => Promise<void>;
  /** Runs the handler React attached to a click, as the browser would. */
  click: () => Promise<void>;
  /** Runs the handler React attached to a key press on the element. */
  keyDown: (key: string) => Promise<void>;
  /** Runs the handler React attached to a form's submit. */
  submit: () => Promise<void>;
  /** Whether this element holds the focus. */
  focused: () => boolean;
};

function elementsOf(node: FakeNode): FakeNode[] {
  return [node, ...node.childNodes.flatMap(elementsOf)];
}

/** The React props of a host node, as React keeps them on it. */
function propsOf(node: FakeNode): Record<string, unknown> {
  const key = Object.keys(node).find((name: string) => name.startsWith("__reactProps$"));
  return key === undefined ? {} : (node[key] as Record<string, unknown>);
}

function handleOf(node: FakeNode, press: (work: () => void) => Promise<void>): Handle {
  return {
    text: node.textContent,
    get disabled() {
      return propsOf(node).disabled === true;
    },
    get value() {
      const held = propsOf(node).value;
      return held === undefined || held === null ? null : String(held);
    },
    attribute: (name: string) => {
      const value = propsOf(node)[name];
      return value === undefined || value === null ? null : String(value);
    },
    change: (value: string) =>
      press(() => {
        const target = { value };
        (propsOf(node).onChange as ((event: unknown) => void) | undefined)?.({
          type: "change",
          target,
          currentTarget: target,
        });
      }),
    keyDown: (key: string) =>
      press(() => {
        (propsOf(node).onKeyDown as ((event: unknown) => void) | undefined)?.({
          type: "keydown",
          key,
          target: node,
          currentTarget: node,
          preventDefault: () => undefined,
          stopPropagation: () => undefined,
        });
      }),
    submit: () =>
      press(() => {
        (propsOf(node).onSubmit as ((event: unknown) => void) | undefined)?.({
          type: "submit",
          target: node,
          currentTarget: node,
          preventDefault: () => undefined,
          stopPropagation: () => undefined,
        });
      }),
    focused: () => node.ownerDocument.activeElement === node,
    click: () =>
      press(() => {
        const props = propsOf(node);
        if (props.disabled === true) return;
        (props.onClick as ((event: unknown) => void) | undefined)?.({
          type: "click",
          target: node,
          currentTarget: node,
          preventDefault: () => undefined,
          stopPropagation: () => undefined,
        });
      }),
  };
}

/** Renders `element` and keeps it live until `unmount`. */
export async function renderLive(element: ReactElement): Promise<Screen> {
  const document = new FakeDocument();
  installGlobals(document);
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root: Root = createRoot(container as unknown as Element);
  const press = async (work: () => void | Promise<void>): Promise<void> => {
    await act(async () => {
      await work();
    });
  };
  await press(() => root.render(element));
  const unmount = async () => {
    if (mounted.delete(unmount)) await press(() => root.unmount());
  };
  mounted.add(unmount);
  const all = () => elementsOf(container);
  return {
    text: () => container.textContent,
    byText: (text: string) => {
      const matches = all().filter(
        (node: FakeNode) => node.nodeType === 1 && node.textContent === text
      );
      const innermost = matches[matches.length - 1];
      return innermost === undefined ? null : handleOf(innermost, press);
    },
    byAttribute: (name: string, value: string) =>
      all()
        .filter((node: FakeNode) => node.nodeType === 1 && String(propsOf(node)[name]) === value)
        .map((node: FakeNode) => handleOf(node, press)),
    byTag: (tag: string) =>
      all()
        .filter((node: FakeNode) => node.nodeType === 1 && node.tagName === tag.toUpperCase())
        .map((node: FakeNode) => handleOf(node, press)),
    act: press,
    settle: () =>
      press(async () => {
        for (let round = 0; round < 3; round += 1) {
          await new Promise<void>((resolve) => setTimeout(resolve, 0));
        }
      }),
    rerender: (next: ReactElement) => press(() => root.render(next)),
    unmount,
  };
}
