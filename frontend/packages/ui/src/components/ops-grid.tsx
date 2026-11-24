/**
 * The "ops grid" look of the Korur website's panels (its language picker and
 * menu sheets) as Tailwind class lists, so an Oba control that should read as
 * part of the same family (the organization selector's menu, a tree row's
 * "+" action) draws it from one place.
 *
 * Each constant is a complete class list, never built from parts: Tailwind
 * finds classes by scanning source text, and this folder is one every sky
 * app's `@source` already scans. The `dark:` values only apply where an app
 * sets the `.dark` class (Kazan); the sky apps have no dark theme.
 *
 * Compose them with `cn`, so a later constant or a caller's own classes
 * replace a conflicting utility of the element's base classes (corner radius,
 * fill, text colour).
 */

/**
 * The panel surface: square corners, the deep blue rule, the pale ops fill
 * under a 16px grid, and the ink text colour.
 */
export const OPS_GRID_SURFACE =
  "rounded-none border border-[#1a64b8] bg-[#f6fbff] bg-[linear-gradient(rgb(47_143_233/0.08)_1px,transparent_1px),linear-gradient(90deg,rgb(47_143_233/0.08)_1px,transparent_1px)] bg-size-[16px_16px] bg-position-[-1px_-1px] text-[#0b2a47] dark:border-[#7abeff] dark:bg-[#132436] dark:bg-[linear-gradient(rgb(122_190_255/0.035)_1px,transparent_1px),linear-gradient(90deg,rgb(122_190_255/0.035)_1px,transparent_1px)] dark:text-[#e6f0fa]";

/**
 * The azure tint of a highlighted element: keyboard focus, and pointer hover.
 * Tailwind's `hover:` applies only where the device can hover, so a tap on a
 * touch screen leaves no second tinted row behind, as on the website.
 */
export const OPS_GRID_HIGHLIGHT = "focus:bg-[rgb(47_143_233/0.08)] hover:bg-[rgb(47_143_233/0.08)]";

/**
 * The accent colour of icons and marks on the surface. Written as a literal
 * because no theme token holds it in both Kazan and the sky apps.
 */
export const OPS_GRID_ACCENT = "text-[#1a64b8] dark:text-[#7abeff]";
