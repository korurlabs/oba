import { afterEach, describe, expect, test } from "bun:test";
import { cleanup, renderLive, type Screen } from "@oba/shared/testing/live-render";
import { Probe, STORAGE_KEY, storage, tree } from "./sidebar-width-support";

const probe = (screen: Screen) => screen.byAttribute("data-probe", "")[0]!;

async function widthAfterRestore(stored: string | null): Promise<string | null> {
  storage.store = new Map(stored === null ? [] : [[STORAGE_KEY, stored]]);
  const screen = await renderLive(tree(<Probe />));
  await screen.settle();
  return probe(screen).attribute("data-width");
}

afterEach(async () => {
  storage.unavailable = false;
  await cleanup();
});

describe("SidebarWidthProvider", () => {
  test("starts at the widest panel when nothing is stored", async () => {
    expect(await widthAfterRestore(null)).toBe("320");
  });

  test("reads a stored width", async () => {
    expect(await widthAfterRestore("272")).toBe("272");
  });

  test("clamps a stored width to 224 at the low end", async () => {
    expect(await widthAfterRestore("100")).toBe("224");
  });

  test("clamps a stored width to 320 at the high end", async () => {
    expect(await widthAfterRestore("999")).toBe("320");
  });

  test("ignores a stored value that is not a number", async () => {
    expect(await widthAfterRestore("wide")).toBe("320");
    expect(await widthAfterRestore("")).toBe("320");
    expect(await widthAfterRestore("   ")).toBe("320");
    expect(await widthAfterRestore("NaN")).toBe("320");
  });

  test("keeps the default and works when storage is blocked", async () => {
    storage.unavailable = true;
    expect(await widthAfterRestore("272")).toBe("320");
  });

  test("hands the width to the sidebar as --sidebar-width", async () => {
    storage.store = new Map([[STORAGE_KEY, "250"]]);
    const screen = await renderLive(tree(<Probe />));
    await screen.settle();
    expect(probe(screen).attribute("data-css-width")).toBe("250px");
  });
});
