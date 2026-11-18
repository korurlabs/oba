/**
 * The host `next/image` may load remote images from, such as the blob store
 * that holds avatars and attachments. Next.js fixes this list when the app is
 * built, so it comes from `IMAGE_REMOTE_HOST` at build time; a build without
 * it allows no remote image, and no deployment's host is baked into the code.
 *
 * @param {string | undefined} host
 */
const imageRemotePatterns = (host) =>
  host ? [{ protocol: "https", hostname: host, pathname: "/**" }] : [];

/** @type {import('next').NextConfig} */
const createNextConfig = (overrides = {}) => {
  const baseConfig = {
    transpilePackages: ["@oba/ui", "@oba/shared"],
    output: "standalone",
    // Without this every response carries `x-powered-by: Next.js`, which tells
    // an outside scan which framework to probe for known vulnerabilities.
    poweredByHeader: false,
    experimental: {
      optimizePackageImports: [
        "lucide-react",
        "@oba/ui",
        "@oba/shared",
      ],
    },
    images: {
      minimumCacheTTL: 2678400, // 31 days
      qualities: [75],
      deviceSizes: [640, 750, 828, 1080, 1200, 1920],
      remotePatterns: imageRemotePatterns(process.env.IMAGE_REMOTE_HOST),
    },
  };

  // Deep merge the base config with overrides
  const mergeConfig = (base, overrides) => {
    const result = { ...base };

    for (const key in overrides) {
      if (overrides[key] && typeof overrides[key] === "object" && !Array.isArray(overrides[key])) {
        result[key] = mergeConfig(base[key] || {}, overrides[key]);
      } else if (Array.isArray(overrides[key]) && Array.isArray(base[key])) {
        // For arrays like transpilePackages, merge them uniquely
        result[key] = [...new Set([...base[key], ...overrides[key]])];
      } else {
        result[key] = overrides[key];
      }
    }

    return result;
  };

  return mergeConfig(baseConfig, overrides);
};

export default createNextConfig;
