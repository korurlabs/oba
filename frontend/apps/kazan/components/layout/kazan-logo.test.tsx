import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { KazanLogo } from "./kazan-logo";

describe("KazanLogo", () => {
  test("renders an accessible image named Kazan at the family's height", () => {
    const html = renderToStaticMarkup(<KazanLogo />);

    expect(html).toContain('role="img"');
    expect(html).toContain('aria-label="Kazan"');
    expect(html).toContain('viewBox="0 0 600 164"');
    expect(html).toContain(">Kazan</text>");
  });

  test("uses the family blue for the mark and the name", () => {
    const html = renderToStaticMarkup(<KazanLogo />);

    expect(html).toContain('stroke="#2f8fe9"');
    expect(html).toContain('fill="#2f8fe9"');
    expect(html).toContain('stroke-width="7"');
    expect(html).toContain('stroke-width="3"');
  });

  test("defaults to the header size and accepts another class", () => {
    expect(renderToStaticMarkup(<KazanLogo />)).toContain('class="h-5 w-auto"');
    expect(renderToStaticMarkup(<KazanLogo className="h-7 w-auto" />)).toContain(
      'class="h-7 w-auto"'
    );
  });
});
