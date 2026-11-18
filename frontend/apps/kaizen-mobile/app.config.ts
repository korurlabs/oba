import type { ConfigContext, ExpoConfig } from "expo/config";

/** The Kaizen web locales whose pages open in the app. */
const LINK_LOCALES: readonly string[] = ["en", "nl", "tr"];

/**
 * The Kaizen web pages that open in the app, under every locale and without
 * one (the chat copies its message links without a locale). A ticket key is
 * the board key, a dash and a number, so the web new-ticket form stays in
 * the browser.
 */
const APP_LINK_PATH_PATTERNS: readonly string[] = [
  "/ticket/.*-.*",
  "/board/.*",
  "/chats/.*",
  "/daily-updates",
  "/pull-requests",
];

/**
 * The host of the Kaizen web app of this build, whose ticket, board and chat
 * pages open their app screens as iOS universal links and Android app links. Kaizen web
 * proves the link by serving `/.well-known/apple-app-site-association` and
 * `/.well-known/assetlinks.json`; without them the pages stay in the browser.
 */
const kaizenWebHost: string | null =
  process.env.EXPO_PUBLIC_KAIZEN_WEB_URL?.match(/^https:\/\/([^/?#:]+)/i)?.[1]?.toLowerCase() ??
  null;

/**
 * True for a development build. The development profile of eas.json sets
 * `NODE_ENV` to development, and the preview and production profiles to
 * production; locally, `expo prebuild` and a Debug `expo run:ios` default it
 * to development and a Release one to production.
 */
const isDevelopmentBuild: boolean = process.env.NODE_ENV === "development";

/**
 * App Transport Security of a development build: plain http to `localhost`
 * and every `*.localhost` host, where the local Oba stack runs (each stack
 * instance has its own `<instance>.localhost` host). iOS lets local
 * networking reach `localhost` itself but blocks its subdomains. The first
 * two keys repeat Expo's template, since this object replaces the template's.
 * A release build keeps the template's, so it never allows insecure loads.
 */
const DEVELOPMENT_APP_TRANSPORT_SECURITY = {
  NSAllowsArbitraryLoads: false,
  NSAllowsLocalNetworking: true,
  NSExceptionDomains: {
    localhost: { NSIncludesSubdomains: true, NSExceptionAllowsInsecureHTTPLoads: true },
  },
} as const;

/**
 * Expo config of the Kaizen mobile app.
 *
 * Builds and store submissions run by hand with `eas build` and `eas submit`;
 * there are no over-the-air updates (expo-updates is not installed), so every
 * JavaScript change ships as a store build.
 */
export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: "Kaizen",
  // Must match the slug of the linked EAS project.
  slug: "kaizen",
  // The Expo account or organization that owns the EAS project. Without it, EAS
  // uses the account of whoever runs `eas build`.
  owner: process.env.KAIZEN_EAS_OWNER,
  scheme: "kaizen",
  version: "0.0.1",
  orientation: "portrait",
  // Light, dark or system, chosen in Account > Settings; system by default.
  userInterfaceStyle: "automatic",
  platforms: ["ios", "android"],
  splash: {
    image: "./assets/korur-mark.png",
    resizeMode: "contain",
    // The light `screen` token, the ground of the first screens.
    backgroundColor: "#fafafa",
  },
  ios: {
    // Phones only: the design has no tablet layouts.
    supportsTablet: false,
    bundleIdentifier: "com.korur.kaizen",
    config: { usesNonExemptEncryption: false },
    associatedDomains: kaizenWebHost ? [`applinks:${kaizenWebHost}`] : [],
    entitlements: {
      // Urgent incident pushes are Time Sensitive, so they break through a
      // Focus. The App ID needs the Time Sensitive Notifications capability.
      "com.apple.developer.usernotifications.time-sensitive": true,
    },
    infoPlist: {
      // Each screen sets its status bar (the stacks' `statusBarStyle`), so the
      // clock and icons follow the light or dark theme, and the system draws
      // the light bar over a sheet's dimmed backdrop. Expo's template sets NO,
      // a single app-wide style, which stayed dark on the dark theme.
      UIViewControllerBasedStatusBarAppearance: true,
      ...(isDevelopmentBuild ? { NSAppTransportSecurity: DEVELOPMENT_APP_TRANSPORT_SECURITY } : {}),
    },
  },
  android: {
    package: "com.korur.kaizen",
    // Tokens and cached chat must not leave the phone in an Android backup.
    allowBackup: false,
    intentFilters: kaizenWebHost
      ? [
          {
            action: "VIEW",
            autoVerify: true,
            data: ["", ...LINK_LOCALES.map((locale: string) => `/${locale}`)].flatMap(
              (prefix: string) =>
                APP_LINK_PATH_PATTERNS.map((pattern: string) => ({
                  scheme: "https",
                  host: kaizenWebHost,
                  pathPattern: `${prefix}${pattern}`,
                }))
            ),
            category: ["BROWSABLE", "DEFAULT"],
          },
        ]
      : [],
  },
  plugins: [
    "expo-router",
    "expo-localization",
    "expo-font",
    // Tokens and the active organization live in the Keychain and the Keystore.
    "expo-secure-store",
    // The Keycloak login and the Oba web apps open in the system auth session and in-app browser.
    "expo-web-browser",
    // Photos from the camera or the library for chat messages, ticket attachments
    // and ticket descriptions. No video, so no microphone.
    [
      "expo-image-picker",
      {
        photosPermission: "Kaizen opens your photos so you can add them to chats and tickets.",
        cameraPermission: "Kaizen uses the camera so you can add photos to chats and tickets.",
        microphonePermission: false,
      },
    ],
    // "Download file" saves chat and ticket images to the photo library. The photos string
    // repeats the picker's, since both plugins write the same iOS key.
    [
      "expo-media-library",
      {
        photosPermission: "Kaizen opens your photos so you can add them to chats and tickets.",
        savePhotosPermission: "Kaizen saves images from chats and tickets to your photos.",
        granularPermissions: ["photo"],
      },
    ],
    // Push for mentions, replies, @everyone, @here, nudges and urgent incidents.
    // The app creates the three channels at start; a push without a channel
    // falls back to mentions and replies.
    [
      "expo-notifications",
      {
        // The light `primary` token, the tint of the notification icon.
        color: "#2f8fe9",
        defaultChannel: "mentions_replies",
        // A silent read signal wakes the app to remove a conversation's
        // notifications once it was read elsewhere.
        enableBackgroundRemoteNotifications: true,
      },
    ],
  ],
  extra: {
    router: {},
    // Each value comes from an EXPO_PUBLIC_* variable, set per build profile in
    // eas.json and for `expo start` in .env.local (see .env.example). lib/env.ts
    // reads them and fails at start when one is missing.
    apiBaseUrl: process.env.EXPO_PUBLIC_OBA_API_URL,
    keycloakIssuer: process.env.EXPO_PUBLIC_KEYCLOAK_ISSUER,
    keycloakClientId: process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID,
    accountUrl: process.env.EXPO_PUBLIC_ACCOUNT_URL,
    adminUrl: process.env.EXPO_PUBLIC_ADMIN_URL,
    kaizenWebUrl: process.env.EXPO_PUBLIC_KAIZEN_WEB_URL,
    // The EAS project, printed by `eas init`. EAS links builds and submissions
    // to it, and the Expo push service issues this app's push tokens per
    // project (lib/push.ts). `eas init` cannot write into a dynamic config, so
    // the id comes from the environment of `eas build` and of the build worker.
    eas: { projectId: process.env.KAIZEN_EAS_PROJECT_ID },
  },
});
