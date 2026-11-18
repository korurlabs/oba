/** @type {import("prettier").Config} */
const config = {
  semi: true,
  singleQuote: false,
  quoteProps: "as-needed",
  trailingComma: "es5",
  tabWidth: 2,
  useTabs: false,
  printWidth: 100,
  endOfLine: "lf",
  jsxSingleQuote: false,
  bracketSpacing: true,
  bracketSameLine: false,
  arrowParens: "always",
  overrides: [
    {
      files: "*.json",
      options: { printWidth: 200 },
    },
    {
      files: "*.md",
      options: { proseWrap: "always", printWidth: 80 },
    },
  ],
  plugins: ["prettier-plugin-tailwindcss"],
};

export default config;
