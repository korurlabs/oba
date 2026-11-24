import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { ACCOUNT_MARK_BOX, AccountMark } from "./account-mark";
import type { AppMark, MarkBox } from "./app-mark";
import { KALKAN_MARK_BOX, KalkanMark } from "./kalkan-mark";
import { KAYA_MARK_BOX, KayaMark } from "./kaya-mark";
import { KAZAN_MARK_BOX, KazanMark } from "./kazan-mark";
import { OTAK_MARK_BOX, OtakMark } from "./otak-mark";
import { ULAK_MARK_BOX, UlakMark } from "./ulak-mark";

const MARKS: [string, AppMark, MarkBox][] = [
  ["Kazan", KazanMark, KAZAN_MARK_BOX],
  ["Kaya", KayaMark, KAYA_MARK_BOX],
  ["Kalkan", KalkanMark, KALKAN_MARK_BOX],
  ["Otak", OtakMark, OTAK_MARK_BOX],
  ["Ulak", UlakMark, ULAK_MARK_BOX],
  ["Account", AccountMark, ACCOUNT_MARK_BOX],
];

describe.each(MARKS)("%s mark", (_name, Mark, box) => {
  test("is decorative and sized by its own box, cropped to the artwork", () => {
    const html = renderToStaticMarkup(<Mark />);

    expect(html).toContain(`viewBox="${box.x} ${box.y} ${box.width} ${box.height}"`);
    expect(html).toContain(`width="${box.width}" height="${box.height}"`);
    expect(html).toContain('aria-hidden="true"');
    expect(html).not.toContain("<text");
  });

  test("nests at its box inside a wordmark, so the logo keeps its drawing", () => {
    const svg = renderToStaticMarkup(<Mark {...box} />).match(/^<svg [^>]*>/)?.[0] ?? "";

    expect(svg).toContain(`x="${box.x}"`);
    expect(svg).toContain(`y="${box.y}"`);
    expect(svg).toContain(`width="${box.width}"`);
    expect(svg).toContain(`height="${box.height}"`);
  });

  test("takes a class, so a caller can size it to a line of text", () => {
    expect(renderToStaticMarkup(<Mark className="h-5 w-auto" />)).toContain('class="h-5 w-auto"');
  });
});
