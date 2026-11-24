import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { AccountLogo } from "./account-logo";

describe("AccountLogo", () => {
  test("renders an accessible image named Account at the family's height", () => {
    const html = renderToStaticMarkup(<AccountLogo />);

    expect(html).toContain('role="img"');
    expect(html).toContain('aria-label="Account"');
    expect(html).toContain('viewBox="0 0 710 164"');
    expect(html).toContain(">Account</text>");
  });

  test("uses the family blue for the mark and the name", () => {
    const html = renderToStaticMarkup(<AccountLogo />);

    expect(html).toContain('stroke="#2f8fe9"');
    expect(html).toContain('fill="#2f8fe9"');
    expect(html).toContain('stroke-width="7"');
    expect(html).toContain('stroke-width="3"');
  });

  test("defaults to the header size and accepts another class", () => {
    expect(renderToStaticMarkup(<AccountLogo />)).toContain('class="h-5 w-auto"');
    expect(renderToStaticMarkup(<AccountLogo className="h-7 w-auto" />)).toContain(
      'class="h-7 w-auto"'
    );
  });
});
