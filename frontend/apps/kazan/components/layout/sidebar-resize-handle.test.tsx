import { afterEach, describe, expect, test } from "bun:test";
import { cleanup, renderLive, type Screen } from "@oba/shared/testing/live-render";
import en from "@/locales/en.json";
import { SidebarResizeHandle } from "./sidebar-resize-handle";
import { Probe, STORAGE_KEY, storage, tree } from "./sidebar-width-support";

const probe = (screen: Screen) => screen.byAttribute("data-probe", "")[0]!;

afterEach(async () => {
  storage.unavailable = false;
  await cleanup();
});

describe("SidebarResizeHandle", () => {
  async function handle(stored: string | null = null) {
    storage.store = new Map(stored === null ? [] : [[STORAGE_KEY, stored]]);
    const screen = await renderLive(
      tree(
        <>
          <SidebarResizeHandle />
          <Probe />
        </>
      )
    );
    await screen.settle();
    const separator = () => screen.byAttribute("role", "separator")[0]!;
    return { screen, separator };
  }

  test("is a labelled vertical separator that reports the width", async () => {
    const { separator } = await handle("272");
    expect(separator().attribute("aria-label")).toBe(en.sidebar.resizeSidebar);
    expect(separator().attribute("aria-orientation")).toBe("vertical");
    expect(separator().attribute("aria-valuenow")).toBe("272");
    expect(separator().attribute("tabIndex")).toBe("0");
  });

  test("ArrowLeft narrows by 16 and remembers it", async () => {
    const { screen, separator } = await handle();
    await separator().keyDown("ArrowLeft");
    expect(probe(screen).attribute("data-width")).toBe("304");
    expect(storage.store.get(STORAGE_KEY)).toBe("304");
  });

  test("ArrowRight widens by 16, up to 320", async () => {
    const { screen, separator } = await handle("240");
    await separator().keyDown("ArrowRight");
    expect(probe(screen).attribute("data-width")).toBe("256");
    await separator().keyDown("End");
    await separator().keyDown("ArrowRight");
    expect(probe(screen).attribute("data-width")).toBe("320");
  });

  test("Home and End jump to the narrowest and widest panel", async () => {
    const { screen, separator } = await handle("272");
    await separator().keyDown("Home");
    expect(probe(screen).attribute("data-width")).toBe("224");
    expect(storage.store.get(STORAGE_KEY)).toBe("224");
    await separator().keyDown("End");
    expect(probe(screen).attribute("data-width")).toBe("320");
  });

  test("ArrowLeft at the narrowest width collapses the panel to icons, keeping the width", async () => {
    const { screen, separator } = await handle("224");
    await separator().keyDown("ArrowLeft");
    expect(probe(screen).attribute("data-open")).toBe("false");
    expect(probe(screen).attribute("data-width")).toBe("224");
    expect(separator().attribute("aria-valuenow")).toBe("0");
  });

  test("ArrowRight on a collapsed panel expands it again at its width", async () => {
    const { screen, separator } = await handle("224");
    await separator().keyDown("ArrowLeft");
    await separator().keyDown("ArrowRight");
    expect(probe(screen).attribute("data-open")).toBe("true");
    expect(probe(screen).attribute("data-width")).toBe("224");
  });

  test("Enter toggles the panel", async () => {
    const { screen, separator } = await handle();
    await separator().keyDown("Enter");
    expect(probe(screen).attribute("data-open")).toBe("false");
    await separator().keyDown("Enter");
    expect(probe(screen).attribute("data-open")).toBe("true");
  });

  test("ignores other keys", async () => {
    const { screen, separator } = await handle("272");
    await separator().keyDown("a");
    expect(probe(screen).attribute("data-width")).toBe("272");
    expect(probe(screen).attribute("data-open")).toBe("true");
  });
});
