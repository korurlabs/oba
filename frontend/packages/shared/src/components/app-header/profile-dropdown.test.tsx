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

type PartProps = { children?: ReactNode; className?: string; disabled?: boolean };
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
  // Radix marks a disabled item with aria-disabled and data-disabled, and
  // leaves it out of keyboard focus.
  DropdownMenuItem: ({ children, className, disabled }: PartProps) => (
    <div
      data-slot="item"
      className={className}
      aria-disabled={disabled || undefined}
      data-disabled={disabled ? "" : undefined}
    >
      {children}
    </div>
  ),
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
const { SidebarProvider } = await import("@oba/ui/sky/sidebar");

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
    // Profile, Dark mode, Log out; Language is a submenu.
    expect(items).toHaveLength(3);
    for (const item of items) {
      expect(item).toContain("rounded-none");
    }
    // Dark mode does nothing yet, so only the working rows take the highlight.
    for (const item of [items[0], items[2]]) {
      expect(item).toContain("focus:bg-[rgb(47_143_233/0.08)]");
    }
    const separators = classesOf(html, "separator");
    // Label | Profile | Dark mode | Language | Log out.
    expect(separators).toHaveLength(4);
    for (const separator of separators) {
      expect(separator).toContain("border-[rgb(47_143_233/0.22)]");
    }
  });

  test("lists Profile, Dark mode, Language and Log out, and no Settings", () => {
    const order = ["profile", "darkMode", "language", "logOut"].map((key) =>
      html.search(new RegExp(`<span[^>]*>${key}</span>`))
    );
    expect(order[0]).toBeGreaterThan(-1);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
    expect(html).not.toContain("settings");
  });

  test("links Profile to the given page", () => {
    const own = renderToStaticMarkup(
      <ProfileDropdown profileHref="https://account.example.test" />
    );
    expect(own).toContain('href="https://account.example.test"');
    expect(html).toContain('href="/profile"');
  });

  test("shows Dark mode as not available yet: disabled, switch off, marked Soon", () => {
    const row = html.match(
      /<div data-slot="item"[^>]*>(?:(?!data-slot="item").)*darkMode.*?<\/div>/
    )?.[0];
    expect(row).toBeDefined();
    expect(row).toContain('aria-disabled="true"');
    expect(row).toContain("data-disabled");
    expect(row).toContain("lucide-moon");
    expect(row).toContain("soon");
    expect(row).toContain('role="switch"');
    expect(row).toContain('aria-checked="false"');
    expect(row).toContain('data-state="unchecked"');
    expect(row).toMatch(/<button[^>]*role="switch"[^>]*disabled=""/);
    expect(row).toMatch(/<button[^>]*role="switch"[^>]*tabindex="-1"/);
    const [, darkModeClasses] = classesOf(html, "item");
    expect(darkModeClasses).not.toContain("cursor-pointer");
    expect(darkModeClasses).not.toContain("focus:bg-[rgb(47_143_233/0.08)]");
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

describe("ProfileDropdown sidebar variant", () => {
  // The sidebar trigger is a sidebar menu button, which needs its provider.
  const html = renderToStaticMarkup(
    <SidebarProvider>
      <ProfileDropdown variant="sidebar" showDarkModeSoon={false} extraMenuContent={withLanguage} />
    </SidebarProvider>
  );

  test("keeps the theme's rounded menu", () => {
    const [content] = classesOf(html, "content");
    expect(content).toBe("border-border w-56 rounded-xl border p-2 shadow-xl");
    for (const item of classesOf(html, "item")) {
      expect(item).toContain("rounded-lg");
      expect(item).not.toContain("rounded-none");
    }
  });

  test("leaves out the Dark mode row when the app has a theme of its own", () => {
    expect(html).not.toContain("darkMode");
    // Profile, the two languages of the plain submenu, and Log out.
    expect(classesOf(html, "item")).toHaveLength(4);
  });

  test("keeps the plain language submenu", () => {
    expect(html).not.toContain('data-slot="radio-group"');
    const [subContent] = classesOf(html, "sub-content");
    expect(subContent).toBe("min-w-[180px]");
  });
});
