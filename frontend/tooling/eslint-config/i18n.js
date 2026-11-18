/**
 * A custom ESLint configuration for i18n-specific rules.
 * Ensures proper usage of internationalized navigation components.
 *
 * @type {import("eslint").Linter.Config}
 */
export const i18nConfig = [
  {
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "next/navigation",
              importNames: [
                "usePathname",
                "useRouter",
                "redirect",
                "permanentRedirect",
              ],
              message:
                "Import navigation functions from '@oba/shared/libs/i18n/navigation' instead to ensure i18n support.",
            },
            {
              name: "next/link",
              message:
                "Import Link from '@oba/shared/libs/i18n/navigation' instead to ensure i18n support.",
            },
          ],
        },
      ],
    },
  },
];
