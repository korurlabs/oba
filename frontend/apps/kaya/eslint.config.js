import coreWebVitals from "eslint-config-next/core-web-vitals";
import typescriptConfig from "eslint-config-next/typescript";

/** @type {import("eslint").Linter.Config[]} */
const config = [
  {
    ignores: [".next/**", "out/**", "node_modules/**", "public/runs/**"],
  },
  ...coreWebVitals,
  ...typescriptConfig,
  {
    rules: {
      // No silent failures: an empty catch block is never allowed.
      "no-empty": ["error", { allowEmptyCatch: false }],
      "@typescript-eslint/no-unused-vars": ["error", { caughtErrors: "all" }],
    },
  },
];

export default config;
