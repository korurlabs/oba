import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// A static render cannot open a menu, so the menu parts are stand-ins that
// render their children with the classes and states the dropdown hands them.
// The radio group keeps its value, so an item can be rendered checked as Radix
// does. Copies, not the namespaces: mock.module rewrites a loaded module in
// place, so the afterAll restore would otherwise put the mocks back for the
// files that run later.
const actualMenu = { ...(await import("@oba/ui/components/dropdown-menu")) };
const actualIntl = { ...(await import("next-intl")) };
const actualAuthReact = { ...(await import("next-auth/react")) };
const actualNext = { ...(await import("next/navigation")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };

type PartProps = { children?: ReactNode; className?: string };
const part =
  (slot: string) =>
  ({ children, className }: PartProps) => (
    <div data-slot={slot} className={className}>
      {children}
    </div>
  );
let radioValue: string | undefined;

mock.module("@oba/ui/components/dropdown-menu", () => ({
  ...actualMenu,
  DropdownMenu: part("menu"),
  DropdownMenuTrigger: part("trigger"),
  DropdownMenuContent: part("content"),
  DropdownMenuLabel: part("label"),
  DropdownMenuItem: part("item"),
  DropdownMenuSeparator: ({ className }: PartProps) => (
    <hr data-slot="separator" className={className} />
  ),
  DropdownMenuSub: part("sub"),
  DropdownMenuSubTrigger: part("sub-trigger"),
  DropdownMenuSubContent: part("sub-content"),
  DropdownMenuRadioGroup: ({ children, value }: PartProps & { value?: string }) => {
    radioValue = value;
    return <div data-slot="radio-group">{children}</div>;
  },
  DropdownMenuRadioItem: ({ children, className, value }: PartProps & { value: string }) => (
    <div
      data-slot="radio-item"
      data-value={value}
      data-state={value === radioValue ? "checked" : "unchecked"}
      className={className}
    >
      {children}
    </div>
  ),
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
  useLocale: () => "nl",
}));
mock.module("next-auth/react", () => ({
  ...actualAuthReact,
  useSession: () => ({
    data: { user: { name: "Ada Lovelace", email: "ada@example.test" } },
    status: "authenticated",
  }),
}));
mock.module("next/navigation", () => ({
  ...actualNext,
  useRouter: () => ({ replace: () => undefined, refresh: () => undefined }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ children, href }: { children?: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({ data: undefined }),
}));

afterAll(() => {
  mock.module("@oba/ui/components/dropdown-menu", () => actualMenu);
  mock.module("next-intl", () => actualIntl);
  mock.module("next-auth/react", () => actualAuthReact);
  mock.module("next/navigation", () => actualNext);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
});

const { ProfileDropdown } = await import("./profile-dropdown");
const { LanguageSwitcherSubMenu } = await import("../language-switcher");

function classesOf(html: string, slot: string): string[] {
  return [...html.matchAll(new RegExp(`data-slot="${slot}"[^>]*class="([^"]*)"`, "g"))].map(
    (match) => match[1] ?? ""
  );
}

const withLanguage = <LanguageSwitcherSubMenu supportedLocales={["en", "nl"]} label="language" />;

describe("ProfileDropdown", () => {
  const html = renderToStaticMarkup(<ProfileDropdown extraMenuContent={withLanguage} />);

  test("draws the menu in the square grid panel", () => {
    const [content] = classesOf(html, "content");
    for (const name of ["rounded-none", "border-[#1a64b8]", "bg-size-[16px_16px]"]) {
      expect(content).toContain(name);
    }
  });

  test("gives every row the grid row and highlight, and parts them by hairlines", () => {
    const items = classesOf(html, "item");
    expect(items).toHaveLength(3);
    for (const item of items) {
      expect(item).toContain("rounded-none");
      expect(item).toContain("focus:bg-[rgb(47_143_233/0.08)]");
    }
    const separators = classesOf(html, "separator");
    // Label | Profile | Settings | Language | Log out.
    expect(separators).toHaveLength(4);
    for (const separator of separators) {
      expect(separator).toContain("border-[rgb(47_143_233/0.22)]");
    }
  });

  test("keeps the full name and email readable instead of cutting them", () => {
    expect(html).toContain("Ada Lovelace");
    expect(html).toContain("ada@example.test");
    expect(html).toContain("break-all");
  });

  test("draws the language submenu in the grid look, the current language checked", () => {
    const [subTrigger] = classesOf(html, "sub-trigger");
    expect(subTrigger).toContain("data-[state=open]:bg-[rgb(47_143_233/0.08)]");
    const [subContent] = classesOf(html, "sub-content");
    expect(subContent).toContain("border-[#1a64b8]");

    expect(html).toContain('data-value="nl" data-state="checked"');
    expect(html).toContain('data-value="en" data-state="unchecked"');
    const current = html.match(/data-value="nl" data-state="checked" class="([^"]*)"/)?.[1];
    expect(current).toContain("data-[state=checked]:before:w-0.5");
  });
});
