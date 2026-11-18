import createNextConfig from "@oba/shared/next.config.mjs";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./lib/i18n/request.ts");

/** @type {import('next').NextConfig} */
const nextConfig = createNextConfig();

export default withNextIntl(nextConfig);
