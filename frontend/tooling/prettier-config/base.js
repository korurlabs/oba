/** @type {import("prettier").Config} */
const config = {
  // Core formatting
  semi: true,
  singleQuote: false,
  quoteProps: "as-needed",
  trailingComma: "es5",
  tabWidth: 2,
  useTabs: false,

  // Line wrapping
  printWidth: 100,
  endOfLine: "lf",

  // JSX specific
  jsxSingleQuote: false,
  bracketSpacing: true,
  bracketSameLine: false,
  arrowParens: "always",

  // File type overrides
  overrides: [
    {
      files: "*.json",
      options: {
        printWidth: 200,
      },
    },
    {
      files: "*.md",
      options: {
        proseWrap: "always",
        printWidth: 80,
      },
    },
    {
      files: ["*.yaml", "*.yml"],
      options: {
        tabWidth: 2,
      },
    },
  ],

  // Plugin configurations
  plugins: ["prettier-plugin-tailwindcss"],
};

export default config;
