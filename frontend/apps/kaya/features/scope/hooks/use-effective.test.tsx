import { describe, expect, test } from "bun:test";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";

import { useShowInherited } from "./use-effective";

function Probe() {
  const [value] = useShowInherited();
  return <span>{`inherited=${String(value)}`}</span>;
}
const render = (searchParams?: Record<string, string>) =>
  renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={searchParams}>
      <Probe />
    </NuqsTestingAdapter>
  );

describe("useShowInherited", () => {
  test("a pack page shows its own items only until the URL asks for the inherited ones", () => {
    expect(render()).toContain("inherited=false");
    expect(render({ inherited: "false" })).toContain("inherited=false");
  });

  test("the inherited parameter turns the toggle on, so a link opens the same view", () => {
    expect(render({ inherited: "true" })).toContain("inherited=true");
  });

  test("an unreadable value falls back to the own view", () => {
    expect(render({ inherited: "maybe" })).toContain("inherited=false");
  });
});
