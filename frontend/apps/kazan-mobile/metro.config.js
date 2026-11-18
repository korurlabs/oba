const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const projectRoot = __dirname;
// apps/kazan-mobile -> apps -> frontend: the Bun workspace root that owns
// bun.lock and the hoisted node_modules.
const workspaceRoot = path.resolve(projectRoot, "../..");

// Tailwind v3 for NativeWind, in this process and in NativeWind's forked
// Tailwind CLI child (see tailwind-v3-resolve.js). Must run before
// nativewind/metro loads, because NativeWind checks the Tailwind version when
// its module loads.
const tailwindResolvePath = path.join(projectRoot, "tailwind-v3-resolve.js");
require(tailwindResolvePath);
const preloadOption = `--require ${JSON.stringify(tailwindResolvePath)}`;
if (!(process.env.NODE_OPTIONS ?? "").includes(preloadOption)) {
  process.env.NODE_OPTIONS = [process.env.NODE_OPTIONS, preloadOption].filter(Boolean).join(" ");
}

const { withNativeWind } = require("nativewind/metro");

const config = getDefaultConfig(projectRoot);

// Watch the workspace root, so Metro sees the hoisted node_modules and the
// workspace packages (`@oba/shared` resolves through its package `exports` to
// TypeScript source under packages/shared, which Metro transpiles like app
// code). Without it, hoisted modules fail to resolve and shared changes do not
// reload.
config.watchFolders = [workspaceRoot];

// App-local node_modules first (the pinned React 19.2.0 and Tailwind v3 live
// there), then the hoisted workspace root. Hierarchical lookup stays on: Bun
// nests a package under its dependent when versions conflict. Expo's
// autolinking resolver, on by default in a workspace, resolves react,
// react-dom and react-native from this app for every importer, so hoisted
// libraries share the app's single React copy.
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
];

// Expo DOM components are bundled for the web platform, which Expo's
// autolinking resolver leaves out because the app declares no web platform.
// There a hoisted library (TipTap's React bindings) would import the
// workspace root's react and react-dom beside the app's react, and two Reacts
// in one page break every hook. Resolve them from the app for every importer.
const APP_PINNED_MODULES = /^(react|react-dom)(\/.*)?$/;
const appOrigin = path.join(projectRoot, "package.json");
const upstreamResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  const resolve = upstreamResolveRequest ?? context.resolveRequest;
  if (platform === "web" && APP_PINNED_MODULES.test(moduleName)) {
    return resolve({ ...context, originModulePath: appOrigin }, moduleName, platform);
  }
  return resolve(context, moduleName, platform);
};

module.exports = withNativeWind(config, {
  input: "./global.css",
  inlineRem: 16,
});
