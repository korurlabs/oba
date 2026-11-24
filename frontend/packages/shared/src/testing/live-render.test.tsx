import { afterEach, describe, expect, test } from "bun:test";
import { useEffect, useState } from "react";

import { cleanup, renderLive } from "./live-render";

afterEach(cleanup);

const effects: number[] = [];

function Counter({ label }: { label: string }) {
  const [count, setCount] = useState(0);
  useEffect(() => {
    effects.push(count);
  }, [count]);
  return (
    <div>
      <span>
        {label} {count}
      </span>
      <button onClick={() => setCount((value: number) => value + 1)} disabled={count >= 2}>
        More
      </button>
    </div>
  );
}

describe("renderLive", () => {
  test("keeps state across presses and runs effects", async () => {
    const screen = await renderLive(<Counter label="n" />);
    expect(screen.text()).toContain("n 0");
    await screen.byText("More")?.click();
    expect(screen.text()).toContain("n 1");
    expect(effects).toEqual([0, 1]);
    await screen.byText("More")?.click();
    expect(screen.byText("More")?.disabled).toBe(true);
    await screen.byText("More")?.click();
    expect(screen.text()).toContain("n 2");
  });

  test("a rerender with other props keeps the state of the same component", async () => {
    const screen = await renderLive(<Counter label="a" />);
    await screen.byText("More")?.click();
    await screen.rerender(<Counter label="b" />);
    expect(screen.text()).toContain("b 1");
  });
});

describe("cleanup", () => {
  test("puts the globals back", async () => {
    const before = typeof (globalThis as { document?: unknown }).document;
    await renderLive(<Counter label="x" />);
    expect(typeof (globalThis as { document?: unknown }).document).toBe("object");
    await cleanup();
    expect(typeof (globalThis as { document?: unknown }).document).toBe(before);
  });
});
