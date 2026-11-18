import createNextConfig from "@oba/shared/next.config.mjs";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./lib/i18n/request.ts");

/** @type {import('next').NextConfig} */
const nextConfig = createNextConfig({
  async redirects() {
    return [
      // The lead page moved to `/battlefield`; bookmarks and shared links to the
      // old path still land. URLs carry no locale (localePrefix "never"); a typed
      // or linked prefix keeps its locale, so the proxy still applies it to the
      // new path. The locales match `SUPPORTED_LOCALES` in
      // lib/i18n/supported-locales.ts.
      { source: "/board", destination: "/battlefield", permanent: true },
      { source: "/:locale(en|nl|tr)/board", destination: "/:locale/battlefield", permanent: true },
    ];
  },
});

export default withNextIntl(nextConfig);
