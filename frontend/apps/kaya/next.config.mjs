/** @type {import("next").NextConfig} */
const nextConfig = {
  // Kaya signs users in through Keycloak and reads sessions from its API, so it
  // runs as a Next.js server: the Auth.js routes under /api/auth need one.
  reactStrictMode: true,
  // The production image, built by the workspace Dockerfile, runs
  // .next/standalone/apps/kaya/server.js; postbuild copies the static assets
  // and public/ next to it.
  output: "standalone",
  // The tabbed page of rule and agent files was /conventions before it became
  // /rules, and /conventions is the page of the convention docs. A link that names
  // a tab is one to the old page; Next.js passes its other query parameters on.
  async redirects() {
    return [
      {
        source: "/conventions",
        has: [{ type: "query", key: "tab" }],
        destination: "/rules",
        permanent: false,
      },
      // A Claude Code installation on one computer was called an agent before it
      // became a machine, so bookmarks and shared links to the old pages still land.
      { source: "/agents", destination: "/machines", permanent: true },
      { source: "/agents/:id", destination: "/machines/:id", permanent: true },
      // The wall's filter was `agentId`. Next.js keeps the request's query on the
      // redirect, so the rule must not match once `machineId` is set, or it loops.
      {
        source: "/",
        has: [{ type: "query", key: "agentId", value: "(?<agentId>.+)" }],
        missing: [{ type: "query", key: "machineId" }],
        destination: "/?machineId=:agentId",
        permanent: true,
      },
    ];
  },
};

export default nextConfig;
