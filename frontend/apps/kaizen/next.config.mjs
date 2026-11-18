import createNextConfig from "@oba/shared/next.config.mjs";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./lib/i18n/request.ts");

/** @type {import('next').NextConfig} */
const nextConfig = createNextConfig({
  async redirects() {
    return [
      // The chat page and its daily update pages moved from `updates` to
      // `chat`. Links already sent in notifications and emails, and saved
      // bookmarks, still use the old path; Next.js keeps the query string
      // (`channelId`, `jump`, `thread`) on the redirect, and the `chat` rules
      // below take it on to the current pages.
      {
        source: "/dashboard/updates/:path*",
        destination: "/dashboard/chat/:path*",
        permanent: false,
      },
      // Pages no longer sit under `/dashboard`. Old links in notifications,
      // bookmarks and CLI output land on the same page with their query kept.
      // These rules map paths only: an old link that names a record by id
      // (`/dashboard/chat?channelId=<id>`) is turned into its slug URL by the
      // page it reaches. Temporary redirects, so browsers do not cache them
      // and the rules can be removed later. The more specific paths come
      // first, since the first matching rule wins.
      {
        source: "/dashboard/chat/pull-requests/:path*",
        destination: "/pull-requests/:path*",
        permanent: false,
      },
      {
        source: "/dashboard/chat/new",
        destination: "/daily-updates/new",
        permanent: false,
      },
      {
        source: "/dashboard/chat/:updateId/edit",
        destination: "/daily-updates/:updateId/edit",
        permanent: false,
      },
      {
        source: "/dashboard/chat",
        destination: "/chats",
        permanent: false,
      },
      {
        source: "/dashboard/tickets",
        destination: "/tickets",
        permanent: false,
      },
      {
        source: "/dashboard/incidents",
        destination: "/incidents",
        permanent: false,
      },
      {
        source: "/dashboard",
        destination: "/home",
        permanent: false,
      },
    ];
  },
});

export default withNextIntl(nextConfig);
