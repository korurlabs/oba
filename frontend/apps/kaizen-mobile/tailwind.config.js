const { hairlineWidth } = require("nativewind/theme");

/** A colour token from global.css. */
const token = (name) => `hsl(var(--${name}))`;

/** Kanban and status names shared by the status, status-bg and column groups. */
const STATUSES = ["todo", "in-progress", "waiting", "in-review", "done"];
const TICKET_TYPES = [
  "incident",
  "bug",
  "feature",
  "tech-debt",
  "improvement",
  "problem",
  "refactor",
];
const PRIORITIES = ["lowest", "low", "medium", "high", "highest", "p1", "p2", "p3"];

const byName = (names, toToken) => Object.fromEntries(names.map((name) => [name, toToken(name)]));

/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: "class",
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    // No `shadow-<colour>` utilities: the shadow token `shadow-card` would also
    // match the colour token `card` and paint the shadow white. Every design
    // shadow carries its own colour in boxShadow below.
    boxShadowColor: {},
    extend: {
      colors: {
        background: token("background"),
        foreground: token("foreground"),
        // Scrolling ground of grouped lists and the kanban; also NAV_THEME.background.
        screen: token("screen"),
        border: token("border"),
        input: token("input"),
        ring: token("ring"),
        // Blue text under 17 pt; primary on white is too light for text.
        link: token("link"),
        grabber: token("grabber"),
        // Translucent tokens keep their alpha here, not in the variable.
        bar: "hsl(var(--bar) / 0.92)",
        "notification-surface": "hsl(var(--notification-surface) / 0.86)",
        scrim: "hsl(var(--scrim) / var(--scrim-alpha))",
        primary: {
          DEFAULT: token("primary"),
          foreground: token("primary-foreground"),
          fill: token("primary-fill"),
          light: token("primary-light"),
          medium: token("primary-medium"),
        },
        card: {
          DEFAULT: token("card"),
          foreground: token("card-foreground"),
          background: token("card-background"),
        },
        popover: {
          DEFAULT: token("popover"),
          foreground: token("popover-foreground"),
        },
        secondary: {
          DEFAULT: token("secondary"),
          foreground: token("secondary-foreground"),
        },
        muted: {
          DEFAULT: token("muted"),
          foreground: token("muted-foreground"),
        },
        accent: {
          DEFAULT: token("accent"),
          foreground: token("accent-foreground"),
        },
        // text-destructive for text, outlines and marks; bg-destructive-fill
        // under text-destructive-foreground.
        destructive: {
          DEFAULT: token("destructive"),
          foreground: token("destructive-foreground"),
          fill: token("destructive-fill"),
        },
        success: {
          DEFAULT: token("success"),
          foreground: token("success-foreground"),
          fill: token("success-fill"),
          light: token("success-light"),
        },
        error: {
          DEFAULT: token("error"),
          light: token("error-light"),
        },
        warning: {
          DEFAULT: token("warning"),
          light: token("warning-light"),
        },
        info: {
          DEFAULT: token("info"),
          light: token("info-light"),
        },
        purple: {
          DEFAULT: token("purple"),
          light: token("purple-light"),
        },
        "chat-highlight": {
          DEFAULT: token("chat-highlight"),
          border: token("chat-highlight-border"),
        },
        sidebar: {
          DEFAULT: token("sidebar"),
          foreground: token("sidebar-foreground"),
          accent: token("sidebar-accent"),
          border: token("sidebar-border"),
        },
        // bg-status-done (dot), bg-status-bg-done (pill), bg-column-done (kanban column)
        status: byName(STATUSES, (name) => token(`status-${name}`)),
        "status-bg": byName(STATUSES, (name) => token(`status-${name}-bg`)),
        column: byName(STATUSES, (name) => token(`column-${name}`)),
        // bg-type-bug-bg, text-type-bug-fg
        type: byName(TICKET_TYPES, (name) => ({
          bg: token(`type-${name}-bg`),
          fg: token(`type-${name}-fg`),
        })),
        priority: byName(PRIORITIES, (name) => token(`priority-${name}`)),
        chart: byName(["1", "2", "3", "4", "5"], (name) => token(`chart-${name}`)),
        korur: {
          blue: {
            DEFAULT: token("korur-blue"),
            hover: token("korur-blue-hover"),
            strong: token("korur-blue-strong"),
            deep: token("korur-blue-deep"),
            medium: token("korur-blue-medium"),
            light: token("korur-blue-light"),
            accent: token("korur-blue-accent"),
          },
          slate: {
            DEFAULT: token("korur-slate"),
            border: token("korur-slate-border"),
          },
        },
      },
      borderRadius: {
        xs: "calc(var(--radius) - 6px)",
        sm: "calc(var(--radius) - 4px)",
        md: "calc(var(--radius) - 2px)",
        lg: "var(--radius)",
        xl: "calc(var(--radius) + 4px)",
        // Android sheet and dialog top corners; iOS sheets use the system radius.
        sheet: "28px",
      },
      // The type scale, named after iOS Dynamic Type; Android uses the same
      // sizes. The weight comes from the per-weight font family below
      // (font-sans-semibold), because a static font ignores fontWeight.
      fontSize: {
        "large-title": ["34px", { lineHeight: "41px", letterSpacing: "-0.5px" }],
        "title-1": ["28px", { lineHeight: "34px", letterSpacing: "-0.4px" }],
        "title-2": ["22px", { lineHeight: "28px", letterSpacing: "-0.3px" }],
        "title-3": ["20px", { lineHeight: "25px" }],
        headline: ["17px", { lineHeight: "22px" }],
        body: ["17px", { lineHeight: "22px" }],
        callout: ["16px", { lineHeight: "21px" }],
        subhead: ["15px", { lineHeight: "20px" }],
        footnote: ["13px", { lineHeight: "18px" }],
        caption: ["12px", { lineHeight: "16px" }],
        "caption-2": ["11px", { lineHeight: "13px" }],
        button: ["15px", { lineHeight: "20px" }],
        meta: ["13px", { lineHeight: "16px" }],
        // Set uppercase with the class `uppercase`.
        badge: ["11px", { lineHeight: "14px", letterSpacing: "0.4px" }],
        "ticket-key": ["12px", { lineHeight: "16px" }],
        code: ["13px", { lineHeight: "20px" }],
      },
      fontFamily: {
        // The base families map to the Regular faces registered in lib/fonts.ts;
        // each other weight is its own family.
        sans: ["Inter", "system-ui", "sans-serif"],
        "sans-medium": ["Inter-Medium"],
        "sans-semibold": ["Inter-SemiBold"],
        "sans-bold": ["Inter-Bold"],
        mono: ["JetBrainsMono", "monospace"],
        "mono-medium": ["JetBrainsMono-Medium"],
        "mono-semibold": ["JetBrainsMono-SemiBold"],
      },
      // Light mode only: pair each with `dark:shadow-none`, because dark
      // surfaces carry depth through lightness. NativeWind draws the first
      // layer of each with the native shadow props; the exact two-layer and
      // negative-spread shadows need React Native's `boxShadow` style.
      boxShadow: {
        card: "0 4px 12px rgba(0, 0, 0, 0.05), 0 1px 3px rgba(74, 163, 255, 0.1)",
        "card-hover": "0 12px 24px -8px rgba(0, 0, 0, 0.12)",
        dialog: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
        "primary-glow": "0 8px 20px rgba(47, 143, 233, 0.35)",
      },
      // Android elevation of each shadow (NativeWind sets it with `shadow-*` on
      // Android): a resting card is level 1; the dragged card, sheets, dialogs
      // and the FAB use the Material 3 levels 4, 3 and 3.
      elevation: {
        card: 1,
        "card-hover": 8,
        dialog: 6,
        "primary-glow": 6,
      },
      // Size tokens: h-hit-ios, min-h-row-android, size-fab, size-avatar-md.
      // The 2 to 48 px spacing tokens are the default scale.
      spacing: {
        "hit-ios": "44px",
        "hit-android": "48px",
        "row-ios": "44px",
        "row-android": "56px",
        "nav-bar-ios": "44px",
        "top-bar-android": "64px",
        "tab-bar-ios": "49px",
        "nav-bar-android": "80px",
        fab: "56px",
        "avatar-sm": "20px",
        "avatar-md": "32px",
        "avatar-lg": "64px",
      },
      borderWidth: {
        hairline: hairlineWidth(),
      },
    },
  },
  future: {
    hoverOnlyWhenSupported: true,
  },
  plugins: [],
};
