const path = require("path");
const Module = require("module");

/*
 * Points every Node `require("tailwindcss")` and `require("tailwindcss/...")`
 * of the current process at the Tailwind CSS v3 copy installed for this app.
 *
 * NativeWind 4 works only with Tailwind v3. The Oba web apps use Tailwind v4,
 * which Bun hoists to the workspace node_modules next to the hoisted
 * `nativewind`, so NativeWind would otherwise load v4 and fail ("NativeWind
 * only supports Tailwind CSS v3", or a Tailwind CLI child that crashes while
 * Metro waits on it). Only build tooling requires tailwindcss; the app bundle
 * never does, and the web apps run in their own processes.
 *
 * metro.config.js requires this file for the Metro process, and passes it to
 * NativeWind's forked Tailwind CLI child through NODE_OPTIONS `--require`,
 * because a forked process does not inherit a resolver patched in memory.
 */
const appTailwindDir = path.dirname(
  require.resolve("tailwindcss/package.json", { paths: [__dirname] })
);

const originalResolveFilename = Module._resolveFilename;
Module._resolveFilename = function resolveTailwindV3(request, ...rest) {
  if (request === "tailwindcss" || request.startsWith("tailwindcss/")) {
    const subPath = request.slice("tailwindcss".length);
    const redirected = subPath ? path.join(appTailwindDir, subPath) : appTailwindDir;
    return originalResolveFilename.call(this, redirected, ...rest);
  }
  return originalResolveFilename.call(this, request, ...rest);
};
