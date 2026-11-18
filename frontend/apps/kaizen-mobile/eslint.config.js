// ESLint flat config for the Kaizen mobile app. It spreads eslint-config-expo,
// not @oba/eslint-config, whose presets target Next.js and the DOM.
//
// Empty and inert catches are errors (no silent failures):
//   - `no-empty` with `allowEmptyCatch: false` flags `catch {}` with no body;
//   - `@typescript-eslint/no-unused-vars` with `caughtErrors: "all"` flags a
//     catch that never reads its error.
const expoConfig = require("eslint-config-expo/flat");

module.exports = [
  ...expoConfig,
  {
    rules: {
      "no-empty": ["error", { allowEmptyCatch: false }],
    },
  },
  {
    files: ["**/*.ts", "**/*.tsx", "**/*.d.ts"],
    rules: {
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          vars: "all",
          args: "none",
          ignoreRestSiblings: true,
          caughtErrors: "all",
          caughtErrorsIgnorePattern: "^_",
        },
      ],
    },
  },
  {
    // A Node build helper, loaded by metro.config.js and preloaded into
    // NativeWind's Tailwind child process; never part of the app bundle.
    files: ["tailwind-v3-resolve.js"],
    languageOptions: {
      globals: { __dirname: "readonly", require: "readonly", module: "writable" },
    },
  },
  {
    ignores: ["node_modules/**", ".expo/**", "dist/**", "ios/**", "android/**"],
  },
];
