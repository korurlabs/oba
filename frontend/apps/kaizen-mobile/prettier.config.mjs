import baseConfig from "@oba/prettier-config/base";

/**
 * The shared options without prettier-plugin-tailwindcss: the plugin targets
 * the Tailwind v4 the web apps use, while this app builds with Tailwind v3 for
 * NativeWind.
 *
 * @type {import("prettier").Config}
 */
const config = {
  ...baseConfig,
  plugins: [],
};

export default config;
